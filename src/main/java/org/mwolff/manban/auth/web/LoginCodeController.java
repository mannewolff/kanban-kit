package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.mwolff.manban.auth.application.LoginService;
import org.mwolff.manban.auth.application.MeService;
import org.mwolff.manban.auth.application.MeService.MeView;
import org.mwolff.manban.auth.application.RequestLoginCodeService;
import org.mwolff.manban.auth.application.SessionTokens;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anmeldung per Einmalcode (Plan #1801, A4, A5, A6, A9, E6): Code anfordern, abgetippten Code
 * eingeben oder den Link aus der Mail einlösen. Angemeldet wird nur per {@code POST} — ein {@code
 * GET} auf den Link (etwa durch die Vorschau eines Mailprogramms) verbraucht nichts (A9).
 */
@Tag(name = "Anmeldung")
@RestController
@RequestMapping("/api/auth/code")
class LoginCodeController {

  private final RequestLoginCodeService requestCode;
  private final LoginService loginService;
  private final MeService meService;
  private final SessionTokens tokens;
  private final SessionCookieManager cookies;

  LoginCodeController(
      RequestLoginCodeService requestCode,
      LoginService loginService,
      MeService meService,
      SessionTokens tokens,
      SessionCookieManager cookies) {
    this.requestCode = requestCode;
    this.loginService = loginService;
    this.meService = meService;
    this.tokens = tokens;
    this.cookies = cookies;
  }

  /** Antwortet immer mit 202 — kein Rückschluss, ob es ein Konto zu der Adresse gibt (A5). */
  @Operation(
      summary = "Anmeldecode anfordern",
      description =
          "Ohne Anmeldung. Gibt es ein Konto zu der Adresse, erhält es eine Mail mit einem"
              + " sechsstelligen Code und einem Link; beide gelten zusammen nur einmal und nur"
              + " befristet, und eine neue Anforderung macht die vorige ungültig. Die Antwort ist"
              + " für jede Adresse dieselbe — registriert, unbekannt, gesperrt oder wartend —,"
              + " damit niemand so herausfindet, welche Adressen registriert sind. Je Adresse"
              + " gilt eine Grenze von Anforderungen je Stunde, für jede Adresse gleich gezählt.")
  @ApiResponse(
      responseCode = "202",
      description = "Angenommen — unabhängig davon, ob die Adresse registriert ist.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe: E-Mail leer, ungültig oder länger als 320 Zeichen (Details in"
              + " fieldErrors).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "429",
      description =
          "Zu viele Anforderungen für diese Adresse in der letzten Stunde. Der Hinweis ist für"
              + " registrierte und unbekannte Adressen derselbe.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping("/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void request(@Valid @RequestBody RequestCodeRequest request) {
    requestCode.requestCode(request.email());
  }

  @Operation(
      summary = "Mit Anmeldecode anmelden",
      description =
          "Ohne Anmeldung. Löst den abgetippten Code der jüngsten offenen Anforderung der"
              + " Adresse ein und setzt bei Erfolg das Session-Cookie dieses Geräts (Header"
              + " Set-Cookie, HttpOnly). Die Antwort ist dieselbe Selbstauskunft wie GET /api/me."
              + " Das erste Einlösen bestätigt die E-Mail-Adresse. Nach zu vielen falschen"
              + " Eingaben verfällt der Code.")
  @ApiResponse(
      responseCode = "200",
      description = "Angemeldet; die Selbstauskunft des Kontos, das Cookie steht im Header.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe: E-Mail leer oder ungültig, Code nicht sechsstellig (Details in"
              + " fieldErrors).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "401",
      description =
          "Code falsch, abgelaufen, schon eingelöst oder ersetzt, oder die Adresse ist"
              + " unbekannt. Die Antwort verrät nicht, welcher Fall vorliegt.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description =
          "Der Code stimmt, aber das Konto ist gesperrt oder wartet noch auf die Freigabe durch"
              + " einen Plattform-Admin.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping("/verify")
  MeView verify(@Valid @RequestBody VerifyCodeRequest request, HttpServletResponse response) {
    return signIn(loginService.loginWithCode(request.email(), request.code()), response);
  }

  @Operation(
      summary = "Mit dem Link aus der Mail anmelden",
      description =
          "Ohne Anmeldung. Löst das Token aus dem Link der Mail ein und setzt bei Erfolg das"
              + " Session-Cookie des Geräts, auf dem der Link geöffnet wurde (Header Set-Cookie,"
              + " HttpOnly) — nie das des Geräts, das den Code angefordert hat. Code und Link"
              + " gelten zusammen nur einmal. Die Antwort ist dieselbe Selbstauskunft wie"
              + " GET /api/me.")
  @ApiResponse(
      responseCode = "200",
      description = "Angemeldet; die Selbstauskunft des Kontos, das Cookie steht im Header.")
  @ApiResponse(
      responseCode = "400",
      description = "Ungültige Eingabe: Token leer oder zu lang (Details in fieldErrors).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "401",
      description =
          "Der Link ist unbekannt, abgelaufen, schon eingelöst oder durch eine neue Anforderung"
              + " ersetzt. Die Antwort verrät nicht, welcher Fall vorliegt.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description =
          "Der Link stimmt, aber das Konto ist gesperrt oder wartet noch auf die Freigabe durch"
              + " einen Plattform-Admin.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping("/link")
  MeView link(@Valid @RequestBody LinkLoginRequest request, HttpServletResponse response) {
    return signIn(loginService.loginWithLink(request.token()), response);
  }

  private MeView signIn(AppUser user, HttpServletResponse response) {
    long userId = user.requireId();
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(tokens.issue(userId)).toString());
    return meService.load(userId);
  }
}
