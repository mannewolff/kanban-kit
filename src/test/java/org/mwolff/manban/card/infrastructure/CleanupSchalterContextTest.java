package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CleanupProperties;
import org.mwolff.manban.card.application.DoneRetentionService;
import org.mwolff.manban.card.application.DoneRetentionSettingService;
import org.mwolff.manban.card.application.TrashRetentionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Belegt, dass {@code manban.cleanup.enabled} die beiden Aufraeum-Jobs wirklich stilllegt und nicht
 * nur in der Anwendung ankommt (Issue #906).
 *
 * <p>Dass der Schalter den Container erreicht, sichert der Abgleich aus Issue #905. Dass er dort
 * auch wirkt — die Beans also verschwinden —, sichert dieser Test. Beide zusammen loesen das
 * Versprechen der Anleitung ein; einer allein nicht.
 *
 * <p>Die Jobs sind package-private, deshalb liegt der Test in ihrem Paket.
 */
class CleanupSchalterContextTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(TrashRetentionService.class, () -> mock(TrashRetentionService.class))
          .withBean(DoneRetentionService.class, () -> mock(DoneRetentionService.class))
          .withBean(
              DoneRetentionSettingService.class, () -> mock(DoneRetentionSettingService.class))
          .withBean(CleanupProperties.class, () -> mock(CleanupProperties.class))
          .withBean(Clock.class, Clock::systemUTC)
          .withUserConfiguration(TrashRetentionJob.class, DoneRetentionJob.class);

  @Test
  void abgeschaltet_verschwindenBeideAufraeumJobs() {
    runner
        .withPropertyValues("manban.cleanup.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(TrashRetentionJob.class)
                    .doesNotHaveBean(DoneRetentionJob.class));
  }

  @Test
  void eingeschaltet_stehenBeideAufraeumJobs() {
    runner
        .withPropertyValues("manban.cleanup.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(TrashRetentionJob.class)
                    .hasSingleBean(DoneRetentionJob.class));
  }

  @Test
  void ohneAngabe_greiftDieVorgabeUndBeideStehen() {
    runner.run(
        context ->
            assertThat(context)
                .hasNotFailed()
                .hasSingleBean(TrashRetentionJob.class)
                .hasSingleBean(DoneRetentionJob.class));
  }

  /**
   * Die Leerwert-Falle: Ein leerer Wert ist nicht {@code "true"}, und {@code matchIfMissing} greift
   * nicht — die Eigenschaft ist ja gesetzt. Beide Jobs fallen weg, ohne dass etwas anschlaegt.
   *
   * <p>Genau das vermeidet die Default-Form {@code ${NAME:-true}} in der {@code
   * docker-compose.yml}. <b>Dieser Test schuetzt jene Form nicht</b> — das tut der Abgleich aus
   * Issue #905. Er haelt die Falle selbst fest und schlaegt zu, wenn jemand die Bedingung der Jobs
   * spaeter auf eine Semantik ohne {@code havingValue} umstellt.
   */
  @Test
  void leererWert_zaehltNichtAlsEingeschaltet() {
    runner
        .withPropertyValues("manban.cleanup.enabled=")
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(TrashRetentionJob.class)
                    .doesNotHaveBean(DoneRetentionJob.class));
  }
}
