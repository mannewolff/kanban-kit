package org.mwolff.manban.deployment.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ein gemeldeter Stand mit seiner Adresse — die jüngste Vorschau oder Veröffentlichung eines
 * Projekts (Issue #2215, Plan #2206 E10).
 *
 * @param revision der gemeldete Stand (Commit)
 * @param url Adresse der Vorschau bzw. Veröffentlichung; {@code null}, wenn die Meldung keine
 *     nannte
 * @param finishedAt Zeitpunkt des Ausgangs laut Meldung
 * @param eingang Zeitpunkt, zu dem die Meldung beim Leitstand einging (Serverzeit)
 */
public record GemeldeterStand(
    String revision, @Nullable String url, Instant finishedAt, Instant eingang) {}
