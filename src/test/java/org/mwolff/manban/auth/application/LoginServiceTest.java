package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
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

  /** Verifizierter, aber noch nicht freigegebener Benutzer ({@code approvedAt=null}). */
  private static AppUser pendingUser() {
    return new AppUser(2L, "a@x.de", "storedHash", "Ada", true, PlatformRole.USER, null, null);
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

  @Test
  void login_throwsUserNotApproved_whenPendingAndAdminExists() {
    // Given: verifizierter, aber nicht freigegebener Nutzer; es existiert bereits ein Admin
    // (gemischte Liste: nur so unterscheidet sich anyMatch von allMatch).
    when(users.findByEmail("a@x.de")).thenReturn(Optional.of(pendingUser()));
    when(passwordEncoder.matches("pw", "storedHash")).thenReturn(true);
    when(users.findAll())
        .thenReturn(
            List.of(
                new AppUser(1L, "admin@x.de", "h", "Ad", true, PlatformRole.ADMIN), pendingUser()));

    // When / Then
    assertThatThrownBy(() -> service.login("a@x.de", "pw"))
        .isInstanceOf(UserNotApprovedException.class);
  }

  @Test
  void login_succeeds_forApprovedUser_evenWhenAdminExists() {
    // Given: freigegebener Nutzer; ein Admin existiert. Der Gate darf hier NICHT greifen —
    // der Freigabe-Check muss am approved()-Zustand hängen, nicht allein an der Admin-Existenz.
    when(users.findByEmail("a@x.de")).thenReturn(Optional.of(user(true)));
    when(passwordEncoder.matches("pw", "storedHash")).thenReturn(true);
    when(users.findAll())
        .thenReturn(List.of(new AppUser(1L, "admin@x.de", "h", "Ad", true, PlatformRole.ADMIN)));

    // When
    AppUser result = service.login("a@x.de", "pw");

    // Then
    assertThat(result.id()).isEqualTo(2L);
  }

  @Test
  void login_succeeds_whenPendingButNoAdminExistsYet() {
    // Given: Bootstrap-Fenster — noch kein Admin. Der nicht freigegebene erste Nutzer darf sich
    // anmelden, um sich per Bootstrap-Token zum ersten Admin zu erheben.
    when(users.findByEmail("a@x.de")).thenReturn(Optional.of(pendingUser()));
    when(passwordEncoder.matches("pw", "storedHash")).thenReturn(true);
    when(users.findAll()).thenReturn(List.of(pendingUser()));

    // When
    AppUser result = service.login("a@x.de", "pw");

    // Then
    assertThat(result.id()).isEqualTo(2L);
  }
}
