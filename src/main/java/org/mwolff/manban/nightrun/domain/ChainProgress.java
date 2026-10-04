package org.mwolff.manban.nightrun.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Fortschritt einer Kette des Laufs (Issue #1374, Plan #1372 E4, E5, E12).
 *
 * @param anforderung die fachliche Anforderung, an der die Kette hängt
 * @param plan der Plan der Kette; {@code null}, solange keiner bekannt ist
 * @param pakete die Arbeitspakete, die der Lauf zum Plan angelegt hat, nach Nummer
 * @param stufen der Weg in seiner Reihenfolge — {@link ProgressStage#UMSETZUNG} nur in Variante B
 * @param aktuelleStufe die aktuelle Stelle; {@code null}, wenn das Ende des Wegs erreicht ist
 * @param endeErreicht ob jede Stufe des Wegs erreicht ist — in Variante A nach der Abdeckung
 */
public record ChainProgress(
    CardRef anforderung,
    @Nullable CardRef plan,
    List<PackageProgress> pakete,
    List<StageProgress> stufen,
    @Nullable ProgressStage aktuelleStufe,
    boolean endeErreicht) {

  public ChainProgress {
    pakete = List.copyOf(pakete);
    stufen = List.copyOf(stufen);
  }
}
