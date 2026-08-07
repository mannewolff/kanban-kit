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
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;

/** Verhaltenstests der E-Mail-Verifikation (Mockito an den Ports). */
class VerifyEmailServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private AppUserRepository users;
  private EmailVerificationTokenRepository tokens;
  private AdminNotificationMailer adminNotificationMailer;
  private VerifyEmailService service;

  /** Noch nicht freigegebener Benutzer (kanonischer Konstruktor, {@code approvedAt=null}). */
  private static AppUser pendingUser() {
    return new AppUser(2L, "a@x.de", "hash", "Ada", false, PlatformRole.USER, null, null);
  }

  private static AppUser approvedUser() {
    return new AppUser(2L, "a@x.de", "hash", "Ada", false, PlatformRole.USER);
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
  void verify_consumesTokenByHashWithInjectedClock() {
    // Given
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.of(2L));
    when(users.findById(2L)).thenReturn(Optional.of(approvedUser()));

    // When
    service.verify("plaintext");

    // Then: der Verbrauch läuft über den Hash und die injizierte Uhr.
    verify(tokens).consume(SecureTokens.sha256Hex("plaintext"), FIXED);
  }

  @Test
  void verify_setsEmailVerifiedOnUser() {
    // Given
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.of(2L));
    when(users.findById(2L)).thenReturn(Optional.of(approvedUser()));

    // When
    ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
    service.verify("plaintext");

    // Then
    verify(users).save(captor.capture());
    assertThat(captor.getValue().emailVerified()).isTrue();
  }

  @Test
  void verify_throwsInvalidToken_whenTokenNotConsumable() {
    // Given: unbekannt, abgelaufen oder bereits verbraucht — für den Aufrufer ununterscheidbar.
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.verify("plaintext"))
        .isInstanceOf(InvalidVerificationTokenException.class);
  }

  @Test
  void verify_writesNothingAndNotifiesNobody_whenTokenNotConsumable() {
    // Given: freigabepflichtiger Nutzer und Admins gestubbt, damit ein Umgehen des
    // Verbrauchs-Guards (Mutant) sichtbar in Schreibzugriff und Mailversand umschlägt.
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.empty());
    when(users.findById(2L)).thenReturn(Optional.of(pendingUser()));
    when(users.findByPlatformRole(PlatformRole.ADMIN))
        .thenReturn(
            List.of(new AppUser(10L, "admin@x.de", "h", "Admin", true, PlatformRole.ADMIN)));

    // When
    assertThatThrownBy(() -> service.verify("plaintext"))
        .isInstanceOf(InvalidVerificationTokenException.class);

    // Then: der Verlierer des Rennens verifiziert nicht und benachrichtigt nicht.
    verify(users, never()).save(any(AppUser.class));
    verify(adminNotificationMailer, never())
        .sendNewUserPendingApproval(anyString(), anyString(), anyString());
  }

  @Test
  void verify_throwsInvalidToken_whenUserUnknown() {
    // Given
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.of(2L));
    when(users.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.verify("plaintext"))
        .isInstanceOf(InvalidVerificationTokenException.class);
  }

  @Test
  void verify_notifiesAllAdmins_whenUserStillPending() {
    // Given
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.of(2L));
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
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.of(2L));
    when(users.findById(2L)).thenReturn(Optional.of(approvedUser()));

    // When
    service.verify("plaintext");

    // Then: kein Admin-Abruf, keine Benachrichtigung.
    verify(users, never()).findByPlatformRole(any(PlatformRole.class));
    verify(adminNotificationMailer, never())
        .sendNewUserPendingApproval(anyString(), anyString(), anyString());
  }

  @Test
  void verify_sendsNoNotification_whenUserIsPlatformAdminWithoutApproval() {
    // Given: ein Plattform-Admin ohne Freigabe-Zeitstempel (etwa per rohem SQL-UPDATE gesetzt).
    // Er braucht keine Fremdfreigabe (Issue #556) und darf sich deshalb auch nicht selbst als
    // wartender Nutzer bei den Admins melden.
    when(tokens.consume(anyString(), any(Instant.class))).thenReturn(Optional.of(2L));
    when(users.findById(2L))
        .thenReturn(
            Optional.of(
                new AppUser(
                    2L, "root@x.de", "hash", "Root", false, PlatformRole.ADMIN, null, null)));

    // When
    service.verify("plaintext");

    // Then
    verify(users, never()).findByPlatformRole(any(PlatformRole.class));
    verify(adminNotificationMailer, never())
        .sendNewUserPendingApproval(anyString(), anyString(), anyString());
  }
}
