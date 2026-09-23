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
 * @param kind Gattung des Eintrags — Nachtlauf oder interaktive Sitzung (Issue #1010); niemals
 *     {@code null}, ein Erzeugungspfad ohne eigene Gattung trägt {@link NightRunKind#NIGHT}
 * @param durationMs Dauer des Laufs in Millisekunden
 * @param processedCount Zahl der bearbeiteten Arbeitspakete
 * @param skippedCount Zahl der übergangenen Arbeitspakete
 * @param unparsedCount Zahl der ungedeuteten Runner-Zeilen
 * @param unparsedSample Auszug der ungedeuteten Zeilen; {@code null}, wenn es keine gab
 * @param createdAt Einfügezeitpunkt
 * @param noWorkReason Grund, warum der Lauf nichts abgearbeitet hat (Issue #1068); gesetzt nur an
 *     Läufen der Gattung {@link NightRunKind#NIGHT}, die abgeschlossen gemeldet wurden und deren
 *     {@code processedCount} 0 ist. {@code null} heißt „hat gearbeitet" oder „vor der Umstellung
 *     eingeliefert" — beides ist kein Befund.
 * @param budget die Vorgaben, unter denen der Lauf angetreten ist (Issue #1112); {@code null} heißt
 *     „nicht angegeben" — Läufe vor {@code V37} und jeder Lauf, der keine gemeldet hat
 * @param abortReason Grund, warum der Lauf hart abgebrochen ist (Issue #1142); gesetzt nur an
 *     Läufen der Gattung {@link NightRunKind#NIGHT}, die abgeschlossen gemeldet wurden und ihren
 *     Abbruch selbst gemeldet haben. {@code null} heißt „nicht abgebrochen" oder „vor der
 *     Umstellung eingeliefert" — beides ist kein Befund.
 */
public record NightRun(
    @Nullable Long id,
    Long projectId,
    Instant startedAt,
    NightRunMode mode,
    NightRunKind kind,
    long durationMs,
    int processedCount,
    int skippedCount,
    int unparsedCount,
    @Nullable String unparsedSample,
    Instant createdAt,
    NightRunOrigin origin,
    @Nullable String tokenName,
    boolean complete,
    @Nullable Instant updatedAt,
    @Nullable NightRunUsage usage,
    @Nullable String noWorkReason,
    @Nullable NightRunBudget budget,
    @Nullable String abortReason)
    implements Identifiable {}
