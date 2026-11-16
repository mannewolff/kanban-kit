package org.mwolff.manban.fremdboard.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer.ISSUES;
import static org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer.PROJEKT;
import static org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer.ZUGANG;
import static org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer.datei;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.FremdEreignis;
import org.mwolff.manban.fremdboard.domain.FremdKarte;
import org.mwolff.manban.fremdboard.domain.FremdNotiz;
import org.slf4j.LoggerFactory;

/**
 * Die GitLab-Quelle gegen aufgezeichnete Antworten (Issue #2160): alle Endpunkte, eine und mehrere
 * Seiten, die Gründe der Fehlerantworten und der Zugang, der nie in Meldung oder Protokoll steht.
 * Die übrigen Fehlerfälle prüft {@link GitLabQuelleFehlerTest}.
 */
class GitLabQuelleTest {

  private static final String LABEL_EREIGNISSE =
      PROJEKT + "/issues/7/resource_label_events?per_page=100&page=";
  private static final String STATUS_EREIGNISSE =
      PROJEKT + "/issues/7/resource_state_events?per_page=100&page=";
  private static final String NOTIZEN =
      PROJEKT + "/issues/7/notes?sort=asc&order_by=created_at&per_page=100&page=";
  private static final FremdKarte KARTE_7 =
      new FremdKarte(
          7,
          "Login per Passkey",
          "Plan: #3\nFachliche Quelle: Issue #2",
          List.of("Ready", "kit:nightrun"),
          false,
          Instant.parse("2026-11-01T09:00:00Z"),
          Instant.parse("2026-11-02T10:30:00Z"));

  /** Fängt die Warnungen des Abrufs: Auch dort darf der Zugang nicht stehen. */
  private final ListAppender<ILoggingEvent> logWatcher = new ListAppender<>();

  private GitLabTestServer gitlab;
  private GitLabQuelle quelle;

  @BeforeEach
  void setUp() throws Exception {
    gitlab = new GitLabTestServer();
    quelle = gitlab.quelle();
    logWatcher.start();
    ((Logger) LoggerFactory.getLogger(GitLabAbruf.class)).addAppender(logWatcher);
  }

  /** Der Logger ist global — ohne Abmelden sammelte der Appender über Testklassen hinweg weiter. */
  @AfterEach
  void tearDown() {
    ((Logger) LoggerFactory.getLogger(GitLabAbruf.class)).detachAppender(logWatcher);
    gitlab.close();
  }

  @Test
  void art_istGitLab() {
    assertThat(quelle.art()).isEqualTo(FremdBoardArt.GITLAB);
  }

  @Test
  void nummern_eineSeite_mitZugangImKopfUndKodiertemProjektpfad() {
    // Given
    gitlab.antworte(ISSUES + 1, 200, datei("issues-seite-1.json"), "");

    // When
    List<Long> nummern = quelle.nummern(gitlab.verbindung());

    // Then
    assertThat(nummern).containsExactly(7L, 8L);
    assertThat(gitlab.abrufe()).containsExactly(ISSUES + 1);
    assertThat(gitlab.zugaenge()).containsExactly(ZUGANG);
  }

  @Test
  void nummern_mehrereSeiten_folgtDerAngabeDerFolgeseite() {
    // Given
    gitlab.antworte(ISSUES + 1, 200, datei("issues-seite-1.json"), "2");
    gitlab.antworte(ISSUES + 2, 200, datei("issues-seite-2.json"), "");

    // When
    List<Long> nummern = quelle.nummern(gitlab.verbindung());

    // Then
    assertThat(nummern).containsExactly(7L, 8L, 11L);
    assertThat(gitlab.abrufe()).containsExactly(ISSUES + 1, ISSUES + 2);
  }

  @Test
  void nummern_ohneAngabeDerFolgeseite_endetNachDerErstenSeite() {
    // Given: ältere GitLab-Fassungen lassen den Kopf bei der letzten Seite ganz weg.
    gitlab.antworte(ISSUES + 1, 200, datei("issues-seite-1.json"), null);

    // When / Then
    assertThat(quelle.nummern(gitlab.verbindung())).containsExactly(7L, 8L);
  }

  @Test
  void basisadresseMitUnterpfadUndSchlussstrich_wirdVorDieApiGesetzt() {
    // Given
    gitlab.antworte("/gitlab" + ISSUES + 1, 200, "[]", "");

    // When
    List<Long> nummern = quelle.nummern(gitlab.verbindung(gitlab.basis() + "/gitlab/"));

    // Then
    assertThat(nummern).isEmpty();
    assertThat(gitlab.abrufe()).containsExactly("/gitlab" + ISSUES + 1);
  }

  @Test
  void geaenderteKarten_fragtMitUpdatedAfter_undLiestAlleFelder() {
    // Given
    String pfad =
        PROJEKT
            + "/issues?state=all&updated_after=2026-11-01T00%3A00%3A00Z"
            + "&order_by=updated_at&sort=asc&per_page=100&page=";
    gitlab.antworte(pfad + 1, 200, datei("issues-seite-1.json"), "2");
    gitlab.antworte(pfad + 2, 200, datei("issues-seite-2.json"), "");

    // When
    List<FremdKarte> karten =
        quelle.geaenderteKarten(gitlab.verbindung(), Instant.parse("2026-11-01T00:00:00Z"));

    // Then: ohne Beschreibung ist der Text leer, ein Zeitversatz wird umgerechnet.
    assertThat(karten)
        .containsExactly(
            KARTE_7,
            new FremdKarte(
                8,
                "[Plan] Passkeys",
                "",
                List.of(),
                true,
                Instant.parse("2026-11-01T07:00:00Z"),
                Instant.parse("2026-11-03T12:00:00Z")),
            new FremdKarte(
                11,
                "Abmelden überall",
                "",
                List.of("In Progress"),
                false,
                Instant.parse("2026-11-04T07:00:00Z"),
                Instant.parse("2026-11-04T07:05:00Z")));
  }

  @Test
  void karte_liefertDieKarteMitIhrerNummer() {
    // Given
    gitlab.antworte(PROJEKT + "/issues/7", 200, datei("issue-7.json"), null);

    // When / Then
    assertThat(quelle.karte(gitlab.verbindung(), 7)).contains(KARTE_7);
  }

  @Test
  void karte_dieEsNichtGibt_istLeer() {
    // Given: keine Antwort hinterlegt — der Testserver antwortet 404.

    // When / Then
    assertThat(quelle.karte(gitlab.verbindung(), 99)).isEmpty();
  }

  @Test
  void ereignisse_ausLabelUndStatus_zeitlichGeordnet() {
    // Given
    gitlab.antworte(LABEL_EREIGNISSE + 1, 200, datei("label-events-7.json"), "");
    gitlab.antworte(STATUS_EREIGNISSE + 1, 200, datei("state-events-7.json"), "");

    // When
    List<FremdEreignis> ereignisse = quelle.ereignisse(gitlab.verbindung(), 7);

    // Then: das Label-Ereignis ohne Label (in GitLab gelöscht) fällt weg.
    assertThat(ereignisse)
        .containsExactly(
            new FremdEreignis(
                "label-501",
                7,
                FremdEreignis.Art.LABEL_GESETZT,
                "Ready",
                Instant.parse("2026-11-02T10:00:00Z"),
                "anna"),
            new FremdEreignis(
                "state-601",
                7,
                FremdEreignis.Art.GESCHLOSSEN,
                null,
                Instant.parse("2026-11-02T10:10:00Z"),
                "kit-bot"),
            new FremdEreignis(
                "label-502",
                7,
                FremdEreignis.Art.LABEL_ENTFERNT,
                "Backlog",
                Instant.parse("2026-11-02T10:20:00Z"),
                null),
            new FremdEreignis(
                "state-602",
                7,
                FremdEreignis.Art.WIEDER_GEOEFFNET,
                null,
                Instant.parse("2026-11-02T10:30:00Z"),
                "anna"));
  }

  @Test
  void ereignisse_unbekannteArt_werdenUebergangen() {
    // Given: "merged" gibt es nur an Merge Requests, eine fremde Label-Aktion ist unbekannt.
    gitlab.antworte(
        LABEL_EREIGNISSE + 1,
        200,
        "[{\"id\":1,\"user\":null,\"created_at\":\"2026-11-02T10:00:00Z\","
            + "\"label\":{\"name\":\"x\"},\"action\":\"umbenannt\"}]",
        "");
    gitlab.antworte(
        STATUS_EREIGNISSE + 1,
        200,
        "[{\"id\":2,\"user\":null,\"created_at\":\"2026-11-02T10:00:00Z\",\"state\":\"merged\"}]",
        "");

    // When / Then
    assertThat(quelle.ereignisse(gitlab.verbindung(), 7)).isEmpty();
  }

  @Test
  void notizen_ohneSystemnotizen_mitAnlageUndAenderungszeit() {
    // Given
    gitlab.antworte(NOTIZEN + 1, 200, datei("notes-7.json"), "");

    // When
    List<FremdNotiz> notizen = quelle.notizen(gitlab.verbindung(), 7);

    // Then
    assertThat(notizen)
        .containsExactly(
            new FremdNotiz(
                "701",
                7,
                "kit-bot",
                "## Kette angehalten\nFrage: Passkey oder Passwort?",
                Instant.parse("2026-11-02T10:12:00Z"),
                Instant.parse("2026-11-02T11:00:00Z")));
  }

  @Test
  void adresse_zeigtAufDieKarteInGitLab_ohneAbruf() {
    // When
    String adresse = quelle.adresse(gitlab.verbindung("https://git.example.org/"), 7);

    // Then
    assertThat(adresse).isEqualTo("https://git.example.org/gruppe/projekt/-/issues/7");
    assertThat(gitlab.abrufe()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "401, GitLab lehnt den Zugang ab (401)",
    "404, GitLab kennt das Projekt oder die Karte nicht (404)",
    "500, GitLab meldet einen Serverfehler (500)",
    "503, GitLab meldet einen Serverfehler (503)",
    "403, GitLab antwortet unerwartet mit 403",
    "300, GitLab antwortet unerwartet mit 300",
    "302, GitLab antwortet unerwartet mit 302"
  })
  void fehlerhafteAntwort_wirdZurAusnahmeMitLesbaremGrund_ohneZugang(int status, String grund) {
    // Given: der Rumpf spiegelt den Zugang — er darf trotzdem nirgends erscheinen.
    gitlab.antworte(ISSUES + 1, status, "{\"message\":\"" + ZUGANG + "\"}", null);

    // When / Then
    assertThatThrownBy(() -> quelle.nummern(gitlab.verbindung()))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageContaining(grund)
        .hasMessageNotContaining(ZUGANG);
  }

  @Test
  void zugang_stehtWederInMeldungNochImLog() {
    // Given
    gitlab.antworte(ISSUES + 1, 401, "{\"message\":\"" + ZUGANG + "\"}", null);

    // When
    assertThatThrownBy(() -> quelle.nummern(gitlab.verbindung()))
        .isInstanceOf(GitLabAbrufException.class)
        .hasMessageNotContaining(ZUGANG);

    // Then: gewarnt wird mit Grund, ohne Zugang.
    assertThat(logWatcher.list)
        .singleElement()
        .satisfies(
            ereignis -> {
              assertThat(ereignis.getFormattedMessage()).contains("(401)").doesNotContain(ZUGANG);
              assertThat(String.valueOf(ereignis.getThrowableProxy())).doesNotContain(ZUGANG);
            });
  }
}
