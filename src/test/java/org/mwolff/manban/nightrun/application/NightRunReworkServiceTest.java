package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.BoardKarteView;
import org.mwolff.manban.card.application.CardRunQueryService.KartenGruppe;
import org.mwolff.manban.card.application.CardRunQueryService.LabelEreignisView;
import org.mwolff.manban.card.application.CardRunQueryService.LaufBewegungView;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.LaufKommentarView;
import org.mwolff.manban.nightrun.domain.Nacharbeit;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Karteneintrag;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Zustand;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;

/**
 * Der Dienst der Platte „Nacharbeit“ (Issue #1757, Plan #1752 E4, E12): Recht vor jeder Abfrage,
 * Projekt über die board-Fassade, Laufstarts aus den aufbewahrten NIGHT-Läufen und die Abbildung
 * der Fassaden-Sichten auf die Eingaben der Ermittlung.
 */
class NightRunReworkServiceTest {

  private static final long USER = 7L;
  private static final long BOARD = 11L;
  private static final long PROJECT = 42L;
  private static final Instant JETZT = Instant.parse("2026-10-15T06:00:00Z");
  private static final Instant ERFASSUNG = Instant.parse("2026-01-01T00:00:00Z");

  /** Ein Laufstart mit Mikrosekunden, wie Postgres ihn liefert; die Spuren tragen Millisekunden. */
  private static final Instant LAUF = Instant.parse("2026-10-14T22:00:00.123456Z");

  private static final Instant LAUF_MS = Instant.parse("2026-10-14T22:00:00.123Z");
  private static final Instant FRUEHER_LAUF = Instant.parse("2026-10-07T22:00:00.5Z");

  /** Älter als das älteste der zwölf Wochenfenster. */
  private static final Instant ALTER_LAUF = JETZT.minus(Duration.ofDays(100));

  private final BoardService boards = mock(BoardService.class);
  private final PermissionChecker permissions = mock(PermissionChecker.class);
  private final NightRunRepository runs = mock(NightRunRepository.class);
  private final CardRunQueryService cards = mock(CardRunQueryService.class);
  private final CommentService comments = mock(CommentService.class);
  private final NightRunReworkService service =
      new NightRunReworkService(
          boards, permissions, runs, cards, comments, Clock.fixed(JETZT, ZoneOffset.UTC));

  private static NightRun nachtlauf(Instant startedAt) {
    return new NightRun(
        1L,
        PROJECT,
        startedAt,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        60_000,
        0,
        0,
        0,
        null,
        startedAt,
        NightRunOrigin.TOKEN,
        "runner",
        true,
        startedAt,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private void laeufe(Instant... starts) {
    when(boards.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.labelErfassungSeit()).thenReturn(ERFASSUNG);
    when(runs.findByProjectAndKindOrderByStartedAtDesc(PROJECT, NightRunKind.NIGHT))
        .thenReturn(Arrays.stream(starts).map(NightRunReworkServiceTest::nachtlauf).toList());
  }

  @Test
  void ohneRechtWirdKeineFassadeGelesen() {
    when(boards.requireProjectId(BOARD)).thenReturn(PROJECT);
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.nacharbeit(USER, BOARD))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verifyNoInteractions(runs, cards, comments);
  }

  @Test
  void einUnbekanntesBoardEndetVorDerRechtepruefung() {
    when(boards.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    assertThatThrownBy(() -> service.nacharbeit(USER, BOARD))
        .isInstanceOf(BoardNotFoundException.class);

    verifyNoInteractions(permissions, runs, cards, comments);
  }

  @Test
  void dasProjektKommtAusDerBoardFassadeUndDasRechtVorDenLaeufen() {
    laeufe();

    service.nacharbeit(USER, BOARD);

    InOrder reihenfolge = inOrder(boards, permissions, runs);
    reihenfolge.verify(boards).requireProjectId(BOARD);
    reihenfolge.verify(permissions).requireNightRunAccess(USER, PROJECT);
    reihenfolge.verify(runs).findByProjectAndKindOrderByStartedAtDesc(PROJECT, NightRunKind.NIGHT);
  }

  @Test
  void ohneLaufImZeitraumWerdenKeineSpurenGelesen() {
    laeufe(ALTER_LAUF);

    Nacharbeit nacharbeit = service.nacharbeit(USER, BOARD);

    assertThat(nacharbeit.runsImZeitraum()).isFalse();
    assertThat(nacharbeit.pakete().datenbeginn()).isEqualTo(ALTER_LAUF);
    verify(cards, never()).boardKarten(BOARD);
    verify(cards, never()).laufBewegungen(anyCollection(), any());
    verify(cards, never()).labelEreignisse(anyCollection(), any());
    verify(comments, never()).laufKommentare(anyCollection(), any());
  }

  @Test
  void dieSpurenWerdenAbDemAeltestenLaufstartImZeitraumGelesen() {
    laeufe(LAUF, FRUEHER_LAUF, ALTER_LAUF);
    when(cards.boardKarten(BOARD))
        .thenReturn(List.of(new BoardKarteView(5L, 1705, "Paket", KartenGruppe.PAKET)));

    service.nacharbeit(USER, BOARD);

    Instant von = Instant.parse("2026-10-07T22:00:00.5Z");
    verify(cards).laufBewegungen(List.of(5L), von);
    verify(cards).labelEreignisse(List.of(5L), von);
    verify(comments).laufKommentare(List.of(5L), von);
  }

  @Test
  void derBeginnDerSpurenIstAufMillisekundenGekuerzt() {
    laeufe(LAUF);
    when(cards.boardKarten(BOARD)).thenReturn(List.of());

    service.nacharbeit(USER, BOARD);

    verify(cards).laufBewegungen(List.of(), LAUF_MS);
  }

  @Test
  void dieSichtenDerFassadenGehenAlsEingabenInDieErmittlung() {
    laeufe(LAUF);
    when(cards.boardKarten(BOARD))
        .thenReturn(
            List.of(
                new BoardKarteView(1L, 1701, "Paket zurück", KartenGruppe.PAKET),
                new BoardKarteView(2L, 1702, "Paket fertig", KartenGruppe.PAKET),
                new BoardKarteView(3L, 1703, "[Plan] Angehalten", KartenGruppe.DOKUMENT),
                new BoardKarteView(4L, 1704, "[Plan] Kommentiert", KartenGruppe.DOKUMENT),
                new BoardKarteView(9L, 1709, "[Idee] Andere", KartenGruppe.ANDERE)));
    when(cards.laufBewegungen(List.of(1L, 2L, 3L, 4L, 9L), LAUF_MS))
        .thenReturn(
            List.of(
                new LaufBewegungView(1L, "MOVED", LAUF_MS, LAUF_MS, "IN_PROGRESS", "READY"),
                new LaufBewegungView(1L, "MOVED", LAUF_MS, LAUF_MS, "BACKLOG", "IN_PROGRESS"),
                new LaufBewegungView(2L, "MOVED", LAUF_MS, LAUF_MS, "IN_PROGRESS", null)));
    when(cards.labelEreignisse(List.of(1L, 2L, 3L, 4L, 9L), LAUF_MS))
        .thenReturn(List.of(new LabelEreignisView(3L, "kit:clarify", LAUF_MS, LAUF_MS)));
    when(comments.laufKommentare(List.of(1L, 2L, 3L, 4L, 9L), LAUF_MS))
        .thenReturn(List.of(new LaufKommentarView(4L, LAUF_MS)));

    Nacharbeit nacharbeit = service.nacharbeit(USER, BOARD);

    assertThat(nacharbeit.runsImZeitraum()).isTrue();
    assertThat(nacharbeit.pakete().betroffen()).isEqualTo(1);
    assertThat(nacharbeit.pakete().bearbeitet()).isEqualTo(2);
    assertThat(nacharbeit.pakete().karten())
        .containsExactly(new Karteneintrag(1701, "Paket zurück", 1));
    assertThat(nacharbeit.dokumente().betroffen()).isEqualTo(1);
    assertThat(nacharbeit.dokumente().bearbeitet()).isEqualTo(2);
    assertThat(nacharbeit.dokumente().karten())
        .containsExactly(new Karteneintrag(1703, "[Plan] Angehalten", 1));
    assertThat(nacharbeit.dokumente().datenbeginn()).isEqualTo(LAUF_MS);
    assertThat(nacharbeit.wochen()).hasSize(12);
    assertThat(nacharbeit.wochen().get(11).pakete().zustand()).isEqualTo(Zustand.QUOTE);
  }

  @Test
  void derErfassungsbeginnDerLabelsBegrenztDieDokumente() {
    Instant spaeteErfassung = Instant.parse("2026-10-14T23:00:00Z");
    laeufe(LAUF);
    when(cards.labelErfassungSeit()).thenReturn(spaeteErfassung);
    when(cards.boardKarten(BOARD)).thenReturn(List.of());

    Nacharbeit nacharbeit = service.nacharbeit(USER, BOARD);

    assertThat(nacharbeit.pakete().datenbeginn()).isEqualTo(LAUF_MS);
    assertThat(nacharbeit.dokumente().datenbeginn()).isEqualTo(spaeteErfassung);
  }
}
