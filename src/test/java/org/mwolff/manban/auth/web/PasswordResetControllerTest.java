package org.mwolff.manban.auth.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.RequestPasswordResetService;
import org.mwolff.manban.auth.application.ResetPasswordService;

/** Unit-Tests des Passwort-Reset-Controllers (Services gemockt). */
class PasswordResetControllerTest {

  private RequestPasswordResetService requestReset;
  private ResetPasswordService resetPassword;
  private PasswordResetController controller;

  @BeforeEach
  void setUp() {
    requestReset = mock(RequestPasswordResetService.class);
    resetPassword = mock(ResetPasswordService.class);
    controller = new PasswordResetController(requestReset, resetPassword);
  }

  @Test
  void forgot_delegatesEmail() {
    // When
    controller.forgot(new ForgotPasswordRequest("a@b.de"));

    // Then
    verify(requestReset).requestReset("a@b.de");
  }

  @Test
  void reset_delegatesTokenAndPassword() {
    // When
    controller.reset(new ResetPasswordRequest("tok", "newpass12"));

    // Then
    verify(resetPassword).reset("tok", "newpass12");
  }
}
