package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.RetainedByKind;
import org.mwolff.manban.nightrun.domain.NightRunKind;

/** Die Aufbewahrungsgrenze gibt es je Gattung nur bei vollem Ringpuffer (Issue #2002, E3). */
class AufbewahrungsgrenzeTest {

  /** Nachtläufe fassen 3, Sitzungen 2 — so unterscheiden sich die Grenzen je Gattung. */
  private static final NightRunProperties PROPERTIES =
      new NightRunProperties(3, null, 2, null, null);

  private static final Instant FRUEH = Instant.parse("2026-09-01T21:00:00Z");
  private static final Instant SPAET = Instant.parse("2026-09-10T21:00:00Z");

  private final Aufbewahrungsgrenze grenze = new Aufbewahrungsgrenze(PROPERTIES);

  @Test
  void vollerPufferDerNachtlaeufe_setztDieGrenzeAnSeinenAeltestenEintrag() {
    assertThat(grenze.jeGattung(new RetainedByKind(NightRunKind.NIGHT, 3L, FRUEH))).contains(FRUEH);
  }

  @Test
  void nichtVollerPufferDerNachtlaeufe_hatKeineGrenze() {
    assertThat(grenze.jeGattung(new RetainedByKind(NightRunKind.NIGHT, 2L, FRUEH))).isEmpty();
  }

  @Test
  void vollerPufferDerSitzungen_mitSeinerEigenenGroesse() {
    assertThat(grenze.jeGattung(new RetainedByKind(NightRunKind.INTERACTIVE, 2L, FRUEH)))
        .contains(FRUEH);
    assertThat(grenze.jeGattung(new RetainedByKind(NightRunKind.INTERACTIVE, 1L, FRUEH))).isEmpty();
  }

  @Test
  void einGefuellterPufferOhneZeitpunkt_hatKeineGrenze() {
    assertThat(grenze.jeGattung(new RetainedByKind(NightRunKind.NIGHT, 3L, null))).isEmpty();
  }

  @Test
  void ohneVollenPuffer_gibtEsKeineGrenze() {
    assertThat(
            grenze.gesamt(
                List.of(
                    new RetainedByKind(NightRunKind.NIGHT, 2L, FRUEH),
                    new RetainedByKind(NightRunKind.INTERACTIVE, 1L, SPAET))))
        .isEmpty();
  }

  @Test
  void gesamt_istDerSpaetesteAeltesteEintragUnterDenVollenGattungen() {
    assertThat(
            grenze.gesamt(
                List.of(
                    new RetainedByKind(NightRunKind.NIGHT, 3L, FRUEH),
                    new RetainedByKind(NightRunKind.INTERACTIVE, 2L, SPAET))))
        .contains(SPAET);
  }

  /** Eine nicht volle Gattung mit älteren Einträgen verschiebt die Grenze nicht (H5). */
  @Test
  void gesamt_eineNichtVolleGattungZaehltNicht() {
    assertThat(
            grenze.gesamt(
                List.of(
                    new RetainedByKind(NightRunKind.NIGHT, 3L, FRUEH),
                    new RetainedByKind(NightRunKind.INTERACTIVE, 1L, SPAET))))
        .contains(FRUEH);
  }
}
