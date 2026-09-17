package org.mwolff.manban.project.application;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementierung des rechteprüfungsfreien Schreib-Ports {@link InteractiveUsageSinceWriter} (Issue
 * #1012).
 *
 * <p>Bewusst eine eigene, <em>package-private</em> Klasse statt einer Methode auf {@link
 * ProjectService} — dieselbe Begründung wie bei {@link ProjectNumberingService}: Auf der
 * allgemeinen Projekt-Fassade autorisiert jede andere Methode, eine ungeprüfte Schreibmethode dort
 * wäre die Ausnahme, die man beim Lesen übersieht. Hier trägt der Typname die Rechtefreiheit — und
 * weil die Klasse package-private ist, lässt sich von außen ausschließlich der Port injizieren,
 * dessen Aufruferkreis {@code ArchitectureTest} begrenzt.
 */
@Service
class ProjectInteractiveUsageService implements InteractiveUsageSinceWriter {

  private final ProjectRepository projects;

  ProjectInteractiveUsageService(ProjectRepository projects) {
    this.projects = projects;
  }

  @Override
  @Transactional
  public void setInteractiveUsageSinceIfAbsent(long projectId, Instant startedAt) {
    projects.setInteractiveUsageSinceIfAbsent(projectId, startedAt);
  }
}
