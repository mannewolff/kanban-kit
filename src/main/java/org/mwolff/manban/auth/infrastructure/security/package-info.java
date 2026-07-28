/**
 * Ausgehender Security-Adapter des Moduls {@code auth}: die HMAC-Implementierung des Ports {@code
 * auth.application.SessionTokens}.
 *
 * <p>Servlet-Filter und Cookie-Handling liegen nicht hier, sondern als eingehende Adapter in {@code
 * auth.web.security}; die Filterkette verdrahtet {@code config.SecurityConfig} (Issue #438).
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.auth.infrastructure.security;

import org.jspecify.annotations.NullMarked;
