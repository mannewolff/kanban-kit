package org.mwolff.manban.accesstoken.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.accesstoken.application.AccessTokenService;
import org.mwolff.manban.accesstoken.application.AccessTokenService.AccessTokenView;
import org.mwolff.manban.accesstoken.application.AccessTokenService.AppZugangZeile;
import org.mwolff.manban.accesstoken.application.AccessTokenService.CreatedAccessToken;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verwaltung persönlicher API-Zugriffstokens. Nur per Cookie-Login erreichbar (SecurityConfig
 * verlangt {@code AUTH_SESSION}); nicht per PAT selbst (Least Privilege).
 */
@Tag(
    name = "API-Tokens",
    description =
        "Persönliche Zugriffs-Tokens anlegen, auflisten und widerrufen. Ein Token steht im Header"
            + " X-Kanban-Token und handelt im Namen seines Besitzers. Es gibt drei Arten: Ein"
            + " ungebundenes Token gilt für die übrige API unter /api/** wie eine Anmeldung. Ein"
            + " an ein Board gebundenes Token (Projekt-Token) gilt nur für das Kanban-kompatible"
            + " Einliefern unter /api/kanban/** und adressiert dort genau sein Board. Der Zugang"
            + " der App ist ein an ein Board gebundenes Token der Desktop-App: Je Board gibt es"
            + " höchstens einen wirksamen, verwaltet wird er unter /api/access-tokens/app."
            + " Verwaltet werden Tokens nur mit einer Anmeldung, nicht mit einem Token.")
@RestController
@RequestMapping("/api/access-tokens")
class AccessTokenController {

  private static final String KEINE_ROLLE =
      "Der Aufrufer ist Mitglied des Projekts, aber weder Admin noch Owner.";
  private static final String KEIN_ZUGANG =
      "Den Zugang der App gibt es nicht, oder der Aufrufer ist nicht Mitglied des Projekts.";

  private final AccessTokenService accessTokens;

  AccessTokenController(AccessTokenService accessTokens) {
    this.accessTokens = accessTokens;
  }

  /** Legt ein Token an; der Klartext wird hier GENAU EINMAL zurückgegeben. */
  @Operation(
      summary = "Token anlegen",
      description =
          "Legt ein Token an. Der Klartext steht nur in dieser einen Antwort — der Leitstand"
              + " speichert ihn nicht und kann ihn später nicht mehr zeigen. Ohne projectId und"
              + " boardId entsteht ein ungebundenes Token. Mit beiden entsteht ein an dieses"
              + " Board gebundenes Projekt-Token; dafür muss das Board zum Projekt gehören und"
              + " die Projekt-Rolle des Aufrufers TICKET_CREATE umfassen.")
  @ApiResponse(
      responseCode = "201",
      description = "Das angelegte Token mit seinem Klartext (nur dieses eine Mal).")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe: name leer oder länger als 120 Zeichen (Details in fieldErrors),"
              + " oder die Bindung ist unschlüssig: nur eines von projectId und boardId gesetzt,"
              + " das Board ist unbekannt, oder es gehört nicht zum Projekt.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description =
          "Für ein gebundenes Token: Der Aufrufer ist kein Mitglied des Projekts, oder seine"
              + " Projekt-Rolle umfasst TICKET_CREATE nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CreatedAccessToken create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateAccessTokenRequest request) {
    return accessTokens.create(userId, request.name(), request.projectId(), request.boardId());
  }

  @Operation(
      summary = "Eigene Tokens auflisten",
      description =
          "Liefert die Tokens des Aufrufers samt Bindung und Nutzung, ohne Klartext. Auch"
              + " widerrufene Tokens stehen in der Liste.")
  @ApiResponse(responseCode = "200", description = "Die Tokens des Aufrufers.")
  @GetMapping
  List<AccessTokenView> list(@AuthenticationPrincipal Long userId) {
    return accessTokens.list(userId);
  }

  @Operation(
      summary = "Token widerrufen",
      description =
          "Widerruft ein eigenes Token; danach weist der Leitstand es ab. Ein Widerruf ist"
              + " endgültig. Ein schon widerrufenes Token erneut zu widerrufen, ändert nichts.")
  @ApiResponse(responseCode = "204", description = "Das Token ist widerrufen.")
  @ApiResponse(
      responseCode = "404",
      description = "Das Token gibt es nicht, oder es gehört nicht dem Aufrufer.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revoke(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Tokens.", example = "12") @PathVariable long id) {
    accessTokens.revoke(userId, id);
  }

  @Operation(
      summary = "Zugänge der App auflisten",
      description =
          "Liefert je Board aller Projekte, in denen der Aufrufer selbst Admin oder Owner ist,"
              + " eine Zeile. tokenId ist null, solange das Board keinen wirksamen Zugang hat."
              + " eigener sagt, ob der Aufrufer Inhaber ist und erneuern darf. Ohne solche"
              + " Projekte ist die Liste leer; ein Plattform-Admin ohne Projektrolle hat kein"
              + " Sonderrecht. Der Klartext steht nie in dieser Liste.")
  @ApiResponse(responseCode = "200", description = "Je Board eine Zeile.")
  @ApiResponse(
      responseCode = "403",
      description = "Aufruf in einer laufenden Vorschau (Problem-Typ vorschau-persoenlich).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/app")
  List<AppZugangZeile> listApp(@AuthenticationPrincipal Long userId) {
    return accessTokens.appZugaengeAuflisten(userId);
  }

  @Operation(
      summary = "Zugang der App anlegen",
      description =
          "Legt den Zugang der App für ein Board an. Inhaber ist der Aufrufer, der Name ist fest"
              + " Desktop-App. Der Klartext steht nur in dieser einen Antwort. Verlangt die eigene"
              + " Rolle Admin oder Owner im Projekt; ein Plattform-Admin ohne Projektrolle wird"
              + " abgewiesen.")
  @ApiResponse(responseCode = "201", description = "Der Zugang mit seinem Klartext.")
  @ApiResponse(
      responseCode = "400",
      description = "projectId oder boardId fehlt, oder das Board gehört nicht zum Projekt.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = KEINE_ROLLE,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = "Der Aufrufer hat im Projekt keine eigene Mitgliedschaft.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = "Am Board besteht schon ein wirksamer Zugang; die Meldung nennt den Inhaber.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping("/app")
  @ResponseStatus(HttpStatus.CREATED)
  CreatedAccessToken createApp(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateAppZugangRequest request) {
    return accessTokens.appZugangAnlegen(userId, request.projectId(), request.boardId());
  }

  @Operation(
      summary = "Zugang der App erneuern",
      description =
          "Ersetzt den Zugang durch einen neuen. Der alte funktioniert sofort nicht mehr, der"
              + " Klartext des neuen steht nur in dieser einen Antwort. Erneuern darf nur der"
              + " Inhaber.")
  @ApiResponse(responseCode = "201", description = "Der neue Zugang mit seinem Klartext.")
  @ApiResponse(
      responseCode = "403",
      description = "Der Aufrufer ist Mitglied des Projekts, aber nicht Inhaber des Zugangs.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KEIN_ZUGANG + " Auch ein schon widerrufener Zugang lässt sich nicht erneuern.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping("/app/{id}/erneuern")
  @ResponseStatus(HttpStatus.CREATED)
  CreatedAccessToken renewApp(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Zugangs.", example = "12") @PathVariable long id) {
    return accessTokens.appZugangErneuern(userId, id);
  }

  @Operation(
      summary = "Zugang der App widerrufen",
      description =
          "Widerruft den Zugang endgültig. Das dürfen der Inhaber und jeder, der im Projekt selbst"
              + " Admin oder Owner ist. Danach kann für das Board sofort ein neuer Zugang angelegt"
              + " werden.")
  @ApiResponse(responseCode = "204", description = "Der Zugang ist widerrufen.")
  @ApiResponse(
      responseCode = "403",
      description = KEINE_ROLLE,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KEIN_ZUGANG,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @DeleteMapping("/app/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revokeApp(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Zugangs.", example = "12") @PathVariable long id) {
    accessTokens.appZugangWiderrufen(userId, id);
  }

  /**
   * {@code projectId}/{@code boardId} sind optional: sind beide gesetzt, wird das Token an dieses
   * Board gebunden (Kanban-Compat-API). Beide leer = ungebundenes Token.
   */
  @Schema(description = "Name und optionale Board-Bindung eines neuen Tokens.")
  record CreateAccessTokenRequest(
      @Schema(description = "Name zur Wiedererkennung, höchstens 120 Zeichen.", example = "Laptop")
          @NotBlank
          @Size(max = 120)
          String name,
      @Schema(
              description = "Projekt des gebundenen Boards; nur zusammen mit boardId.",
              example = "1")
          Long projectId,
      @Schema(
              description = "Board, an das das Token gebunden wird; nur zusammen mit projectId.",
              example = "3")
          Long boardId) {}

  /** Projekt und Board eines neuen Zugangs der App; der Name ist fest. */
  @Schema(description = "Projekt und Board eines neuen Zugangs der App.")
  record CreateAppZugangRequest(
      @Schema(description = "Projekt des Boards.", example = "1") @NotNull Long projectId,
      @Schema(description = "Board, für das der Zugang gilt.", example = "3") @NotNull
          Long boardId) {}
}
