package org.mwolff.manban.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.common.KitMeldungen.Wert;
import org.mwolff.manban.common.KitMeldungen.Zeile;

/** Register der Kit-Meldungen: Zeilenanfänge, Werte und Köpfe (Issue #2113, Plan #2108 E2). */
class KitMeldungenTest {

  /** Die Zeilenanfänge wörtlich aus {@code kit/board/namen.mjs}, Kit 6.1.3. */
  private static final List<Arguments> ZEILEN =
      List.of(
          Arguments.of(Zeile.PLAN_REVIEW_RUNDE, "## Plan-Review, Runde"),
          Arguments.of(Zeile.PLAN_REVIEW_RUNDE, "## Plan-Review, Round"),
          Arguments.of(Zeile.FACHPLAN_REVIEW_RUNDE, "## Fachplan-Review, Runde"),
          Arguments.of(Zeile.FACHPLAN_REVIEW_RUNDE, "## Requirement-Review, Round"),
          Arguments.of(Zeile.ISSUE_REVIEW_RUNDE, "## Issue-Review, Runde"),
          Arguments.of(Zeile.ISSUE_REVIEW_RUNDE, "## Issue-Review, Round"),
          Arguments.of(Zeile.EINARBEITUNG_RUNDE, "## Einarbeitung, Runde"),
          Arguments.of(Zeile.EINARBEITUNG_RUNDE, "## Incorporation, Round"),
          Arguments.of(Zeile.REVIEWER, "### Reviewer"),
          Arguments.of(Zeile.ABSCHLUSSBERICHT, "## Abschlussbericht Issue #"),
          Arguments.of(Zeile.ABSCHLUSSBERICHT, "## Completion report issue #"),
          Arguments.of(Zeile.STAND_DES_VORHABENS, "## Stand des Vorhabens"),
          Arguments.of(Zeile.STAND_DES_VORHABENS, "## State of the project"),
          Arguments.of(Zeile.CODE_REVIEW, "## Code-Review (Schritt 6)"),
          Arguments.of(Zeile.CODE_REVIEW, "## Code review (step 6)"),
          Arguments.of(Zeile.CODE_REVIEW, "## Code-Review (Schritt 7)"),
          Arguments.of(Zeile.CODE_REVIEW, "## Code review (step 7)"),
          Arguments.of(Zeile.UEBERTRAGUNG, "## Übertragung auf den Hauptzweig"),
          Arguments.of(Zeile.UEBERTRAGUNG, "## Push to main"),
          Arguments.of(Zeile.BUILD_NACHGETRAGEN, "## Build-Dienst nachgetragen"),
          Arguments.of(Zeile.BUILD_NACHGETRAGEN, "## Build service recorded"),
          Arguments.of(Zeile.VEROEFFENTLICHUNG, "## Veröffentlichung"),
          Arguments.of(Zeile.VEROEFFENTLICHUNG, "## Release"),
          Arguments.of(Zeile.COMMIT, "Commit:"),
          Arguments.of(Zeile.PRUEFLAUF, "Prüflauf:"),
          Arguments.of(Zeile.PRUEFLAUF, "Check run:"),
          Arguments.of(Zeile.UMGESETZT_VON, "Umgesetzt von:"),
          Arguments.of(Zeile.UMGESETZT_VON, "Implemented by:"),
          Arguments.of(Zeile.KIT_STAND, "Kit-Stand:"),
          Arguments.of(Zeile.KIT_STAND, "Kit-Revision:"),
          Arguments.of(Zeile.BERICHT_LAUF, "Bericht-Lauf:"),
          Arguments.of(Zeile.BERICHT_LAUF, "Report-Run:"),
          Arguments.of(Zeile.VERSUCH, "Versuch:"),
          Arguments.of(Zeile.VERSUCH, "Attempt:"),
          Arguments.of(Zeile.BASIS, "Basis:"),
          Arguments.of(Zeile.BASIS, "Base:"),
          Arguments.of(Zeile.WEG, "Weg:"),
          Arguments.of(Zeile.WEG, "Way:"),
          Arguments.of(Zeile.BUILD_DIENST, "Build-Dienst:"),
          Arguments.of(Zeile.BUILD_DIENST, "Build service:"),
          Arguments.of(Zeile.QUELLE, "Quelle:"),
          Arguments.of(Zeile.QUELLE, "Source:"),
          Arguments.of(Zeile.AUSGANG, "Ausgang:"),
          Arguments.of(Zeile.AUSGANG, "Outcome:"),
          Arguments.of(Zeile.FREIGEGEBEN_VON, "Freigegeben von:"),
          Arguments.of(Zeile.FREIGEGEBEN_VON, "Approved by:"),
          Arguments.of(Zeile.AUSGEFUEHRT_VON, "Ausgeführt von:"),
          Arguments.of(Zeile.AUSGEFUEHRT_VON, "Executed by:"),
          Arguments.of(Zeile.VERSION, "Version:"),
          Arguments.of(Zeile.GEKLAERTER_STAND, "## Geklärter Stand"),
          Arguments.of(Zeile.GEKLAERTER_STAND, "## Clarified state"),
          Arguments.of(Zeile.ERGEBNIS, "Ergebnis:"),
          Arguments.of(Zeile.ERGEBNIS, "Result:"));

  /** Die Werte wörtlich aus {@code MELDUNG_WERTE}, Kit 6.1.3. */
  private static final List<Arguments> WERTE =
      List.of(
          Arguments.of(Wert.GRUEN, "gruen"),
          Arguments.of(Wert.GRUEN, "green"),
          Arguments.of(Wert.ROT, "rot"),
          Arguments.of(Wert.ROT, "red"),
          Arguments.of(Wert.KEINE, "keine"),
          Arguments.of(Wert.KEINE, "none"),
          Arguments.of(Wert.AUSSTEHEND, "ausstehend"),
          Arguments.of(Wert.AUSSTEHEND, "pending"),
          Arguments.of(Wert.NICHT_ERFASST, "nicht erfasst"),
          Arguments.of(Wert.NICHT_ERFASST, "not recorded"),
          Arguments.of(Wert.LOKAL, "lokal"),
          Arguments.of(Wert.LOKAL, "local"),
          Arguments.of(Wert.BUILD_DIENST, "build-dienst"),
          Arguments.of(Wert.BUILD_DIENST, "build-service"),
          Arguments.of(Wert.NOTFALL, "notfall"),
          Arguments.of(Wert.NOTFALL, "emergency"),
          Arguments.of(Wert.VORBEREITET, "vorbereitet"),
          Arguments.of(Wert.VORBEREITET, "prepared"),
          Arguments.of(Wert.UEBERTRAGEN, "übertragen"),
          Arguments.of(Wert.UEBERTRAGEN, "pushed"),
          Arguments.of(Wert.NICHT_UEBERTRAGEN, "nicht übertragen"),
          Arguments.of(Wert.NICHT_UEBERTRAGEN, "not pushed"),
          Arguments.of(Wert.PULL_REQUEST_ANGELEGT, "Pull Request angelegt"),
          Arguments.of(Wert.PULL_REQUEST_ANGELEGT, "pull request opened"),
          Arguments.of(Wert.NICHT_VEROEFFENTLICHT, "nicht veröffentlicht"),
          Arguments.of(Wert.NICHT_VEROEFFENTLICHT, "not released"),
          Arguments.of(Wert.PRUEFLAUF, "Prüflauf"),
          Arguments.of(Wert.PRUEFLAUF, "check run"));

  private static Stream<Arguments> zeilenTabelle() {
    return ZEILEN.stream();
  }

  private static Stream<Arguments> werteTabelle() {
    return WERTE.stream();
  }

  private static Stream<Arguments> koepfe() {
    return Stream.of(
        Arguments.of(Meldungsart.PLAN_REVIEW, "## Plan-Review, Runde 1"),
        Arguments.of(Meldungsart.PLAN_REVIEW, "## Plan-Review, Round 2"),
        Arguments.of(Meldungsart.FACHPLAN_REVIEW, "## Fachplan-Review, Runde 1"),
        Arguments.of(Meldungsart.FACHPLAN_REVIEW, "## Requirement-Review, Round 2"),
        Arguments.of(Meldungsart.ISSUE_REVIEW, "## Issue-Review, Runde 3"),
        Arguments.of(Meldungsart.ISSUE_REVIEW, "## Issue-Review, Round 3"),
        Arguments.of(Meldungsart.EINARBEITUNG, "## Einarbeitung, Runde 1"),
        Arguments.of(Meldungsart.EINARBEITUNG, "## Incorporation, Round 1"),
        Arguments.of(Meldungsart.ABSCHLUSSBERICHT, "## Abschlussbericht Issue #2113"),
        Arguments.of(Meldungsart.ABSCHLUSSBERICHT, "## Completion report issue #2113"),
        Arguments.of(Meldungsart.ABSCHLUSSBERICHT, "## Stand des Vorhabens"),
        Arguments.of(Meldungsart.ABSCHLUSSBERICHT, "## State of the project"),
        Arguments.of(Meldungsart.CODE_REVIEW, "## Code-Review (Schritt 6)"),
        Arguments.of(Meldungsart.CODE_REVIEW, "## Code review (step 6)"),
        Arguments.of(Meldungsart.CODE_REVIEW, "## Code-Review (Schritt 7)"),
        Arguments.of(Meldungsart.CODE_REVIEW, "## Code review (step 7)"),
        Arguments.of(Meldungsart.UEBERTRAGUNG, "## Übertragung auf den Hauptzweig"),
        Arguments.of(Meldungsart.UEBERTRAGUNG, "## Push to main"),
        Arguments.of(Meldungsart.BUILD_NACHTRAG, "## Build-Dienst nachgetragen"),
        Arguments.of(Meldungsart.BUILD_NACHTRAG, "## Build service recorded"),
        Arguments.of(Meldungsart.VEROEFFENTLICHUNG, "## Veröffentlichung"),
        Arguments.of(Meldungsart.VEROEFFENTLICHUNG, "## Release"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("zeilenTabelle")
  void rest_jederZeilenanfang_liefertDenTextDahinter(Zeile zeile, String name) {
    assertThat(zeile.rest(name + " Wert")).contains(" Wert");
  }

  @Test
  void tabelle_nenntJedeZeileDesRegisters() {
    List<Object> imTest = ZEILEN.stream().map(a -> a.get()[0]).distinct().toList();

    assertThat(imTest).containsExactlyInAnyOrder((Object[]) Zeile.values());
  }

  @Test
  void namen_inRegisterreihenfolge_deutschZuerstAltnamenZuletzt() {
    assertThat(Zeile.CODE_REVIEW.namen())
        .containsExactly(
            "## Code-Review (Schritt 6)",
            "## Code review (step 6)",
            "## Code-Review (Schritt 7)",
            "## Code review (step 7)");
  }

  @Test
  void rest_fuehrenderLeerraumUndSchreibung_gleichgueltigWieImKit() {
    assertThat(Zeile.COMMIT.rest("  COMMIT: abc")).contains(" abc");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "Kein Anfang", "Commi: abc", "x Commit: abc"})
  void rest_ohneAnfang_leer(String zeile) {
    assertThat(Zeile.COMMIT.rest(zeile)).isEmpty();
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("werteTabelle")
  void von_jederWert_inBeidenSprachen(Wert wert, String text) {
    assertThat(Wert.von(text)).contains(wert);
  }

  @Test
  void tabelle_nenntJedenWertDesRegisters() {
    List<Object> imTest = WERTE.stream().map(a -> a.get()[0]).distinct().toList();

    assertThat(imTest).containsExactlyInAnyOrder((Object[]) Wert.values());
  }

  @Test
  void von_umgebenderLeerraum_zaehltNicht() {
    assertThat(Wert.von("  gruen ")).contains(Wert.GRUEN);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "grün", "Gruen"})
  void von_unbekannterText_leer(String text) {
    assertThat(Wert.von(text)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"rot,true", "red,true", "gruen,false"})
  void bezeichnet_nurDieEigenenNamen(String text, boolean erwartet) {
    assertThat(Wert.ROT.bezeichnet(text)).isEqualTo(erwartet);
  }

  @Test
  void bezeichnet_null_falsch() {
    assertThat(Wert.ROT.bezeichnet(null)).isFalse();
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("koepfe")
  void artDerKopfzeile_jederKopf_inBeidenSprachen(Meldungsart art, String kopf) {
    assertThat(KitMeldungen.artDerKopfzeile(kopf)).contains(art);
  }

  @Test
  void artDerKopfzeile_nachlaufenderLeerraumUndEinzug_gleichgueltig() {
    assertThat(KitMeldungen.artDerKopfzeile("  ## Plan-Review, Runde 2  "))
        .contains(Meldungsart.PLAN_REVIEW);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "Hallo",
        "Commit: abc",
        "## Release notes",
        "## Push to main branch",
        "## Veröffentlichungen",
        "## Stand des Vorhabens im Mai",
        "## Code-Review (Schritt 6) nachgeholt",
        "## Plan-Review, Runde",
        "## Plan-Review, Runde x",
        "## Plan-Review, Runde 1 und 2",
        "## Plan-Review, Runde 12345678901",
        "## Einarbeitung, Runde",
        "## Abschlussbericht Issue #",
        "## Abschlussbericht Issue #abc",
        "### Reviewer 1: form, fable"
      })
  void artDerKopfzeile_keinFesterKopf_leer(String zeile) {
    assertThat(KitMeldungen.artDerKopfzeile(zeile)).isEqualTo(Optional.empty());
  }

  @Test
  void festeAnfaenge_nennenJedenNamenJedesKopfs() {
    List<String> erwartet =
        Stream.of(
                Zeile.PLAN_REVIEW_RUNDE,
                Zeile.FACHPLAN_REVIEW_RUNDE,
                Zeile.ISSUE_REVIEW_RUNDE,
                Zeile.EINARBEITUNG_RUNDE,
                Zeile.ABSCHLUSSBERICHT,
                Zeile.STAND_DES_VORHABENS,
                Zeile.CODE_REVIEW,
                Zeile.UEBERTRAGUNG,
                Zeile.BUILD_NACHGETRAGEN,
                Zeile.VEROEFFENTLICHUNG)
            .flatMap(z -> z.namen().stream())
            .toList();

    assertThat(KitMeldungen.festeAnfaenge()).containsExactlyInAnyOrderElementsOf(erwartet);
  }

  @Test
  void festeAnfaenge_ohneFeldzeilen() {
    assertThat(KitMeldungen.festeAnfaenge())
        .doesNotContainAnyElementsOf(
            Arrays.asList("Commit:", "### Reviewer", "Ergebnis:", "## Geklärter Stand"));
  }
}
