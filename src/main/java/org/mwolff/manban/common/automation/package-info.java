/**
 * Port, über den Module ihre abschaltbaren Automatiken melden.
 *
 * <p>Der Port liegt hier und nicht in {@code config}: Ein Port dort erzwänge die Gegenkante {@code
 * card → config}, die mit der bestehenden Kante {@code config → card} den Slice-Zyklus schlösse,
 * den {@code KEINE_MODUL_ZYKLEN} prüft. Und er liegt nicht in einem Fachmodul, weil {@code
 * card.application} und {@code outbox.application} außerhalb ihrer Fassaden-Whitelist für fremde
 * Module gesperrt sind.
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.common.automation;

import org.jspecify.annotations.NullMarked;
