package org.mwolff.manban.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Prüft das ES256-JWT nach RFC 8292 (Issue #1996): Die Signatur hält der Prüfung durch das JDK
 * stand, und die Ansprüche {@code aud}, {@code exp}, {@code sub} stimmen.
 */
class VapidSignaturTest {

  private static final String OEFFENTLICH =
      "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
  private static final String PRIVAT = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
  private static final String SUBJECT = "mailto:betrieb@example.org";
  private static final Instant JETZT = Instant.parse("2026-10-29T12:00:00Z");

  private final ObjectMapper json = new ObjectMapper();
  private final VapidSchluessel schluessel = VapidSchluessel.aus(OEFFENTLICH, PRIVAT);
  private final VapidSignatur signatur =
      new VapidSignatur(schluessel, SUBJECT, Clock.fixed(JETZT, ZoneOffset.UTC));

  @Test
  void kopfzeile_traegtTokenUndOeffentlichenSchluessel() {
    // When
    String kopfzeile = signatur.kopfzeile(URI.create("https://push.example.net/push/abc?x=1"));

    // Then
    assertThat(kopfzeile).startsWith("vapid t=").endsWith(", k=" + OEFFENTLICH);
  }

  @Test
  void signatur_bestehtDieJdkPruefung() throws GeneralSecurityException {
    // Given
    String[] teile = token("https://push.example.net/push/abc").split("\\.", -1);
    KeyPair paar = schluessel.schluesselpaar();

    // When
    Signature pruefung = Signature.getInstance("SHA256withECDSAinP1363Format");
    pruefung.initVerify(paar.getPublic());
    pruefung.update((teile[0] + "." + teile[1]).getBytes(StandardCharsets.US_ASCII));

    // Then
    assertThat(teile).hasSize(3);
    assertThat(Base64.getUrlDecoder().decode(teile[2])).hasSize(64);
    assertThat(pruefung.verify(Base64.getUrlDecoder().decode(teile[2]))).isTrue();
  }

  @Test
  void kopf_nenntEs256() throws Exception {
    // When
    JsonNode kopf = teil(token("https://push.example.net/x"), 0);

    // Then
    assertThat(kopf.get("alg").asText()).isEqualTo("ES256");
    assertThat(kopf.get("typ").asText()).isEqualTo("JWT");
  }

  @Test
  void ansprueche_audIstDerUrsprung_expHoechstens24Stunden_subIstDasSubject() throws Exception {
    // When
    JsonNode ansprueche = teil(token("https://push.example.net:8443/push/abc?x=1"), 1);

    // Then
    assertThat(ansprueche.get("aud").asText()).isEqualTo("https://push.example.net:8443");
    assertThat(ansprueche.get("sub").asText()).isEqualTo(SUBJECT);
    long exp = ansprueche.get("exp").asLong();
    assertThat(exp).isGreaterThan(JETZT.getEpochSecond());
    assertThat(exp).isLessThanOrEqualTo(JETZT.plusSeconds(24 * 3600).getEpochSecond());
  }

  @Test
  void aud_laesstDenStandardportWeg() throws Exception {
    // When
    JsonNode ansprueche = teil(token("https://push.example.net:443/push"), 1);

    // Then
    assertThat(ansprueche.get("aud").asText()).isEqualTo("https://push.example.net");
  }

  @Test
  void aud_einesHttpEndpunktsMitPort80_laesstIhnWeg() throws Exception {
    // When
    JsonNode ansprueche = teil(token("http://localhost:80/push"), 1);

    // Then
    assertThat(ansprueche.get("aud").asText()).isEqualTo("http://localhost");
  }

  @Test
  void aud_einesHttpsEndpunktsMitPort80_behaeltIhn() throws Exception {
    // When
    JsonNode ansprueche = teil(token("HTTPS://Push.Example.net:80/push"), 1);

    // Then: Schema und Host klein, Port 80 ist für https kein Standardport.
    assertThat(ansprueche.get("aud").asText()).isEqualTo("https://push.example.net:80");
  }

  @Test
  void subjectMitAnfuehrungszeichen_bleibtGueltigesJson() throws Exception {
    // Given
    String subject = "mailto:\"betrieb\"\\@example.org";
    VapidSignatur mitZeichen = new VapidSignatur(schluessel, subject, Clock.systemUTC());
    String kopfzeile = mitZeichen.kopfzeile(URI.create("https://push.example.net/x"));
    String token = kopfzeile.substring("vapid t=".length(), kopfzeile.indexOf(", k="));

    // When
    JsonNode ansprueche = teil(token, 1);

    // Then
    assertThat(ansprueche.get("sub").asText()).isEqualTo(subject);
  }

  @Test
  void toString_nenntKeinenSchluessel() {
    assertThat(schluessel.toString()).doesNotContain(OEFFENTLICH).doesNotContain(PRIVAT);
  }

  @Test
  void zweiSignaturen_sindVerschieden_weilEcdsaZufallNutzt() {
    // When / Then: Schutz gegen eine versehentlich deterministische oder zwischengespeicherte
    // Signatur — beide Token bleiben trotzdem gültig.
    assertThat(token("https://push.example.net/x"))
        .isNotEqualTo(token("https://push.example.net/x"));
  }

  private String token(String endpunkt) {
    String kopfzeile = signatur.kopfzeile(URI.create(endpunkt));
    return kopfzeile.substring("vapid t=".length(), kopfzeile.indexOf(", k="));
  }

  private JsonNode teil(String token, int index) throws Exception {
    return json.readTree(Base64.getUrlDecoder().decode(token.split("\\.", -1)[index]));
  }
}
