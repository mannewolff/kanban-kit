package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardNumbers;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.card.application.EpicService;
import org.mwolff.manban.card.application.EpicService.DerivationNodeView;
import org.mwolff.manban.card.application.EpicService.EpicView;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vorhaben und Herkunft (Issue #1393, Plan #1387 E12): Vorhaben eines Boards mit Fortschritt, der
 * Herkunftsbaum eines Vorhabens, die Zuordnung einer Karte zu einem Vorhaben, ihre Herkunft, die
 * Anforderungskarte und das Eröffnen eines Vorgangs. Pfade, HTTP-Methoden, Rechte und Antworten
 * sind unverändert die aus {@link CardController}; delegiert wird an den {@link EpicService}.
 */
@Tag(name = "Karten")
@RestController
class EpicController {

  private static final String BESCHREIBUNG_BOARD_ID = "Interne ID des Boards.";

  private static final String BESCHREIBUNG_CARD_ID =
      "Interne ID der Karte (Feld id), nicht die projektweite Nummer.";

  private static final String UNGUELTIGE_EINGABE =
      "Ungültige Eingabe: ein Feld fehlt oder verletzt seine Grenzen (Details in fieldErrors).";

  private static final String VERBOTEN =
      "Die Projekt-Rolle des Aufrufers umfasst das verlangte Recht nicht.";

  private static final String BOARD_FEHLT =
      "Das Board gibt es nicht, oder der Aufrufer ist kein Mitglied seines Projekts.";

  private static final String KARTE_FEHLT =
      "Die Karte gibt es nicht, oder der Aufrufer ist kein Mitglied ihres Projekts.";

  private final EpicService epicService;

  EpicController(EpicService epicService) {
    this.epicService = epicService;
  }

  @Operation(
      summary = "Vorhaben eines Boards mit Fortschritt lesen",
      description =
          "Liefert die Vorhaben des Boards mit ihrem Fortschritt. Zu einem Vorhaben gehören die"
              + " ihm direkt zugeordneten Karten (rootNumbers) und alle, die über ihre Herkunft"
              + " (derivedFrom) von diesen abstammen (memberNumbers); done zählt davon die"
              + " erledigten, total alle. requirementCardNumber ist die Anforderungskarte des"
              + " Vorhabens. Verlangt die Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Vorhaben des Boards.")
  @ApiResponse(
      responseCode = "404",
      description = BOARD_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/epics")
  List<EpicView> epics(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId) {
    return epicService.listEpics(userId, boardId);
  }

  /**
   * Herkunftsbaum eines Vorhabens — dieselbe Rechnung wie beim board-weiten Baum, angewandt auf die
   * Mitglieder dieses Vorhabens (Issue #643).
   *
   * <p>Der Pfad endet bewusst auf {@code /tree} und wiederholt den Pfadbestandteil des board-weiten
   * Endpunkts darüber nicht: Der kommt im Controller genau einmal vor, und daran bleibt sein
   * Rückbau maschinell prüfbar.
   */
  @Operation(
      summary = "Herkunftsbaum eines Vorhabens lesen",
      description =
          "Liefert die Karten des Vorhabens als Baum ihrer Herkunft, flach in Baumreihenfolge:"
              + " depth ist die Tiefe, derivedFrom die Nummer der Herkunftskarte. blocked heißt,"
              + " eine Abhängigkeit innerhalb des Vorhabens ist offen; externalDependencies nennt"
              + " Abhängigkeiten auf Karten außerhalb des Boards, externalOrigin eine Herkunft"
              + " außerhalb. Verlangt die Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Zeilen des Baums.")
  @ApiResponse(
      responseCode = "404",
      description = BOARD_FEHLT + " Ebenso: epicId bezeichnet kein Vorhaben dieses Boards.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/epics/{epicId}/tree")
  List<DerivationNodeView> epicTree(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId,
      @Parameter(description = "Interne ID des Vorhabens.", example = "640") @PathVariable
          long epicId) {
    return epicService.epicDerivationTree(userId, boardId, epicId);
  }

  /**
   * Ordnet eine Karte einem Vorhaben zu ({@code parentId}) oder löst die Zuordnung ({@code
   * parentId: null}).
   */
  @Operation(
      summary = "Karte einem Vorhaben zuordnen",
      description =
          "Ordnet die Karte dem Vorhaben parentId desselben Boards zu; parentId: null löst die"
              + " Zuordnung. Nur Karten lassen sich zuordnen, keine Vorhaben. Recht:"
              + " TICKET_UPDATE.")
  @ApiResponse(responseCode = "200", description = "Die Karte mit ihrer neuen Zuordnung.")
  @ApiResponse(
      responseCode = "400",
      description = "Die Karte ist ein Vorhaben, oder parentId ist kein Vorhaben dieses Boards.",
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
      description = KARTE_FEHLT + " Ebenso: das Vorhaben parentId gibt es nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "TICKET_UPDATE")
  @PatchMapping("/api/cards/{cardId}/parent")
  CardView assignParent(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @RequestBody AssignParentRequest request) {
    return epicService.assignParent(userId, cardId, request.parentId());
  }

  /**
   * Setzt die Herkunft einer Karte ({@code derivedFrom} als projektweite Kartennummer) oder löscht
   * sie ({@code derivedFrom: null}).
   *
   * <p>Eigener Endpunkt statt eines Feldes in {@link CardController.UpdateCardRequest}: Jener Pfad
   * ist ein Voll-Update, und ein fehlendes JSON-Feld ist in einem Jackson-Record nicht von {@code
   * null} zu unterscheiden — jeder bestehende Client haette die Herkunft bei jedem Karten-Edit
   * geloescht (Issue #607).
   */
  @Operation(
      summary = "Herkunft einer Karte setzen",
      description =
          "Setzt die Herkunft der Karte: derivedFrom ist die projektweite Nummer der Karte, aus"
              + " der sie abgeleitet ist, etwa der Plan eines Arbeitspakets; derivedFrom: null"
              + " löscht sie. Ein eigener Aufruf, damit das Bearbeiten einer Karte die Herkunft"
              + " nicht versehentlich löscht. Recht: TICKET_UPDATE für Karten, EPIC_UPDATE für"
              + " Vorhaben.")
  @ApiResponse(responseCode = "200", description = "Die Karte mit ihrer neuen Herkunft.")
  @ApiResponse(
      responseCode = "400",
      description =
          UNGUELTIGE_EINGABE
              + " Ebenso: unbekannte Nummer, Verweis auf sich selbst, oder die Herkunft bildete"
              + " einen Zyklus.",
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
  @ApiVertrag(recht = "TICKET_UPDATE")
  @PatchMapping("/api/cards/{cardId}/derived-from")
  CardView assignDerivedFrom(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @Valid @RequestBody AssignDerivedFromRequest request) {
    return epicService.assignDerivedFrom(userId, cardId, request.derivedFrom());
  }

  /**
   * Eröffnet einen Vorgang an dieser Karte: Vorhaben anlegen, Karte als Anforderung setzen und ihr
   * zuordnen — in einem Aufruf. Kartenzentriert wie {@code move}, {@code transfer} und {@code
   * archive}; die Antwort ist die Sicht des <b>neuen Vorhabens</b>.
   */
  @Operation(
      summary = "Vorgang an einer Karte eröffnen",
      description =
          "Legt in einem Zug ein Vorhaben auf dem Board der Karte an, setzt die Karte als seine"
              + " Anforderung und ordnet sie ihm zu. Das Vorhaben bleibt ohne Beschreibung; den"
              + " Inhalt trägt die Anforderungskarte. Recht: EPIC_CREATE.")
  @ApiResponse(responseCode = "201", description = "Das neue Vorhaben.")
  @ApiResponse(
      responseCode = "400",
      description =
          UNGUELTIGE_EINGABE
              + " Ebenso: die Karte ist selbst ein Vorhaben, ist archiviert oder liegt im"
              + " Papierkorb, oder sie ist schon einem Vorhaben zugeordnet.",
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
  @ApiVertrag(recht = "EPIC_CREATE")
  @PostMapping("/api/cards/{cardId}/open-epic")
  @ResponseStatus(HttpStatus.CREATED)
  CardView openEpic(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @Valid @RequestBody OpenEpicRequest request) {
    return epicService.openEpicFromCard(userId, cardId, request.kuerzel(), request.name());
  }

  /**
   * Setzt oder löscht die Anforderungskarte eines Vorhabens.
   *
   * <p>Schmaler Endpunkt wie {@code derived-from} (#607): Ein Voll-Update kann ein fehlendes Feld
   * nicht von {@code null} unterscheiden und löschte die Zuordnung bei jedem Karten-Edit. Übergabe
   * von {@code null} löscht sie ausdrücklich.
   */
  @Operation(
      summary = "Anforderungskarte eines Vorhabens setzen",
      description =
          "Setzt die Anforderungskarte des Vorhabens: requirementCardNumber ist die projektweite"
              + " Nummer einer Karte auf demselben Board; null löscht die Zuordnung. Recht:"
              + " EPIC_UPDATE.")
  @ApiResponse(responseCode = "200", description = "Das Vorhaben mit seiner Anforderung.")
  @ApiResponse(
      responseCode = "400",
      description =
          UNGUELTIGE_EINGABE
              + " Ebenso: die Karte ist kein Vorhaben, die Nummer ist unbekannt, verweist auf"
              + " das Vorhaben selbst oder auf eine Karte eines anderen Boards.",
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
  @ApiVertrag(recht = "EPIC_UPDATE")
  @PatchMapping("/api/cards/{cardId}/requirement")
  CardView assignRequirement(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "640") @PathVariable long cardId,
      @Valid @RequestBody AssignRequirementRequest request) {
    return epicService.assignRequirement(userId, cardId, request.requirementCardNumber());
  }

  @Schema(description = "Das Vorhaben, dem die Karte angehören soll.")
  record AssignParentRequest(
      @Schema(description = "Interne ID des Vorhabens; null löst die Zuordnung.", example = "640")
          Long parentId) {}

  // Dieselben Grenzen wie im kanbancompat-Ingest (`CreateItemRequest`), damit beide Schreibpfade
  // dieselbe Nummer akzeptieren und dieselbe ablehnen.
  @Schema(description = "Die Herkunft der Karte.")
  record AssignDerivedFromRequest(
      @Schema(
              description = "Projektweite Nummer der Herkunftskarte; null löscht die Herkunft.",
              example = "1400")
          @Nullable
          @Positive
          @Max(CardNumbers.MAX)
          Integer derivedFrom) {}

  @Schema(description = "Die Anforderungskarte des Vorhabens.")
  record AssignRequirementRequest(
      @Schema(
              description = "Projektweite Nummer der Anforderungskarte; null löscht sie.",
              example = "1365")
          @Nullable
          @Positive
          @Max(CardNumbers.MAX)
          Integer requirementCardNumber) {}

  @Schema(description = "Das neue Vorhaben.")
  record OpenEpicRequest(
      @Schema(description = "Optionales Kürzel des Vorhabens.", example = "API")
          @Nullable String kuerzel,
      @Schema(description = "Name des Vorhabens.", example = "API-Übersicht") @NotBlank
          String name) {}
}
