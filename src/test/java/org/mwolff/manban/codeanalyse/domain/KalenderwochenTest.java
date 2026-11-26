package org.mwolff.manban.codeanalyse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Kalenderwochen in UTC für die Platte „Codeanalyse“ (Issue #2273, Plan #2272 E6). */
class KalenderwochenTest {

  /** Ein Mittwoch. */
  private static final Instant JETZT = Instant.parse("2026-11-25T12:00:00Z");

  @Test
  void wochenbeginn_istDerMontagUmMitternachtUtc() {
    assertThat(Kalenderwochen.wochenbeginn(JETZT)).isEqualTo(Instant.parse("2026-11-23T00:00:00Z"));
    assertThat(Kalenderwochen.wochenbeginn(Instant.parse("2026-11-23T00:00:00Z")))
        .isEqualTo(Instant.parse("2026-11-23T00:00:00Z"));
    assertThat(Kalenderwochen.wochenbeginn(Instant.parse("2026-11-29T23:59:59Z")))
        .isEqualTo(Instant.parse("2026-11-23T00:00:00Z"));
  }

  @Test
  void sonntagSpaetUndMontagFrueh_fallenInVerschiedeneWochen() {
    OptionalInt sonntag = Kalenderwochen.index(Instant.parse("2026-11-22T23:59:59Z"), JETZT);
    OptionalInt montag = Kalenderwochen.index(Instant.parse("2026-11-23T00:00:00Z"), JETZT);

    assertThat(sonntag).isEqualTo(OptionalInt.of(Kalenderwochen.ANZAHL - 2));
    assertThat(montag).isEqualTo(OptionalInt.of(Kalenderwochen.ANZAHL - 1));
  }

  @Test
  void jahreswechsel_liegtInDerWocheSeinesMontags() {
    // Donnerstag 2026-01-01 gehört zur Woche ab Montag 2025-12-29.
    Instant jetzt = Instant.parse("2026-01-02T08:00:00Z");

    assertThat(Kalenderwochen.index(Instant.parse("2025-12-29T00:00:00Z"), jetzt))
        .isEqualTo(OptionalInt.of(Kalenderwochen.ANZAHL - 1));
    assertThat(Kalenderwochen.index(Instant.parse("2025-12-31T23:59:59Z"), jetzt))
        .isEqualTo(OptionalInt.of(Kalenderwochen.ANZAHL - 1));
    assertThat(Kalenderwochen.index(Instant.parse("2025-12-28T23:59:59Z"), jetzt))
        .isEqualTo(OptionalInt.of(Kalenderwochen.ANZAHL - 2));
    assertThat(Kalenderwochen.beginn(Kalenderwochen.ANZAHL - 1, jetzt))
        .isEqualTo(Instant.parse("2025-12-29T00:00:00Z"));
  }

  @Test
  void zeitpunktVorDemFenster_liefertKeinenIndex() {
    Instant fensterbeginn = Kalenderwochen.fensterbeginn(JETZT);

    assertThat(Kalenderwochen.index(fensterbeginn.minusSeconds(1), JETZT)).isEmpty();
    assertThat(Kalenderwochen.index(fensterbeginn, JETZT)).isEqualTo(OptionalInt.of(0));
  }

  @Test
  void zeitpunktNachJetzt_liefertKeinenIndex() {
    assertThat(Kalenderwochen.index(JETZT.plusSeconds(1), JETZT)).isEmpty();
    assertThat(Kalenderwochen.index(JETZT, JETZT))
        .isEqualTo(OptionalInt.of(Kalenderwochen.ANZAHL - 1));
  }

  @Test
  void fenster_hatZwoelfWochen_undEndetMitDerLaufenden() {
    assertThat(Kalenderwochen.ANZAHL).isEqualTo(12);
    assertThat(Kalenderwochen.fensterbeginn(JETZT))
        .isEqualTo(Instant.parse("2026-09-07T00:00:00Z"));
    assertThat(Kalenderwochen.beginn(0, JETZT)).isEqualTo(Kalenderwochen.fensterbeginn(JETZT));
    assertThat(Kalenderwochen.beginn(Kalenderwochen.ANZAHL - 1, JETZT))
        .isEqualTo(Kalenderwochen.wochenbeginn(JETZT));
    IntStream.range(0, Kalenderwochen.ANZAHL)
        .forEach(
            i ->
                assertThat(Kalenderwochen.index(Kalenderwochen.beginn(i, JETZT), JETZT))
                    .isEqualTo(OptionalInt.of(i)));
  }
}
