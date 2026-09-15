/**
 * Web-Adapter des Querschnittsmoduls {@code ratelimit}: die Auflösung der Herkunft aus dem
 * eingehenden Request (Plan #892, E2/E3/E7).
 *
 * <p>Gelesen werden ausschließlich Header und Peer-Adresse — nie der Request-Body. Damit kennt die
 * Bremse die angefragte E-Mail-Adresse technisch gar nicht; das ist stärker als die Zusage, sie
 * nicht zu protokollieren (AK 5 aus Issue #840, E11).
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.ratelimit.web;

import org.jspecify.annotations.NullMarked;
