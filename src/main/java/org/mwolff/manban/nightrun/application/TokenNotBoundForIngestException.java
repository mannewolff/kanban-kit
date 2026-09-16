package org.mwolff.manban.nightrun.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Token ohne Projektbindung kann keinen Lauf einliefern (Issue #947).
 *
 * <p>Der Endpunkt liest das Zielprojekt aus der Bindung des Tokens, nicht aus dem Aufruf: So kann
 * eine Meldung nur dort landen, wofür das Token ausgestellt wurde. Ohne Bindung gibt es kein
 * Zielprojekt, und ein Vorgabewert wäre geraten.
 *
 * <p>{@code 400} und nicht {@code 409} wie bei der kanbancompat-Strecke: Dort ist die fehlende
 * Bindung ein Zustandskonflikt an einer bestehenden Ressource, hier eine untaugliche Anmeldung für
 * diesen Aufruf. Der {@code GlobalExceptionHandler} bildet {@code @ResponseStatus}-Ausnahmen
 * generisch ab; dort ändert sich nichts.
 */
@ResponseStatus(
    value = HttpStatus.BAD_REQUEST,
    reason =
        "Token ist an kein Projekt gebunden. Bitte ein projektgebundenes Kanban-Token verwenden.")
public class TokenNotBoundForIngestException extends RuntimeException {}
