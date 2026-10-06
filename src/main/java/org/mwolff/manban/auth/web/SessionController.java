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

/** Login/Logout: setzt bzw. löscht das signierte Session-Cookie. */
@Tag(name = "Anmeldung")
@RestController
@RequestMapping("/api/auth")
class SessionController {

  private final LoginService loginService;
  private final MeService meService;
  private final SessionTokens tokens;
  private final SessionCookieManager cookies;

  SessionController(
      LoginService loginService,
      MeService meService,
      SessionTokens tokens,
      SessionCookieManager cookies) {
    this.loginService = loginService;
    this.meService = meService;
    this.tokens = tokens;
    this.cookies = cookies;
  }

  @Operation(
      summary = "Anmelden",
      description =
          "Ohne Anmeldung. Prüft E-Mail-Adresse und Passwort und setzt bei Erfolg das"
              + " Session-Cookie (Header Set-Cookie, HttpOnly). Die Antwort ist dieselbe"
              + " Selbstauskunft wie GET /api/me. Angemeldet wird nur ein Konto, das nicht"
              + " gesperrt ist, dessen Adresse bestätigt ist und das freigegeben ist; solange es"
              + " noch keinen Plattform-Admin gibt, entfällt die Freigabe, damit sich der erste"
              + " Nutzer per Bootstrap zum Admin erheben kann.")
  @ApiResponse(
      responseCode = "200",
      description = "Angemeldet; die Selbstauskunft des Kontos, das Cookie steht im Header.")
  @ApiResponse(
      responseCode = "400",
      description = "Ungültige Eingabe: E-Mail oder Passwort leer (Details in fieldErrors).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "401",
      description =
          "E-Mail-Adresse oder Passwort falsch. Die Antwort verrät nicht, welches von beiden.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description =
          "Die Anmeldedaten stimmen, aber das Konto ist gesperrt, seine E-Mail-Adresse ist"
              + " noch nicht bestätigt, oder es wartet noch auf die Freigabe durch einen"
              + " Plattform-Admin.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping("/login")
  MeView login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    AppUser user = loginService.login(request.email(), request.password());
    long userId = user.requireId();
    String token = tokens.issue(userId);
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(token).toString());
    return meService.load(userId);
  }

  @Operation(
      summary = "Abmelden",
      description =
          "Ohne Anmeldung aufrufbar. Löscht das Session-Cookie im Browser (Header Set-Cookie)."
              + " Gelingt immer, auch ohne bestehende Anmeldung.")
  @ApiResponse(responseCode = "204", description = "Das Session-Cookie ist gelöscht.")
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
  }
}
