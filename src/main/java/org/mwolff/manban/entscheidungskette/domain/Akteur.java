package org.mwolff.manban.entscheidungskette.domain;

import org.jspecify.annotations.Nullable;

/**
 * Wer ein Glied freigegeben oder ausgeführt hat (Kriterium 5). Ein Mensch steht mit seiner
 * Personen-ID da, deren Anzeigenamen der Dienst auflöst; ein Modell mit seinem Namen; ein Werkzeug
 * mit dem Namen seines Zugangs und, soweit gemeldet, dem Modell.
 *
 * @param typ Mensch, Modell oder Werkzeug
 * @param person die Person eines Menschen; {@code null}, wenn nicht erfasst
 * @param name der Name des Modells oder des Zugangs; {@code null}, wenn nicht erfasst
 * @param modell das gemeldete Modell eines Werkzeugs; {@code null}, wenn nicht gemeldet
 */
public record Akteur(
    Akteurtyp typ, @Nullable Long person, @Nullable String name, @Nullable String modell) {

  /** Ein Mensch. */
  public static Akteur mensch(@Nullable Long person) {
    return new Akteur(Akteurtyp.MENSCH, person, null, null);
  }

  /** Ein Modell mit seinem Namen. */
  public static Akteur modell(String name) {
    return new Akteur(Akteurtyp.MODELL, null, name, null);
  }

  /** Ein Werkzeug mit dem Namen seines Zugangs und dem gemeldeten Modell. */
  public static Akteur werkzeug(@Nullable String zugang, @Nullable String modell) {
    return new Akteur(Akteurtyp.WERKZEUG, null, zugang, modell);
  }
}
