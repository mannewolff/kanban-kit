package org.mwolff.manban.deployment.domain;

import java.time.Instant;

/**
 * Ein Commit, den ein Auslieferungsversuch gemeldet hat (Issue #1832, Plan #1831 E6). Denselben
 * {@code sha} können mehrere Versuche melden; die Ermittlung zählt ihn genau einmal.
 *
 * @param versuchId interne Kennung des meldenden {@link Auslieferungsversuch}
 * @param committedAt Commit-Zeitpunkt laut Meldung
 */
public record GemeldeterCommit(long versuchId, String sha, Instant committedAt) {}
