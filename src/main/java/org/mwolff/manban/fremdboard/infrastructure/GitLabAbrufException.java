package org.mwolff.manban.fremdboard.infrastructure;

/**
 * Ein Abruf bei GitLab ist gescheitert (Issue #2160, Plan #2152 A9): Zeitüberschreitung, nicht
 * erreichbar, Zugang abgelehnt (401), Projekt oder Karte unbekannt (404), Serverfehler (5xx) oder
 * eine Antwort, die sich nicht lesen lässt. Die Meldung ist ein lesbarer Grund für die Einstellung
 * und das Protokoll; sie nennt nie den Zugang und nie den Rumpf der Antwort.
 */
public class GitLabAbrufException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  GitLabAbrufException(String grund) {
    super(grund);
  }

  GitLabAbrufException(String grund, Throwable ursache) {
    super(grund, ursache);
  }
}
