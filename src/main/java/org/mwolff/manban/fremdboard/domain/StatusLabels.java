package org.mwolff.manban.fremdboard.domain;

/**
 * Die Labelnamen, an denen das fremde System den Status einer Karte trägt (Plan #2152 A3). Done hat
 * kein Label: Eine geschlossene Karte gilt als Done, eine ohne Statuslabel als Backlog.
 *
 * @param backlog Label für Backlog
 * @param ready Label für Ready
 * @param inProgress Label für In Progress
 * @param inReview Label für In Review
 */
public record StatusLabels(String backlog, String ready, String inProgress, String inReview) {

  /** Die Vorgabe: dieselben Namen wie die Spalten des Kits ({@code columns}). */
  public static final StatusLabels STANDARD =
      new StatusLabels("Backlog", "Ready", "In progress", "In review");
}
