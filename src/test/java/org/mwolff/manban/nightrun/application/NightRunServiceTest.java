package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.application.NightRunRepository.UpsertResult;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunBudget;
import org.mwolff.manban.nightrun.domain.NightRunBudgetOrigin;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunItemStage;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunStage;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.InteractiveUsageSinceWriter;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/**
 * Verhaltenstests der Nachtlauf-Use-Cases (Issue #722).
 *
 * <p>Das Repository ist ein <b>ausgespähter Fake</b>, kein reiner Mock: Der Ringpuffer ist eine
 * Zusage über den Zustand danach („es bleiben drei"), nicht über einen Aufruf. Mit einem reinen
 * Mock ließe sich nur belegen, <em>dass</em> verdrängt wurde — nicht, <em>was</em> übrig bleibt.
 * Das {@code spy} darüber hält zugleich {@code verifyNoInteractions} verfügbar, das die
 * Rechteprüfung braucht.
 */
// Testklasse: Jede Methode ist ein Fall, und Faelle werden nicht zusammengelegt, um eine
// Zahl zu druecken. Issue #946 bringt sieben Faelle fuer den meldenden Weg dazu, Issue #1113 vier
// fuer Budgets und Stufen.
// PMD.ExcessiveImports/CouplingBetweenObjects: Die Importe folgen den Typen, die der Dienst fuehrt
// — mit Issue #1113 kommen NightRunBudget, NightRunBudgetOrigin, NightRunItemStage und
// NightRunStage dazu. Dieselbe Ursache wie am NightRunService selbst, nur an der Testseite.
// PMD.CyclomaticComplexity: die Summe ueber lauter Methoden der Komplexitaet 1 — sie zaehlt hier
// die Zahl der Faelle und nicht Verzweigungen, die es nicht gibt.
@SuppressWarnings({
  "PMD.TooManyMethods",
  "PMD.ExcessiveImports",
  "PMD.CouplingBetweenObjects",
  "PMD.CyclomaticComplexity"
})
class NightRunServiceTest {

  private static final Instant FIXED = Instant.parse("2026-09-02T04:00:00Z");
  private static final long USER = 1L;
  private static final long PROJECT = 42L;

  private static final Instant T1 = Instant.parse("2026-08-29T01:00:00Z");
  private static final Instant T2 = Instant.parse("2026-08-30T01:00:00Z");
  private static final Instant T3 = Instant.parse("2026-08-31T01:00:00Z");
  private static final Instant T4 = Instant.parse("2026-09-01T01:00:00Z");

  private NightRunRepository runs;
  private PermissionChecker permissions;
  private InteractiveUsageSinceWriter erfassungsbeginn;
  private NightRunService service;

  @BeforeEach
  void setUp() {
    runs = spy(new FakeNightRunRepository());
    permissions = mock(PermissionChecker.class);
    erfassungsbeginn = mock(InteractiveUsageSinceWriter.class);
    service = serviceMitPuffer(30);
  }

  private NightRunService serviceMitPuffer(int maxPerProject) {
    return new NightRunService(
        runs,
        permissions,
        erfassungsbeginn,
        // Die Grenzen der interaktiven Sitzung stehen bewusst anders als die der Nachtlaeufe
        // (Issue #1011): Ein Test, der sie gleich setzte, saehe nicht, welche durchgereicht wird.
        new NightRunProperties(maxPerProject, 2000, 400, 4000, null),
        Clock.fixed(FIXED, ZoneOffset.UTC));
  }

  // --- Rechte -----------------------------------------------------------------------------

  @Test
  void submit_requiresOwner() {
    service.submit(USER, PROJECT, List.of(lauf(T1)));

    verify(permissions).requireOwner(USER, PROJECT);
  }

  @Test
  void submit_touchesNoRepository_whenMemberIsNotOwner() {
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireOwner(USER, PROJECT);

    assertThatThrownBy(() -> service.submit(USER, PROJECT, List.of(lauf(T1))))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verifyNoInteractions(runs);
  }

  @Test
  void submit_touchesNoRepository_whenUserIsNoMember() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireOwner(USER, PROJECT);

    assertThatThrownBy(() -> service.submit(USER, PROJECT, List.of(lauf(T1))))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(runs);
  }

  @Test
  void list_verlangtDenNachtlaufZugriff() {
    service.list(USER, PROJECT);

    verify(permissions).requireNightRunAccess(USER, PROJECT);
  }

  @Test
  void list_touchesNoRepository_whenAccessIsDenied() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.list(USER, PROJECT))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verifyNoInteractions(runs);
  }

  @Test
  void list_touchesNoRepository_whenUserIsNoMember() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.list(USER, PROJECT))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(runs);
  }

  @Test
  void countRunsByErrorClass_verlangtDenNachtlaufZugriff() {
    service.countRunsByErrorClass(USER, PROJECT);

    verify(permissions).requireNightRunAccess(USER, PROJECT);
  }

  @Test
  void countRunsByErrorClass_touchesNoRepository_whenAccessIsDenied() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.countRunsByErrorClass(USER, PROJECT))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verifyNoInteractions(runs);
  }

  @Test
  void countRunsByErrorClass_touchesNoRepository_whenUserIsNoMember() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.countRunsByErrorClass(USER, PROJECT))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(runs);
  }

  // --- Anlegen ----------------------------------------------------------------------------

  @Test
  void submit_reportsOneResultPerInput_inInputOrder() {
    List<NightRunService.NightRunResult> results =
        service.submit(USER, PROJECT, List.of(lauf(T3), lauf(T1), lauf(T2)));

    assertThat(results)
        .extracting(NightRunService.NightRunResult::startedAt)
        .containsExactly(T3, T1, T2);
    assertThat(results).allMatch(NightRunService.NightRunResult::created);
  }

  @Test
  void submit_returnsEmptyResult_withoutRepositoryAccess_whenNothingSubmitted() {
    List<NightRunService.NightRunResult> results = service.submit(USER, PROJECT, List.of());

    assertThat(results).isEmpty();
    verify(permissions).requireOwner(USER, PROJECT);
    verifyNoInteractions(runs);
  }

  @Test
  void submit_setsCreatedAtFromInjectedClock() {
    service.submit(USER, PROJECT, List.of(lauf(T1)));

    assertThat(service.list(USER, PROJECT))
        .singleElement()
        .extracting(NightRunService.NightRunView::createdAt)
        .isEqualTo(FIXED);
  }

  @Test
  void submit_reportsSecondRunOfSameRequestAsAlreadyPresent_whenStartedAtRepeats() {
    List<NightRunService.NightRunResult> results =
        service.submit(USER, PROJECT, List.of(lauf(T1), lauf(T1)));

    assertThat(results)
        .extracting(NightRunService.NightRunResult::created)
        .containsExactly(true, false);
    assertThat(service.list(USER, PROJECT)).hasSize(1);
  }

  @Test
  void submit_keepsKnownRunUntouched_andCreatesTheOthersOfTheSameRequest() {
    service.submit(USER, PROJECT, List.of(lauf(T1, item(7, NightRunState.GREEN, null))));

    List<NightRunService.NightRunResult> results =
        service.submit(
            USER,
            PROJECT,
            List.of(
                lauf(T1, item(99, NightRunState.RED, NightRunErrorClass.HARD_ABORT)),
                lauf(T2, item(8, NightRunState.GREEN, null))));

    assertThat(results)
        .extracting(NightRunService.NightRunResult::created)
        .containsExactly(false, true);
    assertThat(itemsOf(T1))
        .singleElement()
        .extracting(NightRunService.NightRunItemView::cardNumber)
        .isEqualTo(7);
  }

  // --- Ringpuffer -------------------------------------------------------------------------

  @Test
  void submit_evictsRunWithOldestStartedAt_whenBufferOverflows() {
    service = serviceMitPuffer(3);
    // Der zuerst eingefuegte Lauf ist bewusst nicht der aelteste: Verdraengt wird nach
    // started_at, nicht nach Einfuegereihenfolge (Plan #718, A14).
    service.submit(USER, PROJECT, List.of(lauf(T2), lauf(T1), lauf(T3)));

    service.submit(USER, PROJECT, List.of(lauf(T4)));

    assertThat(service.list(USER, PROJECT))
        .extracting(NightRunService.NightRunView::startedAt)
        .containsExactly(T4, T3, T2);
  }

  @Test
  void submit_evictsAsManyRunsAsNeeded_whenOneRequestOverflowsSeveralTimes() {
    service = serviceMitPuffer(2);

    service.submit(USER, PROJECT, List.of(lauf(T1), lauf(T2), lauf(T3), lauf(T4)));

    assertThat(service.list(USER, PROJECT))
        .extracting(NightRunService.NightRunView::startedAt)
        .containsExactly(T4, T3);
  }

  @Test
  void submit_reportsCreated_forRunOlderThanAllKept_thoughItIsEvictedAtOnce() {
    service = serviceMitPuffer(3);
    service.submit(USER, PROJECT, List.of(lauf(T2), lauf(T3), lauf(T4)));

    List<NightRunService.NightRunResult> results = service.submit(USER, PROJECT, List.of(lauf(T1)));

    assertThat(results).singleElement().returns(true, NightRunService.NightRunResult::created);
    assertThat(service.list(USER, PROJECT))
        .extracting(NightRunService.NightRunView::startedAt)
        .containsExactly(T4, T3, T2);
  }

  // --- Auflisten und Zaehlen ---------------------------------------------------------------

  @Test
  void list_returnsRunsNewestFirst_withTheirOwnItems() {
    service.submit(
        USER,
        PROJECT,
        List.of(
            lauf(T1, item(11, NightRunState.GREEN, null)),
            lauf(T2, item(22, NightRunState.RED, NightRunErrorClass.CHECKS_RED))));

    List<NightRunService.NightRunView> views = service.list(USER, PROJECT);

    assertThat(views).extracting(NightRunService.NightRunView::startedAt).containsExactly(T2, T1);
    assertThat(views.get(0).items())
        .singleElement()
        .returns(22, NightRunService.NightRunItemView::cardNumber)
        .returns(NightRunErrorClass.CHECKS_RED, NightRunService.NightRunItemView::errorClass);
    assertThat(views.get(1).items())
        .singleElement()
        .returns(11, NightRunService.NightRunItemView::cardNumber);
  }

  @Test
  void list_carriesTheRunFieldsIntoTheView() {
    service.submit(USER, PROJECT, List.of(lauf(T1)));

    NightRunService.NightRunView view = service.list(USER, PROJECT).get(0);

    assertThat(view.mode()).isEqualTo(NightRunMode.IMPLEMENTATION);
    assertThat(view.durationMs()).isEqualTo(1_000L);
    assertThat(view.processedCount()).isEqualTo(2);
    assertThat(view.skippedCount()).isEqualTo(1);
    assertThat(view.unparsedCount()).isEqualTo(3);
    assertThat(view.unparsedSample()).isEqualTo("Rest");
    assertThat(view.id()).isNotNull();
  }

  @Test
  void list_carriesTheItemFieldsIntoTheView() {
    service.submit(
        USER,
        PROJECT,
        List.of(lauf(T1, item(11, NightRunState.RED, NightRunErrorClass.HARD_ABORT))));

    NightRunService.NightRunItemView item = service.list(USER, PROJECT).get(0).items().get(0);

    assertThat(item.id()).isNotNull();
    assertThat(item.title()).isEqualTo("Paket 11");
    assertThat(item.state()).isEqualTo(NightRunState.RED);
    assertThat(item.durationMs()).isEqualTo(500L);
    assertThat(item.commitHash()).isEqualTo("abc1234");
    assertThat(item.excerpt()).isEqualTo("Auszug 11");
  }

  @Test
  void countRunsByErrorClass_countsEachRunAtMostOnce_andIgnoresEvictedRuns() {
    service = serviceMitPuffer(2);
    service.submit(
        USER,
        PROJECT,
        List.of(
            // T1 faellt gleich aus dem Puffer und darf nicht mehr zaehlen.
            lauf(T1, item(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED)),
            lauf(
                T2,
                item(2, NightRunState.RED, NightRunErrorClass.CHECKS_RED),
                item(3, NightRunState.RED, NightRunErrorClass.CHECKS_RED)),
            lauf(T3, item(4, NightRunState.RED, NightRunErrorClass.CHECKS_RED))));

    Map<NightRunErrorClass, Long> counts = service.countRunsByErrorClass(USER, PROJECT);

    assertThat(counts).containsExactly(Map.entry(NightRunErrorClass.CHECKS_RED, 2L));
  }

  // --- Der Befund der Auswertung (Issue #1123) ----------------------------------------------

  /**
   * Kriterium 6 der fachlichen Quelle #1064: Die Auswertung des Laufs zeigt dasselbe maßgebliche
   * Paket wie der Plattform-Leitstand. Bei einer abgebrochenen Kette ist das <b>nicht</b> die
   * Ketten-Einheit, die immer zuerst steht und ihren Abbruch geerbt hat, sondern das Paket, an dem
   * die Kette riss — hier #1112.
   */
  @Test
  void list_zeigtBeiEinerKetteDasPaketAnDemSieRiss() {
    service.ingest(
        USER,
        PROJECT,
        TOKEN,
        NightRunKind.NIGHT,
        meldung(
            T1,
            true,
            null,
            item(993, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
            item(1112, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(service.list(USER, PROJECT).getFirst().outcome().decisiveItem().cardNumber())
        .isEqualTo(1112);
  }

  /**
   * Derselbe Lauf als Implementierungs-Lauf: Dort gibt es keine erbende Ketten-Einheit, und das
   * erste Paket in Laufreihenfolge bleibt maßgeblich. Beide Fälle stehen hier, weil die Laufart
   * sonst wirkungslos weitergereicht werden könnte.
   */
  @Test
  void list_bleibtAusserhalbEinerKetteBeimErstenPaket() {
    service.submit(
        USER,
        PROJECT,
        List.of(
            lauf(
                T1,
                item(993, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(1112, NightRunState.RED, NightRunErrorClass.HARD_ABORT))));

    assertThat(service.list(USER, PROJECT).getFirst().outcome().decisiveItem().cardNumber())
        .isEqualTo(993);
  }

  // --- Hilfsmittel --------------------------------------------------------------------------

  private List<NightRunService.NightRunItemView> itemsOf(Instant startedAt) {
    return service.list(USER, PROJECT).stream()
        .filter(v -> v.startedAt().equals(startedAt))
        .findFirst()
        .orElseThrow()
        .items();
  }

  private static NightRunService.NewNightRun lauf(
      Instant startedAt, NightRunService.NewNightRunItem... items) {
    return new NightRunService.NewNightRun(
        startedAt,
        NightRunMode.IMPLEMENTATION,
        1_000L,
        2,
        1,
        3,
        "Rest",
        true,
        null,
        null,
        null,
        List.of(items));
  }

  private static NightRunService.NewNightRunItem item(
      int cardNumber, NightRunState state, NightRunErrorClass errorClass) {
    return new NightRunService.NewNightRunItem(
        cardNumber,
        "Paket " + cardNumber,
        state,
        errorClass,
        500L,
        "abc1234",
        "Auszug " + cardNumber,
        null,
        List.of());
  }

  /**
   * Speichernder Ersatz des Ports mit der Semantik des Adapters: {@code insertIfAbsent} weist einen
   * schon vorhandenen {@code (projectId, startedAt)} ab, {@code deleteOlderThanNewest} verdrängt
   * nach {@code startedAt} absteigend.
   */

  // --- ingest: der meldende Weg (Issue #946) ----------------------------------------------

  private static final String TOKEN = "nacht-token";

  private static NightRunService.NewNightRun meldung(
      Instant startedAt,
      boolean complete,
      @Nullable NightRunUsage usage,
      NightRunService.NewNightRunItem... items) {
    return meldung(startedAt, complete, usage, null, items);
  }

  private static NightRunService.NewNightRun meldung(
      Instant startedAt,
      boolean complete,
      @Nullable NightRunUsage usage,
      @Nullable NightRunBudget budget,
      NightRunService.NewNightRunItem... items) {
    return new NightRunService.NewNightRun(
        startedAt,
        NightRunMode.CHAIN,
        1_000L,
        1,
        0,
        0,
        null,
        complete,
        usage,
        null,
        budget,
        List.of(items));
  }

  private NightRun gemeldeterLauf() {
    return gemeldeterLauf(NightRunKind.NIGHT);
  }

  private NightRun gemeldeterLauf(NightRunKind kind) {
    return runs.findByProjectAndKindOrderByStartedAtDesc(PROJECT, kind).getFirst();
  }

  @Test
  void ingest_verlangtDenBesitzer() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null));

    verify(permissions).requireOwner(USER, PROJECT);
  }

  @Test
  void ingest_schreibtNichts_wennDerBesitzerFehlt() {
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireOwner(USER, PROJECT);

    assertThatThrownBy(
            () -> service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null)))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verifyNoInteractions(runs);
  }

  @Test
  void ingest_schreibtMaschinelleHerkunftMitTokennamenUndZeitpunkt() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, false, null));

    NightRun geschrieben = gemeldeterLauf();
    assertThat(geschrieben.origin()).isEqualTo(NightRunOrigin.TOKEN);
    assertThat(geschrieben.tokenName()).isEqualTo(TOKEN);
    assertThat(geschrieben.updatedAt()).isEqualTo(FIXED);
    assertThat(geschrieben.complete()).isFalse();
  }

  /** Die gemeldete Gattung steht am Lauf und an jedem seiner Arbeitspakete (Issue #1010). */
  @Test
  void ingest_schreibtDieGattungNightAnLaufUndPaket() {
    service.ingest(
        USER,
        PROJECT,
        TOKEN,
        NightRunKind.NIGHT,
        meldung(T1, true, null, item(721, NightRunState.GREEN, null)));

    NightRun geschrieben = gemeldeterLauf();
    assertThat(geschrieben.kind()).isEqualTo(NightRunKind.NIGHT);
    assertThat(runs.findItemsByRunIds(List.of(geschrieben.requireId())))
        .extracting(NightRunItem::kind)
        .containsExactly(NightRunKind.NIGHT);
  }

  // --- Die Gattung der Einlieferung (Issue #1012) ------------------------------------------

  /**
   * Die Gegenprobe zum Fall darueber: Der Service erfindet die Gattung nicht, sondern nimmt sie
   * entgegen. Ein Service, der {@code NIGHT} fest verdrahtet hielte, bestuende den Fall darueber —
   * und legte jede Sitzung als Nachtlauf ab.
   */
  @Test
  void ingest_schreibtDieGemeldeteGattungInteractiveAnLaufUndPaket() {
    service.ingest(
        USER,
        PROJECT,
        TOKEN,
        NightRunKind.INTERACTIVE,
        meldung(T1, true, null, item(1012, NightRunState.GREEN, null)));

    NightRun geschrieben = gemeldeterLauf(NightRunKind.INTERACTIVE);
    assertThat(geschrieben.kind()).isEqualTo(NightRunKind.INTERACTIVE);
    assertThat(runs.findItemsByRunIds(List.of(geschrieben.requireId())))
        .extracting(NightRunItem::kind)
        .containsExactly(NightRunKind.INTERACTIVE);
  }

  /**
   * Der Erfassungsbeginn traegt den <b>Startzeitpunkt der Sitzung</b> und nicht die Uhr des
   * Servers: Er markiert, ab wann erfasst wurde, und das ist der Zeitpunkt der ersten Sitzung
   * selbst. Ob der Wert schon steht, entscheidet der Port — der Service ruft ihn bei jeder Sitzung.
   */
  @Test
  void ingest_meldetDenErfassungsbeginnMitDemStartzeitpunktDerSitzung() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.INTERACTIVE, meldung(T1, true, null));

    verify(erfassungsbeginn).setInteractiveUsageSinceIfAbsent(PROJECT, T1);
  }

  /** Ein Nachtlauf sagt ueber die interaktive Nutzung nichts aus und ruehrt den Wert nicht an. */
  @Test
  void ingest_ruehrtDenErfassungsbeginnNichtAn_beiEinemNachtlauf() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null));

    verifyNoInteractions(erfassungsbeginn);
  }

  /** Auch der Upload-Weg liefert nur Nachtlaeufe ein — kein Erfassungsbeginn. */
  @Test
  void submit_ruehrtDenErfassungsbeginnNichtAn() {
    service.submit(USER, PROJECT, List.of(lauf(T1)));

    verifyNoInteractions(erfassungsbeginn);
  }

  /** Die Gattung bestimmt auch, welcher Ringpuffer gezogen wird (Issue #1011). */
  @Test
  void ingest_zieehtDieGrenzenDerSitzung_wennEineSitzungGemeldetWird() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.INTERACTIVE, meldung(T1, true, null));

    verify(runs).deleteOlderThanNewest(PROJECT, NightRunKind.INTERACTIVE, 400);
    verify(runs).deleteOrphanItemsOlderThanNewest(PROJECT, NightRunKind.INTERACTIVE, 4000);
  }

  // --- Die Nachtlauf-Seite sieht nur Nachtlaeufe (Issue #1012, Nicht-Ziel) ------------------

  /**
   * Die Laufliste speist die Nachtlauf-Seite und die Platte „Letzter Lauf". Sie muss dieselben
   * Ergebnisse liefern wie vor der Einlieferung von Sitzungen — sonst stuende nach der ersten
   * Sitzung eine Sitzung als „letzter Lauf" da.
   */
  @Test
  void list_zeigtNurNachtlaeufe_auchWennSitzungenDanebenLiegen() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null));
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.INTERACTIVE, meldung(T2, true, null));

    assertThat(service.list(USER, PROJECT))
        .extracting(NightRunService.NightRunView::startedAt)
        .containsExactly(T1);
  }

  /** Dasselbe fuer die Platte „Abbruchgruende": Eine rote Sitzung zaehlt dort nicht mit. */
  @Test
  void countRunsByErrorClass_zaehltNurNachtlaeufe() {
    service.ingest(
        USER,
        PROJECT,
        TOKEN,
        NightRunKind.NIGHT,
        meldung(T1, true, null, item(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED)));
    service.ingest(
        USER,
        PROJECT,
        TOKEN,
        NightRunKind.INTERACTIVE,
        meldung(T2, true, null, item(2, NightRunState.RED, NightRunErrorClass.CHECKS_RED)));

    assertThat(service.countRunsByErrorClass(USER, PROJECT))
        .containsExactly(Map.entry(NightRunErrorClass.CHECKS_RED, 1L));
  }

  @Test
  void ingest_zieehtDenRingpufferNach() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null));

    verify(runs).deleteOlderThanNewest(PROJECT, NightRunKind.NIGHT, 30);
  }

  @Test
  void ingest_reichtDenGemeldetenKostenbetragUnveraendertDurch() {
    NightRunUsage gemeldet =
        new NightRunUsage(new BigDecimal("8.032575"), 148L, 62_411L, 8_883_160L, null, null);

    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, gemeldet));

    NightRunUsage angekommen = gemeldeterLauf().usage();
    assertThat(angekommen).isNotNull();
    assertThat(angekommen.costUsd())
        .usingComparator(BigDecimal::compareTo)
        .isEqualTo(gemeldet.costUsd());
    assertThat(angekommen.inputTokens()).isEqualTo(148L);
    assertThat(angekommen.cachedInputTokens()).isEqualTo(8_883_160L);
  }

  /**
   * Der nicht zuordenbare Rest: Die Lauf-Summe ist groesser als die Summe ueber die Pakete, und
   * genau so muss sie ankommen. Rechnete der Service sie aus den Paketen, waere der Rest per
   * Konstruktion null und damit unsichtbar — die Auswertung braucht ihn aber als eigene Zahl.
   */
  @Test
  void ingest_normalisiertDenNichtZuordenbarenRestNichtWeg() {
    NightRunUsage laufSumme =
        new NightRunUsage(new BigDecimal("10.000000"), 1_000L, 100L, 900L, null, null);
    NightRunUsage paketAnteil =
        new NightRunUsage(new BigDecimal("4.000000"), 400L, 40L, 360L, null, null);

    service.ingest(
        USER,
        PROJECT,
        TOKEN,
        NightRunKind.NIGHT,
        meldung(T1, true, laufSumme, itemMitVerbrauch(721, paketAnteil)));

    NightRunUsage angekommen = gemeldeterLauf().usage();
    assertThat(angekommen).isNotNull();
    assertThat(angekommen.costUsd())
        .usingComparator(BigDecimal::compareTo)
        .isEqualTo(new BigDecimal("10.000000"));
    assertThat(runs.findItemsByRunIds(List.of(gemeldeterLauf().requireId())))
        .singleElement()
        .extracting(item -> item.usage().costUsd())
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
        .usingComparator(BigDecimal::compareTo)
        .isEqualTo(new BigDecimal("4.000000"));
  }

  // --- Budgets und Stufen durch den Dienst (Issue #1113) ------------------------------------

  private static final NightRunBudget VORGABEN =
      new NightRunBudget(
          30,
          30,
          25,
          10,
          new BigDecimal("50"),
          NightRunBudgetOrigin.DEFAULTED,
          List.of("paketeMin", "kostenUsd"));

  private static NightRunService.NewNightRunItem itemMitStufen(
      int cardNumber, NightRunItemStage... stufen) {
    return new NightRunService.NewNightRunItem(
        cardNumber,
        "Paket " + cardNumber,
        NightRunState.GREEN,
        null,
        5L,
        null,
        null,
        null,
        List.of(stufen));
  }

  private static NightRunItemStage stufe(NightRunStage stage) {
    return new NightRunItemStage(
        stage, 600_000L, new NightRunUsage(new BigDecimal("0.25"), 10L, 20L, 5L, 400L, 7));
  }

  /**
   * Der Kern des Pakets am Dienst: Budget und Stufen kommen aus der Meldung und werden nicht mehr
   * auf „nicht angegeben" festgehalten, wie Issue #1112 es bis hierher tat.
   */
  @Test
  void ingest_schreibtDasGemeldeteBudgetUndDieStufen() {
    service.ingest(
        USER,
        PROJECT,
        TOKEN,
        NightRunKind.NIGHT,
        meldung(
            T1,
            true,
            null,
            VORGABEN,
            itemMitStufen(993, stufe(NightRunStage.PLAN), stufe(NightRunStage.REVIEW))));

    NightRun geschrieben = gemeldeterLauf();
    assertThat(geschrieben.budget()).isEqualTo(VORGABEN);
    assertThat(runs.findItemsByRunIds(List.of(geschrieben.requireId())))
        .singleElement()
        .extracting(NightRunItem::stages)
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(NightRunItemStage.class))
        .extracting(NightRunItemStage::stage)
        .containsExactly(NightRunStage.PLAN, NightRunStage.REVIEW);
  }

  /** Die Sicht liefert beides aus — sonst haette die Anzeige nichts zu zeigen (AK 1, AK 2). */
  @Test
  void list_liefertBudgetUndStufenAus() {
    service.ingest(
        USER,
        PROJECT,
        TOKEN,
        NightRunKind.NIGHT,
        meldung(T1, true, null, VORGABEN, itemMitStufen(993, stufe(NightRunStage.PAKETE))));

    NightRunService.NightRunView sicht = service.list(USER, PROJECT).getFirst();

    assertThat(sicht.budget()).isEqualTo(VORGABEN);
    assertThat(sicht.items().getFirst().stages()).containsExactly(stufe(NightRunStage.PAKETE));
  }

  /** Ohne gemeldete Vorgaben steht „nicht angegeben" am Lauf und eine leere Liste am Vorgang. */
  @Test
  void list_laesstBudgetLeerUndStufenLeer_wennNichtsGemeldetWurde() {
    service.ingest(
        USER,
        PROJECT,
        TOKEN,
        NightRunKind.NIGHT,
        meldung(T1, true, null, item(993, NightRunState.GREEN, null)));

    NightRunService.NightRunView sicht = service.list(USER, PROJECT).getFirst();

    assertThat(sicht.budget()).isNull();
    assertThat(sicht.items().getFirst().stages()).isEmpty();
  }

  /**
   * Der Upload-Weg fuehrt beides nicht (E14). Der Dienst reicht durch, was der Controller uebergibt
   * — und das ist dort fest „nicht gemeldet".
   */
  @Test
  void submit_laesstBudgetUndStufenLeer() {
    service.submit(USER, PROJECT, List.of(lauf(T1, item(993, NightRunState.GREEN, null))));

    NightRunService.NightRunView sicht = service.list(USER, PROJECT).getFirst();
    assertThat(sicht.budget()).isNull();
    assertThat(sicht.items().getFirst().stages()).isEmpty();
  }

  @Test
  void ingest_reichtDasErgebnisDesSchreibwegsDurch() {
    assertThat(
            service
                .ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null))
                .created())
        .isTrue();
    assertThat(
            service
                .ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null))
                .created())
        .isFalse();
  }

  private static NightRunService.NewNightRunItem itemMitVerbrauch(
      int cardNumber, NightRunUsage usage) {
    return new NightRunService.NewNightRunItem(
        cardNumber,
        "Paket " + cardNumber,
        NightRunState.GREEN,
        null,
        5L,
        null,
        null,
        usage,
        List.of());
  }

  // --- Wiederkehrender Lauf: verwaiste Pakete vorher weg (Issue #965) --------------------

  /**
   * Ein verdraengter Lauf, der erneut hochgeladen wird, bringt seinen vollstaendigen Stand mit —
   * seine verwaisten Pakete muessen vorher weg, sonst stuenden sie doppelt da. Die Reihenfolge ist
   * die Zusage: Hinterher geloescht, trafe der Aufruf nichts mehr, weil die neuen Pakete schon am
   * Lauf haengen.
   */
  @Test
  void submit_loeschtVerwaistePaketeDesLaufs_bevorEsIhnAnlegt() {
    service.submit(USER, PROJECT, List.of(lauf(T1)));

    var reihenfolge = inOrder(runs);
    reihenfolge.verify(runs).deleteOrphanItemsOfRun(PROJECT, T1);
    reihenfolge.verify(runs).insertIfAbsent(any(NightRun.class), any());
  }

  @Test
  void ingest_loeschtVerwaistePaketeDesLaufs_bevorEsIhnAnlegt() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null));

    var reihenfolge = inOrder(runs);
    reihenfolge.verify(runs).deleteOrphanItemsOfRun(PROJECT, T1);
    reihenfolge.verify(runs).upsert(any(NightRun.class), any());
  }

  /** Am Fake belegt: Nach Verdraengung und erneutem Hochladen steht jede Karte genau einmal da. */
  @Test
  void submit_legtDiePaketeEinesVerdraengtenLaufsBeimWiederholenNichtDoppeltAn() {
    service = serviceMitPuffer(1);
    service.submit(USER, PROJECT, List.of(lauf(T2)));
    service.submit(
        USER,
        PROJECT,
        List.of(lauf(T1, item(721, NightRunState.RED, NightRunErrorClass.CHECKS_RED))));

    service.submit(
        USER,
        PROJECT,
        List.of(lauf(T1, item(721, NightRunState.RED, NightRunErrorClass.CHECKS_RED))));

    assertThat(((FakeNightRunRepository) runs).allePakete())
        .singleElement()
        .returns(721, NightRunItem::cardNumber)
        .returns(null, NightRunItem::nightRunId);
  }

  // --- Kappung der verwaisten Pakete (Issue #966) ------------------------------------------

  /**
   * Die Kappung folgt unmittelbar auf die Lauf-Verdraengung und schliesst den Vorgang ab: Erst
   * danach steht fest, welche Pakete verwaist sind.
   */
  @Test
  void submit_kapptVerwaistePaketeUnmittelbarNachDerVerdraengung() {
    service.submit(USER, PROJECT, List.of(lauf(T1)));

    var reihenfolge = inOrder(runs);
    reihenfolge.verify(runs).deleteOlderThanNewest(PROJECT, NightRunKind.NIGHT, 30);
    reihenfolge.verify(runs).deleteOrphanItemsOlderThanNewest(PROJECT, NightRunKind.NIGHT, 2000);
    reihenfolge.verifyNoMoreInteractions();
  }

  @Test
  void ingest_kapptVerwaistePaketeUnmittelbarNachDerVerdraengung() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null));

    var reihenfolge = inOrder(runs);
    reihenfolge.verify(runs).deleteOlderThanNewest(PROJECT, NightRunKind.NIGHT, 30);
    reihenfolge.verify(runs).deleteOrphanItemsOlderThanNewest(PROJECT, NightRunKind.NIGHT, 2000);
    reihenfolge.verifyNoMoreInteractions();
  }

  // --- Serialisierung des Ringpuffers (Issue #1090) ----------------------------------------

  /**
   * Die Projektsperre ist die <b>erste</b> Datenbankaktion jedes Schreibwegs. Zwei gleichzeitige
   * Meldungen verschiedener Laeufe desselben Projekts zaehlten einander sonst nicht mit, und nach
   * beiden Commits laege ein Lauf zu viel da. Die feste Reihenfolge haelt zugleich die
   * Sperrreihenfolge gerade: Projektzeile vor Laufzeile, nie umgekehrt.
   *
   * <p>Belegt wird die <b>ganze</b> Kette und nicht nur das erste Paar: Rutschte die Sperre hinter
   * einen der spaeteren Aufrufe, faellt das nur auf, wenn jeder von ihnen in der Reihenfolge steht.
   */
  @Test
  void submit_sperrtDasProjekt_vorJederAnderenDatenbankaktion() {
    service.submit(USER, PROJECT, List.of(lauf(T1)));

    var reihenfolge = inOrder(runs);
    reihenfolge.verify(runs).lockProject(PROJECT);
    reihenfolge.verify(runs).deleteOrphanItemsOfRun(PROJECT, T1);
    reihenfolge.verify(runs).insertIfAbsent(any(NightRun.class), any());
    reihenfolge.verify(runs).deleteOlderThanNewest(PROJECT, NightRunKind.NIGHT, 30);
    reihenfolge.verify(runs).deleteOrphanItemsOlderThanNewest(PROJECT, NightRunKind.NIGHT, 2000);
    reihenfolge.verifyNoMoreInteractions();
  }

  @Test
  void ingest_sperrtDasProjekt_vorJederAnderenDatenbankaktion() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null));

    var reihenfolge = inOrder(runs);
    reihenfolge.verify(runs).lockProject(PROJECT);
    reihenfolge.verify(runs).deleteOrphanItemsOfRun(PROJECT, T1);
    reihenfolge.verify(runs).upsert(any(NightRun.class), any());
    reihenfolge.verify(runs).deleteOlderThanNewest(PROJECT, NightRunKind.NIGHT, 30);
    reihenfolge.verify(runs).deleteOrphanItemsOlderThanNewest(PROJECT, NightRunKind.NIGHT, 2000);
    reihenfolge.verifyNoMoreInteractions();
  }

  // --- Getrennte Grenzen je Gattung (Issue #1011) ------------------------------------------

  /**
   * Beide Einlieferungswege tragen heute die Gattung {@code NIGHT} (Issue #1010) und muessen
   * deshalb deren Grenzen ziehen — nicht die der interaktiven Sitzung. Der Fall haelt fest, was
   * durchgereicht wird: Reichte der Service versehentlich die Sitzungs-Grenzen durch, verdraengten
   * Nachtlaeufe erst bei 400 statt bei 30, und das faellt sonst nirgends auf.
   */
  @Test
  void submit_zieehtDieGrenzenDerGattungNight_nichtDieDerSitzung() {
    service.submit(USER, PROJECT, List.of(lauf(T1)));

    verify(runs).deleteOlderThanNewest(PROJECT, NightRunKind.NIGHT, 30);
    verify(runs).deleteOrphanItemsOlderThanNewest(PROJECT, NightRunKind.NIGHT, 2000);
  }

  @Test
  void ingest_zieehtDieGrenzenDerGattungDesGemeldetenLaufs() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null));

    verify(runs).deleteOlderThanNewest(PROJECT, NightRunKind.NIGHT, 30);
    verify(runs).deleteOrphanItemsOlderThanNewest(PROJECT, NightRunKind.NIGHT, 2000);
  }

  /**
   * Am Fake belegt: Eine Gattung verdraengt nie die andere. Der Fake bekommt eine Sitzung
   * untergeschoben, die der Service selbst noch nicht einliefert — sie bleibt stehen, waehrend die
   * Nachtlaeufe auf ihre Grenze zusammenschrumpfen.
   */
  @Test
  void submit_verdraengtKeineSitzung_wennDieNachtlaeufeIhreGrenzeReissen() {
    FakeNightRunRepository fake = (FakeNightRunRepository) runs;
    fake.insertIfAbsent(sitzung(T4), List.of());
    service = serviceMitPuffer(1);

    service.submit(USER, PROJECT, List.of(lauf(T1)));
    service.submit(USER, PROJECT, List.of(lauf(T2)));
    service.submit(USER, PROJECT, List.of(lauf(T3)));

    assertThat(fake.alleLaeufe(PROJECT))
        .extracting(NightRun::startedAt, NightRun::kind)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(T4, NightRunKind.INTERACTIVE),
            org.assertj.core.groups.Tuple.tuple(T3, NightRunKind.NIGHT));
  }

  private static NightRun sitzung(Instant startedAt) {
    return new NightRun(
        null,
        PROJECT,
        startedAt,
        NightRunMode.INTERACTIVE,
        NightRunKind.INTERACTIVE,
        1_000L,
        1,
        0,
        0,
        null,
        FIXED,
        NightRunOrigin.TOKEN,
        TOKEN,
        true,
        FIXED,
        null,
        null,
        null);
  }

  // --- Anlaeufe einer Karte (Issue #967) -------------------------------------------------

  @Test
  void anlaeufeDerKarte_verlangtDenNachtlaufZugriff() {
    service.anlaeufeDerKarte(USER, PROJECT, 721);

    verify(permissions).requireNightRunAccess(USER, PROJECT);
  }

  @Test
  void anlaeufeDerKarte_liestNichts_ohneZugriff() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.anlaeufeDerKarte(USER, PROJECT, 721))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verifyNoInteractions(runs);
  }

  /** Ueber Laeufe hinweg, juengster zuerst — einschliesslich eines verdraengten Laufs. */
  @Test
  void anlaeufeDerKarte_liefertDieAnlaeufeUeberLaeufeHinweg_juengsterZuerst() {
    service = serviceMitPuffer(1);
    service.submit(
        USER,
        PROJECT,
        List.of(lauf(T1, item(721, NightRunState.RED, NightRunErrorClass.CHECKS_RED))));
    service.submit(USER, PROJECT, List.of(lauf(T2, item(721, NightRunState.GREEN, null))));
    service.submit(USER, PROJECT, List.of(lauf(T3, item(722, NightRunState.GREEN, null))));

    assertThat(service.anlaeufeDerKarte(USER, PROJECT, 721))
        .extracting(NightRunItem::startedAt, NightRunItem::state)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(T2, NightRunState.GREEN),
            org.assertj.core.groups.Tuple.tuple(T1, NightRunState.RED));
  }

  // --- Lauf ohne Arbeit: der Grund am Lauf (Issue #1068, Plan #1067) -----------------------

  /** Der Rueckfalltext des Servers (E4, AK 2): Ein Lauf ohne Arbeit steht nie ohne Text da. */
  private static final String RUECKFALL = "Nichts abgearbeitet — Grund unbekannt";

  private static final String GEMELDETER_GRUND = "Kein Eintrag trug das Label kit:nightrun";

  /** Eine Meldung, die nichts abgearbeitet hat: {@code processedCount = 0} (E5). */
  private static NightRunService.NewNightRun ohneArbeit(
      Instant startedAt, boolean complete, @Nullable String grund) {
    return new NightRunService.NewNightRun(
        startedAt,
        NightRunMode.CHAIN,
        1_000L,
        0,
        0,
        0,
        null,
        complete,
        null,
        grund,
        null,
        List.of());
  }

  /** Derselbe Fall auf dem Upload-Weg, der kein Grund-Feld kennt. */
  private static NightRunService.NewNightRun hochgeladenOhneArbeit(Instant startedAt) {
    return new NightRunService.NewNightRun(
        startedAt,
        NightRunMode.IMPLEMENTATION,
        1_000L,
        0,
        0,
        0,
        null,
        true,
        null,
        null,
        null,
        List.of());
  }

  @Test
  void ingest_setztDenRueckfalltext_wennEinNachtlaufOhneArbeitKeinenGrundMeldet() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, ohneArbeit(T1, true, null));

    assertThat(gemeldeterLauf().noWorkReason()).isEqualTo(RUECKFALL);
  }

  @Test
  void ingest_uebernimmtDenGemeldetenGrundWoertlich() {
    service.ingest(
        USER, PROJECT, TOKEN, NightRunKind.NIGHT, ohneArbeit(T1, true, GEMELDETER_GRUND));

    assertThat(gemeldeterLauf().noWorkReason()).isEqualTo(GEMELDETER_GRUND);
  }

  /**
   * Leer ist wie nicht gemeldet: Ein Runner, der das Feld mitschickt aber nicht fuellt, darf keinen
   * leeren Text an die Anzeige durchreichen — AK 2 verlangt einen Text, nicht ein Feld.
   */
  @Test
  void ingest_faelltAufDenRueckfalltextZurueck_wennDerGemeldeteGrundLeerIst() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, ohneArbeit(T1, true, "   "));

    assertThat(gemeldeterLauf().noWorkReason()).isEqualTo(RUECKFALL);
  }

  @Test
  void ingest_laesstDenGrundLeer_wennDerLaufGearbeitetHat() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, meldung(T1, true, null));

    assertThat(gemeldeterLauf().noWorkReason()).isNull();
  }

  @Test
  void ingest_laesstDenGrundLeer_wennDerLaufNichtAbgeschlossenIst() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, ohneArbeit(T1, false, null));

    assertThat(gemeldeterLauf().noWorkReason()).isNull();
  }

  /**
   * Die Gattung entscheidet vor dem gemeldeten Wert (E6): Eine Sitzung arbeitet keine Pakete ab,
   * ihre 0 ist der Normalfall und kein Befund. Der Grund wird hier absichtlich <b>mitgemeldet</b> —
   * eine Regel, die nur auf den fehlenden Grund sieht, bestuende den Fall sonst zufaellig.
   */
  @Test
  void ingest_laesstDenGrundLeer_beiEinerInteraktivenSitzung() {
    service.ingest(
        USER, PROJECT, TOKEN, NightRunKind.INTERACTIVE, ohneArbeit(T1, true, GEMELDETER_GRUND));

    assertThat(gemeldeterLauf(NightRunKind.INTERACTIVE).noWorkReason()).isNull();
  }

  @Test
  void submit_setztDenRueckfalltext_beiEinemHochgeladenenLaufOhneArbeit() {
    service.submit(USER, PROJECT, List.of(hochgeladenOhneArbeit(T1)));

    assertThat(gemeldeterLauf().noWorkReason()).isEqualTo(RUECKFALL);
  }

  @Test
  void list_reichtDenGrundInDieSichtDurch() {
    service.ingest(USER, PROJECT, TOKEN, NightRunKind.NIGHT, ohneArbeit(T1, true, null));

    assertThat(service.list(USER, PROJECT))
        .extracting(NightRunService.NightRunView::noWorkReason)
        .containsExactly(RUECKFALL);
  }

  static class FakeNightRunRepository implements NightRunRepository {

    private final List<NightRun> gespeicherteLaeufe = new ArrayList<>();
    private final List<NightRunItem> gespeichertePakete = new ArrayList<>();
    private long naechsteLaufId = 1L;
    private long naechstePaketId = 1L;

    /**
     * Ohne Wirkung am Fake: Was die Sperre leistet, leistet allein die Datenbank (Issue #1090).
     * Hier zaehlt nur, <em>dass</em> und <em>wann</em> sie gerufen wird — das belegt der {@code
     * InOrder}-Fall, den nebenlaeufigen Beweis fuehrt {@code NightRunNebenlaufIT}.
     */
    @Override
    public void lockProject(long projectId) {
      // bewusst leer
    }

    @Override
    public UpsertResult upsert(NightRun run, List<NightRunItem> items) {
      Optional<NightRun> vorhanden =
          gespeicherteLaeufe.stream()
              .filter(
                  r ->
                      r.projectId().equals(run.projectId())
                          && r.startedAt().equals(run.startedAt()))
              .findFirst();
      if (vorhanden.isEmpty()) {
        return new UpsertResult(insertIfAbsent(run, items).orElseThrow(), true);
      }
      long id = vorhanden.get().requireId();
      gespeicherteLaeufe.remove(vorhanden.get());
      gespeichertePakete.removeIf(item -> Objects.equals(item.nightRunId(), id));
      gespeicherteLaeufe.add(
          new NightRun(
              id,
              run.projectId(),
              run.startedAt(),
              run.mode(),
              run.kind(),
              run.durationMs(),
              run.processedCount(),
              run.skippedCount(),
              run.unparsedCount(),
              run.unparsedSample(),
              // created_at der ersten Meldung, nicht der jetzigen.
              vorhanden.get().createdAt(),
              run.origin(),
              run.tokenName(),
              run.complete(),
              run.updatedAt(),
              run.usage(),
              run.noWorkReason(),
              run.budget()));
      for (NightRunItem item : items) {
        gespeichertePakete.add(paket(item, run, id));
      }
      return new UpsertResult(id, false);
    }

    @Override
    public Optional<Long> insertIfAbsent(NightRun run, List<NightRunItem> items) {
      boolean bekannt =
          gespeicherteLaeufe.stream()
              .anyMatch(
                  r ->
                      r.projectId().equals(run.projectId())
                          && r.startedAt().equals(run.startedAt()));
      if (bekannt) {
        return Optional.empty();
      }
      long id = naechsteLaufId;
      naechsteLaufId += 1;
      gespeicherteLaeufe.add(
          new NightRun(
              id,
              run.projectId(),
              run.startedAt(),
              run.mode(),
              run.kind(),
              run.durationMs(),
              run.processedCount(),
              run.skippedCount(),
              run.unparsedCount(),
              run.unparsedSample(),
              run.createdAt(),
              // Uebernommen statt erfunden: Ein Fake, der hier feste Werte setzte, machte jeden
              // Test darueber blind fuer das, was der Service tatsaechlich schreibt.
              run.origin(),
              run.tokenName(),
              run.complete(),
              run.updatedAt(),
              run.usage(),
              run.noWorkReason(),
              run.budget()));
      for (NightRunItem item : items) {
        gespeichertePakete.add(paket(item, run, id));
      }
      return Optional.of(id);
    }

    /**
     * Vergibt eine Id und uebernimmt alle uebergebenen Werte — auch den Verbrauch. Projekt,
     * Startzeitpunkt und Lauf-Art kommen wie im Adapter aus dem Lauf (Issue #964).
     */
    private NightRunItem paket(NightRunItem item, NightRun run, long runId) {
      long paketId = naechstePaketId;
      naechstePaketId += 1;
      return new NightRunItem(
          paketId,
          runId,
          run.projectId(),
          run.startedAt(),
          run.mode(),
          run.kind(),
          item.cardNumber(),
          item.title(),
          item.state(),
          item.errorClass(),
          item.durationMs(),
          item.commitHash(),
          item.excerpt(),
          item.usage(),
          item.stages());
    }

    @Override
    public List<NightRun> findByProjectAndKindOrderByStartedAtDesc(
        long projectId, NightRunKind kind) {
      return alleLaeufe(projectId).stream().filter(r -> r.kind() == kind).toList();
    }

    /**
     * Alle Laeufe des Projekts ueber beide Gattungen hinweg — der Port bietet das nicht mehr an
     * (Issue #1012), der Fake braucht es fuer die Verdraengung und die Faelle, die belegen, dass
     * eine Gattung die andere stehen laesst.
     */
    List<NightRun> alleLaeufe(long projectId) {
      return gespeicherteLaeufe.stream()
          .filter(r -> r.projectId() == projectId)
          .sorted(
              Comparator.comparing(NightRun::startedAt)
                  .thenComparing(NightRun::requireId)
                  .reversed())
          .toList();
    }

    @Override
    public List<NightRunItem> findItemsByRunIds(Collection<Long> runIds) {
      return gespeichertePakete.stream()
          .filter(i -> i.nightRunId() != null && runIds.contains(i.nightRunId()))
          .sorted(Comparator.comparing(NightRunItem::requireId))
          .toList();
    }

    @Override
    public int deleteOlderThanNewest(long projectId, NightRunKind kind, int keep) {
      List<NightRun> zuVerdraengen =
          findByProjectAndKindOrderByStartedAtDesc(projectId, kind).stream().skip(keep).toList();
      Set<Long> ids = zuVerdraengen.stream().map(NightRun::requireId).collect(Collectors.toSet());
      gespeicherteLaeufe.removeAll(zuVerdraengen);
      // ON DELETE SET NULL (Issue #964): Die Pakete bleiben verwaist stehen.
      gespeichertePakete.replaceAll(i -> ids.contains(i.nightRunId()) ? verwaist(i) : i);
      return zuVerdraengen.size();
    }

    private static NightRunItem verwaist(NightRunItem item) {
      return new NightRunItem(
          item.id(),
          null,
          item.projectId(),
          item.startedAt(),
          item.mode(),
          item.kind(),
          item.cardNumber(),
          item.title(),
          item.state(),
          item.errorClass(),
          item.durationMs(),
          item.commitHash(),
          item.excerpt(),
          item.usage(),
          // Die Stufen haengen am Paket, nicht am Lauf (V37): Ein verwaistes Paket behaelt sie.
          item.stages());
    }

    @Override
    public int deleteOrphanItemsOfRun(long projectId, Instant startedAt) {
      int vorher = gespeichertePakete.size();
      gespeichertePakete.removeIf(
          i ->
              i.nightRunId() == null
                  && i.projectId() == projectId
                  && i.startedAt().equals(startedAt));
      return vorher - gespeichertePakete.size();
    }

    @Override
    public int deleteOrphanItemsOlderThanNewest(long projectId, NightRunKind kind, int keep) {
      List<NightRunItem> zuKappen =
          gespeichertePakete.stream()
              .filter(i -> i.nightRunId() == null && i.projectId() == projectId && i.kind() == kind)
              .sorted(
                  Comparator.comparing(NightRunItem::startedAt)
                      .thenComparing(NightRunItem::requireId)
                      .reversed())
              .skip(keep)
              .toList();
      gespeichertePakete.removeAll(zuKappen);
      return zuKappen.size();
    }

    @Override
    public List<NightRunItem> findByCard(long projectId, int cardNumber) {
      return gespeichertePakete.stream()
          .filter(i -> i.projectId() == projectId && i.cardNumber() == cardNumber)
          .sorted(
              Comparator.comparing(NightRunItem::startedAt)
                  .thenComparing(NightRunItem::requireId)
                  .reversed())
          .toList();
    }

    /** Alle Pakete, auch verwaiste — die Verdraengung ist sonst ueber keinen Port sichtbar. */
    List<NightRunItem> allePakete() {
      return List.copyOf(gespeichertePakete);
    }

    @Override
    public Map<NightRunErrorClass, Long> countRunsByErrorClass(long projectId, NightRunKind kind) {
      Set<Long> laufIds =
          findByProjectAndKindOrderByStartedAtDesc(projectId, kind).stream()
              .map(NightRun::requireId)
              .collect(Collectors.toSet());
      Map<NightRunErrorClass, Long> counts = new EnumMap<>(NightRunErrorClass.class);
      gespeichertePakete.stream()
          .filter(i -> laufIds.contains(i.nightRunId()) && i.errorClass() != null)
          .map(i -> Map.entry(i.errorClass(), i.nightRunId()))
          .distinct()
          .forEach(e -> counts.merge(e.getKey(), 1L, Long::sum));
      return counts;
    }
  }

  /**
   * Der Upload-Weg ist die menschliche Herkunft, und ein hochgeladener Lauf gilt als abgeschlossen:
   * Der Browser liefert einen unvollstaendigen gar nicht erst ein. {@code updatedAt} bleibt leer,
   * weil dieser Weg nichts fortschreibt.
   */
  @Test
  void submit_schreibtMenschlicheHerkunft_undGiltAlsVollstaendig() {
    service.submit(USER, PROJECT, List.of(lauf(T1)));

    NightRun geschrieben = gemeldeterLauf();
    assertThat(geschrieben.origin()).isEqualTo(NightRunOrigin.UPLOAD);
    assertThat(geschrieben.complete()).isTrue();
    assertThat(geschrieben.updatedAt()).isNull();
    assertThat(geschrieben.tokenName()).isNull();
  }

  /** Auch der Upload-Weg liefert heute nur Nachtlaeufe ein (Issue #1010). */
  @Test
  void submit_schreibtDieGattungNightAnLaufUndPaket() {
    service.submit(USER, PROJECT, List.of(lauf(T1, item(721, NightRunState.GREEN, null))));

    NightRun geschrieben = gemeldeterLauf();
    assertThat(geschrieben.kind()).isEqualTo(NightRunKind.NIGHT);
    assertThat(runs.findItemsByRunIds(List.of(geschrieben.requireId())))
        .extracting(NightRunItem::kind)
        .containsExactly(NightRunKind.NIGHT);
  }
}
