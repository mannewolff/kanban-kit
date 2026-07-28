package org.mwolff.manban.card.web;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * In-Memory-Registry der offenen SSE-Verbindungen je Projekt (Server→Client-Kanal für Live-Updates
 * des Ideen-Pools). Spiegelbild von {@code BoardEventRegistry}, nur projekt- statt board-scoped.
 *
 * <p>Bewusst Single-Node: bei mehreren App-Instanzen erreicht ein publiziertes Event nur die
 * Verbindungen der eigenen Instanz. Für den self-hosted Einzelbetrieb ausreichend; Multi-Instanz
 * ist außerhalb des Scopes (dann bräuchte es einen geteilten Bus, z. B. Redis-Pub/Sub).
 */
@Component
public class ProjectIdeaEventRegistry {

  private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
  // Periodischer Kommentar-Ping hält den Stream durch Proxies (Caddy) offen, die inaktive
  // Verbindungen sonst nach kurzer Zeit schließen.
  private static final long HEARTBEAT_MS = 25_000L;

  private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

  /** Öffnet eine neue SSE-Verbindung für das Projekt und registriert Aufräum-Callbacks. */
  public SseEmitter subscribe(long projectId) {
    SseEmitter emitter = createEmitter();
    emitters.computeIfAbsent(projectId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
    emitter.onCompletion(() -> remove(projectId, emitter));
    emitter.onTimeout(() -> remove(projectId, emitter));
    emitter.onError(error -> remove(projectId, emitter));
    return emitter;
  }

  /** Sendet ein {@code project-ideas-changed}-Event an alle offenen Verbindungen des Projekts. */
  public void publish(long projectId) {
    Set<SseEmitter> projectEmitters = emitters.get(projectId);
    if (projectEmitters == null) {
      return;
    }
    for (SseEmitter emitter : projectEmitters) {
      trySend(projectId, emitter, SseEmitter.event().name("project-ideas-changed").data(projectId));
    }
  }

  @Scheduled(fixedRate = HEARTBEAT_MS)
  void heartbeat() {
    emitters.forEach(
        (projectId, projectEmitters) ->
            projectEmitters.forEach(
                emitter -> trySend(projectId, emitter, SseEmitter.event().comment("ping"))));
  }

  /**
   * Sendet und räumt tote Verbindungen ab. Der Fehler wird bewusst nicht protokolliert (#472): Ein
   * geschlossener Browser-Tab erzeugt hier eine Ausnahme pro Emitter und Heartbeat — das ist der
   * Normalfall, kein Vorfall. Diagnostisch aussagekräftig ist die Zahl offener Verbindungen, nicht
   * die einzelne Ausnahme; deshalb bleibt das Muster ohne Namen (Unnamed Pattern).
   */
  private void trySend(long projectId, SseEmitter emitter, SseEventBuilder event) {
    try {
      emitter.send(event);
    } catch (IOException | IllegalStateException _) {
      // Verbindung ist tot (Client weg / bereits abgeschlossen) — aufräumen.
      remove(projectId, emitter);
    }
  }

  private void remove(long projectId, SseEmitter emitter) {
    emitters.computeIfPresent(
        projectId,
        (id, projectEmitters) -> {
          projectEmitters.remove(emitter);
          return projectEmitters.isEmpty() ? null : projectEmitters;
        });
  }

  /** Erzeugt den Emitter — als Seam ausgelagert, damit Tests einen Mock einschleusen können. */
  SseEmitter createEmitter() {
    return new SseEmitter(STREAM_TIMEOUT_MS);
  }

  /** Anzahl offener Verbindungen eines Projekts (für Tests). */
  int subscriberCount(long projectId) {
    Set<SseEmitter> projectEmitters = emitters.get(projectId);
    return projectEmitters == null ? 0 : projectEmitters.size();
  }
}
