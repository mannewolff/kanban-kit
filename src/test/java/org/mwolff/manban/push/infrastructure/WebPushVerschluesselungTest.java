package org.mwolff.manban.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Prüft die Nachrichtenverschlüsselung nach RFC 8291 gegen den Beispielvektor aus Anhang A (Issue
 * #1996, Plan #1989 E15): Mit Salt und Schlüsselpaar des Anwendungsservers aus dem Beispiel muss
 * genau der dort abgedruckte Chiffretext entstehen, Byte für Byte.
 */
class WebPushVerschluesselungTest {

  private static final String KLARTEXT = "When I grow up, I want to be a watermelon";
  private static final String AS_PRIVAT = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
  private static final String AS_OEFFENTLICH =
      "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
  private static final String UA_OEFFENTLICH =
      "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
  private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
  private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
  private static final String CHIFFRETEXT =
      "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS"
          + "6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQ"
          + "exSgSxsj_Qulcy4a-fN";

  @Test
  void reproduziertDenChiffretextAusRfc8291AnhangA() {
    // Given
    KeyPair server = P256.schluesselpaar(b64(AS_OEFFENTLICH), b64(AS_PRIVAT));

    // When
    byte[] ergebnis =
        WebPushVerschluesselung.verschluesseln(
            KLARTEXT.getBytes(StandardCharsets.UTF_8),
            b64(UA_OEFFENTLICH),
            b64(AUTH),
            b64(SALT),
            server);

    // Then
    assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(ergebnis))
        .isEqualTo(CHIFFRETEXT);
  }

  @Test
  void klartextAnDerObergrenze_wirdVerschluesselt_einByteMehrNicht() {
    // Given
    KeyPair server = P256.schluesselpaar(b64(AS_OEFFENTLICH), b64(AS_PRIVAT));
    byte[] grenze = new byte[WebPushVerschluesselung.MAX_KLARTEXT];
    byte[] zuLang = new byte[WebPushVerschluesselung.MAX_KLARTEXT + 1];

    // When
    byte[] ergebnis =
        WebPushVerschluesselung.verschluesseln(
            grenze, b64(UA_OEFFENTLICH), b64(AUTH), b64(SALT), server);

    // Then: Die ganze Nachricht passt in die 4096 Bytes, die Push-Dienste annehmen.
    assertThat(ergebnis).hasSize(4096);
    assertThatThrownBy(
            () ->
                WebPushVerschluesselung.verschluesseln(
                    zuLang, b64(UA_OEFFENTLICH), b64(AUTH), b64(SALT), server))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void falscheLaengen_werdenAbgewiesen() {
    // Given
    KeyPair server = P256.schluesselpaar(b64(AS_OEFFENTLICH), b64(AS_PRIVAT));
    byte[] klartext = KLARTEXT.getBytes(StandardCharsets.UTF_8);
    byte[] p256dh = b64(UA_OEFFENTLICH);
    byte[] auth = b64(AUTH);
    byte[] salt = b64(SALT);

    // When / Then
    assertThatThrownBy(
            () ->
                WebPushVerschluesselung.verschluesseln(
                    klartext, p256dh, Arrays.copyOf(auth, 15), salt, server))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                WebPushVerschluesselung.verschluesseln(
                    klartext, p256dh, auth, Arrays.copyOf(salt, 15), server))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                WebPushVerschluesselung.verschluesseln(
                    klartext, Arrays.copyOf(p256dh, 64), auth, salt, server))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void punktAusserhalbDerKurve_wirdAbgewiesen() {
    // Given: letztes Byte der y-Koordinate verändert — der Punkt liegt nicht mehr auf P-256.
    KeyPair server = P256.schluesselpaar(b64(AS_OEFFENTLICH), b64(AS_PRIVAT));
    byte[] p256dh = b64(UA_OEFFENTLICH);
    p256dh[64] ^= 1;

    // When / Then
    assertThatThrownBy(
            () ->
                WebPushVerschluesselung.verschluesseln(
                    KLARTEXT.getBytes(StandardCharsets.UTF_8),
                    p256dh,
                    b64(AUTH),
                    b64(SALT),
                    server))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static byte[] b64(String wert) {
    return Base64.getUrlDecoder().decode(wert);
  }
}
