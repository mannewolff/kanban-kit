package org.mwolff.manban.project.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Einladung einer E-Mail-Adresse in ein Projekt mit vorgesehener Rolle. Persistiert wird nur der
 * Token-Hash; das Klartext-Token geht per E-Mail an die eingeladene Person.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param projectId Zielprojekt
 * @param email eingeladene E-Mail-Adresse (normalisiert)
 * @param role vorgesehene Rolle
 * @param tokenHash SHA-256-Hash des Klartext-Tokens
 * @param expiresAt Ablaufzeitpunkt
 * @param acceptedAt Annahmezeitpunkt; {@code null} solange offen
 * @param invitedBy einladender Benutzer
 */
public record ProjectInvitation(
    @Nullable Long id,
    Long projectId,
    String email,
    ProjectRole role,
    String tokenHash,
    Instant expiresAt,
    @Nullable Instant acceptedAt,
    Long invitedBy)
    implements Identifiable {

  public boolean isAccepted() {
    return acceptedAt != null;
  }

  public boolean isExpired(Instant now) {
    return expiresAt.isBefore(now);
  }

  public ProjectInvitation markAccepted(Instant when) {
    return new ProjectInvitation(id, projectId, email, role, tokenHash, expiresAt, when, invitedBy);
  }
}
