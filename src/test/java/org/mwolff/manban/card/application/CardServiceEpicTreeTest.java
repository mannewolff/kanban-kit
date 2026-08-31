package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardService.DerivationNodeView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit-Tests des Herkunftsbaums <b>eines Vorhabens</b> (Issue #643).
 *
 * <p>Eigene Klasse und nicht ein weiterer Block in {@code CardServiceTest}: Jene Klasse steht an
 * ihren PMD-Grenzen (Größe und Import-Zahl), und der Vorhaben-Baum ist ein abgeschlossenes Thema
 * mit eigenem Fixture-Zuschnitt.
 *
 * <p>Als Unit-Test und nicht nur als Integrationstest, weil PIT ausschliesslich Unit-Tests misst
 * (Befund aus Issue #605): Die Ableitungen wären sonst trotz grüner Suite eine Mutationslücke.
 *
 * <p>Jede Zusicherung hält <b>beide</b> Seiten fest: was im Baum steht und was nicht.
 */
class CardServiceEpicTreeTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;
  private static final long EPIC = 5L;

  private CardRepository cards;
  private CardDependencyRepository dependencies;
  private BoardService boardService;
  private CardService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    dependencies = mock(CardDependencyRepository.class);
    boardService = mock(BoardService.class);
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
            mock(CardAssigneeRepository.class),
            mock(LabelRepository.class),
            mock(CardLabelRepository.class),
            mock(CardActivityRepository.class),
            actor,
            mock(ApplicationEventPublisher.class),
            Clock.fixed(FIXED, ZoneOffset.UTC));
  }

  private static Card karte(
      long id,
      int number,
      CardType type,
      @Nullable Long parentId,
      @Nullable Long derivedFrom,
      boolean archived) {
    return new Card(
        id,
        BOARD,
        20L,
        number,
        "Titel",
        null,
        0,
        archived,
        false,
        null,
        1L,
        FIXED,
        FIXED,
        type,
        parentId,
        null,
        null,
        PROJECT,
        null,
        null,
        derivedFrom,
        null);
  }

  private static Card mitglied(
      long id, int number, @Nullable Long parentId, @Nullable Long derivedFrom) {
    return karte(id, number, CardType.CARD, parentId, derivedFrom, false);
  }

  private static Card vorhaben(long id, int number) {
    return karte(id, number, CardType.EPIC, null, null, false);
  }

  /** Stubs, die jeder Baum-Test braucht: Rechte, Board-Karten, keine Abhaengigkeiten. */
  private void stub(List<Card> boardKarten) {
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.findByBoardId(BOARD)).thenReturn(boardKarten);
    when(dependencies.findByCardIds(any())).thenReturn(Map.of());
  }

  /**
   * Die Baumzeile zu einer Kartennummer. Nicht ueber die Position: {@code sortiereGeschwister}
   * ordnet topologisch, ein Blocker steht also vor dem Blockierten.
   */
  private static DerivationNodeView zeile(List<DerivationNodeView> baum, int nummer) {
    return baum.stream().filter(z -> z.number() == nummer).findFirst().orElseThrow();
  }

  private List<DerivationNodeView> baum() {
    return service.epicDerivationTree(1L, BOARD, EPIC);
  }

  @Test
  void dreistufigeKette_liefertDreiEbenenInPraeorder() {
    stub(
        List.of(
            vorhaben(EPIC, 1),
            mitglied(6L, 2, EPIC, null),
            mitglied(7L, 3, null, 6L),
            mitglied(8L, 4, null, 7L)));

    List<DerivationNodeView> baum = baum();

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(2, 3, 4);
    assertThat(baum).extracting(DerivationNodeView::depth).containsExactly(0, 1, 2);
    // Das Vorhaben selbst ist kein Mitglied seines eigenen Baums.
    assertThat(baum).extracting(DerivationNodeView::number).doesNotContain(1);
  }

  /** Setzt Issue #642 voraus: Ohne die gelockerte Wurzelregel waere dieser Baum leer. */
  @Test
  void ohneAnforderung_zeigtDieZugeordnetenAlsEigeneWurzeln() {
    stub(List.of(vorhaben(EPIC, 1), mitglied(6L, 2, EPIC, null), mitglied(7L, 3, EPIC, null)));

    List<DerivationNodeView> baum = baum();

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(2, 3);
    assertThat(baum).extracting(DerivationNodeView::depth).containsExactly(0, 0);
  }

  @Test
  void karteInZweiVorhaben_erscheintInBeidenBaeumen() {
    long epicB = 9L;
    stub(
        List.of(
            vorhaben(EPIC, 1),
            vorhaben(epicB, 5),
            mitglied(6L, 2, EPIC, null),
            // Zu A ueber die Herkunft, zu B ueber die Zuordnung.
            mitglied(7L, 3, epicB, 6L),
            mitglied(8L, 4, null, null)));

    List<DerivationNodeView> baumA = baum();
    List<DerivationNodeView> baumB = service.epicDerivationTree(1L, BOARD, epicB);

    assertThat(baumA).extracting(DerivationNodeView::number).containsExactly(2, 3);
    assertThat(baumB).extracting(DerivationNodeView::number).containsExactly(3);
    // Beide Seiten: Die unbeteiligte Karte 4 steht in keinem der beiden Baeume.
    assertThat(baumA).extracting(DerivationNodeView::number).doesNotContain(4);
    assertThat(baumB).extracting(DerivationNodeView::number).doesNotContain(4);
  }

  @Test
  void vorhabenOhneZuordnung_liefertLeereListeStattFehler() {
    stub(List.of(vorhaben(EPIC, 1), mitglied(6L, 2, null, null)));

    assertThat(baum()).isEmpty();
  }

  /**
   * Der leere Baum und das unbekannte Vorhaben duerfen nicht zusammenfallen: Ein {@code
   * getOrDefault} lieferte beide Male eine leere Liste, und der Client koennte „existiert nicht"
   * nicht vom legitimen Leer-Fall unterscheiden.
   */
  @Test
  void keinVorhabenDiesesBoards_liefertNichtGefunden() {
    stub(List.of(vorhaben(EPIC, 1), mitglied(6L, 2, EPIC, null)));

    // Karte 6 existiert, ist aber kein Vorhaben.
    assertThatThrownBy(() -> service.epicDerivationTree(1L, BOARD, 6L))
        .isInstanceOf(CardNotFoundException.class);
    // Und eine ID, die es auf diesem Board gar nicht gibt.
    assertThatThrownBy(() -> service.epicDerivationTree(1L, BOARD, 999L))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void abhaengigkeitAusserhalbDesVorhabens_giltAlsExternUndBlocktNicht() {
    stub(List.of(vorhaben(EPIC, 1), mitglied(6L, 2, EPIC, null), mitglied(9L, 9, null, null)));
    // Karte 6 haengt von der offenen Board-Karte 9 ab, die nicht zum Vorhaben gehoert.
    when(dependencies.findByCardIds(any())).thenReturn(Map.of(6L, List.of(9)));

    DerivationNodeView z = zeile(baum(), 2);

    assertThat(z.externalDependencies()).containsExactly(9);
    assertThat(z.dependencies()).isEmpty();
    assertThat(z.blocked()).isFalse();
  }

  /** Gegenrichtung zum Test darueber: Dieselbe offene Abhaengigkeit INNERHALB blockt sehr wohl. */
  @Test
  void abhaengigkeitInnerhalbDesVorhabens_blockt() {
    stub(List.of(vorhaben(EPIC, 1), mitglied(6L, 2, EPIC, null), mitglied(9L, 9, EPIC, null)));
    when(dependencies.findByCardIds(any())).thenReturn(Map.of(6L, List.of(9)));

    DerivationNodeView z = zeile(baum(), 2);

    assertThat(z.dependencies()).containsExactly(9);
    assertThat(z.externalDependencies()).isEmpty();
    assertThat(z.blocked()).isTrue();
  }

  /**
   * Gegenstueck zum Test darunter: Eine <b>board-fremde</b> Herkunft behaelt ihre Nummer. Nur so
   * unterscheidet der Baum die beiden Aussenfaelle — der board-fremde Vorfahr wird benannt, das
   * board-interne Nicht-Mitglied bleibt namenlos.
   */
  @Test
  void boardFremderVorfahr_bleibtExternBehaeltAberSeineNummer() {
    stub(List.of(vorhaben(EPIC, 1), mitglied(6L, 2, EPIC, 77L)));
    Card fremderVorfahr =
        new Card(
            77L,
            99L,
            20L,
            42,
            "Fremd",
            null,
            0,
            false,
            false,
            null,
            1L,
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
    when(cards.findByIds(any())).thenReturn(List.of(fremderVorfahr));

    DerivationNodeView z = zeile(baum(), 2);

    assertThat(z.externalOrigin()).isTrue();
    assertThat(z.derivedFrom()).isEqualTo(42);
    assertThat(z.depth()).isZero();
  }

  /**
   * Ein archiviertes Mittelglied gehoert nicht zum Vorhaben, kappt die Kette aber nicht (#632). Aus
   * Sicht von {@code build} liegt der Vorfahr des Enkels damit ausserhalb der Menge: Der Enkel wird
   * Wurzel und als extern ausgewiesen — <b>ohne</b> Nummer, denn das Mittelglied liegt auf dem
   * Board und steht deshalb nicht in der Map der board-fremden Vorfahren.
   */
  @Test
  void archiviertesMittelglied_machtDenEnkelZurExternenWurzel() {
    stub(
        List.of(
            vorhaben(EPIC, 1),
            mitglied(6L, 2, EPIC, null),
            karte(7L, 3, CardType.CARD, null, 6L, true),
            mitglied(8L, 4, null, 7L)));

    List<DerivationNodeView> baum = baum();

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(2, 4);
    // Beide Seiten: Das archivierte Mittelglied fehlt, der Enkel steht als externe Wurzel da.
    assertThat(baum).extracting(DerivationNodeView::number).doesNotContain(3);
    DerivationNodeView enkel = zeile(baum, 4);
    assertThat(enkel.depth()).isZero();
    assertThat(enkel.externalOrigin()).isTrue();
    assertThat(enkel.derivedFrom()).isNull();
  }
}
