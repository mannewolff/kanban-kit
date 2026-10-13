package org.mwolff.manban.card.application;

import java.io.Serializable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein zugewiesener Benutzer ist kein Mitglied des Projekts (400).
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class InvalidAssigneeException extends RuntimeException
    implements UebersetzbareMeldung {

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  private InvalidAssigneeException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente;
  }

  /** Der Benutzer ist kein Mitglied des Projekts. */
  public static InvalidAssigneeException keinMitglied(Long benutzer) {
    return new InvalidAssigneeException(
        "card.zustaendig.kein-mitglied", "Kein Projektmitglied: " + benutzer, benutzer);
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
