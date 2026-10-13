package org.mwolff.manban.card.application;

import java.io.Serializable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die gesetzte Projekt-Startnummer ist ungültig — sie liegt nicht über der höchsten bereits
 * vergebenen Kartennummer des Projekts.
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class InvalidCardNumberException extends RuntimeException
    implements UebersetzbareMeldung {

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  private InvalidCardNumberException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente;
  }

  /** Die Nummer liegt über der Obergrenze. */
  public static InvalidCardNumberException ueberObergrenze(Integer obergrenze) {
    return new InvalidCardNumberException(
        "card.startnummer.ueber-obergrenze",
        "Nächste Nummer darf höchstens " + obergrenze + " sein",
        obergrenze);
  }

  /** Die Nummer liegt nicht über der höchsten vergebenen. */
  public static InvalidCardNumberException nichtUeberHoechster(Integer hoechste) {
    return new InvalidCardNumberException(
        "card.startnummer.nicht-ueber-hoechster",
        "Nächste Nummer muss größer als die höchste vergebene Nummer " + hoechste + " sein",
        hoechste);
  }

  @Override
  public String schluessel() {
    return meldungsschluessel;
  }

  @Override
  public Object[] argumente() {
    return meldungsargumente.clone();
  }
}
