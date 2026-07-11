package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PasswordResetToken;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Verhaltenstests der Passwort-Reset-Anforderung (Mockito an den Ports). */
class RequestPasswordResetServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private AppUserRepository users;
  private PasswordResetTokenRepository tokens;
  private PasswordResetMailer mailer;
  private RequestPasswordResetService service;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    tokens = mock(PasswordResetTokenRepository.class);
    mailer = mock(PasswordResetMailer.class);
    AuthProperties properties =
        new AuthProperties("https://app.example", null, null, null, null, Duration.ofHours(1));
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service = new RequestPasswordResetService(users, tokens, mailer, properties, clock);
  }

  @Test
  void requestReset_setsTokenExpiryFromInjectedClockPlusTtl() {
    // Given
    when(users.findByEmail("a@x.de"))
        .thenReturn(Optional.of(new AppUser(2L, "a@x.de", "hash", "Ada", true, PlatformRole.USER)));

    // When
    ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
    service.requestReset("a@x.de");

    // Then
    verify(tokens).save(captor.capture());
    assertThat(captor.getValue().expiresAt()).isEqualTo(FIXED.plus(Duration.ofHours(1)));
  }

  @Test
  void requestReset_sendsResetEmail_whenUserExists() {
    // Given
    when(users.findByEmail("a@x.de"))
        .thenReturn(Optional.of(new AppUser(2L, "a@x.de", "hash", "Ada", true, PlatformRole.USER)));

    // When
    service.requestReset("  A@X.de ");

    // Then
    verify(mailer).sendPasswordResetEmail(anyString(), anyString());
  }

  @Test
  void requestReset_doesNothing_whenUserUnknown() {
    // Given
    when(users.findByEmail("a@x.de")).thenReturn(Optional.empty());

    // When
    service.requestReset("a@x.de");

    // Then
    verify(tokens, never()).save(any(PasswordResetToken.class));
  }
}
