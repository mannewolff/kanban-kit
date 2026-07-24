package org.mwolff.manban.card.web;

import org.mwolff.manban.card.application.ProjectIdeaEventService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE-Endpoint für Live-Updates des projektweiten Ideen-Pools. Ein Projektmitglied abonniert den
 * Stream; Server-seitige Änderungen werden über die {@link ProjectIdeaEventRegistry} an alle
 * Abonnenten des Projekts gepusht. Session-Auth erzwingt die Security-Filterkette ({@code
 * /api/**}); die Mitgliedschaft prüft der {@link ProjectIdeaEventService}.
 */
@RestController
class ProjectIdeaEventController {

  private final ProjectIdeaEventService events;
  private final ProjectIdeaEventRegistry registry;

  ProjectIdeaEventController(ProjectIdeaEventService events, ProjectIdeaEventRegistry registry) {
    this.events = events;
    this.registry = registry;
  }

  @GetMapping(
      path = "/api/projects/{projectId}/ideas/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter subscribe(@AuthenticationPrincipal Long userId, @PathVariable long projectId) {
    events.requireSubscribable(userId, projectId);
    return registry.subscribe(projectId);
  }
}
