package org.mwolff.manban.auth.application;

import org.mwolff.manban.auth.domain.AdminVorschau;

/**
 * Ergebnis eines Starts der Vorschau „Ansicht als XY“ (Issue #1917, Plan #1914 E2).
 *
 * @param vorschau die gespeicherte Vorschau
 * @param geheimnis Klartext des Geheimnisses für das Cookie; nur hier und nur einmal vorhanden,
 *     gespeichert ist allein sein Hash
 */
public record VorschauStart(AdminVorschau vorschau, String geheimnis) {

  /** Ohne Geheimnis, damit es in keinem Protokoll landet. */
  @Override
  public String toString() {
    return "VorschauStart[vorschau=" + vorschau + "]";
  }
}
