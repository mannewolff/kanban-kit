package org.mwolff.manban.fremdboard.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer.ISSUES;
import static org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer.PROJEKT;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.fremdboard.domain.FremdKarte;
import org.mwolff.manban.fremdboard.domain.FremdNotiz;

/**
 * Der Angriff aus Issue #2195: Der hinterlegte GitLab-Zugang des Spiegels darf vertrauliche Issues
 * und interne Notizen lesen, ein Mitglied des kanban-kit-Projekts in GitLab aber nicht. Liest die
 * Quelle sie trotzdem, werden sie als gewöhnliche Karten und Kommentare für jedes Mitglied
 * abrufbar. Die Quelle lässt sie darum aus, auf jedem Lesweg: Nummern, geänderte Karten, einzelne
 * Karte, Notizen.
 */
class GitLabQuelleVertraulichTest {

  private static final String GEHEIM = "Gehaltsliste Q4";
  private static final String NOTIZEN =
      PROJEKT + "/issues/7/notes?sort=asc&order_by=created_at&per_page=100&page=";

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
  void nummern_lassenVertraulicheIssuesAus() {
    // Given: Der Zugang sieht das vertrauliche Issue 9, das Mitglied in GitLab nicht.
    gitlab.antworte(ISSUES + 1, 200, "[" + issue(7, false) + "," + issue(9, true) + "]", "");

    // When / Then: Eine schon gespiegelte Karte 9 fehlt damit dort und wandert beim
    // Vollabgleich in den Papierkorb.
    assertThat(quelle.nummern(gitlab.verbindung())).containsExactly(7L);
  }

  @Test
  void geaenderteKarten_lassenVertraulicheIssuesAus() {
    // Given
    String pfad =
        PROJEKT
            + "/issues?state=all&updated_after=2026-11-01T00%3A00%3A00Z"
            + "&order_by=updated_at&sort=asc&per_page=100&page=";
    gitlab.antworte(pfad + 1, 200, "[" + issue(7, false) + "," + issue(9, true) + "]", "");

    // When / Then
    assertThat(quelle.geaenderteKarten(gitlab.verbindung(), Instant.parse("2026-11-01T00:00:00Z")))
        .extracting(FremdKarte::nummer)
        .containsExactly(7L);
  }

  @Test
  void karte_vertraulich_istLeerWieEineUnbekannte() {
    // Given
    gitlab.antworte(PROJEKT + "/issues/9", 200, issue(9, true), null);

    // When / Then
    assertThat(quelle.karte(gitlab.verbindung(), 9)).isEmpty();
  }

  @Test
  void karte_ausdruecklichNichtVertraulich_wirdGelesen() {
    // Given
    gitlab.antworte(PROJEKT + "/issues/7", 200, issue(7, false), null);

    // When / Then
    assertThat(quelle.karte(gitlab.verbindung(), 7))
        .hasValueSatisfying(karte -> assertThat(karte.nummer()).isEqualTo(7));
  }

  @Test
  void notizen_lassenInterneUndVertraulicheNotizenAus() {
    // Given: "internal" seit GitLab 15, "confidential" der frühere Name desselben Kennzeichens.
    gitlab.antworte(
        NOTIZEN + 1,
        200,
        "["
            + notiz(801, "\"internal\":true,\"confidential\":true")
            + ","
            + notiz(802, "\"internal\":true")
            + ","
            + notiz(803, "\"confidential\":true")
            + ","
            + notiz(804, "\"internal\":false,\"confidential\":false")
            + "]",
        "");

    // When / Then: Nur die öffentliche Notiz wird zum Kommentar.
    assertThat(quelle.notizen(gitlab.verbindung(), 7))
        .extracting(FremdNotiz::kennung)
        .containsExactly("804");
  }

  private static String issue(long nummer, boolean vertraulich) {
    return "{\"id\":"
        + (1000 + nummer)
        + ",\"iid\":"
        + nummer
        + ",\"title\":\""
        + (vertraulich ? GEHEIM : "Öffentlich")
        + "\",\"description\":\"\",\"state\":\"opened\",\"confidential\":"
        + vertraulich
        + ",\"created_at\":\"2026-11-01T09:00:00Z\",\"updated_at\":\"2026-11-02T10:00:00Z\""
        + ",\"labels\":[]}";
  }

  private static String notiz(long kennung, String kennzeichen) {
    return "{\"id\":"
        + kennung
        + ",\"body\":\""
        + GEHEIM
        + "\",\"author\":{\"username\":\"anna\"},\"system\":false,"
        + kennzeichen
        + ",\"created_at\":\"2026-11-02T10:00:00Z\",\"updated_at\":\"2026-11-02T10:00:00Z\"}";
  }
}
