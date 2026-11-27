package org.mwolff.manban.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.nightrun.domain.Meldegrund.Grund;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.push.application.LaufNachricht;
import org.mwolff.manban.push.application.PushAboRepository;
import org.mwolff.manban.push.application.PushProperties;
import org.mwolff.manban.push.domain.PushAbo;

/**
 * Wohin der Handler mit dem echten Versand Verbindungen aufbaut (Issue #2290): zu einem Push-Dienst
 * ja, zu einem Ziel im Netz des Servers nie. Das Ziel im Servernetz ist ein lauschender Testserver
 * auf Loopback — kommt bei ihm eine Anfrage an, hat der Angriff gewirkt.
 */
class LaufNachrichtHandlerZielTest {

  private static final Instant JETZT = Instant.parse("2026-10-29T03:01:00Z");
  private static final Instant GEMELDET = Instant.parse("2026-10-29T03:00:00Z");
  private static final long KONTO = 5L;
  private static final long PROJEKT = 7L;
  private static final long ABO_ID = 11L;
  private static final String P256DH =
      "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
  private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

  /** Das VAPID-Paar aus {@code WebPushSenderTest}. */
  private static final PushProperties VAPID =
      new PushProperties(
          "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
          "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw",
          "mailto:betrieb@example.org",
          null);

  private final List<String> angekommen = new ArrayList<>();
  private PushAboRepository abos;
  private UserLookup users;
  private PermissionChecker permissions;
  private HttpServer server;

  @BeforeEach
  void setUp() throws IOException {
    abos = mock(PushAboRepository.class);
    users = mock(UserLookup.class);
    permissions = mock(PermissionChecker.class);
    when(users.istAktiv(KONTO)).thenReturn(true);
    when(permissions.hatNachtlaufZugriff(KONTO, PROJEKT)).thenReturn(true);
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        austausch -> {
          angekommen.add(austausch.getRequestHeaders().getFirst("Authorization"));
          austausch.getRequestBody().readAllBytes();
          austausch.sendResponseHeaders(201, -1);
          austausch.close();
        });
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private int port() {
    return server.getAddress().getPort();
  }

  private void abo(String endpunkt) {
    when(abos.findById(ABO_ID))
        .thenReturn(
            Optional.of(
                new PushAbo(
                    ABO_ID,
                    KONTO,
                    endpunkt,
                    P256DH,
                    AUTH,
                    GEMELDET.minus(Duration.ofDays(1)),
                    JETZT.plus(Duration.ofDays(29)),
                    Set.of())));
  }

  /** Der Handler, wie Spring ihn baut: mit dem echten Versand über Web Push. */
  private LaufNachrichtHandler handler() {
    return new LaufNachrichtHandler(
        abos,
        users,
        permissions,
        SprachFixture.mit(Sprache.DE),
        VAPID,
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static String gescheitert() {
    return new LaufNachricht(
            300L, ABO_ID, KONTO, PROJEKT, "Alpha · Produkt", Grund.GESCHEITERT, 1975, GEMELDET)
        .payload();
  }

  /**
   * Der Angriff aus dem Sicherheitsbefund: Ein Projekt-Owner hat als Push-Adresse ein Ziel im Netz
   * des Servers hinterlegt — etwa, bevor das Anlegen es abwies. Bei einer Laufbenachrichtigung
   * entsteht keine Verbindung dorthin; das unzustellbare Abo wird gelöscht.
   */
  @Test
  void einZielImServernetz_bekommtKeineVerbindung_undDasAboWirdGeloescht() {
    for (String ziel :
        List.of(
            "https://127.0.0.1:" + port() + "/internal",
            "https://localhost:" + port() + "/admin",
            "http://127.0.0.1:" + port() + "/klartext")) {
      abo(ziel);

      handler().handle(gescheitert());
    }

    assertThat(angekommen).isEmpty();
    verify(abos, times(3)).deleteById(ABO_ID);
    verify(abos, never()).save(any());
  }

  /**
   * Gegenprobe: Der echte Versand stellt mit VAPID-Signatur zu, sobald er gerufen wird. Der Test
   * ruft die Zustellung direkt, weil der Handler den Testserver auf Loopback nicht anspräche.
   */
  @Test
  void derEchteVersand_stelltMitVapidSignaturZu() throws Exception {
    LaufNachrichtHandler.Zustellung zustellung =
        Objects.requireNonNull(LaufNachrichtHandler.webPush(VAPID, Clock.systemUTC()));

    int status =
        zustellung.senden(
            URI.create("http://127.0.0.1:" + port() + "/push"),
            Base64.getUrlDecoder().decode(P256DH),
            Base64.getUrlDecoder().decode(AUTH),
            "{}".getBytes(StandardCharsets.UTF_8),
            Duration.ofMinutes(1));

    assertThat(status).isEqualTo(201);
    assertThat(angekommen).singleElement().satisfies(k -> assertThat(k).startsWith("vapid t="));
  }
}
