package org.mwolff.manban.project.application;

import java.util.Optional;
import org.mwolff.manban.project.domain.ProjectInvitation;

/** Ausgehender Port für die Persistenz von Projekt-Einladungen. */
public interface ProjectInvitationRepository {

  ProjectInvitation save(ProjectInvitation invitation);

  Optional<ProjectInvitation> findByTokenHash(String tokenHash);
}
