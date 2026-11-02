package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;

/**
 * Die Projekte mit Vorhaben über die card-Fassade (Issue #2004): je Board mit Vorhaben sein
 * Projekt, jedes einmal und aufsteigend; ein Board ohne Projekt fällt weg.
 */
class CardRunQueryServiceProjekteMitVorhabenTest {

  private final CardRepository cards = mock(CardRepository.class);
  private final BoardService boards = mock(BoardService.class);
  private final CardRunQueryService service =
      new CardRunQueryService(
          cards,
          mock(CardActivityRepository.class),
          mock(KartenZuordnung.class),
          boards,
          mock(CardLabelEventRepository.class));

  @Test
  void jedesProjektEinmalUndAufsteigend() {
    when(cards.findBoardIdsWithEpics()).thenReturn(List.of(30L, 10L, 20L, 40L));
    when(boards.findProjectId(30L)).thenReturn(Optional.of(7L));
    when(boards.findProjectId(10L)).thenReturn(Optional.of(3L));
    when(boards.findProjectId(20L)).thenReturn(Optional.of(7L));
    when(boards.findProjectId(40L)).thenReturn(Optional.empty());

    assertThat(service.projekteMitVorhaben()).containsExactly(3L, 7L);
  }

  @Test
  void ohneVorhabenKeinProjekt() {
    when(cards.findBoardIdsWithEpics()).thenReturn(List.of());

    assertThat(service.projekteMitVorhaben()).isEmpty();
  }
}
