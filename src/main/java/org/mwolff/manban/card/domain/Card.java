package org.mwolff.manban.card.domain;

import java.time.Instant;

/**
 * Karte — Kern-Aggregat. Die {@code number} ist board-scoped (eindeutig pro Board).
 * Positionslogik (Move/Reindex) kommt mit B3; hier nur Anlegen/Bearbeiten/Archivieren.
 *
 * @param id               technische ID; {@code null} vor der Persistierung
 * @param boardId          zugehöriges Board
 * @param columnId         aktuelle Spalte
 * @param number           board-scoped Anzeigenummer
 * @param title            Titel
 * @param description      Markdown-Beschreibung (nullable)
 * @param positionInColumn Position in der Spalte
 * @param archived         ob archiviert (dann außerhalb des aktiven Positions-Namespace)
 * @param movedToDoneAt    Zeitpunkt des Zugs nach Done (nullable; gesetzt in B3)
 * @param createdBy        Ersteller (nullable, z. B. bei PAT)
 * @param createdAt        Erstellzeitpunkt
 * @param updatedAt        letzte Änderung
 */
public record Card(
        Long id,
        Long boardId,
        Long columnId,
        int number,
        String title,
        String description,
        int positionInColumn,
        boolean archived,
        Instant movedToDoneAt,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public Card withContent(String newTitle, String newDescription) {
        return new Card(id, boardId, columnId, number, newTitle, newDescription, positionInColumn,
                archived, movedToDoneAt, createdBy, createdAt, updatedAt);
    }

    public Card asArchived() {
        return new Card(id, boardId, columnId, number, title, description, positionInColumn,
                true, movedToDoneAt, createdBy, createdAt, updatedAt);
    }

    /** Wiederherstellen an einer freien Position (append), um Positionskollisionen zu vermeiden. */
    public Card asRestored(int newPositionInColumn) {
        return new Card(id, boardId, columnId, number, title, description, newPositionInColumn,
                false, movedToDoneAt, createdBy, createdAt, updatedAt);
    }

    public Card withMovedToDoneAt(Instant when) {
        return new Card(id, boardId, columnId, number, title, description, positionInColumn,
                archived, when, createdBy, createdAt, updatedAt);
    }
}
