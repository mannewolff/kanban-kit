package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Kopiermethode des Arbeitspakets: Der Fremdschlüssel kommt erst beim Schreiben dazu (#721). */
class NightRunItemTest {

  private static final NightRunItem OHNE_LAUF =
      new NightRunItem(
          null,
          null,
          721,
          "Migration, Domaene und Persistenz",
          NightRunState.RED,
          NightRunErrorClass.CHECKS_RED,
          92_000L,
          "4c9f42a",
          "  Issue #721: gelaufen: mvn verify -> rot");

  @Test
  void withNightRunIdSetztDenFremdschluessel() {
    NightRunItem mitLauf = OHNE_LAUF.withNightRunId(4711L);

    assertThat(mitLauf.nightRunId()).isEqualTo(4711L);
  }

  @Test
  void withNightRunIdLaesstJedeAndereKomponenteUnveraendert() {
    NightRunItem mitLauf = OHNE_LAUF.withNightRunId(4711L);

    assertThat(mitLauf)
        .extracting(
            NightRunItem::id,
            NightRunItem::cardNumber,
            NightRunItem::title,
            NightRunItem::state,
            NightRunItem::errorClass,
            NightRunItem::durationMs,
            NightRunItem::commitHash,
            NightRunItem::excerpt)
        .containsExactly(
            null,
            721,
            "Migration, Domaene und Persistenz",
            NightRunState.RED,
            NightRunErrorClass.CHECKS_RED,
            92_000L,
            "4c9f42a",
            "  Issue #721: gelaufen: mvn verify -> rot");
  }

  @Test
  void withNightRunIdLaesstDasOriginalUnberuehrt() {
    OHNE_LAUF.withNightRunId(4711L);

    assertThat(OHNE_LAUF.nightRunId()).isNull();
  }
}
