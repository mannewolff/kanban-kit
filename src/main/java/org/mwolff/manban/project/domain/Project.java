package org.mwolff.manban.project.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Projekt — oberste Ebene (Tenant). Boards, Mitgliedschaften und alles Weitere hängen darunter.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param name Projektname
 * @param ownerUserId Ersteller/Eigentümer
 * @param createdAt Erstellzeitpunkt
 */
public record Project(@Nullable Long id, String name, Long ownerUserId, Instant createdAt)
    implements Identifiable {

  public Project withName(String newName) {
    return new Project(id, newName, ownerUserId, createdAt);
  }
}
