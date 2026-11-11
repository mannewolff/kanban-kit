package org.mwolff.manban.entscheidungskette.domain;

import org.jspecify.annotations.Nullable;

/**
 * Wer ein Glied freigegeben oder ausgeführt hat (Kriterium 5). Ein Mensch steht mit seiner
 * Personen-ID da, deren Anzeigenamen der Dienst auflöst; ein Modell mit seinem Namen; ein Werkzeug
 * mit dem Namen seines Zugangs und, soweit gemeldet, dem Modell.
 *
 * @param typ Mensch, Modell oder Werkzeug
 * @param person die Person eines Menschen; {@code null}, wenn nicht erfasst
 * @param name der Name des Modells, des Zugangs oder eines nur genannten Menschen; {@code null},
 *     wenn nicht erfasst
 * @param modell das gemeldete Modell eines Werkzeugs; {@code null}, wenn nicht gemeldet
 */
public record Akteur(
    Akteurtyp typ, @Nullable Long person, @Nullable String name, @Nullable String modell) {

  private static final String HERKUNFT_SESSION = "SESSION";
  private static final String HERKUNFT_TOKEN = "TOKEN";

  /** Ein Mensch. */
  public static Akteur mensch(@Nullable Long person) {
    return new Akteur(Akteurtyp.MENSCH, person, null, null);
  }

  /**
   * Der Akteur eines Eintrags nach seiner Herkunft: Eine Person zählt nur bei {@code SESSION}, ein
   * Token nennt seinen Zugang (Plan #2108 E11); ohne Herkunft {@code null}.
   */
  static @Nullable Akteur nachHerkunft(
      @Nullable String herkunft,
      @Nullable Long person,
      @Nullable String tokenName,
      @Nullable String agent) {
    if (HERKUNFT_SESSION.equals(herkunft)) {
      return mensch(person);
    }
    return HERKUNFT_TOKEN.equals(herkunft) ? werkzeug(tokenName, agent) : null;
  }

  /** Ein Mensch, den eine Meldung des Kits nur mit Namen nennt. */
  public static Akteur menschGenannt(String name) {
    return new Akteur(Akteurtyp.MENSCH, null, name, null);
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
