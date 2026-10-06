package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Unit-Tests der Titel zu Kartennummern eines Projekts für die Morgenmeldung (Issue #1457).
 *
 * <p>Eigene Klasse wie {@code CardRunQueryServiceFreigabenTest}. Als Unit-Test, weil PIT allein
 * Unit-Tests misst.
 */
class CardRunQueryServiceTitelTest {

  private static final Instant FIXED = Instant.parse("2026-10-05T15:00:00Z");
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

  private static Card karte(long id, int number, String titel) {
    return new Card(
        id,
        2L,
        10L,
        number,
        titel,
        null,
        0,
        false,
        null,
        null,
        FIXED,
        FIXED,
        CardType.CARD,
        null,
        null,
        null,
        PROJECT,
        null,
        null,
        null,
        null);
  }

  @Test
  void liefertDieTitelDerGefragtenNummernAusDemProjekt() {
    when(cards.findByProjectId(PROJECT))
        .thenReturn(
            List.of(karte(5L, 1449, "Paket A"), karte(6L, 1450, "Paket B"), karte(7L, 1, "X")));

    assertThat(service.titlesByCardNumber(PROJECT, List.of(1449, 1450)))
        .isEqualTo(Map.of(1449, "Paket A", 1450, "Paket B"));
    verify(cards).findByProjectId(PROJECT);
  }

  @Test
  void eineUnbekannteNummerFehltImErgebnis() {
    when(cards.findByProjectId(PROJECT)).thenReturn(List.of(karte(5L, 1449, "Paket A")));

    assertThat(service.titlesByCardNumber(PROJECT, Set.of(1449, 4711)))
        .isEqualTo(Map.of(1449, "Paket A"));
  }

  @Test
  void eineLeereNummernmengeFragtDieDatenbankNicht() {
    assertThat(service.titlesByCardNumber(PROJECT, Set.of())).isEmpty();

    verifyNoInteractions(cards);
  }
}
