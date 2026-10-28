package org.mwolff.manban.nightrun.domain;

import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;

/**
 * Die Signale der Nacharbeit (Plan #1752 E1, E3), gemeinsam für die Platten „Nacharbeit“ und
 * „Modelle“ (Issue #1946, Plan #1905 E8), damit beide nach denselben Regeln zählen.
 */
public final class NacharbeitSignale {

  /** Konstantenname von {@code CardStatus} für In Progress. */
  public static final String IN_PROGRESS = "IN_PROGRESS";

  private static final Set<String> RUECKZUG_ZIELE = Set.of("READY", "BACKLOG");
  private static final Set<Sache> HALT = EnumSet.of(Sache.KLAEREN, Sache.LAUF_WARTET);

  private NacharbeitSignale() {}

  /**
   * Ob eine Bewegung ein Rückzug ist: nach Ready oder Backlog, aus dem bekannten Status In Progress
   * (E1).
   *
   * @param statusVorher bekannter Status der Karte davor; {@code null}, wenn unbekannt
   * @param statusNachher Status der Karte nach der Bewegung
   */
  public static boolean istRueckzug(@Nullable String statusVorher, @Nullable String statusNachher) {
    // Set.of verträgt kein contains(null); darum die Prüfung davor.
    return IN_PROGRESS.equals(statusVorher)
        && statusNachher != null
        && RUECKZUG_ZIELE.contains(statusNachher);
  }

  /**
   * Ob ein Label ein Halt ist: eines der Sachen {@code KLAEREN} oder {@code LAUF_WARTET}, gleich in
   * welcher Sprache (E3).
   */
  public static boolean istHalt(String labelName) {
    return KitNamen.STANDARD.sache(labelName).filter(HALT::contains).isPresent();
  }
}
