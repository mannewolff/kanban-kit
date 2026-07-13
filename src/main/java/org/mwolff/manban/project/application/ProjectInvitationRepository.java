package org.mwolff.manban.project.application;

import java.time.Instant;
import java.util.Optional;
import org.mwolff.manban.project.domain.ProjectInvitation;

/** Ausgehender Port für die Persistenz von Projekt-Einladungen. */
public interface ProjectInvitationRepository {

  ProjectInvitation save(ProjectInvitation invitation);

  Optional<ProjectInvitation> findByTokenHash(String tokenHash);

  /**
   * Ob für die E-Mail eine <strong>offene</strong> Einladung existiert (noch nicht angenommen und
   * noch nicht abgelaufen), bezogen auf {@code now}.
   */
  boolean existsOpenInvitation(String email, Instant now);
}
