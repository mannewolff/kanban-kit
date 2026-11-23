package org.mwolff.manban.fremdboard.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Die Lesestelle einer Notiz schützt sich selbst gegen interne Notizen (Issue #2249): {@link
 * GitLabQuelle} sortiert sie zwar vorher aus, doch {@link GitLabAntwort#notiz} darf sich darauf
 * nicht verlassen — sonst sähe jedes Mitglied eine interne Notiz als gewöhnlichen Kommentar, sobald
 * ein zweiter Aufrufer die Vorprüfung vergisst.
 */
class GitLabAntwortTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @ParameterizedTest
  @ValueSource(strings = {"\"internal\":true", "\"confidential\":true"})
  void notiz_intern_istLeerAuchOhneVorpruefung(String kennzeichen) throws Exception {
    // Given: keine Systemnotiz, aber intern gekennzeichnet.
    JsonNode eintrag = notiz("\"system\":false," + kennzeichen);

    // When / Then
    assertThat(GitLabAntwort.notiz(eintrag, 7)).isEmpty();
  }

  @Test
  void notiz_systemnotiz_istLeer() throws Exception {
    // Given
    JsonNode eintrag = notiz("\"system\":true");

    // When / Then
    assertThat(GitLabAntwort.notiz(eintrag, 7)).isEmpty();
  }

  @Test
  void notiz_oeffentlich_wirdGelesen() throws Exception {
    // Given
    JsonNode eintrag = notiz("\"system\":false,\"internal\":false");

    // When / Then
    assertThat(GitLabAntwort.notiz(eintrag, 7))
        .hasValueSatisfying(
            notiz -> {
              assertThat(notiz.kennung()).isEqualTo("801");
              assertThat(notiz.text()).isEqualTo("Hallo");
            });
  }

  private static JsonNode notiz(String kennzeichen) throws Exception {
    return JSON.readTree(
        "{\"id\":801,\"body\":\"Hallo\",\"author\":{\"username\":\"anna\"},"
            + kennzeichen
            + ",\"created_at\":\"2026-11-02T10:00:00Z\",\"updated_at\":\"2026-11-02T10:00:00Z\"}");
  }
}
