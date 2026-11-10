package org.mwolff.manban.entscheidungskette.domain;

import java.util.List;

/**
 * Die gerechnete Kette einer Karte.
 *
 * @param art das Dokument der geöffneten Karte
 * @param abschnitte von der Idee bis zur geöffneten Karte
 * @param nachfahren nur am Fachplan und am Plan, in der Folge der Eingabe
 */
public record Entscheidungskette(
    Dokumentart art, List<Abschnitt> abschnitte, List<Nachfahre> nachfahren) {}
