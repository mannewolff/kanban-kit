package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit-Tests der Sammelzugriffe von {@code listByBoard} (Issue #768).
 *
 * <p>Eigene Klasse und nicht ein weiterer Block in {@code CardServiceTest} — wie schon bei {@link
 * CardServiceEpicTreeTest}: Jene Klasse steht an ihren PMD-Grenzen (Größe und Import-Zahl).
 *
 * <p>Gemessen wird an den <b>Ports</b> und nicht mit Hibernate-Statistiken: Die drei beteiligten
 * Repositories sind reines JDBC und tauchen in der JPA-Statistik gar nicht auf. Jeder Test hält
 * beide Seiten fest — welcher Sammelzugriff läuft und dass der Einzel-Finder daneben schweigt.
 */
class CardServiceListByBoardTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;
  private static final Set<Long> KARTEN_IDS = Set.of(1L, 2L);

  private CardRepository cards;
  private CardDependencyRepository dependencies;
  private CardAssigneeRepository assignees;
  private CardLabelRepository cardLabels;
  private CardService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    dependencies = mock(CardDependencyRepository.class);
    assignees = mock(CardAssigneeRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    BoardService boardService = mock(BoardService.class);
    ActorContext actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    service =
        new CardService(
            cards,
            dependencies,
            boardService,
            mock(PermissionChecker.class),
            mock(ProjectService.class),
            mock(CardColumnTransitionRepository.class),
            assignees,
            mock(LabelRepository.class),
            cardLabels,
            mock(CardActivityRepository.class),
            actor,
            mock(ApplicationEventPublisher.class),
            Clock.fixed(FIXED, ZoneOffset.UTC));
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
  }

  private static Card karte(long id, int number, CardType type, @Nullable Long derivedFrom) {
    return new Card(
        id,
        BOARD,
        20L,
        number,
        "Titel",
        null,
        0,
        false,
        false,
        null,
        1L,
        FIXED,
        FIXED,
        type,
        null,
        null,
        null,
        PROJECT,
        null,
        null,
        derivedFrom,
        null);
  }

  @Test
  void laedtZusatzdatenGesammeltStattJeKarte() {
    // Given: zwei Karten und ein Vorhaben; Karte 1 mit Abhaengigkeiten, Zustaendigen, Labels und
    // Herkunft, Karte 2 ohne alles.
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                karte(1L, 1, CardType.CARD, 99L),
                karte(2L, 2, CardType.CARD, null),
                karte(3L, 3, CardType.EPIC, null)));
    when(dependencies.findByCardIds(KARTEN_IDS)).thenReturn(Map.of(1L, List.of(7)));
    when(assignees.findByCardIds(KARTEN_IDS)).thenReturn(Map.of(1L, List.of(42L)));
    when(cardLabels.findByCardIds(KARTEN_IDS)).thenReturn(Map.of(1L, List.of(5L)));
    when(cards.findByIds(Set.of(99L))).thenReturn(List.of(karte(99L, 88, CardType.CARD, null)));

    // When
    List<CardService.CardView> result = service.listByBoard(1L, BOARD);

    // Then: je ein Sammelzugriff ueber genau die CARD-IDs — das Vorhaben ist nicht dabei.
    verify(dependencies, times(1)).findByCardIds(KARTEN_IDS);
    verify(assignees, times(1)).findByCardIds(KARTEN_IDS);
    verify(cardLabels, times(1)).findByCardIds(KARTEN_IDS);
    verify(cards, times(1)).findByIds(any());
    verify(dependencies, never()).findByCardId(anyLong());
    verify(assignees, never()).findByCardId(anyLong());
    verify(cardLabels, never()).findByCardId(anyLong());
    verify(cards, never()).findById(anyLong());

    assertThat(result)
        .extracting(
            CardService.CardView::id,
            CardService.CardView::dependencies,
            CardService.CardView::assignees,
            CardService.CardView::labels,
            CardService.CardView::derivedFrom)
        .containsExactly(
            tuple(1L, List.of(7), List.of(42L), List.of(5L), 88),
            tuple(2L, List.of(), List.of(), List.of(), null));
  }

  @Test
  void ohneKarten_fragtKeineHerkunftAb() {
    // Given: nur ein Vorhaben — seine Herkunft gehoert nicht in den Sammelzugriff.
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(karte(3L, 3, CardType.EPIC, 99L)));

    // When / Then
    assertThat(service.listByBoard(1L, BOARD)).isEmpty();
    verify(cards, never()).findByIds(any());
  }

  // --- Auszug statt voller Beschreibung (Issue #771) --------------------

  private static Card karteMit(long id, @Nullable String beschreibung) {
    return karte(id, (int) id, CardType.CARD, null).withContent("Titel", beschreibung);
  }

  @Test
  void kuerztEineLangeBeschreibungAufZweihundertZeichen() {
    // Given: eine Beschreibung, die die Auszugsgrenze um genau ein Zeichen ueberschreitet.
    String lang = "a".repeat(201);
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(karteMit(1L, lang)));

    // When
    CardService.CardView sicht = service.listByBoard(1L, BOARD).getFirst();

    // Then: der Auszug traegt die Vorschau, die volle Beschreibung bleibt der Einzelabfrage.
    assertThat(sicht.excerpt()).hasSize(200).isEqualTo(lang.substring(0, 200));
    assertThat(sicht.description()).isNull();
  }

  @Test
  void schneidetKeinEmojiInDerMitteDurch() {
    // Given: 201 Emojis — jedes belegt zwei char, aber nur einen Codepoint. Wer auf char-Ebene
    // schneidet, liefert am Ende ein halbes Surrogatpaar und damit ein kaputtes Zeichen.
    String emojis = "🚀".repeat(201);
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(karteMit(1L, emojis)));

    // When
    String auszug = service.listByBoard(1L, BOARD).getFirst().excerpt();

    // Then
    assertThat(auszug).isNotNull();
    assertThat(auszug.codePointCount(0, auszug.length())).isEqualTo(200);
    assertThat(emojis).startsWith(auszug);
  }

  @Test
  void laesstEineKurzeBeschreibungUnveraendert() {
    // Given: eine kurze Beschreibung und eine, die die Grenze genau ausschoepft.
    String genauGrenze = "b".repeat(200);
    when(cards.findByBoardId(BOARD))
        .thenReturn(List.of(karteMit(1L, "Kurz und knapp"), karteMit(2L, genauGrenze)));

    // When
    List<CardService.CardView> sichten = service.listByBoard(1L, BOARD);

    // Then
    assertThat(sichten)
        .extracting(CardService.CardView::excerpt)
        .containsExactly("Kurz und knapp", genauGrenze);
    assertThat(sichten).extracting(CardService.CardView::description).containsOnlyNulls();
  }

  @Test
  void ohneBeschreibungBleibtDerAuszugLeer() {
    // Given
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(karteMit(1L, null)));

    // When / Then
    assertThat(service.listByBoard(1L, BOARD).getFirst().excerpt()).isNull();
  }
}
