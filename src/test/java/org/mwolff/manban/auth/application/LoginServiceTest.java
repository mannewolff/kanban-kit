package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verhaltenstests der Anmeldung (Mockito an den Ports): Einlösen von Code und Link (Issue #1805,
 * Plan #1801 A8, E5, E6). Eine Anmeldung mit Passwort gibt es nicht mehr (Issue #1816).
 */
class LoginServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
  private static final String EMAIL_HASH = SecureTokens.sha256Hex("a@x.de");
  private static final int MAX_ATTEMPTS = 5;
  private static final AppUser ADMIN =
      new AppUser(1L, "admin@x.de", "h", "Ad", true, PlatformRole.ADMIN);

  private AppUserRepository users;
  private LoginCodeRepository codes;
  private LoginCodeCrypto crypto;
  private AdminNotificationMailer adminNotificationMailer;
  private LoginService service;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    codes = mock(LoginCodeRepository.class);
    adminNotificationMailer = mock(AdminNotificationMailer.class);
    AuthProperties properties =
        new AuthProperties(
            "https://app.example",
            "geheim",
            null,
            null,
            new AuthProperties.LoginCode(Duration.ofMinutes(10), MAX_ATTEMPTS, 5));
    crypto = new LoginCodeCrypto(properties);
    service =
        new LoginService(
            users,
            codes,
            crypto,
            adminNotificationMailer,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  /** Konto mit Zustand: bestätigt, freigegeben, gesperrt. */
  private static AppUser konto(boolean verified, boolean approved, boolean disabled) {
    return new AppUser(
        2L,
        "a@x.de",
        "storedHash",
        "Ada",
        verified,
        PlatformRole.USER,
        approved ? Instant.EPOCH : null,
        null,
        disabled ? Instant.EPOCH : null);
  }

  /** Der richtige Code löst die Anforderung von Konto 2 ein. */
  private void codeGilt(String code) {
    when(codes.consumeByCode(EMAIL_HASH, crypto.codeHmac(code), NOW, MAX_ATTEMPTS))
        .thenReturn(Optional.of(2L));
  }

  @Test
  void loginWithCode_richtigerCode_liefertKonto() {
    codeGilt("123456");
    when(users.findById(2L)).thenReturn(Optional.of(konto(true, true, false)));

    AppUser result = service.loginWithCode("a@x.de", "123456");

    assertThat(result.id()).isEqualTo(2L);
    verify(codes, never()).recordFailure(anyString(), any(Instant.class), anyInt());
    verify(users, never()).save(any(AppUser.class));
    verifyNoInteractions(adminNotificationMailer);
  }

  @Test
  void loginWithCode_normalisiertDieAdresse_undPrueftNurIhreJuengsteOffeneAnforderung() {
    // E6: Der Verbrauch hängt an der Adresse — nur ihre offene Anforderung zählt, und davon gibt es
    // nach supersedeOpen höchstens eine, die jüngste.
    codeGilt("123456");
    when(users.findById(2L)).thenReturn(Optional.of(konto(true, true, false)));

    AppUser result = service.loginWithCode("  A@X.de ", "123456");

    assertThat(result.id()).isEqualTo(2L);
    verify(codes).consumeByCode(EMAIL_HASH, crypto.codeHmac("123456"), NOW, MAX_ATTEMPTS);
  }

  @Test
  void loginWithCode_falscherCode_verbuchtFehlversuchUndWirft() {
    when(codes.consumeByCode(anyString(), anyString(), any(Instant.class), anyInt()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loginWithCode("a@x.de", "000000"))
        .isInstanceOf(InvalidLoginCodeException.class)
        .hasMessage("Code ungültig oder abgelaufen — fordere einen neuen an");

    verify(codes).recordFailure(EMAIL_HASH, NOW, MAX_ATTEMPTS);
    verify(users, never()).findById(anyLong());
  }

  @Test
  void loginWithCode_unbekannteAdresse_abgelaufenVerbrauchtOderErsetzt_wirftDieselbeAusnahme() {
    // Unbekannte Adresse, abgelaufener, verbrauchter und ersetzter Code sehen für den Dienst
    // gleich aus: Der atomare Verbrauch liefert leer (A2). Die Antwort ist dieselbe (E6).
    when(codes.consumeByCode(anyString(), anyString(), any(Instant.class), anyInt()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loginWithCode("unbekannt@x.de", "123456"))
        .isInstanceOf(InvalidLoginCodeException.class);

    verify(codes).recordFailure(SecureTokens.sha256Hex("unbekannt@x.de"), NOW, MAX_ATTEMPTS);
  }

  @Test
  void loginWithCode_kontoInzwischenGeloescht_wirftDieselbeAusnahme() {
    codeGilt("123456");
    when(users.findById(2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loginWithCode("a@x.de", "123456"))
        .isInstanceOf(InvalidLoginCodeException.class);
  }

  @Test
  void loginWithLink_gueltigesToken_liefertKonto() {
    when(codes.consumeByLink(crypto.linkHash("tok"), NOW, MAX_ATTEMPTS))
        .thenReturn(Optional.of(2L));
    when(users.findById(2L)).thenReturn(Optional.of(konto(true, true, false)));

    AppUser result = service.loginWithLink("tok");

    assertThat(result.id()).isEqualTo(2L);
  }

  @Test
  void loginWithLink_ungueltigesToken_wirftDieselbeAusnahme() {
    when(codes.consumeByLink(anyString(), any(Instant.class), anyInt()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loginWithLink("falsch"))
        .isInstanceOf(InvalidLoginCodeException.class)
        .hasMessage("Code ungültig oder abgelaufen — fordere einen neuen an");

    verify(codes, never()).recordFailure(anyString(), any(Instant.class), anyInt());
  }

  @Test
  void loginWithCode_unbestaetigteAdresse_wirdBestaetigt() {
    codeGilt("123456");
    when(users.findById(2L)).thenReturn(Optional.of(konto(false, true, false)));

    AppUser result = service.loginWithCode("a@x.de", "123456");

    assertThat(result.emailVerified()).isTrue();
    verify(users).save(konto(true, true, false));
    verifyNoInteractions(adminNotificationMailer);
  }

  @Test
  void loginWithLink_unbestaetigteAdresse_wirdBestaetigt() {
    when(codes.consumeByLink(crypto.linkHash("tok"), NOW, MAX_ATTEMPTS))
        .thenReturn(Optional.of(2L));
    when(users.findById(2L)).thenReturn(Optional.of(konto(false, true, false)));

    AppUser result = service.loginWithLink("tok");

    assertThat(result.emailVerified()).isTrue();
    verify(users).save(konto(true, true, false));
  }

  @Test
  void gesperrtNachGueltigemCode_adresseBestaetigt_keineSitzung() {
    // E5: Der Code beweist das Postfach, die Bestätigung gibt keinen Zugang.
    codeGilt("123456");
    when(users.findById(2L)).thenReturn(Optional.of(konto(false, false, true)));

    assertThatThrownBy(() -> service.loginWithCode("a@x.de", "123456"))
        .isInstanceOf(UserDisabledException.class)
        .hasMessage("Konto gesperrt");

    verify(users).save(konto(true, false, true));
    verifyNoInteractions(adminNotificationMailer);
  }

  @Test
  void gesperrtUndBestaetigt_wirftUserDisabled_ohneSpeichern() {
    codeGilt("123456");
    when(users.findById(2L)).thenReturn(Optional.of(konto(true, true, true)));

    assertThatThrownBy(() -> service.loginWithCode("a@x.de", "123456"))
        .isInstanceOf(UserDisabledException.class);

    verify(users, never()).save(any(AppUser.class));
  }

  @Test
  void wartendesKonto_adminsGenauEinmalBenachrichtigt() {
    AppUser admin2 = new AppUser(3L, "admin2@x.de", "h", "Ad2", true, PlatformRole.ADMIN);
    when(users.findByPlatformRole(PlatformRole.ADMIN)).thenReturn(List.of(ADMIN, admin2));
    when(users.findAll()).thenReturn(List.of(ADMIN, admin2, konto(false, false, false)));
    codeGilt("111111");
    codeGilt("222222");

    // Erster Versuch: Übergang unbestätigt → bestätigt, die Admins erfahren davon.
    when(users.findById(2L)).thenReturn(Optional.of(konto(false, false, false)));
    assertThatThrownBy(() -> service.loginWithCode("a@x.de", "111111"))
        .isInstanceOf(UserNotApprovedException.class)
        .hasMessageStartingWith("Das Konto wartet auf die Freigabe");

    InOrder order = inOrder(users, adminNotificationMailer);
    order.verify(users).save(konto(true, false, false));
    order.verify(adminNotificationMailer).sendNewUserPendingApproval("admin@x.de", "a@x.de", "Ada");
    verify(adminNotificationMailer).sendNewUserPendingApproval("admin2@x.de", "a@x.de", "Ada");

    // Zweiter Versuch: Die Adresse ist schon bestätigt — keine weitere Benachrichtigung.
    when(users.findById(2L)).thenReturn(Optional.of(konto(true, false, false)));
    assertThatThrownBy(() -> service.loginWithCode("a@x.de", "222222"))
        .isInstanceOf(UserNotApprovedException.class);

    verify(adminNotificationMailer).sendNewUserPendingApproval("admin@x.de", "a@x.de", "Ada");
    verify(adminNotificationMailer).sendNewUserPendingApproval("admin2@x.de", "a@x.de", "Ada");
    verify(users).save(any(AppUser.class));
  }

  @Test
  void wartendesKonto_ohneAdmin_ersterNutzer_wirdAngemeldet() {
    // Ausnahme für den ersten Plattform-Admin bleibt (anyAdminExists): Ohne Admin darf sich der
    // erste Nutzer anmelden, um sich per Bootstrap-Token zu erheben.
    codeGilt("123456");
    when(users.findById(2L)).thenReturn(Optional.of(konto(false, false, false)));
    when(users.findAll()).thenReturn(List.of(konto(true, false, false)));

    AppUser result = service.loginWithCode("a@x.de", "123456");

    assertThat(result.emailVerified()).isTrue();
    assertThat(result.effectivelyApproved()).isFalse();
  }

  @Test
  void wartenderPlattformAdmin_wirdAngemeldet_ohneBenachrichtigung() {
    // effectivelyApproved (Issue #561): Ein Admin ohne Freigabe-Zeitstempel wartet auf niemanden.
    AppUser pendingUnverifiedAdmin =
        new AppUser(2L, "a@x.de", "storedHash", "Ada", false, PlatformRole.ADMIN, null, null);
    codeGilt("123456");
    when(users.findById(2L)).thenReturn(Optional.of(pendingUnverifiedAdmin));
    when(users.findAll()).thenReturn(List.of(pendingUnverifiedAdmin));

    AppUser result = service.loginWithCode("a@x.de", "123456");

    assertThat(result.platformRole()).isEqualTo(PlatformRole.ADMIN);
    verifyNoInteractions(adminNotificationMailer);
  }

  @Test
  void abweisungenRollenNichtZurueck() throws NoSuchMethodException {
    // Fehlversuch, bestätigte Adresse und vorgemerkte Admin-Mail müssen die Abweisung überstehen;
    // ein Rollback nähme dem Nutzer die Bestätigung und den Admins die Nachricht (A8), und
    // unbegrenzte Rateversuche wären die Folge eines zurückgerollten Fehlversuchs (A2).
    for (var method :
        List.of(
            LoginService.class.getMethod("loginWithCode", String.class, String.class),
            LoginService.class.getMethod("loginWithLink", String.class))) {
      Transactional tx = method.getAnnotation(Transactional.class);
      assertThat(tx).as(method.getName()).isNotNull();
      assertThat(tx.noRollbackFor())
          .as(method.getName())
          .containsExactlyInAnyOrder(
              InvalidLoginCodeException.class,
              UserDisabledException.class,
              UserNotApprovedException.class);
    }
  }
}
