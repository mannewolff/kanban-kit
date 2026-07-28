package org.mwolff.manban.project.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementierung des rechteprüfungsfreien Schreib-Ports {@link NextCardNumberWriter} (Issue #463).
 *
 * <p>Bewusst eine eigene, <em>package-private</em> Klasse statt einer Methode auf {@link
 * ProjectService}: Auf der allgemeinen Projekt-Fassade autorisiert jede andere Methode, eine
 * ungeprüfte Schreibmethode dort wäre die Ausnahme, die man beim Lesen übersieht. Hier trägt der
 * Typname die Rechtefreiheit — und weil die Klasse package-private ist, lässt sich von außen
 * ausschließlich der Port injizieren, dessen Aufruferkreis {@code ArchitectureTest} begrenzt.
 */
@Service
class ProjectNumberingService implements NextCardNumberWriter {

  private final ProjectRepository projects;

  ProjectNumberingService(ProjectRepository projects) {
    this.projects = projects;
  }

  @Override
  @Transactional
  public void setNextCardNumber(long projectId, int value) {
    projects.setNextCardNumber(projectId, value);
  }
}
