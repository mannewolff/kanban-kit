package org.mwolff.manban.fremdboard.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HostErlaubnisTest {

  private final HostErlaubnis erlaubnis =
      new HostErlaubnis(List.of("gitlab.com", " Git.Example.ORG "));

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void erlaubterHostGehtDurch() {
    URI ziel = URI.create("https://gitlab.com/api/v4/projects/1/issues");

    assertThat(erlaubnis.pruefe(ziel)).isEqualTo(ziel);
  }

  @Test
  void hostVergleichKenntKeineGrossschreibungUndKeinenSchlusspunkt() {
    assertThat(erlaubnis.pruefe(URI.create("https://GIT.example.org./api/v4"))).isNotNull();
    assertThat(erlaubnis.pruefe(URI.create("https://git.example.org:8443/api/v4"))).isNotNull();
  }

  @Test
  void fremderHostWirdAbgelehnt() {
    assertThatThrownBy(() -> erlaubnis.pruefe(URI.create("https://metadata.intern/latest")))
        .isInstanceOf(HostNichtErlaubtException.class)
        .hasMessageContaining("metadata.intern")
        .hasMessageContaining("MANBAN_FREMDBOARD_HOSTS");
  }

  @Test
  void hostMitErlaubtemNamenAlsTeilWirdAbgelehnt() {
    assertThatThrownBy(() -> erlaubnis.pruefe(URI.create("https://gitlab.com.boese.example/x")))
        .isInstanceOf(HostNichtErlaubtException.class);
    assertThatThrownBy(() -> erlaubnis.pruefe(URI.create("https://evilgitlab.com/x")))
        .isInstanceOf(HostNichtErlaubtException.class);
  }

  @Test
  void httpWirdAbgelehnt() {
    assertThatThrownBy(() -> erlaubnis.pruefe(URI.create("http://gitlab.com/api/v4")))
        .isInstanceOf(HostNichtErlaubtException.class)
        .hasMessageContaining("https");
  }

  @Test
  void adresseOhneSchemaOderHostWirdAbgelehnt() {
    assertThatThrownBy(() -> erlaubnis.pruefe(URI.create("gitlab.com/api")))
        .isInstanceOf(HostNichtErlaubtException.class);
    assertThatThrownBy(() -> erlaubnis.pruefe(URI.create("https:///api")))
        .isInstanceOf(HostNichtErlaubtException.class);
  }

  @Test
  void adresseMitZugangsdatenImHostteilWirdAbgelehnt() {
    assertThatThrownBy(() -> erlaubnis.pruefe(URI.create("https://user:pw@gitlab.com/api")))
        .isInstanceOf(HostNichtErlaubtException.class)
        .hasMessageNotContaining("pw");
  }

  @Test
  void textAdresseWirdGeprueft() {
    assertThat(erlaubnis.pruefe("https://gitlab.com/gruppe/projekt"))
        .isEqualTo(URI.create("https://gitlab.com/gruppe/projekt"));
    assertThatThrownBy(() -> erlaubnis.pruefe("https://gitlab.com/ hier nicht"))
        .isInstanceOf(HostNichtErlaubtException.class);
    assertThatThrownBy(() -> erlaubnis.pruefe("https://intern.local/x"))
        .isInstanceOf(HostNichtErlaubtException.class);
  }

  @Test
  void ohneListeGiltGitlabCom() {
    HostErlaubnis vorgabe = new HostErlaubnis(List.of());
    HostErlaubnis leer = new HostErlaubnis(List.of(" ", ""));

    assertThat(vorgabe.hosts()).containsExactly("gitlab.com");
    assertThat(leer.hosts()).containsExactly("gitlab.com");
    assertThat(erlaubnis.hosts()).containsExactly("gitlab.com", "git.example.org");
  }

  @Test
  void anfrageTraegtDasZeitlimitUndPrueftDenHost() {
    HttpRequest anfrage = erlaubnis.anfrage(URI.create("https://gitlab.com/api/v4")).build();

    assertThat(anfrage.timeout()).contains(Duration.ofSeconds(10));
    assertThatThrownBy(() -> erlaubnis.anfrage(URI.create("https://intern.local/")))
        .isInstanceOf(HostNichtErlaubtException.class);
  }

  @Test
  void clientFolgtKeinerWeiterleitungUndHatDasZeitlimit() {
    assertThat(erlaubnis.httpClient().followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
    assertThat(erlaubnis.httpClient().connectTimeout()).contains(Duration.ofSeconds(10));
    assertThat(erlaubnis.httpClient()).isSameAs(erlaubnis.httpClient());
  }

  @Test
  void weiterleitungWirdNichtGefolgt() throws IOException, InterruptedException {
    AtomicInteger aufrufeZiel = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/start",
        austausch -> {
          austausch.getResponseHeaders().add("Location", "/ziel");
          austausch.sendResponseHeaders(302, -1);
          austausch.close();
        });
    server.createContext(
        "/ziel",
        austausch -> {
          aufrufeZiel.incrementAndGet();
          austausch.sendResponseHeaders(200, -1);
          austausch.close();
        });
    server.start();
    URI start = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/start");

    HttpResponse<Void> antwort =
        erlaubnis
            .httpClient()
            .send(HttpRequest.newBuilder(start).build(), HttpResponse.BodyHandlers.discarding());

    assertThat(antwort.statusCode()).isEqualTo(302);
    assertThat(aufrufeZiel).hasValue(0);
  }
}
