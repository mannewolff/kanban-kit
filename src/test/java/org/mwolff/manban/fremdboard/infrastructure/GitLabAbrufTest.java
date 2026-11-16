package org.mwolff.manban.fremdboard.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer.ISSUES;
import static org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer.PROJEKT;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Ein angebundener GitLab-Server darf den Speicher der Anwendung nicht erschöpfen (Issue #2196):
 * Jede Antwort ist schon beim Empfang auf {@link GitLabAbruf.Grenzen#antwortBytes} begrenzt, eine
 * Liste über alle Seiten auf {@link GitLabAbruf.Grenzen#listeBytes} und {@link
 * GitLabAbruf.Grenzen#eintraege}. Ein Server, der {@code per_page} missachtet oder einen Rumpf ohne
 * Ende schickt, bricht den Abruf ab, statt ihn in den Heap zu laden.
 */
class GitLabAbrufTest {

  private static final long FLUT = 256L * 1024 * 1024;

  private GitLabTestServer gitlab;

  @BeforeEach
  void setUp() throws Exception {
    gitlab = new GitLabTestServer();
  }

  @AfterEach
  void tearDown() {
    gitlab.close();
  }

  @Test
  void rumpfOhneEnde_wirdBeimEmpfangAbgebrochen() throws Exception {
    // Given: der Angriff — 256 MiB ohne Längenangabe, weit über der Vorgabe je Antwort.
    gitlab.flute(FLUT);

    // When / Then
    assertThatThrownBy(() -> gitlab.quelle().nummern(gitlab.verbindung()))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("Abruf abgebrochen")
        .hasMessageContaining(String.valueOf(GitLabAbruf.Grenzen.VORGABE.antwortBytes()));
    assertThat(gitlab.flutBeendet(Duration.ofSeconds(30))).isTrue();
    assertThat(gitlab.geflutet()).isLessThan(FLUT / 4);
  }

  @Test
  void angekuendigteUeberlaenge_brichtAb() {
    // Given: eine Antwort mit Längenangabe über der Grenze.
    GitLabAbruf abruf = abruf(new GitLabAbruf.Grenzen(1024, 10_000, 1000));
    gitlab.antworte(ISSUES + 1, 200, liste(1025), "");

    // When / Then
    assertThatThrownBy(() -> abruf.seiten(gitlab.verbindung(), "/issues?state=all&per_page=100"))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessage("GitLab liefert eine Antwort über 1024 Bytes — Abruf abgebrochen");
  }

  @Test
  void antwortGenauAnDerGrenze_wirdGelesen() {
    // Given
    GitLabAbruf abruf = abruf(new GitLabAbruf.Grenzen(1024, 1024, 1000));
    gitlab.antworte(ISSUES + 1, 200, liste(1024), "");

    // When
    List<JsonNode> eintraege = abruf.seiten(gitlab.verbindung(), "/issues?state=all&per_page=100");

    // Then
    assertThat(eintraege).isNotEmpty();
  }

  @Test
  void gesamtbudgetGleichDerAntwortgrenze_meldetDieAntwort() {
    // Given: solange das Budget der Liste die Antwortgrenze nicht unterschreitet, gilt diese.
    GitLabAbruf abruf = abruf(new GitLabAbruf.Grenzen(1024, 1024, 1000));
    gitlab.antworte(ISSUES + 1, 200, liste(1025), "");

    // When / Then
    assertThatThrownBy(() -> abruf.seiten(gitlab.verbindung(), "/issues?state=all&per_page=100"))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessage("GitLab liefert eine Antwort über 1024 Bytes — Abruf abgebrochen");
  }

  @Test
  void einzelnerEintrag_istEbensoBegrenzt() {
    // Given
    GitLabAbruf abruf = abruf(new GitLabAbruf.Grenzen(1024, 10_000, 1000));
    gitlab.antworte(PROJEKT + "/issues/7", 200, "{\"title\":\"" + "x".repeat(2000) + "\"}", null);

    // When / Then
    assertThatThrownBy(() -> abruf.eintrag(gitlab.verbindung(), "/issues/7"))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining("über 1024 Bytes");
  }

  @Test
  void listeUeberDemGesamtbudget_brichtAb() {
    // Given: jede Seite für sich klein genug, zusammen zu groß.
    GitLabAbruf abruf = abruf(new GitLabAbruf.Grenzen(1024, 1500, 1000));
    gitlab.antworte(ISSUES + 1, 200, liste(800), "2");
    gitlab.antworte(ISSUES + 2, 200, liste(800), "");

    // When / Then
    assertThatThrownBy(() -> abruf.seiten(gitlab.verbindung(), "/issues?state=all&per_page=100"))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessage("GitLab liefert für eine Liste mehr als 1500 Bytes — Abruf abgebrochen");
  }

  @Test
  void listeGenauAmGesamtbudget_wirdGelesen() {
    // Given
    GitLabAbruf abruf = abruf(new GitLabAbruf.Grenzen(1024, 1600, 1000));
    gitlab.antworte(ISSUES + 1, 200, liste(800), "2");
    gitlab.antworte(ISSUES + 2, 200, liste(800), "");

    // When
    List<JsonNode> eintraege = abruf.seiten(gitlab.verbindung(), "/issues?state=all&per_page=100");

    // Then
    assertThat(gitlab.abrufe()).hasSize(2);
    assertThat(eintraege).isNotEmpty();
  }

  @Test
  void zuVieleEintraege_brechenAb() {
    // Given: ein Server, der per_page missachtet — vier kleine Einträge über zwei Seiten.
    GitLabAbruf abruf = abruf(new GitLabAbruf.Grenzen(1024, 10_000, 3));
    gitlab.antworte(ISSUES + 1, 200, "[{},{}]", "2");
    gitlab.antworte(ISSUES + 2, 200, "[{},{}]", "");

    // When / Then
    assertThatThrownBy(() -> abruf.seiten(gitlab.verbindung(), "/issues?state=all&per_page=100"))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessage("GitLab liefert mehr als 3 Einträge — Abruf abgebrochen");
  }

  @Test
  void eintraegeGenauAnDerGrenze_werdenGelesen() {
    // Given
    GitLabAbruf abruf = abruf(new GitLabAbruf.Grenzen(1024, 10_000, 4));
    gitlab.antworte(ISSUES + 1, 200, "[{},{}]", "2");
    gitlab.antworte(ISSUES + 2, 200, "[{},{}]", "");

    // When / Then
    assertThat(abruf.seiten(gitlab.verbindung(), "/issues?state=all&per_page=100")).hasSize(4);
  }

  @Test
  void abbruchBeimEmpfang_wirdZurAusnahme() throws Exception {
    // Given: ein Rumpf, dessen Verbindung mitten im Empfang abreißt — nur ein nachgestellter Client
    // liefert das verlässlich.
    try (HttpClient client = mock(HttpClient.class);
        InputStream rumpf = mock(InputStream.class)) {
      HttpResponse<?> antwort = mock(HttpResponse.class);
      when(antwort.statusCode()).thenReturn(200);
      when(antwort.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, wert) -> true));
      when(rumpf.readNBytes(anyInt())).thenThrow(new IOException("Verbindung abgerissen"));
      doReturn(rumpf).when(antwort).body();
      doReturn(antwort).when(client).send(any(HttpRequest.class), any());
      GitLabAbruf abruf =
          new GitLabAbruf(
              new HostErlaubnis(List.of("localhost"), client, Duration.ofSeconds(1)),
              new ZugangsTresor(GitLabTestServer.SCHLUESSEL, new SecureRandom()),
              GitLabAbruf.Grenzen.VORGABE);

      // When / Then
      assertThatThrownBy(() -> abruf.eintrag(gitlab.verbindung(), "/issues/7"))
          .isInstanceOf(GitLabAbrufException.class)
          .hasMessageContaining("brach beim Empfang ab");
    }
  }

  @Test
  void vorgabe_haeltDieSeitenzahlDerListeAus() {
    // Then: 1000 volle Seiten zu je 100 Einträgen bleiben möglich.
    assertThat(GitLabAbruf.Grenzen.VORGABE.eintraege())
        .isEqualTo(GitLabAbruf.HOECHSTE_SEITEN * 100);
    assertThat(GitLabAbruf.Grenzen.VORGABE.listeBytes())
        .isGreaterThan(GitLabAbruf.Grenzen.VORGABE.antwortBytes());
  }

  private GitLabAbruf abruf(GitLabAbruf.Grenzen grenzen) {
    return new GitLabAbruf(
        gitlab.erlaubnis(),
        new ZugangsTresor(GitLabTestServer.SCHLUESSEL, new SecureRandom()),
        grenzen);
  }

  /** Eine JSON-Liste mit einem Eintrag von genau {@code bytes} Bytes. */
  private static String liste(int bytes) {
    String rahmen = "[{\"t\":\"\"}]";
    return "[{\"t\":\"" + "x".repeat(bytes - rahmen.length()) + "\"}]";
  }
}
