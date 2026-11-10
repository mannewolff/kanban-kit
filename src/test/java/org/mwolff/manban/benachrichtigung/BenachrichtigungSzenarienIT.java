package org.mwolff.manban.benachrichtigung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungenRepository;
import org.mwolff.manban.benachrichtigung.domain.Ruhezeit;
import org.mwolff.manban.benachrichtigung.infrastructure.BenachrichtigungJob;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.outbox.application.OutboxDispatchService;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Die Kette vom Lauf bis zur Mail gegen Postgres (Issue #2081, Plan #2070): Takt des Jobs, Outbox
 * und Mail-Handler laufen echt, nur der {@link JavaMailSender} ist ein Stub.
 *
 * <p>Der Takt steht auf einer Stunde, damit der geplante Job dieses Kontexts nie neben dem Test
 * läuft; aufgerufen wird er von Hand. Die stellbare Uhr gilt für den ganzen Kontext und wird nicht
 * zurückgestellt, darum rechnet jeder Test von {@code clock.instant()} aus.
 */
// PMD.ExcessiveImports: Der Test verbindet die ganze Kette; seine Typen stammen aus allen Modulen,
// die sie durchläuft.
@SuppressWarnings("PMD.ExcessiveImports")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({StellbareUhrConfig.class, BenachrichtigungSzenarienIT.MailStub.class})
@TestPropertySource(
    properties = {
      "manban.mail.enabled=true",
      "manban.benachrichtigung.takt=PT1H",
      // Eigener Spring-Kontext: Der Pool bleibt klein, wie in MailOutboxIT.
      "spring.datasource.hikari.maximum-pool-size=3",
      "spring.datasource.hikari.minimum-idle=1"
    })
class BenachrichtigungSzenarienIT extends AbstractIntegrationTest {

  private static final String EMPFAENGER = "owner@example.com";
  private static final String PROJEKT_NAME = "Leitstand";
  private static final int KARTEN_NUMMER = 7;
  private static final String KARTEN_TITEL = "Frage klären";

  @TestConfiguration
  static class MailStub {

    @Bean
    @Primary
    JavaMailSender mailStub() {
      return Mockito.mock(JavaMailSender.class);
    }
  }

  @Autowired private BenachrichtigungJob job;
  @Autowired private OutboxDispatchService outbox;
  @Autowired private BenachrichtigungEinstellungenRepository einstellungen;
  @Autowired private NightRunRepository runs;
  @Autowired private MutableClock clock;
  @Autowired private JavaMailSender mailSender;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  private long person;
  private long projekt;

  @BeforeEach
  void legeBestandAn() {
    Mockito.reset(mailSender);
    person =
        id(
            "INSERT INTO app_user (email, display_name, approved_at) VALUES (?, 'Owner', now())"
                + " RETURNING id",
            EMPFAENGER);
    projekt =
        id(
            "INSERT INTO project (name, owner_user_id) VALUES (?, ?) RETURNING id",
            PROJEKT_NAME,
            person);
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, 'OWNER')",
        projekt,
        person);
    long board = id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projekt);
    long spalte =
        id(
            "INSERT INTO board_column (board_id, name, position) VALUES (?, 'Ready', 0)"
                + " RETURNING id",
            board);
    jdbc.update(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column)"
            + " VALUES (?, ?, ?, ?, 0)",
        board,
        spalte,
        KARTEN_NUMMER,
        KARTEN_TITEL);
    Instant seit = clock.instant().minus(Duration.ofHours(1));
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              einstellungen.schalteProjektEin(projekt, seit);
              einstellungen.schaltePersonEin(person, projekt, seit);
            });
  }

  @Test
  @DisplayName("Szenario: Ein Lauf wartet auf den Menschen")
  void einLaufWartetAufDenMenschen() {
    lauf(false, null, List.of(wartendesPaket()));

    takt();
    takt();

    assertThat(mails()).hasSize(1);
    SimpleMailMessage mail = mails().getFirst();
    assertThat(mail.getTo()).containsExactly(EMPFAENGER);
    assertThat(mail.getSubject()).contains(PROJEKT_NAME, "Lauf wartet auf dich");
    assertThat(mail.getText())
        .contains(
            "Projekt: " + PROJEKT_NAME,
            "Karte: #" + KARTEN_NUMMER + " " + KARTEN_TITEL,
            "Nächster Schritt: Triff die offene Entscheidung im Leitstand.");
  }

  @Test
  @DisplayName("Szenario: Ruhezeit")
  void ruhezeit() {
    Instant jetzt = clock.instant();
    Duration halbeStunde = Duration.ofMinutes(30);
    Ruhezeit ruhezeit =
        new Ruhezeit(
            uhrzeit(jetzt.minus(halbeStunde)), uhrzeit(jetzt.plus(halbeStunde)), ZoneOffset.UTC);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> einstellungen.speichereRuhezeit(person, ruhezeit));
    lauf(false, null, List.of(wartendesPaket()));

    takt();

    assertThat(mails()).isEmpty();

    clock.advance(halbeStunde.plusMinutes(1));
    takt();

    assertThat(mails()).hasSize(1);
    assertThat(mails().getFirst().getSubject()).contains("während deiner Ruhezeit");
    assertThat(mails().getFirst().getText())
        .contains("Karte: #" + KARTEN_NUMMER + " " + KARTEN_TITEL);
  }

  @Test
  @DisplayName("Szenario: Übersicht nach dem Lauf")
  void uebersichtNachDemLauf() {
    NightRunUsage kosten = new NightRunUsage(new BigDecimal("1.25"), null, null, null, null, null);
    lauf(true, kosten, List.of(gruenesPaket(), wartendesPaket()));

    takt();

    SimpleMailMessage uebersicht =
        mails().stream()
            .filter(mail -> mail.getSubject().contains("Übersicht zum Lauf"))
            .findFirst()
            .orElseThrow();
    assertThat(uebersicht.getTo()).containsExactly(EMPFAENGER);
    assertThat(uebersicht.getText())
        .contains(
            "Ausgang: ",
            "Wartende Menschenschritte:",
            "- #" + KARTEN_NUMMER + " " + KARTEN_TITEL,
            "Kosten: 1,25 USD");
  }

  private void takt() {
    job.run();
    outbox.dispatchDue();
  }

  private List<SimpleMailMessage> mails() {
    ArgumentCaptor<SimpleMailMessage> gesendet = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender, atLeast(0)).send(gesendet.capture());
    return gesendet.getAllValues();
  }

  private static LocalTime uhrzeit(Instant zeitpunkt) {
    return LocalTime.ofInstant(zeitpunkt, ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
  }

  private void lauf(boolean abgeschlossen, NightRunUsage usage, List<NightRunItem> pakete) {
    Instant jetzt = clock.instant();
    Instant start = jetzt.minus(Duration.ofMinutes(20)).truncatedTo(ChronoUnit.SECONDS);
    runs.upsert(
        new NightRun(
            null,
            projekt,
            start,
            NightRunMode.IMPLEMENTATION,
            NightRunKind.NIGHT,
            1000L,
            pakete.size(),
            0,
            0,
            null,
            start,
            NightRunOrigin.TOKEN,
            "runner-szenario",
            abgeschlossen,
            jetzt.minus(Duration.ofMinutes(1)),
            usage,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null),
        pakete);
  }

  private NightRunItem wartendesPaket() {
    return paket(
        KARTEN_NUMMER, KARTEN_TITEL, NightRunState.RED, NightRunErrorClass.AWAITING_DECISION);
  }

  private NightRunItem gruenesPaket() {
    return paket(KARTEN_NUMMER + 1, "Fertig", NightRunState.GREEN, null);
  }

  private NightRunItem paket(
      int nummer, String titel, NightRunState zustand, NightRunErrorClass fehlerklasse) {
    return new NightRunItem(
        null,
        null,
        projekt,
        clock.instant(),
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        nummer,
        titel,
        zustand,
        fehlerklasse,
        1000L,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of());
  }

  private long id(String sql, Object... argumente) {
    Long generated = jdbc.queryForObject(sql, Long.class, argumente);
    return generated == null ? 0L : generated;
  }
}
