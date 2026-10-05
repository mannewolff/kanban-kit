package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.CardType;

/**
 * Kartendarstellung inkl. Abhängigkeits-Nummern, Typ und Vorhaben-Zuordnung.
 *
 * <p>{@code description} und {@code excerpt} schließen einander aus (Issue #771): Die
 * Einzelkarten-Pfade liefern den Volltext in {@code description} und lassen {@code excerpt} leer,
 * die Board-Liste genau umgekehrt. Zwei Felder mit demselben Text nebeneinander wären zwei
 * Wahrheiten über dieselbe Beschreibung.
 *
 * @param description volle Markdown-Beschreibung; {@code null} in der Antwort von {@link
 *     CardService#listByBoard(long, long)}
 * @param excerpt erste 200 Codepoints der rohen Beschreibung, nur in der Board-Liste gesetzt; sonst
 *     {@code null}
 * @param status eigener Status als Konstantenname von {@code CardStatus} (Plan #1294, E8/E24);
 *     {@code null} bei Vorhaben und Dokumentarten — dort zählt die Spalte
 * @param canSetStatus ob der Betrachter den Status dieser Karte setzen darf: {@link
 *     org.mwolff.manban.project.domain.Permission#CARD_MOVE} im Projekt <em>dieser</em> Karte (E10)
 *     — und nur, wenn sie einen eigenen Status trägt
 */
public record CardView(
    Long id,
    Long boardId,
    Long columnId,
    Integer number,
    String title,
    @Nullable String description,
    @Nullable String excerpt,
    int positionInColumn,
    boolean archived,
    @Nullable Instant movedToDoneAt,
    List<Integer> dependencies,
    CardType type,
    @Nullable Long parentId,
    @Nullable String shortcode,
    List<Long> assignees,
    @Nullable Instant dueDate,
    List<Long> labels,
    @Nullable Integer derivedFrom,
    @Nullable String status,
    boolean canSetStatus) {}
