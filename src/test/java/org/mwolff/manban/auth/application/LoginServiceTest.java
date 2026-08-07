package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
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

  /** Plattform-Admin ohne Freigabe-Zeitstempel — der Kundenfall aus Issue #556. */
  private static AppUser pendingAdmin() {
    return new AppUser(2L, "a@x.de", "storedHash", "Ada", true, PlatformRole.ADMIN, null, null);
  }

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    service = new LoginService(users, passwordEncoder);
  }

  private static AppUser disabledUser() {
    return new AppUser(
        2L,
        "a@x.de",
        "storedHash",
        "Ada",
        true,
        PlatformRole.USER,
        Instant.EPOCH,
        null,
        Instant.EPOCH);
  }

  @Test
  void login_throwsUserDisabled_whenAccountDisabled() {
    when(users.findByEmail("a@x.de")).thenReturn(Optional.of(disabledUser()));
    when(passwordEncoder.matches("pw", "storedHash")).thenReturn(true);

    assertThatThrownBy(() -> service.login("a@x.de", "pw"))
        .isInstanceOf(UserDisabledException.class);
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

    // When / Then: die Meldung nennt neben dem Grund den Ausweg, der hier tatsächlich trägt
    // (Issue #562) — ein Admin existiert ja, sonst gäbe es diese Meldung nicht. Sie sagt dabei
    // nichts darüber aus, ob die E-Mail-Adresse registriert ist.
    assertThatThrownBy(() -> service.login("a@x.de", "pw"))
        .isInstanceOf(UserNotApprovedException.class)
        .hasMessage(
            "Das Konto wartet auf die Freigabe durch einen Plattform-Admin."
                + " Bitte wenden Sie sich an den Betreiber dieser Instanz.");
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
  void login_succeeds_forPendingPlatformAdmin_whenAdminExists() {
    // Given: Der Nutzer ist selbst zum Plattform-Admin geworden, ohne freigegeben worden zu sein.
    // Damit existiert ein Admin — er selbst — und die Bootstrap-Ausnahme kippt: das Gate sperrte
    // ihn aus seiner eigenen Anmeldung aus. Ein Admin braucht keine Freigabe, es gibt niemanden
    // über ihm, der freigeben könnte.
    when(users.findByEmail("a@x.de")).thenReturn(Optional.of(pendingAdmin()));
    when(passwordEncoder.matches("pw", "storedHash")).thenReturn(true);
    when(users.findAll()).thenReturn(List.of(pendingAdmin()));

    // When
    AppUser result = service.login("a@x.de", "pw");

    // Then
    assertThat(result.platformRole()).isEqualTo(PlatformRole.ADMIN);
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
