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
 * @param archivedAt Archivierungszeitpunkt; {@code null} bedeutet „aktiv"
 */
public record Board(
    @Nullable Long id, Long projectId, String name, Instant createdAt, @Nullable Instant archivedAt)
    implements Identifiable {

  /** Kompakt-Konstruktor für ein aktives (nicht archiviertes) Board. */
  public Board(@Nullable Long id, Long projectId, String name, Instant createdAt) {
    this(id, projectId, name, createdAt, null);
  }

  public Board withName(String newName) {
    return new Board(id, projectId, newName, createdAt, archivedAt);
  }

  /** Markiert das Board als archiviert. */
  public Board archivedAt(Instant when) {
    return new Board(id, projectId, name, createdAt, when);
  }

  /** Hebt die Archivierung auf (wieder aktiv). */
  public Board restored() {
    return new Board(id, projectId, name, createdAt, null);
  }

  public boolean isArchived() {
    return archivedAt != null;
  }
}
