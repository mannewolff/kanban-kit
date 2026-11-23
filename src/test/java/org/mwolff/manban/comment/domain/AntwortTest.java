package org.mwolff.manban.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.comment.domain.Antwort.Einzelantwort;

/** Lesen und Schreiben einer Antwort des Kunden (Issue #2210, Plan #2206 E8, E9; Vertrag V1). */
class AntwortTest {

  @Test
  void liestKopfUndBlock() {
    // Given
    String text =
        """
        ## Antwort

        ```json kit-antwort
        {"vorlage": 9, "ja": false, "weg": 2, "commit": "abc",
         "antworten": [{"nr": 1, "wahl": 2}, {"nr": 2, "text": "eigene"},
                       {"nr": 3, "zurueckstellen": true}, {"nr": 4, "text": "  "},
                       {"nr": 5}, {"wahl": 1}, {"nr": 6, "zurueckstellen": false, "wahl": 1}, 7]}
        ```
        """;

    // When
    Antwort antwort = Antwort.lies(text).orElseThrow();

    // Then
    assertThat(antwort.vorlage()).isEqualTo(9L);
    assertThat(antwort.ja()).isFalse();
    assertThat(antwort.weg()).isEqualTo(2);
    assertThat(antwort.commit()).isEqualTo("abc");
    assertThat(antwort.antworten())
        .containsExactly(
            Einzelantwort.gewaehlt(1, 2),
            Einzelantwort.eigene(2, "eigene"),
            Einzelantwort.zurueckgestellt(3),
            Einzelantwort.gewaehlt(6, 1));
  }

  @Test
  void liestEnglischenKopfUndKennungAlsText() {
    // Given
    String text = "## answer\n```json kit-antwort\n{\"vorlage\": \"12\", \"ja\": true}\n```";

    // When
    Antwort antwort = Antwort.lies(text).orElseThrow();

    // Then
    assertThat(antwort.vorlage()).isEqualTo(12L);
    assertThat(antwort.ja()).isTrue();
    assertThat(antwort.antworten()).isEmpty();
    assertThat(antwort.weg()).isNull();
    assertThat(antwort.commit()).isNull();
  }

  @Test
  void keineAntwortOhneKopfBlockOderKennung() {
    assertThat(Antwort.lies("## Rückfrage\n```json kit-antwort\n{\"vorlage\": 1}\n```")).isEmpty();
    assertThat(Antwort.lies("## Antwort\nNur Text")).isEmpty();
    assertThat(Antwort.lies("## Antwort\n```json kit-antwort\n[1]\n```")).isEmpty();
    assertThat(Antwort.lies("## Antwort\n```json kit-antwort\n{\"ja\": true}\n```")).isEmpty();
    assertThat(Antwort.lies("## Antwort\n```json kit-antwort\n{\"vorlage\": \"x\"}\n```"))
        .isEmpty();
    assertThat(Antwort.lies("## Antwort\n```json kit-antwort\n{\"vorlage\": true}\n```")).isEmpty();
    assertThat(Antwort.lies("## Antwort\n```json kit-antwort\n{kaputt\n```")).isEmpty();
  }

  @Test
  void leereZeilenVorDemKopfZaehlenNicht() {
    // Given
    String text = "\n  \n## Antwort\n```json kit-antwort\n{\"vorlage\": 3}\n```";

    // When / Then
    assertThat(Antwort.lies(text)).map(Antwort::vorlage).contains(3L);
  }

  @Test
  void leererOderNurLeerraumTextIstKeineAntwort() {
    assertThat(Antwort.lies("")).isEmpty();
    assertThat(Antwort.lies("\n  \n\t")).isEmpty();
  }

  @Test
  void kennungOhneGanzeZahlImLongBereichIstKeineAntwort() {
    assertThat(Antwort.lies("## Antwort\n```json kit-antwort\n{\"vorlage\": 9.5}\n```")).isEmpty();
    assertThat(
            Antwort.lies(
                "## Antwort\n```json kit-antwort\n{\"vorlage\": 99999999999999999999}\n```"))
        .isEmpty();
  }

  @Test
  void felderMitFalschemTypFallenWeg() {
    // Given
    String text =
        """
        ## Antwort
        ```json kit-antwort
        {"vorlage": 4, "ja": "true", "commit": 5, "weg": "2",
         "antworten": [{"nr": 1, "text": 5}, {"nr": 2, "zurueckstellen": "true"},
                       {"nr": 3, "zurueckstellen": 1, "wahl": 2}]}
        ```
        """;

    // When
    Antwort antwort = Antwort.lies(text).orElseThrow();

    // Then
    assertThat(antwort.ja()).isFalse();
    assertThat(antwort.commit()).isNull();
    assertThat(antwort.weg()).isNull();
    assertThat(antwort.antworten()).containsExactly(Einzelantwort.gewaehlt(3, 2));
  }

  @Test
  void leererCommitFehlt() {
    // Given
    String text = "## Antwort\n```json kit-antwort\n{\"vorlage\": 4, \"commit\": \"  \"}\n```";

    // When / Then
    assertThat(Antwort.lies(text).orElseThrow().commit()).isNull();
  }

  @Test
  void schreibtDeutschenKopfUndBlock() {
    // Given
    Antwort antwort =
        new Antwort(
            9L,
            false,
            List.of(
                Einzelantwort.gewaehlt(1, 2),
                Einzelantwort.eigene(2, "Mit \"Zitat\""),
                Einzelantwort.zurueckgestellt(3)),
            1,
            "abc");

    // When
    String text = antwort.alsKommentar(Kopfsprache.DEUTSCH);

    // Then
    assertThat(text)
        .isEqualTo(
            "## Antwort\n\n```json kit-antwort\n"
                + "{\"vorlage\":9,\"ja\":false,\"antworten\":[{\"nr\":1,\"wahl\":2},"
                + "{\"nr\":2,\"text\":\"Mit \\\"Zitat\\\"\"},{\"nr\":3,\"zurueckstellen\":true}],"
                + "\"weg\":1,\"commit\":\"abc\"}\n```\n");
    assertThat(Antwort.lies(text)).contains(antwort);
  }

  @Test
  void schreibtEnglischenKopfOhneLeereFelder() {
    // Given
    Antwort antwort = new Antwort(9L, true, List.of(), null, null);

    // When
    String text = antwort.alsKommentar(Kopfsprache.ENGLISCH);

    // Then
    assertThat(text)
        .isEqualTo(
            """
            ## Answer

            ```json kit-antwort
            {"vorlage":9,"ja":true,"antworten":[]}
            ```
            """);
    assertThat(Antwort.lies(text)).contains(antwort);
  }

  @Test
  void eigenerTextMitCodezaunBleibtLesbar() {
    // Given
    Antwort antwort =
        new Antwort(9L, false, List.of(Einzelantwort.eigene(1, "a\n```\nb")), null, null);

    // When
    String text = antwort.alsKommentar(Kopfsprache.DEUTSCH);

    // Then
    assertThat(Antwort.lies(text)).contains(antwort);
  }

  @Test
  void einzelantwortTraegtGenauEineAngabe() {
    assertThatThrownBy(() -> new Einzelantwort(1, null, null, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Einzelantwort(1, 1, "x", false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Einzelantwort(1, 1, null, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Einzelantwort(1, null, "x", true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Einzelantwort.eigene(1, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
