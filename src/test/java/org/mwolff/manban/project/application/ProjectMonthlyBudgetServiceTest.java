package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.domain.Project;

/**
 * Verhaltenstest des rechteprüfungsfreien Ports für das Monatsbudget (Issue #1964, Plan #1962).
 * Liegt im selben Paket wie die package-private Implementierung — von außen ist nur der Port {@link
 * MonthlyBudgetReader} injizierbar.
 */
class ProjectMonthlyBudgetServiceTest {

  private static final Instant ANGELEGT = Instant.parse("2026-10-01T10:00:00Z");

  @Test
  void monthlyBudget_liefertDenBetragDesProjekts() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(9L)).thenReturn(Optional.of(projekt(new BigDecimal("100.00"))));
    MonthlyBudgetReader service = new ProjectMonthlyBudgetService(projects);

    assertThat(service.monthlyBudget(9L))
        .hasValueSatisfying(b -> assertThat(b).isEqualByComparingTo("100.00"));
  }

  @Test
  void monthlyBudget_istLeerOhneBudget() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(9L)).thenReturn(Optional.of(projekt(null)));
    MonthlyBudgetReader service = new ProjectMonthlyBudgetService(projects);

    assertThat(service.monthlyBudget(9L)).isEmpty();
  }

  @Test
  void monthlyBudget_istLeerFuerEinUnbekanntesProjekt() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(9L)).thenReturn(Optional.empty());
    MonthlyBudgetReader service = new ProjectMonthlyBudgetService(projects);

    assertThat(service.monthlyBudget(9L)).isEmpty();
  }

  private static Project projekt(@Nullable BigDecimal budget) {
    return new Project(9L, "P", 1L, ANGELEGT, null, false, budget);
  }
}
