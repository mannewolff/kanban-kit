package org.mwolff.manban.nightrun.domain;

import java.util.List;

/**
 * Der Fortschritt eines Laufs, wie ihn das Board heute zeigt (Issue #1374, Plan #1372), ermittelt
 * von {@link FortschrittErmittlung}.
 *
 * @param zuordnung ob sich die Karten des Laufs überhaupt zuordnen lassen (E3)
 * @param ketten die Ketten des Laufs, nach Nummer der Anforderung; leer in der Umsetzungsnacht (E8)
 * @param pakete alle Arbeitspakete des Laufs, nach Nummer
 * @param unbekannt Karten, deren Zuordnung sich nicht feststellen lässt — gelistet, nicht gezählt
 * @param offeneFragen Karten mit einer offenen Frage an den Menschen (E9)
 * @param unbekanntOhneAusweis ob unter {@code unbekannt} Karten stehen, weil der Lauf sich nicht
 *     ausgewiesen hat und seine Spuren zeitlich zu einem anderen Lauf passen (Issue #1429, A9)
 */
public record NightRunProgress(
    ProgressAssignment zuordnung,
    List<ChainProgress> ketten,
    List<PackageProgress> pakete,
    List<CardRef> unbekannt,
    List<CardRef> offeneFragen,
    boolean unbekanntOhneAusweis) {

  public NightRunProgress {
    ketten = List.copyOf(ketten);
    pakete = List.copyOf(pakete);
    unbekannt = List.copyOf(unbekannt);
    offeneFragen = List.copyOf(offeneFragen);
  }

  /** Kein Fortschritt — für Läufe ohne Kette und ohne Umsetzung (E8). */
  public static NightRunProgress leer() {
    return new NightRunProgress(
        ProgressAssignment.OK, List.of(), List.of(), List.of(), List.of(), false);
  }

  /** Der ganze Fortschritt ist unbekannt — der Lauf hat keinen Token-Namen (E3). */
  public static NightRunProgress zuordnungUnbekannt() {
    return new NightRunProgress(
        ProgressAssignment.UNBEKANNT, List.of(), List.of(), List.of(), List.of(), false);
  }
}
