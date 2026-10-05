package org.mwolff.manban.card.application;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Eine Karte oder ein Vorhaben.")
public record CardView(
    @Schema(description = "Interne ID; in den Pfaden verwendet.", example = "812") Long id,
    @Schema(description = "Interne ID des Boards.", example = "3") Long boardId,
    @Schema(description = "Interne ID der Spalte.", example = "17") Long columnId,
    @Schema(description = "Projektweite Nummer, etwa #1404.", example = "1404") Integer number,
    @Schema(description = "Titel.", example = "Export als CSV") String title,
    @Schema(
            description =
                "Volle Beschreibung in Markdown; leer in der Board-Liste, dort steht excerpt.",
            example = "## Kontext\nWarum …")
        @Nullable String description,
    @Schema(
            description =
                "Die ersten 200 Zeichen der Beschreibung; nur in der Board-Liste gesetzt.",
            example = "## Kontext\nWarum …")
        @Nullable String excerpt,
    @Schema(description = "Position in der Spalte, oben zuerst.", example = "0")
        int positionInColumn,
    @Schema(description = "Ob die Karte archiviert ist.", example = "false") boolean archived,
    @Schema(
            description = "Zeitpunkt, zu dem die Karte nach Done kam (ISO-8601).",
            example = "2026-10-05T13:35:19Z")
        @Nullable Instant movedToDoneAt,
    @Schema(
            description = "Projektweite Nummern der Karten, von denen diese abhängt.",
            example = "[41, 42]")
        List<Integer> dependencies,
    @Schema(description = "CARD für eine Karte, EPIC für ein Vorhaben.", example = "CARD")
        CardType type,
    @Schema(description = "Interne ID des Vorhabens, dem die Karte angehört.", example = "640")
        @Nullable Long parentId,
    @Schema(description = "Kürzel eines Vorhabens.", example = "API") @Nullable String shortcode,
    @Schema(description = "Benutzer-IDs der Zuständigen.", example = "[5]") List<Long> assignees,
    @Schema(description = "Fälligkeit (ISO-8601).", example = "2026-10-31T00:00:00Z")
        @Nullable Instant dueDate,
    @Schema(description = "IDs der Labels.", example = "[9]") List<Long> labels,
    @Schema(
            description =
                "Herkunft: projektweite Nummer der Karte, aus der diese abgeleitet ist, etwa der"
                    + " Plan eines Arbeitspakets.",
            example = "1400")
        @Nullable Integer derivedFrom,
    @Schema(
            description =
                "Eigener Status eines Arbeitspakets (etwa BACKLOG, READY, IN_PROGRESS,"
                    + " IN_REVIEW, DONE); leer bei Vorhaben und Dokumentarten, dort zählt die"
                    + " Spalte.",
            example = "IN_PROGRESS")
        @Nullable String status,
    @Schema(
            description =
                "Ob der Aufrufer den Status dieser Karte setzen darf (Recht CARD_MOVE im Projekt"
                    + " der Karte, und nur bei Karten mit eigenem Status).",
            example = "true")
        boolean canSetStatus) {}
