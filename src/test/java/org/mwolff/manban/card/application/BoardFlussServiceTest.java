package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.AgingWipView.AgingCard;
import org.mwolff.manban.card.application.CardFlowStayRepository.ErfassungsMarke;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.FlussErmittlung;
import org.mwolff.manban.card.domain.FlussStay;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/** Dienst der Platten „Fluss“ und „Alter in Arbeit“ (Issue #1773; Ports gemockt, feste Uhr). */
class BoardFlussServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;
  private static final long USER = 3L;

  private CardFlowStayRepository aufenthalte;
  private CardRepository cards;
  private BoardService boardService;
  private PermissionChecker permissions;
  private BoardFlussService service;

  @BeforeEach
  void setUp() {
    aufenthalte = mock(CardFlowStayRepository.class);
    cards = mock(CardRepository.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    service =
        new BoardFlussService(
            aufenthalte, cards, boardService, permissions, Clock.fixed(NOW, ZoneOffset.UTC));
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(aufenthalte.marke())
        .thenReturn(new ErfassungsMarke(NOW.minus(Duration.ofDays(200)), NOW.minusSeconds(60)));
  }

  private static FlussStay stay(
      long cardId, CardStatus status, Instant entered, @Nullable Instant left, boolean known) {
    return new FlussStay(
        null, cardId, BOARD, status, entered, left, known, left == null ? null : EndedBy.STATUS);
  }

  private static Card card(long id, int number, String title) {
    return new Card(
        id,
        BOARD,
        5L,
        number,
        title,
        null,
        0,
        false,
        null,
        1L,
        NOW,
        NOW,
        CardType.CARD,
        null,
        null,
        null,
        1L,
        null,
        null,
        null,
        null);
  }

  // --- Recht ---------------------------------------------------------------------------------

  @Test
  void flow_verlangtDieMitgliedschaft() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireMembership(USER, PROJECT);

    assertThatThrownBy(() -> service.flow(USER, BOARD, BERLIN))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(aufenthalte);
  }

  @Test
  void aging_verlangtDieMitgliedschaft() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireMembership(USER, PROJECT);

    assertThatThrownBy(() -> service.aging(USER, BOARD))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(aufenthalte, cards);
  }

  @Test
  void flow_unbekanntesOderArchiviertesBoardIstNichtGefunden() {
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    assertThatThrownBy(() -> service.flow(USER, BOARD, BERLIN))
        .isInstanceOf(BoardNotFoundException.class);
    assertThatThrownBy(() -> service.aging(USER, BOARD)).isInstanceOf(BoardNotFoundException.class);
  }

  // --- Fluss ---------------------------------------------------------------------------------

  @Test
  void flow_bildetDieTageDerErmittlungAb() {
    List<FlussStay> verlauf =
        List.of(
            stay(1L, CardStatus.READY, NOW.minus(Duration.ofDays(300)), null, true),
            stay(2L, CardStatus.DONE, NOW.minus(Duration.ofDays(2)), null, true));
    when(aufenthalte.findByBoardId(BOARD)).thenReturn(verlauf);

    BoardFlowView view = service.flow(USER, BOARD, BERLIN);

    FlussErmittlung.Fluss erwartet =
        FlussErmittlung.ermittle(
            FlussErmittlung.TAGE,
            BERLIN,
            NOW,
            BOARD,
            verlauf,
            true,
            NOW.minus(Duration.ofDays(200)));
    assertThat(view.days()).hasSize(84);
    assertThat(view.dataSince()).isNull();
    assertThat(view.days())
        .extracting(
            BoardFlowView.Day::date,
            BoardFlowView.Day::known,
            BoardFlowView.Day::backlog,
            BoardFlowView.Day::ready,
            BoardFlowView.Day::inProgress,
            BoardFlowView.Day::inReview,
            BoardFlowView.Day::done)
        .containsExactlyElementsOf(
            erwartet.tage().stream()
                .map(
                    t ->
                        tuple(
                            t.datum(),
                            t.known(),
                            t.backlog(),
                            t.ready(),
                            t.inProgress(),
                            t.inReview(),
                            t.done()))
                .toList());
    BoardFlowView.Day heute = view.days().getLast();
    assertThat(heute.date()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(heute.ready()).isEqualTo(1);
    assertThat(heute.done()).isEqualTo(1);
    assertThat(heute.backlog()).isZero();
  }

  @Test
  void flow_ohneNachtragSindDieTageVorDemErfassungsbeginnUnbekannt() {
    Instant since = NOW.minus(Duration.ofDays(10));
    when(aufenthalte.marke()).thenReturn(new ErfassungsMarke(since, null));
    when(aufenthalte.findByBoardId(BOARD)).thenReturn(List.of());

    BoardFlowView view = service.flow(USER, BOARD, BERLIN);

    assertThat(view.days().getFirst().known()).isFalse();
    assertThat(view.days().getFirst().backlog()).isNull();
    assertThat(view.dataSince()).isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(view.days().getLast().known()).isTrue();
  }

  @Test
  void flow_nachDemNachtragSindAuchDieTageVorDemErfassungsbeginnBekannt() {
    Instant since = NOW.minus(Duration.ofDays(10));
    when(aufenthalte.marke()).thenReturn(new ErfassungsMarke(since, NOW.minusSeconds(60)));
    when(aufenthalte.findByBoardId(BOARD)).thenReturn(List.of());

    BoardFlowView view = service.flow(USER, BOARD, BERLIN);

    assertThat(view.days().getFirst().known()).isTrue();
    assertThat(view.days().getFirst().backlog()).isZero();
    assertThat(view.dataSince()).isNull();
  }

  @Test
  void flow_rechnetDieTageInDerZoneDesLesers() {
    when(aufenthalte.findByBoardId(BOARD)).thenReturn(List.of());

    BoardFlowView view = service.flow(USER, BOARD, ZoneId.of("Pacific/Kiritimati"));

    // 10:00 UTC ist in UTC+14 schon der 16. Oktober.
    assertThat(view.days().getLast().date()).isEqualTo(LocalDate.of(2026, 10, 16));
  }

  // --- Alter ---------------------------------------------------------------------------------

  @Test
  void aging_bildetJeZustandAbMitNummerTitelUndAlterInSekunden() {
    Instant vorDreiTagen = NOW.minus(Duration.ofDays(3));
    Instant vorEinemTag = NOW.minus(Duration.ofDays(1));
    when(aufenthalte.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                stay(1L, CardStatus.READY, vorEinemTag, null, true),
                stay(2L, CardStatus.READY, vorDreiTagen, null, false),
                stay(3L, CardStatus.IN_REVIEW, vorEinemTag, null, true)));
    when(cards.findByIds(Set.of(1L, 2L, 3L)))
        .thenReturn(List.of(card(1L, 101, "Eins"), card(2L, 102, "Zwei"), card(3L, 103, "Drei")));

    AgingWipView view = service.aging(USER, BOARD);

    assertThat(view.ready().comparisonCount()).isZero();
    assertThat(view.ready().thresholdSeconds()).isNull();
    assertThat(view.ready().cards())
        .containsExactly(
            new AgingCard(2L, 102, "Zwei", Duration.ofDays(3).toSeconds(), true, false),
            new AgingCard(1L, 101, "Eins", Duration.ofDays(1).toSeconds(), false, false));
    assertThat(view.inProgress().cards()).isEmpty();
    assertThat(view.inReview().cards())
        .containsExactly(
            new AgingCard(3L, 103, "Drei", Duration.ofDays(1).toSeconds(), false, false));
  }

  @Test
  void aging_liefertSchwelleInSekundenUndDenMelder() {
    Instant basis = NOW.minus(Duration.ofDays(30));
    List<FlussStay> verlauf =
        List.of(
            stay(11L, CardStatus.IN_PROGRESS, basis, basis.plus(Duration.ofHours(1)), true),
            stay(12L, CardStatus.IN_PROGRESS, basis, basis.plus(Duration.ofHours(2)), true),
            stay(13L, CardStatus.IN_PROGRESS, basis, basis.plus(Duration.ofHours(3)), true),
            stay(14L, CardStatus.IN_PROGRESS, basis, basis.plus(Duration.ofHours(4)), true),
            stay(15L, CardStatus.IN_PROGRESS, basis, basis.plus(Duration.ofHours(5)), true),
            stay(1L, CardStatus.IN_PROGRESS, NOW.minus(Duration.ofHours(6)), null, true));
    when(aufenthalte.findByBoardId(BOARD)).thenReturn(verlauf);
    when(cards.findByIds(Set.of(1L))).thenReturn(List.of(card(1L, 7, "Alt")));

    AgingWipView view = service.aging(USER, BOARD);

    assertThat(view.inProgress().comparisonCount()).isEqualTo(5);
    assertThat(view.inProgress().thresholdSeconds()).isEqualTo(Duration.ofHours(5).toSeconds());
    assertThat(view.inProgress().cards())
        .containsExactly(new AgingCard(1L, 7, "Alt", Duration.ofHours(6).toSeconds(), false, true));
  }

  @Test
  void aging_ohneKarteImZustandBleibtDieSpalteLeerUndFragtKeineKarten() {
    when(aufenthalte.findByBoardId(BOARD)).thenReturn(List.of());
    when(cards.findByIds(Set.of())).thenReturn(List.of());

    AgingWipView view = service.aging(USER, BOARD);

    assertThat(view.ready().cards()).isEmpty();
    assertThat(view.inProgress().cards()).isEmpty();
    assertThat(view.inReview().cards()).isEmpty();
  }

  @Test
  void aging_uebergehtEineKarteDieZwischenLesenUndNachschlagenVerschwand() {
    when(aufenthalte.findByBoardId(BOARD))
        .thenReturn(List.of(stay(1L, CardStatus.READY, NOW.minusSeconds(30), null, true)));
    when(cards.findByIds(Set.of(1L))).thenReturn(List.of());

    assertThat(service.aging(USER, BOARD).ready().cards()).isEmpty();
  }
}
