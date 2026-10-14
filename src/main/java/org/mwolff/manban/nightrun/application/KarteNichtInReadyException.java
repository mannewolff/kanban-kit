package org.mwolff.manban.nightrun.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Karte steht nicht in Ready, sie wartet also nicht auf den Runner (Issue #1728, Plan #1724
 * E12).
 *
 * <p>404 und nicht 409: Für diese Karte gibt es keinen Wartegrund, so wie es für eine unbekannte
 * keinen gibt. Der Kartendialog fragt für jede Karte und zeigt bei 404 nichts — ob Ready gilt,
 * entscheidet der Server, nicht der Dialog.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class KarteNichtInReadyException extends RuntimeException {

  private static final long serialVersionUID = 1L;
}
