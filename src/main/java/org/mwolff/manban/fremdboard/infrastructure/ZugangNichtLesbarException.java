package org.mwolff.manban.fremdboard.infrastructure;

/**
 * Ein gespeicherter Zugang lässt sich nicht entschlüsseln (Issue #2157, Plan #2152 A13) — etwa nach
 * einem Schlüsselwechsel oder aus einer Sicherung ohne Schlüssel. Die Einstellung zeigt das und
 * erlaubt, den Zugang neu zu hinterlegen. Die Meldung nennt nie den Zugang selbst.
 */
public class ZugangNichtLesbarException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  ZugangNichtLesbarException(String meldung) {
    super(meldung);
  }

  ZugangNichtLesbarException(String meldung, Throwable ursache) {
    super(meldung, ursache);
  }
}
