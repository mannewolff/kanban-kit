package org.mwolff.manban.backup.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.outbox.application.OutboxDispatchService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Der Wachhund am laufenden Stack (Issue #828): Protokoll, Urteil, Empfängerliste und Outbox
 * zusammen.
 *
 * <p>Den Zustandsspeicher „genau ein Alarm je Zustandswechsel" bildet allein der
 * Idempotenzschlüssel (Plan #825). Ob das trägt, lässt sich nur hier zeigen — im Unit-Test ist die
 * Outbox ein Mock und unterdrückt nichts.
 *
 * <p>Warnfristen aus dem eingestellten Rhythmus: 03:00 täglich → 48 h für Basissicherung und Kopie
 * außer Haus, 5 min Spiegeltakt → 10 min für Spiegel und WAL-Archiv.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "manban.backup.enabled=true",
      "manban.backup.base-cron=0 0 3 * * *",
      "manban.backup.mirror-interval=PT5M",
      // Eigener Spring-Kontext → zweiter Verbindungspool; klein halten, damit die Suite die
      // max_connections des geteilten Containers nicht reißt (wie im MailOutboxIT).
      "spring.datasource.hikari.maximum-pool-size=3",
      "spring.datasource.hikari.minimum-idle=1"
    })
class BackupWatchdogIT extends AbstractIntegrationTest {

  private static final String ALARM_TYPE = "mail.backup-alert";

  /** Der Wachhund liest nur E-Mail-Adressen; ein echter Hash wird dafür nicht gebraucht. */
  private static final String HASH = "{noop}irrelevant";

  // ObjectProvider statt direkter Injektion: Die verschachtelte Klasse fährt denselben Test mit
  // abgeschalteter Sicherung, und dort existiert die Bean gerade nicht — genau das ist ihr Punkt.
  @Autowired private ObjectProvider<BackupWatchdogJob> wachhund;

  @Autowired private OutboxDispatchService dispatch;
  @Autowired private AppUserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private void admin(String email) {
    users.save(new AppUser(null, email, HASH, "Admin", true, PlatformRole.ADMIN));
  }

  private void lauf(String art, Duration vor, String ergebnis) {
    Instant start = Instant.now().minus(vor);
    jdbc.update(
        "INSERT INTO backup_run (kind, started_at, finished_at, outcome, detail, bytes)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        art,
        Timestamp.from(start),
        Timestamp.from(start.plusSeconds(30)),
        ergebnis,
        null,
        4096L);
  }

  /** Alles frisch bis auf die Basissicherung — sie schweigt drei Tage und ist damit veraltet. */
  private void nurDieBasissicherungSchweigt() {
    lauf("basis", Duration.ofDays(3), "erfolg");
    lauf("wal", Duration.ofMinutes(1), "erfolg");
    lauf("spiegel", Duration.ofMinutes(1), "erfolg");
    lauf("offsite", Duration.ofHours(2), "erfolg");
  }

  private List<Map<String, Object>> alarme() {
    return jdbc.queryForList(
        "SELECT * FROM outbox_entry WHERE event_type = ? ORDER BY id", ALARM_TYPE);
  }

  private static String feld(Map<String, Object> eintrag, int index) {
    return PayloadFields.split(String.valueOf(eintrag.get("payload")), 3).get(index);
  }

  @Test
  void zweiLaeufeOhneNeuenErfolgErgebenGenauEinenAlarmJeAdmin() {
    // Given
    admin("a@example.org");
    admin("b@example.org");
    nurDieBasissicherungSchweigt();

    // When — der Wachhund schlägt stündlich an, der Zustand ändert sich dazwischen nicht.
    wachhund.getObject().run();
    wachhund.getObject().run();

    // Then
    List<Map<String, Object>> alarme = alarme();
    assertThat(alarme).hasSize(2);
    assertThat(alarme)
        .map(eintrag -> feld(eintrag, 0))
        .containsExactly("a@example.org", "b@example.org");
    assertThat(alarme).map(eintrag -> feld(eintrag, 1)).containsOnly("VERALTET");
    assertThat(alarme).map(eintrag -> eintrag.get("idempotency_key")).doesNotHaveDuplicates();
  }

  @Test
  void nachEinemNeuenErfolgDerErneutVeraltetGehtEinZweiterAlarmHinaus() {
    // Given
    admin("a@example.org");
    nurDieBasissicherungSchweigt();
    wachhund.getObject().run();
    assertThat(alarme()).hasSize(1);

    // When — es gab wieder einen gelungenen Lauf, der inzwischen selbst zu alt ist.
    lauf("basis", Duration.ofHours(60), "erfolg");
    wachhund.getObject().run();

    // Then
    List<Map<String, Object>> alarme = alarme();
    assertThat(alarme).hasSize(2);
    assertThat(alarme.get(0).get("idempotency_key"))
        .isNotEqualTo(alarme.get(1).get("idempotency_key"));
  }

  @Test
  void ohneJemalsGelungenenLaufGehtGenauEinAlarmHinaus() {
    // Given — frische Instanz mit eingeschalteter Sicherung, die nie gelaufen ist.
    admin("a@example.org");

    // When
    wachhund.getObject().run();
    wachhund.getObject().run();

    // Then
    List<Map<String, Object>> alarme = alarme();
    assertThat(alarme).hasSize(1);
    assertThat(feld(alarme.get(0), 2)).isEqualTo("nie");
  }

  @Test
  void beiAbgeschaltetemMailversandLaeuftDerWachhundDurch() {
    // Given — der ausgelieferte Zustand ist manban.mail.enabled=false.
    admin("a@example.org");
    nurDieBasissicherungSchweigt();

    // When
    wachhund.getObject().run();
    int zugestellt = dispatch.dispatchDue();

    // Then — der Alarm gilt als erledigt, nicht als Fehlversuch; er landete nur im Protokoll.
    assertThat(zugestellt).isEqualTo(1);
    assertThat(alarme())
        .singleElement()
        .extracting(eintrag -> eintrag.get("status"))
        .isEqualTo("DONE");
  }

  /**
   * Der ausgelieferte Zustand (Plan #825 E6): ohne das Backup-Overlay existiert der Wachhund gar
   * nicht — kein Lauf, kein Alarm, und niemand bekommt Post über eine Sicherung, die er nie
   * eingerichtet hat.
   */
  @Nested
  @TestPropertySource(properties = "manban.backup.enabled=false")
  class OhneEingeschalteteSicherung {

    @Autowired private ObjectProvider<BackupWatchdogJob> abgeschalteterWachhund;
    @Autowired private JdbcTemplate abgeschaltetesJdbc;

    @Test
    void ohneEingeschalteteSicherungGibtEsDenWachhundNicht() {
      admin("a@example.org");

      assertThat(abgeschalteterWachhund.getIfAvailable()).isNull();
      assertThat(abgeschaltetesJdbc.queryForList("SELECT * FROM outbox_entry")).isEmpty();
    }
  }
}
