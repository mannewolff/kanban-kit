package org.mwolff.manban.board.application;

import org.mwolff.manban.project.application.ProjectCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Legt zu jedem neu angelegten Projekt automatisch ein normales Board „default" mit den fünf
 * Standardspalten an, damit der Owner sofort ein benutzbares Projekt hat.
 *
 * <p>Als eigener Listener bleibt das {@code project}-Modul frei von einer Board-Abhängigkeit (der
 * umgekehrte Weg {@code board → project} existiert bereits) — so entsteht kein Modul-Zyklus.
 *
 * <p>Bewusst ein synchroner {@link EventListener} (nicht {@code @TransactionalEventListener}): er
 * läuft im selben Transaktions-Scope wie {@code ProjectService.create}. Scheitert die Board-Anlage,
 * rollt die gesamte Projektanlage atomar mit zurück (kein Projekt ohne Board). Der Rechte-Check von
 * {@link BoardService#createBoard} greift für den Owner, dessen Mitgliedschaft im selben Scope
 * bereits gespeichert ist.
 */
@Component
class DefaultBoardCreator {

  private final BoardService boards;

  DefaultBoardCreator(BoardService boards) {
    this.boards = boards;
  }

  @EventListener
  void onProjectCreated(ProjectCreatedEvent event) {
    boards.createBoard(event.ownerUserId(), event.projectId(), "default");
  }
}
