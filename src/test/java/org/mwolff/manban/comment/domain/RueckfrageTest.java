package org.mwolff.manban.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.comment.domain.Rueckfrage.Art;
import org.mwolff.manban.comment.domain.Rueckfrage.Eintrag;
import org.mwolff.manban.comment.domain.Rueckfrage.Option;

/** Lesen einer Rückfrage der KI (Issue #2210, Plan #2206 E8, E9, E11; Vertrag V1). */
class RueckfrageTest {

  private static final Instant AM = Instant.parse("2026-11-22T10:00:00Z");

  private static final String BLOCK =
      """
      ```json kit-vorlage
      [
        {"nr": 1, "art": "frage", "frage": "Welche Farbe?", "empfehlung": "Blau",
         "grund": "passt zum Logo", "einarbeitung": "Farbe Blau",
         "optionen": [{"text": "Rot"}, {"text": "Blau", "hinweis": "ruhig", "empfohlen": true}]},
        {"nr": 2, "frage": "Wie gross?", "empfehlung": "mittel", "grund": "lesbar",
         "einarbeitung": "mittel"}
      ]
      ```
      """;

  @Test
  void liestKopfZeilenUndEintraege() {
    // Given
    String text =
        "## Rückfrage\n\nVorschau: https://vorschau.example/1\nCommit: abc123\n\n" + BLOCK;

    // When
    Rueckfrage rueckfrage = Rueckfrage.lies(9L, AM, text).orElseThrow();

    // Then
    assertThat(rueckfrage.kommentarId()).isEqualTo(9L);
    assertThat(rueckfrage.angelegtAm()).isEqualTo(AM);
    assertThat(rueckfrage.sprache()).isEqualTo(Kopfsprache.DEUTSCH);
    assertThat(rueckfrage.vorschau()).isEqualTo("https://vorschau.example/1");
    assertThat(rueckfrage.commit()).isEqualTo("abc123");
    assertThat(rueckfrage.istFreigabe()).isTrue();
    assertThat(rueckfrage.eintraege())
        .containsExactly(
            new Eintrag(
                1,
                Art.FRAGE,
                "Welche Farbe?",
                "Blau",
                "passt zum Logo",
                "Farbe Blau",
                List.of(new Option("Rot", null, false), new Option("Blau", "ruhig", true))),
            new Eintrag(2, Art.FRAGE, "Wie gross?", "mittel", "lesbar", "mittel", List.of()));
    assertThat(rueckfrage.eintraege().get(0).empfohleneWahl()).isEqualTo(2);
    assertThat(rueckfrage.eintraege().get(1).empfohleneWahl()).isNull();
    assertThat(rueckfrage.eintrag(2)).isPresent();
    assertThat(rueckfrage.eintrag(3)).isEmpty();
  }

  @Test
  void liestEnglischenKopf() {
    // Given
    String text = "\n  ## question\nPreview: https://p\n" + BLOCK;

    // When
    Rueckfrage rueckfrage = Rueckfrage.lies(9L, AM, text).orElseThrow();

    // Then
    assertThat(rueckfrage.sprache()).isEqualTo(Kopfsprache.ENGLISCH);
    assertThat(rueckfrage.vorschau()).isEqualTo("https://p");
    assertThat(rueckfrage.commit()).isNull();
    assertThat(rueckfrage.istFreigabe()).isFalse();
  }

  @Test
  void liestWegUndFehlendeFelder() {
    // Given
    String text =
        """
        ## Rückfrage
        ```json kit-vorlage
        [{"nr": 4, "art": "weg", "optionen": [{"text": "A", "empfohlen": false}]},
         {"nr": 5, "art": "unbekannt"}, {"art": "frage"}, {"nr": "x"},
         {"nr": 4, "frage": "doppelt"},
         "kein Objekt"]
        ```
        """;

    // When
    Rueckfrage rueckfrage = Rueckfrage.lies(1L, AM, text).orElseThrow();

    // Then
    assertThat(rueckfrage.eintraege())
        .containsExactly(
            new Eintrag(4, Art.WEG, "", "", "", "", List.of(new Option("A", null, false))));
  }

  @Test
  void zeilenNachDemBlockZaehlenNicht() {
    // Given
    String text = "## Rückfrage\n" + BLOCK + "Commit: spaeter\n";

    // When
    Rueckfrage rueckfrage = Rueckfrage.lies(1L, AM, text).orElseThrow();

    // Then
    assertThat(rueckfrage.commit()).isNull();
  }

  @Test
  void leereZeilenNennenKeinenWert() {
    // Given
    String text = "## Rückfrage\nVorschau:   \nCommit:\n" + BLOCK;

    // When
    Rueckfrage rueckfrage = Rueckfrage.lies(1L, AM, text).orElseThrow();

    // Then
    assertThat(rueckfrage.vorschau()).isNull();
    assertThat(rueckfrage.commit()).isNull();
  }

  @Test
  void keineRueckfrageOhneKopfBlockOderEintraege() {
    assertThat(Rueckfrage.lies(1L, AM, "## Antwort\n" + BLOCK)).isEmpty();
    assertThat(Rueckfrage.lies(1L, AM, "## Rückfrage nachgereicht\n" + BLOCK)).isEmpty();
    assertThat(Rueckfrage.lies(1L, AM, "Text davor\n## Rückfrage\n" + BLOCK)).isEmpty();
    assertThat(Rueckfrage.lies(1L, AM, "## Rückfrage\nnur Text")).isEmpty();
    assertThat(Rueckfrage.lies(1L, AM, "   \n")).isEmpty();
    assertThat(Rueckfrage.lies(1L, AM, "## Rückfrage\n```json kit-vorlage\n{kaputt\n```"))
        .isEmpty();
    assertThat(Rueckfrage.lies(1L, AM, "## Rückfrage\n```json kit-vorlage\n{\"nr\": 1}\n```"))
        .isEmpty();
    assertThat(Rueckfrage.lies(1L, AM, "## Rückfrage\n```json kit-vorlage\n[]\n```")).isEmpty();
    assertThat(Rueckfrage.lies(1L, AM, "## Rückfrage\n```json kit-entscheidung\n[{\"nr\":1}]\n```"))
        .isEmpty();
  }

  @Test
  void blockOhneEndeReichtBisZumTextende() {
    // Given
    String text = "## Rückfrage\n````json   kit-vorlage\n[{\"nr\": 1, \"frage\": \"F\"}]";

    // When
    Rueckfrage rueckfrage = Rueckfrage.lies(1L, AM, text).orElseThrow();

    // Then
    assertThat(rueckfrage.eintraege()).extracting(Eintrag::frage).containsExactly("F");
  }
}
