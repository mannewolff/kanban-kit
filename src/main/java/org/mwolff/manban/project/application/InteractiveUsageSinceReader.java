package org.mwolff.manban.project.application;

import java.time.Instant;
import java.util.Optional;

/**
 * Lesender Port für den Erfassungsbeginn der interaktiven Sitzungen {@code
 * project.interactive_usage_since} (Issue #1013, Plan #1007 E18). Das Gegenstück zu {@link
 * InteractiveUsageSinceWriter}: Die Verbrauchs-Auswertung im {@code nightrun}-Modul gibt den
 * Zeitpunkt in ihren Kennzahlen mit, damit die Anzeige „nie erfasst" von „erfasst, dann verdrängt"
 * unterscheiden kann.
 *
 * <p><strong>Sicherheitshinweis:</strong> Dieser Port prüft <strong>keine</strong> Rechte. Der
 * Aufrufer <em>muss</em> vorher autorisiert haben — verbindlich ist {@code requireOwner}, wie in
 * {@code nightrun.application.NightRunUsageService#period} als erste Anweisung geprüft (Plan #933
 * E17).
 *
 * <p>Der Aufruferkreis ist deshalb wie beim Schreib-Port maschinell begrenzt: {@code
 * ArchitectureTest.INTERACTIVE_USAGE_SINCE_PORTS_HABEN_AUFRUFER_WHITELIST} lässt ausschließlich
 * {@code project.application} (Ports und Implementierung) sowie {@code nightrun.application}
 * (autorisierender Aufrufer) zu.
 */
@FunctionalInterface
public interface InteractiveUsageSinceReader {

  /**
   * Der Startzeitpunkt der ersten je gemeldeten interaktiven Sitzung des Projekts; leer, solange
   * keine gemeldet wurde — und leer für ein unbekanntes Projekt.
   */
  Optional<Instant> interactiveUsageSince(long projectId);
}
