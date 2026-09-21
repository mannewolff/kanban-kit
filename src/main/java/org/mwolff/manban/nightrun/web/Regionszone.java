package org.mwolff.manban.nightrun.web;

import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Eingrenzung einer vom Leser gemeldeten Zone auf eine Regionszone (Issue #939, geteilt seit
 * #1095).
 *
 * <p><b>Warum nicht einfach {@link ZoneId}:</b> Unbekanntes weist die Bindung als {@code ZoneId}
 * selbst mit 400 ab; Offset-Zonen wie {@code +05:30}, {@code Z} oder {@code GMT+2} akzeptiert
 * {@code ZoneId.of} dagegen. Sie werden hier mit 400 abgewiesen — Postgres deutet POSIX-Offsets mit
 * umgekehrtem Vorzeichen, und die Nächte lägen sonst verschoben.
 *
 * <p><b>Eigener Typ statt einer privaten Methode je Controller</b> (Plan #1088 E6): Die
 * Verbrauchs-Auswertung und der Plattform-Leitstand ziehen ihre Nachtgrenzen nach derselben Regel.
 * Zweimal geschrieben liefe sie bei der nächsten Änderung auseinander, und eine verschobene
 * Nachtgrenze fällt niemandem auf — sie zeigt nur die falschen Läufe.
 */
final class Regionszone {

  private Regionszone() {}

  /** Lässt allein Zonen aus der Zonendatenbank durch — nie einen festen Offset. */
  static ZoneId of(ZoneId zone) {
    if (!ZoneId.getAvailableZoneIds().contains(zone.getId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "zone muss eine Regionszone sein, etwa Europe/Berlin");
    }
    return zone;
  }
}
