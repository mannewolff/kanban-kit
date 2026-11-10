package org.mwolff.manban.entscheidungskette.domain;

import java.util.List;

/**
 * Die Glieder eines Dokuments der Kette: zuerst Idee oder Herkunft, danach die Prüfrunden nach
 * Zeitpunkt und ID.
 *
 * @param art welches Dokument
 * @param glieder nie leer
 */
public record Abschnitt(Dokumentart art, List<Glied> glieder) {}
