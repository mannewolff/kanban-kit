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
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe.BenachrichtigbarerLauf;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.project.application.ProjectService;

/**
 * Der Lesedienst der Benachrichtigungen (Issue #2074, Plan #2070 E2): die Läufe der Art {@code
 * NIGHT} der letzten 48 Stunden über alle Projekte, auch die ohne Grund, samt Paketen, Projekt und
 * Board.
 */
class BenachrichtigbareLaeufeTest {

  private static final Instant JETZT = Instant.parse("2026-11-09T08:00:00Z");
  private static final Instant FENSTER_BEGINN = Instant.parse("2026-11-07T08:00:00Z");
  private static final long PROJEKT = 7L;
  private static final long BOARD = 40L;

  private NightRunRepository runs;
  private ProjectService projects;
  private BoardService boards;
  private BenachrichtigbareLaeufe benachrichtigbare;
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
    when(runs.findByKindStartedSince(any(), any()))
        .thenAnswer(
            inv -> {
              NightRunKind art = inv.getArgument(0);
              Instant seit = inv.getArgument(1);
              return laeufe.stream()
                  .filter(run -> run.kind() == art && !run.startedAt().isBefore(seit))
                  .toList();
            });
    when(runs.findItemsByRunIds(any()))
        .thenAnswer(
            inv -> {
              List<Long> ids = List.copyOf(inv.getArgument(0));
              return pakete.stream().filter(p -> ids.contains(p.nightRunId())).toList();
            });
    benachrichtigbare =
        new BenachrichtigbareLaeufe(runs, projects, boards, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private NightRun lauf(
      long id, long projektId, @Nullable Long boardId, NightRunKind art, Instant start) {
    NightRun run =
        new NightRun(
            id,
            projektId,
            start,
            NightRunMode.IMPLEMENTATION,
            art,
            0,
            0,
            0,
            0,
            null,
            start,
            NightRunOrigin.TOKEN,
            "runner",
            true,
            start,
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

  private NightRun nachtlauf(long id, Instant start) {
    return lauf(id, PROJEKT, BOARD, NightRunKind.NIGHT, start);
  }

  private NightRunItem paket(long laufId, int karte, NightRunState state) {
    NightRunItem item =
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
            null,
            1_000L,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of());
    pakete.add(item);
    return item;
  }

  @Test
  void einLaufOhneGrundErscheintMitPaketenProjektUndBoard() {
    NightRun run = nachtlauf(1L, JETZT.minus(Duration.ofHours(3)));
    NightRunItem erstes = paket(1L, 1, NightRunState.GREEN);
    NightRunItem zweites = paket(1L, 2, NightRunState.GREEN);

    assertThat(benachrichtigbare.laeufe())
        .containsExactly(
            new BenachrichtigbarerLauf(run, List.of(erstes, zweites), "Alpha", "Produkt"));
  }

  @Test
  void einLaufOhnePaketeErscheintMitLeererListe() {
    NightRun run = nachtlauf(1L, JETZT.minus(Duration.ofHours(3)));

    assertThat(benachrichtigbare.laeufe())
        .containsExactly(new BenachrichtigbarerLauf(run, List.of(), "Alpha", "Produkt"));
  }

  @Test
  void jederLaufBekommtNurSeinePakete() {
    NightRun eins = nachtlauf(1L, JETZT.minus(Duration.ofHours(5)));
    NightRun zwei = nachtlauf(2L, JETZT.minus(Duration.ofHours(4)));
    NightRunItem vonEins = paket(1L, 1, NightRunState.RED);
    NightRunItem vonZwei = paket(2L, 2, NightRunState.GREEN);

    assertThat(benachrichtigbare.laeufe())
        .containsExactly(
            new BenachrichtigbarerLauf(eins, List.of(vonEins), "Alpha", "Produkt"),
            new BenachrichtigbarerLauf(zwei, List.of(vonZwei), "Alpha", "Produkt"));
  }

  @Test
  void einLaufGenauAmFensterbeginnErscheint() {
    NightRun run = nachtlauf(1L, FENSTER_BEGINN);

    assertThat(benachrichtigbare.laeufe())
        .extracting(BenachrichtigbarerLauf::lauf)
        .containsExactly(run);
  }

  @Test
  void einAeltererLaufErscheintNicht() {
    nachtlauf(1L, FENSTER_BEGINN.minusSeconds(1));

    assertThat(benachrichtigbare.laeufe()).isEmpty();
  }

  @Test
  void einLaufAndererArtErscheintNicht() {
    lauf(1L, PROJEKT, BOARD, NightRunKind.INTERACTIVE, JETZT.minus(Duration.ofHours(1)));

    assertThat(benachrichtigbare.laeufe()).isEmpty();
  }

  @Test
  void einLaufOhneBoardErscheintOhneBoardnamen() {
    NightRun run = lauf(1L, PROJEKT, null, NightRunKind.NIGHT, JETZT.minus(Duration.ofHours(1)));

    assertThat(benachrichtigbare.laeufe())
        .containsExactly(new BenachrichtigbarerLauf(run, List.of(), "Alpha", null));
    verify(boards, never()).requireBoardSummary(anyLong());
  }

  @Test
  void einLaufEinesVerschwundenenProjektsErscheintNicht() {
    lauf(1L, 99L, BOARD, NightRunKind.NIGHT, JETZT.minus(Duration.ofHours(1)));

    assertThat(benachrichtigbare.laeufe()).isEmpty();
  }

  @Test
  void ohneLaeufeFragtEsKeinePaketeAb() {
    assertThat(benachrichtigbare.laeufe()).isEmpty();
    verify(runs, never()).findItemsByRunIds(any());
  }
}
