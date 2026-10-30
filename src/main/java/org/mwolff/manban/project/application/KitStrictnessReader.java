package org.mwolff.manban.project.application;

import java.util.Map;

/**
 * Lesender Port für die zuletzt vom Kit gemeldete Strenge-Stufe der Projekte (Issue #2014). Der
 * Plattform-Leitstand zeigt sie am Kopf jeder Projektgruppe; das Nachtlauf-Modul fragt sie hier in
 * <b>einem</b> Zug für alle Projekte ab, statt einmal je Zeile. Gebaut nach dem Vorbild von {@link
 * DashboardParticipationReader}.
 *
 * <p><strong>Sicherheitshinweis:</strong> Dieser Port prüft <strong>keine</strong> Rechte — der
 * Plattform-Leitstand ist bereits auf Plattform-Admins beschränkt, das prüft der Aufrufer.
 *
 * <p>Der Aufruferkreis ist deshalb wie beim schreibenden {@link KitStrictnessWriter} maschinell
 * begrenzt, zusätzlich zugelassen ist {@code nightrun.application}.
 */
@FunctionalInterface
public interface KitStrictnessReader {

  /** Die gemeldete Stufe je Projekt-Id; ein Projekt ohne Meldung fehlt in der Abbildung. */
  Map<Long, String> reportedStrictness();
}
