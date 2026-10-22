package org.mwolff.manban.nightrun.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.nightrun.domain.NightRunConcurrency;

/**
 * Die Deutung der drei Angaben zur gleichzeitigen Umsetzung (Issue #1877, Plan #1876 E2–E4): Ein
 * Wert außerhalb seines Wertebereichs wird „nicht gemeldet“, ein geführtes Feld macht trotzdem
 * einen Record.
 */
class IngestConcurrencyTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static JsonNode knoten(String json) {
    try {
      return JSON.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(json, e);
    }
  }

  private static NightRunConcurrency nurConcurrent(JsonNode wert) {
    NightRunConcurrency ergebnis = IngestConcurrency.toDomain(wert, null, null);
    assertThat(ergebnis).isNotNull();
    return ergebnis;
  }

  private static NightRunConcurrency nurWartezeit(JsonNode wert) {
    NightRunConcurrency ergebnis = IngestConcurrency.toDomain(null, wert, null);
    assertThat(ergebnis).isNotNull();
    return ergebnis;
  }

  @Test
  void alleDreiFehlend_ergibtKeinenRecord() {
    assertThat(IngestConcurrency.toDomain(null, null, null)).isNull();
  }

  @Test
  void alleDreiGemeldet_werdenUebernommen() {
    assertThat(IngestConcurrency.toDomain(knoten("true"), knoten("420000"), knoten("false")))
        .isEqualTo(new NightRunConcurrency(true, 420_000L, false));
  }

  @Test
  void alleDreiKaputt_ergibtEinenRecordMitDreiNull() {
    assertThat(IngestConcurrency.toDomain(knoten("\"ja\""), knoten("-1"), knoten("1")))
        .isEqualTo(new NightRunConcurrency(null, null, null));
  }

  /** E2: Ein ausdrückliches JSON-null ist ein geführtes Feld ohne Wert. */
  @Test
  void einNullNodeGiltAlsGefuehrt_undAlsNichtGemeldet() {
    assertThat(IngestConcurrency.toDomain(NullNode.getInstance(), null, null))
        .isEqualTo(new NightRunConcurrency(null, null, null));
    assertThat(IngestConcurrency.toDomain(null, NullNode.getInstance(), null))
        .isEqualTo(new NightRunConcurrency(null, null, null));
    assertThat(IngestConcurrency.toDomain(null, null, NullNode.getInstance()))
        .isEqualTo(new NightRunConcurrency(null, null, null));
  }

  @Test
  void einWahrheitswertWirdUebernommen() {
    assertThat(nurConcurrent(knoten("true")).concurrent()).isTrue();
    assertThat(nurConcurrent(knoten("false")).concurrent()).isFalse();
    assertThat(IngestConcurrency.toDomain(null, null, knoten("true")))
        .isEqualTo(new NightRunConcurrency(null, null, true));
  }

  /** E3: Auch der Text "true" ist kein Wahrheitswert. */
  @ParameterizedTest
  @ValueSource(strings = {"\"ja\"", "\"true\"", "1", "0", "{}", "[]"})
  void keinWahrheitswertWirdNichtGemeldet(String json) {
    assertThat(nurConcurrent(knoten(json))).isEqualTo(new NightRunConcurrency(null, null, null));
    assertThat(IngestConcurrency.toDomain(null, null, knoten(json)))
        .isEqualTo(new NightRunConcurrency(null, null, null));
  }

  @Test
  void eineWartezeitAbNullWirdUebernommen() {
    assertThat(nurWartezeit(knoten("776000")).checkWaitMs()).isEqualTo(776_000L);
    assertThat(nurWartezeit(knoten("1")).checkWaitMs()).isEqualTo(1L);
    assertThat(nurWartezeit(knoten(String.valueOf(Long.MAX_VALUE))).checkWaitMs())
        .isEqualTo(Long.MAX_VALUE);
  }

  /** K7: Eine gemeldete 0 ist ein Wert, kein „nicht gemeldet“. */
  @Test
  void eineWartezeitVonNullBleibtNull() {
    assertThat(nurWartezeit(knoten("0")).checkWaitMs()).isZero();
  }

  /** E3: negativ, Gleitkomma, Text, zu groß für long und Wahrheitswert werden nicht gemeldet. */
  @ParameterizedTest
  @ValueSource(strings = {"-1", "1.5", "420000.0", "\"420000\"", "9223372036854775808", "true"})
  void eineWartezeitAusserhalbDesWertebereichsWirdNichtGemeldet(String json) {
    assertThat(nurWartezeit(knoten(json)).checkWaitMs()).isNull();
  }

  /** E17: Die Beschreibung von checkWaitMs nennt den Zusammenhang mit der Paketdauer. */
  @Test
  void dieBeschreibungDerWartezeitNenntDieDauer() {
    assertThat(IngestConcurrency.CHECK_WAIT_MS_BESCHREIBUNG)
        .contains("Die gemeldete Paketdauer `durationMs` enthält diese Wartezeit.");
  }
}
