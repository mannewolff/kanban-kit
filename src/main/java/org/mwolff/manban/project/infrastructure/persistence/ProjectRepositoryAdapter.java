package org.mwolff.manban.project.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.Project;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adapter des {@link ProjectRepository}-Ports auf Spring Data JPA. */
@Component
class ProjectRepositoryAdapter implements ProjectRepository {

  private final ProjectJpaRepository jpa;
  private final JdbcTemplate jdbc;

  ProjectRepositoryAdapter(ProjectJpaRepository jpa, JdbcTemplate jdbc) {
    this.jpa = jpa;
    this.jdbc = jdbc;
  }

  @Override
  public Project save(Project project) {
    return toDomain(jpa.save(toEntity(project)));
  }

  @Override
  public Optional<Project> findById(long id) {
    return jpa.findById(id).map(ProjectRepositoryAdapter::toDomain);
  }

  @Override
  public List<Project> findAll() {
    return jpa.findAll().stream().map(ProjectRepositoryAdapter::toDomain).toList();
  }

  @Override
  public void deleteById(long id) {
    jpa.deleteById(id);
  }

  @Override
  public void setNextCardNumber(long projectId, int value) {
    // next_card_number wird bewusst nicht auf die JPA-Entity gemappt (reine Nummerierungs-Belange
    // liest CardRepository.nextCardNumber direkt per SQL); daher der gezielte Direkt-Update.
    jdbc.update("UPDATE project SET next_card_number = ? WHERE id = ?", value, projectId);
  }

  /**
   * Das {@code IS NULL} in der Bedingung ist die ganze Zusage „falls noch leer" (Issue #1012):
   * Trifft das {@code UPDATE} keine Zeile, stand der Wert schon — oder das Projekt gibt es nicht.
   * Beides ist kein Fehler und wird nicht unterschieden; der Aufrufer meldet eine Sitzung, er setzt
   * keinen Wert.
   */
  @Override
  public void setInteractiveUsageSinceIfAbsent(long projectId, Instant startedAt) {
    jdbc.update(
        "UPDATE project SET interactive_usage_since = ?"
            + " WHERE id = ? AND interactive_usage_since IS NULL",
        OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC),
        projectId);
  }

  /**
   * Gezielter Direkt-Update wie bei den Nachbarspalten (Issue #1077): {@code
   * dashboard_participation} ist an der Entity {@code insertable = false, updatable = false}, also
   * erreicht {@code save} sie nicht — und soll sie auch nicht erreichen. Ein unbekanntes Projekt
   * trifft keine Zeile; das ist kein Fehler, sondern dasselbe Ergebnis wie ein Projekt, das
   * gleichzeitig gelöscht wurde.
   */
  @Override
  public void setDashboardParticipation(long projectId, boolean participating) {
    jdbc.update(
        "UPDATE project SET dashboard_participation = ? WHERE id = ?", participating, projectId);
  }

  /**
   * {@code interactive_usage_since} und {@code dashboard_participation} fehlen hier absichtlich:
   * Beide Spalten sind an der Entity {@code insertable = false, updatable = false} und gehören
   * jeweils einem engen Schreibweg. Über {@code save} können sie deshalb weder gesetzt noch
   * verloren werden.
   */
  private static ProjectEntity toEntity(Project p) {
    return new ProjectEntity(p.id(), p.name(), p.ownerUserId(), p.createdAt());
  }

  private static Project toDomain(ProjectEntity e) {
    return new Project(
        e.getId(),
        e.getName(),
        e.getOwnerUserId(),
        e.getCreatedAt(),
        e.getInteractiveUsageSince(),
        e.isDashboardParticipation());
  }
}
