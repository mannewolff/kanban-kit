package org.mwolff.manban.project.application;

import java.util.List;

/**
 * Lesender Port für die Teilnahme eines Projekts am Plattform-Leitstand ({@code
 * project.dashboard_participation}, Issue #1076, Plan #1072 E5). Der Plattform-Leitstand zeigt
 * Störungen nur aus teilnehmenden Projekten; das Nachtlauf-Modul fragt hier ab, welche das sind.
 *
 * <p><strong>Sicherheitshinweis:</strong> Dieser Port prüft <strong>keine</strong> Rechte — der
 * Plattform-Leitstand ist bereits auf Plattform-Admins beschränkt, das prüft der Aufrufer.
 *
 * <p>Der Aufruferkreis ist deshalb wie bei {@link InteractiveUsageSinceReader} maschinell begrenzt:
 * {@code ArchitectureTest.DASHBOARD_PARTICIPATION_READER_HAT_AUFRUFER_WHITELIST} lässt
 * ausschließlich {@code project.application} (Ports und Implementierung) sowie {@code
 * nightrun.application} (autorisierender Aufrufer) zu.
 */
public interface DashboardParticipationReader {

  /** Id und Name aller Projekte, die aktuell am Plattform-Leitstand teilnehmen. */
  List<ParticipatingProject> participatingProjects();

  /** Ob das Projekt am Plattform-Leitstand teilnimmt; {@code false} für ein unbekanntes Projekt. */
  boolean isParticipating(long projectId);

  /** Projekt-Kurzinfo für die Anzeige im Plattform-Leitstand. */
  record ParticipatingProject(long id, String name) {}
}
