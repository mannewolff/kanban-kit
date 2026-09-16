package org.mwolff.manban.outbox.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.outbox.application.OutboxDispatchService;
import org.mwolff.manban.outbox.application.OutboxProperties;
import org.mwolff.manban.outbox.application.OutboxRetentionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Belegt, dass die beiden Outbox-Beans an den Schaltern haengen, die die Anleitung nennt (Issue
 * #906).
 *
 * <p>Die beiden haengen an <b>verschiedenen</b> Schaltern, und das ist keine Nachlaessigkeit: Der
 * Zustell-Worker folgt {@code manban.outbox.enabled}, der Aufraeum-Job dagegen {@code
 * manban.cleanup.enabled} — er raeumt auf, er stellt nichts zu. Wer nur einen der beiden Schalter
 * umlegt, legt also nicht beide Beans still.
 *
 * <p>Die Beans sind package-private, deshalb liegt der Test in ihrem Paket.
 */
class OutboxSchalterContextTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(OutboxDispatchService.class, () -> mock(OutboxDispatchService.class))
          .withBean(OutboxRetentionService.class, () -> mock(OutboxRetentionService.class))
          .withBean(OutboxProperties.class, () -> mock(OutboxProperties.class))
          .withBean(Clock.class, Clock::systemUTC)
          .withUserConfiguration(OutboxWorker.class, OutboxRetentionJob.class);

  @Test
  void aufraeumSchalterAus_verschwindetDerOutboxAufraeumJob() {
    runner
        // Bewusst der Aufraeum-Schalter, nicht der Outbox-Schalter: OutboxRetentionJob haengt an
        // manban.cleanup.enabled, weil er aufraeumt und nicht zustellt.
        .withPropertyValues("manban.cleanup.enabled=false")
        .run(
            context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(OutboxRetentionJob.class));
  }

  @Test
  void outboxSchalterAus_verschwindetDerZustellWorker() {
    runner
        .withPropertyValues("manban.outbox.enabled=false")
        .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(OutboxWorker.class));
  }

  @Test
  void aufraeumSchalterAus_laesstDenZustellWorkerStehen() {
    runner
        .withPropertyValues("manban.cleanup.enabled=false")
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(OutboxWorker.class));
  }

  @Test
  void outboxSchalterAus_laesstDenAufraeumJobStehen() {
    runner
        .withPropertyValues("manban.outbox.enabled=false")
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(OutboxRetentionJob.class));
  }

  @Test
  void eingeschaltet_stehenBeide() {
    runner
        .withPropertyValues("manban.outbox.enabled=true", "manban.cleanup.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(OutboxWorker.class)
                    .hasSingleBean(OutboxRetentionJob.class));
  }

  @Test
  void ohneAngabe_greiftDieVorgabeUndBeideStehen() {
    runner.run(
        context ->
            assertThat(context)
                .hasNotFailed()
                .hasSingleBean(OutboxWorker.class)
                .hasSingleBean(OutboxRetentionJob.class));
  }
}
