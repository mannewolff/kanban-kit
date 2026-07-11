package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.EmailVerificationToken;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Verhaltenstests der Registrierung (Mockito an den Ports). */
class RegisterUserServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private AppUserRepository users;
  private EmailVerificationTokenRepository tokens;
  private VerificationMailer mailer;
  private PasswordEncoder encoder;
  private RegisterUserService service;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    tokens = mock(EmailVerificationTokenRepository.class);
    mailer = mock(VerificationMailer.class);
    encoder = mock(PasswordEncoder.class);
    AuthProperties properties =
        new AuthProperties("https://app.example", Duration.ofHours(24), null, null, null, null);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service = new RegisterUserService(users, tokens, mailer, encoder, properties, clock);
    when(encoder.encode(anyString())).thenReturn("argon2hash");
    when(users.save(any(AppUser.class)))
        .thenAnswer(
            inv -> {
              AppUser u = inv.getArgument(0);
              return new AppUser(
                  2L,
                  u.email(),
                  u.passwordHash(),
                  u.displayName(),
                  u.emailVerified(),
                  u.platformRole());
            });
  }

  @Test
  void register_setsTokenExpiryFromInjectedClockPlusTtl() {
    // Given
    when(users.existsByEmail("a@x.de")).thenReturn(false);

    // When
    ArgumentCaptor<EmailVerificationToken> captor =
        ArgumentCaptor.forClass(EmailVerificationToken.class);
    service.register("a@x.de", "pw", "Ada");

    // Then
    verify(tokens).save(captor.capture());
    assertThat(captor.getValue().expiresAt()).isEqualTo(FIXED.plus(Duration.ofHours(24)));
  }

  @Test
  void register_returnsPersistedUser() {
    // Given
    when(users.existsByEmail("a@x.de")).thenReturn(false);

    // When
    AppUser registered = service.register("a@x.de", "pw", "Ada");

    // Then
    assertThat(registered.id()).isEqualTo(2L);
  }

  @Test
  void register_normalizesEmail() {
    // Given
    when(users.existsByEmail("a@x.de")).thenReturn(false);

    // When
    ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
    service.register("  A@X.de ", "pw", "Ada");

    // Then
    verify(users).save(captor.capture());
    assertThat(captor.getValue().email()).isEqualTo("a@x.de");
  }

  @Test
  void register_persistsUserUnverifiedWithEncodedPassword() {
    // Given
    when(users.existsByEmail("a@x.de")).thenReturn(false);

    // When
    ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
    service.register("a@x.de", "pw", "Ada");

    // Then
    verify(users).save(captor.capture());
    assertThat(captor.getValue())
        .extracting(AppUser::passwordHash, AppUser::emailVerified, AppUser::platformRole)
        .containsExactly("argon2hash", false, PlatformRole.USER);
  }

  @Test
  void register_trimsDisplayName() {
    // Given
    when(users.existsByEmail("a@x.de")).thenReturn(false);

    // When
    ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
    service.register("a@x.de", "pw", "  Ada  ");

    // Then
    verify(users).save(captor.capture());
    assertThat(captor.getValue().displayName()).isEqualTo("Ada");
  }

  @Test
  void register_sendsVerificationEmail() {
    // Given
    when(users.existsByEmail("a@x.de")).thenReturn(false);

    // When
    service.register("a@x.de", "pw", "Ada");

    // Then
    verify(mailer).sendVerificationEmail(eq("a@x.de"), anyString());
  }

  @Test
  void register_throwsEmailAlreadyRegistered_whenEmailTaken() {
    // Given
    when(users.existsByEmail("a@x.de")).thenReturn(true);

    // When / Then
    assertThatThrownBy(() -> service.register("a@x.de", "pw", "Ada"))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }
}
