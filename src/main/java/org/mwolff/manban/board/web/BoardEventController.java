package org.mwolff.manban.board.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mwolff.manban.board.application.BoardChangedEvent;
import org.mwolff.manban.board.application.BoardEventService;
import org.mwolff.manban.common.web.api.ApiSchemas;
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
@Tag(name = "Boards")
@RestController
class BoardEventController {

  private final BoardEventService events;
  private final BoardEventRegistry registry;

  BoardEventController(BoardEventService events, BoardEventRegistry registry) {
    this.events = events;
    this.registry = registry;
  }

  @Operation(
      summary = "Änderungen eines Boards live abonnieren",
      description =
          "Öffnet einen Server-Sent-Events-Stream (text/event-stream), über den der Leitstand"
              + " jede Änderung am Board meldet, bis der Aufrufer die Verbindung schließt oder"
              + " sie nach 30 Minuten endet; danach neu abonnieren. Verlangt die Mitgliedschaft"
              + " im Projekt.\n\n"
              + "Ereignisarten: Jede Änderung kommt als Ereignis mit dem Namen board-changed;"
              + " seine Daten sind ein JSON-Objekt mit boardId, type und cardId. type ist eine"
              + " von CREATED (Karte angelegt), UPDATED (geändert), MOVED (verschoben), ARCHIVED"
              + " (archiviert), RESTORED (wiederhergestellt) oder DELETED (gelöscht); cardId nennt"
              + " die betroffene Karte oder ist null, wenn die Änderung keine einzelne Karte"
              + " betrifft. Etwa alle 25 Sekunden kommt zusätzlich ein Kommentar ping, der die"
              + " Verbindung durch Proxys offen hält und keine Daten trägt.")
  @ApiResponse(
      responseCode = "200",
      description = "Der Ereignisstrom; jedes Ereignis board-changed trägt ein solches Objekt.",
      content =
          @Content(
              mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
              schema = @Schema(implementation = BoardChangedEvent.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Das Board gibt es nicht, oder der Aufrufer ist kein Mitglied seines Projekts.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping(path = "/api/boards/{boardId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter subscribe(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Boards.", example = "3") @PathVariable
          long boardId) {
    events.requireSubscribable(userId, boardId);
    return registry.subscribe(boardId);
  }
}
