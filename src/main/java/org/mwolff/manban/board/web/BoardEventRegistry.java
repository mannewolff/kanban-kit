package org.mwolff.manban.board.web;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.mwolff.manban.board.application.BoardChangedEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * In-Memory-Registry der offenen SSE-Verbindungen je Board (Server→Client-Kanal für Live-Updates).
 *
 * <p>Bewusst Single-Node: bei mehreren App-Instanzen erreicht ein publiziertes Event nur die
 * Verbindungen der eigenen Instanz. Für den self-hosted Einzelbetrieb ausreichend; Multi-Instanz
 * ist außerhalb des Scopes (dann bräuchte es einen geteilten Bus, z. B. Redis-Pub/Sub).
 */
@Component
public class BoardEventRegistry {

  private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
  // Periodischer Kommentar-Ping hält den Stream durch Proxies (Caddy) offen, die inaktive
  // Verbindungen sonst nach kurzer Zeit schließen.
  private static final long HEARTBEAT_MS = 25_000L;

  private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

  /** Öffnet eine neue SSE-Verbindung für das Board und registriert Aufräum-Callbacks. */
  public SseEmitter subscribe(long boardId) {
    SseEmitter emitter = createEmitter();
    emitters.computeIfAbsent(boardId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
    emitter.onCompletion(() -> remove(boardId, emitter));
    emitter.onTimeout(() -> remove(boardId, emitter));
    emitter.onError(error -> remove(boardId, emitter));
    return emitter;
  }

  /** Sendet das Event an alle offenen Verbindungen des Boards. */
  public void publish(long boardId, BoardChangedEvent event) {
    Set<SseEmitter> boardEmitters = emitters.get(boardId);
    if (boardEmitters == null) {
      return;
    }
    for (SseEmitter emitter : boardEmitters) {
      trySend(boardId, emitter, SseEmitter.event().name("board-changed").data(event));
    }
  }

  @Scheduled(fixedRate = HEARTBEAT_MS)
  void heartbeat() {
    emitters.forEach(
        (boardId, boardEmitters) ->
            boardEmitters.forEach(
                emitter -> trySend(boardId, emitter, SseEmitter.event().comment("ping"))));
  }

  private void trySend(long boardId, SseEmitter emitter, SseEventBuilder event) {
    try {
      emitter.send(event);
    } catch (IOException | IllegalStateException e) {
      // Verbindung ist tot (Client weg / bereits abgeschlossen) — aufräumen.
      remove(boardId, emitter);
    }
  }

  private void remove(long boardId, SseEmitter emitter) {
    emitters.computeIfPresent(
        boardId,
        (id, boardEmitters) -> {
          boardEmitters.remove(emitter);
          return boardEmitters.isEmpty() ? null : boardEmitters;
        });
  }

  /** Erzeugt den Emitter — als Seam ausgelagert, damit Tests einen Mock einschleusen können. */
  SseEmitter createEmitter() {
    return new SseEmitter(STREAM_TIMEOUT_MS);
  }

  /** Anzahl offener Verbindungen eines Boards (für Tests). */
  int subscriberCount(long boardId) {
    Set<SseEmitter> boardEmitters = emitters.get(boardId);
    return boardEmitters == null ? 0 : boardEmitters.size();
  }
}
