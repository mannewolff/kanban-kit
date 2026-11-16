package org.mwolff.manban.fremdboard.application;

import java.io.Serializable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das fremde System hat auf einen Anstoß nicht geantwortet (Issue #2167, Plan #2152 A6, A9): 502,
 * weil kanban-kit hier als Vermittler scheitert, nicht der Aufrufer. Der Grund stammt aus der
 * Quelle und nennt den Zugang nie.
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public final class FremdBoardNichtErreichbarException extends RuntimeException
    implements UebersetzbareMeldung {

  private static final long serialVersionUID = 1L;

  private final String grund;

  /**
   * Der gescheiterte Abruf mit seinem Grund.
   *
   * @param grund lesbarer Grund aus der Quelle
   * @param ursache der gescheiterte Abruf
   */
  public FremdBoardNichtErreichbarException(String grund, Throwable ursache) {
    super("Das fremde Board ließ sich nicht lesen: " + grund, ursache);
    this.grund = grund;
  }

  @Override
  public String schluessel() {
    return "fremdboard.nicht-erreichbar";
  }

  @Override
  public Object[] argumente() {
    return new Serializable[] {grund};
  }
}
