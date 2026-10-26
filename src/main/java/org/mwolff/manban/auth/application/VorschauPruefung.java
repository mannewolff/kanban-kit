package org.mwolff.manban.auth.application;

import org.mwolff.manban.auth.domain.AdminVorschau;
import org.mwolff.manban.auth.domain.VorschauEndgrund;

/**
 * Ergebnis der Prüfung einer Vorschau „Ansicht als XY“ je Anfrage (Issue #1917, Plan #1914 E3, E9).
 */
public sealed interface VorschauPruefung
    permits VorschauPruefung.Gueltig, VorschauPruefung.Beendet, VorschauPruefung.Unbekannt {

  /**
   * Die Vorschau läuft und gehört zu dieser Anmeldung.
   *
   * @param vorschau die laufende Vorschau
   */
  record Gueltig(AdminVorschau vorschau) implements VorschauPruefung {}

  /**
   * Die Vorschau ist beendet — schon vorher oder durch diese Prüfung.
   *
   * @param endgrund warum sie endete
   */
  record Beendet(VorschauEndgrund endgrund) implements VorschauPruefung {}

  /**
   * Kennung unbekannt, Geheimnis falsch oder die Vorschau gehört nicht zu diesem Admin. Nichts wird
   * geschrieben: Wer das Geheimnis nicht kennt, kann eine fremde Vorschau nicht beenden.
   */
  record Unbekannt() implements VorschauPruefung {}
}
