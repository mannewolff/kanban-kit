package org.mwolff.manban.deployment.application;

import org.jspecify.annotations.Nullable;

/**
 * Die jüngste Vorschau und die jüngste Veröffentlichung eines Projekts (Issue #2215, Plan #2206
 * E10).
 *
 * @param vorschau die jüngste gemeldete Vorschau; {@code null} ohne
 * @param veroeffentlichung die jüngste gemeldete Veröffentlichung, auch eine Meldung ohne Art;
 *     {@code null} ohne
 */
public record ZuletztGemeldet(
    @Nullable GemeldeterStand vorschau, @Nullable GemeldeterStand veroeffentlichung) {}
