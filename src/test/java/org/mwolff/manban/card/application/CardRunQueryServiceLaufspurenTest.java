package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardRunQueryService.LaufKarteView;
import org.mwolff.manban.card.application.CardRunQueryService.TokenActivityView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Unit-Tests der Lese-Abfragen für den Fortschritt eines laufenden Laufs (Issue #1373).
 *
 * <p>Eigene Klasse wie {@code CardServiceExistingCardNumbersTest}: {@code CardServiceTest} steht an
 * seinen PMD-Grenzen. Als Unit-Test, weil PIT allein Unit-Tests misst.
 */
class CardRunQueryServiceLaufspurenTest {

  private static final Instant FIXED = Instant.parse("2026-10-03T15:00:00Z");
  private static final Instant VON = Instant.parse("2026-10-03T15:00:00Z");
  private static final Instant BIS = Instant.parse("2026-10-03T16:00:00Z");
  private static final long PROJECT = 1L;
  private static final long BOARD = 2L;
  private static final long ANDERES_BOARD = 3L;

  private CardRepository cards;
  private CardActivityRepository activity;
  private LabelRepository labels;
  private CardLabelRepository cardLabels;
  private CardRunQueryService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    activity = mock(CardActivityRepository.class);
    labels = mock(LabelRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    service =
        new CardRunQueryService(
            cards,
            activity,
            new KartenZuordnung(
                mock(CardAssigneeRepository.class),
                labels,
                cardLabels,
                mock(PermissionChecker.class)));
  }

  private static Card karte(
      long id,
      long board,
      String titel,
      CardType type,
      CardStatus status,
      Long derivedFrom,
      String description) {
    return new Card(
        id,
        board,
        10L,
        (int) id + 100,
        titel,
        description,
        0,
        false,
        null,
        null,
        FIXED,
        FIXED,
        type,
        null,
        null,
        null,
        PROJECT,
        null,
        derivedFrom,
        null,
        status);
  }

  @Test
  void tokenActivitiesInWindowLiefertKarteTypUndZeitpunktJeEintrag() {
    when(activity.findTokenActivitiesInWindow(PROJECT, "Nachtlauf", VON, BIS))
        .thenReturn(
            List.of(
                new CardActivity(
                    1L,
                    7L,
                    null,
                    CardActivityType.CREATED,
                    "",
                    VON,
                    CardActivityOrigin.TOKEN,
                    "Nachtlauf",
                    "claude-opus-5-5",
                    null,
                    null),
                new CardActivity(
                    2L,
                    8L,
                    null,
                    CardActivityType.MOVED,
                    "x",
                    BIS,
                    CardActivityOrigin.TOKEN,
                    "Nachtlauf",
                    "claude-opus-5-5",
                    null,
                    null)));

    assertThat(service.tokenActivitiesInWindow(PROJECT, "Nachtlauf", VON, BIS))
        .containsExactly(
            new TokenActivityView(7L, "CREATED", VON), new TokenActivityView(8L, "MOVED", BIS));
  }

  @Test
  void cardsByIdsOhneIdsFragtKeinenPort() {
    assertThat(service.cardsByIds(List.of())).isEmpty();

    verifyNoInteractions(cards, cardLabels, labels);
  }

  @Test
  void cardsByIdsLiefertDieAngabenDerErmittlung() {
    Card paket =
        karte(5L, BOARD, "Paket 1/7", CardType.CARD, CardStatus.IN_PROGRESS, 4L, "Plan-Review: x");
    Card plan = karte(4L, BOARD, "[Plan] Fortschritt", CardType.CARD, null, 3L, null);
    when(cards.findByIds(Set.of(5L, 4L))).thenReturn(List.of(paket, plan));
    when(cardLabels.findByCardIds(List.of(5L, 4L))).thenReturn(Map.of(5L, List.of(20L, 21L)));
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                new Label(20L, BOARD, "lauf:laeuft", "#000", false),
                new Label(21L, BOARD, "kit:klaeren", "#000", false),
                new Label(22L, BOARD, "unbenutzt", "#000", false)));

    List<LaufKarteView> gefunden = service.cardsByIds(List.of(5L, 4L));

    assertThat(gefunden)
        .containsExactly(
            new LaufKarteView(
                5L,
                105,
                "Paket 1/7",
                BOARD,
                "IN_PROGRESS",
                List.of("kit:klaeren", "lauf:laeuft"),
                4L,
                "CARD",
                true,
                "Plan-Review: x"),
            new LaufKarteView(
                4L, 104, "[Plan] Fortschritt", BOARD, null, List.of(), 3L, "CARD", false, null));
  }

  @Test
  void cardsByIdsFolgtDerEingabeUndUebergehtUnbekannteIds() {
    Card erste = karte(5L, BOARD, "Erste", CardType.CARD, null, null, null);
    Card zweite = karte(4L, BOARD, "Zweite", CardType.CARD, null, null, null);
    when(cards.findByIds(Set.of(4L, 99L, 5L))).thenReturn(List.of(erste, zweite));

    assertThat(service.cardsByIds(List.of(4L, 99L, 5L)))
        .extracting(LaufKarteView::id)
        .containsExactly(4L, 5L);
  }

  @Test
  void cardsByIdsLoestLabelsJeBoardAufUndErkenntVorhaben() {
    Card vorhaben = karte(6L, ANDERES_BOARD, "Vorhaben", CardType.EPIC, null, null, null);
    when(cards.findByIds(Set.of(6L))).thenReturn(List.of(vorhaben));
    when(cardLabels.findByCardIds(List.of(6L))).thenReturn(Map.of(6L, List.of(30L, 99L)));
    when(labels.findByBoardId(ANDERES_BOARD))
        .thenReturn(List.of(new Label(30L, ANDERES_BOARD, "lauf:fertig", "#000", false)));

    assertThat(service.cardsByIds(List.of(6L)))
        .singleElement()
        .satisfies(
            k -> {
              assertThat(k.type()).isEqualTo("EPIC");
              assertThat(k.arbeitspaket()).isFalse();
              // Ein Label, das das Board nicht (mehr) kennt, fällt still heraus.
              assertThat(k.labels()).containsExactly("lauf:fertig");
              assertThat(k.derivedFromCardId()).isNull();
            });
  }
}
