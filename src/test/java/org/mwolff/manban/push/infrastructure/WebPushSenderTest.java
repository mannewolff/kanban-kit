package org.mwolff.manban.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prüft den Versand gegen einen lokalen Testserver (Issue #1996): Kopfzeilen nach RFC
 * 8030/8291/8292, Aufbau des Rumpfs und der zurückgegebene HTTP-Status.
 */
class WebPushSenderTest {

  private static final String OEFFENTLICH =
      "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
  private static final String PRIVAT = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
  private static final byte[] P256DH =
      Base64.getUrlDecoder()
          .decode(
              "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIA"
                  + "iw4");
  private static final byte[] AUTH = Base64.getUrlDecoder().decode("BTBZMqHH6r4Tts7J_aSIgg");
  private static final byte[] KLARTEXT = "Lauf festgefahren".getBytes(StandardCharsets.UTF_8);

  // Als Map statt Headers (PMD LooseCoupling); get() normalisiert den Namen weiter, weil der
  // Laufzeittyp Headers bleibt.
  private final List<Map<String, List<String>>> kopfzeilen = new ArrayList<>();
  private final List<byte[]> ruempfe = new ArrayList<>();
  private final List<String> methoden = new ArrayList<>();
  private final AtomicInteger antwortStatus = new AtomicInteger(201);
  private HttpServer server;
  private WebPushSender sender;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/push",
        austausch -> {
          try (InputStream rumpf = austausch.getRequestBody()) {
            ruempfe.add(rumpf.readAllBytes());
          }
          kopfzeilen.add(austausch.getRequestHeaders());
          methoden.add(austausch.getRequestMethod());
          austausch.sendResponseHeaders(antwortStatus.get(), -1);
          austausch.close();
        });
    server.start();
    VapidSignatur signatur =
        new VapidSignatur(
            VapidSchluessel.aus(OEFFENTLICH, PRIVAT),
            "mailto:betrieb@example.org",
            Clock.systemUTC());
    sender = new WebPushSender(HttpClient.newHttpClient(), signatur, new SecureRandom());
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void sendetMitDenKopfzeilenDesStandards_undLiefertDenStatus() throws Exception {
    // When
    int status = sender.senden(endpunkt(), P256DH, AUTH, KLARTEXT, Duration.ofHours(1));

    // Then
    assertThat(status).isEqualTo(201);
    assertThat(methoden).containsExactly("POST");
    Map<String, List<String>> kopf = kopfzeilen.getFirst();
    assertThat(kopf.get("Content-Encoding").getFirst()).isEqualTo("aes128gcm");
    assertThat(kopf.get("Content-Type").getFirst()).isEqualTo("application/octet-stream");
    assertThat(kopf.get("TTL").getFirst()).isEqualTo("3600");
    assertThat(kopf.get("Urgency").getFirst()).isEqualTo("high");
    assertThat(kopf.get("Authorization").getFirst())
        .startsWith("vapid t=")
        .endsWith(", k=" + OEFFENTLICH);
  }

  @Test
  void rumpf_istEinAes128gcmDatensatzMitFrischemSaltUndSchluessel() throws Exception {
    // When
    sender.senden(endpunkt(), P256DH, AUTH, KLARTEXT, Duration.ofMinutes(5));
    sender.senden(endpunkt(), P256DH, AUTH, KLARTEXT, Duration.ofMinutes(5));

    // Then: Kopf aus Salt (16), Datensatzgröße (4), Schlüssellänge (1), Schlüssel (65); dahinter
    // Klartext, Begrenzer und GCM-Prüfsumme.
    byte[] erster = ruempfe.get(0);
    byte[] zweiter = ruempfe.get(1);
    assertThat(erster).hasSize(86 + KLARTEXT.length + 1 + 16);
    assertThat(Arrays.copyOfRange(erster, 16, 21)).containsExactly(0, 0, 0x10, 0, 65);
    assertThat(erster[21]).isEqualTo((byte) 0x04);
    assertThat(Arrays.copyOfRange(erster, 0, 16)).isNotEqualTo(Arrays.copyOfRange(zweiter, 0, 16));
    assertThat(Arrays.copyOfRange(erster, 21, 86))
        .isNotEqualTo(Arrays.copyOfRange(zweiter, 21, 86));
  }

  @Test
  void ttl_wirdAufGanzeSekundenAbgerundet_undNieNegativ() throws Exception {
    // When
    sender.senden(endpunkt(), P256DH, AUTH, KLARTEXT, Duration.ofMillis(1500));
    sender.senden(endpunkt(), P256DH, AUTH, KLARTEXT, Duration.ofSeconds(-3));

    // Then
    assertThat(kopfzeilen.get(0).get("TTL").getFirst()).isEqualTo("1");
    assertThat(kopfzeilen.get(1).get("TTL").getFirst()).isEqualTo("0");
  }

  @Test
  void liefertAuchEinenAblehnendenStatusZurueck() throws Exception {
    // Given: 410 Gone — das Abo gibt es beim Push-Dienst nicht mehr.
    antwortStatus.set(410);

    // When
    int status = sender.senden(endpunkt(), P256DH, AUTH, KLARTEXT, Duration.ofHours(1));

    // Then
    assertThat(status).isEqualTo(410);
  }

  private URI endpunkt() {
    return URI.create(
        "http://"
            + server.getAddress().getHostString()
            + ":"
            + server.getAddress().getPort()
            + "/push/abo-1");
  }
}
