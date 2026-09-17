package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.CardTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.NightTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.PeriodTotals;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lesezugriffe der Verbrauchs-Auswertung gegen Postgres (Issue #937, Plan #933).
 *
 * <p>Die Gruppierung zu Nächten rechnet SQL mit {@code AT TIME ZONE}; die Grenzen der Spanne
 * rechnet Java in {@code NightRunPeriod}. Beide tragen die Tagesgrenze 12:00 (Issue #969), und die
 * Sommerzeit-Fälle hier nutzen dieselben Spannen wie dessen Einheitentest — sonst liefen die beiden
 * Rechenorte unbemerkt auseinander (Plan E4, H2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NightRunUsageRepositoryIT extends AbstractIntegrationTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
  private static final Instant ANGELEGT = Instant.parse("2026-09-20T06:00:00Z");

  /** Nacht vom 15. auf den 16.09.2026: 12:00 CEST am 15. bis 12:00 CEST am 16. */
  private static final Instant NACHT_15_VON = Instant.parse("2026-09-15T10:00:00Z");

  private static final Instant NACHT_15_BIS = Instant.parse("2026-09-16T10:00:00Z");

  @Autowired private NightRunRepository runs;
  @Autowired private NightRunUsageRepository usage;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;
  private long fremdesProjekt;

  @BeforeEach
  void seed() {
    projectId = projekt("u1@example.com");
    fremdesProjekt = projekt("u2@example.com");
  }

  private long projekt(String email) {
    Long userId =
        jdbc.queryForObject(
            "INSERT INTO app_user (email, password_hash, display_name) VALUES (?, 'x', 'U')"
                + " RETURNING id",
            Long.class,
            email);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id",
            Long.class,
            userId);
    return id == null ? 0L : id;
  }

  private static NightRunUsage kosten(String betrag) {
    return new NightRunUsage(new BigDecimal(betrag), null, null, null);
  }

  private void lauf(
      long projekt,
      String startedAt,
      NightRunMode mode,
      long durationMs,
      @Nullable NightRunUsage laufVerbrauch,
      NightRunItem... pakete) {
    runs.insertIfAbsent(
        new NightRun(
            null,
            projekt,
            Instant.parse(startedAt),
            mode,
            NightRunKind.NIGHT,
            durationMs,
            pakete.length,
            0,
            0,
            null,
            ANGELEGT,
            NightRunOrigin.TOKEN,
            "t",
            true,
            null,
            laufVerbrauch),
        List.of(pakete));
  }

  private static NightRunItem paket(
      int cardNumber,
      NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable Long durationMs,
      @Nullable NightRunUsage verbrauch) {
    return new NightRunItem(
        null,
        null,
        0L,
        Instant.EPOCH,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        cardNumber,
        "Paket " + cardNumber,
        state,
        errorClass,
        durationMs,
        null,
        null,
        verbrauch);
  }

  private static NightRunItem gruen(int cardNumber, @Nullable NightRunUsage verbrauch) {
    return paket(cardNumber, NightRunState.GREEN, null, 60_000L, verbrauch);
  }

  // --- Eine Nacht aus mehreren Laeufen ----------------------------------------------------------

  /**
   * 23:10 und 03:22 des Folgetages bilden eine Nacht; 12:00 des Folgetages beginnt die naechste.
   */
  @Test
  void zweiLaeufeUeberMitternachtBildenEineNacht_zwoelfUhrBeginntDieNaechste() {
    lauf(projectId, "2026-09-15T21:10:00Z", NightRunMode.IMPLEMENTATION, 1_000L, null);
    lauf(projectId, "2026-09-16T01:22:00Z", NightRunMode.REVIEW, 2_000L, null);
    lauf(projectId, "2026-09-16T10:00:00Z", NightRunMode.CHAIN, 4_000L, null);

    assertThat(
            usage.totalsPerNight(
                projectId, NACHT_15_VON, Instant.parse("2026-09-17T10:00:00Z"), BERLIN))
        .extracting(NightTotals::night, NightTotals::runCount, NightTotals::durationMs)
        .containsExactly(
            tuple(LocalDate.of(2026, 9, 15), 2L, 3_000L),
            tuple(LocalDate.of(2026, 9, 16), 1L, 4_000L));
  }

  @Test
  void eineKarteInBeidenLaeufenDerNachtZaehltEinmal() {
    lauf(
        projectId,
        "2026-09-15T21:10:00Z",
        NightRunMode.IMPLEMENTATION,
        1_000L,
        null,
        gruen(721, null),
        gruen(722, null));
    lauf(projectId, "2026-09-16T01:22:00Z", NightRunMode.REVIEW, 2_000L, null, gruen(721, null));

    assertThat(usage.totalsPerNight(projectId, NACHT_15_VON, NACHT_15_BIS, BERLIN))
        .singleElement()
        .returns(2L, NightTotals::cardCount);
    assertThat(usage.totals(projectId, NACHT_15_VON, NACHT_15_BIS))
        .returns(2L, PeriodTotals::runCount)
        .returns(2L, PeriodTotals::cardCount);
  }

  /** Die Lauf-Summe liegt ueber der Summe der Pakete; beide kommen getrennt (Plan E6, AK 2). */
  @Test
  void laufSummenUndPaketSummenKommenGetrennt() {
    lauf(
        projectId,
        "2026-09-15T21:10:00Z",
        NightRunMode.CHAIN,
        1_000L,
        new NightRunUsage(new BigDecimal("10.000000"), 1_000L, 100L, 900L),
        gruen(721, new NightRunUsage(new BigDecimal("4.000000"), 400L, 40L, 360L)));
    lauf(
        projectId,
        "2026-09-16T01:22:00Z",
        NightRunMode.REVIEW,
        1_000L,
        kosten("2.500000"),
        gruen(722, kosten("1.000000")));

    PeriodTotals summe = usage.totals(projectId, NACHT_15_VON, NACHT_15_BIS);
    assertThat(summe.runUsage().costUsd()).isEqualByComparingTo("12.5");
    assertThat(summe.runUsage().inputTokens()).isEqualTo(1_000L);
    assertThat(summe.itemUsage().costUsd()).isEqualByComparingTo("5");
    assertThat(summe.itemUsage().cachedInputTokens()).isEqualTo(360L);

    NightTotals nacht =
        usage.totalsPerNight(projectId, NACHT_15_VON, NACHT_15_BIS, BERLIN).getFirst();
    assertThat(nacht.runUsage().costUsd()).isEqualByComparingTo("12.5");
    assertThat(nacht.itemUsage().costUsd()).isEqualByComparingTo("5");
    assertThat(nacht.itemUsage().outputTokens()).isEqualTo(40L);
  }

  @Test
  void summenJeKarteUeberAlleAnlaeufeDerSpanne() {
    lauf(
        projectId,
        "2026-09-15T21:10:00Z",
        NightRunMode.IMPLEMENTATION,
        1_000L,
        null,
        paket(721, NightRunState.RED, NightRunErrorClass.CHECKS_RED, 60_000L, kosten("1.25")),
        paket(722, NightRunState.GREY, NightRunErrorClass.DEPENDENCY_UNMET, null, null));
    lauf(
        projectId,
        "2026-09-16T01:22:00Z",
        NightRunMode.REVIEW,
        1_000L,
        null,
        paket(721, NightRunState.GREEN, null, 30_000L, kosten("0.75")));

    assertThat(usage.totalsPerCard(projectId, NACHT_15_VON, NACHT_15_BIS))
        .extracting(CardTotals::cardNumber, CardTotals::attemptCount, CardTotals::durationMs)
        .containsExactly(tuple(721, 2L, 90_000L), tuple(722, 1L, null));
    CardTotals karte = usage.totalsPerCard(projectId, NACHT_15_VON, NACHT_15_BIS).getFirst();
    assertThat(karte.usage().costUsd()).isEqualByComparingTo("2.00");
    assertThat(usage.totalsPerCard(projectId, NACHT_15_VON, NACHT_15_BIS).get(1).usage().costUsd())
        .isNull();
  }

  // --- Sommerzeit ------------------------------------------------------------------------------

  /**
   * Nacht vom 28. auf den 29.03.2026 ueber die Umstellung auf Sommerzeit — dieselbe Spanne wie in
   * Issue #969: 12:00 CET = 11:00Z bis 12:00 CEST = 10:00Z.
   */
  @Test
  void dieGruppierungZuNaechtenStimmtUeberDieMaerzUmstellung() {
    Instant von = Instant.parse("2026-03-28T11:00:00Z");
    Instant bis = Instant.parse("2026-03-29T10:00:00Z");
    lauf(projectId, "2026-03-28T22:30:00Z", NightRunMode.IMPLEMENTATION, 1L, null); // 23:30 CET
    lauf(projectId, "2026-03-29T01:30:00Z", NightRunMode.REVIEW, 2L, null); // 03:30 CEST
    lauf(projectId, "2026-03-29T09:59:00Z", NightRunMode.CHAIN, 4L, null); // 11:59 CEST
    lauf(projectId, "2026-03-29T10:00:00Z", NightRunMode.CHAIN, 8L, null); // 12:00 CEST

    assertThat(usage.totalsPerNight(projectId, von, bis, BERLIN))
        .extracting(NightTotals::night, NightTotals::runCount, NightTotals::durationMs)
        .containsExactly(tuple(LocalDate.of(2026, 3, 28), 3L, 7L));
    assertThat(usage.totalsPerNight(projectId, von, Instant.parse("2026-03-30T10:00:00Z"), BERLIN))
        .extracting(NightTotals::night)
        .containsExactly(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 29));
  }

  /** Nacht vom 24. auf den 25.10.2026 ueber die Umstellung auf Winterzeit: 10:00Z bis 11:00Z. */
  @Test
  void dieGruppierungZuNaechtenStimmtUeberDieOktoberUmstellung() {
    Instant von = Instant.parse("2026-10-24T10:00:00Z");
    Instant bis = Instant.parse("2026-10-25T11:00:00Z");
    lauf(projectId, "2026-10-24T21:00:00Z", NightRunMode.IMPLEMENTATION, 1L, null); // 23:00 CEST
    lauf(projectId, "2026-10-25T10:30:00Z", NightRunMode.REVIEW, 2L, null); // 11:30 CET
    lauf(projectId, "2026-10-25T11:00:00Z", NightRunMode.CHAIN, 4L, null); // 12:00 CET

    assertThat(usage.totalsPerNight(projectId, von, bis, BERLIN))
        .extracting(NightTotals::night, NightTotals::runCount)
        .containsExactly(tuple(LocalDate.of(2026, 10, 24), 2L));
  }

  // --- Abbruch ---------------------------------------------------------------------------------

  @Test
  void dieFehlerklassenEinerNachtKommenMit_ohneAbbruchKeine() {
    lauf(
        projectId,
        "2026-09-15T21:10:00Z",
        NightRunMode.IMPLEMENTATION,
        1L,
        null,
        paket(721, NightRunState.RED, NightRunErrorClass.HARD_ABORT, null, null),
        paket(722, NightRunState.RED, NightRunErrorClass.CHECKS_RED, null, null));
    lauf(
        projectId,
        "2026-09-16T21:10:00Z",
        NightRunMode.CHAIN,
        1L,
        null,
        paket(723, NightRunState.RED, NightRunErrorClass.TIME_BUDGET_EXCEEDED, null, null));
    lauf(projectId, "2026-09-17T21:10:00Z", NightRunMode.CHAIN, 1L, null, gruen(724, null));

    assertThat(
            usage.totalsPerNight(
                projectId, NACHT_15_VON, Instant.parse("2026-09-18T10:00:00Z"), BERLIN))
        .extracting(NightTotals::night, NightTotals::errorClasses)
        .containsExactly(
            tuple(
                LocalDate.of(2026, 9, 15),
                Set.of(NightRunErrorClass.HARD_ABORT, NightRunErrorClass.CHECKS_RED)),
            tuple(LocalDate.of(2026, 9, 16), Set.of(NightRunErrorClass.TIME_BUDGET_EXCEEDED)),
            tuple(LocalDate.of(2026, 9, 17), Set.of()));
  }

  // --- Aeltester Lauf, leere Spanne, fremdes Projekt -------------------------------------------

  @Test
  void derAeltesteAufbewahrteLaufWirdGeliefert() {
    lauf(projectId, "2026-09-16T01:22:00Z", NightRunMode.REVIEW, 1L, null);
    lauf(projectId, "2026-09-10T21:00:00Z", NightRunMode.IMPLEMENTATION, 1L, null);

    assertThat(usage.oldestRetainedRunStart(projectId))
        .contains(Instant.parse("2026-09-10T21:00:00Z"));
  }

  @Test
  void einProjektOhneLaeufeHatKeinenAeltestenLauf() {
    assertThat(usage.oldestRetainedRunStart(projectId)).isEmpty();
  }

  /** Leere Summen, nicht Nullen an Verbrauchsstellen (Plan E5). */
  @Test
  void eineSpanneOhneLaeufeLiefertLeereSummen() {
    lauf(
        projectId,
        "2026-09-20T21:10:00Z",
        NightRunMode.CHAIN,
        1L,
        kosten("1"),
        gruen(1, kosten("1")));

    PeriodTotals summe = usage.totals(projectId, NACHT_15_VON, NACHT_15_BIS);

    assertThat(summe.runCount()).isZero();
    assertThat(summe.cardCount()).isZero();
    assertThat(summe.durationMs()).isZero();
    assertThat(summe.runUsage()).isEqualTo(new NightRunUsage(null, null, null, null));
    assertThat(summe.itemUsage()).isEqualTo(new NightRunUsage(null, null, null, null));
    assertThat(usage.totalsPerNight(projectId, NACHT_15_VON, NACHT_15_BIS, BERLIN)).isEmpty();
    assertThat(usage.totalsPerCard(projectId, NACHT_15_VON, NACHT_15_BIS)).isEmpty();
  }

  @Test
  void laeufeEinesFremdenProjektsFallenAusAllenVierZugriffen() {
    lauf(
        fremdesProjekt,
        "2026-09-15T21:10:00Z",
        NightRunMode.CHAIN,
        1L,
        kosten("9"),
        gruen(721, kosten("9")));

    assertThat(usage.totalsPerNight(projectId, NACHT_15_VON, NACHT_15_BIS, BERLIN)).isEmpty();
    assertThat(usage.totalsPerCard(projectId, NACHT_15_VON, NACHT_15_BIS)).isEmpty();
    assertThat(usage.totals(projectId, NACHT_15_VON, NACHT_15_BIS).runCount()).isZero();
    assertThat(usage.oldestRetainedRunStart(projectId)).isEmpty();
  }

  /** Die Grenzen der Spanne: Beginn eingeschlossen, Ende ausgeschlossen. */
  @Test
  void dieSpanneSchliesstDenBeginnEinUndDasEndeAus() {
    lauf(projectId, "2026-09-15T10:00:00Z", NightRunMode.CHAIN, 1L, null);
    lauf(projectId, "2026-09-16T10:00:00Z", NightRunMode.CHAIN, 2L, null);

    assertThat(usage.totals(projectId, NACHT_15_VON, NACHT_15_BIS))
        .returns(1L, PeriodTotals::runCount)
        .returns(1L, PeriodTotals::durationMs);
  }

  /** Ein Paket, dessen Lauf verdraengt wurde (#964), gehoert zu keiner gezeigten Nacht mehr. */
  @Test
  void verwaistePaketeZaehlenNicht() {
    lauf(projectId, "2026-09-15T21:10:00Z", NightRunMode.CHAIN, 1L, null, gruen(721, kosten("3")));
    jdbc.update("DELETE FROM night_run WHERE project_id = ?", projectId);

    assertThat(usage.totalsPerCard(projectId, NACHT_15_VON, NACHT_15_BIS)).isEmpty();
    assertThat(usage.totals(projectId, NACHT_15_VON, NACHT_15_BIS).itemUsage().costUsd()).isNull();
  }
}
