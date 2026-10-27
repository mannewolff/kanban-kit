package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.mwolff.manban.auth.application.AdminVorschauService;
import org.mwolff.manban.auth.application.VorschauKontext;
import org.mwolff.manban.auth.application.VorschauKontextLeser;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.mwolff.manban.auth.web.security.VorschauCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Abmelden: löscht das signierte Session-Cookie. Angemeldet wird per Einmalcode im {@link
 * LoginCodeController}; die Anmeldung mit Passwort ist entfallen (Plan #1801, A13).
 *
 * <p>Läuft in der Anmeldung eine Vorschau „Ansicht als XY“, endet sie mit (Issue #1921, Plan #1914
 * E9): mit dem Grund {@code ABMELDUNG}, und beide Cookies werden gelöscht.
 */
@Tag(name = "Anmeldung")
@RestController
@RequestMapping("/api/auth")
class SessionController {

  private final SessionCookieManager cookies;
  private final AdminVorschauService vorschauen;
  private final VorschauKontextLeser kontext;

  SessionController(
      SessionCookieManager cookies, AdminVorschauService vorschauen, VorschauKontextLeser kontext) {
    this.cookies = cookies;
    this.vorschauen = vorschauen;
    this.kontext = kontext;
  }

  @Operation(
      summary = "Abmelden",
      description =
          "Ohne Anmeldung aufrufbar. Löscht das Session-Cookie und das Cookie einer Vorschau"
              + " im Browser (Header Set-Cookie); eine laufende Vorschau endet damit. Gelingt"
              + " immer, auch ohne bestehende Anmeldung.")
  @ApiResponse(responseCode = "204", description = "Beide Cookies sind gelöscht.")
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(HttpServletRequest request, HttpServletResponse response) {
    Optional<VorschauKontext> laufend = kontext.aktuell();
    Optional<VorschauCookie> cookie = cookies.readVorschau(request);
    if (laufend.isPresent() && cookie.isPresent()) {
      vorschauen.beendeBeimAbmelden(
          cookie.get().kennung(), cookie.get().geheimnis(), laufend.get().adminId());
    }
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearVorschau().toString());
  }
}
