package org.mwolff.manban.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
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
 * Der Versand einer geplanten Nachricht (Issue #1998, Plan #1989 E17, E21): Vor dem Senden prüft
 * der Handler noch einmal Konto, Recht und Gültigkeit des Abos; 404 und 410 löschen das Abo, andere
 * Fehler werfen, damit die Outbox wiederholt.
 */
class LaufNachrichtHandlerTest {

  private static final Instant JETZT = Instant.parse("2026-10-29T03:01:00Z");
  private static final Instant GEMELDET = Instant.parse("2026-10-29T03:00:00Z");
  private static final long KONTO = 5L;
  private static final long PROJEKT = 7L;
  private static final long ABO_ID = 11L;
  private static final String P256DH =
      "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
  private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

  /** Das VAPID-Paar aus {@code WebPushSenderTest}. */
  private static final String VAPID_OEFFENTLICH =
      "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A"
          + "8";

  /** Ein Aufruf an den Push-Dienst, wie der Handler ihn absetzt. */
  private record Aufruf(URI endpunkt, String p256dh, String auth, String klartext, Duration ttl) {}

  private final List<Aufruf> aufrufe = new ArrayList<>();
  private final AtomicInteger antwort = new AtomicInteger(201);
  private final ObjectMapper json = new ObjectMapper();
  private PushAboRepository abos;
  private UserLookup users;
  private PermissionChecker permissions;

  @BeforeEach
  void setUp() {
    abos = mock(PushAboRepository.class);
    users = mock(UserLookup.class);
    permissions = mock(PermissionChecker.class);
    when(abos.findById(anyLong())).thenReturn(Optional.empty());
    when(users.istAktiv(KONTO)).thenReturn(true);
    when(permissions.hatNachtlaufZugriff(KONTO, PROJEKT)).thenReturn(true);
  }

  private LaufNachrichtHandler handler(Sprache sprache) {
    return handler(
        sprache,
        (endpunkt, p256dh, auth, klartext, ttl) -> {
          aufrufe.add(
              new Aufruf(
                  endpunkt,
                  Base64.getUrlEncoder().withoutPadding().encodeToString(p256dh),
                  Base64.getUrlEncoder().withoutPadding().encodeToString(auth),
                  new String(klartext, StandardCharsets.UTF_8),
                  ttl));
          return antwort.get();
        });
  }

  private LaufNachrichtHandler handler(
      Sprache sprache, LaufNachrichtHandler.@Nullable Zustellung zustellung) {
    return new LaufNachrichtHandler(
        abos,
        users,
        permissions,
        SprachFixture.mit(sprache),
        zustellung,
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private LaufNachrichtHandler handler() {
    return handler(Sprache.DE);
  }

  private PushAbo abo(long userId, Instant angelegt, Instant giltBis) {
    PushAbo abo =
        new PushAbo(
            ABO_ID,
            userId,
            "https://push.example/geraet",
            P256DH,
            AUTH,
            angelegt,
            giltBis,
            Set.of());
    when(abos.findById(ABO_ID)).thenReturn(Optional.of(abo));
    return abo;
  }

  private PushAbo abo() {
    return abo(KONTO, GEMELDET.minus(Duration.ofDays(1)), JETZT.plus(Duration.ofDays(29)));
  }

  private static String nachricht(Grund grund, @Nullable Integer karte, String titel) {
    return new LaufNachricht(300L, ABO_ID, KONTO, PROJEKT, titel, grund, karte, GEMELDET).payload();
  }

  private static String gescheitert() {
    return nachricht(Grund.GESCHEITERT, 1975, "Alpha · Produkt");
  }

  private String feld(Aufruf aufruf, String name) {
    try {
      return json.readTree(aufruf.klartext()).get(name).asText();
    } catch (IOException e) {
      throw new AssertionError("Keine JSON-Nachricht: " + aufruf.klartext(), e);
    }
  }

  private JsonNode gesendet() throws IOException {
    assertThat(aufrufe).hasSize(1);
    return json.readTree(aufrufe.getFirst().klartext());
  }

  // --- Versand -------------------------------------------------------------------------------

  @Test
  void eventTypePasstZurPlanung() {
    assertThat(handler().eventType()).isEqualTo(LaufNachricht.TYP);
  }

  @Test
  void sendetTitelZustandMitGrundUndAdresseAnDasGeraet() throws IOException {
    abo();

    handler().handle(gescheitert());

    JsonNode nachricht = gesendet();
    assertThat(nachricht.get("title").asText()).isEqualTo("Alpha · Produkt");
    assertThat(nachricht.get("body").asText())
        .isEqualTo("Lauf gescheitert: Paket #1975 ist gescheitert");
    assertThat(nachricht.get("url").asText()).isEqualTo("/mobil/laeufe/300");
    Aufruf aufruf = aufrufe.getFirst();
    assertThat(aufruf.endpunkt()).isEqualTo(URI.create("https://push.example/geraet"));
    assertThat(aufruf.p256dh()).isEqualTo(P256DH);
    assertThat(aufruf.auth()).isEqualTo(AUTH);
    assertThat(aufruf.ttl()).isEqualTo(LaufNachrichtHandler.AUFBEWAHRUNG);
  }

  @Test
  void nenntJedenGrundInDerSpracheDerInstallation() {
    abo();
    LaufNachrichtHandler handler = handler(Sprache.EN);

    handler.handle(nachricht(Grund.WARTET, 12, "Alpha"));
    handler.handle(nachricht(Grund.FESTGEFAHREN, 13, "Alpha"));
    handler.handle(nachricht(Grund.ZEITBUDGET, 14, "Alpha"));
    handler.handle(nachricht(Grund.VERSTUMMT, null, "Alpha"));

    assertThat(aufrufe)
        .extracting(a -> feld(a, "body"))
        .containsExactly(
            "Run is stuck: Package #12 is waiting for a decision",
            "Run is stuck: Package #13 is stuck on a check",
            "Run failed: Package #14 ran out of time",
            "Run failed: The run has gone silent");
  }

  @Test
  void maskiertTitelFuerJson() throws IOException {
    abo();

    handler().handle(nachricht(Grund.GESCHEITERT, 1, "Al\"pha · \\Pro\ndukt"));

    assertThat(gesendet().get("title").asText()).isEqualTo("Al\"pha · \\Pro\ndukt");
  }

  // --- Versandfehler (E21) -------------------------------------------------------------------

  @Test
  void notFoundLoeschtDasAboUndMeldetErfolg() {
    abo();
    antwort.set(404);

    handler().handle(gescheitert());

    verify(abos).deleteById(ABO_ID);
  }

  @Test
  void goneLoeschtDasAboUndMeldetErfolg() {
    abo();
    antwort.set(410);

    handler().handle(gescheitert());

    verify(abos).deleteById(ABO_ID);
  }

  @Test
  void einServerfehlerWirftDamitDieOutboxWiederholt() {
    abo();
    antwort.set(500);
    LaufNachrichtHandler handler = handler();

    assertThatThrownBy(() -> handler.handle(gescheitert()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("500");
    verify(abos, never()).deleteById(anyLong());
  }

  @Test
  void einUmleitungsstatusGiltAlsFehler() {
    abo();
    antwort.set(301);
    LaufNachrichtHandler handler = handler();

    assertThatThrownBy(() -> handler.handle(gescheitert()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void einNichtErreichbarerDienstWirft() {
    abo();
    LaufNachrichtHandler handler =
        handler(
            Sprache.DE,
            (endpunkt, p256dh, auth, klartext, ttl) -> {
              throw new IOException("keine Verbindung");
            });

    assertThatThrownBy(() -> handler.handle(gescheitert()))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void eineUnterbrechungWirftUndSetztDieMarkeWieder() {
    abo();
    LaufNachrichtHandler handler =
        handler(
            Sprache.DE,
            (endpunkt, p256dh, auth, klartext, ttl) -> {
              throw new InterruptedException("Abbruch");
            });

    try {
      assertThatThrownBy(() -> handler.handle(gescheitert()))
          .isInstanceOf(IllegalStateException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  // --- Prüfung vor dem Senden (E21) ----------------------------------------------------------

  @Test
  void einEntzogenesRechtVerhindertDenVersand() {
    abo();
    when(permissions.hatNachtlaufZugriff(KONTO, PROJEKT)).thenReturn(false);

    handler().handle(gescheitert());

    assertThat(aufrufe).isEmpty();
  }

  @Test
  void einInaktivesKontoBekommtNichts() {
    abo();
    when(users.istAktiv(KONTO)).thenReturn(false);

    handler().handle(gescheitert());

    assertThat(aufrufe).isEmpty();
  }

  @Test
  void einAbgelaufenesAboBekommtNichts() {
    abo(KONTO, GEMELDET.minus(Duration.ofDays(30)), JETZT);

    handler().handle(gescheitert());

    assertThat(aufrufe).isEmpty();
  }

  @Test
  void einGeloeschtesAboBekommtNichts() {
    handler().handle(gescheitert());

    assertThat(aufrufe).isEmpty();
  }

  @Test
  void einAboEinesAnderenKontosAufDemselbenGeraetBekommtNichts() {
    // Das Abo begann nach dem Abmelden mit einem anderen Konto neu (Issue #1997).
    abo(99L, GEMELDET.minus(Duration.ofDays(1)), JETZT.plus(Duration.ofDays(29)));

    handler().handle(gescheitert());

    assertThat(aufrufe).isEmpty();
  }

  @Test
  void einNachDemMeldenNeuBegonnenesAboBekommtNichts() {
    abo(KONTO, GEMELDET.plusSeconds(30), JETZT.plus(Duration.ofDays(30)));

    handler().handle(gescheitert());

    assertThat(aufrufe).isEmpty();
  }

  @Test
  void ohnePushVerwirftDerHandlerDieNachricht() {
    abo();

    handler(Sprache.DE, null).handle(gescheitert());

    verify(abos, never()).findById(anyLong());
  }

  @Test
  void einePayloadOhnePassendeFelderWirft() {
    LaufNachrichtHandler handler = handler();

    assertThatThrownBy(() -> handler.handle("kaputt")).isInstanceOf(RuntimeException.class);
  }

  // --- Verdrahtung ---------------------------------------------------------------------------

  @Test
  void ohneSchluesselGibtEsKeineZustellung() {
    assertThat(
            LaufNachrichtHandler.webPush(
                new PushProperties(null, null, null, null), Clock.systemUTC()))
        .isNull();
  }

  @Test
  void mitSchluesselnStelltDerSpringKonstruktorUeberWebPushZu() throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    List<String> kopf = new ArrayList<>();
    server.createContext(
        "/push",
        austausch -> {
          kopf.add(austausch.getRequestHeaders().getFirst("Authorization"));
          austausch.getRequestBody().readAllBytes();
          austausch.sendResponseHeaders(201, -1);
          austausch.close();
        });
    server.start();
    try {
      String endpunkt = "http://127.0.0.1:" + server.getAddress().getPort() + "/push";
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
      PushProperties properties =
          new PushProperties(
              VAPID_OEFFENTLICH,
              "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw",
              "mailto:betrieb@example.org",
              null);

      new LaufNachrichtHandler(
              abos,
              users,
              permissions,
              SprachFixture.mit(Sprache.DE),
              properties,
              Clock.fixed(JETZT, ZoneOffset.UTC))
          .handle(gescheitert());

      assertThat(kopf).singleElement().satisfies(k -> assertThat(k).startsWith("vapid t="));
    } finally {
      server.stop(0);
    }
  }
}
