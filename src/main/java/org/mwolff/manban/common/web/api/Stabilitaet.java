package org.mwolff.manban.common.web.api;

/**
 * Stabilität eines Aufrufs in der API-Beschreibung (Issue #1402, Plan #1400).
 *
 * <p>Ein verlässlicher Aufruf ändert sich nicht ohne Vorlauf: Er wird zuerst abgekündigt ({@link
 * ApiVertrag#abgekuendigtSeit()}) und ändert sich oder entfällt frühestens eine Minor-Version
 * später. Ein änderbarer Aufruf kann sich mit jeder Version ändern.
 */
public enum Stabilitaet {
  /** Eine fremde Anbindung kann sich auf den Aufruf stützen. */
  VERLAESSLICH("verlaesslich"),
  /** Der Aufruf kann sich noch ändern; die Vorgabe ohne {@link ApiVertrag}. */
  AENDERBAR("aenderbar");

  private final String wert;

  Stabilitaet(String wert) {
    this.wert = wert;
  }

  /** Maschinenlesbare Kennung, wie sie in {@code x-stabilitaet} steht. */
  public String kennung() {
    return wert;
  }
}
