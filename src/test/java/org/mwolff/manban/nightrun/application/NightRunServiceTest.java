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
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
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
// Zahl zu druecken. Issue #946 bringt sieben Faelle fuer den meldenden Weg dazu.
@SuppressWarnings("PMD.TooManyMethods")
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
  private NightRunService service;

  @BeforeEach
  void setUp() {
    runs = spy(new FakeNightRunRepository());
    permissions = mock(PermissionChecker.class);
    service = serviceMitPuffer(30);
  }

  private NightRunService serviceMitPuffer(int maxPerProject) {
    return new NightRunService(
        runs,
        permissions,
        new NightRunProperties(maxPerProject),
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
  void list_requiresOwner() {
    service.list(USER, PROJECT);

    verify(permissions).requireOwner(USER, PROJECT);
  }

  @Test
  void list_touchesNoRepository_whenMemberIsNotOwner() {
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireOwner(USER, PROJECT);

    assertThatThrownBy(() -> service.list(USER, PROJECT))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verifyNoInteractions(runs);
  }

  @Test
  void list_touchesNoRepository_whenUserIsNoMember() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireOwner(USER, PROJECT);

    assertThatThrownBy(() -> service.list(USER, PROJECT))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(runs);
  }

  @Test
  void countRunsByErrorClass_requiresOwner() {
    service.countRunsByErrorClass(USER, PROJECT);

    verify(permissions).requireOwner(USER, PROJECT);
  }

  @Test
  void countRunsByErrorClass_touchesNoRepository_whenMemberIsNotOwner() {
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireOwner(USER, PROJECT);

    assertThatThrownBy(() -> service.countRunsByErrorClass(USER, PROJECT))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verifyNoInteractions(runs);
  }

  @Test
  void countRunsByErrorClass_touchesNoRepository_whenUserIsNoMember() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireOwner(USER, PROJECT);

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
        null);
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
    return new NightRunService.NewNightRun(
        startedAt, NightRunMode.CHAIN, 1_000L, 1, 0, 0, null, complete, usage, List.of(items));
  }

  private NightRun gemeldeterLauf() {
    return runs.findByProjectOrderByStartedAtDesc(PROJECT).getFirst();
  }

  @Test
  void ingest_verlangtDenBesitzer() {
    service.ingest(USER, PROJECT, TOKEN, meldung(T1, true, null));

    verify(permissions).requireOwner(USER, PROJECT);
  }

  @Test
  void ingest_schreibtNichts_wennDerBesitzerFehlt() {
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireOwner(USER, PROJECT);

    assertThatThrownBy(() -> service.ingest(USER, PROJECT, TOKEN, meldung(T1, true, null)))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verifyNoInteractions(runs);
  }

  @Test
  void ingest_schreibtMaschinelleHerkunftMitTokennamenUndZeitpunkt() {
    service.ingest(USER, PROJECT, TOKEN, meldung(T1, false, null));

    NightRun geschrieben = gemeldeterLauf();
    assertThat(geschrieben.origin()).isEqualTo(NightRunOrigin.TOKEN);
    assertThat(geschrieben.tokenName()).isEqualTo(TOKEN);
    assertThat(geschrieben.updatedAt()).isEqualTo(FIXED);
    assertThat(geschrieben.complete()).isFalse();
  }

  @Test
  void ingest_zieehtDenRingpufferNach() {
    service.ingest(USER, PROJECT, TOKEN, meldung(T1, true, null));

    verify(runs).deleteOlderThanNewest(PROJECT, 30);
  }

  @Test
  void ingest_reichtDenGemeldetenKostenbetragUnveraendertDurch() {
    NightRunUsage gemeldet =
        new NightRunUsage(new BigDecimal("8.032575"), 148L, 62_411L, 8_883_160L);

    service.ingest(USER, PROJECT, TOKEN, meldung(T1, true, gemeldet));

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
    NightRunUsage laufSumme = new NightRunUsage(new BigDecimal("10.000000"), 1_000L, 100L, 900L);
    NightRunUsage paketAnteil = new NightRunUsage(new BigDecimal("4.000000"), 400L, 40L, 360L);

    service.ingest(
        USER, PROJECT, TOKEN, meldung(T1, true, laufSumme, itemMitVerbrauch(721, paketAnteil)));

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

  @Test
  void ingest_reichtDasErgebnisDesSchreibwegsDurch() {
    assertThat(service.ingest(USER, PROJECT, TOKEN, meldung(T1, true, null)).created()).isTrue();
    assertThat(service.ingest(USER, PROJECT, TOKEN, meldung(T1, true, null)).created()).isFalse();
  }

  private static NightRunService.NewNightRunItem itemMitVerbrauch(
      int cardNumber, NightRunUsage usage) {
    return new NightRunService.NewNightRunItem(
        cardNumber, "Paket " + cardNumber, NightRunState.GREEN, null, 5L, null, null, usage);
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
    service.ingest(USER, PROJECT, TOKEN, meldung(T1, true, null));

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

  static class FakeNightRunRepository implements NightRunRepository {

    private final List<NightRun> gespeicherteLaeufe = new ArrayList<>();
    private final List<NightRunItem> gespeichertePakete = new ArrayList<>();
    private long naechsteLaufId = 1L;
    private long naechstePaketId = 1L;

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
              run.usage()));
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
              run.usage()));
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
          item.cardNumber(),
          item.title(),
          item.state(),
          item.errorClass(),
          item.durationMs(),
          item.commitHash(),
          item.excerpt(),
          item.usage());
    }

    @Override
    public List<NightRun> findByProjectOrderByStartedAtDesc(long projectId) {
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
    public int deleteOlderThanNewest(long projectId, int keep) {
      List<NightRun> zuVerdraengen =
          findByProjectOrderByStartedAtDesc(projectId).stream().skip(keep).toList();
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
          item.cardNumber(),
          item.title(),
          item.state(),
          item.errorClass(),
          item.durationMs(),
          item.commitHash(),
          item.excerpt(),
          item.usage());
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

    /** Alle Pakete, auch verwaiste — die Verdraengung ist sonst ueber keinen Port sichtbar. */
    List<NightRunItem> allePakete() {
      return List.copyOf(gespeichertePakete);
    }

    @Override
    public Map<NightRunErrorClass, Long> countRunsByErrorClass(long projectId) {
      Set<Long> laufIds =
          findByProjectOrderByStartedAtDesc(projectId).stream()
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

    NightRun geschrieben = runs.findByProjectOrderByStartedAtDesc(PROJECT).getFirst();
    assertThat(geschrieben.origin()).isEqualTo(NightRunOrigin.UPLOAD);
    assertThat(geschrieben.complete()).isTrue();
    assertThat(geschrieben.updatedAt()).isNull();
    assertThat(geschrieben.tokenName()).isNull();
  }
}
