package org.mwolff.manban.fremdboard.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.common.web.api.Stabilitaet;
import org.mwolff.manban.fremdboard.application.AnstossService;
import org.mwolff.manban.fremdboard.application.KeinFremdesBoardException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anstoß des Kits nach einem eigenen Schritt im fremden Board (Issue #2167, Plan #2152 A6, A16):
 * kanban-kit liest die genannte Karte sofort aus dem fremden System. Angemeldet wird mit dem an das
 * Spiegel-Board gebundenen Projekt-Token über die Strecke {@code /api/kanban/**} und damit unter
 * ihrer Durchsatzbremse; das Projekt kommt aus der Bindung des Tokens, nicht aus dem Aufruf.
 *
 * <p>Der Aufruf ist verlässlich, weil das Kit ihn benutzt (Plan #1400, E4).
 */
@Tag(name = "Fremdes Board")
@RestController
class FremdBoardAnstossController {

  private static final String PROBLEM = ApiSchemas.PROBLEM_JSON;

  private final AnstossService service;

  FremdBoardAnstossController(AnstossService service) {
    this.service = service;
  }

  @Operation(
      summary = "Karte aus dem fremden Board sofort lesen",
      description =
          """
          Das Kit ruft diesen Aufruf nach jedem eigenen Schritt an einer Karte im fremden \
          Board (Verschieben, Label, Kommentar) mit der Nummer der Karte. kanban-kit liest \
          die Karte daraufhin sofort aus dem fremden System und übernimmt jeden neuen \
          Wechsel mit dem Zeitpunkt von dort; jeder Wechsel zählt genau einmal, gleich wie oft \
          angestoßen wird. Es gilt immer der Stand im fremden System.

          Angemeldet wird mit dem an das Board gebundenen Projekt-Token (Header \
          X-Kanban-Token); das Projekt kommt aus der Bindung. Höchstens ein Abruf je Karte \
          in 10 Sekunden: Ein weiterer Anstoß derselben Karte in dieser Zeit antwortet mit \
          202 und liest nichts.""")
  @ApiResponse(responseCode = "200", description = "Die Karte ist gelesen und abgeglichen.")
  @ApiResponse(
      responseCode = "202",
      description =
          "Angenommen, aber nicht gelesen: Die Karte wurde in den letzten 10 Sekunden schon"
              + " gelesen.")
  @ApiResponse(
      responseCode = "400",
      description = "Ungültige Eingabe: die Nummer fehlt oder ist nicht positiv.",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          "Das Projekt gibt es nicht, oder die Person hinter dem Token ist kein Mitglied.",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Das Token ist an kein Board gebunden, oder das Board des Projekts liegt in kanban-kit.",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "502",
      description = "Das fremde System hat nicht oder nicht lesbar geantwortet; nichts übernommen.",
      content =
          @Content(mediaType = PROBLEM, schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @PostMapping("/api/kanban/fremdboard/anstoss")
  ResponseEntity<AnstossResponse> anstossen(
      @Nullable Authentication authentication, @Valid @RequestBody AnstossRequest request) {
    KanbanPrincipal principal = gebunden(authentication);
    boolean abgeglichen =
        service.anstossen(
            principal.userId(), Objects.requireNonNull(principal.projectId()), request.nummer());
    return ResponseEntity.status(abgeglichen ? HttpStatus.OK : HttpStatus.ACCEPTED)
        .body(new AnstossResponse(abgeglichen));
  }

  /** Der Principal eines gebundenen Tokens; ohne Bindung gibt es kein Board, also 409. */
  private static KanbanPrincipal gebunden(@Nullable Authentication authentication) {
    if (authentication != null
        && authentication.getDetails() instanceof KanbanPrincipal principal
        && principal.isBound()) {
      return principal;
    }
    throw new KeinFremdesBoardException();
  }

  /**
   * Der Anstoß.
   *
   * @param nummer Nummer der Karte im fremden System, zugleich ihre Kartennummer
   */
  @Schema(name = "FremdBoardAnstossRequest", description = "Welche Karte gelesen werden soll.")
  record AnstossRequest(
      @Schema(
              description = "Nummer der Karte im fremden System (bei GitLab die iid).",
              example = "42",
              requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull
          @Positive
          Integer nummer) {}

  /**
   * Die Antwort.
   *
   * @param abgeglichen ob gelesen wurde
   */
  @Schema(name = "FremdBoardAnstossResponse", description = "Ob die Karte gelesen wurde.")
  record AnstossResponse(
      @Schema(
              description =
                  "true: gelesen und abgeglichen (200). false: in den letzten 10 Sekunden schon"
                      + " gelesen, nichts getan (202).",
              requiredMode = Schema.RequiredMode.REQUIRED)
          boolean abgeglichen) {}
}
