package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardSummary;
import org.mwolff.manban.nightrun.application.MeldbareLaeufe.MeldbarerLauf;
import org.mwolff.manban.nightrun.domain.Meldegrund.Grund;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.project.application.ProjectService;

/**
 * Der Lesedienst des Meldejobs (Issue #1998, Plan #1989 E11, E12): die Nachtläufe der laufenden und
 * der vorigen Nacht über alle Projekte, die einen Meldegrund haben, samt Projekt und Board.
 */
class MeldbareLaeufeTest {

  /** 08:00 UTC: Die laufende Nacht begann am 28.10. um 12:00, die vorige am 27.10. um 12:00. */
  private static final Instant JETZT = Instant.parse("2026-10-29T08:00:00Z");

  private static final Instant VORIGE_NACHT_BEGINN = Instant.parse("2026-10-27T12:00:00Z");
  private static final long PROJEKT = 7L;
  private static final long BOARD = 40L;

  private NightRunRepository runs;
  private ProjectService projects;
  private BoardService boards;
  private MeldbareLaeufe meldbare;
  private final List<NightRun> laeufe = new ArrayList<>();
  private final List<NightRunItem> pakete = new ArrayList<>();

  @BeforeEach
  void setUp() {
    runs = mock(NightRunRepository.class);
    projects = mock(ProjectService.class);
    boards = mock(BoardService.class);
    when(projects.findName(anyLong())).thenReturn(Optional.empty());
    when(projects.findName(PROJEKT)).thenReturn(Optional.of("Alpha"));
    when(boards.requireBoardSummary(BOARD)).thenReturn(new BoardSummary(BOARD, "Produkt", false));
    when(runs.findByKindStartedSince(NightRunKind.NIGHT, VORIGE_NACHT_BEGINN)).thenReturn(laeufe);
    when(runs.findItemsByRunIds(any()))
        .thenAnswer(
            inv -> {
              List<Long> ids = List.copyOf(inv.getArgument(0));
              return pakete.stream().filter(p -> ids.contains(p.nightRunId())).toList();
            });
    meldbare =
        new MeldbareLaeufe(
            runs,
            projects,
            boards,
            new NightRunProperties(null, null, null, null, null),
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private NightRun lauf(long id, long projektId, @Nullable Long boardId, Instant updatedAt) {
    Instant start = JETZT.minus(Duration.ofHours(3));
    NightRun run =
        new NightRun(
            id,
            projektId,
            start,
            NightRunMode.IMPLEMENTATION,
            NightRunKind.NIGHT,
            0,
            0,
            0,
            0,
            null,
            start,
            NightRunOrigin.TOKEN,
            "runner",
            false,
            updatedAt,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            boardId);
    laeufe.add(run);
    return run;
  }

  private void paket(long laufId, int karte, NightRunState state, NightRunErrorClass klasse) {
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
            klasse,
            1_000L,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of()));
  }

  @Test
  void einGescheiterterLaufErscheintMitProjektBoardUndGrund() {
    lauf(1L, PROJEKT, BOARD, JETZT);
    paket(1L, 1975, NightRunState.RED, NightRunErrorClass.HARD_ABORT);

    List<MeldbarerLauf> ergebnis = meldbare.laeufe();

    assertThat(ergebnis).hasSize(1);
    MeldbarerLauf lauf = ergebnis.getFirst();
    assertThat(lauf.laufId()).isEqualTo(1L);
    assertThat(lauf.projectId()).isEqualTo(PROJEKT);
    assertThat(lauf.projectName()).isEqualTo("Alpha");
    assertThat(lauf.boardId()).isEqualTo(BOARD);
    assertThat(lauf.boardName()).isEqualTo("Produkt");
    assertThat(lauf.grund().grund()).isEqualTo(Grund.GESCHEITERT);
    assertThat(lauf.grund().paket()).isNotNull();
  }

  @Test
  void einLaufOhneGrundErscheintNicht() {
    lauf(1L, PROJEKT, BOARD, JETZT);
    paket(1L, 1, NightRunState.GREEN, null);

    assertThat(meldbare.laeufe()).isEmpty();
  }

  @Test
  void einVerstummterLaufOhneBoardErscheintOhneBoardnamen() {
    lauf(2L, PROJEKT, null, JETZT.minus(Duration.ofMinutes(91)));

    List<MeldbarerLauf> ergebnis = meldbare.laeufe();

    assertThat(ergebnis)
        .singleElement()
        .satisfies(
            lauf -> {
              assertThat(lauf.boardId()).isNull();
              assertThat(lauf.boardName()).isNull();
              assertThat(lauf.grund().grund()).isEqualTo(Grund.VERSTUMMT);
            });
    verify(boards, never()).requireBoardSummary(anyLong());
  }

  @Test
  void einLaufEinesVerschwundenenProjektsErscheintNicht() {
    lauf(3L, 99L, null, JETZT.minus(Duration.ofMinutes(91)));

    assertThat(meldbare.laeufe()).isEmpty();
  }

  @Test
  void ohneLaeufeFragtEsKeinePaketeAb() {
    assertThat(meldbare.laeufe()).isEmpty();
    verify(runs, never()).findItemsByRunIds(any());
  }
}
