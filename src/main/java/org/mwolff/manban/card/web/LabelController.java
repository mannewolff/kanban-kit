package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.LabelService;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verwaltung der board-scoped Labels (Anlegen/Umbenennen/Farbe/Löschen; Auflisten je Mitglied).
 *
 * <p>{@code POST} und {@code PATCH} teilen sich {@link LabelRequest}. Das Feld {@code
 * countOnEpicTile} wird beim Anlegen ausdrücklich verworfen: Neue Labels starten immer mit {@code
 * false} und werden anschließend über ihre Bestandszeile markiert (Entscheidung Manne, 2026-08-31).
 */
@Tag(
    name = "Labels",
    description =
        "Labels eines Boards anlegen, umbenennen, umfärben, löschen und auflisten. Ein Label"
            + " gehört genau einem Board; Karten tragen Labels dieses Boards. Zuordnen und Abnehmen"
            + " an einer Karte geht über die Karten-Aufrufe. Freigabe-Labels sind die Labels, mit"
            + " denen das claude-workflow-kit Karten für seinen unbeaufsichtigten Betrieb freigibt"
            + " oder zurückhält (kit:night, kit:nightrun, kit:klaeren, kit:geschuetzt); ihre"
            + " Definition ändert nur ein Mensch im Board, nicht ein Projekt-Token.")
@RestController
class LabelController {

  private final LabelService labels;

  private static final String BESCHREIBUNG_BOARD_ID = "Interne ID des Boards.";

  private static final String BESCHREIBUNG_LABEL_ID = "Interne ID des Labels.";

  private static final String UNGUELTIGE_EINGABE =
      "Ungültige Eingabe: ein Feld verletzt seine Grenzen (Details in fieldErrors).";

  private static final String VERBOTEN =
      "Die Projekt-Rolle des Aufrufers umfasst BOARD_UPDATE nicht, oder ein Projekt-Token will"
          + " die Definition eines Freigabe-Labels ändern.";

  private static final String BOARD_FEHLT =
      "Das Board gibt es nicht, es ist archiviert, oder der Aufrufer ist kein Mitglied seines"
          + " Projekts.";

  private static final String LABEL_FEHLT =
      "Das Label gibt es nicht, oder der Aufrufer ist kein Mitglied des Projekts seines Boards.";

  LabelController(LabelService labels) {
    this.labels = labels;
  }

  @Operation(
      summary = "Labels eines Boards auflisten",
      description = "Liefert alle Labels des Boards. Verlangt die Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Labels des Boards.")
  @ApiResponse(
      responseCode = "404",
      description = BOARD_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/labels")
  List<LabelView> list(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId) {
    return labels.list(userId, boardId).stream().map(LabelController::view).toList();
  }

  @Operation(
      summary = "Label anlegen",
      description =
          "Legt ein Label mit Name und Farbe am Board an. countOnEpicTile wird beim Anlegen"
              + " verworfen; ein neues Label startet immer mit false und wird danach per PATCH"
              + " markiert.")
  @ApiResponse(responseCode = "201", description = "Das angelegte Label.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE + " Ebenso: der Name ist leer oder am Board schon vergeben.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = BOARD_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "BOARD_UPDATE")
  @PostMapping("/api/boards/{boardId}/labels")
  @ResponseStatus(HttpStatus.CREATED)
  LabelView create(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId,
      @Valid @RequestBody LabelRequest request) {
    return view(labels.create(userId, boardId, request.name(), request.color()));
  }

  @Operation(
      summary = "Label ändern",
      description =
          "Setzt Name und Farbe des Labels. countOnEpicTile ist dreiwertig: fehlt das Feld oder"
              + " ist es null, bleibt der gespeicherte Wert; true oder false setzen ihn. Ein Label"
              + " mit countOnEpicTile=true wird auf der Kachel eines Vorhabens mitgezählt.")
  @ApiResponse(responseCode = "200", description = "Das geänderte Label.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE + " Ebenso: der Name ist leer oder am Board schon vergeben.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = LABEL_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "BOARD_UPDATE")
  @PatchMapping("/api/labels/{labelId}")
  LabelView update(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_LABEL_ID, example = "41") @PathVariable long labelId,
      @Valid @RequestBody LabelRequest request) {
    return view(
        labels.update(userId, labelId, request.name(), request.color(), request.countOnEpicTile()));
  }

  @Operation(
      summary = "Label löschen",
      description = "Löscht das Label; die Karten des Boards verlieren damit diese Zuordnung.")
  @ApiResponse(responseCode = "204", description = "Das Label ist gelöscht.")
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = LABEL_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "BOARD_UPDATE")
  @DeleteMapping("/api/labels/{labelId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_LABEL_ID, example = "41") @PathVariable long labelId) {
    labels.delete(userId, labelId);
  }

  private static LabelView view(Label l) {
    return new LabelView(l.requireId(), l.boardId(), l.name(), l.color(), l.countOnEpicTile());
  }

  @Schema(description = "Ein Label eines Boards.")
  record LabelView(
      @Schema(description = "Interne ID des Labels.", example = "41") long id,
      @Schema(description = "Interne ID des Boards.", example = "3") long boardId,
      @Schema(description = "Name, je Board eindeutig.", example = "kit:nightrun") String name,
      @Schema(description = "Farbe als CSS-Farbwert.", example = "#b87333") String color,
      @Schema(description = "Ob das Label auf der Kachel eines Vorhabens mitgezählt wird.")
          boolean countOnEpicTile) {}

  /**
   * Gemeinsamer Request von {@code POST} und {@code PATCH}.
   *
   * @param countOnEpicTile nur beim {@code PATCH} wirksam und dort dreiwertig: {@code null} lässt
   *     den gespeicherten Wert stehen, {@code true}/{@code false} setzen ihn. Beim Anlegen wird das
   *     Feld verworfen — neue Labels starten immer mit {@code false} (Issue #659).
   */
  @Schema(description = "Name und Farbe eines Labels; beim Ändern zusätzlich countOnEpicTile.")
  record LabelRequest(
      @Schema(description = "Name, höchstens 60 Zeichen.", example = "Bug")
          @NotBlank
          @Size(max = 60)
          String name,
      @Schema(description = "Farbe als CSS-Farbwert, höchstens 20 Zeichen.", example = "#c0392b")
          @NotBlank
          @Size(max = 20)
          String color,
      @Schema(
              description =
                  "Nur beim Ändern wirksam: null lässt den Wert stehen, true oder false setzen"
                      + " ihn.")
          @Nullable Boolean countOnEpicTile) {}
}
