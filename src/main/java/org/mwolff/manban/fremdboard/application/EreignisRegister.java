package org.mwolff.manban.fremdboard.application;

import java.util.Collection;
import java.util.Set;

/**
 * Port der Sperre gegen doppelte Übernahme (Issue #2167, Plan #2152 A6): Jedes Ereignis des fremden
 * Systems wird über seine Kennung genau einmal je Projekt übernommen, gleich ob der Abgleich oder
 * ein Anstoß es zuerst liest.
 */
public interface EreignisRegister {

  /** Die Kennungen aus {@code kennungen}, die im Projekt schon übernommen sind. */
  Set<String> bekannte(long projectId, Collection<String> kennungen);

  /**
   * Trägt das Ereignis ein, sofern es noch nicht eingetragen ist. Läuft in der Transaktion des
   * Aufrufers, damit Eintrag und Übernahme gemeinsam gelten oder gemeinsam verfallen.
   *
   * @param art was sich geändert hat, zur Auskunft
   * @return {@code true}, wenn das Ereignis neu war
   */
  boolean eintragen(long projectId, String art, String kennung);
}
