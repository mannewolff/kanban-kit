package org.mwolff.manban.card.application;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;

/**
 * Auflösung der Anforderungskarte eines Vorhabens: projektweite Kartennummer → Karten-ID, mit vier
 * harten Ablehnungen.
 *
 * <p>Gespeichert wird die ID und nicht die Nummer, weil {@code CardService.doTransfer} beim
 * Projektwechsel eine neue Nummer vergibt; eine gespeicherte Nummer zeigte danach auf eine fremde
 * Karte. Dieselbe Begründung wie bei {@link DerivedFrom}.
 *
 * <p><b>Warum überhaupt gespeichert:</b> Die Zugehörigkeit aller Karten zu einem Vorhaben wird
 * abgeleitet ({@link EpicMembership}), weil sie ableitbar ist. Welche der zugeordneten Karten die
 * <em>Anforderung</em> ist, ist es nicht — manuelles Zuordnen bleibt, ein Vorhaben kann also
 * mehrere Wurzeln haben (Plan #637, E1).
 *
 * <p><b>Eine unbekannte Nummer wird abgelehnt.</b> Der Ingest-Fall aus #566, der unbekannte Nummern
 * bewusst durchlässt, gilt hier nicht: Diese Nummer tippt ein Mensch in die Oberfläche, ein
 * unbekannter Wert ist dort ein Tippfehler.
 */
final class RequirementCard {

  private RequirementCard() {}

  /**
   * Löst {@code requirementNumber} im Projekt des Vorhabens auf und liefert die ID der
   * Anforderungskarte, oder {@code null}, wenn keine angegeben ist.
   *
   * <p>{@code null} löscht die Zuordnung. Ein Vorhaben ohne Anforderung ist ein gültiger Zustand:
   * Es darf auch ohne Herkunftskette zum Gruppieren dienen (PO-Entscheidung in #636).
   *
   * @param epic die Karte, an der die Anforderung gesetzt wird — muss ein Vorhaben sein
   */
  static @Nullable Long resolve(
      CardRepository cards, Card epic, @Nullable Integer requirementNumber) {
    // Die Typprüfung steht vor der null-Prüfung: Eine gewöhnliche Karte trägt das Feld nie, also
    // ist auch ein Löschen an ihr kein sinnvoller Aufruf, sondern ein Irrtum des Aufrufers.
    if (epic.type() != CardType.EPIC) {
      throw new InvalidRequirementCardException(
          "Eine Anforderung laesst sich nur an einem Vorhaben setzen: " + epic.requireNumber());
    }
    if (requirementNumber == null) {
      return null;
    }
    Card anforderung =
        cards
            .findByProjectIdAndNumber(epic.projectId(), requirementNumber)
            .orElseThrow(
                () ->
                    new InvalidRequirementCardException(
                        "Unbekannte Kartennummer als Anforderung: " + requirementNumber));
    long anforderungId = anforderung.requireId();
    if (anforderungId == epic.requireId()) {
      throw new InvalidRequirementCardException(
          "Ein Vorhaben kann nicht seine eigene Anforderung sein");
    }
    // Board-gebunden wie die Zugehörigkeit selbst (Plan #631, E4): Ein board-fremder Verweis wäre
    // im Baum des Vorhabens nicht auflösbar — dieselbe Grenze zieht `requireEpicInBoard`.
    if (!Objects.equals(anforderung.boardId(), epic.boardId())) {
      throw new InvalidRequirementCardException(
          "Die Anforderung liegt nicht auf dem Board des Vorhabens: " + requirementNumber);
    }
    return anforderungId;
  }
}
