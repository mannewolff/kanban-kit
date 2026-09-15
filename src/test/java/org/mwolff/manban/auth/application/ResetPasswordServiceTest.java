package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Verhaltenstests des Passwort-Reset-Einlösens (Mockito an den Ports). */
class ResetPasswordServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private AppUserRepository users;
  private PasswordResetTokenRepository tokens;
  private PasswordEncoder encoder;
  private SessionGenerations generations;
  private ResetPasswordService service;

  private static AppUser user() {
    return new AppUser(2L, "a@x.de", "oldHash", "Ada", true, PlatformRole.USER);
  }

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    tokens = mock(PasswordResetTokenRepository.class);
    encoder = mock(PasswordEncoder.class);
    generations = mock(SessionGenerations.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service = new ResetPasswordService(users, tokens, encoder, clock, generations);
  }

  @Test
  void reset_consumesTokenByHashWithInjectedClock() {
    // Given
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.of(2L));
    when(users.findById(2L)).thenReturn(Optional.of(user()));
    when(encoder.encode(anyString())).thenReturn("newHash");

    // When
    service.reset("plaintext", "newPw");

    // Then: der Verbrauch läuft über den Hash und die injizierte Uhr.
    verify(tokens).consume(SecureTokens.sha256Hex("plaintext"), FIXED);
  }

  @Test
  void reset_persistsNewPasswordHash() {
    // Given
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.of(2L));
    when(users.findById(2L)).thenReturn(Optional.of(user()));
    when(encoder.encode("newPw")).thenReturn("newHash");

    // When
    ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
    service.reset("plaintext", "newPw");

    // Then
    verify(users).save(captor.capture());
    assertThat(captor.getValue().passwordHash()).isEqualTo("newHash");
  }

  @Test
  void reset_throwsInvalidToken_whenTokenNotConsumable() {
    // Given: unbekannt, abgelaufen oder bereits verbraucht — für den Aufrufer ununterscheidbar.
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.reset("plaintext", "newPw"))
        .isInstanceOf(InvalidResetTokenException.class);
  }

  @Test
  void reset_leavesPasswordUnchanged_whenTokenNotConsumable() {
    // Given: Nutzer und Encoder gestubbt, damit ein Umgehen des Verbrauchs-Guards (Mutant)
    // sichtbar in einen Passwortwechsel umschlägt statt weiter unten zu werfen.
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.empty());
    when(users.findById(2L)).thenReturn(Optional.of(user()));
    when(encoder.encode(anyString())).thenReturn("newHash");

    // When
    assertThatThrownBy(() -> service.reset("plaintext", "newPw"))
        .isInstanceOf(InvalidResetTokenException.class);

    // Then: der Verlierer des Rennens schreibt kein Passwort.
    verify(users, never()).save(any(AppUser.class));
  }

  @Test
  void reset_throwsInvalidToken_whenUserUnknown() {
    // Given
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.of(2L));
    when(users.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.reset("plaintext", "newPw"))
        .isInstanceOf(InvalidResetTokenException.class);
  }

  @Test
  void reset_invalidatesSessionsOfTheAccount() {
    // Given
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.of(2L));
    when(users.findById(2L)).thenReturn(Optional.of(user()));
    when(encoder.encode(anyString())).thenReturn("newHash");

    // When
    service.reset("plaintext", "newPw");

    // Then: wer sein Passwort neu setzt, beendet jede Sitzung des Kontos.
    verify(generations).invalidateSessions(2L);
  }

  @Test
  void reset_leavesSessionsAlive_whenTokenNotConsumable() {
    // Given: ungültiges oder bereits verbrauchtes Token — der Verlierer des Rennens beendet nichts.
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.empty());
    when(users.findById(2L)).thenReturn(Optional.of(user()));
    when(encoder.encode(anyString())).thenReturn("newHash");

    // When
    assertThatThrownBy(() -> service.reset("plaintext", "newPw"))
        .isInstanceOf(InvalidResetTokenException.class);

    // Then
    verify(generations, never()).invalidateSessions(anyLong());
  }

  @Test
  void reset_leavesSessionsAlive_whenUserUnknown() {
    // Given
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.of(2L));
    when(users.findById(2L)).thenReturn(Optional.empty());

    // When
    assertThatThrownBy(() -> service.reset("plaintext", "newPw"))
        .isInstanceOf(InvalidResetTokenException.class);

    // Then: ohne Konto gibt es kein Passwort zu setzen und keine Sitzung zu beenden.
    verify(generations, never()).invalidateSessions(anyLong());
  }
}
