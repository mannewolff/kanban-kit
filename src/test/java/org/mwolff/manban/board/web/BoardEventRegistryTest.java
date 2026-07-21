package org.mwolff.manban.board.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardChangedEvent;
import org.mwolff.manban.board.application.BoardChangedEvent.ChangeType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/** Registry-Verhalten (Emitter-Verwaltung, Publish, Heartbeat, Cleanup) mit Mock-Emittern. */
class BoardEventRegistryTest {

  private static final long BOARD = 1L;
  private static final BoardChangedEvent EVENT = new BoardChangedEvent(BOARD, ChangeType.MOVED, 9L);

  @Test
  void subscribe_returnsRealEmitterAndCountsIt() {
    var registry = new BoardEventRegistry();

    SseEmitter emitter = registry.subscribe(BOARD);

    assertThat(emitter).isNotNull();
    assertThat(registry.subscriberCount(BOARD)).isEqualTo(1);
  }

  @Test
  void publish_sendsEventToSubscriber() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    var registry = spy(new BoardEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(BOARD);

    registry.publish(BOARD, EVENT);

    verify(emitter).send(any(SseEventBuilder.class));
  }

  @Test
  void publish_isNoOp_whenNoSubscribers() {
    var registry = new BoardEventRegistry();

    assertThatNoException().isThrownBy(() -> registry.publish(BOARD, EVENT));
    assertThat(registry.subscriberCount(BOARD)).isZero();
  }

  @Test
  void publish_removesEmitter_whenSendFails() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    doThrow(new IOException("client weg")).when(emitter).send(any(SseEventBuilder.class));
    var registry = spy(new BoardEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(BOARD);

    registry.publish(BOARD, EVENT);

    assertThat(registry.subscriberCount(BOARD)).isZero();
  }

  @Test
  void heartbeat_pingsSubscribers() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    var registry = spy(new BoardEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(BOARD);

    registry.heartbeat();

    verify(emitter).send(any(SseEventBuilder.class));
  }

  @Test
  void heartbeat_isNoOp_whenNoSubscribers() {
    var registry = new BoardEventRegistry();

    assertThatNoException().isThrownBy(registry::heartbeat);
  }

  @Test
  void completionCallback_removesEmitter() {
    SseEmitter emitter = mock(SseEmitter.class);
    var registry = spy(new BoardEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(BOARD);

    onCompletion(emitter).run();

    assertThat(registry.subscriberCount(BOARD)).isZero();
  }

  @Test
  void timeoutCallback_removesEmitter() {
    SseEmitter emitter = mock(SseEmitter.class);
    var registry = spy(new BoardEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(BOARD);

    onTimeout(emitter).run();

    assertThat(registry.subscriberCount(BOARD)).isZero();
  }

  @Test
  void errorCallback_removesEmitter() {
    SseEmitter emitter = mock(SseEmitter.class);
    var registry = spy(new BoardEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(BOARD);

    onError(emitter).accept(new IllegalStateException("boom"));

    assertThat(registry.subscriberCount(BOARD)).isZero();
  }

  @Test
  void remove_keepsBoard_whileOtherSubscribersRemain() {
    SseEmitter first = mock(SseEmitter.class);
    SseEmitter second = mock(SseEmitter.class);
    var registry = spy(new BoardEventRegistry());
    doReturn(first).doReturn(second).when(registry).createEmitter();
    registry.subscribe(BOARD);
    registry.subscribe(BOARD);

    onCompletion(first).run();

    assertThat(registry.subscriberCount(BOARD)).isEqualTo(1);
  }

  private static Runnable onCompletion(SseEmitter emitter) {
    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(emitter, times(1)).onCompletion(captor.capture());
    return captor.getValue();
  }

  private static Runnable onTimeout(SseEmitter emitter) {
    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(emitter, times(1)).onTimeout(captor.capture());
    return captor.getValue();
  }

  // ArgumentCaptor.forClass(Consumer.class) liefert roh; der Cast auf Consumer<Throwable> ist
  // wegen Type-Erasure unvermeidbar und hier sicher (der Captor fängt genau diesen Callback-Typ).
  @SuppressWarnings("unchecked")
  private static Consumer<Throwable> onError(SseEmitter emitter) {
    ArgumentCaptor<Consumer<Throwable>> captor = ArgumentCaptor.forClass(Consumer.class);
    verify(emitter, times(1)).onError(captor.capture());
    return captor.getValue();
  }
}
