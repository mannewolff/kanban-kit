/**
 * Anwendungsschicht des Moduls {@code outbox}: Schreibweg, Ports und Abarbeitung.
 *
 * <p>Nach außen sichtbar sind nur {@link org.mwolff.manban.outbox.application.OutboxWriter}, {@link
 * org.mwolff.manban.outbox.application.OutboxMessage} und {@link
 * org.mwolff.manban.outbox.application.OutboxHandler} — alles Weitere ist modulintern (durch
 * ArchUnit erzwungen, Issue #501).
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.outbox.application;

import org.jspecify.annotations.NullMarked;
