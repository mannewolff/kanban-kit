package org.mwolff.manban.kanbancompat.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der angeforderte Kanban-Spalten-Key ist unbekannt oder das gebundene Board hat keine passende
 * Spalte. Ergibt HTTP 400.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidKanbanColumnException extends RuntimeException {

  public InvalidKanbanColumnException(String message) {
    super(message);
  }
}
