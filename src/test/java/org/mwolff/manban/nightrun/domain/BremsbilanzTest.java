package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Die geschätzte gesparte Zeit eines festgefahrenen Pakets (Issue #1549, Plan #1547 E6/E7). */
class BremsbilanzTest {

  private static final long ZEITGRENZE = 3_600_000L;

  private static NightRunItem paket(
      @Nullable NightRunErrorClass errorClass,
      @Nullable Long durationMs,
      @Nullable NightRunStuck stuck) {
    return new NightRunItem(
        null,
        null,
        42L,
        Instant.parse("2026-10-09T22:00:00Z"),
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        1549,
        "Einlieferung",
        NightRunState.RED,
        errorClass,
        durationMs,
        null,
        null,
        null,
        stuck,
        List.of());
  }

  private static NightRunStuck angaben(@Nullable Long sessionLimitMs) {
    return new NightRunStuck("mvn verify", "OpenApiIT rot", 3, sessionLimitMs, "s-1");
  }

  @Test
  void mitAllenAngabenIstDieErsparnisZeitgrenzeMinusLaufzeit() {
    assertThat(Bremsbilanz.gespart(paket(NightRunErrorClass.STUCK, 900_000L, angaben(ZEITGRENZE))))
        .isEqualTo(2_700_000L);
  }

  @Test
  void ohneZeitgrenzeGibtEsKeineErsparnis() {
    assertThat(Bremsbilanz.gespart(paket(NightRunErrorClass.STUCK, 900_000L, angaben(null))))
        .isNull();
  }

  @Test
  void ohneAngabenGibtEsKeineErsparnis() {
    assertThat(Bremsbilanz.gespart(paket(NightRunErrorClass.STUCK, 900_000L, null))).isNull();
  }

  @Test
  void ohneLaufzeitGibtEsKeineErsparnis() {
    assertThat(Bremsbilanz.gespart(paket(NightRunErrorClass.STUCK, null, angaben(ZEITGRENZE))))
        .isNull();
  }

  @Test
  void eineLaufzeitUeberDerZeitgrenzeSpartNichts() {
    assertThat(
            Bremsbilanz.gespart(
                paket(NightRunErrorClass.STUCK, ZEITGRENZE + 1, angaben(ZEITGRENZE))))
        .isZero();
  }

  @Test
  void eineLaufzeitGleichDerZeitgrenzeSpartNichts() {
    assertThat(
            Bremsbilanz.gespart(paket(NightRunErrorClass.STUCK, ZEITGRENZE, angaben(ZEITGRENZE))))
        .isZero();
  }

  @Test
  void einPaketOhneStuckZaehltNicht_auchMitAngaben() {
    assertThat(
            Bremsbilanz.gespart(
                paket(NightRunErrorClass.CHECKS_RED, 900_000L, angaben(ZEITGRENZE))))
        .isNull();
    assertThat(Bremsbilanz.gespart(paket(null, 900_000L, angaben(ZEITGRENZE)))).isNull();
  }
}
