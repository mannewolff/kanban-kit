/**
 * Infrastrukturschicht des Moduls {@code push}: Verschlüsselung nach RFC 8291, VAPID-Signatur nach
 * RFC 8292, Versand und Startprüfung der Schlüssel (Issue #1996) — allein mit {@code
 * java.security}, {@code javax.crypto} und {@code java.net.http}.
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.push.infrastructure;

import org.jspecify.annotations.NullMarked;
