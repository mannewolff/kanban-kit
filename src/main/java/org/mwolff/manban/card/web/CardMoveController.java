package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.card.application.CardMoveService;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.card.application.SortDirection;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verschieben, Status und Umzug (Issue #1395, Plan #1387 E12): Karten innerhalb eines Boards
 * verschieben, den Status eines Arbeitspakets setzen, eine Spalte nach Kartennummer ordnen und
 * Karten einzeln oder gesammelt auf ein anderes Board umziehen. Pfade, HTTP-Methoden, Rechte und
 * Antworten sind unverändert die aus {@link CardController}; delegiert wird an den {@link
 * CardMoveService}.
 */
@Tag(name = "Karten")
@RestController
class CardMoveController {

  private static final String BESCHREIBUNG_CARD_ID =
      "Interne ID der Karte (Feld id), nicht die projektweite Nummer.";

  private static final String UNGUELTIGE_EINGABE =
      "Ungültige Eingabe: ein Feld fehlt oder verletzt seine Grenzen (Details in fieldErrors).";

  private static final String VERBOTEN =
      "Die Projekt-Rolle des Aufrufers umfasst das verlangte Recht nicht.";

  private static final String KARTE_FEHLT =
      "Die Karte gibt es nicht, oder der Aufrufer ist kein Mitglied ihres Projekts.";

  private static final String UMZUG =
      "Liegt das Ziel-Board auf dem Board der Karte, ist das ein Spaltenwechsel wie beim"
          + " Verschieben. Im selben Projekt genügt CARD_MOVE; Nummer, Abhängigkeiten und"
          + " Zuständige bleiben. Über Projektgrenzen muss der Aufrufer in beiden Projekten"
          + " OWNER sein (Regel CARD_MOVE_PROJECT); die Karte erhält im Zielprojekt eine neue"
          + " Nummer, Abhängigkeiten und Zuständige entfallen. Bei jedem Board-Wechsel entfällt"
          + " die Zuordnung zu einem Vorhaben; Kommentare und Anhänge wandern mit.";

  private final CardMoveService moveService;

  CardMoveController(CardMoveService moveService) {
    this.moveService = moveService;
  }

  @Operation(
      summary = "Karte innerhalb des Boards verschieben",
      description =
          "Setzt eine Karte in eine Spalte desselben Boards an die angegebene Position (0 = oben);"
              + " die übrigen Karten rücken nach. Ein Spaltenwechsel steht im Verlauf der Karte."
              + " Vorhaben stehen in keiner Spalte und lassen sich nicht verschieben. Recht:"
              + " CARD_MOVE.")
  @ApiResponse(responseCode = "200", description = "Die verschobene Karte.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE + " Ebenso: die Karte ist ein Vorhaben.",
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
          KARTE_FEHLT + " Ebenso: die Spalte fehlt oder gehört nicht zum Board der Karte.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "CARD_MOVE")
  @PostMapping("/api/cards/{cardId}/move")
  CardView move(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @Valid @RequestBody MoveCardRequest request) {
    return moveService.move(userId, cardId, request.columnId(), request.position());
  }

  /**
   * Setzt den Status eines Arbeitspakets, ohne es zu verschieben (Issue #1300). Ein eigener
   * Endpunkt statt eines Felds am {@code PATCH}: Der Status hängt an {@code CARD_MOVE}, das Patch
   * an {@code TICKET_UPDATE} — zwei Rechte in einem Endpunkt öffneten still zu weit (Plan #1294,
   * E9).
   */
  @Operation(
      summary = "Status eines Arbeitspakets setzen",
      description =
          "Setzt den Status eines Arbeitspakets: BACKLOG, READY, IN_PROGRESS, IN_REVIEW oder DONE."
              + " Hat das Board eine Spalte dieses Status, wandert die Karte dorthin ans Ende;"
              + " sonst bleibt sie stehen und trägt nur den neuen Status. Ein Arbeitspaket ist eine"
              + " Karte, deren Titel nicht mit [Idee], [Fachlich] oder [Plan] beginnt; Vorhaben"
              + " und solche Dokumentkarten tragen keinen eigenen Status. Recht: CARD_MOVE.")
  @ApiResponse(responseCode = "204", description = "Der Status ist gesetzt.")
  @ApiResponse(
      responseCode = "400",
      description =
          UNGUELTIGE_EINGABE
              + " Ebenso: unbekannter Status, oder die Karte trägt keinen eigenen Status.",
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
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "CARD_MOVE")
  @PutMapping("/api/cards/{cardId}/status")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void setStatus(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @Valid @RequestBody SetStatusRequest request) {
    moveService.setStatus(userId, cardId, request.status());
  }

  /**
   * Ordnet die aktiven Karten einer Spalte nach Kartennummer. Die Richtung kommt bei jedem Aufruf
   * mit — das Backend merkt sich keinen Toggle-Zustand.
   */
  @Operation(
      summary = "Spalte nach Kartennummer ordnen",
      description =
          "Ordnet die aktiven Karten der Spalte nach ihrer projektweiten Nummer, ASC kleinste"
              + " zuerst, DESC größte zuerst. Archivierte Karten und der Papierkorb bleiben"
              + " unberührt; der Verlauf der Karten erhält keinen Eintrag. Recht: CARD_MOVE.")
  @ApiResponse(responseCode = "204", description = "Die Spalte ist geordnet.")
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
      description = "Die Spalte gibt es nicht, oder der Aufrufer ist kein Mitglied ihres Projekts.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "CARD_MOVE")
  @PostMapping("/api/columns/{columnId}/cards/sort-by-number")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void sortByNumber(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID der Spalte.", example = "17") @PathVariable
          long columnId,
      @Valid @RequestBody SortByNumberRequest request) {
    moveService.sortColumnByNumber(userId, columnId, request.direction());
  }

  @Operation(
      summary = "Karte auf ein anderes Board umziehen",
      description =
          "Verschiebt eine Karte in eine Spalte eines Boards; sie steht danach am Ende der"
              + " Zielspalte. Vorhaben ziehen nicht um.\n\n"
              + UMZUG)
  @ApiResponse(responseCode = "200", description = "Die umgezogene Karte.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE + " Ebenso: die Karte ist ein Vorhaben.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description =
          VERBOTEN + " Über Projektgrenzen: der Aufrufer ist nicht in beiden Projekten OWNER.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          KARTE_FEHLT
              + " Ebenso: das Ziel-Board fehlt, die Zielspalte gehört nicht zu ihm, oder der"
              + " Aufrufer ist kein Mitglied des Zielprojekts.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "CARD_MOVE")
  @PostMapping("/api/cards/{cardId}/transfer")
  CardView transfer(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @Valid @RequestBody TransferCardRequest request) {
    return moveService.transfer(userId, cardId, request.targetBoardId(), request.targetColumnId());
  }

  /** Verschiebt mehrere Karten in einer Transaktion auf ein anderes Board (alles-oder-nichts). */
  @Operation(
      summary = "Mehrere Karten auf ein anderes Board umziehen",
      description =
          "Zieht bis zu 200 Karten in einem Zug um, alles oder nichts: Scheitert eine Karte,"
              + " bleibt jede an ihrem Platz. Je Karte gelten die Regeln des Einzel-Umzugs.\n\n"
              + UMZUG)
  @ApiResponse(responseCode = "200", description = "Die umgezogenen Karten.")
  @ApiResponse(
      responseCode = "400",
      description =
          UNGUELTIGE_EINGABE
              + " Ebenso: eine leere Liste, mehr als 200 Karten, oder eine Karte ist ein"
              + " Vorhaben.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description =
          VERBOTEN + " Über Projektgrenzen: der Aufrufer ist nicht in beiden Projekten OWNER.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          "Eine der Karten, das Ziel-Board oder die Zielspalte gibt es nicht, oder der Aufrufer"
              + " ist kein Mitglied des jeweiligen Projekts.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "CARD_MOVE")
  @PostMapping("/api/cards/bulk-transfer")
  List<CardView> bulkTransfer(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkTransferRequest request) {
    return moveService.bulkTransfer(
        userId, request.cardIds(), request.targetBoardId(), request.targetColumnId());
  }

  @Schema(description = "Ziel innerhalb des Boards.")
  record MoveCardRequest(
      @Schema(description = "Interne ID der Zielspalte auf dem Board der Karte.", example = "17")
          @NotNull
          Long columnId,
      @Schema(description = "Position in der Zielspalte, 0 = oben.", example = "0")
          @jakarta.validation.constraints.PositiveOrZero
          int position) {}

  @Schema(description = "Der neue Status.")
  record SetStatusRequest(
      @Schema(
              description = "BACKLOG, READY, IN_PROGRESS, IN_REVIEW oder DONE.",
              example = "IN_REVIEW")
          @NotBlank
          String status) {}

  @Schema(description = "Ziel des Umzugs.")
  record TransferCardRequest(
      @Schema(description = "Interne ID des Ziel-Boards.", example = "4") @NotNull
          Long targetBoardId,
      @Schema(description = "Interne ID der Zielspalte auf dem Ziel-Board.", example = "21")
          @NotNull
          Long targetColumnId) {}

  @Schema(description = "Die Richtung der Ordnung.")
  record SortByNumberRequest(
      @Schema(description = "ASC kleinste Nummer zuerst, DESC größte zuerst.", example = "ASC")
          @NotNull
          SortDirection direction) {}

  @Schema(description = "Die Karten und das Ziel des Umzugs.")
  record BulkTransferRequest(
      @Schema(description = "Interne IDs der Karten, 1 bis 200.", example = "[812, 813]")
          @NotEmpty
          @Size(max = 200)
          List<Long> cardIds,
      @Schema(description = "Interne ID des Ziel-Boards.", example = "4") @NotNull
          Long targetBoardId,
      @Schema(description = "Interne ID der Zielspalte auf dem Ziel-Board.", example = "21")
          @NotNull
          Long targetColumnId) {}
}
