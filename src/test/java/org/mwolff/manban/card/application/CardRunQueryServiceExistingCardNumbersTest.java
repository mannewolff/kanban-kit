package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Unit-Tests der schmalen Existenzabfrage zu Kartennummern über die card-Fassade (Issue #1169).
 *
 * <p>Eigene Klasse wie {@code CardServiceEpicsByCardNumberTest}: {@code CardServiceTest} steht an
 * seinen PMD-Grenzen. Als Unit-Test, weil PIT allein Unit-Tests misst.
 */
class CardRunQueryServiceExistingCardNumbersTest {

  private static final long PROJECT = 1L;

  private CardRepository cards;
  private CardRunQueryService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    service =
        new CardRunQueryService(
            cards,
            mock(CardActivityRepository.class),
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                mock(LabelRepository.class),
                mock(CardLabelRepository.class),
                mock(PermissionChecker.class)),
            mock(BoardService.class));
  }

  @Test
  void reichtDieAntwortDesPortsDurch() {
    when(cards.findExistingNumbers(PROJECT, Set.of(964, 965))).thenReturn(Set.of(964, 965));

    assertThat(service.existingCardNumbers(PROJECT, List.of(964, 965)))
        .containsExactlyInAnyOrder(964, 965);
  }

  @Test
  void liefertNurDieVorhandenenNummern() {
    when(cards.findExistingNumbers(PROJECT, Set.of(964, 4711))).thenReturn(Set.of(964));

    assertThat(service.existingCardNumbers(PROJECT, List.of(964, 4711))).containsExactly(964);
  }

  @Test
  void eineNummernmengeOhneTrefferLiefertEineLeereMenge() {
    when(cards.findExistingNumbers(PROJECT, Set.of(4711))).thenReturn(Set.of());

    assertThat(service.existingCardNumbers(PROJECT, List.of(4711))).isEmpty();
  }

  @Test
  void eineLeereNummernmengeFragtDieDatenbankNicht() {
    assertThat(service.existingCardNumbers(PROJECT, Set.of())).isEmpty();

    verifyNoInteractions(cards);
  }
}
