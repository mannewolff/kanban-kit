package org.mwolff.manban.kanbancompat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.mwolff.manban.kanbancompat.application.IdempotencyRecordStore;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Aufräumjob der Idempotenz-Schlüssel und sein Beitrag zum Startprotokoll (Issue #1001, Plan #995
 * E9): Frist 24 Stunden, am Aufräum-Schalter.
 */
class IdempotencyRetentionJobTest {

  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

  private final IdempotencyRecordStore store = mock(IdempotencyRecordStore.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private List<ILoggingEvent> watchLog() {
    appender.start();
    ((Logger) LoggerFactory.getLogger(IdempotencyRetentionJob.class)).addAppender(appender);
    return appender.list;
  }

  /** Der Logger ist global — ohne Abmelden sammelte der Appender über Testklassen hinweg weiter. */
  @AfterEach
  void stopWatchingLog() {
    ((Logger) LoggerFactory.getLogger(IdempotencyRetentionJob.class)).detachAppender(appender);
  }

  private void runJob() {
    new IdempotencyRetentionJob(store, Clock.fixed(NOW, ZoneOffset.UTC)).run();
  }

  @Test
  void run_deletesEverythingOlderThanTwentyFourHours_andStaysQuietWhenNothingWent() {
    List<ILoggingEvent> log = watchLog();

    runJob();

    verify(store).deleteOlderThan(NOW.minus(Duration.ofHours(24)));
    assertThat(log).isEmpty();
  }

  @Test
  void run_reportsEvenOneDeletedRecord() {
    List<ILoggingEvent> log = watchLog();
    when(store.deleteOlderThan(NOW.minus(Duration.ofHours(24)))).thenReturn(1);

    runJob();

    assertThat(log)
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .asString()
        .contains("1 Idempotenz-Schlüssel");
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(IdempotencyRecordStore.class, () -> store)
          .withBean(Clock.class, Clock::systemUTC)
          .withUserConfiguration(IdempotencyRetentionJob.class);

  @Test
  void cleanupSwitchOff_removesTheJob_soNothingIsDeleted() {
    runner
        .withPropertyValues("manban.cleanup.enabled=false")
        .run(
            context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(IdempotencyRetentionJob.class));
  }

  @Test
  void cleanupSwitchMissing_keepsTheJob() {
    runner.run(
        context -> assertThat(context).hasNotFailed().hasSingleBean(IdempotencyRetentionJob.class));
  }

  // Mockito kann generische Typen nicht ohne ungeprüfte Umwandlung mocken.
  @SuppressWarnings("unchecked")
  private static ObjectProvider<IdempotencyRetentionJob> liefert(IdempotencyRetentionJob job) {
    ObjectProvider<IdempotencyRetentionJob> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(job);
    return provider;
  }

  @Test
  void startupReport_namesTheJob_withSwitchAndRetention() {
    assertThat(
            new KanbancompatAutomationStatusContributor(
                    liefert(mock(IdempotencyRetentionJob.class)))
                .statuses())
        .containsExactly(
            new AutomationStatus(
                "Aufräumung der Idempotenz-Schlüssel",
                true,
                "MANBAN_CLEANUP_ENABLED",
                "Aufbewahrung 24 Stunden"));
  }

  @Test
  void startupReport_withoutJob_showsItSwitchedOff() {
    assertThat(new KanbancompatAutomationStatusContributor(liefert(null)).statuses())
        .singleElement()
        .extracting(AutomationStatus::eingeschaltet)
        .isEqualTo(false);
  }
}
