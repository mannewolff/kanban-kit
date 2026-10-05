package org.mwolff.manban.comment.web;

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
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.CommentView;
import org.mwolff.manban.common.TextLimits;
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

/** Kommentare an Karten. */
@Tag(
    name = "Kommentare",
    description =
        "Kommentare an Karten anlegen, lesen, bearbeiten und löschen. Der Text ist Markdown."
            + " Bearbeiten darf nur der Autor selbst; Löschen ist Moderation und den"
            + " Projekt-Rollen OWNER und ADMIN vorbehalten.")
@RestController
class CommentController {

  private final CommentService comments;

  private static final String BESCHREIBUNG_CARD_ID = "Interne ID der Karte.";

  private static final String BESCHREIBUNG_COMMENT_ID = "Interne ID des Kommentars.";

  private static final String UNGUELTIGER_TEXT =
      "Ungültige Eingabe: body ist leer oder länger als "
          + TextLimits.MAX_TEXT
          + " Zeichen (Details in fieldErrors).";

  private static final String KARTE_FEHLT =
      "Die Karte gibt es nicht, oder der Aufrufer ist kein Mitglied ihres Projekts.";

  private static final String KOMMENTAR_FEHLT =
      "Den Kommentar gibt es nicht, oder der Aufrufer ist kein Mitglied des Projekts.";

  CommentController(CommentService comments) {
    this.comments = comments;
  }

  @Operation(
      summary = "Kommentar anlegen",
      description =
          "Hängt einen Kommentar an die Karte. Autor ist der Aufrufer; sein Anzeigename wird"
              + " mit dem Kommentar gespeichert.")
  @ApiResponse(responseCode = "201", description = "Der angelegte Kommentar.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGER_TEXT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst COMMENT_CREATE nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "COMMENT_CREATE")
  @PostMapping("/api/cards/{cardId}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  CommentView create(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @Valid @RequestBody CommentRequest request) {
    return comments.create(userId, cardId, request.body());
  }

  @Operation(
      summary = "Kommentare einer Karte lesen",
      description =
          "Liefert alle Kommentare der Karte. Jedes Mitglied des Projekts darf sie lesen.")
  @ApiResponse(responseCode = "200", description = "Die Kommentare der Karte.")
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/cards/{cardId}/comments")
  List<CommentView> list(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId) {
    return comments.list(userId, cardId);
  }

  @Operation(
      summary = "Kommentar bearbeiten",
      description =
          "Ersetzt den Text des Kommentars. Das darf nur sein Autor — auch OWNER und ADMIN"
              + " bearbeiten keine fremden Kommentare.")
  @ApiResponse(responseCode = "200", description = "Der geänderte Kommentar.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGER_TEXT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description =
          "Die Projekt-Rolle des Aufrufers umfasst COMMENT_UPDATE nicht, oder er ist nicht der"
              + " Autor des Kommentars.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KOMMENTAR_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "COMMENT_UPDATE")
  @PatchMapping("/api/comments/{commentId}")
  CommentView update(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_COMMENT_ID, example = "455") @PathVariable
          long commentId,
      @Valid @RequestBody CommentRequest request) {
    return comments.update(userId, commentId, request.body());
  }

  @Operation(
      summary = "Kommentar löschen",
      description =
          "Löscht den Kommentar. Das ist Moderation: Es verlangt COMMENT_DELETE (Rollen OWNER"
              + " und ADMIN); der Autor allein darf es nicht.")
  @ApiResponse(responseCode = "204", description = "Der Kommentar ist gelöscht.")
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst COMMENT_DELETE nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KOMMENTAR_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "COMMENT_DELETE")
  @DeleteMapping("/api/comments/{commentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_COMMENT_ID, example = "455") @PathVariable
          long commentId) {
    comments.delete(userId, commentId);
  }

  @Schema(description = "Text eines Kommentars.")
  record CommentRequest(
      @Schema(description = "Text in Markdown.", example = "Sieht gut aus, bitte noch testen.")
          @NotBlank
          @Size(max = TextLimits.MAX_TEXT)
          String body) {}
}
