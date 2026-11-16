package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremdeKarte;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremderAkteur;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Schreibweg des Spiegels ohne Datenbank (Issue #2175): Je Methode die Zweige, die Aufrufe an die
 * Ports, der gesetzte Zeitpunkt der Transaktion und die Herkunft des Akteurs. Das Zusammenspiel mit
 * der Datenbank zeigt {@code FremdKartenSchreiberIT}; dieser Test trägt die Mutationsprüfung, die
 * keine Integrationstests fährt.
 */
// PMD.TooManyMethods: ein Fall je Zweig über alle Schreibwege des Spiegels; die gemeinsame Fixture
// ist der Zweck der Klasse.
@SuppressWarnings("PMD.TooManyMethods")
class FremdKartenSchreiberTest {

  private static final Instant FRUEHER = Instant.parse("2026-11-01T08:00:00Z");
  private static final Instant ZEITPUNKT = Instant.parse("2026-11-15T10:00:00Z");
  private static final long PROJEKT = 3L;
  private static final long BOARD = 10L;
  private static final long KARTEN_ID = 55L;
  private static final int NUMMER = 42;
  private static final long BACKLOG = 100L;
  private static final long READY = 101L;
  private static final long IN_PROGRESS = 102L;
  private static final long DONE = 104L;
  private static final FremderAkteur KIT = new FremderAkteur("kit", true);
  private static final FremderAkteur MENSCH = new FremderAkteur("jdoe", false);

  private CardRepository cards;
  private BoardService boardService;
  private CardColumnTransitionRepository transitions;
  private CardActivityRepository activity;
  private CardLabelEventRepository labelEvents;
  private LabelRepository labels;
  private CardLabelRepository cardLabels;
  private KartenGrundlage grundlage;
  private boolean fremd;
  private FremdKartenSchreiber schreiber;

  private static List<ColumnView> spalten() {
    return List.of(
        new ColumnView(BACKLOG, "Backlog", 0, null),
        new ColumnView(READY, "Ready", 1, null),
        new ColumnView(IN_PROGRESS, "In progress", 2, null),
        new ColumnView(103L, "In review", 3, null),
        new ColumnView(DONE, "Done", 4, null));
  }

  private static Card karte(
      long spalte, String titel, String text, String externUrl, Long derivedFrom) {
    return new Card(
        KARTEN_ID,
        BOARD,
        spalte,
        NUMMER,
        titel,
        text,
        0,
        false,
        null,
        null,
        FRUEHER,
        FRUEHER,
        CardType.CARD,
        null,
        null,
        null,
        PROJEKT,
        null,
        derivedFrom,
        null,
        null,
        null,
        externUrl);
  }

  private static Card karte(long spalte) {
    return karte(spalte, "Titel", "Text", "https://git.example/1", null);
  }

  @BeforeEach
  void setUp() {
    TransactionSynchronizationManager.initSynchronization();
    cards = mock(CardRepository.class);
    boardService = mock(BoardService.class);
    transitions = mock(CardColumnTransitionRepository.class);
    activity = mock(CardActivityRepository.class);
    labelEvents = mock(CardLabelEventRepository.class);
    labels = mock(LabelRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    grundlage = mock(KartenGrundlage.class);
    fremd = true;
    schreiber =
        new FremdKartenSchreiber(
            cards,
            boardService,
            transitions,
            activity,
            labelEvents,
            labels,
            cardLabels,
            grundlage,
            projectId -> fremd && projectId == PROJEKT);
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJEKT);
    when(boardService.listColumns(BOARD)).thenReturn(spalten());
  }

  @AfterEach
  void tearDown() {
    TransactionSynchronizationManager.unbindResourceIfPossible(FlussZeitpunkt.class);
    TransactionSynchronizationManager.clearSynchronization();
  }

  // --- anlegen ---

  @Test
  void anlegen_legtKarteInDerSpalteIhresStatusAn() {
    when(cards.allocateActivePosition(IN_PROGRESS)).thenReturn(7);
    when(cards.save(any())).thenReturn(karte(IN_PROGRESS));

    long id =
        schreiber.anlegen(
            BOARD,
            new FremdeKarte(NUMMER, "  Titel  ", "Text", "IN_PROGRESS", "https://git.example/1"),
            ZEITPUNKT,
            KIT);

    assertThat(id).isEqualTo(KARTEN_ID);
    assertThat(FlussZeitpunkt.vorgabe()).contains(ZEITPUNKT);
    InOrder reihenfolge = inOrder(cards);
    reihenfolge.verify(cards).lockCardNumbers(PROJEKT);
    reihenfolge.verify(cards).isNumberTaken(PROJEKT, NUMMER);
    ArgumentCaptor<Card> gespeichert = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(gespeichert.capture());
    assertThat(gespeichert.getValue().columnId()).isEqualTo(IN_PROGRESS);
    assertThat(gespeichert.getValue().title()).isEqualTo("Titel");
    assertThat(gespeichert.getValue().positionInColumn()).isEqualTo(7);
    assertThat(gespeichert.getValue().status()).isEqualTo(CardStatus.IN_PROGRESS);
    verify(transitions).open(KARTEN_ID, IN_PROGRESS, "In progress", ZEITPUNKT);
    verify(activity)
        .addFremd(
            KARTEN_ID,
            CardActivityType.CREATED,
            "Karte angelegt",
            ZEITPUNKT,
            CardActivityOrigin.TOKEN,
            "kit",
            null);
    verify(grundlage).publishChanged(BOARD, ActivityType.CREATED, KARTEN_ID);
  }

  @Test
  void anlegen_vergebeneNummer_lehntAbOhneZuSchreiben() {
    when(cards.isNumberTaken(PROJEKT, NUMMER)).thenReturn(true);

    assertThatThrownBy(
            () ->
                schreiber.anlegen(
                    BOARD, new FremdeKarte(NUMMER, "Titel", null, "BACKLOG", null), ZEITPUNKT, KIT))
        .isInstanceOf(CardNumberConflictException.class);

    verify(cards, never()).save(any());
    verifyNoInteractions(transitions, activity, grundlage);
  }

  @Test
  void anlegen_boardOhneSpalteDesStatus_lehntAb() {
    when(boardService.listColumns(BOARD))
        .thenReturn(List.of(new ColumnView(BACKLOG, "Backlog", 0, null)));

    assertThatThrownBy(
            () ->
                schreiber.anlegen(
                    BOARD, new FremdeKarte(NUMMER, "Titel", null, "DONE", null), ZEITPUNKT, KIT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Spiegel-Board ohne Spalte für Status DONE");

    verify(cards, never()).save(any());
  }

  @Test
  void anlegen_projektOhneFremdesBoard_lehntAbOhneZuSchreiben() {
    fremd = false;

    assertThatThrownBy(
            () ->
                schreiber.anlegen(
                    BOARD, new FremdeKarte(NUMMER, "Titel", null, "BACKLOG", null), ZEITPUNKT, KIT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Projekt " + PROJEKT);

    verifyNoInteractions(cards, transitions, activity, grundlage);
  }

  // --- aktualisieren ---

  private void aktualisierenVorbereiten() {
    when(cards.findByProjectIdAndNumber(PROJEKT, NUMMER)).thenReturn(Optional.of(karte(BACKLOG)));
    when(grundlage.folgeArtwechsel(any(), any())).thenAnswer(a -> a.getArgument(1));
    when(grundlage.folgeWegBeleg(any(), any())).thenAnswer(a -> a.getArgument(1));
  }

  @Test
  void aktualisieren_ohneAenderung_laesstKarteUnberuehrt() {
    aktualisierenVorbereiten();

    boolean geaendert =
        schreiber.aktualisieren(
            BOARD, NUMMER, " Titel ", "Text", "https://git.example/1", ZEITPUNKT);

    assertThat(geaendert).isFalse();
    assertThat(FlussZeitpunkt.vorgabe()).contains(ZEITPUNKT);
    verify(cards, never()).save(any());
    verifyNoInteractions(grundlage);
  }

  @Test
  void aktualisieren_neuerTitel_speichertUndMeldet() {
    aktualisierenVorbereiten();

    assertThat(
            schreiber.aktualisieren(
                BOARD, NUMMER, "Neu", "Text", "https://git.example/1", ZEITPUNKT))
        .isTrue();

    ArgumentCaptor<Card> gespeichert = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(gespeichert.capture());
    assertThat(gespeichert.getValue().title()).isEqualTo("Neu");
    verify(grundlage).publishChanged(BOARD, ActivityType.UPDATED, KARTEN_ID);
  }

  @Test
  void aktualisieren_neuerText_speichert() {
    aktualisierenVorbereiten();

    assertThat(
            schreiber.aktualisieren(
                BOARD, NUMMER, "Titel", "Anders", "https://git.example/1", ZEITPUNKT))
        .isTrue();

    ArgumentCaptor<Card> gespeichert = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(gespeichert.capture());
    assertThat(gespeichert.getValue().description()).isEqualTo("Anders");
  }

  @Test
  void aktualisieren_neueAdresse_speichert() {
    aktualisierenVorbereiten();

    assertThat(
            schreiber.aktualisieren(
                BOARD, NUMMER, "Titel", "Text", "https://git.example/2", ZEITPUNKT))
        .isTrue();

    ArgumentCaptor<Card> gespeichert = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(gespeichert.capture());
    assertThat(gespeichert.getValue().externUrl()).isEqualTo("https://git.example/2");
  }

  @Test
  void aktualisieren_unbekannteKarte_lehntAb() {
    assertThatThrownBy(() -> schreiber.aktualisieren(BOARD, NUMMER, "Titel", null, null, ZEITPUNKT))
        .isInstanceOf(CardNotFoundException.class);
  }

  // --- wechseln ---

  @Test
  void wechseln_bucheSpaltenwechselMitDemFremdenZeitpunkt() {
    when(cards.findByProjectIdAndNumber(PROJEKT, NUMMER)).thenReturn(Optional.of(karte(BACKLOG)));
    when(cards.findById(KARTEN_ID)).thenReturn(Optional.of(karte(DONE)));

    assertThat(schreiber.wechseln(BOARD, NUMMER, "DONE", ZEITPUNKT, MENSCH)).isTrue();

    assertThat(FlussZeitpunkt.vorgabe()).contains(ZEITPUNKT);
    InOrder reihenfolge = inOrder(cards, transitions);
    reihenfolge.verify(cards).move(KARTEN_ID, DONE, Integer.MAX_VALUE);
    reihenfolge.verify(transitions).closeOpen(KARTEN_ID, ZEITPUNKT);
    reihenfolge.verify(transitions).open(KARTEN_ID, DONE, "Done", ZEITPUNKT);
    ArgumentCaptor<Card> gespeichert = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(gespeichert.capture());
    assertThat(gespeichert.getValue().status()).isEqualTo(CardStatus.DONE);
    assertThat(gespeichert.getValue().movedToDoneAt()).isEqualTo(ZEITPUNKT);
    verify(activity)
        .addFremd(
            KARTEN_ID,
            CardActivityType.MOVED,
            "Verschoben nach Done",
            ZEITPUNKT,
            CardActivityOrigin.SESSION,
            "jdoe",
            CardStatus.DONE);
    verify(grundlage).publishChanged(BOARD, ActivityType.MOVED, KARTEN_ID);
  }

  @Test
  void wechseln_karteSchonInDerSpalte_hinterlaesstKeineSpur() {
    when(cards.findByProjectIdAndNumber(PROJEKT, NUMMER)).thenReturn(Optional.of(karte(READY)));

    assertThat(schreiber.wechseln(BOARD, NUMMER, "READY", ZEITPUNKT, MENSCH)).isFalse();

    verify(cards, never()).move(anyLong(), anyLong(), anyInt());
    verify(cards, never()).save(any());
    verifyNoInteractions(transitions, activity, grundlage);
  }

  // --- alsGeloeschtMarkieren ---

  @Test
  void alsGeloeschtMarkieren_legtKarteInDenPapierkorb() {
    when(cards.findByProjectIdAndNumber(PROJEKT, NUMMER)).thenReturn(Optional.of(karte(BACKLOG)));

    schreiber.alsGeloeschtMarkieren(BOARD, NUMMER, ZEITPUNKT);

    assertThat(FlussZeitpunkt.vorgabe()).contains(ZEITPUNKT);
    verify(cards).softDelete(KARTEN_ID, ZEITPUNKT);
    verify(grundlage).publishChanged(BOARD, ActivityType.DELETED, KARTEN_ID);
  }

  @Test
  void alsGeloeschtMarkieren_unbekannteKarte_bleibtUnberuehrt() {
    schreiber.alsGeloeschtMarkieren(BOARD, NUMMER, ZEITPUNKT);

    assertThat(FlussZeitpunkt.vorgabe()).isEmpty();
    verify(cards, never()).softDelete(anyLong(), any());
    verifyNoInteractions(grundlage);
  }

  // --- labelSetzen ---

  private void labelsVorbereiten() {
    when(cards.findByProjectIdAndNumber(PROJEKT, NUMMER)).thenReturn(Optional.of(karte(BACKLOG)));
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                new Label(8L, BOARD, "anderes", "#000000", false),
                new Label(9L, BOARD, "bug", "#ff0000", false)));
  }

  @Test
  void labelSetzen_neuesLabel_bucheEreignisMitHerkunft() {
    labelsVorbereiten();
    when(cardLabels.addLabel(KARTEN_ID, 9L)).thenReturn(true);

    assertThat(schreiber.labelSetzen(BOARD, NUMMER, "bug", ZEITPUNKT, MENSCH)).isTrue();

    verify(labels).insertIfAbsent(BOARD, "bug", FremdKartenSchreiber.LABEL_FARBE);
    verify(cardLabels).addLabel(KARTEN_ID, 9L);
    verify(labelEvents).addFremd(KARTEN_ID, "bug", ZEITPUNKT, CardActivityOrigin.SESSION, "jdoe");
    verify(grundlage).publishChanged(BOARD, ActivityType.UPDATED, KARTEN_ID);
  }

  @Test
  void labelSetzen_labelSchonGesetzt_bucheNichts() {
    labelsVorbereiten();
    when(cardLabels.addLabel(KARTEN_ID, 9L)).thenReturn(false);

    assertThat(schreiber.labelSetzen(BOARD, NUMMER, "bug", ZEITPUNKT, MENSCH)).isFalse();

    verifyNoInteractions(labelEvents, grundlage);
  }

  @Test
  void labelSetzen_labelNachAnlegenNichtAmBoard_lehntAb() {
    labelsVorbereiten();

    assertThatThrownBy(() -> schreiber.labelSetzen(BOARD, NUMMER, "neu", ZEITPUNKT, MENSCH))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("neu");

    verify(cardLabels, never()).addLabel(anyLong(), anyLong());
  }

  // --- labelEntfernen ---

  @Test
  void labelEntfernen_getragenesLabel_nimmtEsAb() {
    labelsVorbereiten();
    when(cardLabels.removeLabel(KARTEN_ID, 9L)).thenReturn(true);

    assertThat(schreiber.labelEntfernen(BOARD, NUMMER, "bug")).isTrue();

    verify(cardLabels).removeLabel(KARTEN_ID, 9L);
    verify(grundlage).publishChanged(BOARD, ActivityType.UPDATED, KARTEN_ID);
  }

  @Test
  void labelEntfernen_nichtGetragenesLabel_meldetNichts() {
    labelsVorbereiten();
    when(cardLabels.removeLabel(KARTEN_ID, 9L)).thenReturn(false);

    assertThat(schreiber.labelEntfernen(BOARD, NUMMER, "bug")).isFalse();

    verifyNoInteractions(grundlage);
  }

  @Test
  void labelEntfernen_labelNichtAmBoard_meldetNichts() {
    labelsVorbereiten();

    assertThat(schreiber.labelEntfernen(BOARD, NUMMER, "fehlt")).isFalse();

    verify(cardLabels, never()).removeLabel(anyLong(), anyLong());
    verifyNoInteractions(grundlage);
  }

  // --- herkunftSetzen ---

  @Test
  void herkunftSetzen_bekannterVorfahr_setztIhn() {
    when(cards.findByProjectIdAndNumber(PROJEKT, NUMMER)).thenReturn(Optional.of(karte(BACKLOG)));
    when(cards.findByProjectIdAndNumber(PROJEKT, 7)).thenReturn(Optional.of(vorfahr()));

    assertThat(schreiber.herkunftSetzen(BOARD, NUMMER, 7)).isTrue();

    ArgumentCaptor<Card> gespeichert = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(gespeichert.capture());
    assertThat(gespeichert.getValue().derivedFromCardId()).isEqualTo(77L);
    verify(grundlage).publishChanged(BOARD, ActivityType.UPDATED, KARTEN_ID);
  }

  @Test
  void herkunftSetzen_unbekannterVorfahr_nimmtHerkunftWeg() {
    when(cards.findByProjectIdAndNumber(PROJEKT, NUMMER))
        .thenReturn(Optional.of(karte(BACKLOG, "Titel", null, null, 77L)));

    assertThat(schreiber.herkunftSetzen(BOARD, NUMMER, 8)).isTrue();

    ArgumentCaptor<Card> gespeichert = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(gespeichert.capture());
    assertThat(gespeichert.getValue().derivedFromCardId()).isNull();
  }

  @Test
  void herkunftSetzen_unveraendert_laesstKarteUnberuehrt() {
    when(cards.findByProjectIdAndNumber(PROJEKT, NUMMER)).thenReturn(Optional.of(karte(BACKLOG)));

    assertThat(schreiber.herkunftSetzen(BOARD, NUMMER, null)).isFalse();

    verify(cards, never()).save(any());
    verifyNoInteractions(grundlage);
  }

  private static Card vorfahr() {
    return new Card(
        77L,
        BOARD,
        BACKLOG,
        7,
        "Vorfahr",
        null,
        0,
        false,
        null,
        null,
        FRUEHER,
        FRUEHER,
        CardType.CARD,
        null,
        null,
        null,
        PROJEKT,
        null,
        null,
        null,
        null);
  }

  // --- FremderAkteur ---

  @Test
  void akteur_kitKonto_ohneRuecksichtAufGrossschreibung() {
    assertThat(FremderAkteur.von("KIT", "kit").kitKonto()).isTrue();
    assertThat(FremderAkteur.von("jdoe", "kit").kitKonto()).isFalse();
    assertThat(FremderAkteur.von(null, "kit").kitKonto()).isFalse();
    assertThat(KIT.herkunft()).isEqualTo(CardActivityOrigin.TOKEN);
    assertThat(MENSCH.herkunft()).isEqualTo(CardActivityOrigin.SESSION);
  }

  /** Keine Methode schreibt ohne fremdes Board — auch nicht über den Abgleich. */
  @Test
  void ohneFremdesBoard_schreibtKeinWeg() {
    fremd = false;

    assertThatThrownBy(() -> schreiber.labelEntfernen(BOARD, NUMMER, "bug"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> schreiber.alsGeloeschtMarkieren(BOARD, NUMMER, ZEITPUNKT))
        .isInstanceOf(IllegalStateException.class);

    verify(cards, never()).findByProjectIdAndNumber(anyLong(), anyInt());
    verify(labels, never()).findByBoardId(anyLong());
    verify(labels, never()).insertIfAbsent(anyLong(), anyString(), anyString());
  }
}
