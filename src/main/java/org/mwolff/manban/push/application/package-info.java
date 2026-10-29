/**
 * Application-Schicht des Moduls {@code push}: die Konfiguration {@code manban.push.*} (Issue
 * #1996) und die Abo-Verwaltung mit ihren Persistenz-Ports (Issue #1997). Der Meldejob folgt mit
 * einem eigenen Paket.
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.push.application;

import org.jspecify.annotations.NullMarked;
