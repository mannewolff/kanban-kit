package org.mwolff.manban.nightrun.domain;

import org.jspecify.annotations.Nullable;

/**
 * Angaben zu einem festgefahrenen Arbeitspaket, wie das Kit sie meldet (Issue #1546, Plan #1547).
 *
 * <p>Jede Angabe ist einzeln optional (Plan #1547, E8): Eine fehlende heißt „nicht gemeldet", nicht
 * null oder leer. Ein Paket ohne jede Angabe trägt keinen solchen Record, sondern {@code null}.
 *
 * @param check die Prüfung, an der das Paket hing
 * @param error der wiederkehrende Fehler
 * @param attempts Zahl der Versuche
 * @param sessionLimitMs Zeitgrenze der Sitzung dieses Pakets in Millisekunden
 * @param sessionId Kennung der Sitzung
 */
public record NightRunStuck(
    @Nullable String check,
    @Nullable String error,
    @Nullable Integer attempts,
    @Nullable Long sessionLimitMs,
    @Nullable String sessionId) {}
