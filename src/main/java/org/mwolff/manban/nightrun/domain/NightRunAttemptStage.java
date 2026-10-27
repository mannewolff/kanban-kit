package org.mwolff.manban.nightrun.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/**
 * Die Stufe der Kette, in der ein einzelner Vorgang gelaufen ist (Issue #1944, Plan #1905 E2).
 *
 * <p>Ein eigener Typ neben {@link NightRunStage}: Jene ist an die vier Zeitvorgaben {@code
 * planMin}…{@code abdeckungMin} und an höchstens vier Stufen je Paket gebunden. Ein Vorgang kennt
 * dazu die Umsetzung — eine fünfte Konstante dort änderte die Prüfung der {@code stages} mit.
 */
public enum NightRunAttemptStage {

  /** Der Fachplan der Kette. */
  PLAN,

  /** Die Prüfung eines Dokuments — je Prüfer ein Vorgang. */
  REVIEW,

  /** Die Zerlegung in Arbeitspakete. */
  PAKETE,

  /** Die Abdeckung — der abschließende Durchgang über die Pakete. */
  ABDECKUNG,

  /** Die Umsetzung eines Arbeitspakets. */
  UMSETZUNG;

  /**
   * Ordnet einen gemeldeten Stufennamen unabhängig von seiner Schreibweise zu — dieselbe Regel wie
   * {@link NightRunStage#vonName}: tolerant in der Schreibweise und bei den englischen Namen
   * ({@code PACKAGES}, {@code COVERAGE}, {@code IMPLEMENTATION}), streng im Wertebereich.
   *
   * @param name der gemeldete Name, in beliebiger Schreibweise
   * @return die Stufe zu diesem Namen
   * @throws IllegalArgumentException wenn keine Stufe diesen Namen trägt
   */
  @JsonCreator
  public static NightRunAttemptStage vonName(String name) {
    String gross = name.toUpperCase(Locale.ROOT);
    return switch (gross) {
      case "PACKAGES" -> PAKETE;
      case "COVERAGE" -> ABDECKUNG;
      case "IMPLEMENTATION" -> UMSETZUNG;
      default -> valueOf(gross);
    };
  }
}
