/**
 * Modul {@code fremdboard}: Board eines Projekts in einem fremden System (Plan #2152, zuerst
 * GitLab). kanban-kit spiegelt dessen Karten nur lesend; die Verbindung je Projekt steht in {@code
 * fremdboard_verbindung} (Issue #2156).
 *
 * <p>Fremde Module fragen nicht dieses Modul, sondern den Port {@code
 * project.application.BoardQuelle}, den {@code fremdboard.infrastructure} umsetzt — ein Port hier
 * schlösse über {@code card} einen Modulzyklus (Plan #2152 A10).
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.fremdboard;

import org.jspecify.annotations.NullMarked;
