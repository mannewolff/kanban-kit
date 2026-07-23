package org.mwolff.manban.card.application;

import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autorisierung für das Abonnieren des Live-Ideen-Pool-Streams: stellt die Projekt-Mitgliedschaft
 * sicher (analog zu den lesenden Ideen-Endpoints). Nichtmitglied bzw. unbekanntes Projekt → 404
 * (kein Existenz-Leak). Bewusst getrennt vom Web-/SSE-Plumbing, damit die Autorisierung
 * frameworkfrei testbar bleibt.
 */
@Service
public class ProjectIdeaEventService {

  private final PermissionChecker permissions;

  public ProjectIdeaEventService(PermissionChecker permissions) {
    this.permissions = permissions;
  }

  /** Wirft, wenn der Nutzer den Live-Stream des Ideen-Pools nicht abonnieren darf. */
  @Transactional(readOnly = true)
  public void requireSubscribable(long userId, long projectId) {
    permissions.requireMembership(userId, projectId);
  }
}
