/**
 * Infrastrukturschicht des Querschnittsmoduls {@code ratelimit}: der Zählspeicher im
 * Prozessspeicher hinter dem Port {@code AttemptStore} (Plan #892, E6).
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.ratelimit.infrastructure;

import org.jspecify.annotations.NullMarked;
