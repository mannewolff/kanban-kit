package org.mwolff.manban.fremdboard.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ein Wechsel an einer Karte im fremden System (Issue #2158, Plan #2152 A4, A15): ein Label gesetzt
 * oder abgenommen, die Karte geschlossen oder wieder geöffnet. Für Fluss und Verweildauer zählt
 * {@link #zeit()}, der Zeitpunkt aus dem fremden System, nicht der des Abgleichs.
 *
 * @param kennung Kennung des Ereignisses im fremden System; mit ihr wird kein Ereignis doppelt
 *     übernommen
 * @param kartenNummer Nummer der Karte im fremden System
 * @param art was sich geändert hat
 * @param label der Labelname bei {@link Art#LABEL_GESETZT} und {@link Art#LABEL_ENTFERNT}, sonst
 *     {@code null}
 * @param zeit Zeitpunkt, den das fremde System für den Wechsel festhält
 * @param akteur Benutzername dessen, der den Wechsel ausgelöst hat; {@code null}, wenn das fremde
 *     System ihn nicht festhält („nicht erfasst“)
 */
public record FremdEreignis(
    String kennung,
    long kartenNummer,
    Art art,
    @Nullable String label,
    Instant zeit,
    @Nullable String akteur) {

  /** Was sich an der Karte geändert hat. */
  public enum Art {
    LABEL_GESETZT,
    LABEL_ENTFERNT,
    GESCHLOSSEN,
    WIEDER_GEOEFFNET
  }
}
