/**
 * Persistenz des Moduls {@code ratelimit}: die Aggregate der Abweisungen wegen Überlast je Person
 * und Stunde (Issue #1000) und der Puffer davor.
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.ratelimit.infrastructure.persistence;

import org.jspecify.annotations.NullMarked;
