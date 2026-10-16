package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.LoginCode;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;

/** Anfordern eines Anmeldecodes (Issue #1804, Plan #1801 A5, A6, A12). */
class RequestLoginCodeServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
  private static final String EMAIL = "ada@example.org";
  private static final String EMAIL_HASH = SecureTokens.sha256Hex(EMAIL);

  private AppUserRepository users;
  private LoginCodeRepository codes;
  private LoginCodeMailer mailer;
  private LoginCodeCrypto crypto;
  private RequestLoginCodeService service;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    codes = mock(LoginCodeRepository.class);
    mailer = mock(LoginCodeMailer.class);
    AuthProperties properties =
        new AuthProperties(
            "https://app.example",
            null,
            "geheim",
            null,
            null,
            null,
            new AuthProperties.LoginCode(Duration.ofMinutes(10), 5, 5));
    crypto = new LoginCodeCrypto(properties);
    service =
        new RequestLoginCodeService(
            users, codes, crypto, mailer, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    when(codes.save(any(LoginCode.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private static AppUser konto(Instant approvedAt, Instant disabledAt) {
    return new AppUser(
        7L, EMAIL, "hash", "Ada", true, PlatformRole.USER, approvedAt, null, disabledAt);
  }

  private LoginCode gespeichert() {
    ArgumentCaptor<LoginCode> captor = ArgumentCaptor.forClass(LoginCode.class);
    verify(codes).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void normalisiertDieAdresseVorHashUndSuche() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

    service.requestCode("  Ada@Example.ORG ");

    verify(users).findByEmail(EMAIL);
    verify(codes).countSince(EMAIL_HASH, NOW.minus(Duration.ofHours(1)));
    assertThat(gespeichert().emailHash()).isEqualTo(EMAIL_HASH);
  }

  @Test
  void bekannteAdresse_legtZeileMitKontoCodeUndLinkAnUndMerktMailVor() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(konto(Instant.EPOCH, null)));

    service.requestCode(EMAIL);

    LoginCode zeile = gespeichert();
    assertThat(zeile.id()).isNull();
    assertThat(zeile.userId()).isEqualTo(7L);
    assertThat(zeile.createdAt()).isEqualTo(NOW);
    assertThat(zeile.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    assertThat(zeile.failedAttempts()).isZero();
    assertThat(zeile.consumedAt()).isNull();
    assertThat(zeile.supersededAt()).isNull();

    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
    verify(mailer).sendLoginCode(eq(EMAIL), code.capture(), link.capture());
    assertThat(code.getValue()).matches("\\d{6}");
    assertThat(zeile.codeHash()).isEqualTo(crypto.codeHmac(code.getValue()));
    assertThat(link.getValue()).startsWith("https://app.example/login/link?token=");
    String token = link.getValue().substring("https://app.example/login/link?token=".length());
    assertThat(token).isNotBlank();
    assertThat(zeile.linkHash()).isEqualTo(crypto.linkHash(token));
  }

  @Test
  void unbekannteAdresse_legtZeileOhneKontoUndOhneCodeAnUndSchicktKeineMail() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

    service.requestCode(EMAIL);

    LoginCode zeile = gespeichert();
    assertThat(zeile.userId()).isNull();
    assertThat(zeile.codeHash()).isNull();
    assertThat(zeile.linkHash()).isNull();
    assertThat(zeile.createdAt()).isEqualTo(NOW);
    assertThat(zeile.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    verifyNoInteractions(mailer);
  }

  @Test
  void sechsteAnforderung_bekannteAdresse_wirdAbgewiesen() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(konto(Instant.EPOCH, null)));
    when(codes.countSince(EMAIL_HASH, NOW.minus(Duration.ofHours(1)))).thenReturn(5L);

    assertThatThrownBy(() -> service.requestCode(EMAIL))
        .isInstanceOf(LoginCodeLimitExceededException.class);

    verify(codes, never()).save(any(LoginCode.class));
    verify(codes, never()).supersedeOpen(anyString(), any(Instant.class));
    verifyNoInteractions(mailer);
  }

  @Test
  void sechsteAnforderung_unbekannteAdresse_wirdGleichAbgewiesen() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
    when(codes.countSince(EMAIL_HASH, NOW.minus(Duration.ofHours(1)))).thenReturn(5L);

    assertThatThrownBy(() -> service.requestCode(EMAIL))
        .isInstanceOf(LoginCodeLimitExceededException.class)
        .hasMessage(new LoginCodeLimitExceededException().getMessage());

    verify(codes, never()).save(any(LoginCode.class));
  }

  @Test
  void fuenfteAnforderung_wirdNochAngenommen() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
    when(codes.countSince(EMAIL_HASH, NOW.minus(Duration.ofHours(1)))).thenReturn(4L);

    service.requestCode(EMAIL);

    verify(codes).save(any(LoginCode.class));
  }

  @Test
  void offeneAnforderungenWerdenVorDerNeuenErsetzt() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(konto(Instant.EPOCH, null)));

    service.requestCode(EMAIL);

    InOrder reihenfolge = inOrder(codes);
    reihenfolge.verify(codes).supersedeOpen(EMAIL_HASH, NOW);
    reihenfolge.verify(codes).save(any(LoginCode.class));
  }

  @Test
  void zeilenAelterAls24StundenWerdenMitgeloescht() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

    service.requestCode(EMAIL);

    verify(codes).deleteOlderThan(NOW.minus(Duration.ofHours(24)));
  }

  @Test
  void gesperrtesKonto_bekommtEbenfallsMail() {
    when(users.findByEmail(EMAIL))
        .thenReturn(Optional.of(konto(Instant.EPOCH, NOW.minusSeconds(60))));

    service.requestCode(EMAIL);

    assertThat(gespeichert().userId()).isEqualTo(7L);
    verify(mailer).sendLoginCode(eq(EMAIL), anyString(), anyString());
  }

  @Test
  void wartendesKonto_bekommtEbenfallsMail() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(konto(null, null)));

    service.requestCode(EMAIL);

    assertThat(gespeichert().userId()).isEqualTo(7L);
    verify(mailer).sendLoginCode(eq(EMAIL), anyString(), anyString());
  }
}
