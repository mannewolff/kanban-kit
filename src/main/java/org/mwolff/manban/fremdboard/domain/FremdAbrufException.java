package org.mwolff.manban.fremdboard.domain;

/**
 * Ein Abruf beim fremden System ist gescheitert (Issue #2166, Plan #2152 A9, A15): nicht
 * erreichbar, Zugang abgelehnt, Projekt unbekannt oder eine Antwort, die sich nicht lesen lässt.
 * Jede {@link FremdBoardQuelle} wirft sie oder eine Unterklasse; die Meldung ist ein lesbarer Grund
 * und nennt nie den Zugang.
 */
public class FremdAbrufException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public FremdAbrufException(String grund) {
    super(grund);
  }

  public FremdAbrufException(String grund, Throwable ursache) {
    super(grund, ursache);
  }
}
