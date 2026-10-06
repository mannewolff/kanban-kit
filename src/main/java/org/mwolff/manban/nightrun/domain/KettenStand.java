package org.mwolff.manban.nightrun.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Der Stand der Kette einer Karte, abgeleitet aus ihrem Laufstand (Issue #1451, Plan #1447 E5, E10,
 * E13).
 *
 * @param ziel die Zielstation aus der Zeile {@code Ziel:}; {@code null} ohne sie — dann endet die
 *     Kette nach der Abdeckung (E10)
 * @param pruefer die Prüferzahl aus der Zeile {@code Prüfer:}; {@code null} ohne sie
 * @param zielErreicht ob der Laufstand den Kopf {@code fertig bis <ziel>} trägt
 * @param projektgrenze die Projektgrenze aus der Zeile {@code Grenze:}; {@code null} ohne sie
 * @param stationen alle Stationen in ihrer Reihenfolge
 */
public record KettenStand(
    @Nullable ProgressStage ziel,
    @Nullable Integer pruefer,
    boolean zielErreicht,
    @Nullable Projektgrenze projektgrenze,
    List<StationStand> stationen) {

  public KettenStand {
    stationen = List.copyOf(stationen);
  }

  /**
   * Die Grenze, die das Projekt der Kette setzt.
   *
   * @param stufe die letzte im Projekt erreichbare Stufe
   * @param grund der Wartetext des Kits, wörtlich; {@code null}, solange er nicht im Laufstand
   *     steht
   */
  public record Projektgrenze(ProgressStage stufe, @Nullable String grund) {}
}
