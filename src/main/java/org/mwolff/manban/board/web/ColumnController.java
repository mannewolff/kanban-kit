package org.mwolff.manban.board.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Spalten-Verwaltung eines Boards (anlegen, bearbeiten, umsortieren, löschen). */
@Tag(
    name = "Spalten",
    description =
        "Spalten eines Boards anlegen, umbenennen, umsortieren und löschen. Eine Spalte hat eine"
            + " Position (0 = ganz links) und optional ein WIP-Limit: die Zahl der Karten, die"
            + " höchstens gleichzeitig in ihr liegen sollen. Alle Aufrufe verlangen das"
            + " Projekt-Recht BOARD_UPDATE; einem Nichtmitglied antwortet der Leitstand mit 404.")
@RestController
class ColumnController {

  private final BoardService boards;

  private static final String BESCHREIBUNG_BOARD_ID = "Interne ID des Boards.";

  private static final String BESCHREIBUNG_COLUMN_ID = "Interne ID der Spalte.";

  private static final String UNGUELTIGE_SPALTE =
      "Ungültige Eingabe: name ist leer oder länger als 120 Zeichen, oder wipLimit ist nicht"
          + " positiv (Details in fieldErrors).";

  private static final String VERBOTEN =
      "Die Projekt-Rolle des Aufrufers umfasst BOARD_UPDATE nicht.";

  private static final String BOARD_FEHLT =
      "Das Board gibt es nicht, es ist archiviert, oder der Aufrufer ist kein Mitglied seines"
          + " Projekts.";

  private static final String SPALTE_FEHLT =
      "Die Spalte gibt es nicht, ihr Board ist archiviert, oder der Aufrufer ist kein Mitglied"
          + " des Projekts.";

  ColumnController(BoardService boards) {
    this.boards = boards;
  }

  @Operation(
      summary = "Spalte anlegen",
      description = "Hängt eine Spalte rechts an die Spalten des Boards an.")
  @ApiResponse(responseCode = "201", description = "Die angelegte Spalte.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_SPALTE,
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
  @PostMapping("/api/boards/{boardId}/columns")
  @ResponseStatus(HttpStatus.CREATED)
  ColumnView add(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId,
      @Valid @RequestBody ColumnRequest request) {
    return boards.addColumn(userId, boardId, request.name(), request.wipLimit());
  }

  @Operation(
      summary = "Spalte ändern",
      description =
          "Setzt Name und WIP-Limit der Spalte. Ohne wipLimit (oder mit null) hat die Spalte"
              + " danach kein Limit.")
  @ApiResponse(responseCode = "200", description = "Die geänderte Spalte.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_SPALTE,
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
      description = SPALTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "BOARD_UPDATE")
  @PatchMapping("/api/columns/{columnId}")
  ColumnView update(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_COLUMN_ID, example = "17") @PathVariable long columnId,
      @Valid @RequestBody ColumnRequest request) {
    return boards.updateColumn(userId, columnId, request.name(), request.wipLimit());
  }

  @Operation(
      summary = "Spalte löschen",
      description = "Löscht eine leere Spalte. Liegen noch Karten darin, bleibt sie bestehen.")
  @ApiResponse(responseCode = "204", description = "Die Spalte ist gelöscht.")
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = SPALTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = "In der Spalte liegen noch Karten.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "BOARD_UPDATE")
  @DeleteMapping("/api/columns/{columnId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_COLUMN_ID, example = "17") @PathVariable
          long columnId) {
    boards.deleteColumn(userId, columnId);
  }

  @Operation(
      summary = "Spalten umsortieren",
      description =
          "Ordnet die Spalten des Boards neu. columnIds nennt alle Spalten des Boards genau"
              + " einmal, in der gewünschten Reihenfolge von links nach rechts.")
  @ApiResponse(
      responseCode = "200",
      description = "Die Spalten in der neuen Reihenfolge mit ihren neuen Positionen.")
  @ApiResponse(
      responseCode = "400",
      description = "Ungültige Eingabe: columnIds fehlt oder ist leer (Details in fieldErrors).",
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
      description =
          BOARD_FEHLT
              + " Ebenso: columnIds nennt nicht genau die Spalten des Boards (eine fehlt, ist"
              + " fremd oder doppelt).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "BOARD_UPDATE")
  @PutMapping("/api/boards/{boardId}/columns/order")
  List<ColumnView> reorder(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId,
      @Valid @RequestBody ReorderRequest request) {
    return boards.reorderColumns(userId, boardId, request.columnIds());
  }

  @Schema(description = "Name und WIP-Limit einer Spalte.")
  record ColumnRequest(
      @Schema(description = "Name, höchstens 120 Zeichen.", example = "In Arbeit")
          @NotBlank
          @Size(max = 120)
          String name,
      @Schema(
              description =
                  "WIP-Limit: höchstens so viele Karten sollen gleichzeitig in der Spalte liegen;"
                      + " null heißt kein Limit.",
              example = "3")
          @Positive
          Integer wipLimit) {}

  @Schema(description = "Neue Reihenfolge der Spalten eines Boards.")
  record ReorderRequest(
      @Schema(
              description = "IDs aller Spalten des Boards, von links nach rechts.",
              example = "[17, 19, 18, 20]")
          @NotEmpty
          List<Long> columnIds) {}
}
