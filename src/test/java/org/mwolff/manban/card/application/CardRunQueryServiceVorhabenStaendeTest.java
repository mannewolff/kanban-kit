package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardRunQueryService.DokumentStandView;
import org.mwolff.manban.card.application.CardRunQueryService.PaketStandView;
import org.mwolff.manban.card.application.CardRunQueryService.VorhabenStandView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Unit-Tests des Stands je Vorhaben über die card-Fassade (Issue #2001, Plan #1990 E1, E5, E10).
 *
 * <p>Die Regeln selbst prüft {@code VorhabenStandTest}; hier geht es um die Fassade: über alle
 * Boards, Spaltennamen je Board, Zugehörigkeit samt Archiv und Herkunft, die Aufgabenstufe aus der
 * Beschreibung und die Felder der Sichten. Als Unit-Test, weil PIT allein Unit-Tests misst.
 */
class CardRunQueryServiceVorhabenStaendeTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long PROJECT = 1L;
  private static final long BOARD_A = 10L;
  private static final long BOARD_B = 11L;
  private static final long A_BACKLOG = 100L;
  private static final long A_DONE = 101L;
  private static final long B_FERTIG = 200L;

  private CardRepository cards;
  private BoardService boards;
  private CardRunQueryService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    boards = mock(BoardService.class);
    service =
        new CardRunQueryService(
            cards,
            mock(CardActivityRepository.class),
            // Echte KartenZuordnung aus Port-Mocks (Issue #1051), kein Mock der Zuordnung selbst.
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                mock(LabelRepository.class),
                mock(CardLabelRepository.class),
                mock(PermissionChecker.class),
                mock(CardLabelEventRepository.class),
                ActorContext.ActorStamp::unknown,
                Clock.systemUTC(),
                mock(ZuweisungsBenachrichtigung.class)),
            boards,
            mock(CardLabelEventRepository.class),
            mock(CardBestandRepository.class));
    when(boards.listColumns(BOARD_A))
        .thenReturn(
            List.of(
                new ColumnView(A_BACKLOG, "Backlog", 0, null),
                new ColumnView(A_DONE, "Done", 1, null)));
    // Eine eigene Done-Spalte auf dem zweiten Board: Die Spaltennamen gelten je Board.
    when(boards.listColumns(BOARD_B))
        .thenReturn(List.of(new ColumnView(B_FERTIG, "Done ✓", 0, null)));
  }

  private static Card karte(
      long id,
      long board,
      long spalte,
      int number,
      String titel,
      CardType type,
      @Nullable String beschreibung,
      @Nullable Long parentId,
      @Nullable Long derivedFrom,
      boolean archived,
      @Nullable CardStatus status) {
    return new Card(
        id,
        board,
        spalte,
        number,
        titel,
        beschreibung,
        0,
        archived,
        status == CardStatus.DONE ? FIXED.plusSeconds(60) : null,
        1L,
        FIXED,
        FIXED,
        type,
        parentId,
        type == CardType.EPIC ? "K" + number : null,
        null,
        PROJECT,
        null,
        derivedFrom,
        null,
        status);
  }

  private static Card vorhaben(long id, long board, int number, boolean archived) {
    return karte(
        id,
        board,
        0L,
        number,
        "Vorhaben " + number,
        CardType.EPIC,
        null,
        null,
        null,
        archived,
        null);
  }

  @Test
  void einVorhabenMitAnforderungPlanUndPaketenAlsSicht() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                vorhaben(1L, BOARD_A, 900, false),
                karte(
                    2L,
                    BOARD_A,
                    A_DONE,
                    901,
                    "[Fachlich] A",
                    CardType.CARD,
                    null,
                    1L,
                    null,
                    false,
                    null),
                karte(
                    3L,
                    BOARD_A,
                    A_DONE,
                    902,
                    "[Plan] P",
                    CardType.CARD,
                    null,
                    null,
                    2L,
                    false,
                    null),
                karte(
                    4L,
                    BOARD_A,
                    A_DONE,
                    903,
                    "Paket",
                    CardType.CARD,
                    "Kontext\nAufgabenstufe: schwer",
                    null,
                    3L,
                    true,
                    CardStatus.DONE),
                karte(
                    5L,
                    BOARD_A,
                    A_BACKLOG,
                    904,
                    "Paket",
                    CardType.CARD,
                    null,
                    null,
                    3L,
                    false,
                    CardStatus.BACKLOG)));

    List<VorhabenStandView> staende = service.vorhabenStaende(PROJECT);

    assertThat(staende)
        .containsExactly(
            new VorhabenStandView(
                new VorhabenRef(1L, 900, "K900", "Vorhaben 900", false),
                BOARD_A,
                List.of(
                    new PaketStandView(
                        903, true, Aufgabenstufe.SCHWER, FIXED, FIXED.plusSeconds(60)),
                    new PaketStandView(904, false, null, FIXED, null)),
                List.of(
                    new DokumentStandView(901, "FACHLICH", false, true),
                    new DokumentStandView(902, "PLAN", false, true)),
                true,
                true,
                false,
                List.of(901, 902, 903, 904),
                FIXED));
  }

  @Test
  void vorhabenAllerBoardsMitIhrenSpaltennamenUndArchivierteVorhaben() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(
                vorhaben(1L, BOARD_A, 950, false),
                vorhaben(20L, BOARD_B, 820, true),
                karte(
                    21L,
                    BOARD_B,
                    B_FERTIG,
                    821,
                    "Paket",
                    CardType.CARD,
                    null,
                    20L,
                    null,
                    false,
                    null)));

    List<VorhabenStandView> staende = service.vorhabenStaende(PROJECT);

    assertThat(staende)
        .extracting(s -> s.vorhaben().number(), VorhabenStandView::boardId)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(820, BOARD_B),
            org.assertj.core.groups.Tuple.tuple(950, BOARD_A));
    VorhabenStandView archiviert = staende.getFirst();
    assertThat(archiviert.vorhaben().archiviert()).isTrue();
    assertThat(archiviert.abgeschlossen()).isTrue();
    assertThat(archiviert.pakete())
        .containsExactly(new PaketStandView(821, true, null, FIXED, null));
    assertThat(archiviert.hatAnforderung()).isFalse();
    assertThat(archiviert.hatteUngeschnittenesDokument()).isFalse();
  }

  @Test
  void einVorhabenOhneKostenkarteTraegtKeineAnlagezeit() {
    when(cards.findByProjectId(PROJECT)).thenReturn(List.of(vorhaben(1L, BOARD_A, 900, false)));

    VorhabenStandView stand = service.vorhabenStaende(PROJECT).getFirst();

    assertThat(stand.kostenkarten()).isEmpty();
    assertThat(stand.fruehesteKostenkarteAngelegt()).isNull();
    assertThat(stand.abgeschlossen()).isFalse();
  }

  @Test
  void einProjektOhneKartenFragtKeineSpalten() {
    when(cards.findByProjectId(PROJECT)).thenReturn(List.of());

    assertThat(service.vorhabenStaende(PROJECT)).isEmpty();

    verifyNoInteractions(boards);
  }
}
