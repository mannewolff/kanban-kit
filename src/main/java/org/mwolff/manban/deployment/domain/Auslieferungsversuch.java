package org.mwolff.manban.deployment.domain;

import java.time.Instant;

/**
 * Ein gemeldeter Auslieferungsversuch (Issue #1832, Plan #1831 E6).
 *
 * @param id interne Kennung; sie steigt mit dem Eingang und entscheidet den Gleichstand zweier
 *     Versuche mit demselben {@code finishedAt}: der zuerst eingegangene gilt als früher
 * @param attemptId die Kennung des Versuchs aus der Meldung
 * @param finishedAt Zeitpunkt des Ausgangs; er zählt, nicht der Eingang der Meldung
 * @param revision der ausgelieferte Stand (Commit)
 */
public record Auslieferungsversuch(
    long id, String attemptId, Ausgang ausgang, Instant finishedAt, String revision) {}
