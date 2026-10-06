package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.card.application.FreigabeLabels.Richtung;
import org.mwolff.manban.card.domain.CardActivityOrigin;

/** Unit-Tests der Richtungsregel für die Freigabe-Labels des Kits (Issue #1421). */
class FreigabeLabelsTest {

  private static final String DEFINITION_GESPERRT =
      " ist ein Freigabe-Label; seine Definition ändert nur ein Mensch im Board";

  private static final Map<Long, String> NAMEN =
      Map.of(1L, "kit:night", 2L, "kit:klaeren", 3L, "bug");

  @ParameterizedTest
  @ValueSource(strings = {"kit:night", "kit:nightrun"})
  void token_darfNurMenschenLabelNichtSetzen(String name) {
    assertThatThrownBy(() -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + name + " setzt nur ein Mensch im Board");
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:night", "kit:nightrun"})
  void token_darfNurMenschenLabelAbnehmen(String name) {
    assertThatCode(() -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.ABNEHMEN))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:klaeren", "kit:geschuetzt"})
  void token_darfMaschinenLabelSetzen(String name) {
    assertThatCode(() -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.SETZEN))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:klaeren", "kit:geschuetzt"})
  void token_darfMaschinenLabelNichtAbnehmen(String name) {
    assertThatThrownBy(
            () -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.ABNEHMEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + name + " nimmt nur ein Mensch im Board ab");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "SESSION")
  void sessionUndUnbekannt_duerfenAllesInBeideRichtungen(CardActivityOrigin herkunft) {
    for (String name : List.of("kit:night", "kit:nightrun", "kit:klaeren", "kit:geschuetzt")) {
      for (Richtung richtung : Richtung.values()) {
        assertThatCode(() -> FreigabeLabels.pruefe(herkunft, name, richtung))
            .doesNotThrowAnyException();
      }
    }
  }

  @Test
  void token_gewoehnlichesLabel_inBeideRichtungen() {
    assertThatCode(
            () -> {
              FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, "bug", Richtung.SETZEN);
              FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, "bug", Richtung.ABNEHMEN);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void namensvergleich_getrimmt() {
    assertThatThrownBy(
            () -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, "  kit:night ", Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:night setzt nur ein Mensch im Board");
  }

  @Test
  void namensvergleich_grossKleinSchreibungWieDieLabelAufloesung() {
    // requireLabelId löst case-sensitiv auf: "Kit:Night" ist ein anderes Label als "kit:night".
    assertThatCode(
            () -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, "Kit:Night", Richtung.SETZEN))
        .doesNotThrowAnyException();
  }

  @Test
  void wechsel_token_neuHinzuGekommenesNurMenschenLabel_abgewiesen() {
    assertThatThrownBy(
            () ->
                FreigabeLabels.pruefeWechsel(
                    CardActivityOrigin.TOKEN, NAMEN, List.of(2L), List.of(2L, 1L)))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:night setzt nur ein Mensch im Board");
  }

  @Test
  void wechsel_token_weggefallenesMaschinenLabel_abgewiesen() {
    assertThatThrownBy(
            () ->
                FreigabeLabels.pruefeWechsel(
                    CardActivityOrigin.TOKEN, NAMEN, List.of(2L, 3L), List.of(3L)))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:klaeren nimmt nur ein Mensch im Board ab");
  }

  @Test
  void wechsel_token_unveraenderteFreigabeLabels_durchgelassen() {
    assertThatCode(
            () ->
                FreigabeLabels.pruefeWechsel(
                    CardActivityOrigin.TOKEN, NAMEN, List.of(1L, 2L), List.of(2L, 1L, 3L)))
        .doesNotThrowAnyException();
  }

  @Test
  void wechsel_token_nurMenschenLabelAbnehmenUndMaschinenLabelSetzen_durchgelassen() {
    assertThatCode(
            () ->
                FreigabeLabels.pruefeWechsel(
                    CardActivityOrigin.TOKEN, NAMEN, List.of(1L), List.of(2L)))
        .doesNotThrowAnyException();
  }

  /**
   * Der Start der Kette nimmt {@code kit:night}, {@code ziel:*} und {@code planreview:*} zusammen
   * ab (Kit E1); beide Label-Familien stehen in keiner Sperrliste (Plan #1447, E11).
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "ziel:plan",
        "ziel:pakete",
        "ziel:umsetzung",
        "ziel:push-vorbereitet",
        "planreview:1",
        "planreview:2"
      })
  void token_darfZielUndPrueferLabelAbnehmen(String name) {
    assertThatCode(() -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.ABNEHMEN))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                FreigabeLabels.pruefeWechsel(
                    CardActivityOrigin.TOKEN,
                    Map.of(1L, "kit:night", 4L, name, 3L, "bug"),
                    List.of(1L, 4L, 3L),
                    List.of(3L)))
        .doesNotThrowAnyException();
  }

  @Test
  void wechsel_unbekannteId_istKeinFreigabeLabel() {
    assertThatCode(
            () ->
                FreigabeLabels.pruefeWechsel(
                    CardActivityOrigin.TOKEN, NAMEN, List.of(99L), List.of(98L)))
        .doesNotThrowAnyException();
  }

  @Test
  void wechsel_session_darfAlles() {
    assertThatCode(
            () ->
                FreigabeLabels.pruefeWechsel(
                    CardActivityOrigin.SESSION, NAMEN, List.of(2L), List.of(1L)))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:night", "kit:nightrun", "kit:klaeren", "kit:geschuetzt"})
  void definition_token_loeschenAbgewiesen(String name) {
    assertThatThrownBy(() -> FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, name, null))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + name + DEFINITION_GESPERRT);
  }

  @Test
  void definition_token_umbenennenVonGeschuetztemNamenAbgewiesen() {
    assertThatThrownBy(
            () -> FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "kit:klaeren", "x"))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessageContaining("kit:klaeren");
  }

  @Test
  void definition_token_umbenennenAufGeschuetztenNamenAbgewiesen() {
    assertThatThrownBy(
            () -> FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "bug", " kit:night "))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:night" + DEFINITION_GESPERRT);
  }

  @Test
  void definition_token_gleicherNameBleibt_durchgelassen() {
    // Nur Farbe/Zählung geändert: der Name bleibt, keine Umbenennung.
    assertThatCode(
            () ->
                FreigabeLabels.pruefeDefinition(
                    CardActivityOrigin.TOKEN, "kit:klaeren", "kit:klaeren"))
        .doesNotThrowAnyException();
  }

  @Test
  void definition_token_gewoehnlichesLabel_durchgelassen() {
    assertThatCode(
            () -> {
              FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "bug", "feature");
              FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "bug", null);
            })
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "SESSION")
  void definition_sessionUndUnbekannt_duerfenAlles(CardActivityOrigin herkunft) {
    assertThatCode(
            () -> {
              FreigabeLabels.pruefeDefinition(herkunft, "kit:klaeren", null);
              FreigabeLabels.pruefeDefinition(herkunft, "kit:klaeren", "x");
              FreigabeLabels.pruefeDefinition(herkunft, "bug", "kit:night");
            })
        .doesNotThrowAnyException();
  }
}
