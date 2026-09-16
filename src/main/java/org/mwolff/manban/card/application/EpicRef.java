package org.mwolff.manban.card.application;

import org.jspecify.annotations.Nullable;

/**
 * Ein Vorhaben, wie es fremde Module sehen, die nur seine Kennung brauchen (Issue #936).
 *
 * <p>Eigener schmaler Typ statt {@code CardView} oder {@code EpicView}: Der Vertrag bleibt klein,
 * und das fragende Modul berührt {@code card.domain} nicht ({@code ArchitectureTest}, {@code
 * CARD_DOMAIN_IST_MODULINTERN}).
 *
 * @param id technische ID des Vorhabens
 * @param shortcode Kürzel; {@code null}, wenn das Vorhaben keines trägt
 * @param title Titel des Vorhabens
 */
public record EpicRef(long id, @Nullable String shortcode, String title) {}
