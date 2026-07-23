package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.ProjectIdeaEventService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Der Controller prüft die Berechtigung und gibt den Registry-Emitter zurück. */
class ProjectIdeaEventControllerTest {

  private static final long USER = 4L;
  private static final long PROJECT = 8L;

  @Test
  void subscribe_checksAuthThenReturnsRegistryEmitter() {
    var service = mock(ProjectIdeaEventService.class);
    var registry = mock(ProjectIdeaEventRegistry.class);
    var emitter = new SseEmitter();
    when(registry.subscribe(PROJECT)).thenReturn(emitter);
    var controller = new ProjectIdeaEventController(service, registry);

    SseEmitter result = controller.subscribe(USER, PROJECT);

    verify(service).requireSubscribable(USER, PROJECT);
    assertThat(result).isSameAs(emitter);
  }
}
