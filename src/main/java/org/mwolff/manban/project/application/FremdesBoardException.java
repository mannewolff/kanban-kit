package org.mwolff.manban.project.application;

import org.mwolff.manban.common.MitProblemTyp;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das Board des Projekts liegt in einem fremden System; kanban-kit spiegelt es nur und lehnt jedes
 * Schreiben außer dem Spiegel ab (Issue #2161, Plan #2152 A10, A18).
 *
 * <p>Zustandskonflikt, kein Rechtefehler — deshalb 409 und nicht 403: Derselbe Aufruf wäre in einem
 * Projekt mit eigenem Board gültig. Der eigene Problem-Typ lässt den Client die Sperre erkennen,
 * ohne den übersetzten Grund zu lesen. Die Ausnahme liegt neben {@link BoardQuelle} in {@code
 * project}, weil {@code card}, {@code comment}, {@code attachment}, {@code kanbancompat} und {@code
 * board} sie gleichermaßen werfen.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class FremdesBoardException extends RuntimeException
    implements UebersetzbareMeldung, MitProblemTyp {

  /** Problem-Typ der Antwort, an dem ein Client die Sperre erkennt. */
  public static final String PROBLEM_TYP = "fremdes-board";

  public FremdesBoardException() {
    super(
        "Das Board dieses Projekts liegt in einem fremden System und wird dort bearbeitet."
            + " In kanban-kit lässt es sich nur lesen.");
  }

  @Override
  public String schluessel() {
    return "project.fremdes-board";
  }

  @Override
  public String problemTyp() {
    return PROBLEM_TYP;
  }
}
