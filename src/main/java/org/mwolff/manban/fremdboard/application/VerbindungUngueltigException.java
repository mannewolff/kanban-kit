package org.mwolff.manban.fremdboard.application;

import java.io.Serializable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Angaben zur Verbindung taugen nicht (Issue #2166, Plan #2152 A14): Zugang fehlt, Adresse
 * nicht erlaubt oder das fremde Board ließ sich mit ihnen nicht lesen (400). Keine Meldung nennt
 * den Zugang.
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class VerbindungUngueltigException extends RuntimeException
    implements UebersetzbareMeldung {

  private static final long serialVersionUID = 1L;

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  private VerbindungUngueltigException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente;
  }

  /** Zum ersten Anbinden fehlt der Zugang. */
  public static VerbindungUngueltigException zugangFehlt() {
    return new VerbindungUngueltigException(
        "fremdboard.zugang-fehlt", "Zum Anbinden fehlt der Zugang.");
  }

  /** Die Adresse steht nicht in der Erlaubnisliste oder ist kein https (A14). */
  public static VerbindungUngueltigException adresseAbgelehnt(String grund) {
    return new VerbindungUngueltigException(
        "fremdboard.adresse-abgelehnt", "Die Adresse ist nicht erlaubt: " + grund, grund);
  }

  /** Der Probeabruf beim fremden System ist gescheitert; {@code ursache} bleibt angehängt. */
  public static VerbindungUngueltigException nichtLesbar(String grund, Throwable ursache) {
    VerbindungUngueltigException fehler =
        new VerbindungUngueltigException(
            "fremdboard.nicht-lesbar",
            "Das fremde Board ließ sich mit diesen Angaben nicht lesen: " + grund,
            grund);
    fehler.initCause(ursache);
    return fehler;
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
