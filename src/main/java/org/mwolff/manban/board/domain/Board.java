package org.mwolff.manban.board.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Board — Container für Karten innerhalb eines Projekts.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param projectId zugehöriges Projekt
 * @param name Board-Name
 * @param createdAt Erstellzeitpunkt
 */
public record Board(@Nullable Long id, Long projectId, String name, Instant createdAt)
    implements Identifiable {

  public Board withName(String newName) {
    return new Board(id, projectId, newName, createdAt);
  }
}
