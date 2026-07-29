package org.mwolff.manban.board.application;

/**
 * Ein Board steht unmittelbar vor dem endgültigen Löschen (Hard-Delete inkl. Cascade) — publiziert
 * <strong>vor</strong> dem Delete, im selben Transaktions-Scope (Issue #503).
 *
 * <p>Das card-Modul hört darauf und meldet die betroffenen Karten als {@code CardsPurgedEvent}
 * weiter (Kette analog zur Datenbank-Cascade {@code board → card → attachment_meta}). Das
 * board-Modul kann die Karten nicht selbst auflösen — die Kante {@code card → board} existiert
 * bereits, die Umkehrung wäre ein Modul-Zyklus.
 *
 * @param boardId ID des endgültig zu löschenden Boards
 */
public record BoardPurgedEvent(long boardId) {}
