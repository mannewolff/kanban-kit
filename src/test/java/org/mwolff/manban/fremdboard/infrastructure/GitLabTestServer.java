package org.mwolff.manban.fremdboard.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.domain.Verbindung;

/**
 * Stellt GitLab für die Tests der {@link GitLabQuelle} nach (Issue #2160): dasselbe Mittel wie in
 * {@code WebPushSenderTest}, hier als {@link HttpsServer}, weil {@link HostErlaubnis} nur {@code
 * https} durchlässt. Zertifikat für {@code localhost} und aufgezeichnete Antworten liegen unter
 * {@code src/test/resources/fremdboard/gitlab/}. Unbekannte Pfade beantwortet er mit 404.
 */
public final class GitLabTestServer implements AutoCloseable {

  public static final String ZUGANG = "glpat-geheim-0123456789";
  public static final String PROJEKT = "/api/v4/projects/gruppe%2Fprojekt";
  static final String ISSUES = PROJEKT + "/issues?state=all&per_page=100&page=";

  /** Schlüssel des Tresors; wer einen Zugang dieses Servers entschlüsseln will, setzt ihn. */
  public static final String SCHLUESSEL =
      "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

  private static final char[] KEYSTORE_KENNWORT = "nurfuertests".toCharArray();

  private record Antwort(int status, String rumpf, @Nullable String naechsteSeite) {}

  private final Map<String, Antwort> antworten = new ConcurrentHashMap<>();
  private final List<String> abgerufen = new CopyOnWriteArrayList<>();
  private final List<String> gesendeteZugaenge = new CopyOnWriteArrayList<>();
  private final HttpsServer server;
  private final HttpClient client;
  private final ZugangsTresor tresor = new ZugangsTresor(SCHLUESSEL, new SecureRandom());
  private final HostErlaubnis hostErlaubnis;
  private final GitLabQuelle gitLabQuelle;
  private volatile Duration verzoegerung = Duration.ZERO;
  private volatile boolean ohneEnde;
  private volatile long flutBytes;
  private final AtomicLong gefluteteBytes = new AtomicLong();
  private final CountDownLatch flutEnde = new CountDownLatch(1);

  public GitLabTestServer() throws IOException, GeneralSecurityException {
    SSLContext tls = tlsKontext();
    server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(tls));
    server.createContext("/", this::beantworten);
    server.start();
    client =
        HttpClient.newBuilder()
            .sslContext(tls)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    hostErlaubnis = new HostErlaubnis(List.of("localhost"), client, Duration.ofMillis(500));
    gitLabQuelle = new GitLabQuelle(hostErlaubnis, tresor);
  }

  GitLabQuelle quelle() {
    return gitLabQuelle;
  }

  /**
   * Die Erlaubnis für {@code localhost} mit dem Client, der dem Zertifikat dieses Servers traut —
   * für Tests, die die {@link GitLabQuelle} des Anwendungskontexts gegen ihn richten (Issue #2167).
   */
  public HostErlaubnis erlaubnis() {
    return hostErlaubnis;
  }

  /** {@link #ZUGANG}, verschlüsselt mit {@link #SCHLUESSEL}. */
  public String zugangVerschluesselt() {
    return tresor.verschluesseln(ZUGANG);
  }

  /** Beantwortet {@code pfadUndAnfrage} (roh, wie sie ankommt) mit Status, Rumpf und Folgeseite. */
  public void antworte(
      String pfadUndAnfrage, int status, String rumpf, @Nullable String naechsteSeite) {
    antworten.put(pfadUndAnfrage, new Antwort(status, rumpf, naechsteSeite));
  }

  /** Vergisst alle Antworten und Abrufe — für einen Server, den mehrere Tests teilen. */
  public void zuruecksetzen() {
    antworten.clear();
    abgerufen.clear();
    gesendeteZugaenge.clear();
  }

  /** Lässt jede Antwort um {@code dauer} warten. */
  void verzoegere(Duration dauer) {
    verzoegerung = dauer;
  }

  /** Beantwortet jede unbekannte Seite mit einer leeren Liste und einer weiteren Folgeseite. */
  void endloseSeiten() {
    ohneEnde = true;
  }

  /**
   * Stellt den Angriff aus Issue #2196 nach: Jeder Abruf bekommt Status 200 ohne Längenangabe und
   * einen Rumpf, der bis {@code hoechstens} Bytes weiterläuft — oder bis der Abrufer die Verbindung
   * schließt.
   */
  void flute(long hoechstens) {
    flutBytes = hoechstens;
  }

  /** Bytes, die der geflutete Rumpf bis zu seinem Ende tatsächlich losgeworden ist. */
  long geflutet() {
    return gefluteteBytes.get();
  }

  /**
   * Wartet, bis der geflutete Rumpf geendet hat; {@code false}, wenn das nicht rechtzeitig geschah.
   */
  boolean flutBeendet(Duration hoechstens) throws InterruptedException {
    return flutEnde.await(hoechstens.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** Die abgerufenen Pfade samt Anfrage, roh und in Reihenfolge. */
  public List<String> abrufe() {
    return abgerufen;
  }

  /** Der Kopf {@code PRIVATE-TOKEN} je Abruf. */
  List<String> zugaenge() {
    return gesendeteZugaenge;
  }

  public String basis() {
    return "https://localhost:" + server.getAddress().getPort();
  }

  Verbindung verbindung() {
    return verbindung(basis());
  }

  Verbindung verbindung(String basisUrl) {
    return new Verbindung(
        1L,
        FremdBoardArt.GITLAB,
        basisUrl,
        "gruppe/projekt",
        tresor.verschluesseln(ZUGANG),
        "kit-bot",
        new StatusLabels("Backlog", "Ready", "In Progress", "In Review"),
        10L,
        Instant.parse("2026-11-01T00:00:00Z"),
        null,
        null,
        null);
  }

  /** Hält den Server an; danach ist GitLab nicht erreichbar. */
  public void stoppe() {
    server.stop(0);
  }

  @Override
  public void close() {
    server.stop(0);
    client.close();
  }

  /** Eine aufgezeichnete Antwort aus {@code src/test/resources/fremdboard/gitlab/}. */
  public static String datei(String name) {
    try (InputStream ein = GitLabTestServer.class.getResourceAsStream(pfad(name))) {
      if (ein == null) {
        throw new IllegalStateException("Aufzeichnung fehlt: " + name);
      }
      return new String(ein.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void beantworten(HttpExchange austausch) throws IOException {
    String roh = austausch.getRequestURI().getRawPath();
    String anfrage = austausch.getRequestURI().getRawQuery();
    String schluessel = anfrage == null ? roh : roh + "?" + anfrage;
    abgerufen.add(schluessel);
    gesendeteZugaenge.add(austausch.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
    warten();
    if (flutBytes > 0) {
      fluten(austausch);
      return;
    }
    Antwort antwort = antworten.get(schluessel);
    if (antwort == null && ohneEnde) {
      int seite = Integer.parseInt(schluessel.substring(schluessel.lastIndexOf('=') + 1));
      antwort = new Antwort(200, "[]", String.valueOf(seite + 1));
    }
    if (antwort == null) {
      antwort = new Antwort(404, "{\"message\":\"404 Not Found\"}", null);
    }
    if (antwort.naechsteSeite() != null) {
      austausch.getResponseHeaders().add("X-Next-Page", antwort.naechsteSeite());
    }
    byte[] rumpf = antwort.rumpf().getBytes(StandardCharsets.UTF_8);
    austausch.getResponseHeaders().add("Content-Type", "application/json");
    austausch.sendResponseHeaders(antwort.status(), rumpf.length == 0 ? -1 : rumpf.length);
    try (OutputStream aus = austausch.getResponseBody()) {
      aus.write(rumpf);
    }
  }

  private void fluten(HttpExchange austausch) throws IOException {
    byte[] stueck = new byte[64 * 1024];
    Arrays.fill(stueck, (byte) ' ');
    stueck[0] = '[';
    austausch.getResponseHeaders().add("Content-Type", "application/json");
    austausch.sendResponseHeaders(200, 0);
    try (OutputStream aus = austausch.getResponseBody()) {
      while (gefluteteBytes.get() < flutBytes) {
        aus.write(stueck);
        aus.flush();
        gefluteteBytes.addAndGet(stueck.length);
        stueck[0] = ' ';
      }
    } catch (IOException ignored) {
      // Der Abrufer hat die Verbindung geschlossen — genau das soll die Abwehr tun.
    } finally {
      flutEnde.countDown();
    }
  }

  private void warten() {
    try {
      Thread.sleep(verzoegerung.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String pfad(String name) {
    return "/fremdboard/gitlab/" + name;
  }

  private static SSLContext tlsKontext() throws IOException, GeneralSecurityException {
    KeyStore schluesselbund = KeyStore.getInstance("PKCS12");
    try (InputStream ein = GitLabTestServer.class.getResourceAsStream(pfad("testserver.p12"))) {
      schluesselbund.load(ein, KEYSTORE_KENNWORT);
    }
    KeyManagerFactory schluessel =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    schluessel.init(schluesselbund, KEYSTORE_KENNWORT);
    TrustManagerFactory vertrauen =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    vertrauen.init(schluesselbund);
    SSLContext kontext = SSLContext.getInstance("TLS");
    kontext.init(schluessel.getKeyManagers(), vertrauen.getTrustManagers(), new SecureRandom());
    return kontext;
  }
}
