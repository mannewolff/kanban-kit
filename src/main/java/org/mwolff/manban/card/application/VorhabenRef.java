package org.mwolff.manban.card.application;

import org.jspecify.annotations.Nullable;

/**
 * Ein Vorhaben, wie es fremde Module sehen, die nur seine Kennung brauchen (Issue #2000, Plan
 * #1990; Nachfolger von {@code EpicRef} aus Issue #936).
 *
 * <p>Eigener schmaler Typ statt {@code CardView} oder {@code EpicView}: Der Vertrag bleibt klein,
 * und das fragende Modul berührt {@code card.domain} nicht ({@code ArchitectureTest}, {@code
 * CARD_DOMAIN_IST_MODULINTERN}).
 *
 * @param id technische ID des Vorhabens
 * @param number projektweite Kartennummer des Vorhabens
 * @param shortcode Kürzel; {@code null}, wenn das Vorhaben keines trägt
 * @param title Titel des Vorhabens
 * @param archiviert ob das Vorhaben archiviert ist — es steht dann mit dem Zusatz „archiviert“ in
 *     der Aufstellung (#1954 AK 1)
 */
public record VorhabenRef(
    long id, int number, @Nullable String shortcode, String title, boolean archiviert) {}
