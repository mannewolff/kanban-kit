package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Verhaltenstests der Anmeldung (Mockito am Repository- und PasswordEncoder-Port). */
class LoginServiceTest {

  private AppUserRepository users;
  private PasswordEncoder passwordEncoder;
  private LoginService service;

  private static AppUser user(boolean verified) {
    return new AppUser(2L, "a@x.de", "storedHash", "Ada", verified, PlatformRole.USER);
  }

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    service = new LoginService(users, passwordEncoder);
  }

  @Test
  void login_returnsUser_whenCredentialsValidAndEmailVerified() {
    // Given
    when(users.findByEmail("a@x.de")).thenReturn(Optional.of(user(true)));
    when(passwordEncoder.matches("pw", "storedHash")).thenReturn(true);

    // When
    AppUser result = service.login("a@x.de", "pw");

    // Then
    assertThat(result.id()).isEqualTo(2L);
  }

  @Test
  void login_normalizesEmail_beforeLookup() {
    // Given
    when(users.findByEmail("a@x.de")).thenReturn(Optional.of(user(true)));
    when(passwordEncoder.matches("pw", "storedHash")).thenReturn(true);

    // When
    AppUser result = service.login("  A@X.de  ", "pw");

    // Then
    assertThat(result.email()).isEqualTo("a@x.de");
  }

  @Test
  void login_throwsInvalidCredentials_whenUserUnknown() {
    // Given
    when(users.findByEmail("a@x.de")).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.login("a@x.de", "pw"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void login_throwsInvalidCredentials_whenPasswordMismatch() {
    // Given
    when(users.findByEmail("a@x.de")).thenReturn(Optional.of(user(true)));
    when(passwordEncoder.matches("wrong", "storedHash")).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> service.login("a@x.de", "wrong"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void login_throwsEmailNotVerified_whenEmailUnverified() {
    // Given
    when(users.findByEmail("a@x.de")).thenReturn(Optional.of(user(false)));
    when(passwordEncoder.matches("pw", "storedHash")).thenReturn(true);

    // When / Then
    assertThatThrownBy(() -> service.login("a@x.de", "pw"))
        .isInstanceOf(EmailNotVerifiedException.class);
  }
}
