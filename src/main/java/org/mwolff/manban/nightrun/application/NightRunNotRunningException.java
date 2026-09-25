package org.mwolff.manban.nightrun.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Lauf, der von Hand als beendet gekennzeichnet werden soll, läuft gar nicht mehr (Issue
 * #1197).
 *
 * <p>Gekennzeichnet wird nur, was der Leitstand als laufend zeigt. Ein verstummter Lauf ist heute
 * eine Störung, und das soll er bleiben: Wer ihn wegräumen will, quittiert ihn — dafür gibt es den
 * eigenen Weg, und er sagt etwas anderes („gesehen" statt „beendet"). Ein abgeschlossener Lauf
 * trägt ohnehin seinen gemeldeten Ausgang.
 *
 * <p>409 und nicht 404: Den Lauf gibt es sehr wohl, nur ist sein Zustand ein anderer, als der
 * Aufrufer annahm — regelmäßig, weil die Seite dem Stand 30 Sekunden hinterherhängt.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class NightRunNotRunningException extends RuntimeException {

  private static final long serialVersionUID = 1L;
}
