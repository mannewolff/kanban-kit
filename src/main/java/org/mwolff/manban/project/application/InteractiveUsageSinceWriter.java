package org.mwolff.manban.project.application;

import java.time.Instant;

/**
 * Schreibender Port für den Erfassungsbeginn der interaktiven Sitzungen {@code
 * project.interactive_usage_since} (Issue #1012, Plan #1007 E18). Damit setzt das {@code
 * nightrun}-Modul — dem die Auswertung der Sitzungen fachlich gehört — den Wert am
 * Projekt-Aggregat, ohne den Projekt-Persistenz-Port zu kennen. Gebaut nach dem Vorbild von {@link
 * NextCardNumberWriter} (Issue #463).
 *
 * <p><strong>Sicherheitshinweis:</strong> Dieser Port prüft <strong>keine</strong> Rechte. Der
 * Aufrufer <em>muss</em> vorher autorisiert haben — verbindlich ist {@code requireOwner}, wie in
 * {@code nightrun.application.NightRunService#ingest} als erste Anweisung geprüft (Plan #718, A6).
 * Wer diesen Port ohne vorgelagerte Prüfung aufruft, umgeht die Projekt-Rechtelogik vollständig.
 *
 * <p>Der Aufruferkreis ist deshalb nicht nur dokumentiert, sondern maschinell begrenzt: {@code
 * ArchitectureTest.INTERACTIVE_USAGE_SINCE_WRITER_HAT_AUFRUFER_WHITELIST} lässt ausschließlich
 * {@code project.application} (Port und Implementierung) sowie {@code nightrun.application}
 * (autorisierender Aufrufer) zu.
 */
@FunctionalInterface
public interface InteractiveUsageSinceWriter {

  /**
   * Setzt den Erfassungsbeginn des Projekts auf den Startzeitpunkt der Sitzung — <b>nur, wenn er
   * noch leer ist</b>. Steht er schon, bleibt er unverändert; ein unbekanntes Projekt ist ein
   * No-Op.
   *
   * <p>Der Aufrufer braucht nicht zu wissen, ob der Wert schon steht: Die Bedingung liegt im {@code
   * UPDATE} selbst, damit zwei gleichzeitig eingehende Sitzungen sie nicht beide passieren.
   */
  void setInteractiveUsageSinceIfAbsent(long projectId, Instant startedAt);
}
