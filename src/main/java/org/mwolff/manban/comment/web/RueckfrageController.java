package org.mwolff.manban.comment.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.comment.application.RueckfrageService;
import org.mwolff.manban.comment.application.RueckfrageService.AntwortAuftrag;
import org.mwolff.manban.comment.application.RueckfrageService.EinzelAuftrag;
import org.mwolff.manban.comment.application.RueckfrageService.RueckfrageView;
import org.mwolff.manban.common.TextLimits;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rückfragen der KI an Karten lesen und beantworten (Issue #2210, Plan #2206 E8, E11). */
@Tag(
    name = "Rückfragen",
    description =
        "Rückfragen der KI an einer Karte mit dem Stand je Frage lesen und per Klick"
            + " beantworten. Die Antwort steht danach als Kommentar an der Karte.")
@RestController
class RueckfrageController {

  private static final String BESCHREIBUNG_CARD_ID = "Interne ID der Karte.";

  private static final String KARTE_FEHLT =
      "Die Karte gibt es nicht, oder der Aufrufer ist kein Mitglied ihres Projekts.";

  private final RueckfrageService rueckfragen;

  RueckfrageController(RueckfrageService rueckfragen) {
    this.rueckfragen = rueckfragen;
  }

  @Operation(
      summary = "Rückfragen einer Karte lesen",
      description =
          "Liefert die Rückfragen der Karte mit ihren Fragen, Möglichkeiten und dem Stand je"
              + " Frage. Zählen nur Antworten von Menschen; eine Freigabe-Rückfrage, für deren"
              + " Projekt eine neuere Vorschau gemeldet ist, steht als UEBERHOLT. Jedes Mitglied"
              + " des Projekts darf sie lesen.")
  @ApiResponse(responseCode = "200", description = "Die Rückfragen der Karte.")
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/cards/{cardId}/rueckfragen")
  List<RueckfrageView> liste(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId) {
    return rueckfragen.liste(userId, cardId);
  }

  @Operation(
      summary = "Rückfrage beantworten",
      description =
          "Schreibt die Antwort als Kommentar mit dem Kopf „Antwort“ (in der Sprache der"
              + " Rückfrage) an die Karte und liefert den Stand der Rückfrage danach. ja"
              + " übernimmt alle Empfehlungen, antworten nennt Abweichungen oder einzelne"
              + " Antworten, freigeben gibt den Stand einer Freigabe-Rückfrage frei.")
  @ApiResponse(responseCode = "201", description = "Der Stand der Rückfrage nach der Antwort.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Die Antwort passt nicht zur Rückfrage: unbekannte Frage, nicht genau eine Angabe je"
              + " Frage, nicht angebotene Möglichkeit oder Weg, Freigabe ohne Freigabe-Rückfrage,"
              + " keine Angabe oder zu lang.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description =
          "Die Projekt-Rolle des Aufrufers umfasst COMMENT_CREATE nicht, oder er antwortet über"
              + " einen gewöhnlichen Projekt-Zugang.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          "Die Karte gibt es nicht, der Aufrufer ist kein Mitglied ihres Projekts, oder an der"
              + " Karte steht keine Rückfrage mit dieser ID.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description =
          "freigeben an einer überholten Rückfrage: Für das Projekt ist eine neuere Vorschau"
              + " gemeldet.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "COMMENT_CREATE")
  @PostMapping("/api/cards/{cardId}/rueckfragen/{kommentarId}/antwort")
  @ResponseStatus(HttpStatus.CREATED)
  RueckfrageView antworten(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @Parameter(description = "Interne ID des Kommentars der Rückfrage.", example = "455")
          @PathVariable
          long kommentarId,
      @Valid @RequestBody AntwortRequest request) {
    List<EinzelantwortRequest> einzeln = request.antworten();
    List<EinzelAuftrag> antworten =
        einzeln == null
            ? List.of()
            : einzeln.stream()
                .map(a -> new EinzelAuftrag(a.nr(), a.wahl(), a.text(), a.zurueckstellen()))
                .toList();
    return rueckfragen.antworten(
        userId,
        cardId,
        kommentarId,
        new AntwortAuftrag(
            Boolean.TRUE.equals(request.ja()),
            antworten,
            request.weg(),
            Boolean.TRUE.equals(request.freigeben())));
  }

  @Schema(description = "Die Antwort auf eine Rückfrage.")
  record AntwortRequest(
      @Schema(description = "Alle Empfehlungen übernehmen; antworten nennt dann Abweichungen.")
          @Nullable Boolean ja,
      @Schema(description = "Einzelne Antworten; fehlt wie leer.") @Valid @Size(max = 200)
          @Nullable List<@NotNull @Valid EinzelantwortRequest> antworten,
      @Schema(description = "Nummer des gewählten Wegs.", example = "1") @Nullable Integer weg,
      @Schema(description = "Den Stand einer Freigabe-Rückfrage freigeben.")
          @Nullable Boolean freigeben) {}

  @Schema(
      description = "Die Antwort auf eine Frage: genau eines von wahl, text und zurueckstellen.")
  record EinzelantwortRequest(
      @Schema(description = "Nummer der Frage.", example = "1") @NotNull Integer nr,
      @Schema(description = "Nummer der gewählten Möglichkeit, ab 1.", example = "2")
          @Nullable Integer wahl,
      @Schema(description = "Eigene Antwort.", example = "Lieber grün.")
          @Size(max = TextLimits.MAX_TEXT)
          @Nullable String text,
      @Schema(description = "Frage zurückstellen („Später“).") @Nullable Boolean zurueckstellen) {}
}
