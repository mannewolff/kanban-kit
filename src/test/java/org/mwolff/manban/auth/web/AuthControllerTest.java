package org.mwolff.manban.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.RegisterUserService;
import org.mwolff.manban.auth.application.VerifyEmailService;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Unit-Tests des öffentlichen Auth-Controllers (Services gemockt). */
class AuthControllerTest {

  private RegisterUserService registerUser;
  private VerifyEmailService verifyEmail;
  private AuthController controller;

  @BeforeEach
  void setUp() {
    registerUser = mock(RegisterUserService.class);
    verifyEmail = mock(VerifyEmailService.class);
    controller = new AuthController(registerUser, verifyEmail);
  }

  @Test
  void register_mapsRegisteredUserToResponse() {
    // Given
    var user = new AppUser(42L, "a@b.de", "hash", "Alice", false, PlatformRole.USER);
    var request = new RegisterRequest("a@b.de", "password1", "Alice");
    when(registerUser.register("a@b.de", "password1", "Alice")).thenReturn(user);

    // When
    AuthController.RegisteredUserResponse result = controller.register(request);

    // Then
    assertThat(result).isEqualTo(new AuthController.RegisteredUserResponse(42L, "a@b.de", false));
  }

  @Test
  void verify_delegatesToken() {
    // When
    controller.verify("the-token");

    // Then
    verify(verifyEmail).verify("the-token");
  }
}
