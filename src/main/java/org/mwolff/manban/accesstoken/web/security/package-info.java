/**
 * Web-Security-Adapter des Moduls {@code accesstoken}: PAT-Authentifizierung per Servlet-Filter.
 *
 * <p>Der Filter ist ein eingehender HTTP-Adapter (er liest einen Request-Header) und spricht
 * ausschließlich über die Application-Schicht des eigenen Moduls. Verdrahtet wird er in der
 * anwendungsweiten Composition-Root {@code org.mwolff.manban.config.SecurityConfig} (Issue #438).
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.accesstoken.web.security;

import org.jspecify.annotations.NullMarked;
