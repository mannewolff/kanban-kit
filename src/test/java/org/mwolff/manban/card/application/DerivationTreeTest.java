package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Unit-Tests des Herkunftsbaums (Issue #609). Bewusst als Unit-Tests und nicht nur als
 * Integrationstests: PIT misst nur Unit-Tests, eine ausschliesslich per IT belegte Ableitung
 * hinterliesse trotz gruener Suite eine Mutationsluecke (Befund aus Issue #605).
 */
class DerivationTreeTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private CardRepository cards;
  private CardDependencyRepository dependencies;
  private CardService service;

  /** Karte dieses Boards. {@code derivedFrom} ist die ID des Vorfahren, nicht dessen Nummer. */
  private static Card card(long id, int number, @Nullable Long derivedFrom) {
    return card(id, number, derivedFrom, false, null);
  }

  private static Card card(
      long id, int number, @Nullable Long derivedFrom, boolean ideaStored, @Nullable Instant done) {
    return new Card(
        id,
        BOARD,
        100L,
        number,
        "Karte " + number,
        null,
        0,
        false,
        ideaStored,
        done,
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
        derivedFrom,
        null);
  }

  /** Karte eines fremden Boards — nur als Vorfahr ueber {@code findByIds} erreichbar. */
  private static Card fremd(long id, int number) {
    return new Card(
        id,
        99L,
        900L,
        number,
        "Fremd " + number,
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
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    dependencies = mock(CardDependencyRepository.class);
    BoardService boardService = mock(BoardService.class);
    PermissionChecker permissions = mock(PermissionChecker.class);
    ActorContext actor = mock(ActorContext.class);
    when(actor.current()).thenReturn(ActorContext.ActorStamp.unknown());
    service =
        new CardService(
            cards,
            dependencies,
            boardService,
            permissions,
            mock(ProjectService.class),
            mock(CardColumnTransitionRepository.class),
            mock(CardAssigneeRepository.class),
            mock(LabelRepository.class),
            mock(CardLabelRepository.class),
            mock(CardActivityRepository.class),
            actor,
            mock(ApplicationEventPublisher.class),
            Clock.fixed(FIXED, ZoneOffset.UTC));
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(dependencies.findByCardIds(any())).thenReturn(Map.of());
    when(cards.findByIds(any())).thenReturn(List.of());
  }

  /** ID des Vorhabens, ueber das der Baum seit Issue #645 abgerufen wird. */
  private static final long VORHABEN = 999L;

  /**
   * Baut den Baum ueber {@code epicDerivationTree}.
   *
   * <p>Seit Issue #645 gibt es keinen board-weiten Einstieg mehr. Diese Klasse traegt aber die
   * <b>Mutationsabdeckung</b> von {@link DerivationTree} — PIT misst nur Unit-Tests (Befund aus
   * Issue #605) —, und die Rechnung selbst ist unveraendert geblieben. Sie wird deshalb
   * umverdrahtet statt geloescht.
   *
   * <p>Damit jede uebergebene Karte Mitglied des Vorhabens ist, wird ihr {@code parentId} darauf
   * gesetzt: {@link EpicMembership} nimmt jede direkt zugeordnete Karte auf und steigt von dort
   * ueber die Herkunft ab. Die Testfaelle darunter bleiben dadurch unveraendert — sie beschreiben
   * die Rechnung, nicht den Einstieg.
   */
  private List<DerivationNodeView> tree(List<Card> boardCards) {
    List<Card> mitgliederUndVorhaben = new java.util.ArrayList<>();
    mitgliederUndVorhaben.add(
        new Card(
            VORHABEN,
            BOARD,
            100L,
            9_999,
            "Vorhaben",
            null,
            0,
            false,
            false,
            null,
            1L,
            FIXED,
            FIXED,
            CardType.EPIC,
            null,
            null,
            null,
            PROJECT,
            null,
            null,
            null,
            null));
    boardCards.forEach(c -> mitgliederUndVorhaben.add(c.withParent(VORHABEN)));
    when(cards.findByBoardId(BOARD)).thenReturn(List.copyOf(mitgliederUndVorhaben));
    return service.epicDerivationTree(1L, BOARD, VORHABEN);
  }

  @Test
  void praeorder_liefert_wurzel_gefolgt_von_ihrem_teilbaum() {
    // A(1) <- B(2) <- C(3): die Kette muss in genau dieser Reihenfolge und mit 0/1/2 kommen.
    List<DerivationNodeView> baum =
        tree(List.of(card(1L, 1, null), card(2L, 2, 1L), card(3L, 3, 2L)));

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(1, 2, 3);
    assertThat(baum).extracting(DerivationNodeView::depth).containsExactly(0, 1, 2);
    assertThat(baum).extracting(DerivationNodeView::derivedFrom).containsExactly(null, 1, 2);
    // Ohne diese Zusicherung waere eine Fassung gruen, die jede Zeile als board-fremd ausweist.
    assertThat(baum).noneMatch(DerivationNodeView::externalOrigin);
    assertThat(baum).noneMatch(DerivationNodeView::broken);
  }

  /**
   * Seit Issue #642 ist jede Karte ohne board-interne Herkunft eine Wurzel — auch die isolierte.
   * Der Baum wird je Vorhaben gebaut, und dessen Mitglieder sind bereits ausgewaehlt; eine manuell
   * zugeordnete Karte ohne Herkunft und ohne Nachfahren fiele sonst still heraus, und genau so
   * sieht eine Gruppierung ohne Herkunftskette aus (PO-Entscheidung in #636).
   */
  @Test
  void karte_ohne_herkunft_und_ohne_nachfahren_ist_eine_eigene_wurzel() {
    List<DerivationNodeView> baum = tree(List.of(card(1L, 1, null)));

    // Beide Seiten: Sie ist da, sie steht auf Wurzelhoehe, und sie traegt keine Herkunft. Ohne die
    // zweite und dritte Zusicherung bliebe eine Fassung gruen, die sie als Kind irgendwo einhaengt.
    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(1);
    assertThat(baum.get(0).depth()).isZero();
    assertThat(baum.get(0).derivedFrom()).isNull();
  }

  /**
   * Gegenprobe zur gelockerten Regel: Die erste Filterbedingung bleibt. Wer einen Vorfahren in der
   * Menge hat, erscheint unter ihm — nicht zusaetzlich als Wurzel. Ohne diesen Test waere eine
   * Fassung gruen, die nach dem Wegfall der zweiten Bedingung pauschal jede Karte zur Wurzel macht.
   */
  @Test
  void karte_mit_board_interner_herkunft_ist_keine_wurzel_sondern_steht_unter_ihrem_vorfahren() {
    List<DerivationNodeView> baum = tree(List.of(card(1L, 1, null), card(2L, 2, 1L)));

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(1, 2);
    assertThat(baum).extracting(DerivationNodeView::depth).containsExactly(0, 1);
    // Die Karte erscheint genau einmal — nicht zusaetzlich als eigene Wurzel.
    assertThat(baum).filteredOn(z -> z.number() == 2).hasSize(1);
  }

  @Test
  void ideen_speicher_karten_bleiben_aussen_vor() {
    // ideaStored heisst "noch nicht eingeplant" — die Karte gehoert nicht auf das Board.
    List<DerivationNodeView> baum =
        tree(List.of(card(1L, 1, null), card(2L, 2, 1L, true, null), card(3L, 3, 1L)));

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(1, 3);
  }

  @Test
  void fremde_herkunft_macht_die_karte_zur_wurzel_auch_ohne_kinder() {
    // Board-fremde Herkunft: Der Vorfahr liegt nicht in der Menge, die Karte ist also Wurzel und
    // wird als externalOrigin ausgewiesen. Seit #642 waere sie das auch ohne jede Herkunft — hier
    // geht es um die Kennzeichnung, nicht mehr um die Wurzeleigenschaft.
    when(cards.findByIds(any())).thenReturn(List.of(fremd(77L, 42)));

    List<DerivationNodeView> baum = tree(List.of(card(1L, 1, 77L)));

    assertThat(baum).hasSize(1);
    assertThat(baum.get(0).externalOrigin()).isTrue();
    assertThat(baum.get(0).depth()).isZero();
    assertThat(baum.get(0).derivedFrom()).isEqualTo(42);
  }

  @Test
  void herkunftszyklus_wird_eingesammelt_statt_still_zu_verschwinden() {
    // Ring ohne Wurzel: von keiner Wurzel erreichbar. Kleinste Nummer wird Wurzel, alles broken.
    List<DerivationNodeView> baum = tree(List.of(card(1L, 5, 2L), card(2L, 6, 1L)));

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(5, 6);
    assertThat(baum).extracting(DerivationNodeView::depth).containsExactly(0, 1);
    assertThat(baum).allMatch(DerivationNodeView::broken);
  }

  @Test
  void im_ring_gewinnt_die_kleinste_nummer_auch_wenn_der_aufstieg_woanders_begann() {
    // Der Aufstieg startet bei Nummer 9; Wurzel muss trotzdem die 8 werden. Ohne diese Regel haenge
    // die Wurzel eines Rings davon ab, welche Karte die Kartenliste zufaellig zuerst liefert.
    List<DerivationNodeView> baum = tree(List.of(card(1L, 9, 2L), card(2L, 8, 1L)));

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(8, 9);
    assertThat(baum).extracting(DerivationNodeView::depth).containsExactly(0, 1);
    assertThat(baum).allMatch(DerivationNodeView::broken);
  }

  @Test
  void ein_gesunder_teilbaum_neben_einem_ring_bleibt_unbeschaedigt() {
    List<DerivationNodeView> baum =
        tree(List.of(card(1L, 1, null), card(2L, 2, 1L), card(3L, 8, 4L), card(4L, 9, 3L)));

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(1, 2, 8, 9);
    assertThat(baum.subList(0, 2)).noneMatch(DerivationNodeView::broken);
    assertThat(baum.subList(2, 4)).allMatch(DerivationNodeView::broken);
  }

  @Test
  void done_folgt_dem_zeitstempel_und_blockiert_nicht_mehr() {
    when(dependencies.findByCardIds(any())).thenReturn(Map.of(3L, List.of(2)));
    List<DerivationNodeView> baum =
        tree(List.of(card(1L, 1, null), card(2L, 2, 1L, false, FIXED), card(3L, 3, 1L)));

    assertThat(baum).filteredOn(n -> n.number() == 2).allMatch(DerivationNodeView::done);
    // Gegenprobe: sonst waere eine Fassung gruen, die jede Zeile als erledigt meldet.
    assertThat(baum).filteredOn(n -> n.number() != 2).noneMatch(DerivationNodeView::done);
    // Die Abhaengigkeit liegt in Done -> die abhaengige Karte ist nicht blockiert.
    assertThat(baum).noneMatch(DerivationNodeView::blocked);
  }

  @Test
  void offene_board_interne_abhaengigkeit_blockiert() {
    when(dependencies.findByCardIds(any())).thenReturn(Map.of(3L, List.of(2)));
    List<DerivationNodeView> baum =
        tree(List.of(card(1L, 1, null), card(2L, 2, 1L), card(3L, 3, 1L)));

    assertThat(baum).filteredOn(n -> n.number() == 3).allMatch(DerivationNodeView::blocked);
    assertThat(baum).filteredOn(n -> n.number() == 2).noneMatch(DerivationNodeView::blocked);
  }

  @Test
  void abhaengigkeit_auf_eine_board_karte_ausserhalb_des_baums_blockiert_und_ist_nicht_extern() {
    // Der Normalfall: die Zielkarte liegt auf dem Board, aber ohne Herkunftsbezug. Aufgeloest wird
    // gegen die Board-Menge, nicht gegen die Baummenge — sonst waere sie faelschlich extern.
    when(dependencies.findByCardIds(any())).thenReturn(Map.of(2L, List.of(9)));
    List<DerivationNodeView> baum =
        tree(List.of(card(1L, 1, null), card(2L, 2, 1L), card(9L, 9, null)));

    DerivationNodeView kind = baum.stream().filter(n -> n.number() == 2).findFirst().orElseThrow();
    assertThat(kind.blocked()).isTrue();
    assertThat(kind.dependencies()).containsExactly(9);
    assertThat(kind.externalDependencies()).isEmpty();
  }

  @Test
  void board_fremde_abhaengigkeitsnummer_wird_als_extern_ausgewiesen_und_blockiert_nicht() {
    when(dependencies.findByCardIds(any())).thenReturn(Map.of(2L, List.of(4242)));
    List<DerivationNodeView> baum = tree(List.of(card(1L, 1, null), card(2L, 2, 1L)));

    DerivationNodeView kind = baum.stream().filter(n -> n.number() == 2).findFirst().orElseThrow();
    assertThat(kind.externalDependencies()).containsExactly(4242);
    assertThat(kind.dependencies()).isEmpty();
    assertThat(kind.blocked()).isFalse();
  }

  @Test
  void geschwister_stehen_topologisch_bei_gleichstand_nach_nummer() {
    // 3 blockiert 2 -> 3 vor 2. Die uebrigen Geschwister ordnet die Nummer.
    when(dependencies.findByCardIds(any())).thenReturn(Map.of(2L, List.of(3)));
    List<DerivationNodeView> baum =
        tree(List.of(card(1L, 1, null), card(2L, 2, 1L), card(3L, 3, 1L), card(4L, 4, 1L)));

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(1, 3, 2, 4);
  }

  @Test
  void geschwister_ohne_abhaengigkeiten_kommen_nach_nummer_geordnet() {
    // Die Eingabereihenfolge ist 7, 5, 6 — die Ausgabe muss 5, 6, 7 sein. Ohne die Vorsortierung
    // haenge die Reihenfolge daran, wie das Repository die Karten zufaellig liefert.
    List<DerivationNodeView> baum =
        tree(List.of(card(1L, 1, null), card(2L, 7, 1L), card(3L, 5, 1L), card(4L, 6, 1L)));

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(1, 5, 6, 7);
  }

  @Test
  void abhaengigkeit_auf_eine_nicht_geschwisterkarte_blockiert_ohne_die_position_zu_aendern() {
    // 2 haengt an 5 (Kind einer anderen Wurzel) -> blocked, aber die Geschwisterordnung 2,3 bleibt.
    when(dependencies.findByCardIds(any())).thenReturn(Map.of(2L, List.of(5)));
    List<DerivationNodeView> baum =
        tree(
            List.of(
                card(1L, 1, null),
                card(2L, 2, 1L),
                card(3L, 3, 1L),
                card(4L, 4, null),
                card(5L, 5, 4L)));

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(1, 2, 3, 4, 5);
    assertThat(baum).filteredOn(n -> n.number() == 2).allMatch(DerivationNodeView::blocked);
  }

  @Test
  void eine_selbst_abhaengigkeit_verschiebt_die_karte_nicht_nach_hinten() {
    // setDependencies lehnt den Selbstbezug ab, in der Spalte kann er trotzdem stehen. Ohne die
    // Selbstbezugs-Kante in der Geschwisterordnung wartete die Karte auf sich selbst und rutschte
    // hinter ihre Geschwister — die Reihenfolge haenge dann an einem Datenfehler.
    when(dependencies.findByCardIds(any())).thenReturn(Map.of(2L, List.of(2)));
    List<DerivationNodeView> baum =
        tree(List.of(card(1L, 1, null), card(2L, 2, 1L), card(3L, 3, 1L)));

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(1, 2, 3);
  }

  @Test
  void abhaengigkeitszyklus_unter_geschwistern_bleibt_deterministisch() {
    // A<->B ist ueber die normale API anlegbar (setDependencies prueft keinen Zyklus).
    when(dependencies.findByCardIds(any())).thenReturn(Map.of(2L, List.of(3), 3L, List.of(2)));
    List<DerivationNodeView> baum =
        tree(List.of(card(1L, 1, null), card(2L, 2, 1L), card(3L, 3, 1L)));

    assertThat(baum).extracting(DerivationNodeView::number).containsExactly(1, 2, 3);
  }

  @Test
  void die_knotenmenge_kommt_in_einem_zug_ohne_nachladen() {
    tree(List.of(card(1L, 1, null), card(2L, 2, 1L), card(3L, 3, 2L)));

    verify(cards, times(1)).findByBoardId(BOARD);
    verify(cards, never()).findById(anyLong());
    verify(cards, never()).findByDerivedFrom(anyLong());
    verify(dependencies, never()).findByCardId(anyLong());
    // Liegen alle Vorfahren auf diesem Board, gibt es nichts nachzuschlagen — der Zugriff auf
    // board-fremde Nummern darf dann gar nicht erst stattfinden.
    verify(cards, never()).findByIds(any());
  }
}
