package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.EmailVerificationToken;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Verhaltenstests der E-Mail-Verifikation (Mockito an den Ports). */
class VerifyEmailServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private AppUserRepository users;
  private EmailVerificationTokenRepository tokens;
  private AdminNotificationMailer adminNotificationMailer;
  private VerifyEmailService service;

  private static EmailVerificationToken token(Instant expiresAt, Instant usedAt) {
    return new EmailVerificationToken(1L, 2L, "hash", expiresAt, usedAt);
  }

  /** Noch nicht freigegebener Benutzer (kanonischer Konstruktor, {@code approvedAt=null}). */
  private static AppUser pendingUser() {
    return new AppUser(2L, "a@x.de", "hash", "Ada", false, PlatformRole.USER, null, null);
  }

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    tokens = mock(EmailVerificationTokenRepository.class);
    adminNotificationMailer = mock(AdminNotificationMailer.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service = new VerifyEmailService(users, tokens, adminNotificationMailer, clock);
    // save() spiegelt standardmäßig den übergebenen Nutzer zurück (wie der echte Adapter).
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void verify_marksTokenUsedWithInjectedClock() {
    // Given
    when(tokens.findByTokenHash(anyString()))
        .thenReturn(Optional.of(token(FIXED.plusSeconds(3600), null)));
    when(users.findById(2L))
        .thenReturn(
            Optional.of(new AppUser(2L, "a@x.de", "hash", "Ada", false, PlatformRole.USER)));

    // When
    ArgumentCaptor<EmailVerificationToken> captor =
        ArgumentCaptor.forClass(EmailVerificationToken.class);
    service.verify("plaintext");

    // Then
    verify(tokens).save(captor.capture());
    assertThat(captor.getValue().usedAt()).isEqualTo(FIXED);
  }

  @Test
  void verify_setsEmailVerifiedOnUser() {
    // Given
    when(tokens.findByTokenHash(anyString()))
        .thenReturn(Optional.of(token(FIXED.plusSeconds(3600), null)));
    when(users.findById(2L))
        .thenReturn(
            Optional.of(new AppUser(2L, "a@x.de", "hash", "Ada", false, PlatformRole.USER)));

    // When
    ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
    service.verify("plaintext");

    // Then
    verify(users).save(captor.capture());
    assertThat(captor.getValue().emailVerified()).isTrue();
  }

  @Test
  void verify_throwsInvalidToken_whenTokenUnknown() {
    // Given
    when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.verify("plaintext"))
        .isInstanceOf(InvalidVerificationTokenException.class);
  }

  @Test
  void verify_throwsInvalidToken_whenTokenAlreadyUsed() {
    // Given: gültiger Nutzer gestubbt, damit ein Umgehen des Used-Guards (Mutant) in
    // einen Erfolg (kein Wurf) umschlägt statt unten am fehlenden Nutzer zu werfen.
    when(tokens.findByTokenHash(anyString()))
        .thenReturn(Optional.of(token(FIXED.plusSeconds(3600), FIXED.minusSeconds(10))));
    when(users.findById(2L))
        .thenReturn(
            Optional.of(new AppUser(2L, "a@x.de", "hash", "Ada", false, PlatformRole.USER)));

    // When / Then
    assertThatThrownBy(() -> service.verify("plaintext"))
        .isInstanceOf(InvalidVerificationTokenException.class);
  }

  @Test
  void verify_throwsInvalidToken_whenTokenExpired() {
    // Given: gültiger Nutzer gestubbt (s. o.).
    when(tokens.findByTokenHash(anyString()))
        .thenReturn(Optional.of(token(FIXED.minusSeconds(1), null)));
    when(users.findById(2L))
        .thenReturn(
            Optional.of(new AppUser(2L, "a@x.de", "hash", "Ada", false, PlatformRole.USER)));

    // When / Then
    assertThatThrownBy(() -> service.verify("plaintext"))
        .isInstanceOf(InvalidVerificationTokenException.class);
  }

  @Test
  void verify_throwsInvalidToken_whenUserUnknown() {
    // Given
    when(tokens.findByTokenHash(anyString()))
        .thenReturn(Optional.of(token(FIXED.plusSeconds(3600), null)));
    when(users.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.verify("plaintext"))
        .isInstanceOf(InvalidVerificationTokenException.class);
  }

  @Test
  void verify_notifiesAllAdmins_whenUserStillPending() {
    // Given
    when(tokens.findByTokenHash(anyString()))
        .thenReturn(Optional.of(token(FIXED.plusSeconds(3600), null)));
    when(users.findById(2L)).thenReturn(Optional.of(pendingUser()));
    AppUser admin1 = new AppUser(10L, "admin1@x.de", "h", "Admin1", true, PlatformRole.ADMIN);
    AppUser admin2 = new AppUser(11L, "admin2@x.de", "h", "Admin2", true, PlatformRole.ADMIN);
    when(users.findByPlatformRole(PlatformRole.ADMIN)).thenReturn(List.of(admin1, admin2));

    // When
    service.verify("plaintext");

    // Then: je Admin eine Benachrichtigung, mit den Daten des neuen Nutzers.
    verify(adminNotificationMailer).sendNewUserPendingApproval("admin1@x.de", "a@x.de", "Ada");
    verify(adminNotificationMailer).sendNewUserPendingApproval("admin2@x.de", "a@x.de", "Ada");
  }

  @Test
  void verify_sendsNoNotification_whenUserAlreadyApproved() {
    // Given: bereits freigegebener Nutzer (z. B. eingeladen, #0099) — Bequem-Konstruktor.
    when(tokens.findByTokenHash(anyString()))
        .thenReturn(Optional.of(token(FIXED.plusSeconds(3600), null)));
    when(users.findById(2L))
        .thenReturn(
            Optional.of(new AppUser(2L, "a@x.de", "hash", "Ada", false, PlatformRole.USER)));

    // When
    service.verify("plaintext");

    // Then: kein Admin-Abruf, keine Benachrichtigung.
    verify(users, never()).findByPlatformRole(any(PlatformRole.class));
    verify(adminNotificationMailer, never())
        .sendNewUserPendingApproval(anyString(), anyString(), anyString());
  }
}
