package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Auswertung eines Nachtlaufs — die verdichtete Fassung eines Runner-Protokolls (Issue #721).
 *
 * <p>Fachlicher Schlüssel eines Laufs ist {@code (projectId, startedAt)} (Plan #718, A4): Dasselbe
 * Protokoll zweimal hochgeladen ergibt genau einen Lauf.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param projectId Projekt, zu dem der Lauf gehört
 * @param startedAt Startzeitpunkt des Laufs — sein fachlicher Schlüssel
 * @param mode Betriebsart des Laufs
 * @param durationMs Dauer des Laufs in Millisekunden
 * @param processedCount Zahl der bearbeiteten Arbeitspakete
 * @param skippedCount Zahl der übergangenen Arbeitspakete
 * @param unparsedCount Zahl der ungedeuteten Runner-Zeilen
 * @param unparsedSample Auszug der ungedeuteten Zeilen; {@code null}, wenn es keine gab
 * @param createdAt Einfügezeitpunkt
 */
public record NightRun(
    @Nullable Long id,
    Long projectId,
    Instant startedAt,
    NightRunMode mode,
    long durationMs,
    int processedCount,
    int skippedCount,
    int unparsedCount,
    @Nullable String unparsedSample,
    Instant createdAt)
    implements Identifiable {}
