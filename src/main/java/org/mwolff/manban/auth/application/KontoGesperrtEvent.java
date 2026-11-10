package org.mwolff.manban.auth.application;

/**
 * Anwendungs-Event: ein Konto wurde gesperrt (Issue #2104, Plan #2100 E4). Wird in der Transaktion
 * der Sperre publiziert, damit andere Module reagieren können, ohne dass {@code auth} sie kennen
 * muss.
 *
 * @param userId das gesperrte Konto
 */
public record KontoGesperrtEvent(long userId) {}
