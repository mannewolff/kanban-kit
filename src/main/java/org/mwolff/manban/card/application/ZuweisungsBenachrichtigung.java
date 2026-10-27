package org.mwolff.manban.card.application;

import java.util.List;

/**
 * Ausgehender Port: Personen, die für eine Karte neu zuständig wurden, erfahren davon (Issue
 * #1916).
 *
 * <p>Gerufen von {@link KartenZuordnung#ersetzeZustaendige}, der einen Stelle, durch die jeder
 * Schreibweg geht — Session, Token und Anlegen. Die Implementierung merkt die Nachricht in
 * derselben Transaktion vor: Scheitert die Zuweisung, geht nichts hinaus.
 */
@FunctionalInterface
public interface ZuweisungsBenachrichtigung {

  /**
   * Meldet die neu Zuständigen einer Karte.
   *
   * @param cardId die Karte
   * @param neueZustaendige gegenüber der bisherigen Zuordnung hinzugekommen, ohne die handelnde
   *     Person; nie leer
   * @param handelnderUserId wer zugewiesen hat — bei einem Token sein Inhaber
   */
  void zugewiesen(long cardId, List<Long> neueZustaendige, long handelnderUserId);
}
