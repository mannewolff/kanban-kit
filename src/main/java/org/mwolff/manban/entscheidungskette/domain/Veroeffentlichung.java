package org.mwolff.manban.entscheidungskette.domain;

import org.jspecify.annotations.Nullable;

/**
 * Die Meldung einer Veröffentlichung. Leer ist, was fehlt oder als {@code nicht erfasst} gemeldet
 * wurde.
 *
 * @param versuch Kennung des Versuchs
 * @param version die Version
 * @param commit der veröffentlichte Stand
 * @param basis der Stand davor
 * @param ausgang der Ausgang, wörtlich
 * @param quelle Verweis auf den Pull Request, wörtlich
 * @param freigegebenVon wer freigegeben hat und wann
 * @param ausgefuehrtVon wer ausgeführt hat und wann
 */
public record Veroeffentlichung(
    @Nullable String versuch,
    @Nullable String version,
    @Nullable String commit,
    @Nullable String basis,
    @Nullable String ausgang,
    @Nullable String quelle,
    Angabe freigegebenVon,
    Angabe ausgefuehrtVon)
    implements KitMeldung {}
