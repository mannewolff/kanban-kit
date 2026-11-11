package org.mwolff.manban.entscheidungskette.domain;

import java.time.Instant;

/**
 * Die Angaben der Freigabe zur Umsetzung (Plan #2108 E11).
 *
 * @param fall welcher Fall galt
 * @param zugNachReady wann das Paket nach Ready gezogen wurde; im Fall {@link
 *     Freigabefall#KENNZEICHEN} die Ausführung der Freigabe
 */
public record Freigabeangaben(Freigabefall fall, Instant zugNachReady) implements Einzelheiten {}
