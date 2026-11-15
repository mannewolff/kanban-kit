/**
 * Domänenschicht des Moduls {@code fremdboard} (Plan #2152): die Verbindung zum fremden Board und
 * ihre Wertobjekte (Issue #2156), dazu die Schnittstelle {@link
 * org.mwolff.manban.fremdboard.domain.FremdBoardQuelle} mit Karten, Ereignissen und Notizen, die
 * Statuszuordnung und die Herkunft aus dem Kartentext (Issue #2158).
 *
 * <p>{@code @NullMarked} (Issue #0080): Alle Referenzen in diesem Package sind per Default
 * non-null; Ausnahmen tragen explizit {@code @Nullable} (CLAUDE-java.md §6.2).
 */
@NullMarked
package org.mwolff.manban.fremdboard.domain;

import org.jspecify.annotations.NullMarked;
