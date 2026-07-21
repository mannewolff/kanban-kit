package org.mwolff.manban.board.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardEventService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Der Controller prüft die Berechtigung und gibt den Registry-Emitter zurück. */
class BoardEventControllerTest {

  private static final long USER = 4L;
  private static final long BOARD = 8L;

  @Test
  void subscribe_checksAuthThenReturnsRegistryEmitter() {
    var service = mock(BoardEventService.class);
    var registry = mock(BoardEventRegistry.class);
    var emitter = new SseEmitter();
    when(registry.subscribe(BOARD)).thenReturn(emitter);
    var controller = new BoardEventController(service, registry);

    SseEmitter result = controller.subscribe(USER, BOARD);

    verify(service).requireSubscribable(USER, BOARD);
    assertThat(result).isSameAs(emitter);
  }
}
