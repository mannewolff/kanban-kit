package org.mwolff.manban.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Suche nach einem Kit-Block in den Zeilen eines Kommentars (Issue #2210, Vertrag V1; #2235). */
class KitBlockTest {

  private static final String KENNUNG = "kit-vorlage";

  @Test
  void zweiBackticksOeffnenKeinenBlock() {
    // Given
    List<String> zeilen = List.of("``json kit-vorlage", "{}", "``");

    // When / Then
    assertThat(KitBlock.finde(zeilen, 0, KENNUNG)).isEmpty();
  }

  @Test
  void blockEndetAnDerSchliessendenZeileMitGleichVielenBackticks() {
    // Given
    List<String> zeilen = List.of("```json kit-vorlage", "{\"a\": 1}", "```", "danach");

    // When
    KitBlock.Fund fund = KitBlock.finde(zeilen, 0, KENNUNG).orElseThrow();

    // Then
    assertThat(fund.beginn()).isZero();
    assertThat(fund.inhalt()).isEqualTo("{\"a\": 1}\n");
  }

  @Test
  void kuerzererZaunUndLeerzeileSchliessenDenBlockNicht() {
    // Given
    List<String> zeilen = List.of("````json kit-vorlage", "```", "", "x", "````", "danach");

    // When
    KitBlock.Fund fund = KitBlock.finde(zeilen, 0, KENNUNG).orElseThrow();

    // Then
    assertThat(fund.inhalt()).isEqualTo("```\n\nx\n");
  }

  @Test
  void ohneSchliessendeZeileEndetDerBlockAmTextende() {
    // Given
    List<String> zeilen = List.of("text", "```json kit-vorlage", "{}", "rest");

    // When
    KitBlock.Fund fund = KitBlock.finde(zeilen, 0, KENNUNG).orElseThrow();

    // Then
    assertThat(fund.beginn()).isEqualTo(1);
    assertThat(fund.inhalt()).isEqualTo("{}\nrest\n");
  }
}
