package org.mwolff.manban.card.web;

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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/** Registry-Verhalten (Emitter-Verwaltung, Publish, Heartbeat, Cleanup) mit Mock-Emittern. */
class ProjectIdeaEventRegistryTest {

  private static final long PROJECT = 1L;

  @Test
  void subscribe_returnsRealEmitterAndCountsIt() {
    var registry = new ProjectIdeaEventRegistry();

    SseEmitter emitter = registry.subscribe(PROJECT);

    assertThat(emitter).isNotNull();
    assertThat(registry.subscriberCount(PROJECT)).isEqualTo(1);
  }

  @Test
  void publish_sendsEventToSubscriber() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    var registry = spy(new ProjectIdeaEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(PROJECT);

    registry.publish(PROJECT);

    verify(emitter).send(any(SseEventBuilder.class));
  }

  @Test
  void publish_isNoOp_whenNoSubscribers() {
    var registry = new ProjectIdeaEventRegistry();

    assertThatNoException().isThrownBy(() -> registry.publish(PROJECT));
    assertThat(registry.subscriberCount(PROJECT)).isZero();
  }

  @Test
  void publish_removesEmitter_whenSendFails() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    doThrow(new IOException("client weg")).when(emitter).send(any(SseEventBuilder.class));
    var registry = spy(new ProjectIdeaEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(PROJECT);

    registry.publish(PROJECT);

    assertThat(registry.subscriberCount(PROJECT)).isZero();
  }

  @Test
  void heartbeat_pingsSubscribers() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    var registry = spy(new ProjectIdeaEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(PROJECT);

    registry.heartbeat();

    verify(emitter).send(any(SseEventBuilder.class));
  }

  @Test
  void heartbeat_isNoOp_whenNoSubscribers() {
    var registry = new ProjectIdeaEventRegistry();

    assertThatNoException().isThrownBy(registry::heartbeat);
  }

  @Test
  void completionCallback_removesEmitter() {
    SseEmitter emitter = mock(SseEmitter.class);
    var registry = spy(new ProjectIdeaEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(PROJECT);

    onCompletion(emitter).run();

    assertThat(registry.subscriberCount(PROJECT)).isZero();
  }

  @Test
  void timeoutCallback_removesEmitter() {
    SseEmitter emitter = mock(SseEmitter.class);
    var registry = spy(new ProjectIdeaEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(PROJECT);

    onTimeout(emitter).run();

    assertThat(registry.subscriberCount(PROJECT)).isZero();
  }

  @Test
  void errorCallback_removesEmitter() {
    SseEmitter emitter = mock(SseEmitter.class);
    var registry = spy(new ProjectIdeaEventRegistry());
    doReturn(emitter).when(registry).createEmitter();
    registry.subscribe(PROJECT);

    onError(emitter).accept(new IllegalStateException("boom"));

    assertThat(registry.subscriberCount(PROJECT)).isZero();
  }

  @Test
  void remove_keepsProject_whileOtherSubscribersRemain() {
    SseEmitter first = mock(SseEmitter.class);
    SseEmitter second = mock(SseEmitter.class);
    var registry = spy(new ProjectIdeaEventRegistry());
    doReturn(first).doReturn(second).when(registry).createEmitter();
    registry.subscribe(PROJECT);
    registry.subscribe(PROJECT);

    onCompletion(first).run();

    assertThat(registry.subscriberCount(PROJECT)).isEqualTo(1);
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
