package org.mwolff.manban.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.project.application.MonthlyBudgetReader;
import org.mwolff.manban.project.application.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Das Monatsbudget am Projekt-Aggregat (Issue #1964, Plan #1962).
 *
 * <p>Die Spalte {@code monthly_budget_usd} ist wie {@code dashboard_participation} an der Entity
 * {@code insertable = false, updatable = false} — nur die echte Datenbank belegt, dass der
 * allgemeine Schreibweg ({@code save}) sie nicht überschreibt, dass der gezielte Weg ({@code
 * setMonthlyBudget}) sie trotzdem erreicht und dass die Prüfbedingung {@code > 0} greift.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectMonthlyBudgetIT extends AbstractIntegrationTest {

  @Autowired private ProjectRepository projects;
  @Autowired private MonthlyBudgetReader budgets;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;

  @BeforeEach
  void seed() {
    Long userId =
        jdbc.queryForObject(
            "INSERT INTO app_user (email, display_name)"
                + " VALUES ('budget@example.com', 'B') RETURNING id",
            Long.class);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id",
            Long.class);
    projectId = id == null ? 0L : id;
  }

  @Test
  void einFrischesProjektHatKeinBudget() {
    assertThat(budgets.monthlyBudget(projectId)).isEmpty();
  }

  /**
   * Der Beleg für die Plan-Vorgabe: Erst festlegen, dann umbenennen — ein Überschreiben mit dem
   * geladenen oder einem leeren Wert fiele hier auf.
   */
  @Test
  void dasUmbenennenLaesstDasBudgetStehen() {
    projects.setMonthlyBudget(projectId, new BigDecimal("100.00"));
    var gesetzt = projects.findById(projectId).orElseThrow();

    projects.save(gesetzt.withName("Umbenannt"));

    assertThat(projects.findById(projectId).orElseThrow().name()).isEqualTo("Umbenannt");
    assertThat(budgets.monthlyBudget(projectId))
        .hasValueSatisfying(b -> assertThat(b).isEqualByComparingTo("100.00"));
  }

  @Test
  void nullEntferntDasBudget() {
    projects.setMonthlyBudget(projectId, new BigDecimal("100.00"));

    projects.setMonthlyBudget(projectId, null);

    assertThat(budgets.monthlyBudget(projectId)).isEmpty();
  }

  /** E3: Die Spaltenbreite trägt die Obergrenze 9 999 999 999,99 USD. */
  @Test
  void dieObergrenzePasstInDieSpalte() {
    projects.setMonthlyBudget(projectId, new BigDecimal("9999999999.99"));

    assertThat(budgets.monthlyBudget(projectId))
        .hasValueSatisfying(b -> assertThat(b).isEqualByComparingTo("9999999999.99"));
  }

  @Test
  void dieDatenbankWeistEinBudgetVonNullAb() {
    assertThatThrownBy(() -> projects.setMonthlyBudget(projectId, BigDecimal.ZERO))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
