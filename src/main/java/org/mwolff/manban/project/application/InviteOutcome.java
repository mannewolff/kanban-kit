package org.mwolff.manban.project.application;

/** Ergebnis einer Projekt-Zuordnung: direkt Mitglied geworden oder (per Token) eingeladen. */
public enum InviteOutcome {
  ADDED("added"),
  INVITED("invited");

  private final String label;

  InviteOutcome(String label) {
    this.label = label;
  }

  /** Status-Kennung für die HTTP-Antwort ({@code "added"} bzw. {@code "invited"}). */
  public String status() {
    return label;
  }
}
