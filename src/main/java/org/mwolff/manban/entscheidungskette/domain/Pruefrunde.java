package org.mwolff.manban.entscheidungskette.domain;

import java.util.List;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;

/**
 * Ein Review-Kommentar des Kits zu einem Dokument (Plan #2108 E9). Ein Gesamturteil gibt es nicht,
 * weil das Kit keines schreibt.
 *
 * @param art Plan-, Fachplan- oder Issue-Review
 * @param runde die Nummer der Runde
 * @param reviewer die Prüfer in der Folge des Kommentars
 * @param funde alle Funde des Kommentars je Schwere
 */
public record Pruefrunde(Meldungsart art, int runde, List<Reviewer> reviewer, Fundzahlen funde)
    implements KitMeldung {}
