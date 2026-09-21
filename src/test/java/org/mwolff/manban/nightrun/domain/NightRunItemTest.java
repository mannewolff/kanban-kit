package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Kopiermethode des Arbeitspakets: Der Fremdschlüssel kommt erst beim Schreiben dazu (#721). */
class NightRunItemTest {

  /** Eine Stufe am Paket, damit die Kopiermethode sie nachweislich mitnimmt (Issue #1112). */
  private static final NightRunItemStage STUFE =
      new NightRunItemStage(NightRunStage.PLAN, 5_000L, null);

  private static final NightRunItem OHNE_LAUF =
      new NightRunItem(
          null,
          null,
          42L,
          Instant.parse("2026-09-01T22:00:00Z"),
          NightRunMode.CHAIN,
          NightRunKind.NIGHT,
          721,
          "Migration, Domaene und Persistenz",
          NightRunState.RED,
          NightRunErrorClass.CHECKS_RED,
          92_000L,
          "4c9f42a",
          "  Issue #721: gelaufen: mvn verify -> rot",
          null,
          List.of(STUFE));

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
            NightRunItem::projectId,
            NightRunItem::startedAt,
            NightRunItem::mode,
            NightRunItem::kind,
            NightRunItem::cardNumber,
            NightRunItem::title,
            NightRunItem::state,
            NightRunItem::errorClass,
            NightRunItem::durationMs,
            NightRunItem::commitHash,
            NightRunItem::excerpt,
            NightRunItem::stages)
        .containsExactly(
            null,
            42L,
            Instant.parse("2026-09-01T22:00:00Z"),
            NightRunMode.CHAIN,
            NightRunKind.NIGHT,
            721,
            "Migration, Domaene und Persistenz",
            NightRunState.RED,
            NightRunErrorClass.CHECKS_RED,
            92_000L,
            "4c9f42a",
            "  Issue #721: gelaufen: mvn verify -> rot",
            List.of(STUFE));
  }

  /**
   * Die Stufenliste ist unveränderlich, auch wenn eine veränderliche hineingereicht wurde (Issue
   * #1112): Ein Aufrufer, der seine Liste danach weiterfüllt, änderte sonst ein gespeichertes Paket
   * nachträglich.
   */
  @Test
  void dieStufenlisteIstUnveraenderlich() {
    List<NightRunItemStage> veraenderlich = new ArrayList<>(List.of(STUFE));
    NightRunItem paket = paketMit(veraenderlich);

    veraenderlich.clear();

    assertThat(paket.stages()).containsExactly(STUFE);
    assertThat(paket.stages()).isUnmodifiable();
  }

  private static NightRunItem paketMit(List<NightRunItemStage> stages) {
    return new NightRunItem(
        null,
        null,
        42L,
        Instant.parse("2026-09-01T22:00:00Z"),
        NightRunMode.CHAIN,
        NightRunKind.NIGHT,
        721,
        "Migration, Domaene und Persistenz",
        NightRunState.RED,
        NightRunErrorClass.CHECKS_RED,
        92_000L,
        "4c9f42a",
        "  Issue #721: gelaufen: mvn verify -> rot",
        null,
        stages);
  }

  @Test
  void withNightRunIdLaesstDasOriginalUnberuehrt() {
    OHNE_LAUF.withNightRunId(4711L);

    assertThat(OHNE_LAUF.nightRunId()).isNull();
  }
}
