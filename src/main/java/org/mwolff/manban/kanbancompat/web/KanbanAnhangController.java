package org.mwolff.manban.kanbancompat.web;

import static org.mwolff.manban.kanbancompat.web.KanbanAuthentifizierung.BESCHREIBUNG_ID;
import static org.mwolff.manban.kanbancompat.web.KanbanAuthentifizierung.NICHT_GEBUNDEN;
import static org.mwolff.manban.kanbancompat.web.KanbanAuthentifizierung.TAG_BESCHREIBUNG;
import static org.mwolff.manban.kanbancompat.web.KanbanAuthentifizierung.TAG_NAME;
import static org.mwolff.manban.kanbancompat.web.KanbanAuthentifizierung.TOKEN_BINDUNG;
import static org.mwolff.manban.kanbancompat.web.KanbanAuthentifizierung.principal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.attachment.application.AttachmentService.AttachmentView;
import org.mwolff.manban.attachment.application.AttachmentService.Download;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.common.web.api.Stabilitaet;
import org.mwolff.manban.kanbancompat.application.KanbanAnhangService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anhänge einer Karte über das board-gebundene Projekt-Token lesen (Issue #1699, Plan #1694).
 *
 * <p>Nur lesende Aufrufe: Hochladen und Löschen über das Token gibt es unter {@code /api/kanban}
 * nicht, sie bleiben dem angemeldeten Menschen an {@code /api/cards/**} und {@code
 * /api/attachments/**} vorbehalten. Beide Aufrufe sind {@code VERLAESSLICH}, weil das Kit darauf
 * ein Kommando baut; der Vertragsschnappschuss in {@code OpenApiIT} macht jede Änderung sichtbar.
 */
@Tag(name = TAG_NAME, description = TAG_BESCHREIBUNG)
@RestController
@RequestMapping("/api/kanban")
class KanbanAnhangController {

  private static final String BESCHREIBUNG_ANHANG_ID =
      "Interne ID des Anhangs (Feld id aus GET /api/kanban/items/{id}/attachments).";

  private static final String UNERREICHBAR =
      " Eine Karte oder ein Anhang außerhalb des gebundenen Boards, eine Karte im Papierkorb und"
          + " ein gelöschter Anhang ergeben dieselbe Antwort 404 wie ein Anhang, den es nicht"
          + " gibt: Das Token erfährt nicht, ob er existiert.";

  private static final String ANHANG_FEHLT =
      "Anhang nicht gefunden — einheitlich für eine unbekannte oder unerreichbare Karte und einen"
          + " unbekannten oder unerreichbaren Anhang.";

  private final KanbanAnhangService service;

  KanbanAnhangController(KanbanAnhangService service) {
    this.service = service;
  }

  @Operation(
      summary = "Anhänge einer Karte auflisten",
      description =
          "Liefert die Metadaten aller Anhänge einer Karte des gebundenen Boards, ohne Inhalt:"
              + " je Anhang id, cardId, filename, contentType, size und createdAt — dieselben"
              + " Angaben wie GET /api/cards/{cardId}/attachments für den angemeldeten Menschen."
              + " Eine eigene Erlaubnis braucht es nicht, jedes gültige board-gebundene Token darf"
              + " lesen."
              + UNERREICHBAR
              + " "
              + TOKEN_BINDUNG)
  @ApiResponse(responseCode = "200", description = "Die Anhänge der Karte.")
  @ApiResponse(
      responseCode = "404",
      description = ANHANG_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @GetMapping("/items/{id}/attachments")
  List<AttachmentView> list(
      @Nullable Authentication authentication,
      @Parameter(description = BESCHREIBUNG_ID, example = "4711") @PathVariable long id) {
    return service.list(principal(authentication), id);
  }

  /**
   * Download. Immer {@code Content-Disposition: attachment} wie in {@code
   * AttachmentController.download} (A7) — getarnte HTML/SVG werden heruntergeladen statt gerendert.
   */
  @Operation(
      summary = "Anhang einer Karte herunterladen",
      description =
          "Liefert den Inhalt eines Anhangs einer Karte des gebundenen Boards, Byte für Byte wie"
              + " hochgeladen. Der Medientyp der Antwort ist der beim Hochladen erkannte; die"
              + " Antwort trägt immer Content-Disposition: attachment mit dem gespeicherten"
              + " Dateinamen. Der Anhang muss zur genannten Karte gehören."
              + UNERREICHBAR
              + " "
              + TOKEN_BINDUNG)
  @ApiResponse(
      responseCode = "200",
      description = "Der Inhalt der Datei.",
      headers = {
        @Header(
            name = HttpHeaders.CONTENT_DISPOSITION,
            description = "Immer attachment, mit dem gespeicherten Dateinamen.",
            schema = @Schema(type = "string", example = "attachment; filename=\"skizze.png\"")),
        @Header(
            name = HttpHeaders.CONTENT_LENGTH,
            description = "Größe der Datei in Byte.",
            schema = @Schema(type = "integer", format = "int64", example = "48213"))
      },
      content =
          @Content(
              mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
              schema = @Schema(type = "string", format = "binary")))
  @ApiResponse(
      responseCode = "404",
      description = ANHANG_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @GetMapping("/items/{id}/attachments/{attachmentId}")
  ResponseEntity<InputStreamResource> download(
      @Nullable Authentication authentication,
      @Parameter(description = BESCHREIBUNG_ID, example = "4711") @PathVariable long id,
      @Parameter(description = BESCHREIBUNG_ANHANG_ID, example = "91") @PathVariable
          long attachmentId) {
    Download download = service.download(principal(authentication), id, attachmentId);
    ContentDisposition disposition =
        ContentDisposition.attachment().filename(download.filename()).build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(MediaType.parseMediaType(download.contentType()))
        .contentLength(download.size())
        .body(new InputStreamResource(download.content()));
  }
}
