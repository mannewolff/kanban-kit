package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.card.application.CardArchiveService;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Archiv und Papierkorb (Issue #1394, Plan #1387 E12): Karten archivieren und wiederherstellen,
 * einzeln und gesammelt in den Papierkorb legen, daraus zurückholen, endgültig entfernen und den
 * Papierkorb eines Boards auflisten. Pfade, HTTP-Methoden, Rechte und Antworten sind unverändert
 * die aus {@link CardController}; delegiert wird an den {@link CardArchiveService}.
 */
@Tag(name = "Karten")
@RestController
class CardArchiveController {

  private static final String BESCHREIBUNG_CARD_ID =
      "Interne ID der Karte (Feld id), nicht die projektweite Nummer.";

  private static final String RECHT_LOESCHEN =
      " Recht: TICKET_DELETE für Karten, EPIC_DELETE für Vorhaben.";

  private static final String UNGUELTIGE_EINGABE =
      "Ungültige Eingabe: eine leere Liste oder mehr als 200 Karten (Details in fieldErrors).";

  private static final String VERBOTEN =
      "Die Projekt-Rolle des Aufrufers umfasst das verlangte Recht nicht.";

  private static final String KARTE_FEHLT =
      "Die Karte gibt es nicht, oder der Aufrufer ist kein Mitglied ihres Projekts.";

  private static final String STAPEL_FEHLT =
      "Eine der Karten gibt es nicht, oder der Aufrufer ist kein Mitglied ihres Projekts. Der"
          + " ganze Stapel bleibt dann unverändert.";

  private final CardArchiveService archiveService;

  CardArchiveController(CardArchiveService archiveService) {
    this.archiveService = archiveService;
  }

  @Operation(
      summary = "Karte archivieren",
      description =
          "Archiviert eine Karte oder ein Vorhaben: Sie verlässt die Spalte, bleibt aber mit"
              + " Nummer, Kommentaren und Anhängen erhalten und lässt sich wiederherstellen."
              + RECHT_LOESCHEN)
  @ApiResponse(responseCode = "200", description = "Die archivierte Karte.")
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
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
  @ApiVertrag(recht = "TICKET_DELETE")
  @PostMapping("/api/cards/{cardId}/archive")
  CardView archive(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId) {
    return archiveService.archive(userId, cardId);
  }

  /** Archiviert mehrere Karten in einer Transaktion (alles-oder-nichts). */
  @Operation(
      summary = "Mehrere Karten archivieren",
      description =
          "Archiviert bis zu 200 Karten in einem Zug, alles oder nichts: Fehlt an einer Karte das"
              + " Recht oder gibt es sie nicht, bleibt keine archiviert."
              + RECHT_LOESCHEN)
  @ApiResponse(responseCode = "200", description = "Die archivierten Karten.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE,
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
      description = STAPEL_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "TICKET_DELETE")
  @PostMapping("/api/cards/bulk-archive")
  List<CardView> bulkArchive(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkArchiveRequest request) {
    return archiveService.bulkArchive(userId, request.cardIds());
  }

  @Operation(
      summary = "Archivierte Karte wiederherstellen",
      description =
          "Holt eine archivierte Karte zurück; sie steht danach am Ende ihrer Spalte."
              + RECHT_LOESCHEN)
  @ApiResponse(responseCode = "200", description = "Die wiederhergestellte Karte.")
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
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
  @ApiVertrag(recht = "TICKET_DELETE")
  @PostMapping("/api/cards/{cardId}/restore")
  CardView restore(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId) {
    return archiveService.restore(userId, cardId);
  }

  /** Verschiebt eine Karte in den Papierkorb (Soft-Delete, reversibel). */
  @Operation(
      summary = "Karte in den Papierkorb legen",
      description =
          "Legt eine Karte oder ein Vorhaben in den Papierkorb des Boards; von dort lässt sie sich"
              + " zurückholen. Bei einem Vorhaben werden die ihm zugeordneten Karten gelöst, sie"
              + " selbst bleiben stehen."
              + RECHT_LOESCHEN)
  @ApiResponse(responseCode = "204", description = "Die Karte liegt im Papierkorb.")
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
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
  @ApiVertrag(recht = "TICKET_DELETE")
  @DeleteMapping("/api/cards/{cardId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId) {
    archiveService.delete(userId, cardId);
  }

  /** Verschiebt mehrere Karten in einer Transaktion in den Papierkorb (alles-oder-nichts). */
  @Operation(
      summary = "Mehrere Karten in den Papierkorb legen",
      description =
          "Legt bis zu 200 Karten in einem Zug in den Papierkorb, alles oder nichts: Fehlt an"
              + " einer Karte das Recht oder gibt es sie nicht, bleibt jede an ihrem Platz."
              + RECHT_LOESCHEN)
  @ApiResponse(responseCode = "204", description = "Die Karten liegen im Papierkorb.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE,
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
      description = STAPEL_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "TICKET_DELETE")
  @PostMapping("/api/cards/bulk-delete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void bulkDelete(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkDeleteRequest request) {
    archiveService.bulkDelete(userId, request.cardIds());
  }

  /** Papierkorb eines Boards. */
  @Operation(
      summary = "Papierkorb eines Boards lesen",
      description =
          "Liefert die Karten im Papierkorb des Boards. Verlangt die Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Karten im Papierkorb.")
  @ApiResponse(
      responseCode = "404",
      description = "Das Board gibt es nicht, oder der Aufrufer ist kein Mitglied seines Projekts.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/trash")
  List<CardView> trash(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Boards.", example = "3") @PathVariable
          long boardId) {
    return archiveService.listTrash(userId, boardId);
  }

  /** Holt eine Karte aus dem Papierkorb zurück. */
  @Operation(
      summary = "Karte aus dem Papierkorb zurückholen",
      description =
          "Holt eine Karte aus dem Papierkorb zurück; sie steht danach am Ende ihrer Spalte."
              + RECHT_LOESCHEN)
  @ApiResponse(responseCode = "200", description = "Die zurückgeholte Karte.")
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
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
  @ApiVertrag(recht = "TICKET_DELETE")
  @PostMapping("/api/cards/{cardId}/restore-deleted")
  CardView restoreDeleted(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId) {
    return archiveService.restoreFromTrash(userId, cardId);
  }

  /** Entfernt eine Karte endgültig (nur Projekt-Admin/Owner). */
  @Operation(
      summary = "Karte endgültig entfernen",
      description =
          "Entfernt eine Karte samt Kommentaren, Anhängen und Abhängigkeiten endgültig; das lässt"
              + " sich nicht zurücknehmen. Recht: BOARD_DELETE, bewusst strenger als das Ablegen"
              + " im Papierkorb.")
  @ApiResponse(responseCode = "204", description = "Die Karte ist entfernt.")
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
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
  @ApiVertrag(recht = "BOARD_DELETE")
  @DeleteMapping("/api/cards/{cardId}/purge")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void purge(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId) {
    archiveService.purge(userId, cardId);
  }

  @Schema(description = "Die Karten, die archiviert werden.")
  record BulkArchiveRequest(
      @Schema(description = "Interne IDs der Karten, 1 bis 200.", example = "[812, 813]")
          @NotEmpty
          @Size(max = 200)
          List<Long> cardIds) {}

  @Schema(description = "Die Karten, die in den Papierkorb gelegt werden.")
  record BulkDeleteRequest(
      @Schema(description = "Interne IDs der Karten, 1 bis 200.", example = "[812, 813]")
          @NotEmpty
          @Size(max = 200)
          List<Long> cardIds) {}
}
