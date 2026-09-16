package org.mwolff.manban.auth.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.mwolff.manban.auth.application.RequestPasswordResetService;
import org.mwolff.manban.auth.application.ResetPasswordService;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Öffentliche Endpunkte für den Passwort-Reset. */
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
  @PostMapping("/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void reset(@Valid @RequestBody ResetPasswordRequest request, HttpServletResponse response) {
    resetPassword.reset(request.token(), request.newPassword());
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
  }
}
