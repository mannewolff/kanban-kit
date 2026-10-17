package org.mwolff.manban.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Invarianten einer Code-Anforderung {@link LoginCode} (Plan #1801, A1, A5). */
class LoginCodeTest {

  private static final Instant CREATED = Instant.parse("2026-10-16T10:00:00Z");
  private static final Instant EXPIRES = CREATED.plusSeconds(600);

  private static LoginCode anforderung(
      @Nullable Long userId, @Nullable String codeHash, @Nullable String linkHash) {
    return new LoginCode(
        null, "email-hash", userId, codeHash, linkHash, CREATED, EXPIRES, 0, null, null);
  }

  @Test
  void nimmtEineAnforderungMitKontoCodeUndLinkAn() {
    LoginCode code = anforderung(7L, "code-hmac", "link-hash");

    assertThat(code.userId()).isEqualTo(7L);
    assertThat(code.codeHash()).isEqualTo("code-hmac");
    assertThat(code.linkHash()).isEqualTo("link-hash");
  }

  @Test
  void nimmtEineAnforderungOhneKontoOhneCodeUndOhneLinkAn() {
    // A5: Auch eine unbekannte Adresse hinterlässt eine Zeile, damit die Zählung gleich läuft.
    assertThatCode(() -> anforderung(null, null, null)).doesNotThrowAnyException();
  }

  @Test
  void weistEinenLeerenEmailHashAb() {
    assertThatThrownBy(
            () -> new LoginCode(null, " ", 7L, "c", "l", CREATED, EXPIRES, 0, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("emailHash");
  }

  @Test
  void weistEinenCodeOhneLinkAb() {
    assertThatThrownBy(() -> anforderung(7L, "code-hmac", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("gemeinsam");
  }

  @Test
  void weistEinenLinkOhneCodeAb() {
    assertThatThrownBy(() -> anforderung(7L, null, "link-hash"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("gemeinsam");
  }

  @Test
  void weistEinenCodeOhneKontoAb() {
    assertThatThrownBy(() -> anforderung(null, "code-hmac", "link-hash"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Konto");
  }

  @Test
  void weistEinKontoOhneCodeAb() {
    assertThatThrownBy(() -> anforderung(7L, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Konto");
  }

  @Test
  void weistNegativeFehlversucheAb() {
    assertThatThrownBy(
            () -> new LoginCode(null, "email-hash", 7L, "c", "l", CREATED, EXPIRES, -1, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("failedAttempts");
  }

  @Test
  void nimmtNullFehlversucheAn() {
    assertThat(anforderung(7L, "c", "l").failedAttempts()).isZero();
  }

  @Test
  void weistEinenAblaufVorDerAnlageAb() {
    assertThatThrownBy(
            () ->
                new LoginCode(
                    null,
                    "email-hash",
                    7L,
                    "c",
                    "l",
                    CREATED,
                    CREATED.minusSeconds(1),
                    0,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expiresAt");
  }

  @Test
  void weistEinenAblaufGleichDerAnlageAb() {
    assertThatThrownBy(
            () -> new LoginCode(null, "email-hash", 7L, "c", "l", CREATED, CREATED, 0, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expiresAt");
  }
}
