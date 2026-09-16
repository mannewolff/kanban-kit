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
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.NightTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.PeriodTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageService.Coverage;
import org.mwolff.manban.nightrun.application.NightRunUsageService.EpicUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.NightUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.PeriodUsageView;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunPeriodType;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;

/**
 * Verhaltenstests der Verbrauchs-Auswertung (Issue #938) gegen einen Doppelgänger des Ports und
 * eine gemockte card-Fassade. Die Summen selbst belegt {@code NightRunUsageRepositoryIT}; hier geht
 * es um das, was der Service daraus macht: Rest, Abbruch, Abdeckung, Vorhaben-Aufstellung.
 */
// Testklasse: Jede Methode ist ein Fall aus dem Akzeptanzkriterium.
@SuppressWarnings("PMD.TooManyMethods")
class NightRunUsageServiceTest {

  private static final long USER = 7L;
  private static final long PROJECT = 3L;
  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  /** Mi 16.09.2026 13:00 in Berlin: die zuletzt abgeschlossene Nacht ist die vom 15. auf den 16. */
  private static final Clock JETZT =
      Clock.fixed(Instant.parse("2026-09-16T11:00:00Z"), ZoneOffset.UTC);

  private static final LocalDate NACHT_15 = LocalDate.of(2026, 9, 15);
  private static final NightRunUsage NICHTS = new NightRunUsage(null, null, null, null);

  private FakeUsage usage;
  private CardService cards;
  private PermissionChecker permissions;
  private NightRunUsageService service;

  @BeforeEach
  void setUp() {
    usage = new FakeUsage();
    cards = mock(CardService.class);
    permissions = mock(PermissionChecker.class);
    service = new NightRunUsageService(usage, cards, permissions, JETZT);
  }

  private static NightRunUsage kosten(String betrag) {
    return new NightRunUsage(new BigDecimal(betrag), null, null, null);
  }

  private static PeriodTotals summe(
      long runs, long karten, NightRunUsage lauf, NightRunUsage pakete) {
    return new PeriodTotals(runs, 1_000L * runs, karten, lauf, pakete);
  }

  private static CardTotals karte(int nummer, @Nullable NightRunUsage verbrauch) {
    return new CardTotals(nummer, 1L, 60_000L, verbrauch == null ? NICHTS : verbrauch);
  }

  // --- Rechte ----------------------------------------------------------------------------------

  @Test
  void dieNachtVerlangtDenBesitzer_undLiestOhneIhnNichts() {
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireOwner(USER, PROJECT);

    assertThatThrownBy(() -> service.night(USER, PROJECT, NACHT_15, BERLIN))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThat(usage.aufrufe).isEmpty();
    verifyNoInteractions(cards);
  }

  @Test
  void derZeitraumVerlangtDenBesitzer_undLiestOhneIhnNichts() {
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireOwner(USER, PROJECT);

    assertThatThrownBy(() -> service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThat(usage.aufrufe).isEmpty();
    verifyNoInteractions(cards);
  }

  @Test
  void beideUseCasesPruefenDenBesitzerFuerDasFragendeProjekt() {
    service.night(USER, PROJECT, NACHT_15, BERLIN);
    service.period(USER, PROJECT, NightRunPeriodType.DAY, 0, BERLIN);

    verify(permissions, org.mockito.Mockito.times(2)).requireOwner(USER, PROJECT);
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
            new NightRunUsage(new BigDecimal("10.00"), 1_000L, 100L, 900L),
            new NightRunUsage(new BigDecimal("4.00"), 400L, 40L, 360L));

    NightUsageView nacht = service.night(USER, PROJECT, NACHT_15, BERLIN);

    assertThat(nacht.usage().total().costUsd()).isEqualByComparingTo("10.00");
    assertThat(nacht.usage().cardShare().costUsd()).isEqualByComparingTo("4.00");
    assertThat(nacht.usage().remainder().costUsd()).isEqualByComparingTo("6.00");
    assertThat(nacht.usage().cardShare().plus(nacht.usage().remainder()))
        .isEqualTo(new NightRunUsage(new BigDecimal("10.00"), 1_000L, 100L, 900L));
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
    return new NightTotals(datum, 1L, 1_000L, 1L, NICHTS, NICHTS, Set.of(klassen));
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
    usage.aeltester = Optional.of(Instant.parse("2026-06-01T00:00:00Z"));

    PeriodUsageView tag = service.period(USER, PROJECT, NightRunPeriodType.DAY, 0, BERLIN);

    assertThat(tag.current().coverage()).isEqualTo(Coverage.COMPLETE);
    assertThat(tag.current().noRuns()).isTrue();
  }

  /** Leerfall 2: der Zeitraum endet vor dem aeltesten aufbewahrten Lauf (Plan E8). */
  @Test
  void einZeitraumGanzVorDemAeltestenLaufIstVorDerAufbewahrung() {
    usage.aeltester = Optional.of(Instant.parse("2026-09-01T10:00:00Z"));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().coverage()).isEqualTo(Coverage.BEFORE_RETENTION);
    assertThat(monat.previous().coverage()).isEqualTo(Coverage.BEFORE_RETENTION);
  }

  /**
   * Leerfall 3: Zahlen **und** Teilabdeckung, wenn die Grenze mitten im Zeitraum liegt (Plan E8).
   */
  @Test
  void einZeitraumMitDerAufbewahrungsgrenzeDarinTraegtZahlenUndTeilabdeckung() {
    usage.aeltester = Optional.of(Instant.parse("2026-08-12T21:00:00Z"));
    usage.summeJeBeginn.put(
        Instant.parse("2026-08-01T10:00:00Z"), summe(5, 4, kosten("12"), kosten("9")));

    PeriodUsageView monat = service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN);

    assertThat(monat.current().coverage()).isEqualTo(Coverage.PARTIAL);
    assertThat(monat.current().noRuns()).isFalse();
    assertThat(monat.current().runCount()).isEqualTo(5L);
  }

  /** Genau an der Grenze: Beginnt der Zeitraum mit dem aeltesten Lauf, ist er vollstaendig. */
  @Test
  void einZeitraumDerMitDemAeltestenLaufBeginntIstVollstaendig() {
    usage.aeltester = Optional.of(Instant.parse("2026-08-01T10:00:00Z"));

    assertThat(
            service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN).current().coverage())
        .isEqualTo(Coverage.COMPLETE);
  }

  /** Endet der Zeitraum genau mit dem aeltesten Lauf, liegt er ganz davor. */
  @Test
  void einZeitraumDerMitDemAeltestenLaufEndetLiegtDavor() {
    usage.aeltester = Optional.of(Instant.parse("2026-09-01T10:00:00Z"));

    assertThat(
            service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN).current().coverage())
        .isEqualTo(Coverage.BEFORE_RETENTION);
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
    final Map<Instant, PeriodTotals> summeJeBeginn = new java.util.HashMap<>();
    Optional<Instant> aeltester = Optional.empty();

    @Override
    public List<NightTotals> totalsPerNight(long projectId, Instant from, Instant to, ZoneId zone) {
      aufrufe.add("perNight " + from + " " + to);
      return naechte;
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
    public Optional<Instant> oldestRetainedRunStart(long projectId) {
      aufrufe.add("oldest");
      return aeltester;
    }
  }
}
