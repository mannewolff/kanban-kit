package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
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
        startedAt, NightRunMode.IMPLEMENTATION, 1_000L, 2, 1, 3, "Rest", List.of(items));
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
        "Auszug " + cardNumber);
  }

  /**
   * Speichernder Ersatz des Ports mit der Semantik des Adapters: {@code insertIfAbsent} weist einen
   * schon vorhandenen {@code (projectId, startedAt)} ab, {@code deleteOlderThanNewest} verdrängt
   * nach {@code startedAt} absteigend.
   */
  static class FakeNightRunRepository implements NightRunRepository {

    private final List<NightRun> gespeicherteLaeufe = new ArrayList<>();
    private final List<NightRunItem> gespeichertePakete = new ArrayList<>();
    private long naechsteLaufId = 1L;
    private long naechstePaketId = 1L;

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
              run.createdAt()));
      for (NightRunItem item : items) {
        long paketId = naechstePaketId;
        naechstePaketId += 1;
        gespeichertePakete.add(
            new NightRunItem(
                paketId,
                id,
                item.cardNumber(),
                item.title(),
                item.state(),
                item.errorClass(),
                item.durationMs(),
                item.commitHash(),
                item.excerpt()));
      }
      return Optional.of(id);
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
          .filter(i -> runIds.contains(i.nightRunId()))
          .sorted(Comparator.comparing(NightRunItem::requireId))
          .toList();
    }

    @Override
    public int deleteOlderThanNewest(long projectId, int keep) {
      List<NightRun> zuVerdraengen =
          findByProjectOrderByStartedAtDesc(projectId).stream().skip(keep).toList();
      Set<Long> ids = zuVerdraengen.stream().map(NightRun::requireId).collect(Collectors.toSet());
      gespeicherteLaeufe.removeAll(zuVerdraengen);
      gespeichertePakete.removeIf(i -> ids.contains(i.nightRunId()));
      return zuVerdraengen.size();
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
}
