package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.EpicRef;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.CardTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.KindTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.LifetimeTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.NightTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.PeriodTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.RetainedByKind;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.StageTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.TotalsByKind;
import org.mwolff.manban.nightrun.application.NightRunUsageService.Coverage;
import org.mwolff.manban.nightrun.application.NightRunUsageService.EpicUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.NightSummary;
import org.mwolff.manban.nightrun.application.NightRunUsageService.NightUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.PeriodUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.StageUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.TotalUsageView;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunPeriodType;
import org.mwolff.manban.nightrun.domain.NightRunStage;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.InteractiveUsageSinceReader;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;

/**
 * Verhaltenstests der Verbrauchs-Auswertung (Issue #938) gegen einen Doppelgänger des Ports und
 * eine gemockte card-Fassade. Die Summen selbst belegt {@code NightRunUsageRepositoryIT}; hier geht
 * es um das, was der Service daraus macht: Rest, Abbruch, Abdeckung, Vorhaben-Aufstellung.
 */
// Testklasse: Jede Methode ist ein Fall aus dem Akzeptanzkriterium, und die Importe folgen den
// abgebildeten Typen — seit Issue #1013 kommen die Gattungs-Records des Ports dazu, seit Issue
// #1071 die Aufbewahrungsgrenze samt ihrer Gattung.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.ExcessiveImports", "PMD.CouplingBetweenObjects"})
class NightRunUsageServiceTest {

  private static final long USER = 7L;
  private static final long PROJECT = 3L;
  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  /** Mi 16.09.2026 13:00 in Berlin: die zuletzt abgeschlossene Nacht ist die vom 15. auf den 16. */
  private static final Clock JETZT =
      Clock.fixed(Instant.parse("2026-09-16T11:00:00Z"), ZoneOffset.UTC);

  private static final LocalDate NACHT_15 = LocalDate.of(2026, 9, 15);
  private static final NightRunUsage NICHTS = new NightRunUsage(null, null, null, null, null, null);

  /** Eine Gattung, die in der Spanne nicht vorkommt: kein Eintrag, nichts gemessen. */
  private static final KindTotals LEER = new KindTotals(0L, NICHTS, NICHTS);

  /** Ringpuffer-Grenze 2 je Gattung, damit die Abdeckungsfälle mit wenigen Läufen auskommen. */
  private static final NightRunProperties PROPERTIES =
      new NightRunProperties(2, null, 2, null, null);

  private FakeUsage usage;
  private CardService cards;
  private PermissionChecker permissions;
  private InteractiveUsageSinceReader erfassungsbeginn;
  private NightRunUsageService service;

  @BeforeEach
  void setUp() {
    usage = new FakeUsage();
    cards = mock(CardService.class);
    permissions = mock(PermissionChecker.class);
    erfassungsbeginn = mock(InteractiveUsageSinceReader.class);
    when(erfassungsbeginn.interactiveUsageSince(anyLong())).thenReturn(Optional.empty());
    service =
        new NightRunUsageService(usage, cards, permissions, erfassungsbeginn, JETZT, PROPERTIES);
  }

  private static RetainedByKind voll(NightRunKind kind, String aeltester) {
    return new RetainedByKind(kind, 2L, Instant.parse(aeltester));
  }

  private static RetainedByKind nichtVoll(NightRunKind kind, @Nullable String aeltester) {
    return new RetainedByKind(kind, 1L, aeltester == null ? null : Instant.parse(aeltester));
  }

  private static NightRunUsage kosten(String betrag) {
    return new NightRunUsage(new BigDecimal(betrag), null, null, null, null, null);
  }

  /** Eine Spanne, deren Verbrauch ganz aus Nachtläufen stammt. */
  private static PeriodTotals summe(
      long runs, long karten, NightRunUsage lauf, NightRunUsage pakete) {
    return new PeriodTotals(
        1_000L * runs, karten, new TotalsByKind(new KindTotals(runs, lauf, pakete), LEER));
  }

  private static CardTotals karte(int nummer, @Nullable NightRunUsage verbrauch) {
    return new CardTotals(nummer, 1L, 60_000L, verbrauch == null ? NICHTS : verbrauch, NICHTS);
  }

  // --- Rechte ----------------------------------------------------------------------------------

  @Test
  void dieNachtVerlangtDenNachtlaufZugriff_undLiestOhneIhnNichts() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.night(USER, PROJECT, NACHT_15, BERLIN))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThat(usage.aufrufe).isEmpty();
    verifyNoInteractions(cards);
  }

  @Test
  void derZeitraumVerlangtDenNachtlaufZugriff_undLiestOhneIhnNichts() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThat(usage.aufrufe).isEmpty();
    verifyNoInteractions(cards);
  }

  @Test
  void beideUseCasesPruefenDenZugriffFuerDasFragendeProjekt() {
    service.night(USER, PROJECT, NACHT_15, BERLIN);
    service.period(USER, PROJECT, NightRunPeriodType.DAY, 0, BERLIN);

    verify(permissions, org.mockito.Mockito.times(2)).requireNightRunAccess(USER, PROJECT);
  }

  // --- Eine Nacht ------------------------------------------------------------------------------

  /** Die Nacht wird ueber die 12-Uhr-Spanne ihres Datums gelesen (Issue #969). */
  @Test
  void dieNachtLiestDieSpanneIhresDatums() {
    service.night(USER, PROJECT, NACHT_15, BERLIN);

    assertThat(usage.aufrufe)
        .contains(
            "totals 2026-09-15T10:00:00Z 2026-09-16T10:00:00Z",
            "perCard 2026-09-15T10:00:00Z 2026-09-16T10:00:00Z");
  }

  /** Kartenbezogener Anteil plus Rest ergibt die Gesamtsumme (AK 2, Plan E6). */
  @Test
  void kartenbezogenerAnteilPlusRestErgibtDieGesamtsumme() {
    usage.summe =
        summe(
            2,
            2,
            new NightRunUsage(new BigDecimal("10.00"), 1_000L, 100L, 900L, null, null),
            new NightRunUsage(new BigDecimal("4.00"), 400L, 40L, 360L, null, null));

    NightUsageView nacht = service.night(USER, PROJECT, NACHT_15, BERLIN);

    assertThat(nacht.usage().total().costUsd()).isEqualByComparingTo("10.00");
    assertThat(nacht.usage().cardShare().costUsd()).isEqualByComparingTo("4.00");
    assertThat(nacht.usage().remainder().costUsd()).isEqualByComparingTo("6.00");
    assertThat(nacht.usage().cardShare().plus(nacht.usage().remainder()))
        .isEqualTo(new NightRunUsage(new BigDecimal("10.00"), 1_000L, 100L, 900L, null, null));
  }

  /** Die Kartenzeilen sind genau die Summen dieser Nacht (AK 3, Plan E20). */
  @Test
  void dieKartenzeilenSindDieSummenDieserNacht() {
    CardTotals a = karte(721, kosten("1.50"));
    CardTotals b = karte(722, kosten("2.50"));
    usage.jeKarte = List.of(a, b);

    assertThat(service.night(USER, PROJECT, NACHT_15, BERLIN).cards()).containsExactly(a, b);
  }

  /**
   * Zwei Laeufe derselben Nacht, eine Karte aus beiden: die Zahlen des Ports, nicht neu gezaehlt.
   */
  @Test
  void zahlenDerNachtKommenAusDerTagesgruppe() {
    usage.summe = summe(2, 1, NICHTS, NICHTS);

    NightUsageView nacht = service.night(USER, PROJECT, NACHT_15, BERLIN);

    assertThat(nacht.night()).isEqualTo(NACHT_15);
    assertThat(nacht.runCount()).isEqualTo(2L);
    assertThat(nacht.cardCount()).isEqualTo(1L);
    assertThat(nacht.durationMs()).isEqualTo(2_000L);
  }

  @Test
  void eineNachtMitHardAbortGiltAlsAbgebrochen() {
    usage.naechte = List.of(nacht(NACHT_15, NightRunErrorClass.HARD_ABORT));

    assertThat(service.night(USER, PROJECT, NACHT_15, BERLIN).aborted()).isTrue();
  }

  @Test
  void eineNachtMitZeitbudgetAbbruchGiltAlsAbgebrochen() {
    usage.naechte = List.of(nacht(NACHT_15, NightRunErrorClass.TIME_BUDGET_EXCEEDED));

    assertThat(service.night(USER, PROJECT, NACHT_15, BERLIN).aborted()).isTrue();
  }

  @Test
  void eineNachtOhneAbbruchklasseGiltNichtAlsAbgebrochen() {
    usage.naechte = List.of(nacht(NACHT_15, NightRunErrorClass.CHECKS_RED));

    assertThat(service.night(USER, PROJECT, NACHT_15, BERLIN).aborted()).isFalse();
  }

  @Test
  void eineNachtOhneLaufIstNichtAbgebrochen() {
    assertThat(service.night(USER, PROJECT, NACHT_15, BERLIN).aborted()).isFalse();
  }

  /** Durchgaengig fehlende Verbrauchsangaben bleiben fehlend (Plan E5). */
  @Test
  void fehlendeVerbrauchsangabenBleibenFehlend() {
    usage.summe = summe(1, 1, NICHTS, NICHTS);

    NightUsageView nacht = service.night(USER, PROJECT, NACHT_15, BERLIN);

    assertThat(nacht.usage().total()).isEqualTo(NICHTS);
    assertThat(nacht.usage().cardShare()).isEqualTo(NICHTS);
    assertThat(nacht.usage().remainder()).isEqualTo(NICHTS);
  }

  private static NightTotals nacht(LocalDate datum, NightRunErrorClass... klassen) {
    return new NightTotals(
        datum,
        1_000L,
        1L,
        new TotalsByKind(new KindTotals(1L, NICHTS, NICHTS), LEER),
        Set.of(klassen));
  }

  // --- Gattungen (Issue #1013) -----------------------------------------------------------------

  /** Der Rest entsteht je Gattung als Differenz aus Lauf-Summe und Paket-Summe (#984 AK 3). */
  @Test
  void derRestEntstehtJeGattungAlsDifferenz_undDieGesamtsummeIstDieAddition() {
    usage.summe =
        new PeriodTotals(
            2_000L,
            2L,
            new TotalsByKind(
                new KindTotals(1L, kosten("10.00"), kosten("4.00")),
                new KindTotals(1L, kosten("2.50"), kosten("1.00"))));

    NightUsageView nacht = service.night(USER, PROJECT, NACHT_15, BERLIN);

    assertThat(nacht.usageByKind().night().total().costUsd()).isEqualByComparingTo("10.00");
    assertThat(nacht.usageByKind().night().cardShare().costUsd()).isEqualByComparingTo("4.00");
    assertThat(nacht.usageByKind().night().remainder().costUsd()).isEqualByComparingTo("6.00");
    assertThat(nacht.usageByKind().interactive().total().costUsd()).isEqualByComparingTo("2.50");
    assertThat(nacht.usageByKind().interactive().cardShare().costUsd())
        .isEqualByComparingTo("1.00");
    assertThat(nacht.usageByKind().interactive().remainder().costUsd())
        .isEqualByComparingTo("1.50");
    assertThat(nacht.usage().total().costUsd()).isEqualByComparingTo("12.50");
    assertThat(nacht.usage().remainder().costUsd()).isEqualByComparingTo("7.50");
    assertThat(nacht.runCount()).isEqualTo(2L);
  }

  /** Ohne Sitzung bleibt ihr Anteil fehlend und wird nicht 0 (Plan E5). */
  @Test
  void ohneSitzungBleibtDerSitzungsanteilFehlend() {
    usage.summe = summe(1, 1, kosten("5"), kosten("2"));

    NightUsageView nacht = service.night(USER, PROJECT, NACHT_15, BERLIN);

    assertThat(nacht.usageByKind().interactive().total()).isEqualTo(NICHTS);
    assertThat(nacht.usageByKind().interactive().cardShare()).isEqualTo(NICHTS);
    assertThat(nacht.usageByKind().interactive().remainder()).isEqualTo(NICHTS);
  }

  /** Eine Spanne allein aus Sitzungen ist nicht leer (#984 AK 1). */
  @Test
  void eineSpanneNurMitSitzungenIstNichtLeer() {
    usage.summeJeBeginn.put(
        Instant.parse("2026-08-01T10:00:00Z"),
        new PeriodTotals(
            1_000L, 1L, new TotalsByKind(LEER, new KindTotals(3L, kosten("9"), kosten("4")))));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().runCount()).isEqualTo(3L);
    assertThat(monat.current().noRuns()).isFalse();
    assertThat(monat.current().usage().total().costUsd()).isEqualByComparingTo("9");
    assertThat(monat.current().usageByKind().night().total()).isEqualTo(NICHTS);
    assertThat(monat.current().usageByKind().interactive().remainder().costUsd())
        .isEqualByComparingTo("5");
  }

  /**
   * Die Zahl der Einträge zerfällt in Läufe und Sitzungen (#984 AK 1): Eine Anzeige, die nur die
   * Summe kennt, schriebe „3 Läufe" über einen Zeitraum, in dem ein Lauf und zwei Sitzungen lagen.
   */
  @Test
  void derZeitraumFuehrtLaeufeUndSitzungenGetrennt() {
    usage.summeJeBeginn.put(
        Instant.parse("2026-08-01T10:00:00Z"),
        new PeriodTotals(
            1_000L,
            1L,
            new TotalsByKind(
                new KindTotals(1L, kosten("6"), kosten("2")),
                new KindTotals(2L, kosten("3"), kosten("1")))));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().nightRunCount()).isEqualTo(1L);
    assertThat(monat.current().interactiveRunCount()).isEqualTo(2L);
    assertThat(monat.current().runCount()).isEqualTo(3L);
  }

  /** Ohne Eintrag ist jede der beiden Zahlen 0 — und der Zeitraum leer. */
  @Test
  void ohneEintragSindBeideZahlenNull() {
    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().nightRunCount()).isZero();
    assertThat(monat.current().interactiveRunCount()).isZero();
    assertThat(monat.current().noRuns()).isTrue();
  }

  /** Die Nächte des Zeitraums tragen dieselbe Trennung. */
  @Test
  void dieNaechteDesZeitraumsTragenDieAufteilungNachGattung() {
    usage.naechte =
        List.of(
            new NightTotals(
                LocalDate.of(2026, 8, 3),
                1_000L,
                1L,
                new TotalsByKind(
                    new KindTotals(1L, kosten("6"), kosten("2")),
                    new KindTotals(2L, kosten("3"), kosten("1"))),
                Set.of()));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.nights()).singleElement().returns(3L, NightSummary::runCount);
    assertThat(monat.nights().getFirst().usageByKind().night().remainder().costUsd())
        .isEqualByComparingTo("4");
    assertThat(monat.nights().getFirst().usageByKind().interactive().total().costUsd())
        .isEqualByComparingTo("3");
    assertThat(monat.nights().getFirst().usage().total().costUsd()).isEqualByComparingTo("9");
    assertThat(monat.nights().getFirst().usage().cardShare().costUsd()).isEqualByComparingTo("3");
    assertThat(monat.nights().getFirst().usage().remainder().costUsd()).isEqualByComparingTo("6");
  }

  /** Eine Karte, die in beiden Gattungen vorkommt, zählt im Vorhaben mit ihrer Summe. */
  @Test
  void eineKarteAusBeidenGattungenZaehltMitIhrerSumme() {
    usage.jeKarte = List.of(new CardTotals(964, 2L, 60_000L, kosten("3"), kosten("1")));
    when(cards.epicsByCardNumber(anyLong(), anyCollection()))
        .thenReturn(Map.of(964, Set.of(PLANEN)));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.epics())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.epic()).isEqualTo(PLANEN);
              assertThat(e.usage().costUsd()).isEqualByComparingTo("4");
            });
  }

  /** Der Erfassungsbeginn des Projekts steht in beiden Kennzahlen-Blöcken (#984, Plan E18). */
  @Test
  void derErfassungsbeginnDesProjektsStehtInDenKennzahlen() {
    Instant seit = Instant.parse("2026-08-14T07:00:00Z");
    when(erfassungsbeginn.interactiveUsageSince(PROJECT)).thenReturn(Optional.of(seit));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().interactiveUsageSince()).isEqualTo(seit);
    assertThat(monat.previous().interactiveUsageSince()).isEqualTo(seit);
  }

  @Test
  void ohneGemeldeteSitzungBleibtDerErfassungsbeginnLeer() {
    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().interactiveUsageSince()).isNull();
    assertThat(monat.previous().interactiveUsageSince()).isNull();
  }

  // --- Die Lebenszeit (Issue #1014) ------------------------------------------------------------

  @Test
  void dieLebenszeitSummeVerlangtDenNachtlaufZugriff_undLiestOhneIhnNichts() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.total(USER, PROJECT))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThat(usage.aufrufe).isEmpty();
    verifyNoInteractions(cards);
  }

  /**
   * Die Lebenszeit trägt dieselbe Rechnung wie ein Zeitraum — der Rest je Gattung als Differenz —
   * und dazu beide Lückenangaben: den ältesten aufbewahrten Eintrag und den Erfassungsbeginn.
   */
  @Test
  void dieLebenszeitSummeTrenntDieGattungen_undTraegtBeideLueckenangaben() {
    Instant aeltester = Instant.parse("2026-06-01T00:00:00Z");
    Instant seit = Instant.parse("2026-08-14T07:00:00Z");
    usage.lebenszeit =
        new LifetimeTotals(
            4L,
            new TotalsByKind(
                new KindTotals(3L, kosten("10.00"), kosten("4.00")),
                new KindTotals(2L, kosten("2.50"), kosten("1.00"))));
    usage.aeltester = Optional.of(aeltester);
    when(erfassungsbeginn.interactiveUsageSince(PROJECT)).thenReturn(Optional.of(seit));

    TotalUsageView gesamt = service.total(USER, PROJECT);

    assertThat(gesamt.nightRunCount()).isEqualTo(3L);
    assertThat(gesamt.interactiveRunCount()).isEqualTo(2L);
    assertThat(gesamt.runCount()).isEqualTo(5L);
    assertThat(gesamt.cardCount()).isEqualTo(4L);
    assertThat(gesamt.usage().total().costUsd()).isEqualByComparingTo("12.50");
    assertThat(gesamt.usage().cardShare().costUsd()).isEqualByComparingTo("5.00");
    assertThat(gesamt.usage().remainder().costUsd()).isEqualByComparingTo("7.50");
    assertThat(gesamt.usageByKind().night().remainder().costUsd()).isEqualByComparingTo("6.00");
    assertThat(gesamt.usageByKind().interactive().remainder().costUsd())
        .isEqualByComparingTo("1.50");
    assertThat(gesamt.oldestRetainedRunStart()).isEqualTo(aeltester);
    assertThat(gesamt.interactiveUsageSince()).isEqualTo(seit);
  }

  /** Ohne Eintrag und ohne gemeldete Sitzung bleiben beide Zeitpunkte leer (Plan E8, E18). */
  @Test
  void ohneEintragUndOhneSitzungBleibenBeideZeitpunkteLeer() {
    TotalUsageView gesamt = service.total(USER, PROJECT);

    assertThat(gesamt.runCount()).isZero();
    assertThat(gesamt.usage().total()).isEqualTo(NICHTS);
    assertThat(gesamt.usageByKind().interactive().total()).isEqualTo(NICHTS);
    assertThat(gesamt.oldestRetainedRunStart()).isNull();
    assertThat(gesamt.interactiveUsageSince()).isNull();
  }

  // --- Ein Zeitraum ----------------------------------------------------------------------------

  @Test
  void rueckschrittNullIstDerZuletztAbgeschlosseneZeitraum_mitVorzeitraumDaneben() {
    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().period().firstDay()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(monat.previous().period().firstDay()).isEqualTo(LocalDate.of(2026, 7, 1));
  }

  @Test
  void rueckschrittEinsIstDerDavor_mitSeinemVorzeitraum() {
    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 1, BERLIN);

    assertThat(monat.current().period().firstDay()).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(monat.previous().period().firstDay()).isEqualTo(LocalDate.of(2026, 6, 1));
  }

  @Test
  void derVorzeitraumTraegtSeineEigenenZahlen() {
    usage.summeJeBeginn.put(
        Instant.parse("2026-08-01T10:00:00Z"), summe(4, 3, kosten("8"), kosten("5")));
    usage.summeJeBeginn.put(
        Instant.parse("2026-07-01T10:00:00Z"), summe(2, 1, kosten("3"), kosten("1")));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().runCount()).isEqualTo(4L);
    assertThat(monat.current().usage().remainder().costUsd()).isEqualByComparingTo("3");
    assertThat(monat.previous().runCount()).isEqualTo(2L);
    assertThat(monat.previous().usage().remainder().costUsd()).isEqualByComparingTo("2");
  }

  /** Leerfall 1: aufbewahrte Laeufe reichen zurueck, aber in diesem Zeitraum lief keiner (AK 9). */
  @Test
  void einZeitraumOhneLaeufeIstVollstaendigAbgedecktUndLeer() {
    usage.retention = List.of(voll(NightRunKind.NIGHT, "2026-06-01T00:00:00Z"));

    PeriodUsageView tag = service.period(USER, PROJECT, NightRunPeriodType.DAY, 0, BERLIN);

    assertThat(tag.current().coverage()).isEqualTo(Coverage.COMPLETE);
    assertThat(tag.current().noRuns()).isTrue();
  }

  /**
   * Ohne vollen Ringpuffer ist nichts verdraengt worden — jeder Zeitraum ist COMPLETE, auch einer,
   * der ganz vor dem einzigen (nicht verdraengten) Lauf liegt. Ein Puffer, der die Zahl noch nicht
   * erreicht, darf keine Grenze setzen (Issue #1071, Plan #1067 E8).
   */
  @Test
  void ohneVollenRingpufferIstJederZeitraumVollstaendig_auchVorDemErstenLauf() {
    usage.retention = List.of(nichtVoll(NightRunKind.NIGHT, "2026-09-16T00:00:00Z"));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().coverage()).isEqualTo(Coverage.COMPLETE);
    assertThat(monat.previous().coverage()).isEqualTo(Coverage.COMPLETE);
  }

  /** Der Zeitraum endet vor der Aufbewahrungsgrenze einer vollen Gattung (Plan E8). */
  @Test
  void einZeitraumGanzVorDerGrenzeEinerVollenGattungIstVorDerAufbewahrung() {
    usage.retention = List.of(voll(NightRunKind.NIGHT, "2026-09-01T10:00:00Z"));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().coverage()).isEqualTo(Coverage.BEFORE_RETENTION);
    assertThat(monat.previous().coverage()).isEqualTo(Coverage.BEFORE_RETENTION);
  }

  /**
   * Zahlen **und** Teilabdeckung, wenn die Grenze einer vollen Gattung mitten im Zeitraum liegt.
   */
  @Test
  void einZeitraumMitDerGrenzeDarinTraegtZahlenUndTeilabdeckung() {
    usage.retention = List.of(voll(NightRunKind.NIGHT, "2026-08-12T21:00:00Z"));
    usage.summeJeBeginn.put(
        Instant.parse("2026-08-01T10:00:00Z"), summe(5, 4, kosten("12"), kosten("9")));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().coverage()).isEqualTo(Coverage.PARTIAL);
    assertThat(monat.current().noRuns()).isFalse();
    assertThat(monat.current().runCount()).isEqualTo(5L);
  }

  /** Genau an der Grenze: Beginnt der Zeitraum mit dem aeltesten Lauf, ist er vollstaendig. */
  @Test
  void einZeitraumDerMitDerGrenzeBeginntIstVollstaendig() {
    usage.retention = List.of(voll(NightRunKind.NIGHT, "2026-08-01T10:00:00Z"));

    assertThat(
            service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN).current().coverage())
        .isEqualTo(Coverage.COMPLETE);
  }

  /** Endet der Zeitraum genau mit der Grenze, liegt er ganz davor. */
  @Test
  void einZeitraumDerMitDerGrenzeEndetLiegtDavor() {
    usage.retention = List.of(voll(NightRunKind.NIGHT, "2026-09-01T10:00:00Z"));

    assertThat(
            service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN).current().coverage())
        .isEqualTo(Coverage.BEFORE_RETENTION);
  }

  /**
   * Der Grenzfall aus E9: der Puffer ist exakt voll ({@code count == max}), obwohl nie verdraengt
   * wurde. Die Aussage irrt dann in Richtung Vorsicht — hingenommen, bis der naechste Lauf kommt.
   */
  @Test
  void einExaktVollerPufferOhneVerdraengungErgibtDennochEineGrenze() {
    usage.retention = List.of(voll(NightRunKind.NIGHT, "2026-09-01T10:00:00Z"));

    assertThat(
            service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN).current().coverage())
        .isEqualTo(Coverage.BEFORE_RETENTION);
  }

  /** Sind beide Gattungen voll, ist die Grenze der spaetere der beiden aeltesten Zeitpunkte. */
  @Test
  void sindBeideGattungenVollIstDieGrenzeDerSpaetereZeitpunkt() {
    usage.retention =
        List.of(
            voll(NightRunKind.NIGHT, "2026-06-15T00:00:00Z"),
            voll(NightRunKind.INTERACTIVE, "2026-07-20T00:00:00Z"));

    PeriodUsageView juli = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 1, BERLIN);

    assertThat(juli.current().period().firstDay()).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(juli.current().coverage()).isEqualTo(Coverage.PARTIAL);
  }

  /**
   * Haelt eine nicht volle Gattung aeltere Eintraege als die volle, liegt die Grenze am aeltesten
   * Eintrag der vollen Gattung — dadurch gelten mehr Zeitraeume als betroffen, nie weniger
   * (Plan-Review H5): Der nicht volle, aeltere Zeitpunkt darf die Grenze nicht nach vorn ziehen.
   */
  @Test
  void eineNichtVolleGattungMitAelterenEintraegenZiehtDieGrenzeNichtNachVorn() {
    usage.retention =
        List.of(
            voll(NightRunKind.NIGHT, "2026-07-20T00:00:00Z"),
            nichtVoll(NightRunKind.INTERACTIVE, "2026-06-01T00:00:00Z"));

    PeriodUsageView juli = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 1, BERLIN);

    assertThat(juli.current().period().firstDay()).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(juli.current().coverage()).isEqualTo(Coverage.PARTIAL);
  }

  /** Ein Projekt ohne jeden Lauf: nichts ist verdraengt, also vollstaendig und leer. */
  @Test
  void einProjektOhneLaeufeIstVollstaendigUndLeer() {
    PeriodUsageView tag = service.period(USER, PROJECT, NightRunPeriodType.DAY, 0, BERLIN);

    assertThat(tag.current().coverage()).isEqualTo(Coverage.COMPLETE);
    assertThat(tag.current().noRuns()).isTrue();
  }

  @Test
  void dieNaechteDesZeitraumsSindErreichbar() {
    usage.naechte =
        List.of(
            nacht(LocalDate.of(2026, 8, 3)),
            nacht(LocalDate.of(2026, 8, 4), NightRunErrorClass.HARD_ABORT));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.nights())
        .extracting(n -> n.night(), n -> n.aborted())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 8, 3), false),
            org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 8, 4), true));
    assertThat(usage.aufrufe).contains("perNight 2026-08-01T10:00:00Z 2026-09-01T10:00:00Z");
  }

  // --- Stufen der Kette (Issue #1114) ----------------------------------------------------------

  /**
   * Die Aufstellung je Stufe kommt in beiden Sichten mit — Feld für Feld unverändert, über die
   * Spanne der jeweiligen Sicht gelesen (#993 AK 8).
   */
  @Test
  void dieAufstellungJeStufeKommtInNachtUndZeitraumMit() {
    NightRunUsage voll = new NightRunUsage(new BigDecimal("1.50"), 150L, 15L, 6L, 45_000L, 5);
    usage.jeStufe =
        List.of(
            new StageTotals(NightRunStage.PLAN, 2L, 70_000L, voll),
            new StageTotals(NightRunStage.ABDECKUNG, 1L, null, NICHTS));

    NightUsageView nacht = service.night(USER, PROJECT, NACHT_15, BERLIN);
    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(nacht.stages())
        .extracting(
            StageUsageView::stage,
            StageUsageView::itemCount,
            StageUsageView::durationMs,
            StageUsageView::usage)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(NightRunStage.PLAN, 2L, 70_000L, voll),
            org.assertj.core.groups.Tuple.tuple(NightRunStage.ABDECKUNG, 1L, null, NICHTS));
    assertThat(monat.stages())
        .extracting(
            StageUsageView::stage,
            StageUsageView::itemCount,
            StageUsageView::durationMs,
            StageUsageView::usage)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(NightRunStage.PLAN, 2L, 70_000L, voll),
            org.assertj.core.groups.Tuple.tuple(NightRunStage.ABDECKUNG, 1L, null, NICHTS));
    assertThat(usage.aufrufe)
        .contains(
            "perStage 2026-09-15T10:00:00Z 2026-09-16T10:00:00Z",
            "perStage 2026-08-01T10:00:00Z 2026-09-01T10:00:00Z");
  }

  /** Ohne Ketten-Lauf bleibt die Aufstellung leer — und nie eine Zeile „ohne Stufe" (Plan E6). */
  @Test
  void ohneKettenLaufBleibtDieAufstellungJeStufeLeer() {
    assertThat(service.night(USER, PROJECT, NACHT_15, BERLIN).stages()).isEmpty();
    assertThat(service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN).stages())
        .isEmpty();
  }

  // --- Vorhaben-Aufstellung --------------------------------------------------------------------

  private static final EpicRef PLANEN = new EpicRef(1L, "PLANEN", "Planen");
  private static final EpicRef BACKUP = new EpicRef(2L, "BACKUP", "Backup");

  @Test
  void eineKarteInZweiVorhabenZaehltInBeiden_undDieAntwortSagtEs() {
    usage.jeKarte = List.of(karte(964, kosten("3")));
    when(cards.epicsByCardNumber(anyLong(), anyCollection()))
        .thenReturn(Map.of(964, Set.of(PLANEN, BACKUP)));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.epics())
        .extracting(EpicUsageView::epic)
        .containsExactlyInAnyOrder(PLANEN, BACKUP);
    assertThat(monat.epics())
        .allSatisfy(e -> assertThat(e.usage().costUsd()).isEqualByComparingTo("3"));
    assertThat(monat.epicsOverlap()).isTrue();
  }

  @Test
  void ohneMehrfachzugehoerigkeitUeberschneidenSichDieVorhabenNicht() {
    usage.jeKarte = List.of(karte(964, kosten("3")), karte(824, kosten("1")));
    when(cards.epicsByCardNumber(anyLong(), anyCollection()))
        .thenReturn(Map.of(964, Set.of(PLANEN), 824, Set.of(BACKUP)));

    assertThat(service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN).epicsOverlap())
        .isFalse();
  }

  /** Eine geloeschte Kartennummer liefert die Fassade nicht — sie zaehlt als „ohne Vorhaben". */
  @Test
  void eineGeloeschteKartennummerZaehltAlsOhneVorhaben_undFaelltNichtAusDerSumme() {
    usage.jeKarte =
        List.of(karte(964, kosten("3")), karte(4711, kosten("2")), karte(1, kosten("1")));
    when(cards.epicsByCardNumber(anyLong(), anyCollection()))
        .thenReturn(Map.of(964, Set.of(PLANEN)));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.withoutEpic().epic()).isNull();
    assertThat(monat.withoutEpic().cardCount()).isEqualTo(2L);
    assertThat(monat.withoutEpic().usage().costUsd()).isEqualByComparingTo("3");
  }

  @Test
  void dieVorhabenStehenAbsteigendNachKosten_ohneKostenZuletzt() {
    EpicRef ohneKosten = new EpicRef(3L, null, "Ohne Kosten");
    usage.jeKarte =
        List.of(
            karte(1, kosten("1")), karte(2, kosten("5")), karte(3, null), karte(4, kosten("2")));
    when(cards.epicsByCardNumber(anyLong(), anyCollection()))
        .thenReturn(
            Map.of(1, Set.of(PLANEN), 2, Set.of(BACKUP), 3, Set.of(ohneKosten), 4, Set.of(PLANEN)));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.epics())
        .extracting(EpicUsageView::epic)
        .containsExactly(BACKUP, PLANEN, ohneKosten);
    assertThat(monat.epics().get(1).cardCount()).isEqualTo(2L);
    assertThat(monat.epics().get(1).usage().costUsd()).isEqualByComparingTo("3");
    assertThat(monat.epics().get(2).usage().costUsd()).isNull();
  }

  /** Bei gleichen Kosten entscheidet die Vorhaben-ID, damit die Reihenfolge feststeht. */
  @Test
  void beiGleichenKostenEntscheidetDieVorhabenId() {
    usage.jeKarte = List.of(karte(1, kosten("2")), karte(2, kosten("2")));
    when(cards.epicsByCardNumber(anyLong(), anyCollection()))
        .thenReturn(Map.of(1, Set.of(BACKUP), 2, Set.of(PLANEN)));

    assertThat(service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN).epics())
        .extracting(EpicUsageView::epic)
        .containsExactly(PLANEN, BACKUP);
  }

  @Test
  void ohneKartenBleibtDieAufstellungLeer_undOhneVorhabenTraegtNichts() {
    when(cards.epicsByCardNumber(anyLong(), anyCollection())).thenReturn(Map.of());

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.epics()).isEmpty();
    assertThat(monat.withoutEpic().cardCount()).isZero();
    assertThat(monat.withoutEpic().usage()).isEqualTo(NICHTS);
    assertThat(monat.epicsOverlap()).isFalse();
  }

  // --- Doppelgaenger des Ports -----------------------------------------------------------------

  /** Liefert vorgegebene Zeilen und protokolliert, mit welcher Spanne gefragt wurde. */
  static final class FakeUsage implements NightRunUsageRepository {

    final List<String> aufrufe = new ArrayList<>();
    List<NightTotals> naechte = List.of();
    List<CardTotals> jeKarte = List.of();
    PeriodTotals summe = summe(0, 0, NICHTS, NICHTS);
    LifetimeTotals lebenszeit = new LifetimeTotals(0L, new TotalsByKind(LEER, LEER));
    final Map<Instant, PeriodTotals> summeJeBeginn = new java.util.HashMap<>();
    Optional<Instant> aeltester = Optional.empty();
    List<RetainedByKind> retention = List.of();
    List<StageTotals> jeStufe = List.of();

    @Override
    public List<NightTotals> totalsPerNight(long projectId, Instant from, Instant to, ZoneId zone) {
      aufrufe.add("perNight " + from + " " + to);
      return naechte;
    }

    @Override
    public List<StageTotals> totalsPerStage(long projectId, Instant from, Instant to) {
      aufrufe.add("perStage " + from + " " + to);
      return jeStufe;
    }

    @Override
    public List<CardTotals> totalsPerCard(long projectId, Instant from, Instant to) {
      aufrufe.add("perCard " + from + " " + to);
      return jeKarte;
    }

    @Override
    public PeriodTotals totals(long projectId, Instant from, Instant to) {
      aufrufe.add("totals " + from + " " + to);
      return summeJeBeginn.getOrDefault(from, summe);
    }

    @Override
    public LifetimeTotals lifetimeTotals(long projectId) {
      aufrufe.add("lifetime");
      return lebenszeit;
    }

    @Override
    public Optional<Instant> oldestRetainedRunStart(long projectId) {
      aufrufe.add("oldest");
      return aeltester;
    }

    @Override
    public List<RetainedByKind> retentionBoundary(long projectId) {
      aufrufe.add("retentionBoundary");
      return retention;
    }
  }
}
