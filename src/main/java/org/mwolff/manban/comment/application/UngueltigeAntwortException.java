package org.mwolff.manban.comment.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Die Antwort passt nicht zur Rückfrage (Issue #2210). Ergibt HTTP 400. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class UngueltigeAntwortException extends RuntimeException
    implements UebersetzbareMeldung {

  private final String meldungsSchluessel;
  private final Object[] meldungsArgumente;

  private UngueltigeAntwortException(String meldung, String schluessel, Object... argumente) {
    super(meldung);
    this.meldungsSchluessel = schluessel;
    this.meldungsArgumente = argumente.clone();
  }

  /** Die Antwort auf eine Frage nennt keine Frage der Rückfrage oder nicht genau eine Angabe. */
  public static UngueltigeAntwortException fuerFrage(int nr) {
    return new UngueltigeAntwortException(
        "Die Antwort auf Frage " + nr + " passt nicht zur Rückfrage", "comment.antwort.frage", nr);
  }

  /** Der gewählte Weg wird nicht angeboten. */
  public static UngueltigeAntwortException keinWeg() {
    return new UngueltigeAntwortException(
        "Die Rückfrage bietet diesen Weg nicht an", "comment.antwort.kein-weg");
  }

  /** Freigeben an einer Rückfrage, die keinen Stand zur Freigabe stellt. */
  public static UngueltigeAntwortException keineFreigabe() {
    return new UngueltigeAntwortException(
        "Diese Rückfrage fragt nach keiner Freigabe", "comment.antwort.keine-freigabe");
  }

  /** Die Antwort sagt nichts. */
  public static UngueltigeAntwortException leer() {
    return new UngueltigeAntwortException(
        "Die Antwort enthält keine Angabe", "comment.antwort.leer");
  }

  /** Die Antwort überschreitet die Länge eines Kommentars. */
  public static UngueltigeAntwortException zuLang() {
    return new UngueltigeAntwortException("Die Antwort ist zu lang", "comment.antwort.zu-lang");
  }

  @Override
  public String schluessel() {
    return meldungsSchluessel;
  }

  @Override
  public Object[] argumente() {
    return meldungsArgumente.clone();
  }
}
