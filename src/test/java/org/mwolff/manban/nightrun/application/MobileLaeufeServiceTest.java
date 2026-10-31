package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardSummary;
import org.mwolff.manban.nightrun.application.MobileLaeufeService.LaufDetail;
import org.mwolff.manban.nightrun.application.MobileLaeufeService.LaufZeile;
import org.mwolff.manban.nightrun.domain.Meldegrund.Zustand;
import org.mwolff.manban.nightrun.domain.MobilerLauf.LetzteMeldung;
import org.mwolff.manban.nightrun.domain.MobilerLauf.MeldungsArt;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.AccessibleProject;
import org.mwolff.manban.project.application.RolePermissionRepository;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;

/**
 * Die Läufe für die mobile Oberfläche (Issue #1994, Plan #1989 E9 und E19).
 *
 * <p>Die Rechteprüfung ist die echte {@link PermissionChecker}-Regel über gestellte Repositories:
 * Wer welche Läufe sieht, soll hier an Rollen und Teilnahme hängen, nicht an einem gestellten
 * Ja/Nein.
 */
// PMD.ExcessiveImports, PMD.CouplingBetweenObjects: Der Test verdrahtet die echte
// PermissionChecker-Regel mit ihren gestellten Ports (Mitgliedschaft, Plattform-Admin, Projekt) —
// nur so hängt die Sicht an Rolle und Teilnahme statt an einem gestellten Ja/Nein. Die Kopplung
// zählt diese Typen, kein Design-Smell.
@SuppressWarnings({"PMD.ExcessiveImports", "PMD.CouplingBetweenObjects"})
class MobileLaeufeServiceTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  /** 09:00 in Berlin: Die laufende Nacht begann am 28.10. um 12:00, die vorige am 27.10. */
  private static final Instant JETZT = Instant.parse("2026-10-29T08:00:00Z");

  private static final long OWNER = 1L;
  private static final long ADMIN = 2L;
  private static final long MITGLIED = 3L;
  private static final long PROJEKT = 7L;
  private static final long ANDERES = 8L;
  private static final long BOARD = 40L;

  private NightRunRepository runs;
  private ProjectMembershipRepository memberships;
  private PlatformAdminChecker platformAdminChecker;
  private ProjectRepository projectRepository;
  private ProjectService projects;
  private BoardService boards;
  private MobileLaeufeService service;
  private final List<NightRun> laeufe = new ArrayList<>();
  private final List<NightRunItem> pakete = new ArrayList<>();

  @BeforeEach
  void setUp() {
    runs = mock(NightRunRepository.class);
    memberships = mock(ProjectMembershipRepository.class);
    platformAdminChecker = mock(PlatformAdminChecker.class);
    projectRepository = mock(ProjectRepository.class);
    projects = mock(ProjectService.class);
    boards = mock(BoardService.class);
    when(memberships.findByProjectIdAndUserId(anyLong(), anyLong())).thenReturn(Optional.empty());
    when(projectRepository.findById(anyLong())).thenReturn(Optional.empty());
    when(boards.requireBoardSummary(BOARD)).thenReturn(new BoardSummary(BOARD, "Produkt", false));
    when(runs.findByProjectAndKindOrderByStartedAtDesc(anyLong(), any(NightRunKind.class)))
        .thenAnswer(
            inv ->
                laeufe.stream()
                    .filter(r -> r.projectId() == (long) inv.getArgument(0))
                    .filter(r -> r.kind() == inv.getArgument(1))
                    .toList());
    when(runs.findItemsByRunIds(any()))
        .thenAnswer(
            inv -> {
              List<Long> ids = inv.getArgument(0);
              return pakete.stream().filter(p -> ids.contains(p.nightRunId())).toList();
            });
    when(runs.findByIdAndProjectId(anyLong(), anyLong()))
        .thenAnswer(
            inv ->
                laeufe.stream()
                    .filter(r -> r.requireId() == (long) inv.getArgument(0))
                    .filter(r -> r.projectId() == (long) inv.getArgument(1))
                    .findFirst());
    Clock clock = Clock.fixed(JETZT, ZoneOffset.UTC);
    PermissionChecker permissions =
        new PermissionChecker(
            memberships,
            mock(RolePermissionRepository.class),
            platformAdminChecker,
            projectRepository,
            clock);
    service =
        new MobileLaeufeService(
            runs,
            permissions,
            projects,
            boards,
            new NightRunProperties(null, null, null, null, null),
            clock);
  }

  // --- Vorrichtungen -------------------------------------------------------------------------

  private void sieht(long userId, long... projektIds) {
    List<AccessibleProject> liste = new ArrayList<>();
    for (long id : projektIds) {
      liste.add(new AccessibleProject(id, "Projekt " + id));
    }
    when(projects.listAccessible(userId)).thenReturn(liste);
  }

  private void rolle(long userId, long projektId, ProjectRole rolle) {
    when(memberships.findByProjectIdAndUserId(projektId, userId))
        .thenReturn(Optional.of(new ProjectMembership(1L, projektId, userId, rolle, JETZT)));
  }

  private void teilnahme(long projektId) {
    when(projectRepository.findById(projektId))
        .thenReturn(Optional.of(new Project(projektId, "P", 9L, JETZT, null, true, null)));
  }

  private NightRun lauf(long id, long projektId, Instant start, boolean complete) {
    return lauf(id, projektId, NightRunKind.NIGHT, start, start.plusSeconds(60), complete, null);
  }

  private NightRun lauf(
      long id,
      long projektId,
      NightRunKind kind,
      Instant start,
      @Nullable Instant updatedAt,
      boolean complete,
      @Nullable String abortReason) {
    return lauf(id, projektId, kind, start, updatedAt, complete, abortReason, null);
  }

  private NightRun lauf(
      long id,
      long projektId,
      NightRunKind kind,
      Instant start,
      @Nullable Instant updatedAt,
      boolean complete,
      @Nullable String abortReason,
      @Nullable Instant closedAt) {
    NightRun run =
        new NightRun(
            id,
            projektId,
            start,
            NightRunMode.IMPLEMENTATION,
            kind,
            3_600_000,
            0,
            0,
            0,
            null,
            start,
            NightRunOrigin.TOKEN,
            "runner",
            complete,
            updatedAt,
            null,
            null,
            null,
            abortReason,
            null,
            null,
            List.of(),
            closedAt,
            BOARD);
    laeufe.add(run);
    return run;
  }

  private void paket(
      long laufId,
      int karte,
      NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable String excerpt) {
    pakete.add(
        new NightRunItem(
            laufId * 100 + karte,
            laufId,
            PROJEKT,
            JETZT,
            NightRunMode.IMPLEMENTATION,
            NightRunKind.NIGHT,
            karte,
            "Paket " + karte,
            state,
            errorClass,
            1_000L,
            null,
            excerpt,
            null,
            null,
            null,
            List.of(),
            List.of()));
  }

  private static Instant vorMinuten(long minuten) {
    return JETZT.minus(Duration.ofMinutes(minuten));
  }

  // --- Liste --------------------------------------------------------------------------------

  @Test
  void derOwnerSiehtDenLaufendenLaufMitProjektBoardUndDauer() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    lauf(11L, PROJEKT, NightRunKind.NIGHT, vorMinuten(20), vorMinuten(5), false, null);

    assertThat(service.laeufe(OWNER, BERLIN))
        .containsExactly(
            new LaufZeile(
                11L,
                PROJEKT,
                "Projekt 7",
                BOARD,
                "Produkt",
                Zustand.LAEUFT,
                vorMinuten(20),
                Duration.ofMinutes(20)));
  }

  @Test
  @DisplayName("Szenario: Lauf verstummt")
  void szenarioLaufVerstummt() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    lauf(11L, PROJEKT, NightRunKind.NIGHT, vorMinuten(150), vorMinuten(91), false, null);

    List<LaufZeile> liste = service.laeufe(OWNER, BERLIN);

    assertThat(liste).extracting(LaufZeile::zustand).containsExactly(Zustand.GESCHEITERT);
    // Die Dauer endet am letzten Lebenszeichen, nicht jetzt.
    assertThat(liste.getFirst().dauer()).isEqualTo(Duration.ofMinutes(59));
  }

  @Test
  void dieListeZeigtHaengendeUndGescheiterte_undLaesstGelungeneWeg() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    lauf(11L, PROJEKT, vorMinuten(300), true);
    paket(11L, 1, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION, null);
    lauf(12L, PROJEKT, vorMinuten(200), true);
    paket(12L, 2, NightRunState.RED, NightRunErrorClass.CHECKS_RED, null);
    lauf(13L, PROJEKT, vorMinuten(100), true);
    paket(13L, 3, NightRunState.GREEN, null, null);

    List<LaufZeile> liste = service.laeufe(OWNER, BERLIN);

    assertThat(liste).extracting(LaufZeile::laufId).containsExactly(12L, 11L);
    assertThat(liste)
        .extracting(LaufZeile::zustand)
        .containsExactly(Zustand.GESCHEITERT, Zustand.HAENGT);
    assertThat(liste.getFirst().dauer()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void einRotesPaketZeigtDenLaufendenLaufSchonAlsGescheitert() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    lauf(11L, PROJEKT, NightRunKind.NIGHT, vorMinuten(30), vorMinuten(1), false, null);
    paket(11L, 1, NightRunState.RED, NightRunErrorClass.CHECKS_RED, null);

    assertThat(service.laeufe(OWNER, BERLIN))
        .extracting(LaufZeile::zustand)
        .containsExactly(Zustand.GESCHEITERT);
  }

  /** Die vorige Nacht zählt mit, die davor nicht; ebenso wenig ein Lauf nach der laufenden. */
  @Test
  void dieListeReichtUeberDieLaufendeUndDieVorigeNacht() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    Instant vorigeNacht = Instant.parse("2026-10-27T11:00:00Z"); // 27.10. 12:00 Berlin
    lauf(11L, PROJEKT, vorigeNacht, true);
    paket(11L, 1, NightRunState.RED, NightRunErrorClass.CHECKS_RED, null);
    lauf(12L, PROJEKT, vorigeNacht.minusSeconds(1), true);
    paket(12L, 2, NightRunState.RED, NightRunErrorClass.CHECKS_RED, null);
    lauf(13L, PROJEKT, Instant.parse("2026-10-29T11:00:00Z"), true);
    paket(13L, 3, NightRunState.RED, NightRunErrorClass.CHECKS_RED, null);

    assertThat(service.laeufe(OWNER, BERLIN)).extracting(LaufZeile::laufId).containsExactly(11L);
  }

  @Test
  void dieListeFragtNurNachtlaeufe() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    lauf(11L, PROJEKT, NightRunKind.INTERACTIVE, vorMinuten(20), vorMinuten(5), false, null);

    assertThat(service.laeufe(OWNER, BERLIN)).isEmpty();
    verify(runs).findByProjectAndKindOrderByStartedAtDesc(PROJEKT, NightRunKind.NIGHT);
    verify(runs, never()).findItemsByRunIds(any());
  }

  @Test
  void einLaufOhneBoardTraegtKeinBoard_undDerBoardnameWirdJeBoardEinmalGeholt() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    lauf(11L, PROJEKT, NightRunKind.NIGHT, vorMinuten(20), vorMinuten(5), false, null);
    lauf(12L, PROJEKT, NightRunKind.NIGHT, vorMinuten(10), vorMinuten(5), false, null);
    NightRun ohneBoard = lauf(13L, PROJEKT, vorMinuten(5), false);
    laeufe.set(laeufe.indexOf(ohneBoard), ohneBoard(ohneBoard));

    List<LaufZeile> liste = service.laeufe(OWNER, BERLIN);

    assertThat(liste).extracting(LaufZeile::boardId).containsExactly(null, BOARD, BOARD);
    assertThat(liste).extracting(LaufZeile::boardName).containsExactly(null, "Produkt", "Produkt");
    verify(boards, times(1)).requireBoardSummary(BOARD);
  }

  @Test
  void dieListeMischtProjekte_juengsterZuoberst() {
    sieht(OWNER, PROJEKT, ANDERES);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    rolle(OWNER, ANDERES, ProjectRole.OWNER);
    lauf(11L, PROJEKT, NightRunKind.NIGHT, vorMinuten(30), vorMinuten(1), false, null);
    lauf(21L, ANDERES, NightRunKind.NIGHT, vorMinuten(20), vorMinuten(1), false, null);
    lauf(12L, PROJEKT, NightRunKind.NIGHT, vorMinuten(10), vorMinuten(1), false, null);

    assertThat(service.laeufe(OWNER, BERLIN))
        .extracting(LaufZeile::laufId)
        .containsExactly(12L, 21L, 11L);
  }

  // --- Wer sieht was (E9) -------------------------------------------------------------------

  @Test
  void derPlattformAdminSiehtDieLaeufeTeilnehmenderProjekte() {
    sieht(ADMIN, PROJEKT, ANDERES);
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    teilnahme(PROJEKT);
    lauf(11L, PROJEKT, NightRunKind.NIGHT, vorMinuten(20), vorMinuten(1), false, null);
    lauf(21L, ANDERES, NightRunKind.NIGHT, vorMinuten(20), vorMinuten(1), false, null);

    assertThat(service.laeufe(ADMIN, BERLIN)).extracting(LaufZeile::laufId).containsExactly(11L);
    verify(runs, never()).findByProjectAndKindOrderByStartedAtDesc(ANDERES, NightRunKind.NIGHT);
  }

  @Test
  void derPlattformAdminOhneTeilnahmeSiehtNichts() {
    sieht(ADMIN, PROJEKT);
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    lauf(11L, PROJEKT, NightRunKind.NIGHT, vorMinuten(20), vorMinuten(1), false, null);

    assertThat(service.laeufe(ADMIN, BERLIN)).isEmpty();
    assertThatThrownBy(() -> service.lauf(ADMIN, 11L))
        .isInstanceOf(NightRunNotFoundException.class);
  }

  @Test
  void einMitgliedUnterhalbVonOwnerSiehtKeineLaeufe() {
    sieht(MITGLIED, PROJEKT);
    rolle(MITGLIED, PROJEKT, ProjectRole.ADMIN);
    lauf(11L, PROJEKT, NightRunKind.NIGHT, vorMinuten(20), vorMinuten(1), false, null);

    assertThat(service.laeufe(MITGLIED, BERLIN)).isEmpty();
    assertThatThrownBy(() -> service.lauf(MITGLIED, 11L))
        .isInstanceOf(NightRunNotFoundException.class);
  }

  // --- Ein Lauf -----------------------------------------------------------------------------

  @Test
  void einFremderLaufIst404() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    lauf(21L, ANDERES, NightRunKind.NIGHT, vorMinuten(20), vorMinuten(1), false, null);

    assertThatThrownBy(() -> service.lauf(OWNER, 21L))
        .isInstanceOf(NightRunNotFoundException.class);
  }

  @Test
  void einUnbekannterLaufUndEineSitzungSind404() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    lauf(11L, PROJEKT, NightRunKind.INTERACTIVE, vorMinuten(20), vorMinuten(1), true, null);

    assertThatThrownBy(() -> service.lauf(OWNER, 99L))
        .isInstanceOf(NightRunNotFoundException.class);
    assertThatThrownBy(() -> service.lauf(OWNER, 11L))
        .isInstanceOf(NightRunNotFoundException.class);
  }

  @Test
  void einGelungenerLaufIstBeendet_auchAusAeltererNacht_ohneMeldung() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    lauf(11L, PROJEKT, JETZT.minus(Duration.ofDays(5)), true);
    paket(11L, 1, NightRunState.GREEN, null, "fertig");

    LaufDetail detail = service.lauf(OWNER, 11L);

    assertThat(detail.zeile().zustand()).isEqualTo(Zustand.BEENDET);
    assertThat(detail.zeile().dauer()).isEqualTo(Duration.ofHours(1));
    assertThat(detail.zeile().projectName()).isEqualTo("Projekt 7");
    assertThat(detail.meldung()).isNull();
  }

  @Test
  void einVonHandBeendeterLaufIstBeendet() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    lauf(
        11L,
        PROJEKT,
        NightRunKind.NIGHT,
        vorMinuten(300),
        vorMinuten(290),
        false,
        null,
        vorMinuten(10));

    LaufDetail detail = service.lauf(OWNER, 11L);

    assertThat(detail.zeile().zustand()).isEqualTo(Zustand.BEENDET);
    assertThat(detail.zeile().dauer()).isEqualTo(Duration.ofHours(1));
    assertThat(detail.meldung()).isNull();
    assertThat(service.laeufe(OWNER, BERLIN)).isEmpty();
  }

  @Test
  void einLaufNenntSeineLetzteMeldung() {
    sieht(OWNER, PROJEKT);
    rolle(OWNER, PROJEKT, ProjectRole.OWNER);
    lauf(11L, PROJEKT, NightRunKind.NIGHT, vorMinuten(60), vorMinuten(1), false, null);
    paket(11L, 2, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION, "wartet auf Manne");

    LaufDetail detail = service.lauf(OWNER, 11L);

    assertThat(detail.zeile().zustand()).isEqualTo(Zustand.HAENGT);
    assertThat(detail.meldung())
        .isEqualTo(new LetzteMeldung(MeldungsArt.PAKET, "wartet auf Manne", 2, null, null));
  }

  private static NightRun ohneBoard(NightRun run) {
    return new NightRun(
        run.id(),
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
        run.origin(),
        run.tokenName(),
        run.complete(),
        run.updatedAt(),
        run.usage(),
        run.noWorkReason(),
        run.budget(),
        run.abortReason(),
        run.abortKind(),
        run.releasePreparation(),
        run.current(),
        run.closedAt(),
        null);
  }
}
