package org.mwolff.manban.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.outbox.domain.OutboxEntry;
import org.mwolff.manban.outbox.domain.OutboxStatus;
import org.slf4j.LoggerFactory;

/** Abarbeitung eines einzelnen Outbox-Eintrags (Issue #501). */
class OutboxEntryDispatcherTest {

  private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");
  private static final String TYPE = "mail.verification";

  private final OutboxRepository repository = mock(OutboxRepository.class);

  /**
   * Fängt die Log-Ausgabe des Dispatchers: Die Wahl des Levels (WARN bei Restversuchen, ERROR beim
   * endgültigen Scheitern) ist beabsichtigtes Verhalten — ERROR ist der Alarm, auf den ein
   * Betreiber reagieren muss, weil der Seiteneffekt sonst nie ausgeführt wurde.
   */
  private final ListAppender<ILoggingEvent> logWatcher = new ListAppender<>();

  @BeforeEach
  void watchDispatcherLog() {
    logWatcher.start();
    ((Logger) LoggerFactory.getLogger(OutboxEntryDispatcher.class)).addAppender(logWatcher);
  }

  /** Der Logger ist global — ohne Abmelden sammelte der Appender über Testklassen hinweg weiter. */
  @AfterEach
  void unwatchDispatcherLog() {
    ((Logger) LoggerFactory.getLogger(OutboxEntryDispatcher.class)).detachAppender(logWatcher);
  }

  /**
   * Fake statt Mock: der Handler ist ein reiner Empfänger, sein Verhalten ist der Testgegenstand.
   */
  private static final class RecordingHandler implements OutboxHandler {

    private final String type;
    private final List<String> received = new ArrayList<>();
    private RuntimeException failure;

    RecordingHandler(String eventType) {
      this.type = eventType;
    }

    @Override
    public String eventType() {
      return type;
    }

    @Override
    public void handle(String payload) {
      received.add(payload);
      if (failure != null) {
        throw failure;
      }
    }
  }

  private static OutboxEntry pending(int attempts) {
    return new OutboxEntry(
        7L, TYPE, "verify:42", "userId=42", OutboxStatus.PENDING, attempts, NOW, NOW, null, null);
  }

  private OutboxEntryDispatcher dispatcher(int maxAttempts, OutboxHandler... handlers) {
    return new OutboxEntryDispatcher(
        List.of(handlers),
        repository,
        new OutboxProperties(
            true, 5000L, 50, maxAttempts, Duration.ofSeconds(10), Duration.ofHours(1), 7),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void dispatch_passesThePayloadToTheHandlerOfThatEventType() {
    // Given
    RecordingHandler handler = new RecordingHandler(TYPE);
    when(repository.claimDue(7L, NOW)).thenReturn(Optional.of(pending(0)));

    // When
    dispatcher(8, handler, new RecordingHandler("other")).dispatch(7L);

    // Then
    assertThat(handler.received).containsExactly("userId=42");
  }

  @Test
  void dispatch_marksTheEntryDoneAfterSuccessfulHandler() {
    // Given
    when(repository.claimDue(7L, NOW)).thenReturn(Optional.of(pending(0)));

    // When
    boolean dispatched = dispatcher(8, new RecordingHandler(TYPE)).dispatch(7L);

    // Then
    assertThat(dispatched).isTrue();
    verify(repository).update(pending(0).completed(NOW));
  }

  @Test
  void dispatch_doesNothingWhenTheEntryCouldNotBeClaimed() {
    // Given
    RecordingHandler handler = new RecordingHandler(TYPE);
    when(repository.claimDue(7L, NOW)).thenReturn(Optional.empty());

    // When
    boolean dispatched = dispatcher(8, handler).dispatch(7L);

    // Then
    assertThat(dispatched).isFalse();
    assertThat(handler.received).isEmpty();
    verify(repository, never()).update(any());
  }

  @Test
  void dispatch_schedulesRetryWhenTheHandlerFails() {
    // Given
    RecordingHandler handler = new RecordingHandler(TYPE);
    handler.failure = new IllegalStateException("SMTP weg");
    when(repository.claimDue(7L, NOW)).thenReturn(Optional.of(pending(0)));

    // When
    boolean dispatched = dispatcher(8, handler).dispatch(7L);

    // Then
    assertThat(dispatched).isFalse();
    ArgumentCaptor<OutboxEntry> captured = ArgumentCaptor.forClass(OutboxEntry.class);
    verify(repository).update(captured.capture());
    assertThat(captured.getValue())
        .extracting(OutboxEntry::status, OutboxEntry::attempts, OutboxEntry::nextAttemptAt)
        .containsExactly(OutboxStatus.PENDING, 1, NOW.plusSeconds(10));
  }

  @Test
  void dispatch_recordsTheHandlerFailureMessage() {
    // Given
    RecordingHandler handler = new RecordingHandler(TYPE);
    handler.failure = new IllegalStateException("SMTP weg");
    when(repository.claimDue(7L, NOW)).thenReturn(Optional.of(pending(0)));

    // When
    dispatcher(8, handler).dispatch(7L);

    // Then
    ArgumentCaptor<OutboxEntry> captured = ArgumentCaptor.forClass(OutboxEntry.class);
    verify(repository).update(captured.capture());
    assertThat(captured.getValue().lastError()).contains("SMTP weg");
  }

  @Test
  void dispatch_givesUpOnceTheAttemptLimitIsReached() {
    // Given
    RecordingHandler handler = new RecordingHandler(TYPE);
    handler.failure = new IllegalStateException("endgültig weg");
    when(repository.claimDue(7L, NOW)).thenReturn(Optional.of(pending(2)));

    // When
    boolean dispatched = dispatcher(3, handler).dispatch(7L);

    // Then
    assertThat(dispatched).isFalse();
    ArgumentCaptor<OutboxEntry> captured = ArgumentCaptor.forClass(OutboxEntry.class);
    verify(repository).update(captured.capture());
    assertThat(captured.getValue().status()).isEqualTo(OutboxStatus.FAILED);
  }

  @Test
  void dispatch_logsFailureWithAttemptsLeftAsWarning() {
    // Given
    RecordingHandler handler = new RecordingHandler(TYPE);
    handler.failure = new IllegalStateException("SMTP weg");
    when(repository.claimDue(7L, NOW)).thenReturn(Optional.of(pending(0)));

    // When
    dispatcher(8, handler).dispatch(7L);

    // Then — mit Restversuchen ist der Fehlschlag Betriebsrauschen, kein Alarm.
    assertThat(logWatcher.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage()).contains("Versuch 1");
            });
  }

  @Test
  void dispatch_logsTheFinalFailureAsError() {
    // Given
    RecordingHandler handler = new RecordingHandler(TYPE);
    handler.failure = new IllegalStateException("endgültig weg");
    when(repository.claimDue(7L, NOW)).thenReturn(Optional.of(pending(2)));

    // When
    dispatcher(3, handler).dispatch(7L);

    // Then — der nie ausgeführte Seiteneffekt ist der Alarmfall, den ein Betreiber sehen muss.
    assertThat(logWatcher.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage()).contains("endgültig gescheitert");
            });
  }

  @Test
  void dispatch_treatsUnknownEventTypeAsFailedAttempt() {
    // Given
    when(repository.claimDue(7L, NOW)).thenReturn(Optional.of(pending(0)));

    // When
    boolean dispatched = dispatcher(8, new RecordingHandler("other")).dispatch(7L);

    // Then
    assertThat(dispatched).isFalse();
    ArgumentCaptor<OutboxEntry> captured = ArgumentCaptor.forClass(OutboxEntry.class);
    verify(repository).update(captured.capture());
    assertThat(captured.getValue())
        .extracting(OutboxEntry::status, OutboxEntry::lastError)
        .satisfies(
            values -> {
              assertThat(values.get(0)).isEqualTo(OutboxStatus.PENDING);
              assertThat((String) values.get(1)).contains(TYPE);
            });
  }

  @Test
  void construction_rejectsTwoHandlersForTheSameEventType() {
    // Given / When / Then
    assertThatThrownBy(() -> dispatcher(8, new RecordingHandler(TYPE), new RecordingHandler(TYPE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(TYPE);
    verifyNoInteractions(repository);
  }
}
