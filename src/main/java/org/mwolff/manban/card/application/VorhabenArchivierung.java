package org.mwolff.manban.card.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.BoardQuelle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archiviert Vorhaben, die keine mitzählende Karte mehr haben, und holt archivierte zurück, sobald
 * wieder eine dazukommt (Plan #1504, Issue #1505).
 *
 * <p>Maßstab ist dieselbe Rechnung wie für {@code total} in {@code EpicService#listEpics}: {@link
 * EpicMembership} über {@link CardRepository#findByBoardId(long)}. Papierkorb-Karten fehlen dort,
 * archivierte zählen nicht — „Papierkorb zählt wie Archiv“ folgt ohne Sonderfall. Ein Vorhaben
 * verschwindet nie endgültig; es wechselt nur zwischen sichtbar und archiviert. Kein
 * Aktivitätseintrag, kein Board-Ereignis (E7).
 */
@Service
public class VorhabenArchivierung {

  private final CardRepository cards;
  private final BoardQuelle boardQuelle;

  public VorhabenArchivierung(CardRepository cards, BoardQuelle boardQuelle) {
    this.cards = cards;
    this.boardQuelle = boardQuelle;
  }

  /**
   * Gleicht alle Boards mit Vorhaben in beide Richtungen ab (E1): Ein sichtbares Vorhaben ohne
   * mitzählende Karte wird archiviert, ein archiviertes mit mitzählender Karte kehrt zurück. Auch
   * archivierte Boards werden geprüft (E5). Boards von Projekten mit fremdem Board bleiben
   * unberührt (Issue #2161, Plan #2152 A10): Ihren Stand bestimmt allein das fremde System.
   *
   * @return Anzahl der in diesem Lauf archivierten Vorhaben; Rückholungen zählen nicht mit
   */
  @Transactional
  public int gleicheAlleAb() {
    int archiviert = 0;
    for (long boardId : cards.findBoardIdsWithEpics()) {
      archiviert += gleicheBoardAb(boardId, true);
    }
    return archiviert;
  }

  /**
   * Holt die archivierten Vorhaben des Boards zurück, die wieder eine mitzählende Karte haben. Nur
   * diese Richtung (E2): Ein leeres Vorhaben bleibt bis zum nächsten Lauf stehen, statt als
   * Nebenwirkung einer fremden Nutzeraktion zu verschwinden.
   *
   * <p>Ohne eigene Transaktion — läuft in der des Aufrufers. Gelesen wird frisch über {@link
   * CardRepository#findByBoardId(long)}; verwertet werden nur Felder, die das Zurückholen aus dem
   * Papierkorb per JDBC nicht ändert (E12).
   */
  public void holeZurueck(long boardId) {
    gleicheBoardAb(boardId, false);
  }

  /**
   * Gleicht ein Board ab.
   *
   * @param archivieren ob leere Vorhaben archiviert werden; zurückgeholt wird immer
   * @return Anzahl der archivierten Vorhaben
   */
  private int gleicheBoardAb(long boardId, boolean archivieren) {
    List<Card> karten = cards.findByBoardId(boardId);
    // Alle Karten eines Boards gehören zu dessen Projekt; eine genügt für die Frage.
    if (!karten.isEmpty() && boardQuelle.istFremd(karten.getFirst().projectId())) {
      return 0;
    }
    Map<Long, Set<Card>> mitglieder = EpicMembership.compute(karten);
    int archiviert = 0;
    for (Card epic : karten) {
      if (epic.type() != CardType.EPIC) {
        continue;
      }
      boolean leer = mitglieder.getOrDefault(epic.requireId(), Set.of()).isEmpty();
      if (epic.archived() && !leer) {
        cards.save(epic.asRestored(epic.positionInColumn()));
      } else if (archivieren && !epic.archived() && leer) {
        cards.save(epic.asArchived());
        archiviert++;
      }
    }
    return archiviert;
  }
}
