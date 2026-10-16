package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Abmelden: löscht das signierte Session-Cookie. Angemeldet wird per Einmalcode im {@link
 * LoginCodeController}; die Anmeldung mit Passwort ist entfallen (Plan #1801, A13).
 */
@Tag(name = "Anmeldung")
@RestController
@RequestMapping("/api/auth")
class SessionController {

  private final SessionCookieManager cookies;

  SessionController(SessionCookieManager cookies) {
    this.cookies = cookies;
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
