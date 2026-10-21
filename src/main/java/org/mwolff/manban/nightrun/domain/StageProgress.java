package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Eine Stufe im Weg einer Kette mit ihrem Zustand (Issue #1374).
 *
 * @param stufe die Stufe
 * @param zustand ihr Zustand
 * @param seit der Beginn der laufenden Stufe aus dem Laufstand (Issue #1883); {@code null} bei
 *     jeder anderen Stufe und wenn kein Beginn gemeldet ist
 */
public record StageProgress(ProgressStage stufe, StageState zustand, @Nullable Instant seit) {}
