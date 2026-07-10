package org.mwolff.manban.kanbancompat.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das präsentierte Token ist gültig, aber nicht an ein Projekt + Board gebunden — die
 * Kanban-Compat-API kann daher kein Board bestimmen. Ergibt HTTP 409.
 */
@ResponseStatus(value = HttpStatus.CONFLICT,
        reason = "Token ist an kein Board gebunden. Bitte ein board-gebundenes Kanban-Token verwenden.")
public class TokenNotBoundException extends RuntimeException {
}
