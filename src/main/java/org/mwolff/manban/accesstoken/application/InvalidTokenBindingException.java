package org.mwolff.manban.accesstoken.application;

import java.io.Serializable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die beim Anlegen eines Tokens angegebene Projekt-/Board-Bindung ist unschlüssig: Board unbekannt,
 * Board gehört nicht zum angegebenen Projekt, oder nur eines von beiden ({@code projectId}/{@code
 * boardId}) gesetzt. Ergibt HTTP 400.
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1641).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class InvalidTokenBindingException extends RuntimeException
    implements UebersetzbareMeldung {

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  private InvalidTokenBindingException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente;
  }

  /** Nur eines von {@code projectId} und {@code boardId} ist gesetzt. */
  public static InvalidTokenBindingException unvollstaendig() {
    return new InvalidTokenBindingException(
        "accesstoken.bindung.unvollstaendig",
        "projectId und boardId müssen zusammen gesetzt sein (oder beide leer)");
  }

  /** Das Board gibt es nicht. */
  public static InvalidTokenBindingException boardUnbekannt(long boardId) {
    return new InvalidTokenBindingException(
        "accesstoken.bindung.board-unbekannt", "Board " + boardId + " unbekannt", boardId);
  }

  /** Das Board gehört zu einem anderen Projekt. */
  public static InvalidTokenBindingException boardGehoertNichtZuProjekt(
      long boardId, long projectId) {
    return new InvalidTokenBindingException(
        "accesstoken.bindung.falsches-projekt",
        "Board " + boardId + " gehört nicht zu Projekt " + projectId,
        boardId,
        projectId);
  }

  @Override
  public String schluessel() {
    return meldungsschluessel;
  }

  @Override
  public Object[] argumente() {
    return meldungsargumente.clone();
  }
}
