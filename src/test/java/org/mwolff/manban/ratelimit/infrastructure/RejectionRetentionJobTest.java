package org.mwolff.manban.ratelimit.infrastructure;

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
import org.mwolff.manban.ratelimit.infrastructure.persistence.OverloadRejectionTable;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Aufräumjob der Abweisungs-Aggregate: Frist 90 Tage, am Aufräum-Schalter (Issue #1000, E23). */
class RejectionRetentionJobTest {

  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

  private final OverloadRejectionTable table = mock(OverloadRejectionTable.class);

  @Test
  void run_deletesEverythingOlderThanNinetyDays() {
    when(table.deleteOlderThan(NOW.minus(Duration.ofDays(90)))).thenReturn(3);

    new RejectionRetentionJob(table, Clock.fixed(NOW, ZoneOffset.UTC)).run();

    verify(table).deleteOlderThan(NOW.minus(Duration.ofDays(90)));
  }

  @Test
  void run_withNothingToDelete_stillAsksTheTable_andStaysQuiet() {
    List<ILoggingEvent> log = watchLog();

    new RejectionRetentionJob(table, Clock.fixed(NOW, ZoneOffset.UTC)).run();

    verify(table).deleteOlderThan(NOW.minus(Duration.ofDays(90)));
    assertThat(log).isEmpty();
  }

  @Test
  void run_reportsEvenOneDeletedRow() {
    List<ILoggingEvent> log = watchLog();
    when(table.deleteOlderThan(NOW.minus(Duration.ofDays(90)))).thenReturn(1);

    new RejectionRetentionJob(table, Clock.fixed(NOW, ZoneOffset.UTC)).run();

    assertThat(log)
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .asString()
        .contains("1 Stunden-Aggregate");
  }

  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private List<ILoggingEvent> watchLog() {
    appender.start();
    ((Logger) LoggerFactory.getLogger(RejectionRetentionJob.class)).addAppender(appender);
    return appender.list;
  }

  /** Der Logger ist global — ohne Abmelden sammelte der Appender über Testklassen hinweg weiter. */
  @AfterEach
  void stopWatchingLog() {
    ((Logger) LoggerFactory.getLogger(RejectionRetentionJob.class)).detachAppender(appender);
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(OverloadRejectionTable.class, () -> table)
          .withBean(Clock.class, Clock::systemUTC)
          .withUserConfiguration(RejectionRetentionJob.class);

  @Test
  void cleanupSwitchOff_removesTheJob_soNothingIsDeleted() {
    runner
        .withPropertyValues("manban.cleanup.enabled=false")
        .run(
            context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(RejectionRetentionJob.class));
  }

  @Test
  void cleanupSwitchOn_orMissing_keepsTheJob() {
    runner.run(
        context -> assertThat(context).hasNotFailed().hasSingleBean(RejectionRetentionJob.class));
    runner
        .withPropertyValues("manban.cleanup.enabled=true")
        .run(
            context ->
                assertThat(context).hasNotFailed().hasSingleBean(RejectionRetentionJob.class));
  }
}
