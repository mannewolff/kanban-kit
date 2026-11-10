package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;

/** Die reinen Leser der Kit-Meldungen (Issue #2113, Plan #2108 E3, E9). */
class KitMeldungLeserTest {

  private static final String TOKEN = "TOKEN";
  private static final Instant T1 = Instant.parse("2026-11-10T10:00:00Z");

  private static final String WORTLAUT = "## Übertragung auf den Hauptzweig\n\nCommit: abc123\n";

  private static Stream<Arguments> kitMeldungen() {
    return Stream.of(
        Arguments.of("## Plan-Review, Runde 1", Pruefrunde.class),
        Arguments.of("## Einarbeitung, Runde 1", Einarbeitung.class),
        Arguments.of("## Abschlussbericht Issue #7", Abschlussbericht.class),
        Arguments.of("## Code-Review (Schritt 6)", CodeReview.class),
        Arguments.of("## Übertragung auf den Hauptzweig", Uebertragung.class),
        Arguments.of("## Build-Dienst nachgetragen", BuildNachtrag.class),
        Arguments.of("## Veröffentlichung", Veroeffentlichung.class));
  }

  private static KitMeldung lies(String text) {
    return KitMeldungLeser.lies(TOKEN, text).orElseThrow();
  }

  // --- Erkennung -------------------------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @MethodSource("kitMeldungen")
  void lies_tokenMitFestemAnfang_istMeldungDerArt(String kopf, Class<?> typ) {
    assertThat(KitMeldungLeser.lies(TOKEN, kopf)).get().isInstanceOf(typ);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"SESSION", "", "token"})
  void lies_herkunftNichtToken_keineMeldungTrotzGleichemWortlaut(String herkunft) {
    assertThat(KitMeldungLeser.lies(herkunft, WORTLAUT)).isEmpty();
  }

  @Test
  void lies_ersteNichtleereZeileZaehlt_fuehrendeLeerzeilenGleichgueltig() {
    assertThat(KitMeldungLeser.lies(TOKEN, "\n   \n## Release\n")).isPresent();
  }

  @Test
  void lies_festerAnfangErstInDerZweitenZeile_keineMeldung() {
    assertThat(KitMeldungLeser.lies(TOKEN, "Vorwort\n## Release\n")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  \n \n", "Nur ein Kommentar"})
  void lies_ohneFestenAnfang_keineMeldung(String text) {
    assertThat(KitMeldungLeser.lies(TOKEN, text)).isEmpty();
  }

  // --- Prüfrunde -------------------------------------------------------------------------------

  @Test
  void pruefrunde_vollstaendig_liestRundeReviewerUndFunde() {
    // Given
    String text =
        """
        ## Issue-Review, Runde 2
        Reviewer 2 ausgefallen.

        ### Reviewer 1: form-beobachtbarkeit, fable

        **WICHTIG — Titel**

        ### Reviewer 3: fachlich, gpt-astra

        1. **BLOCKER — Eins**
        2. **BLOCKER — Zwei**
        """;

    // When
    Pruefrunde runde = (Pruefrunde) lies(text);

    // Then
    assertThat(runde.art()).isEqualTo(Meldungsart.ISSUE_REVIEW);
    assertThat(runde.runde()).isEqualTo(2);
    assertThat(runde.reviewer())
        .containsExactly(
            new Reviewer(1, "form-beobachtbarkeit", "fable", new Fundzahlen(0, 0, 1, 0)),
            new Reviewer(3, "fachlich", "gpt-astra", new Fundzahlen(0, 2, 0, 0)));
    assertThat(runde.funde()).isEqualTo(new Fundzahlen(0, 2, 1, 0));
  }

  @Test
  void pruefrunde_englisch_liestRundeUndArt() {
    // Given
    String text =
        """
        ## Requirement-Review, Round 3

        ### Reviewer 1: form, fable
        """;

    // When
    Pruefrunde runde = (Pruefrunde) lies(text);

    // Then
    assertThat(runde.art()).isEqualTo(Meldungsart.FACHPLAN_REVIEW);
    assertThat(runde.runde()).isEqualTo(3);
    assertThat(runde.funde().keine()).isTrue();
  }

  @Test
  void pruefrunde_ohneReviewer_leereListe() {
    // When
    Pruefrunde runde = (Pruefrunde) lies("## Plan-Review, Runde 1\n\n**WICHTIG — Titel**");

    // Then
    assertThat(runde.reviewer()).isEmpty();
    assertThat(runde.funde()).isEqualTo(new Fundzahlen(0, 0, 1, 0));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      nullValues = "-",
      value = {
        "### Reviewer 1: form|form|-",
        "### Reviewer 1:  , fable|-|fable",
        "### Reviewer 1: form,  |form|-",
        "### Reviewer 1:|-|-",
        "### Reviewer 1: rolle, modell, mit komma|rolle|modell, mit komma"
      })
  void pruefrunde_unvollstaendigerKopf_fehlendeAngabenLeer(
      String kopf, String rolle, String modell) {
    // When
    Pruefrunde runde = (Pruefrunde) lies("## Plan-Review, Runde 1\n" + kopf);

    // Then
    assertThat(runde.reviewer())
        .containsExactly(new Reviewer(1, rolle, modell, new Fundzahlen(0, 0, 0, 0)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"### Reviewer", "### Reviewer x: form, fable", "### Reviewer 1 form"})
  void pruefrunde_keinReviewerKopf_keinReviewer(String zeile) {
    assertThat(((Pruefrunde) lies("## Plan-Review, Runde 1\n" + zeile)).reviewer()).isEmpty();
  }

  @Test
  void einarbeitung_liestRundeInBeidenSprachen() {
    assertThat(lies("## Einarbeitung, Runde 4\n\nübernommen: alles"))
        .isEqualTo(new Einarbeitung(4));
    assertThat(lies("## Incorporation, Round 5")).isEqualTo(new Einarbeitung(5));
  }

  // --- Abschlussbericht ------------------------------------------------------------------------

  @Test
  void abschlussbericht_vollstaendig_liestSchwanz() {
    // Given
    String text =
        """
        ## Abschlussbericht Issue #7

        ### Änderungen
        - Datei

        Commit: abc123
        Prüflauf: gruen, 2026-11-10T10:00:00Z
        Umgesetzt von: claude-opus-5-5
        Kit-Stand: 6.1.3

        Bericht-Lauf: 2026-11-10
        """;

    // When / Then
    assertThat(lies(text))
        .isEqualTo(new Abschlussbericht("abc123", new Angabe("gruen", T1), "claude-opus-5-5"));
  }

  @Test
  void abschlussbericht_englisch_liestSchwanz() {
    // Given
    String text =
        """
        ## Completion report issue #7

        Commit: abc123
        Check run: not recorded
        Implemented by: not recorded
        Kit-Revision: 6.1.3
        Report-Run: 1
        """;

    // When / Then
    assertThat(lies(text)).isEqualTo(new Abschlussbericht("abc123", Angabe.LEER, null));
  }

  @Test
  void abschlussbericht_ohneSchwanz_alleAngabenLeer() {
    assertThat(lies("## Abschlussbericht Issue #7\n\nText."))
        .isEqualTo(new Abschlussbericht(null, Angabe.LEER, null));
  }

  @Test
  void abschlussbericht_commitZeileImFliesstext_zaehltNicht() {
    // Given
    String text =
        """
        ## Abschlussbericht Issue #7

        Commit: im-text-erwaehnt
        Danach folgt noch ein Absatz.
        """;

    // When / Then
    assertThat(lies(text)).isEqualTo(new Abschlussbericht(null, Angabe.LEER, null));
  }

  @Test
  void abschlussbericht_mitVorhabensstandVorn_liestSchwanz() {
    // Given
    String text =
        """
        ## Stand des Vorhabens

        Was der Mensch sieht.

        ## Abschlussbericht Issue #7

        Commit: abc123
        """;

    // When / Then
    assertThat(lies(text)).isEqualTo(new Abschlussbericht("abc123", Angabe.LEER, null));
  }

  // --- Code-Review -----------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(
      strings = {
        "## Code-Review (Schritt 6)",
        "## Code review (step 6)",
        "## Code-Review (Schritt 7)",
        "## Code review (step 7)"
      })
  void codeReview_liestFundeUnterJedemNamen(String kopf) {
    assertThat(lies(kopf + "\n\n**HINWEIS — Titel**"))
        .isEqualTo(new CodeReview(new Fundzahlen(0, 0, 0, 1)));
  }

  @Test
  void codeReview_ohneFunde_keineFunde() {
    assertThat(((CodeReview) lies("## Code-Review (Schritt 6)\n\nKeine Funde.")).funde().keine())
        .isTrue();
  }

  // --- Idee ------------------------------------------------------------------------------------

  @Test
  void ideeErgebnis_unterGeklaertemStand_woertlich() {
    // Given
    String text =
        """
        Die Idee.

        ## Geklärter Stand
        1. Punkt

        Bahn: 2, fachlich
        Ergebnis: Issue #2101
        """;

    // When / Then
    assertThat(KitMeldungLeser.ideeErgebnis(text)).contains("Issue #2101");
  }

  @Test
  void ideeErgebnis_englisch_woertlich() {
    assertThat(KitMeldungLeser.ideeErgebnis("## Clarified state\nResult: Commit abc1234"))
        .contains("Commit abc1234");
  }

  @Test
  void ideeErgebnis_mehrereZeilen_dieLetzteGilt() {
    // Given
    String text =
        """
        ## Geklärter Stand
        Ergebnis: Idee 4
        Ergebnis: Issue #7
        """;

    // When / Then
    assertThat(KitMeldungLeser.ideeErgebnis(text)).contains("Issue #7");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "Ergebnis: Issue #7",
        "## Geklärter Stand\nBahn: 1",
        "## Geklärter Stand\nErgebnis:   ",
        "## Geklärter Stand im Mai\nErgebnis: Issue #7",
        "## Geklärter Stand\n## Weiter\nErgebnis: Issue #7",
        "## Geklärter Stand\n```\nErgebnis: Issue #7\n```",
        "## Geklärter Stand\n~~~\nErgebnis: Issue #7\n~~~",
        "```\n## Geklärter Stand\nErgebnis: Issue #7\n```"
      })
  void ideeErgebnis_ohneGueltigeZeile_leer(String text) {
    assertThat(KitMeldungLeser.ideeErgebnis(text)).isEmpty();
  }

  @Test
  void ideeErgebnis_unterUeberschriftTieferEbene_bleibtImAbschnitt() {
    // Given
    String text =
        """
        ## Geklärter Stand
        ### Einzelheit
        Ergebnis: Issue #7
        """;

    // When / Then
    assertThat(KitMeldungLeser.ideeErgebnis(text)).contains("Issue #7");
  }
}
