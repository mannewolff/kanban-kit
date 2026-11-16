package org.mwolff.manban.fremdboard.application;

/**
 * Port für den hinterlegten Lesezugang eines fremden Boards (Issue #2166, Plan #2152 A13),
 * umgesetzt vom Tresor in {@code fremdboard.infrastructure}. Den Klartext gibt er nie zurück: Die
 * Anwendung verschlüsselt ihn zum Speichern und fragt nur, ob ein gespeicherter Wert noch lesbar
 * ist.
 */
public interface ZugangsSchutz {

  /** Wahr, wenn ein Schlüssel gesetzt ist; sonst ist das Anbinden gesperrt. */
  boolean verfuegbar();

  /**
   * Verschlüsselt den Zugang zum Speichern.
   *
   * @throws IllegalStateException wenn kein Schlüssel gesetzt ist
   */
  String verschluesseln(String klartext);

  /**
   * Ob sich ein gespeicherter Zugang mit dem gesetzten Schlüssel entschlüsseln lässt — nach einem
   * Schlüsselwechsel oder ohne Schlüssel nicht.
   */
  boolean lesbar(String gespeichert);
}
