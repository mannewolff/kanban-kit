package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.mwolff.manban.auth.application.RegisterUserService;
import org.mwolff.manban.auth.application.VerifyEmailService;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Öffentliche Auth-Endpunkte für Registrierung und E-Mail-Verifikation. */
@Tag(
    name = "Anmeldung",
    description =
        "Registrierung, E-Mail-Bestätigung, Anmelden und Abmelden sowie das Zurücksetzen des"
            + " Passworts. Diese Aufrufe gehen ohne Anmeldung. Eine erfolgreiche Anmeldung setzt"
            + " ein signiertes Session-Cookie (HttpOnly), mit dem der Browser alle weiteren"
            + " Aufrufe stellt.")
@RestController
@RequestMapping("/api/auth")
class AuthController {

  private final RegisterUserService registerUser;
  private final VerifyEmailService verifyEmail;

  AuthController(RegisterUserService registerUser, VerifyEmailService verifyEmail) {
    this.registerUser = registerUser;
    this.verifyEmail = verifyEmail;
  }

  @Operation(
      summary = "Konto registrieren",
      description =
          "Ohne Anmeldung. Braucht nur E-Mail-Adresse und Anzeigename. Legt ein Konto mit"
              + " unbestätigter E-Mail-Adresse an und fordert im selben Zug einen Anmeldecode an"
              + " (wie POST /api/auth/code/request, zählt auf dieselbe Grenze je Adresse); eine"
              + " eigene Bestätigungsmail gibt es nicht. Die erste Anmeldung mit dem Code bestätigt"
              + " die Adresse. Die Adresse wird klein geschrieben gespeichert. Angemeldet wird das"
              + " Konto — außer bei per Einladung registrierten Adressen — erst nach der Freigabe"
              + " durch einen Plattform-Admin. Der Erfolg bestätigt die angelegte Registrierung,"
              + " nicht die Zustellung der Mail.")
  @ApiResponse(
      responseCode = "201",
      description = "Das angelegte, noch unbestätigte Konto; die Mail mit dem Code ist vorgemerkt.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Ungültige Eingabe: E-Mail leer, ungültig oder länger als 320 Zeichen, Anzeigename"
              + " leer oder länger als 120 Zeichen, ein mitgeschicktes Passwort kürzer als 8 oder"
              + " länger als 200 Zeichen (Details in fieldErrors).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = "Die E-Mail-Adresse ist schon registriert.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "429",
      description =
          "Für diese Adresse wurden in der letzten Stunde schon zu viele Codes angefordert; es"
              + " wird kein Konto angelegt.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  RegisteredUserResponse register(@Valid @RequestBody RegisterRequest request) {
    AppUser user =
        registerUser.register(request.email(), request.password(), request.displayName());
    return new RegisteredUserResponse(user.requireId(), user.email(), user.emailVerified());
  }

  @Operation(
      summary = "E-Mail-Adresse bestätigen",
      description =
          "Ohne Anmeldung. Löst das Token aus dem Bestätigungslink ein und markiert die"
              + " E-Mail-Adresse als bestätigt. Das Token gilt genau einmal und nur bis zu seinem"
              + " Ablauf. Wartet das Konto danach noch auf Freigabe, erhalten die Plattform-Admins"
              + " eine Benachrichtigung.")
  @ApiResponse(responseCode = "200", description = "Die E-Mail-Adresse ist bestätigt.")
  @ApiResponse(
      responseCode = "400",
      description = "Das Token fehlt, ist unbekannt, abgelaufen oder schon eingelöst.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/verify")
  @ResponseStatus(HttpStatus.OK)
  void verify(
      @Parameter(description = "Token aus dem Bestätigungslink der Mail.") @RequestParam("token")
          String token) {
    verifyEmail.verify(token);
  }

  @Schema(description = "Ein neu registriertes Konto.")
  record RegisteredUserResponse(
      @Schema(description = "Interne ID des Kontos.", example = "7") Long id,
      @Schema(description = "E-Mail-Adresse, klein geschrieben.", example = "ada@example.org")
          String email,
      @Schema(description = "Ob die Adresse bestätigt ist; nach der Registrierung false.")
          boolean emailVerified) {}
}
