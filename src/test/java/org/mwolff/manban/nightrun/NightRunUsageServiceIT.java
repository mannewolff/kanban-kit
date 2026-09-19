package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.application.NightRunUsageService;
import org.mwolff.manban.nightrun.application.NightRunUsageService.NightUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.PeriodUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.TotalUsageView;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunPeriod;
import org.mwolff.manban.nightrun.domain.NightRunPeriodType;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Verbrauchs-Auswertung mit echter Datenbank, echter Rechteprüfung und echter card-Fassade
 * (Issue #938) — für die Fälle, die ein Doppelgänger nicht belegen kann: dass der Plattform-Admin
 * durchkommt, dass zwei Läufe einer Nacht über SQL und Service zusammen eine Nacht ergeben, und
 * dass eine Kartennummer ohne Karte als „ohne Vorhaben" in der Summe bleibt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NightRunUsageServiceIT extends AbstractIntegrationTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
  private static final LocalDate NACHT_15 = LocalDate.of(2026, 9, 15);

  @Autowired private NightRunUsageService service;
  @Autowired private NightRunRepository runs;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private long owner;
  private long mitglied;
  private long admin;
  private long fremder;
  private long projectId;

  @BeforeEach
  void seed() {
    owner = nutzer("nu-owner@example.com", "USER");
    mitglied = nutzer("nu-member@example.com", "USER");
    admin = nutzer("nu-admin@example.com", "ADMIN");
    fremder = nutzer("nu-fremd@example.com", "USER");
    projectId = id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", owner);
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, 'OWNER')",
        projectId,
        owner);
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, 'MEMBER')",
        projectId,
        mitglied);
  }

  private long nutzer(String email, String rolle) {
    return id(
        "INSERT INTO app_user (email, password_hash, display_name, platform_role)"
            + " VALUES (?, 'x', 'U', ?) RETURNING id",
        email,
        rolle);
  }

  private long id(String sql, Object... args) {
    Long id = jdbc.queryForObject(sql, Long.class, args);
    return id == null ? 0L : id;
  }

  private void lauf(Instant startedAt, NightRunMode mode, String kosten, NightRunItem... pakete) {
    eintrag(startedAt, mode, NightRunKind.NIGHT, kosten, pakete);
  }

  /** Eine interaktive Sitzung (Issue #1013); ihre Pakete erben die Gattung aus dem Lauf. */
  private void sitzung(Instant startedAt, String kosten, NightRunItem... pakete) {
    eintrag(startedAt, NightRunMode.INTERACTIVE, NightRunKind.INTERACTIVE, kosten, pakete);
  }

  private void eintrag(
      Instant startedAt,
      NightRunMode mode,
      NightRunKind kind,
      String kosten,
      NightRunItem... pakete) {
    runs.insertIfAbsent(
        new NightRun(
            null,
            projectId,
            startedAt,
            mode,
            kind,
            1_000L,
            pakete.length,
            0,
            0,
            null,
            startedAt,
            NightRunOrigin.TOKEN,
            "t",
            true,
            null,
            new NightRunUsage(new BigDecimal(kosten), null, null, null),
            null),
        List.of(pakete));
  }

  private static NightRunItem paket(int cardNumber, String kosten) {
    return new NightRunItem(
        null,
        null,
        0L,
        Instant.EPOCH,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        cardNumber,
        "Paket",
        NightRunState.GREEN,
        null,
        1_000L,
        null,
        null,
        new NightRunUsage(new BigDecimal(kosten), null, null, null));
  }

  @Test
  void zweiLaeufeEinerNachtErgebenEineNacht_undEineKarteAusBeidenZaehltEinmal() {
    lauf(
        Instant.parse("2026-09-15T21:10:00Z"),
        NightRunMode.IMPLEMENTATION,
        "6",
        paket(721, "2"),
        paket(722, "1"));
    lauf(Instant.parse("2026-09-16T01:22:00Z"), NightRunMode.REVIEW, "4", paket(721, "2"));

    NightUsageView nacht = service.night(owner, projectId, NACHT_15, BERLIN);

    assertThat(nacht.runCount()).isEqualTo(2L);
    assertThat(nacht.cardCount()).isEqualTo(2L);
    assertThat(nacht.usage().total().costUsd()).isEqualByComparingTo("10");
    assertThat(nacht.usage().cardShare().costUsd()).isEqualByComparingTo("5");
    assertThat(nacht.usage().remainder().costUsd()).isEqualByComparingTo("5");
    assertThat(nacht.cards())
        .extracting(c -> c.cardNumber(), c -> c.attemptCount())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(721, 2L),
            org.assertj.core.groups.Tuple.tuple(722, 1L));
  }

  /** Karten, die es im Projekt nicht gibt, bleiben als „ohne Vorhaben" in der Summe (Plan E12). */
  @Test
  void kartennummernOhneKarteZaehlenAlsOhneVorhaben() {
    NightRunPeriod monat = NightRunPeriod.of(NightRunPeriodType.MONTH, BERLIN, clock, 0);
    lauf(
        monat.from().plusSeconds(3_600),
        NightRunMode.CHAIN,
        "9",
        paket(4711, "3"),
        paket(4712, "4"));

    PeriodUsageView auswertung =
        service.period(owner, projectId, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(auswertung.epics()).isEmpty();
    assertThat(auswertung.withoutEpic().cardCount()).isEqualTo(2L);
    assertThat(auswertung.withoutEpic().usage().costUsd()).isEqualByComparingTo("7");
    assertThat(auswertung.current().usage().total().costUsd()).isEqualByComparingTo("9");
  }

  /**
   * Über die ganze Kette — SQL, Service, Gattungen: Die Gesamtsumme ist genau die Addition beider
   * Anteile, und kein Eintrag steht in beiden (Issue #1013, #984 AK 5).
   */
  @Test
  void nachtlaeufeUndSitzungenStehenGetrennt_undDieSummeIstIhreAddition() {
    lauf(
        Instant.parse("2026-09-15T21:10:00Z"),
        NightRunMode.IMPLEMENTATION,
        "6",
        paket(721, "2"),
        paket(722, "1"));
    sitzung(Instant.parse("2026-09-16T01:22:00Z"), "4", paket(721, "3"));

    NightUsageView nacht = service.night(owner, projectId, NACHT_15, BERLIN);

    assertThat(nacht.runCount()).isEqualTo(2L);
    assertThat(nacht.usageByKind().night().total().costUsd()).isEqualByComparingTo("6");
    assertThat(nacht.usageByKind().night().cardShare().costUsd()).isEqualByComparingTo("3");
    assertThat(nacht.usageByKind().night().remainder().costUsd()).isEqualByComparingTo("3");
    assertThat(nacht.usageByKind().interactive().total().costUsd()).isEqualByComparingTo("4");
    assertThat(nacht.usageByKind().interactive().cardShare().costUsd()).isEqualByComparingTo("3");
    assertThat(nacht.usageByKind().interactive().remainder().costUsd()).isEqualByComparingTo("1");
    assertThat(nacht.usage().total().costUsd()).isEqualByComparingTo("10");
    assertThat(nacht.cards())
        .filteredOn(c -> c.cardNumber() == 721)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.nightUsage().costUsd()).isEqualByComparingTo("2");
              assertThat(c.interactiveUsage().costUsd()).isEqualByComparingTo("3");
            });
  }

  /** Der Erfassungsbeginn kommt aus dem Projekt-Aggregat (Issue #1012, Plan E18). */
  @Test
  void derErfassungsbeginnDesProjektsKommtMitDenKennzahlen() {
    Instant seit = Instant.parse("2026-08-14T07:00:00Z");
    jdbc.update(
        "UPDATE project SET interactive_usage_since = ? WHERE id = ?",
        seit.atOffset(ZoneOffset.UTC),
        projectId);

    PeriodUsageView auswertung =
        service.period(owner, projectId, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(auswertung.current().interactiveUsageSince()).isEqualTo(seit);
  }

  @Test
  void ohneGemeldeteSitzungBleibtDerErfassungsbeginnLeer() {
    PeriodUsageView auswertung =
        service.period(owner, projectId, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(auswertung.current().interactiveUsageSince()).isNull();
  }

  // --- Die Lebenszeit (Issue #1014) ------------------------------------------------------------

  /**
   * Über die ganze Kette — SQL, Service, Gattungen: Die Lebenszeit-Summe nimmt Einträge aus
   * verschiedenen Monaten mit, ist genau die Addition beider Anteile und stimmt mit der Summe über
   * alle Zeilen der Tabelle überein (#984 AK 4).
   */
  @Test
  void dieLebenszeitSummeIstDieAdditionBeiderAnteile_undStimmtMitDerTabelleUeberein() {
    lauf(
        Instant.parse("2026-03-02T21:10:00Z"),
        NightRunMode.IMPLEMENTATION,
        "6",
        paket(721, "2"),
        paket(722, "1"));
    sitzung(Instant.parse("2026-09-16T01:22:00Z"), "4", paket(721, "3"));

    TotalUsageView gesamt = service.total(owner, projectId);

    assertThat(gesamt.nightRunCount()).isEqualTo(1L);
    assertThat(gesamt.interactiveRunCount()).isEqualTo(1L);
    assertThat(gesamt.runCount()).isEqualTo(2L);
    assertThat(gesamt.cardCount()).isEqualTo(2L);
    assertThat(gesamt.usageByKind().night().total().costUsd()).isEqualByComparingTo("6");
    assertThat(gesamt.usageByKind().interactive().total().costUsd()).isEqualByComparingTo("4");
    assertThat(gesamt.usage().total().costUsd()).isEqualByComparingTo("10");
    assertThat(gesamt.usage().cardShare().costUsd()).isEqualByComparingTo("6");
    assertThat(gesamt.usage().remainder().costUsd()).isEqualByComparingTo("4");
    assertThat(gesamt.usage().total().costUsd())
        .isEqualByComparingTo(
            jdbc.queryForObject(
                "SELECT sum(cost_usd) FROM night_run WHERE project_id = ?",
                BigDecimal.class,
                projectId));
  }

  /** Beide Lückenangaben kommen mit: die Verdrängung und der Erfassungsbeginn (Plan E8, E18). */
  @Test
  void dieLebenszeitSummeTraegtDenAeltestenEintragUndDenErfassungsbeginn() {
    Instant seit = Instant.parse("2026-08-14T07:00:00Z");
    jdbc.update(
        "UPDATE project SET interactive_usage_since = ? WHERE id = ?",
        seit.atOffset(ZoneOffset.UTC),
        projectId);
    lauf(Instant.parse("2026-09-15T21:10:00Z"), NightRunMode.CHAIN, "1");
    sitzung(Instant.parse("2026-09-10T08:00:00Z"), "2");

    TotalUsageView gesamt = service.total(owner, projectId);

    assertThat(gesamt.oldestRetainedRunStart()).isEqualTo(Instant.parse("2026-09-10T08:00:00Z"));
    assertThat(gesamt.interactiveUsageSince()).isEqualTo(seit);
  }

  /** Kein Lauf heißt „nicht gemessen" und nie 0 an den Verbrauchsstellen (Plan E5). */
  @Test
  void ohneEinenEinzigenLaufBleibenVerbrauchsangabenLeerUndZaehlerNull() {
    TotalUsageView gesamt = service.total(owner, projectId);

    assertThat(gesamt.runCount()).isZero();
    assertThat(gesamt.nightRunCount()).isZero();
    assertThat(gesamt.interactiveRunCount()).isZero();
    assertThat(gesamt.cardCount()).isZero();
    assertThat(gesamt.usage().total().costUsd()).isNull();
    assertThat(gesamt.usage().cardShare().costUsd()).isNull();
    assertThat(gesamt.usage().remainder().costUsd()).isNull();
    assertThat(gesamt.usageByKind().night().total().costUsd()).isNull();
    assertThat(gesamt.usageByKind().interactive().total().costUsd()).isNull();
    assertThat(gesamt.oldestRetainedRunStart()).isNull();
    assertThat(gesamt.interactiveUsageSince()).isNull();
  }

  @Test
  void einMitgliedOhneOwnerRolleWirdAbgewiesen() {
    assertThatThrownBy(() -> service.night(mitglied, projectId, NACHT_15, BERLIN))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThatThrownBy(() -> service.period(mitglied, projectId, NightRunPeriodType.DAY, 0, BERLIN))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThatThrownBy(() -> service.total(mitglied, projectId))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }

  @Test
  void einFremderSiehtDasProjektNicht() {
    assertThatThrownBy(() -> service.night(fremder, projectId, NACHT_15, BERLIN))
        .isInstanceOf(ProjectNotFoundException.class);
    assertThatThrownBy(() -> service.period(fremder, projectId, NightRunPeriodType.DAY, 0, BERLIN))
        .isInstanceOf(ProjectNotFoundException.class);
    assertThatThrownBy(() -> service.total(fremder, projectId))
        .isInstanceOf(ProjectNotFoundException.class);
  }

  /** Der Plattform-Admin kommt ohne Mitgliedschaft durch (Plan E17). */
  @Test
  void derPlattformAdminKommtDurch() {
    lauf(Instant.parse("2026-09-15T21:10:00Z"), NightRunMode.CHAIN, "1", paket(721, "1"));

    assertThat(service.night(admin, projectId, NACHT_15, BERLIN).runCount()).isEqualTo(1L);
    assertThat(service.period(admin, projectId, NightRunPeriodType.WEEK, 0, BERLIN)).isNotNull();
    assertThat(service.total(admin, projectId).runCount()).isEqualTo(1L);
  }
}
