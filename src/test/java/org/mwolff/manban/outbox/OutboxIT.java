package org.mwolff.manban.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.outbox.application.OutboxDispatchService;
import org.mwolff.manban.outbox.application.OutboxHandler;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxRetentionService;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Zusagen der transaktionalen Outbox gegen echtes Postgres (Issue #501).
 *
 * <p>Der geplante Worker ist abgeschaltet ({@code manban.outbox.enabled=false}) und der Durchlauf
 * stattdessen direkt aufgerufen — sonst liefe er den Testschritten dazwischen und die Zusagen wären
 * nicht mehr beobachtbar (dasselbe Vorgehen wie im {@code DoneRetentionIT}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "manban.outbox.enabled=false",
      "manban.outbox.max-attempts=2",
      "manban.outbox.retry-base-delay=PT30S",
      // Der Test-Handler erzwingt einen eigenen Spring-Kontext — und der bringt einen zweiten
      // Verbindungspool mit. Mit der Standardgröße reißt die Suite die max_connections des
      // geteilten Postgres-Containers (andere ITs scheitern dann an "too many clients"). Dieser
      // Kontext läuft sequenziell und kommt mit wenigen Verbindungen aus.
      "spring.datasource.hikari.maximum-pool-size=3",
      "spring.datasource.hikari.minimum-idle=1"
    })
class OutboxIT extends AbstractIntegrationTest {

  private static final String TYPE = "test.effect";

  @Autowired private OutboxWriter writer;
  @Autowired private OutboxDispatchService dispatch;
  @Autowired private OutboxRetentionService retention;
  @Autowired private RecordingHandler handler;
  @Autowired private BusinessUseCase useCase;
  @Autowired private JdbcTemplate jdbc;

  /** Handler, dessen Erfolg der Test steuert; zählt jede Zustellung mit. */
  static final class RecordingHandler implements OutboxHandler {

    private final List<String> delivered = new ArrayList<>();
    private int failuresLeft;

    void failNextTimes(int count) {
      failuresLeft = count;
    }

    void reset() {
      delivered.clear();
      failuresLeft = 0;
    }

    @Override
    public String eventType() {
      return TYPE;
    }

    @Override
    public void handle(String payload) {
      if (failuresLeft > 0) {
        failuresLeft--;
        throw new IllegalStateException("Zustellung fehlgeschlagen");
      }
      delivered.add(payload);
    }
  }

  /**
   * Stellvertreter für einen fachlichen Use-Case: ändert Fachdaten und plant im selben Atemzug den
   * Seiteneffekt ein.
   */
  // PMD.PublicMemberInNonPublicType: Die beiden Methoden MUESSEN public bleiben. Spring legt seinen
  // Transaktions-Proxy nur um public Methoden — package-private annotiert bleibt @Transactional
  // wirkungslos, und genau die Transaktionsgrenze ist hier der Testgegenstand. Ein Umstellen macht
  // drei ITs still falsch-gruen bzw. rot (einmal passiert, siehe Issue #501).
  @SuppressWarnings("PMD.PublicMemberInNonPublicType")
  static class BusinessUseCase {

    private final OutboxWriter writer;
    private final JdbcTemplate jdbc;

    BusinessUseCase(OutboxWriter writer, JdbcTemplate jdbc) {
      this.writer = writer;
      this.jdbc = jdbc;
    }

    @Transactional
    public void changeSomethingAndSchedule(String key, String idempotencyKey) {
      jdbc.update("INSERT INTO app_setting (setting_key, setting_value) VALUES (?, ?)", key, "v");
      writer.schedule(new OutboxMessage(TYPE, idempotencyKey, "settingKey=" + key));
    }

    @Transactional
    public void changeSomethingScheduleAndFail(String key, String idempotencyKey) {
      changeSomethingAndSchedule(key, idempotencyKey);
      throw new IllegalStateException("fachlicher Abbruch nach dem Einplanen");
    }
  }

  // PMD.TestClassWithoutTestCases: Namensmuster-Fehlalarm — die Klasse liefert die Test-Beans und
  // ist keine Testklasse. Umbenennen ist keine Option: Der Klassenname geht in den Schluessel des
  // Spring-Testkontext-Caches ein, und ein anderer Name laesst drei ITs fehlschlagen (probiert).
  @SuppressWarnings("PMD.TestClassWithoutTestCases")
  @TestConfiguration
  static class TestEffects {

    @Bean
    RecordingHandler recordingHandler() {
      return new RecordingHandler();
    }

    @Bean
    BusinessUseCase businessUseCase(OutboxWriter writer, JdbcTemplate jdbc) {
      return new BusinessUseCase(writer, jdbc);
    }
  }

  /**
   * Der Spring-Kontext ist über die Testmethoden geteilt — der Handler-Zustand darf es nicht sein.
   */
  @BeforeEach
  void resetHandler() {
    handler.reset();
  }

  private List<Map<String, Object>> entries() {
    return jdbc.queryForList("SELECT * FROM outbox_entry ORDER BY id");
  }

  private Map<String, Object> onlyEntry() {
    List<Map<String, Object>> all = entries();
    assertThat(all).hasSize(1);
    return all.get(0);
  }

  /**
   * Stellt offene Einträge auf „längst fällig". Bewusst mit der JVM-Uhr statt mit Postgres-{@code
   * now()}: Der Datenbank-Container hat seine eigene Uhr, und geht sie der JVM auch nur
   * Millisekunden vor, gilt ein mit {@code now()} gestempelter Eintrag für den unmittelbar
   * folgenden {@code dispatchDue()} (JVM-Uhr) noch nicht als fällig. Genau diese Drift machte drei
   * Tests dieser Klasse abhängig vom aktuellen Uhrenversatz zwischen Host und Container — erst
   * grün, dann reproduzierbar rot (Issue #501). Die Stunde Rückversatz macht das Ergebnis von
   * beiden Uhren unabhängig; Zeitvergleiche mit Tages-Margen (Retention) bleiben davon unberührt.
   */
  private void makeDue() {
    jdbc.update(
        "UPDATE outbox_entry SET next_attempt_at = ? WHERE status = 'PENDING'",
        Timestamp.from(Instant.now().minus(Duration.ofHours(1))));
  }

  @Test
  void schedulingOutsideTransaction_isRefused() {
    // Given / When / Then — die MANDATORY-Grenze schlägt zu, bevor irgendetwas geschrieben wird.
    assertThatThrownBy(() -> writer.schedule(new OutboxMessage(TYPE, "key:1", "payload")))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(entries()).isEmpty();
  }

  @Test
  void rollbackOfTheBusinessTransaction_leavesNoEntry() {
    // Given / When
    assertThatThrownBy(() -> useCase.changeSomethingScheduleAndFail("k1", "key:1"))
        .isInstanceOf(IllegalStateException.class);

    // Then
    assertThat(entries()).isEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM app_setting", Integer.class)).isZero();
  }

  @Test
  void commitOfTheBusinessTransaction_leavesOnePendingEntry() {
    // Given / When
    useCase.changeSomethingAndSchedule("k1", "key:1");

    // Then
    assertThat(onlyEntry())
        .containsEntry("status", "PENDING")
        .containsEntry("event_type", TYPE)
        .containsEntry("idempotency_key", "key:1")
        .containsEntry("payload", "settingKey=k1")
        .containsEntry("attempts", 0);
  }

  @Test
  void schedulingTheSameIdempotencyKeyTwice_yieldsSingleEntry() {
    // Given
    useCase.changeSomethingAndSchedule("k1", "key:1");

    // When
    useCase.changeSomethingAndSchedule("k2", "key:1");

    // Then
    assertThat(onlyEntry()).containsEntry("payload", "settingKey=k1");
  }

  @Test
  void dispatch_runsTheEffectOnceAndMarksTheEntryDone() {
    // Given
    useCase.changeSomethingAndSchedule("k1", "key:1");

    // When
    int dispatched = dispatch.dispatchDue();

    // Then
    assertThat(dispatched).isEqualTo(1);
    assertThat(handler.delivered).containsExactly("settingKey=k1");
    assertThat(onlyEntry()).containsEntry("status", "DONE").containsEntry("attempts", 1);
  }

  @Test
  void dispatch_clearsThePayloadOfCompletedEntry() {
    // Given
    useCase.changeSomethingAndSchedule("k1", "key:1");

    // When
    dispatch.dispatchDue();

    // Then — kein Klartext bleibt dauerhaft liegen, der Eintrag selbst bleibt auffindbar.
    assertThat(onlyEntry()).containsEntry("payload", "").containsEntry("idempotency_key", "key:1");
  }

  @Test
  void dispatchingTwice_deliversTheEffectOnlyOnce() {
    // Given
    useCase.changeSomethingAndSchedule("k1", "key:1");
    dispatch.dispatchDue();

    // When
    int secondRun = dispatch.dispatchDue();

    // Then
    assertThat(secondRun).isZero();
    assertThat(handler.delivered).hasSize(1);
  }

  @Test
  void failedAttempt_isRetriedWithoutRepeatingTheBusinessOperation() {
    // Given
    useCase.changeSomethingAndSchedule("k1", "key:1");
    handler.failNextTimes(1);

    // When
    int dispatched = dispatch.dispatchDue();

    // Then
    assertThat(dispatched).isZero();
    assertThat(handler.delivered).isEmpty();
    assertThat(onlyEntry())
        .containsEntry("status", "PENDING")
        .containsEntry("attempts", 1)
        .hasEntrySatisfying(
            "last_error", error -> assertThat((String) error).contains("fehlgeschlagen"));
    // Die fachliche Änderung lief genau einmal — der Retry wiederholt nur den Seiteneffekt.
    assertThat(jdbc.queryForObject("SELECT count(*) FROM app_setting", Integer.class)).isOne();
  }

  @Test
  void failedAttempt_isPostponedIntoTheFuture() {
    // Given
    useCase.changeSomethingAndSchedule("k1", "key:1");
    handler.failNextTimes(1);
    Instant before = Instant.now();

    // When
    dispatch.dispatchDue();

    // Then — vor Ablauf der Wartezeit greift kein weiterer Lauf.
    assertThat(dispatch.dispatchDue()).isZero();
    assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM outbox_entry", Instant.class))
        .isAfter(before.plus(Duration.ofSeconds(20)));
  }

  @Test
  void theRetryAfterTheWaitingPeriod_succeeds() {
    // Given
    useCase.changeSomethingAndSchedule("k1", "key:1");
    handler.failNextTimes(1);
    dispatch.dispatchDue();
    makeDue();

    // When
    int dispatched = dispatch.dispatchDue();

    // Then
    assertThat(dispatched).isEqualTo(1);
    assertThat(handler.delivered).containsExactly("settingKey=k1");
    assertThat(onlyEntry()).containsEntry("status", "DONE").containsEntry("attempts", 2);
  }

  @Test
  void exhaustedAttempts_endInVisibleFailedEntry() {
    // Given — max-attempts=2, der zweite Fehlversuch gibt auf.
    useCase.changeSomethingAndSchedule("k1", "key:1");
    handler.failNextTimes(2);
    dispatch.dispatchDue();
    makeDue();

    // When
    dispatch.dispatchDue();

    // Then
    assertThat(onlyEntry())
        .containsEntry("status", "FAILED")
        .containsEntry("attempts", 2)
        .containsEntry("payload", "");
    assertThat(dispatch.dispatchDue()).isZero();
  }

  @Test
  void retention_removesCompletedEntriesOnly() {
    // Given
    useCase.changeSomethingAndSchedule("k1", "key:1");
    dispatch.dispatchDue();
    useCase.changeSomethingAndSchedule("k2", "key:2");
    handler.failNextTimes(2);
    dispatch.dispatchDue();
    makeDue();
    dispatch.dispatchDue();
    jdbc.update("UPDATE outbox_entry SET completed_at = now() - interval '10 days'");

    // When
    int purged = retention.purgeCompleted(Instant.now(), 7);

    // Then — der gescheiterte Eintrag bleibt als sichtbarer Rückstand stehen.
    assertThat(purged).isEqualTo(1);
    assertThat(onlyEntry())
        .containsEntry("status", "FAILED")
        .containsEntry("idempotency_key", "key:2");
  }
}
