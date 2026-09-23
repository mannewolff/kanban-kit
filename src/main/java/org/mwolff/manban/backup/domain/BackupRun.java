package org.mwolff.manban.backup.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ein protokollierter Sicherungslauf (Issue #826).
 *
 * <p>Geschrieben wird diese Zeile vom Sicherungs-Container, nicht von der Anwendung (Plan #825 E2);
 * die Anwendung liest sie ausschließlich. Deshalb trägt der Datensatz keine Kennung — der Lesepfad
 * braucht immer nur den jüngsten Lauf einer Art, nie einen einzelnen.
 *
 * @param startedAt Beginn des Laufs; daran hängt die Alterung (und in der Alarmstufe der
 *     Idempotenzschlüssel), nicht am Ende — ein hängengebliebener Lauf hat gar kein Ende
 * @param finishedAt Ende des Laufs; {@code null}, solange der Container nichts gemeldet hat
 * @param detail Grund eines Fehlschlags; bei einem gelungenen Lauf {@code null}
 * @param bytes Umfang des Laufs; {@code null}, wenn er nicht ermittelt wurde
 */
public record BackupRun(
    BackupKind kind,
    Instant startedAt,
    @Nullable Instant finishedAt,
    BackupOutcome outcome,
    @Nullable String detail,
    @Nullable Long bytes) {}
