package org.mwolff.manban.card.web;

import org.mwolff.manban.card.application.ProjectIdeasChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reicht ein {@link ProjectIdeasChangedEvent} an die SSE-Registry weiter. Als eigener Listener
 * bleiben die Ideen-Use-Cases, die das Event publizieren, SSE-unabhängig (ArchUnit-konform, kein
 * web-Import in der Application-Schicht).
 *
 * <p>Erst {@link TransactionPhase#AFTER_COMMIT}: ein publiziertes Event erreicht die Abonnenten
 * nur, wenn die auslösende Transaktion committet — bei einem Rollback wird nichts gepusht (so sehen
 * Clients nie eine Änderung, die es gar nicht gab).
 */
@Component
class ProjectIdeaEventListener {

  private final ProjectIdeaEventRegistry registry;

  ProjectIdeaEventListener(ProjectIdeaEventRegistry registry) {
    this.registry = registry;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void onProjectIdeasChanged(ProjectIdeasChangedEvent event) {
    registry.publish(event.projectId());
  }
}
