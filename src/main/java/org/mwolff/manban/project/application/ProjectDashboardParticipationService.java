package org.mwolff.manban.project.application;

import java.util.List;
import org.mwolff.manban.project.domain.Project;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementierung des rechteprüfungsfreien Ports {@link DashboardParticipationReader} (Issue
 * #1076).
 *
 * <p>Bewusst eine eigene, <em>package-private</em> Klasse statt einer Methode auf {@link
 * ProjectService} — dieselbe Begründung wie bei {@link ProjectInteractiveUsageService}: Auf der
 * allgemeinen Projekt-Fassade autorisiert jede andere Methode, eine ungeprüfte Lesemethode dort
 * wäre die Ausnahme, die man übersieht. Hier trägt der Typname die Rechtefreiheit — und weil die
 * Klasse package-private ist, lässt sich von außen ausschließlich der Port injizieren, dessen
 * Aufruferkreis {@code ArchitectureTest} begrenzt.
 */
@Service
class ProjectDashboardParticipationService implements DashboardParticipationReader {

  private final ProjectRepository projects;

  ProjectDashboardParticipationService(ProjectRepository projects) {
    this.projects = projects;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ParticipatingProject> participatingProjects() {
    return projects.findAll().stream()
        .filter(Project::dashboardParticipation)
        .map(p -> new ParticipatingProject(p.requireId(), p.name()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isParticipating(long projectId) {
    return projects.findById(projectId).map(Project::dashboardParticipation).orElse(false);
  }
}
