package org.mwolff.manban.project.infrastructure.persistence;

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

  private static ProjectEntity toEntity(Project p) {
    return new ProjectEntity(p.id(), p.name(), p.ownerUserId(), p.createdAt());
  }

  private static Project toDomain(ProjectEntity e) {
    return new Project(e.getId(), e.getName(), e.getOwnerUserId(), e.getCreatedAt());
  }
}
