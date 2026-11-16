package org.mwolff.manban.fremdboard.domain;

import java.time.Instant;

/**
 * Ein Kommentar an einer Karte im fremden System (Issue #2158, Plan #2152 A15). Ob er als Meldung
 * des Kits gilt, entscheidet der Spiegel am {@link #autor()} gegen das hinterlegte Kit-Konto.
 *
 * @param kennung Kennung des Kommentars im fremden System
 * @param kartenNummer Nummer der Karte im fremden System
 * @param autor Benutzername des Autors im fremden System
 * @param text Text des Kommentars
 * @param angelegtAm Anlagezeitpunkt im fremden System
 * @param geaendertAm letzter Änderungszeitpunkt im fremden System
 */
public record FremdNotiz(
    String kennung,
    long kartenNummer,
    String autor,
    String text,
    Instant angelegtAm,
    Instant geaendertAm) {}
