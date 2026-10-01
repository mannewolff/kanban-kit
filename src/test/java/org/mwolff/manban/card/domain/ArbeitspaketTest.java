package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Präfixregel, Spaltenregel und Done-Maßstab eines Arbeitspakets (Plan #1294, E3–E7). */
class ArbeitspaketTest {

  private static final Instant FIXED = Instant.parse("2026-01-01T00:00:00Z");

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[Idee] roh",
        "[Fachlich] Story",
        "[Plan] Weg",
        "[plan] klein",
        "[PLAN] groß",
        "  [Plan] mit Leerzeichen",
        "\t[Plan] mit Tabulator",
        "\n[Fachlich] mit Zeilenumbruch"
      })
  void istArbeitspaket_nichtBeiDokumentpraefix(String titel) {
    assertThat(Arbeitspaket.istArbeitspaket(CardType.CARD, titel)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Statusfeld an der Karte",
        "[Task] klein",
        "[Mensch] Zugang anlegen",
        "Ein [Plan] mitten im Titel",
        "[Planung] ist kein Präfix",
        ""
      })
  void istArbeitspaket_beiNormalerKarte(String titel) {
    assertThat(Arbeitspaket.istArbeitspaket(CardType.CARD, titel)).isTrue();
  }

  @Test
  void istArbeitspaket_nieBeiVorhaben() {
    assertThat(Arbeitspaket.istArbeitspaket(CardType.EPIC, "Vorhaben ohne Präfix")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Wartet auf Zulieferung", "Anstehend", "Done (Archiv)", "Inprog", ""})
  void statusVonSpalte_leerBeiEigenerSpalte(String name) {
    assertThat(Arbeitspaket.statusVonSpalte(name)).isEmpty();
  }

  @Test
  void statusVonSpalte_leerOhneNamen() {
    assertThat(Arbeitspaket.statusVonSpalte(null)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Backlog|BACKLOG",
        "READY|READY",
        "In progress|IN_PROGRESS",
        "In-Progress|IN_PROGRESS",
        "In Review|IN_REVIEW",
        "'  done  '|DONE"
      })
  void statusVonSpalte_trifftProzessspalte(String name, CardStatus erwartet) {
    assertThat(Arbeitspaket.statusVonSpalte(name)).contains(erwartet);
  }

  @Test
  void effektivDone_statusDoneInEigenerSpalte() {
    assertThat(Arbeitspaket.effektivDone(karte(CardStatus.DONE), "Anstehend")).isTrue();
  }

  @Test
  void effektivDone_statusNichtDoneInDoneSpalte() {
    assertThat(Arbeitspaket.effektivDone(karte(CardStatus.IN_REVIEW), "Done")).isFalse();
  }

  @Test
  void effektivDone_ohneStatusInDoneSpalte() {
    assertThat(Arbeitspaket.effektivDone(karte(null), "Done")).isTrue();
  }

  /** Ohne Status zählt die Spalte wie bisher mit der Substring-Regel (E6). */
  @Test
  void effektivDone_ohneStatusInDoneArchivSpalte() {
    assertThat(Arbeitspaket.effektivDone(karte(null), "Done (Archiv)")).isTrue();
  }

  @Test
  void effektivDone_ohneStatusInEigenerSpalte() {
    assertThat(Arbeitspaket.effektivDone(karte(null), "Anstehend")).isFalse();
  }

  @Test
  void effektivDone_ohneStatusOhneSpaltennamen() {
    assertThat(Arbeitspaket.effektivDone(karte(null), null)).isFalse();
  }

  private static Card karte(@Nullable CardStatus status) {
    return new Card(
        1L,
        10L,
        20L,
        5,
        "T",
        null,
        0,
        false,
        null,
        1L,
        FIXED,
        FIXED,
        CardType.CARD,
        null,
        null,
        null,
        1L,
        null,
        null,
        null,
        status);
  }
}
