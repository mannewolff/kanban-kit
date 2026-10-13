package org.mwolff.manban.card.application;

import java.io.Serializable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ungültige Label-Operation (leerer/duplizierter Name oder Label eines fremden Boards) (400).
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class InvalidLabelException extends RuntimeException implements UebersetzbareMeldung {

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  private InvalidLabelException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente;
  }

  /** Das Label gehört nicht zu diesem Board. */
  public static InvalidLabelException keinLabelDesBoards(Long label) {
    return new InvalidLabelException(
        "card.label.kein-label-des-boards", "Kein Label dieses Boards: " + label, label);
  }

  /** Ein Label dieses Namens gibt es schon. */
  public static InvalidLabelException existiertBereits(String name) {
    return new InvalidLabelException(
        "card.label.existiert-bereits", "Label existiert bereits: " + name, name);
  }

  /** Der Name ist leer. */
  public static InvalidLabelException nameLeer() {
    return new InvalidLabelException("card.label.name-leer", "Labelname darf nicht leer sein");
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
