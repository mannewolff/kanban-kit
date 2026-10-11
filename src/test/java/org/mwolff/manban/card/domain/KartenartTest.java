package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Die Art einer Karte nach Typ und Titel-Präfix (Plan #1574, E1). */
class KartenartTest {

  @ParameterizedTest
  @CsvSource({
    "'[Fachlich] Archiv lesen', FACHLICH",
    "'[Plan] Archiv lesen', PLAN",
    "'[Idee] Archiv lesen', IDEE",
    "'[Task] Archiv lesen', TASK",
    "'[Mensch] Token anlegen', MENSCH"
  })
  void praefix_bestimmtDieArt(String titel, Kartenart erwartet) {
    assertThat(Kartenart.von(CardType.CARD, titel)).isEqualTo(erwartet);
  }

  @ParameterizedTest
  @CsvSource({
    "'[FACHLICH] laut', FACHLICH",
    "'[plan] leise', PLAN",
    "'[iDeE] gemischt', IDEE",
    "'[TASK] laut', TASK",
    "'[mensch] leise', MENSCH"
  })
  void praefix_ohneRuecksichtAufGrossUndKleinschreibung(String titel, Kartenart erwartet) {
    assertThat(Kartenart.von(CardType.CARD, titel)).isEqualTo(erwartet);
  }

  /** Englische Titel-Präfixe aus dem Namen-Register (Issue #1587, Plan #1582 E2). */
  @ParameterizedTest
  @CsvSource({
    "'[Requirement] Archiv lesen', FACHLICH",
    "'[Idea] Archiv lesen', IDEE",
    "'[Human] Token anlegen', MENSCH",
    "'[REQUIREMENT] laut', FACHLICH",
    "'[idea] leise', IDEE",
    "'[hUmAn] gemischt', MENSCH",
    "'  [Requirement] eingerückt', FACHLICH",
    "'\t[Idea] Tab', IDEE"
  })
  void englischesPraefix_bestimmtDieArt(String titel, Kartenart erwartet) {
    assertThat(Kartenart.von(CardType.CARD, titel)).isEqualTo(erwartet);
  }

  @Test
  void fuehrendeLeerzeichen_vorDemPraefix_zaehlenNicht() {
    assertThat(Kartenart.von(CardType.CARD, "   [Plan] eingerückt")).isEqualTo(Kartenart.PLAN);
    assertThat(Kartenart.von(CardType.CARD, "\t[Fachlich] Tab")).isEqualTo(Kartenart.FACHLICH);
  }

  @Test
  void vorhaben_istVorhaben_auchMitPraefixImTitel() {
    assertThat(Kartenart.von(CardType.EPIC, "Archivzugang")).isEqualTo(Kartenart.VORHABEN);
    assertThat(Kartenart.von(CardType.EPIC, "[Plan] Archivzugang")).isEqualTo(Kartenart.VORHABEN);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Kanban-Strecke: Einzelabruf",
        "Plan ohne Klammern",
        "[Unbekannt] fremdes Präfix",
        "[Plan ohne schließende Klammer",
        "[ Plan] Leerzeichen in der Klammer",
        ""
      })
  void ohneBekanntesPraefix_istEsEinArbeitspaket(String titel) {
    assertThat(Kartenart.von(CardType.CARD, titel)).isEqualTo(Kartenart.ARBEITSPAKET);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Nachtrag zu [Fachlich] Archiv", "Siehe [Plan]", "x[Task] y"})
  void praefixNichtAmAnfang_zaehltNicht(String titel) {
    assertThat(Kartenart.von(CardType.CARD, titel)).isEqualTo(Kartenart.ARBEITSPAKET);
  }
}
