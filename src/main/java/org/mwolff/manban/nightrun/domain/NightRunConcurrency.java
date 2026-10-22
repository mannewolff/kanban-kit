package org.mwolff.manban.nightrun.domain;

import org.jspecify.annotations.Nullable;

/**
 * Angaben zur gleichzeitigen Umsetzung eines Arbeitspakets, wie das Kit sie meldet (Issue #1877,
 * Plan #1876 E4; fachliche Quelle #1863).
 *
 * <p>Ein Paket trägt diesen Record, sobald die Meldung mindestens eines der drei Felder führt —
 * auch mit einem Wert außerhalb seines Wertebereichs. Eine {@code null}-Komponente heißt dann
 * „nicht gemeldet". Ein Paket, das keines der drei Felder führt, trägt keinen Record, sondern
 * {@code null}.
 *
 * @param concurrent ob das Paket gleichzeitig mit mindestens einem anderen lief
 * @param checkWaitMs Wartezeit auf einen freien Platz für den vollen Prüflauf in Millisekunden, nie
 *     negativ
 * @param deferredAtMerge ob die Übernahme in den gemeinsamen Stand zurückgestellt wurde
 */
public record NightRunConcurrency(
    @Nullable Boolean concurrent, @Nullable Long checkWaitMs, @Nullable Boolean deferredAtMerge) {}
