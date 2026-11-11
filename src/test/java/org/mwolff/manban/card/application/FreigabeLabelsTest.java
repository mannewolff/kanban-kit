package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.FreigabeLabels.Richtung;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Eintrag;
import org.mwolff.manban.common.KitNamen.Sache;

/** Unit-Tests der Richtungsregel für die Freigabe-Labels des Kits (Issue #1421). */
@SuppressWarnings("PMD.TooManyMethods")
class FreigabeLabelsTest {

  private static final String DEFINITION_GESPERRT =
      " ist ein Freigabe-Label; seine Definition ändert nur ein Mensch im Board";

  private static final String NAMENSRAUM_SETZEN =
      " liegt im Namensraum des Kits und ist dort unbekannt; es setzt nur ein Mensch im Board";

  private static final String NAMENSRAUM_UMBENENNEN =
      " liegt im Namensraum des Kits und ist dort unbekannt; auf diesen Namen benennt nur ein"
          + " Mensch im Board um";

  private static final Map<Long, String> NAMEN =
      Map.of(1L, "kit:night", 2L, "kit:klaeren", 3L, "bug");

  private static final ActorStamp APP =
      new ActorStamp(CardActivityOrigin.TOKEN, "Desktop-App", null, null, true);

  private static final ActorStamp TOKEN =
      new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", null, null);

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
  @ValueSource(strings = {"kit:klaeren", "kit:geschuetzt", "kit:wartet"})
  void token_darfMaschinenLabelSetzen(String name) {
    assertThatCode(() -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.SETZEN))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:klaeren", "kit:geschuetzt", "kit:wartet"})
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
    for (String name :
        List.of("kit:night", "kit:nightrun", "kit:klaeren", "kit:geschuetzt", "kit:on-hold")) {
      for (Richtung richtung : Richtung.values()) {
        assertThatCode(() -> FreigabeLabels.pruefe(herkunft, name, richtung))
            .doesNotThrowAnyException();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:manuell", "kit:manual"})
  void token_darfManuellWederSetzenNochAbnehmen(String name) {
    assertThatThrownBy(() -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + name + " setzt nur ein Mensch im Board");
    assertThatThrownBy(
            () -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.ABNEHMEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + name + " nimmt nur ein Mensch im Board ab");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "SESSION")
  void mensch_darfManuellSetzenUndAbnehmen(CardActivityOrigin herkunft) {
    for (String name : List.of("kit:manuell", "kit:manual")) {
      for (Richtung richtung : Richtung.values()) {
        assertThatCode(() -> FreigabeLabels.pruefe(herkunft, name, richtung))
            .doesNotThrowAnyException();
      }
    }
  }

  @Test
  void appZugang_darfManuellWederSetzenNochAbnehmen() {
    assertThatThrownBy(() -> FreigabeLabels.pruefeEinzeln(APP, "kit:manuell", Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class);
    assertThatThrownBy(() -> FreigabeLabels.pruefeEinzeln(APP, "kit:manuell", Richtung.ABNEHMEN))
        .isInstanceOf(FreigabeLabelException.class);
  }

  @Test
  void token_darfManuellWederPerWechselSetzenNochAbnehmen() {
    Map<Long, String> namen = Map.of(7L, "kit:manuell", 3L, "bug");
    assertThatThrownBy(
            () ->
                FreigabeLabels.pruefeWechsel(
                    CardActivityOrigin.TOKEN, namen, List.of(3L), List.of(3L, 7L)))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:manuell setzt nur ein Mensch im Board");
    assertThatThrownBy(
            () ->
                FreigabeLabels.pruefeWechsel(
                    CardActivityOrigin.TOKEN, namen, List.of(3L, 7L), List.of(3L)))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:manuell nimmt nur ein Mensch im Board ab");
  }

  @Test
  void token_darfManuellNichtLoeschenOderUmbenennen() {
    assertThatThrownBy(
            () -> FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "kit:manuell", null))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:manuell" + DEFINITION_GESPERRT);
    assertThatThrownBy(
            () -> FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "bug", "kit:manual"))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:manual" + DEFINITION_GESPERRT);
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

  @ParameterizedTest
  @ValueSource(strings = {"kit:repair", "kit:hotfix"})
  void token_darfReparaturUndHotfixSetzenUndAbnehmen(String name) {
    assertThatCode(() -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.SETZEN))
        .doesNotThrowAnyException();
    assertThatCode(() -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.ABNEHMEN))
        .doesNotThrowAnyException();
  }

  @Test
  void token_unbekannterNameImNamensraum_weiterAbgewiesen() {
    assertThatThrownBy(
            () ->
                FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, "kit:gibtsnicht", Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:gibtsnicht" + NAMENSRAUM_SETZEN);
  }

  @Test
  void namensvergleich_getrimmt() {
    assertThatThrownBy(
            () -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, "  kit:night ", Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:night setzt nur ein Mensch im Board");
  }

  @Test
  void namensvergleich_grossKleinSchreibung_unbekannterNameImNamensraum() {
    // requireLabelId löst case-sensitiv auf: "Kit:Night" ist ein anderes Label als "kit:night".
    // Seit Issue #1586 sperrt es trotzdem der Namensraum, nicht die Sache NIGHT.
    assertThatThrownBy(
            () -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, "Kit:Night", Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label Kit:Night" + NAMENSRAUM_SETZEN);
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

  // --- Beide Sprachen und Namensraum des Kits (Issue #1586, Plan #1582 E7, E8) ----------------

  @ParameterizedTest
  @ValueSource(strings = {"kit:night", "kit:nightrun"})
  void englisch_token_darfNurMenschenLabelNichtSetzen(String name) {
    assertThatThrownBy(() -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + name + " setzt nur ein Mensch im Board");
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:clarify", "kit:protected", "kit:on-hold"})
  void englisch_token_darfMaschinenLabelNichtAbnehmen(String name) {
    assertThatThrownBy(
            () -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.ABNEHMEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + name + " nimmt nur ein Mensch im Board ab");
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:clarify", "kit:protected", "kit:on-hold"})
  void englisch_token_darfMaschinenLabelSetzen(String name) {
    assertThatCode(() -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.SETZEN))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"kit:protected", "kit:clarify", "kit:geschuetzt", "kit:night", "kit:on-hold"})
  void definition_token_loeschenInBeidenSprachenAbgewiesen(String name) {
    assertThatThrownBy(() -> FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, name, null))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + name + DEFINITION_GESPERRT);
  }

  @Test
  void definition_token_umbenennenAufEnglischenGeschuetztenNamenAbgewiesen() {
    assertThatThrownBy(
            () -> FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "bug", "kit:clarify"))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:clarify" + DEFINITION_GESPERRT);
  }

  @Test
  void definition_token_umbenennenVonEnglischemGeschuetztenNamenAbgewiesen() {
    assertThatThrownBy(
            () -> FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "kit:protected", "x"))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:protected" + DEFINITION_GESPERRT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:freigabe", "KIT:night", "Review: UX", "  lauf:neu "})
  void namensraum_token_unbekannterNameSetzenAbgewiesen(String name) {
    assertThatThrownBy(() -> FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + name.trim() + NAMENSRAUM_SETZEN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:freigabe", "KIT:night", "Review: UX"})
  void namensraum_token_umbenennenAufUnbekanntenNamenAbgewiesen(String name) {
    assertThatThrownBy(() -> FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "bug", name))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + name + NAMENSRAUM_UMBENENNEN);
  }

  @Test
  void namensraum_token_neuerUnbekannterNameImWechselAbgewiesen() {
    assertThatThrownBy(
            () ->
                FreigabeLabels.pruefeWechsel(
                    CardActivityOrigin.TOKEN,
                    Map.of(3L, "bug", 5L, "Review: UX"),
                    List.of(3L),
                    List.of(3L, 5L)))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label Review: UX" + NAMENSRAUM_SETZEN);
  }

  @Test
  void namensraum_token_abnehmenUndLoeschenUnbekannterNamenErlaubt() {
    // E8: Anlegen und Abnehmen bleiben erlaubt; ein angelegtes, nicht setzbares Label gibt nichts
    // frei. Das Löschen und das Umbenennen weg vom Namen nehmen ebenso nur etwas weg.
    assertThatCode(
            () -> {
              FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, "kit:freigabe", Richtung.ABNEHMEN);
              FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, "Review: UX", Richtung.ABNEHMEN);
              FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "Review: UX", null);
              FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "Review: UX", "UX");
              FreigabeLabels.pruefeDefinition(
                  CardActivityOrigin.TOKEN, "kit:freigabe", "kit:freigabe");
              FreigabeLabels.pruefeWechsel(
                  CardActivityOrigin.TOKEN,
                  Map.of(3L, "bug", 5L, "Review: UX"),
                  List.of(3L, 5L),
                  List.of(3L));
            })
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"run:running", "goal:plan", "review:done", "kit:check", "lauf:laeuft"})
  void namensraum_token_bekannterNameOhneSchutzSetzbar(String name) {
    assertThatCode(
            () -> {
              FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.SETZEN);
              FreigabeLabels.pruefe(CardActivityOrigin.TOKEN, name, Richtung.ABNEHMEN);
              FreigabeLabels.pruefeDefinition(CardActivityOrigin.TOKEN, "bug", name);
            })
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "SESSION")
  void sessionUndUnbekannt_duerfenAllesAuchEnglischUndImNamensraum(CardActivityOrigin herkunft) {
    for (String name :
        List.of("kit:clarify", "kit:protected", "kit:freigabe", "KIT:night", "Review: UX")) {
      assertThatCode(
              () -> {
                for (Richtung richtung : Richtung.values()) {
                  FreigabeLabels.pruefe(herkunft, name, richtung);
                }
                FreigabeLabels.pruefeDefinition(herkunft, name, null);
                FreigabeLabels.pruefeDefinition(herkunft, "bug", name);
                FreigabeLabels.pruefeWechsel(herkunft, Map.of(5L, name), List.of(), List.of(5L));
              })
          .doesNotThrowAnyException();
    }
  }

  // --- Zugang der App: die beiden Ausnahmen beim einzelnen Ändern (Issue #2106, Plan #2100 E2) --

  @ParameterizedTest
  @ValueSource(strings = {"kit:night", "  kit:night "})
  void einzeln_appZugang_setztDurchlaufFortsetzen(String name) {
    assertThatCode(() -> FreigabeLabels.pruefeEinzeln(APP, name, Richtung.SETZEN))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:klaeren", "kit:clarify", "  kit:clarify "})
  void einzeln_appZugang_nimmtKlaerungNoetigAb(String name) {
    assertThatCode(() -> FreigabeLabels.pruefeEinzeln(APP, name, Richtung.ABNEHMEN))
        .doesNotThrowAnyException();
  }

  @Test
  void einzeln_appZugang_setztKeineAndereFreigabe() {
    assertThatThrownBy(() -> FreigabeLabels.pruefeEinzeln(APP, "kit:nightrun", Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:nightrun setzt nur ein Mensch im Board");
  }

  @ParameterizedTest
  @ValueSource(strings = {"kit:geschuetzt", "kit:protected", "kit:wartet", "kit:on-hold"})
  void einzeln_appZugang_nimmtKeineAndereFreigabeAb(String name) {
    assertThatThrownBy(() -> FreigabeLabels.pruefeEinzeln(APP, name, Richtung.ABNEHMEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + name + " nimmt nur ein Mensch im Board ab");
  }

  @Test
  void einzeln_appZugang_unbekannterNameImNamensraumBleibtGesperrt() {
    assertThatThrownBy(() -> FreigabeLabels.pruefeEinzeln(APP, "kit:freigabe", Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:freigabe" + NAMENSRAUM_SETZEN);
  }

  @Test
  void einzeln_appZugang_behaeltWasJedesTokenDarf() {
    assertThatCode(
            () -> {
              FreigabeLabels.pruefeEinzeln(APP, "kit:night", Richtung.ABNEHMEN);
              FreigabeLabels.pruefeEinzeln(APP, "kit:klaeren", Richtung.SETZEN);
              FreigabeLabels.pruefeEinzeln(APP, "bug", Richtung.SETZEN);
              FreigabeLabels.pruefeEinzeln(APP, "bug", Richtung.ABNEHMEN);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void einzeln_gewoehnlichesToken_bekommtKeineAusnahme() {
    assertThatThrownBy(() -> FreigabeLabels.pruefeEinzeln(TOKEN, "kit:night", Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:night setzt nur ein Mensch im Board");
    assertThatThrownBy(() -> FreigabeLabels.pruefeEinzeln(TOKEN, "kit:clarify", Richtung.ABNEHMEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:clarify nimmt nur ein Mensch im Board ab");
  }

  @Test
  void einzeln_sessionUndUnbekannt_duerfenWeiterAlles() {
    for (ActorStamp akteur :
        List.of(
            ActorStamp.unknown(), new ActorStamp(CardActivityOrigin.SESSION, null, null, null))) {
      for (Richtung richtung : Richtung.values()) {
        assertThatCode(() -> FreigabeLabels.pruefeEinzeln(akteur, "kit:geschuetzt", richtung))
            .doesNotThrowAnyException();
      }
    }
  }

  @Test
  void einzeln_prueftGegenDasUebergebeneRegister() {
    // Die Ausnahme hängt an der Sache, nicht am Namen (E13).
    KitNamen register =
        KitNamen.aus(
            List.of(
                new Eintrag(Sache.NIGHT, "kit:fortsetzen"),
                new Eintrag(Sache.KLAEREN, "kit:rueckfrage")));

    assertThatCode(
            () -> {
              FreigabeLabels.pruefeEinzeln(register, APP, "kit:fortsetzen", Richtung.SETZEN);
              FreigabeLabels.pruefeEinzeln(register, APP, "kit:rueckfrage", Richtung.ABNEHMEN);
            })
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> FreigabeLabels.pruefeEinzeln(register, TOKEN, "kit:fortsetzen", Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class);
    assertThatThrownBy(
            () -> FreigabeLabels.pruefeEinzeln(register, APP, "kit:night", Richtung.SETZEN))
        .isInstanceOf(FreigabeLabelException.class);
  }
}
