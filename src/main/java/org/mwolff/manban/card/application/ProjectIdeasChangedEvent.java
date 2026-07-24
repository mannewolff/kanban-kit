package org.mwolff.manban.card.application;

/**
 * Anwendungs-Event: der projektweite Ideen-Pool hat sich geändert (für Live-Updates via SSE). Wird
 * von den Ideen-Use-Cases über den Spring-{@code ApplicationEventPublisher} publiziert und vom
 * Ideen-Event-Listener an die SSE-Registry weitergereicht — so bleiben die Use-Cases
 * SSE-unabhängig.
 *
 * <p>Bewusst nur „der Pool des Projekts hat sich geändert" ohne Detail: der Client lädt bei einem
 * Event den Pool neu (spiegelbildlich zu {@code BoardChangedEvent}, nur projekt- statt
 * board-scoped).
 *
 * @param projectId betroffenes Projekt
 */
public record ProjectIdeasChangedEvent(long projectId) {}
