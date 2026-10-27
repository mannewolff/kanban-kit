package org.mwolff.manban.auth.application;

import java.time.Instant;

/**
 * Kontext einer Anfrage in der Vorschau „Ansicht als XY“ (Issue #1918, Plan #1914 E1): Der
 * Principal ist das Zielkonto XY, dieser Kontext nennt daneben den handelnden Plattform-Admin.
 *
 * @param kennung öffentliche Kennung der Vorschau
 * @param adminId handelnder Plattform-Admin
 * @param zielId Konto, als das die App gezeigt wird
 * @param laeuftAbAm Ablaufzeitpunkt der Vorschau
 */
public record VorschauKontext(String kennung, long adminId, long zielId, Instant laeuftAbAm) {}
