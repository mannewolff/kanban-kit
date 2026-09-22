/**
 * Infrastrukturschicht des Moduls {@code accesstoken} ausserhalb der Persistenz: der
 * Drosselungsspeicher des Nutzungsstempels im Prozessspeicher hinter dem Port {@code
 * LastUsedStampThrottle} (Issue #997).
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.accesstoken.infrastructure;

import org.jspecify.annotations.NullMarked;
