package org.mwolff.manban.deployment.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.deployment.domain.Ausgang;
import org.mwolff.manban.deployment.domain.Auslieferungsart;

/**
 * Die Meldung eines Auslieferungsversuchs, wie sie der Auslieferungslauf schickt (Issue #1833, Plan
 * #1831 E2, Meldevertrag aus #1784; Art und Adresse seit Issue #2215, Plan #2206 E10).
 *
 * @param attemptId die Kennung des Versuchs; dieselbe Kennung zählt je Projekt nur einmal
 * @param finishedAt Zeitpunkt des Ausgangs; er zählt, nicht der Eingang
 * @param revision der ausgelieferte Stand (Commit)
 * @param commits die Commits seit dem vorigen gemeldeten Stand
 * @param art Vorschau oder Veröffentlichung; {@code null}, wenn die Meldung keine nennt
 * @param url Adresse der Vorschau oder Veröffentlichung; {@code null} ohne
 */
public record NeueMeldung(
    String attemptId,
    Ausgang outcome,
    Instant finishedAt,
    String revision,
    List<Commit> commits,
    @Nullable Auslieferungsart art,
    @Nullable String url) {

  public NeueMeldung {
    commits = List.copyOf(commits);
  }

  /** Eine Meldung ohne Art und Adresse, wie vor der Unterscheidung. */
  public NeueMeldung(
      String attemptId,
      Ausgang outcome,
      Instant finishedAt,
      String revision,
      List<Commit> commits) {
    this(attemptId, outcome, finishedAt, revision, commits, null, null);
  }

  /**
   * Gleicher Inhalt im Sinne von E3: gleicher Ausgang, gleicher Zeitpunkt, gleicher Stand, gleiche
   * Art, gleiche Adresse und gleiche Menge der Commits, unabhängig von ihrer Reihenfolge. Die
   * Kennung vergleicht der Aufrufer.
   */
  boolean gleicherInhalt(NeueMeldung andere) {
    return outcome == andere.outcome
        && finishedAt.equals(andere.finishedAt)
        && revision.equals(andere.revision)
        && art == andere.art
        && Objects.equals(url, andere.url)
        && Set.copyOf(commits).equals(Set.copyOf(andere.commits));
  }

  /** Ein gemeldeter Commit mit seinem Commit-Zeitpunkt. */
  public record Commit(String sha, Instant committedAt) {}
}
