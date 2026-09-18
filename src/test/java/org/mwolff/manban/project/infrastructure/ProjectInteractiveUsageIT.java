package org.mwolff.manban.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.Project;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Erfassungsbeginn der interaktiven Sitzungen am Projekt-Aggregat (Issue #1012, Plan #1007
 * E18).
 *
 * <p>Die Zusage „setze, falls noch leer" liegt in der {@code WHERE}-Bedingung des {@code UPDATE}
 * und nicht im Aufrufer: Zwei gleichzeitig eingehende Sitzungen lasen sonst beide einen leeren Wert
 * und schrieben beide. Nur die echte Datenbank kann das einlösen, deshalb steht der Fall hier.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectInteractiveUsageIT extends AbstractIntegrationTest {

  private static final Instant ERSTE = Instant.parse("2026-09-16T10:00:00Z");
  private static final Instant FRUEHER = Instant.parse("2026-09-15T10:00:00Z");
  private static final Instant SPAETER = Instant.parse("2026-09-17T10:00:00Z");

  @Autowired private ProjectRepository projects;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;

  @BeforeEach
  void seed() {
    Long userId =
        jdbc.queryForObject(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('erfassung@example.com', 'x', 'E') RETURNING id",
            Long.class);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id",
            Long.class);
    projectId = id == null ? 0L : id;
  }

  /** Vor der ersten Sitzung gibt es den Zeitpunkt nicht — „nie erfasst" ist {@code null}. */
  @Test
  void einFrischesProjektHatKeinenErfassungsbeginn() {
    assertThat(projects.findById(projectId).orElseThrow().interactiveUsageSince()).isNull();
  }

  @Test
  void dasSetzenTraegtDenZeitpunktEinUndDasLesenGibtIhnZurueck() {
    projects.setInteractiveUsageSinceIfAbsent(projectId, ERSTE);

    assertThat(projects.findById(projectId).orElseThrow().interactiveUsageSince()).isEqualTo(ERSTE);
  }

  /** Weder ein früherer noch ein späterer Zeitpunkt bewegt den Wert — er steht ein für alle Mal. */
  @Test
  void einZweitesSetzenLaesstDenVorhandenenWertStehen() {
    projects.setInteractiveUsageSinceIfAbsent(projectId, ERSTE);

    projects.setInteractiveUsageSinceIfAbsent(projectId, FRUEHER);
    assertThat(projects.findById(projectId).orElseThrow().interactiveUsageSince()).isEqualTo(ERSTE);

    projects.setInteractiveUsageSinceIfAbsent(projectId, SPAETER);
    assertThat(projects.findById(projectId).orElseThrow().interactiveUsageSince()).isEqualTo(ERSTE);
  }

  /** Ein unbekanntes Projekt ist ein No-Op — das {@code UPDATE} trifft dann keine Zeile. */
  @Test
  void einUnbekanntesProjektIstEinNoOp() {
    projects.setInteractiveUsageSinceIfAbsent(projectId + 999, ERSTE);

    assertThat(projects.findById(projectId).orElseThrow().interactiveUsageSince()).isNull();
  }

  /**
   * Das Umbenennen läuft über {@code save} und damit über den allgemeinen Schreibweg. Der
   * Erfassungsbeginn darf daran nicht hängen: Er wird ausschließlich über den engen Port gesetzt
   * und ist für JPA nicht schreibbar.
   */
  @Test
  void dasUmbenennenLaesstDenErfassungsbeginnStehen() {
    projects.setInteractiveUsageSinceIfAbsent(projectId, ERSTE);
    Project vorher = projects.findById(projectId).orElseThrow();

    projects.save(vorher.withName("Neuer Name"));

    Project nachher = projects.findById(projectId).orElseThrow();
    assertThat(nachher.name()).isEqualTo("Neuer Name");
    assertThat(nachher.interactiveUsageSince()).isEqualTo(ERSTE);
  }
}
