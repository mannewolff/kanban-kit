package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.mwolff.manban.auth.application.RequestPasswordResetService;
import org.mwolff.manban.auth.application.ResetPasswordService;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Öffentliche Endpunkte für den Passwort-Reset. */
@Tag(name = "Anmeldung")
@RestController
@RequestMapping("/api/auth")
class PasswordResetController {

  private final RequestPasswordResetService requestReset;
  private final ResetPasswordService resetPassword;
  private final SessionCookieManager cookies;

  PasswordResetController(
      RequestPasswordResetService requestReset,
      ResetPasswordService resetPassword,
      SessionCookieManager cookies) {
    this.requestReset = requestReset;
    this.resetPassword = resetPassword;
    this.cookies = cookies;
  }

  /** Antwortet immer mit 200 — kein Rückschluss, ob die E-Mail existiert. */
  @Operation(
      summary = "Passwort-Reset anfordern",
      description =
          "Ohne Anmeldung. Gibt es ein Konto zu der Adresse, erhält es eine Mail mit einem"
              + " Link zum Zurücksetzen, dessen Token nur einmal und nur befristet gilt. Die"
              + " Antwort ist in beiden Fällen dieselbe, damit niemand so herausfindet, welche"
              + " Adressen registriert sind. Der Erfolg bestätigt das angelegte Token, nicht die"
              + " Zustellung der Mail.")
  @ApiResponse(
      responseCode = "200",
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
  @PostMapping("/forgot")
  @ResponseStatus(HttpStatus.OK)
  void forgot(@Valid @RequestBody ForgotPasswordRequest request) {
    requestReset.requestReset(request.email());
  }

  /**
   * Setzt das Passwort neu und beendet damit alle Sitzungen des Kontos. Das Session-Cookie des
   * auslösenden Geräts wird zusätzlich gelöscht — seine Sitzung ist mit dem Reset ebenfalls
   * beendet, und ohne Löschung schickte dieser Browser weiterhin ein totes Cookie mit (Issue #886).
   */
  @Operation(
      summary = "Passwort zurücksetzen",
      description =
          "Ohne Anmeldung. Setzt mit dem Token aus der Reset-Mail ein neues Passwort und"
              + " verbraucht das Token. Der Reset beendet alle Sitzungen des Kontos auf allen"
              + " Geräten; das Session-Cookie dieses Browsers wird zusätzlich gelöscht (Header"
              + " Set-Cookie). Danach meldet man sich mit dem neuen Passwort neu an.")
  @ApiResponse(responseCode = "204", description = "Das Passwort ist neu gesetzt.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe (Token leer, Passwort kürzer als 8 oder länger als 200 Zeichen,"
              + " Details in fieldErrors), oder das Token ist unbekannt, abgelaufen oder schon"
              + " eingelöst.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping("/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void reset(@Valid @RequestBody ResetPasswordRequest request, HttpServletResponse response) {
    resetPassword.reset(request.token(), request.newPassword());
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
  }
}
