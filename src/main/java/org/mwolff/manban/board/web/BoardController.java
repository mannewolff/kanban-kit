package org.mwolff.manban.board.web;

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
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
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

/** Board-Verwaltung innerhalb eines Projekts. */
@Tag(
    name = "Boards",
    description =
        "Boards eines Projekts anlegen, lesen, umbenennen, archivieren, wiederherstellen und"
            + " endgültig löschen. Ein Board trägt geordnete Spalten, in denen die Karten liegen."
            + " Löschen archiviert zunächst nur; ein archiviertes Board gilt für die übrigen"
            + " Aufrufe als nicht vorhanden (404), bis es wiederhergestellt oder endgültig"
            + " gelöscht wird. Lesen verlangt die Mitgliedschaft im Projekt, Schreiben das"
            + " genannte Projekt-Recht; einem Nichtmitglied antwortet der Leitstand mit 404.")
@RestController
class BoardController {

  private final BoardService boards;

  private static final String BESCHREIBUNG_PROJECT_ID = "Interne ID des Projekts.";

  private static final String BESCHREIBUNG_BOARD_ID = "Interne ID des Boards.";

  private static final String UNGUELTIGER_NAME =
      "Ungültige Eingabe: name ist leer oder länger als 200 Zeichen (Details in fieldErrors).";

  private static final String PROJEKT_FEHLT =
      "Das Projekt gibt es nicht, oder der Aufrufer ist kein Mitglied.";

  private static final String BOARD_FEHLT =
      "Das Board gibt es nicht, es ist archiviert, oder der Aufrufer ist kein Mitglied seines"
          + " Projekts.";

  private static final String BOARD_FEHLT_AUCH_ARCHIVIERT =
      "Das Board gibt es nicht, auch nicht archiviert, oder der Aufrufer ist kein Mitglied"
          + " seines Projekts.";

  BoardController(BoardService boards) {
    this.boards = boards;
  }

  @Operation(
      summary = "Board anlegen",
      description = "Legt im Projekt ein Board mit den Standardspalten an. Recht: BOARD_CREATE.")
  @ApiResponse(responseCode = "201", description = "Das angelegte Board samt Spalten.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGER_NAME,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst BOARD_CREATE nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "BOARD_CREATE")
  @PostMapping("/api/projects/{projectId}/boards")
  @ResponseStatus(HttpStatus.CREATED)
  BoardView create(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable long projectId,
      @Valid @RequestBody BoardRequest request) {
    return boards.createBoard(userId, projectId, request.name());
  }

  @Operation(
      summary = "Boards eines Projekts auflisten",
      description =
          "Liefert die aktiven Boards des Projekts samt Spalten; archivierte liefert der Aufruf"
              + " /boards/archived. Verlangt die Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die aktiven Boards.")
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/boards")
  List<BoardView> list(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable
          long projectId) {
    return boards.listBoards(userId, projectId);
  }

  @Operation(
      summary = "Archivierte Boards eines Projekts auflisten",
      description =
          "Liefert die archivierten Boards des Projekts samt Spalten. Verlangt die"
              + " Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die archivierten Boards.")
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/boards/archived")
  List<BoardView> listArchived(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_PROJECT_ID, example = "1") @PathVariable
          long projectId) {
    return boards.listArchivedBoards(userId, projectId);
  }

  @Operation(
      summary = "Board lesen",
      description =
          "Liefert das Board samt seinen Spalten in ihrer Reihenfolge. Verlangt die"
              + " Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Das Board samt Spalten.")
  @ApiResponse(
      responseCode = "404",
      description = BOARD_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}")
  BoardView get(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId) {
    return boards.getBoard(userId, boardId);
  }

  @Operation(summary = "Board umbenennen", description = "Setzt den Namen des Boards.")
  @ApiResponse(responseCode = "200", description = "Das umbenannte Board.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGER_NAME,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst BOARD_UPDATE nicht.",
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
  @PatchMapping("/api/boards/{boardId}")
  BoardView rename(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId,
      @Valid @RequestBody BoardRequest request) {
    return boards.renameBoard(userId, boardId, request.name());
  }

  @Operation(
      summary = "Board archivieren",
      description =
          "Archiviert das Board, statt es zu löschen: Spalten und Karten bleiben erhalten, das"
              + " Board verschwindet aus der aktiven Liste und lässt sich wiederherstellen.")
  @ApiResponse(responseCode = "204", description = "Das Board ist archiviert.")
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst BOARD_DELETE nicht.",
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
  @ApiVertrag(recht = "BOARD_DELETE")
  @DeleteMapping("/api/boards/{boardId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId) {
    boards.deleteBoard(userId, boardId);
  }

  @Operation(
      summary = "Archiviertes Board wiederherstellen",
      description = "Hebt die Archivierung auf; das Board ist danach wieder aktiv.")
  @ApiResponse(responseCode = "200", description = "Das wiederhergestellte Board.")
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst BOARD_DELETE nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = BOARD_FEHLT_AUCH_ARCHIVIERT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "BOARD_DELETE")
  @PostMapping("/api/boards/{boardId}/restore")
  BoardView restore(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId) {
    return boards.restoreBoard(userId, boardId);
  }

  @Operation(
      summary = "Archiviertes Board endgültig löschen",
      description =
          "Löscht ein archiviertes Board unwiderruflich, samt Spalten, Karten und deren"
              + " Anhängen. Ein aktives Board muss zuerst archiviert werden.")
  @ApiResponse(responseCode = "204", description = "Das Board ist gelöscht.")
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst BOARD_DELETE nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = BOARD_FEHLT_AUCH_ARCHIVIERT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = "Das Board ist nicht archiviert.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "BOARD_DELETE")
  @DeleteMapping("/api/boards/{boardId}/purge")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void purge(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId) {
    boards.purgeBoard(userId, boardId);
  }

  @Schema(description = "Name eines Boards.")
  record BoardRequest(
      @Schema(description = "Name, höchstens 200 Zeichen.", example = "Entwicklung")
          @NotBlank
          @Size(max = 200)
          String name) {}
}
