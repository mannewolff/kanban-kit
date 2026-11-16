package org.mwolff.manban.fremdboard.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer.ISSUES;
import static org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer.PROJEKT;
import static org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer.ZUGANG;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Fehlerfälle der GitLab-Quelle jenseits der Statuscodes (Issue #2160): Zeitüberschreitung, nicht
 * erreichbar, unterbrochen, unlesbare Antworten, Seitenangaben, die nicht vorwärts zeigen oder kein
 * Ende finden, ein Host außerhalb der Erlaubnisliste und ein Status unter 200, den nur ein
 * nachgestellter Client liefert (Issue #2193).
 */
class GitLabQuelleFehlerTest {

  private GitLabTestServer gitlab;
  private GitLabQuelle quelle;

  @BeforeEach
  void setUp() throws Exception {
    gitlab = new GitLabTestServer();
    quelle = gitlab.quelle();
  }

  @AfterEach
  void tearDown() {
    gitlab.close();
  }

  @Test
  void fehler_beiEinerKarte_istKeinLeeresErgebnis() {
    // Given: nur 404 heißt „gibt es nicht“, ein Serverfehler bleibt ein Fehler.
    gitlab.antworte(PROJEKT + "/issues/7", 502, "", null);

    // When / Then
    assertThatThrownBy(() -> quelle.karte(gitlab.verbindung(), 7))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("(502)");
  }

  @Test
  void fehler_beiEreignissenUndNotizen_werdenGemeldet() {
    // Given: keine Antworten hinterlegt — der Testserver antwortet 404.

    // When / Then
    assertThatThrownBy(() -> quelle.ereignisse(gitlab.verbindung(), 7))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("(404)");
    assertThatThrownBy(() -> quelle.notizen(gitlab.verbindung(), 7))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("(404)");
  }

  @Test
  void zeitueberschreitung_wirdZurAusnahme() {
    // Given
    gitlab.verzoegere(Duration.ofSeconds(2));
    gitlab.antworte(ISSUES + 1, 200, "[]", "");

    // When / Then
    assertThatThrownBy(() -> quelle.nummern(gitlab.verbindung()))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("Zeitüberschreitung")
        .hasMessageNotContaining(ZUGANG);
  }

  @Test
  void nichtErreichbar_wirdZurAusnahme() {
    // Given
    String basis = gitlab.basis();
    gitlab.stoppe();

    // When / Then
    assertThatThrownBy(() -> quelle.nummern(gitlab.verbindung(basis)))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("nicht erreichbar")
        .hasMessageNotContaining(ZUGANG);
  }

  @Test
  void unterbrochenerAbruf_wirdZurAusnahme_undBehaeltDieUnterbrechung() {
    // Given
    gitlab.antworte(ISSUES + 1, 200, "[]", "");
    Thread.currentThread().interrupt();

    // When / Then
    assertThatThrownBy(() -> quelle.nummern(gitlab.verbindung()))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("unterbrochen");
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test
  void unlesbareAntwort_wirdZurAusnahme() {
    // Given
    gitlab.antworte(ISSUES + 1, 200, "<html>Wartung</html>", "");

    // When / Then
    assertThatThrownBy(() -> quelle.nummern(gitlab.verbindung()))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("nicht lesbar");
  }

  @Test
  void antwortMitFalschemZeitformat_wirdZurAusnahme() {
    // Given
    gitlab.antworte(
        PROJEKT + "/issues/7",
        200,
        "{\"iid\":7,\"title\":\"t\",\"state\":\"opened\",\"labels\":[],"
            + "\"created_at\":\"gestern\",\"updated_at\":\"heute\"}",
        null);

    // When / Then
    assertThatThrownBy(() -> quelle.karte(gitlab.verbindung(), 7))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("nicht lesbar");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{\"iid\":7}",
        "[{\"iid\":\"sieben\"}]",
        "[{\"iid\":7,\"title\":null,\"created_at\":\"2026-11-01T09:00:00Z\"}]"
      })
  void listeOhneErwarteteForm_wirdZurAusnahme(String rumpf) {
    // Given: keine Liste, eine Nummer als Text, ein Titel ohne Text.
    gitlab.antworte(
        PROJEKT
            + "/issues?state=all&updated_after=2026-11-01T00%3A00%3A00Z"
            + "&order_by=updated_at&sort=asc&per_page=100&page=1",
        200,
        rumpf,
        "");

    // When / Then
    assertThatThrownBy(
            () ->
                quelle.geaenderteKarten(gitlab.verbindung(), Instant.parse("2026-11-01T00:00:00Z")))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("nicht lesbar");
  }

  @ParameterizedTest
  @CsvSource({"1", "0", "zwei"})
  void ungueltigeFolgeseite_wirdZurAusnahme(String naechste) {
    // Given: eine Folgeseite, die nicht vorwärts zeigt, liefe endlos.
    gitlab.antworte(ISSUES + 1, 200, "[]", naechste);

    // When / Then
    assertThatThrownBy(() -> quelle.nummern(gitlab.verbindung()))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("Seitenangabe");
  }

  @Test
  void zuVieleSeiten_brechenAb() {
    // Given
    gitlab.endloseSeiten();

    // When / Then
    assertThatThrownBy(() -> quelle.nummern(gitlab.verbindung()))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("mehr als " + GitLabAbruf.HOECHSTE_SEITEN + " Seiten");
    assertThat(gitlab.abrufe()).hasSize(GitLabAbruf.HOECHSTE_SEITEN);
  }

  @Test
  void statusUnter200_istKeinErfolg() throws Exception {
    // Given: eine Antwort unterhalb der Erfolgsspanne — ein echter Server liefert sie nie als
    // endgültige Antwort, darum steht hier ein nachgestellter Client.
    try (HttpClient client = mock(HttpClient.class)) {
      HttpResponse<?> antwort = mock(HttpResponse.class);
      when(antwort.statusCode()).thenReturn(199);
      doReturn(antwort).when(client).send(any(HttpRequest.class), any());
      GitLabQuelle nachgestellt =
          new GitLabQuelle(
              new HostErlaubnis(List.of("localhost"), client, Duration.ofSeconds(1)),
              new ZugangsTresor(GitLabTestServer.SCHLUESSEL, new SecureRandom()));

      // When / Then
      assertThatThrownBy(() -> nachgestellt.karte(gitlab.verbindung(), 7))
          .isInstanceOf(GitLabAbrufException.class)
          .hasMessageContaining("unerwartet mit 199");
    }
  }

  @Test
  void fremderHost_wirdNichtAbgerufen() {
    // When / Then
    assertThatThrownBy(() -> quelle.nummern(gitlab.verbindung("https://metadata.intern")))
        .isInstanceOf(HostNichtErlaubtException.class);
    assertThat(gitlab.abrufe()).isEmpty();
  }
}
