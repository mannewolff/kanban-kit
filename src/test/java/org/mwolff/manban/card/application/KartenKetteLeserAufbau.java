package org.mwolff.manban.card.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardBestandRepository.KarteImBestand;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.LabelEreignisMitPerson;

/**
 * Baut den Prüfling {@link KartenKetteLeser} für die Unit-Tests (Issue #2114) auf einen Bestand im
 * Speicher: Die Port-Mocks antworten aus {@link #bestandNachId} und halten fest, wonach gefragt
 * wurde. Je Test ein neuer Aufbau.
 */
final class KartenKetteLeserAufbau {

  static final Instant JETZT = Instant.parse("2026-11-10T12:00:00Z");
  static final long PROJEKT = 1L;
  static final long FREMD = 2L;
  static final long BOARD = 10L;
  static final long SPALTE_READY = 20L;
  static final long SPALTE_EIGEN = 21L;

  final Map<Long, KarteImBestand> bestandNachId = new HashMap<>();
  final List<Card> ideenKandidaten = new ArrayList<>();
  final List<Collection<Integer>> gefragteIdeenNummern = new ArrayList<>();
  final List<Collection<Long>> gefragteAktivitaeten = new ArrayList<>();
  final List<Collection<Long>> gefragteLabelEreignisse = new ArrayList<>();
  final List<CardActivity> aktivitaeten = new ArrayList<>();
  final List<LabelEreignisMitPerson> labelEreignisse = new ArrayList<>();

  final CardBestandRepository bestand = mock(CardBestandRepository.class);
  final CardActivityRepository activity = mock(CardActivityRepository.class);
  final CardLabelEventRepository labelEvents = mock(CardLabelEventRepository.class);
  final BoardService boards = mock(BoardService.class);
  final KartenKetteLeser leser = new KartenKetteLeser(bestand, activity, labelEvents, boards);

  KartenKetteLeserAufbau() {
    when(bestand.findImBestand(any()))
        .thenAnswer(
            aufruf -> {
              Collection<Long> ids = aufruf.getArgument(0);
              return ids.stream().map(bestandNachId::get).filter(Objects::nonNull).toList();
            });
    when(bestand.findImBestandNachNummern(anyLong(), any()))
        .thenAnswer(
            aufruf -> {
              long projekt = aufruf.getArgument(0);
              Collection<Integer> nummern = aufruf.getArgument(1);
              return bestandNachId.values().stream()
                  .filter(k -> k.karte().projectId() == projekt)
                  .filter(k -> nummern.contains(k.karte().number()))
                  .toList();
            });
    when(bestand.findKinderImBestand(any()))
        .thenAnswer(
            aufruf -> {
              Collection<Long> ids = aufruf.getArgument(0);
              return bestandNachId.values().stream()
                  .filter(k -> ids.contains(k.karte().derivedFromCardId()))
                  .toList();
            });
    when(bestand.findMitErgebnisNummer(anyLong(), any()))
        .thenAnswer(
            aufruf -> {
              gefragteIdeenNummern.add(aufruf.getArgument(1));
              return ideenKandidaten;
            });
    when(activity.findByCardIds(any()))
        .thenAnswer(
            aufruf -> {
              gefragteAktivitaeten.add(aufruf.getArgument(0));
              return aktivitaeten;
            });
    when(labelEvents.findMitPerson(any()))
        .thenAnswer(
            aufruf -> {
              gefragteLabelEreignisse.add(aufruf.getArgument(0));
              return labelEreignisse;
            });
    when(boards.listColumns(BOARD))
        .thenReturn(
            List.of(
                new ColumnView(SPALTE_READY, "Ready", 0, null),
                new ColumnView(SPALTE_EIGEN, "Anstehend", 1, null)));
  }

  long lege(Card karte) {
    return lege(karte, false, null);
  }

  long lege(Card karte, boolean papierkorb, @Nullable Integer verloren) {
    long id = 100L + bestandNachId.size();
    Card mitId = karte.id() == null ? mitId(karte, id) : karte;
    bestandNachId.put(mitId.requireId(), new KarteImBestand(mitId, papierkorb, verloren));
    return mitId.requireId();
  }

  static Card neu(
      int nummer, String titel, @Nullable String beschreibung, @Nullable Long herkunft) {
    return aufbauen(null, SPALTE_READY, nummer, titel, beschreibung, PROJEKT, herkunft);
  }

  /** Dieselbe Karte mit ID; alle übrigen Angaben bleiben. */
  static Card mitId(Card c, long id) {
    return new Card(
        id,
        c.boardId(),
        c.columnId(),
        c.number(),
        c.title(),
        c.description(),
        c.positionInColumn(),
        c.archived(),
        c.movedToDoneAt(),
        c.createdBy(),
        c.createdAt(),
        c.updatedAt(),
        c.type(),
        c.parentId(),
        c.shortcode(),
        c.dueDate(),
        c.projectId(),
        c.externalKey(),
        c.derivedFromCardId(),
        c.requirementCardId(),
        c.status(),
        c.wegBelegGeaendertAm());
  }

  static Card inSpalte(Card c, long spalte) {
    return aufbauen(
        c.id(),
        spalte,
        c.number(),
        c.title(),
        c.description(),
        c.projectId(),
        c.derivedFromCardId());
  }

  static Card imProjekt(Card c, long projekt) {
    return aufbauen(
        c.id(),
        c.columnId(),
        c.number(),
        c.title(),
        c.description(),
        projekt,
        c.derivedFromCardId());
  }

  static Card aufbauen(
      @Nullable Long id,
      long spalte,
      int nummer,
      String titel,
      @Nullable String beschreibung,
      long projekt,
      @Nullable Long herkunft) {
    return new Card(
        id,
        BOARD,
        spalte,
        nummer,
        titel,
        beschreibung,
        0,
        false,
        null,
        null,
        JETZT,
        JETZT,
        CardType.CARD,
        null,
        null,
        null,
        projekt,
        null,
        herkunft,
        null,
        null);
  }
}
