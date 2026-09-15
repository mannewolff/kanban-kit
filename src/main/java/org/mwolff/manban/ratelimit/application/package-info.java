/**
 * Anwendungsschicht des Querschnittsmoduls {@code ratelimit}: Fassade, Vorgänge, Konfiguration und
 * der Port auf den Zählspeicher.
 *
 * <p>Nach außen sichtbar sind ausschließlich {@code RateLimiter} und {@code RateLimitedOperation}
 * (Plan #892, E16); alles Übrige — insbesondere {@code RateLimitProperties} — ist modulintern und
 * per ArchUnit-Whitelist abgesichert. Konfiguration ist kein Vertragsbestandteil: Wer wissen will,
 * ob die Bremse läuft, fragt {@code RateLimiter.isEnabled()}.
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.ratelimit.application;

import org.jspecify.annotations.NullMarked;
