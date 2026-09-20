package org.mwolff.manban.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.project.application.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Teilnahme am Plattform-Leitstand am Projekt-Aggregat (Issue #1076, Plan #1072 E5).
 *
 * <p>Lesen und Schalten (Issue #1077). Die Spalte ist wie {@code interactive_usage_since} an der
 * Entity {@code insertable = false, updatable = false} — nur die echte Datenbank belegt, dass der
 * allgemeine Schreibweg ({@code save}) sie weder setzt noch überschreibt und dass der gezielte Weg
 * ({@code setDashboardParticipation}) sie trotzdem erreicht.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectDashboardParticipationIT extends AbstractIntegrationTest {

  @Autowired private ProjectRepository projects;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;

  @BeforeEach
  void seed() {
    Long userId =
        jdbc.queryForObject(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('leitstand@example.com', 'x', 'L') RETURNING id",
            Long.class);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id",
            Long.class);
    projectId = id == null ? 0L : id;
  }

  /** AK 17: Bestand und neue Projekte sind nicht angehakt. */
  @Test
  void einFrischesProjektNimmtNichtAmPlattformLeitstandTeil() {
    assertThat(projects.findById(projectId).orElseThrow().dashboardParticipation()).isFalse();
  }

  @Test
  void dasUmbenennenLaesstDieTeilnahmeStehen() {
    var vorher = projects.findById(projectId).orElseThrow();

    projects.save(vorher.withName("Neuer Name"));

    var nachher = projects.findById(projectId).orElseThrow();
    assertThat(nachher.name()).isEqualTo("Neuer Name");
    assertThat(nachher.dashboardParticipation()).isFalse();
  }

  @Test
  void dasSchaltenSetztDieSpalteUndLaesstDenNamenStehen() {
    projects.setDashboardParticipation(projectId, true);

    var nachher = projects.findById(projectId).orElseThrow();
    assertThat(nachher.dashboardParticipation()).isTrue();
    assertThat(nachher.name()).isEqualTo("P");
  }

  @Test
  void dasSchaltenNimmtDieTeilnahmeAuchWiederZurueck() {
    projects.setDashboardParticipation(projectId, true);

    projects.setDashboardParticipation(projectId, false);

    assertThat(projects.findById(projectId).orElseThrow().dashboardParticipation()).isFalse();
  }

  /**
   * Der eigentliche Beleg für Plan #1072 E25: Die Spalte steht an der Entity auf {@code insertable
   * = false, updatable = false}, also darf der allgemeine Schreibweg sie nicht anfassen. Mit {@code
   * false} wäre der Test wertlos — ein Überschreiben mit dem Default sähe aus wie ein Bewahren.
   * Deshalb erst anhaken, dann umbenennen.
   */
  @Test
  void dasUmbenennenUeberschreibtEineGesetzteTeilnahmeNicht() {
    projects.setDashboardParticipation(projectId, true);
    var angehakt = projects.findById(projectId).orElseThrow();

    projects.save(angehakt.withName("Umbenannt"));

    var nachher = projects.findById(projectId).orElseThrow();
    assertThat(nachher.name()).isEqualTo("Umbenannt");
    assertThat(nachher.dashboardParticipation()).isTrue();
  }

  @Test
  void dasSchaltenEinesUnbekanntenProjektsIstEinNoOp() {
    projects.setDashboardParticipation(projectId + 9999, true);

    assertThat(projects.findById(projectId).orElseThrow().dashboardParticipation()).isFalse();
  }
}
