package org.mwolff.manban.project.application;

import java.math.BigDecimal;
import java.util.Optional;
import org.mwolff.manban.project.domain.Project;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementierung des rechteprüfungsfreien Ports {@link MonthlyBudgetReader} (Issue #1964).
 *
 * <p>Bewusst eine eigene, <em>package-private</em> Klasse statt einer Methode auf {@link
 * ProjectService} — dieselbe Begründung wie bei {@link ProjectDashboardParticipationService}: Auf
 * der allgemeinen Projekt-Fassade autorisiert jede andere Methode, eine ungeprüfte Lesemethode dort
 * wäre die Ausnahme, die man übersieht. Von außen lässt sich ausschließlich der Port injizieren,
 * dessen Aufruferkreis {@code PortWhitelistArchitectureTest} begrenzt.
 */
@Service
class ProjectMonthlyBudgetService implements MonthlyBudgetReader {

  private final ProjectRepository projects;

  ProjectMonthlyBudgetService(ProjectRepository projects) {
    this.projects = projects;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BigDecimal> monthlyBudget(long projectId) {
    return projects.findById(projectId).map(Project::monthlyBudgetUsd);
  }
}
