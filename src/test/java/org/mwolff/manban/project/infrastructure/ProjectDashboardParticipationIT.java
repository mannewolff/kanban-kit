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
 * <p>Nur der lesende Weg: Das Schalten der Teilnahme selbst folgt in einem späteren Paket. Die
 * Spalte ist wie {@code interactive_usage_since} an der Entity {@code insertable = false, updatable
 * = false} — nur die echte Datenbank belegt, dass der allgemeine Schreibweg ({@code save}) sie
 * weder setzt noch überschreibt.
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
}
