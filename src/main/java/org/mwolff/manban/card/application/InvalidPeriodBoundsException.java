package org.mwolff.manban.card.application;

import java.io.Serializable;
import java.time.Instant;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Grenzen eines Zeitraums passen nicht zusammen; welche Kombination zulässig ist, legt der
 * jeweilige Aufrufer fest (Plan #1539, E12; Plan #1574, E7).
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class InvalidPeriodBoundsException extends RuntimeException
    implements UebersetzbareMeldung {

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  private InvalidPeriodBoundsException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente;
  }

  /** Nur eine der beiden Grenzen {@code from} und {@code to} ist gesetzt. */
  public static InvalidPeriodBoundsException halberZeitraum() {
    return new InvalidPeriodBoundsException(
        "card.zeitraum.halb",
        "Ein Zeitraum braucht beide Grenzen from und to oder keine von beiden.");
  }

  /** {@code from} liegt nicht vor {@code to}. */
  public static InvalidPeriodBoundsException beginnNichtVorEnde(Instant from, Instant to) {
    return new InvalidPeriodBoundsException(
        "card.zeitraum.beginn-nicht-vor-ende",
        "Der Beginn from muss vor dem Ende to liegen (from=" + from + ", to=" + to + ").",
        from,
        to);
  }

  /** {@code createdFrom} liegt nicht vor {@code createdTo}. */
  public static InvalidPeriodBoundsException angelegtNichtVorBis() {
    return new InvalidPeriodBoundsException(
        "card.zeitraum.angelegt-nicht-vor-bis", "createdFrom muss vor createdTo liegen");
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
