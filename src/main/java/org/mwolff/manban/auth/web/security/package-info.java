/**
 * Web-Security-Adapter des Moduls {@code auth}: Servlet-Filter und Cookie-Handling.
 *
 * <p>Diese Klassen sind eingehende HTTP-Adapter — sie lesen Cookies, setzen den Sicherheitskontext
 * und sprechen ausschließlich über Application-Ports (z. B. {@code SessionTokens}) mit dem Kern.
 * Verdrahtet werden sie nicht hier, sondern in der anwendungsweiten Composition-Root {@code
 * org.mwolff.manban.config.SecurityConfig} (Issue #438).
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.auth.web.security;

import org.jspecify.annotations.NullMarked;
