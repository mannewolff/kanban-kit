package org.mwolff.manban.deployment.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.mwolff.manban.deployment.domain.Ausgang;

/**
 * Die Meldung eines Auslieferungsversuchs, wie sie der Auslieferungslauf schickt (Issue #1833, Plan
 * #1831 E2, Meldevertrag aus #1784).
 *
 * @param attemptId die Kennung des Versuchs; dieselbe Kennung zählt je Projekt nur einmal
 * @param finishedAt Zeitpunkt des Ausgangs; er zählt, nicht der Eingang
 * @param revision der ausgelieferte Stand (Commit)
 * @param commits die Commits seit dem vorigen gemeldeten Stand
 */
public record NeueMeldung(
    String attemptId, Ausgang outcome, Instant finishedAt, String revision, List<Commit> commits) {

  public NeueMeldung {
    commits = List.copyOf(commits);
  }

  /**
   * Gleicher Inhalt im Sinne von E3: gleicher Ausgang, gleicher Zeitpunkt, gleicher Stand und
   * gleiche Menge der Commits, unabhängig von ihrer Reihenfolge. Die Kennung vergleicht der
   * Aufrufer.
   */
  boolean gleicherInhalt(NeueMeldung andere) {
    return outcome == andere.outcome
        && finishedAt.equals(andere.finishedAt)
        && revision.equals(andere.revision)
        && Set.copyOf(commits).equals(Set.copyOf(andere.commits));
  }

  /** Ein gemeldeter Commit mit seinem Commit-Zeitpunkt. */
  public record Commit(String sha, Instant committedAt) {}
}
