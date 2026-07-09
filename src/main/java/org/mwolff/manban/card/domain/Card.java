package org.mwolff.manban.card.domain;

import java.time.Instant;

/**
 * Karte — Kern-Aggregat. Die {@code number} ist board-scoped (eindeutig pro Board).
 *
 * <p>Ein Datensatz ist entweder eine normale Karte ({@link CardType#CARD}) oder ein
 * Epic ({@link CardType#EPIC}). Epics nehmen nicht am Spalten-Workflow teil (keine
 * aktive Position) und können Kinder gruppieren; eine Karte verweist über
 * {@code parentId} auf ihr Epic.
 *
 * @param id               technische ID; {@code null} vor der Persistierung
 * @param boardId          zugehöriges Board
 * @param columnId         aktuelle Spalte
 * @param number           board-scoped Anzeigenummer
 * @param title            Titel
 * @param description      Markdown-Beschreibung (nullable)
 * @param positionInColumn Position in der Spalte
 * @param archived         ob archiviert (dann außerhalb des aktiven Positions-Namespace)
 * @param movedToDoneAt    Zeitpunkt des Zugs nach Done (nullable)
 * @param createdBy        Ersteller (nullable, z. B. bei PAT)
 * @param createdAt        Erstellzeitpunkt
 * @param updatedAt        letzte Änderung
 * @param type             CARD oder EPIC
 * @param parentId         zugeordnetes Epic (nullable; nur an CARD gesetzt)
 * @param shortcode        Kürzel eines Epics (nullable; nur an EPIC)
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
        Instant updatedAt,
        CardType type,
        Long parentId,
        String shortcode) {

    public Card withContent(String newTitle, String newDescription) {
        return new Card(id, boardId, columnId, number, newTitle, newDescription, positionInColumn,
                archived, movedToDoneAt, createdBy, createdAt, updatedAt, type, parentId, shortcode);
    }

    public Card asArchived() {
        return new Card(id, boardId, columnId, number, title, description, positionInColumn,
                true, movedToDoneAt, createdBy, createdAt, updatedAt, type, parentId, shortcode);
    }

    /** Wiederherstellen an einer freien Position (append), um Positionskollisionen zu vermeiden. */
    public Card asRestored(int newPositionInColumn) {
        return new Card(id, boardId, columnId, number, title, description, newPositionInColumn,
                false, movedToDoneAt, createdBy, createdAt, updatedAt, type, parentId, shortcode);
    }

    public Card withMovedToDoneAt(Instant when) {
        return new Card(id, boardId, columnId, number, title, description, positionInColumn,
                archived, when, createdBy, createdAt, updatedAt, type, parentId, shortcode);
    }

    /** Setzt oder löscht ({@code null}) die Epic-Zuordnung. */
    public Card withParent(Long newParentId) {
        return new Card(id, boardId, columnId, number, title, description, positionInColumn,
                archived, movedToDoneAt, createdBy, createdAt, updatedAt, type, newParentId, shortcode);
    }

    /** Setzt das Kürzel (nur für Epics sinnvoll). */
    public Card withShortcode(String newShortcode) {
        return new Card(id, boardId, columnId, number, title, description, positionInColumn,
                archived, movedToDoneAt, createdBy, createdAt, updatedAt, type, parentId, newShortcode);
    }
}
