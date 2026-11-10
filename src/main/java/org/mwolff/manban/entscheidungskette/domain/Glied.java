package org.mwolff.manban.entscheidungskette.domain;

import java.time.Instant;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Ein Glied der Entscheidungskette (Kriterium 5). Eine fehlende Angabe ist leer und steht in der
 * Oberfläche als „nicht erfasst“; die übrigen Angaben erscheinen trotzdem.
 *
 * @param art die Art des Glieds
 * @param zustand erfasst, nicht erfasst oder nicht vorgesehen
 * @param freigegebenVon wer freigegeben hat
 * @param ausgefuehrtVon wer es getan hat
 * @param zeitpunkt wann
 * @param ergebnis das Ergebnis, wörtlich
 * @param quelle die Karte oder der Kommentar, aus dem es stammt
 * @param hinweise Vermerke wie „von Hand geändert“ oder „anderes Projekt“
 * @param pruefung die Angaben einer Prüfrunde; nur bei {@link Gliedart#PRUEFRUNDE}
 */
public record Glied(
    Gliedart art,
    Zustand zustand,
    @Nullable Akteur freigegebenVon,
    @Nullable Akteur ausgefuehrtVon,
    @Nullable Instant zeitpunkt,
    @Nullable String ergebnis,
    Verweis quelle,
    Set<Hinweis> hinweise,
    @Nullable Pruefung pruefung) {

  /** Ein Glied ohne Angaben im Zustand {@code zustand}. */
  static Glied ohneAngaben(Gliedart art, Zustand zustand, Verweis quelle, Set<Hinweis> hinweise) {
    return new Glied(art, zustand, null, null, null, null, quelle, hinweise, null);
  }
}
