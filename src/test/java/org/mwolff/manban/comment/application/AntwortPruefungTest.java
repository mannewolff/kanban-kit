package org.mwolff.manban.comment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.comment.application.RueckfrageService.AntwortAuftrag;
import org.mwolff.manban.comment.domain.Rueckfrage;

/** Freigeben bindet an den angezeigten Stand (Issue #2253). */
class AntwortPruefungTest {

  private static final long RUECKFRAGE_ID = 40L;
  private static final Instant FRAGE_AM = Instant.parse("2026-11-22T10:00:00Z");

  private static final String FRAGE =
      """
      ```json kit-vorlage
      [{"nr": 1, "frage": "Veröffentlichen?", "empfehlung": "ja", "grund": "fertig",
        "einarbeitung": "veröffentlichen"}]
      ```
      """;

  private static Rueckfrage freigabeFuer(String commit) {
    return Rueckfrage.lies(
            RUECKFRAGE_ID,
            FRAGE_AM,
            "## Rückfrage\nVorschau: https://p\nCommit: " + commit + "\n" + FRAGE)
        .orElseThrow();
  }

  @Test
  @DisplayName("Angriff: ausgetauschter Kommentar lässt den Klick keinen anderen Stand freigeben")
  void ausgetauschterKommentarErzeugtKeineFreigabeFuerDenNeuenStand() {
    // Angenommen der Mensch sieht die Freigabe-Rückfrage mit Stand A
    String angezeigt = freigabeFuer("aaa").commit();
    // Und der Inhaber eines gewöhnlichen Anbieter-Tokens bearbeitet seine Rückfrage auf Stand B,
    // ohne dass eine neuere Vorschau gemeldet ist
    Rueckfrage ausgetauscht = freigabeFuer("bbb");

    // Wenn der Mensch für den angezeigten Stand A freigibt
    AntwortAuftrag klick = new AntwortAuftrag(true, List.of(), null, true, angezeigt);

    // Dann wird der Klick mit 409 abgewiesen, und keine Freigabe für B entsteht
    assertThatThrownBy(() -> AntwortPruefung.antwort(ausgetauscht, klick, Optional.empty()))
        .isInstanceOf(FreigabeStandAbweichendException.class)
        .hasMessage("Der angezeigte Stand ist nicht mehr der zur Freigabe gestellte");
  }

  @Test
  void freigebenOhneAngezeigtenStandWirdAbgewiesen() {
    // Given
    AntwortAuftrag ohneStand = new AntwortAuftrag(true, List.of(), null, true, null);

    // When / Then
    assertThatThrownBy(
            () -> AntwortPruefung.antwort(freigabeFuer("aaa"), ohneStand, Optional.empty()))
        .isInstanceOf(FreigabeStandAbweichendException.class);
  }

  @Test
  void freigebenDesAngezeigtenStandsTraegtIhnInDieAntwort() {
    // Given
    AntwortAuftrag klick = new AntwortAuftrag(true, List.of(), null, true, "aaa");

    // When / Then
    assertThat(AntwortPruefung.antwort(freigabeFuer("aaa"), klick, Optional.empty()).commit())
        .isEqualTo("aaa");
  }

  @Test
  void standAbweichendIstUebersetzbar() {
    assertThat(new FreigabeStandAbweichendException().schluessel())
        .isEqualTo("comment.rueckfrage.stand-abweichend");
  }
}
