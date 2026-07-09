package org.mwolff.manban.project.infrastructure.persistence;

import java.util.Optional;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.Project;
import org.springframework.stereotype.Component;

/** Adapter des {@link ProjectRepository}-Ports auf Spring Data JPA. */
@Component
class ProjectRepositoryAdapter implements ProjectRepository {

    private final ProjectJpaRepository jpa;

    ProjectRepositoryAdapter(ProjectJpaRepository jpa) {
        this.jpa = jpa;
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
    public void deleteById(long id) {
        jpa.deleteById(id);
    }

    private static ProjectEntity toEntity(Project p) {
        return new ProjectEntity(p.id(), p.name(), p.ownerUserId(), p.createdAt());
    }

    private static Project toDomain(ProjectEntity e) {
        return new Project(e.getId(), e.getName(), e.getOwnerUserId(), e.getCreatedAt());
    }
}
