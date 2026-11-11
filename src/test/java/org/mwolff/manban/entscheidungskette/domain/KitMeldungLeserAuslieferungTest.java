package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.common.KitMeldungen.Wert;

/** Die Leser der Meldungen zu Übertragung und Veröffentlichung (Issue #2113, Plan #2108). */
class KitMeldungLeserAuslieferungTest {

  private static final Instant T1 = Instant.parse("2026-11-10T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-11-10T11:00:00.123Z");

  private static final String UEBERTRAGUNG_DE =
      """
      ## Übertragung auf den Hauptzweig

      Versuch: 2026-11-10T09:00:00Z-push
      Commit: abc123
      Basis: def456
      Weg: lokal
      Prüflauf: gruen, 2026-11-10T10:00:00Z
      Build-Dienst: ausstehend
      Quelle: Prüflauf 2026-11-10T10:00:00Z
      Ausgang: übertragen
      Freigegeben von: Erika Muster, 2026-11-10T10:00:00Z
      Ausgeführt von: claude-opus-5-5, 2026-11-10T11:00:00.123Z
      Pakete: #1, #2
      """;

  private static final String UEBERTRAGUNG_EN =
      """
      ## Push to main

      Attempt: a-1
      Commit: abc123
      Base: def456
      Way: build-service
      Check run: red, 2026-11-10T10:00:00Z
      Build service: green, 2026-11-10T11:00:00.123Z
      Source: https://ci.example/1
      Outcome: not pushed
      Approved by: Erika Muster, 2026-11-10T10:00:00Z
      Executed by: claude-opus-5-5, 2026-11-10T11:00:00.123Z
      """;

  private static final String VEROEFFENTLICHUNG_DE =
      """
      ## Veröffentlichung

      Versuch: v-1
      Version: 3.18.0
      Commit: abc123
      Basis: def456
      Ausgang: Pull Request angelegt
      Quelle: https://example.org/pr/1
      Freigegeben von: Erika Muster, 2026-11-10T10:00:00Z
      Ausgeführt von: claude-opus-5-5, 2026-11-10T11:00:00.123Z
      """;

  private static final String VEROEFFENTLICHUNG_EN =
      """
      ## Release

      Attempt: v-2
      Version: 3.18.1
      Commit: abc123
      Base: def456
      Outcome: not released
      Source: not recorded
      Approved by: not recorded, 2026-11-10T10:00:00Z
      Executed by: not recorded
      """;

  private static KitMeldung lies(String text) {
    return KitMeldungLeser.lies("TOKEN", text).orElseThrow();
  }

  // --- Übertragung -----------------------------------------------------------------------------

  @Test
  void uebertragung_deutschVollstaendig_liestJedeZeile() {
    assertThat(lies(UEBERTRAGUNG_DE))
        .isEqualTo(
            new Uebertragung(
                "2026-11-10T09:00:00Z-push",
                "abc123",
                "def456",
                Wert.LOKAL,
                new Angabe("gruen", T1),
                new Angabe("ausstehend", null),
                "Prüflauf 2026-11-10T10:00:00Z",
                "übertragen",
                new Angabe("Erika Muster", T1),
                new Angabe("claude-opus-5-5", T2)));
  }

  @Test
  void uebertragung_englischVollstaendig_liestJedeZeile() {
    assertThat(lies(UEBERTRAGUNG_EN))
        .isEqualTo(
            new Uebertragung(
                "a-1",
                "abc123",
                "def456",
                Wert.BUILD_DIENST,
                new Angabe("red", T1),
                new Angabe("green", T2),
                "https://ci.example/1",
                "not pushed",
                new Angabe("Erika Muster", T1),
                new Angabe("claude-opus-5-5", T2)));
  }

  @Test
  void uebertragung_ohneZeilen_alleAngabenLeer() {
    assertThat(lies("## Push to main"))
        .isEqualTo(
            new Uebertragung(
                null,
                null,
                null,
                null,
                Angabe.LEER,
                Angabe.LEER,
                null,
                null,
                Angabe.LEER,
                Angabe.LEER));
  }

  @ParameterizedTest
  @CsvSource({
    "lokal,LOKAL",
    "local,LOKAL",
    "build-dienst,BUILD_DIENST",
    "build-service,BUILD_DIENST",
    "notfall,NOTFALL",
    "emergency,NOTFALL",
    "vorbereitet,VORBEREITET",
    "prepared,VORBEREITET"
  })
  void uebertragung_jederWeg_inBeidenSprachen(String wort, Wert weg) {
    assertThat(((Uebertragung) lies("## Push to main\nWay: " + wort)).weg()).isEqualTo(weg);
  }

  @ParameterizedTest
  @ValueSource(strings = {"gruen", "quer", "nicht erfasst"})
  void uebertragung_keinWeg_leer(String wort) {
    assertThat(((Uebertragung) lies("## Push to main\nWeg: " + wort)).weg()).isNull();
  }

  @Test
  void uebertragung_nichtErfasst_istLeer() {
    // Given
    String text =
        """
        ## Übertragung auf den Hauptzweig
        Versuch: nicht erfasst
        Prüflauf: nicht erfasst
        Freigegeben von: nicht erfasst, 2026-11-10T10:00:00Z
        """;

    // When
    Uebertragung gelesen = (Uebertragung) lies(text);

    // Then
    assertThat(gelesen.versuch()).isNull();
    assertThat(gelesen.prueflauf()).isEqualTo(Angabe.LEER);
    assertThat(gelesen.freigegebenVon()).isEqualTo(new Angabe(null, T1));
  }

  @Test
  void uebertragung_leererWert_istLeer() {
    assertThat(((Uebertragung) lies("## Push to main\nCommit:   ")).commit()).isNull();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Build-Dienst: gruen, 2026-11-10T10:00:00Z|gruen",
        "Build-Dienst: rot, 2026-11-10T10:00:00Z|rot",
        "Build-Dienst: keine, 2026-11-10T10:00:00Z|keine",
      })
  void uebertragung_buildDienst_mitZeitpunkt(String zeile, String wert) {
    assertThat(((Uebertragung) lies("## Push to main\n" + zeile)).buildDienst())
        .isEqualTo(new Angabe(wert, T1));
  }

  @Test
  void angabe_nameMitKomma_trenntAmLetztenKomma() {
    assertThat(
            ((Uebertragung)
                    lies("## Push to main\nApproved by: Muster, Erika, 2026-11-10T10:00:00Z"))
                .freigegebenVon())
        .isEqualTo(new Angabe("Muster, Erika", T1));
  }

  @Test
  void angabe_ohneGueltigenZeitpunkt_bleibtGanzerText() {
    assertThat(
            ((Uebertragung) lies("## Push to main\nApproved by: Muster, Erika")).freigegebenVon())
        .isEqualTo(new Angabe("Muster, Erika", null));
  }

  @Test
  void angabe_ist_vergleichtMitWert() {
    assertThat(new Angabe("green", T1).ist(Wert.GRUEN)).isTrue();
    assertThat(new Angabe("red", T1).ist(Wert.GRUEN)).isFalse();
    assertThat(Angabe.LEER.ist(Wert.GRUEN)).isFalse();
  }

  // --- Nachtrag --------------------------------------------------------------------------------

  @Test
  void nachtrag_deutschVollstaendig_liestJedeZeile() {
    // Given
    String text =
        """
        ## Build-Dienst nachgetragen

        Versuch: a-1
        Commit: abc123
        Build-Dienst: rot, 2026-11-10T10:00:00Z
        Quelle: https://ci.example/1
        """;

    // When / Then
    assertThat(lies(text))
        .isEqualTo(
            new BuildNachtrag("a-1", "abc123", new Angabe("rot", T1), "https://ci.example/1"));
  }

  @Test
  void nachtrag_englischOhneZeilen_alleAngabenLeer() {
    assertThat(lies("## Build service recorded"))
        .isEqualTo(new BuildNachtrag(null, null, Angabe.LEER, null));
  }

  @Test
  void nachtrag_englisch_liestZeilen() {
    // Given
    String text =
        """
        ## Build service recorded
        Attempt: a-2
        Commit: abc
        Build service: not recorded
        Source: not recorded
        """;

    // When / Then
    assertThat(lies(text)).isEqualTo(new BuildNachtrag("a-2", "abc", Angabe.LEER, null));
  }

  // --- Veröffentlichung ------------------------------------------------------------------------

  @Test
  void veroeffentlichung_deutschVollstaendig_ausgangWoertlich() {
    assertThat(lies(VEROEFFENTLICHUNG_DE))
        .isEqualTo(
            new Veroeffentlichung(
                "v-1",
                "3.18.0",
                "abc123",
                "def456",
                "Pull Request angelegt",
                "https://example.org/pr/1",
                new Angabe("Erika Muster", T1),
                new Angabe("claude-opus-5-5", T2)));
  }

  @Test
  void veroeffentlichung_englisch_ausgangWoertlichUndNichtErfasstLeer() {
    assertThat(lies(VEROEFFENTLICHUNG_EN))
        .isEqualTo(
            new Veroeffentlichung(
                "v-2",
                "3.18.1",
                "abc123",
                "def456",
                "not released",
                null,
                new Angabe(null, T1),
                Angabe.LEER));
  }

  @Test
  void veroeffentlichung_ohneZeilen_alleAngabenLeer() {
    assertThat(lies("## Veröffentlichung"))
        .isEqualTo(
            new Veroeffentlichung(null, null, null, null, null, null, Angabe.LEER, Angabe.LEER));
  }
}
