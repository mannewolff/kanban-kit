package org.mwolff.manban.board.web;

import org.mwolff.manban.board.application.BoardEventService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE-Endpoint für Live-Board-Updates. Ein Board-Mitglied abonniert den Stream; Server-seitige
 * Änderungen werden über die {@link BoardEventRegistry} an alle Abonnenten des Boards gepusht.
 * Session-Auth erzwingt die Security-Filterkette ({@code /api/**}); die Mitgliedschaft prüft der
 * {@link BoardEventService}.
 */
@RestController
class BoardEventController {

  private final BoardEventService events;
  private final BoardEventRegistry registry;

  BoardEventController(BoardEventService events, BoardEventRegistry registry) {
    this.events = events;
    this.registry = registry;
  }

  @GetMapping(path = "/api/boards/{boardId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter subscribe(@AuthenticationPrincipal Long userId, @PathVariable long boardId) {
    events.requireSubscribable(userId, boardId);
    return registry.subscribe(boardId);
  }
}
