package org.mwolff.manban.card.application;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.Card;

/**
 * Auflösung der Herkunft: projektweite Kartennummer → Karten-ID, mit drei harten Ablehnungen.
 *
 * <p>Gespeichert wird die ID und nicht die Nummer, weil {@code CardService.doTransfer} beim
 * Projektwechsel eine neue Nummer vergibt; eine gespeicherte Nummer zeigte danach im Projekt des
 * Kindes auf eine fremde Karte.
 *
 * <p><b>Eine unbekannte Nummer wird abgelehnt</b> — anders als bei {@code
 * CardService#replaceDependenciesFromIngest}, das die Existenz bewusst nicht prüft (#566). Dort
 * kommt die Zielkarte beim Tracker-Import oft später; die Herkunft dagegen entsteht im laufenden
 * Prozess, wo der Vorfahr nachweislich schon existiert. Ein unbekannter Wert ist dort ein
 * Tippfehler — und ohne Existenz gäbe es ohnehin keine ID aufzulösen.
 *
 * <p><b>Selbstverweis und Zyklus sind über die API nicht erreichbar</b>, und das ist kein Grund,
 * sie wegzulassen: Die Herkunft wird nur beim Anlegen gesetzt, und ein Aufrufer kennt die Nummer
 * der neuen Karte vorher nicht. Beide Prüfungen schützen gegen Bestandskorruption — per SQL, aus
 * einer Migration oder aus einem späteren Änderungspfad.
 */
final class DerivedFrom {

  /**
   * Grösste zulässige Kettenlänge. Wird sie erreicht, wird abgelehnt: Eine Kette dieser Tiefe ist
   * entweder ein Bestands-Zyklus ohne Beteiligung der geprüften Karte oder pathologisch. Sie
   * stillschweigend anzunehmen wäre genau die Verfälschung, die die Speicherform als ID verhindern
   * soll.
   */
  static final int MAX_DEPTH = 100;

  private DerivedFrom() {}

  /**
   * Löst {@code derivedFrom} im Projekt auf und liefert die ID des Vorfahren, oder {@code null},
   * wenn keine Herkunft angegeben ist.
   *
   * @param selfCardId ID der zu prüfenden Karte, oder {@code null} beim Anlegen (dort existiert sie
   *     noch nicht — Selbstverweis und Zyklus über sie sind dann konstruktiv ausgeschlossen)
   */
  static @Nullable Long resolve(
      CardRepository cards,
      long projectId,
      @Nullable Integer derivedFrom,
      @Nullable Long selfCardId) {
    if (derivedFrom == null) {
      return null;
    }
    Card vorfahr =
        cards
            .findByProjectIdAndNumber(projectId, derivedFrom)
            .orElseThrow(
                () ->
                    new InvalidDependencyException(
                        "Unbekannte Kartennummer als Herkunft: " + derivedFrom));
    long vorfahrId = vorfahr.requireId();
    if (selfCardId != null && selfCardId == vorfahrId) {
      throw new InvalidDependencyException("Eine Karte kann nicht von sich selbst abstammen");
    }
    requireNoCycle(cards, vorfahr, selfCardId);
    return vorfahrId;
  }

  private static void requireNoCycle(
      CardRepository cards, Card vorfahr, @Nullable Long selfCardId) {
    Long cursor = vorfahr.derivedFromCardId();
    int tiefe = 0;
    while (cursor != null) {
      tiefe++;
      if (tiefe >= MAX_DEPTH) {
        throw new InvalidDependencyException(
            "Herkunftskette erreicht " + MAX_DEPTH + " Glieder — vermutlich zyklisch");
      }
      if (selfCardId != null && cursor.longValue() == selfCardId.longValue()) {
        throw new InvalidDependencyException("Herkunft wuerde einen Zyklus bilden");
      }
      Optional<Card> naechster = cards.findById(cursor);
      if (naechster.isEmpty()) {
        return;
      }
      cursor = naechster.get().derivedFromCardId();
    }
  }
}
