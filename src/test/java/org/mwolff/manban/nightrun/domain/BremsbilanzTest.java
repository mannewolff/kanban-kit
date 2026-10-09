package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Die geschätzte gesparte Zeit eines festgefahrenen Pakets (Issue #1549, Plan #1547 E6/E7) und die
 * Bilanz über viele Pakete (Issue #1550, E4/E5).
 */
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
    return angaben(sessionLimitMs, "s-1");
  }

  private static NightRunStuck angaben(@Nullable Long sessionLimitMs, @Nullable String sessionId) {
    return new NightRunStuck("mvn verify", "OpenApiIT rot", 3, sessionLimitMs, sessionId);
  }

  /** Ein festgefahrenes Paket des Laufs {@code nightRunId}, Zeitgrenze eine Stunde. */
  private static NightRunItem bremsung(
      @Nullable Long nightRunId, @Nullable String sessionId, long durationMs) {
    return imLauf(
        nightRunId,
        paket(NightRunErrorClass.STUCK, durationMs, angaben(ZEITGRENZE, sessionId)),
        null);
  }

  private static NightRunItem imLauf(
      @Nullable Long nightRunId, NightRunItem item, @Nullable String excerpt) {
    return new NightRunItem(
        item.id(),
        nightRunId,
        item.projectId(),
        item.startedAt(),
        item.mode(),
        item.kind(),
        item.cardNumber(),
        item.title(),
        item.state(),
        item.errorClass(),
        item.durationMs(),
        item.commitHash(),
        excerpt,
        item.usage(),
        item.stuck(),
        item.stages());
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

  @Test
  void ohnePaketeIstDieBilanzLeer() {
    assertThat(Bremsbilanz.of(List.of())).isEqualTo(new Bremsbilanz(0L, 0L, 0L));
  }

  @Test
  void zweiPaketeDerselbenSitzungImSelbenLaufZaehlenDenKleinstenWertEinmal() {
    Bremsbilanz bilanz =
        Bremsbilanz.of(List.of(bremsung(7L, "s-1", 600_000L), bremsung(7L, "s-1", 1_200_000L)));

    assertThat(bilanz.brakeCount()).isEqualTo(2L);
    assertThat(bilanz.withoutTimeCount()).isZero();
    assertThat(bilanz.savedMs()).isEqualTo(2_400_000L);
  }

  @Test
  void dieselbeSitzungskennungInVerschiedenenLaeufenZaehltZweimal() {
    Bremsbilanz bilanz =
        Bremsbilanz.of(List.of(bremsung(7L, "s-1", 600_000L), bremsung(8L, "s-1", 1_200_000L)));

    assertThat(bilanz.brakeCount()).isEqualTo(2L);
    assertThat(bilanz.savedMs()).isEqualTo(3_000_000L + 2_400_000L);
  }

  @Test
  void verwaistePaketeMitGleicherSitzungskennungZaehlenJedesFuerSich() {
    Bremsbilanz bilanz =
        Bremsbilanz.of(List.of(bremsung(null, "s-1", 600_000L), bremsung(null, "s-1", 1_200_000L)));

    assertThat(bilanz.brakeCount()).isEqualTo(2L);
    assertThat(bilanz.savedMs()).isEqualTo(3_000_000L + 2_400_000L);
  }

  @Test
  void paketeOhneSitzungskennungZaehlenJedesFuerSich() {
    Bremsbilanz bilanz =
        Bremsbilanz.of(List.of(bremsung(7L, null, 600_000L), bremsung(7L, null, 1_200_000L)));

    assertThat(bilanz.brakeCount()).isEqualTo(2L);
    assertThat(bilanz.savedMs()).isEqualTo(3_000_000L + 2_400_000L);
  }

  @Test
  void paketeOhneStuckZaehlenNicht_auchRotMitAuszugFestgefahren() {
    NightRunItem uebergang =
        imLauf(
            7L,
            paket(NightRunErrorClass.CHECKS_RED, 600_000L, angaben(ZEITGRENZE)),
            "festgefahren: mvn verify, OpenApiIT rot");
    NightRunItem gruen = imLauf(7L, paket(null, 600_000L, null), null);

    assertThat(Bremsbilanz.of(List.of(uebergang, gruen))).isEqualTo(new Bremsbilanz(0L, 0L, 0L));
  }

  @Test
  void eineBremsungOhneZeitwertZaehltOhneBeitragZurSumme() {
    NightRunItem ohneZeitgrenze =
        imLauf(7L, paket(NightRunErrorClass.STUCK, 600_000L, angaben(null, "s-2")), null);
    NightRunItem ohneLaufzeit =
        imLauf(7L, paket(NightRunErrorClass.STUCK, null, angaben(ZEITGRENZE, "s-3")), null);
    NightRunItem ohneAngaben = imLauf(7L, paket(NightRunErrorClass.STUCK, 600_000L, null), null);

    Bremsbilanz bilanz =
        Bremsbilanz.of(
            List.of(ohneZeitgrenze, ohneLaufzeit, ohneAngaben, bremsung(7L, "s-1", 600_000L)));

    assertThat(bilanz.brakeCount()).isEqualTo(4L);
    assertThat(bilanz.withoutTimeCount()).isEqualTo(3L);
    assertThat(bilanz.savedMs()).isEqualTo(3_000_000L);
  }

  @Test
  void eineSitzungAusPaketMitUndOhneZeitwertZaehltDenVorhandenenWert() {
    NightRunItem ohneLaufzeit =
        imLauf(7L, paket(NightRunErrorClass.STUCK, null, angaben(ZEITGRENZE, "s-1")), null);

    Bremsbilanz bilanz = Bremsbilanz.of(List.of(ohneLaufzeit, bremsung(7L, "s-1", 600_000L)));

    assertThat(bilanz).isEqualTo(new Bremsbilanz(2L, 1L, 3_000_000L));
  }

  @Test
  void eineLaufzeitUeberDerZeitgrenzeTraegtNullBei() {
    Bremsbilanz bilanz = Bremsbilanz.of(List.of(bremsung(7L, "s-1", ZEITGRENZE + 5)));

    assertThat(bilanz).isEqualTo(new Bremsbilanz(1L, 0L, 0L));
  }
}
