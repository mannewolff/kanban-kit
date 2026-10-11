package org.mwolff.manban.card.application;

import java.util.List;
import org.mwolff.manban.board.application.BoardPurgedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Übersetzt das endgültige Löschen eines Boards in das endgültige Löschen seiner Karten (Issue
 * #503): Das card-Modul kennt die Karten des Boards, das board-Modul nicht (die Kante {@code card →
 * board} existiert, die Umkehrung wäre ein Zyklus).
 *
 * <p>Bewusst ein synchroner {@link EventListener} (wie {@code DefaultBoardCreator}): Er läuft im
 * Transaktions-Scope von {@code BoardService.purgeBoard} und <strong>vor</strong> dessen Delete —
 * nur so sehen nachgelagerte Listener (Anhänge) die Metadaten noch, bevor die Cascade sie entfernt.
 * Erfasst werden alle Karten des Boards einschließlich archivierter und Papierkorb-Karten, exakt
 * der Umfang der Datenbank-Cascade.
 */
@Component
class BoardPurgeCascade {

  private final CardRepository cards;
  private final CardBestandRepository bestand;
  private final ApplicationEventPublisher events;

  BoardPurgeCascade(
      CardRepository cards, CardBestandRepository bestand, ApplicationEventPublisher events) {
    this.cards = cards;
    this.bestand = bestand;
    this.events = events;
  }

  @EventListener
  void onBoardPurged(BoardPurgedEvent event) {
    List<Long> cardIds = cards.findAllIdsByBoardId(event.boardId());
    if (!cardIds.isEmpty()) {
      // Plan #1574, E18: Die DB-Cascade board → card setzt die Herkunft der Kinder auf anderen
      // Boards auf NULL — vorher die Nummer an ihnen bewahren.
      bestand.bewahreHerkunftsnummern(cardIds);
      events.publishEvent(new CardsPurgedEvent(cardIds));
    }
  }
}
