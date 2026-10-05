package org.mwolff.manban.attachment.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import org.mwolff.manban.attachment.application.AttachmentService;
import org.mwolff.manban.attachment.application.AttachmentService.AttachmentView;
import org.mwolff.manban.attachment.application.AttachmentService.Download;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Karten-Anhänge: Upload (multipart), Download (immer als Attachment), Liste, Löschen. */
@Tag(
    name = "Anhänge",
    description =
        "Dateien an Karten hochladen, auflisten, herunterladen und löschen. Die Datei liegt im"
            + " Objektspeicher, der Leitstand hält nur ihre Metadaten. Den Medientyp bestimmt der"
            + " Leitstand selbst aus dem Inhalt der Datei, nicht aus der Angabe des Aufrufers."
            + " Je Karte gibt es eine Höchstzahl an Anhängen (Vorgabe 20).")
@RestController
class AttachmentController {

  private final AttachmentService attachments;

  private static final String BESCHREIBUNG_CARD_ID = "Interne ID der Karte.";

  private static final String BESCHREIBUNG_ATTACHMENT_ID = "Interne ID des Anhangs.";

  private static final String KARTE_FEHLT =
      "Die Karte gibt es nicht, oder der Aufrufer ist kein Mitglied ihres Projekts.";

  private static final String ANHANG_FEHLT =
      "Den Anhang gibt es nicht, oder der Aufrufer ist kein Mitglied des Projekts.";

  AttachmentController(AttachmentService attachments) {
    this.attachments = attachments;
  }

  @Operation(
      summary = "Anhang hochladen",
      description =
          "Lädt eine Datei als multipart/form-data im Feld file hoch und hängt sie an die Karte."
              + " Der Dateiname kommt aus dem Feld; fehlt er, heißt die Datei datei. Die Größe"
              + " einer Datei begrenzt der Server (Spring-Einstellung"
              + " spring.servlet.multipart.max-file-size).")
  @ApiResponse(responseCode = "201", description = "Die Metadaten des angelegten Anhangs.")
  @ApiResponse(
      responseCode = "400",
      description = "Der Teil file fehlt, oder die Anfrage ist kein multipart/form-data.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst ATTACHMENT_CREATE nicht.",
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
  @ApiResponse(
      responseCode = "409",
      description = "Die Karte hat schon die Höchstzahl an Anhängen.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "413",
      description = "Die Datei überschreitet die Größengrenze des Servers.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "ATTACHMENT_CREATE")
  @PostMapping("/api/cards/{cardId}/attachments")
  @ResponseStatus(HttpStatus.CREATED)
  AttachmentView upload(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @Parameter(description = "Die Datei; ihr Dateiname wird übernommen.") @RequestParam("file")
          MultipartFile file)
      throws IOException {
    String filename = file.getOriginalFilename() == null ? "datei" : file.getOriginalFilename();
    return attachments.upload(userId, cardId, filename, file.getBytes());
  }

  @Operation(
      summary = "Anhänge einer Karte auflisten",
      description =
          "Liefert die Metadaten aller Anhänge der Karte, ohne Inhalt. Jedes Mitglied des"
              + " Projekts darf sie lesen.")
  @ApiResponse(responseCode = "200", description = "Die Anhänge der Karte.")
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/cards/{cardId}/attachments")
  List<AttachmentView> list(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId) {
    return attachments.list(userId, cardId);
  }

  /**
   * Download. Immer {@code Content-Disposition: attachment} — getarnte HTML/SVG werden so
   * heruntergeladen statt inline gerendert (Anti-XSS).
   */
  @Operation(
      summary = "Anhang herunterladen",
      description =
          "Liefert den Inhalt der Datei. Der Medientyp der Antwort ist der beim Hochladen"
              + " erkannte (etwa image/png oder application/pdf). Die Antwort trägt immer"
              + " Content-Disposition: attachment, auch bei Bildern und PDFs: Ein Browser lädt"
              + " die Datei herunter, statt sie anzuzeigen — so wird eine als Bild getarnte"
              + " HTML- oder SVG-Datei nie im Leitstand ausgeführt. Jedes Mitglied des Projekts"
              + " darf herunterladen.")
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
  @GetMapping("/api/attachments/{id}")
  ResponseEntity<InputStreamResource> download(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_ATTACHMENT_ID, example = "91") @PathVariable long id) {
    Download download = attachments.download(userId, id);
    ContentDisposition disposition =
        ContentDisposition.attachment().filename(download.filename()).build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(MediaType.parseMediaType(download.contentType()))
        .contentLength(download.size())
        .body(new InputStreamResource(download.content()));
  }

  @Operation(
      summary = "Anhang löschen",
      description =
          "Löscht den Anhang. Die Metadaten verschwinden sofort; die Datei im Objektspeicher"
              + " entfernt der Leitstand kurz danach im Hintergrund.")
  @ApiResponse(responseCode = "204", description = "Der Anhang ist gelöscht.")
  @ApiResponse(
      responseCode = "403",
      description = "Die Projekt-Rolle des Aufrufers umfasst ATTACHMENT_DELETE nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = ANHANG_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "ATTACHMENT_DELETE")
  @DeleteMapping("/api/attachments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_ATTACHMENT_ID, example = "91") @PathVariable long id) {
    attachments.delete(userId, id);
  }
}
