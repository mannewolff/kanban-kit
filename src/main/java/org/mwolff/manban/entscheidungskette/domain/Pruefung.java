package org.mwolff.manban.entscheidungskette.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;

/**
 * Die Angaben einer Prüfrunde (Kriterium 4, Plan #2108 E9).
 *
 * @param art Plan- oder Fachplan-Review
 * @param runde die Nummer der Runde
 * @param pruefer die Prüfer mit Modell und Funden; leer, wenn die Meldung von Hand geändert wurde
 * @param funde alle Funde je Schwere; {@code null}, wenn nicht erfasst
 * @param einarbeitung die Einarbeitung derselben Runde, sonst ein Glied {@code NICHT_ERFASST}
 */
public record Pruefung(
    Meldungsart art,
    int runde,
    List<Reviewer> pruefer,
    @Nullable Fundzahlen funde,
    Glied einarbeitung) {}
