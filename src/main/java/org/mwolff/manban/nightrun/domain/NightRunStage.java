package org.mwolff.manban.nightrun.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/**
 * Die Stufe der Nacht-Kette, in der ein Vorgang gelaufen ist (Issue #1112, Plan #1110).
 *
 * <p>Die vier Namen sind die der Zeitvorgaben aus {@code workflow.config.json} unter {@code
 * night.kette} — {@code planMin}, {@code reviewMin}, {@code paketeMin}, {@code abdeckungMin}: Wer
 * eine Kennzahl je Stufe neben ihre Vorgabe stellen will, braucht beide unter demselben Namen.
 */
public enum NightRunStage {

  /** Der Fachplan der Kette. */
  PLAN,

  /** Das Review des Plans. */
  REVIEW,

  /** Die Zerlegung in Arbeitspakete. */
  PAKETE,

  /** Die Abdeckung — der abschließende Durchgang über die Pakete. */
  ABDECKUNG;

  /**
   * Ordnet einen gemeldeten Stufennamen unabhängig von seiner Schreibweise zu (Issue #1150).
   *
   * <p>Das Kit schickt die Namen klein geschrieben, der Vertrag nennt sie groß; die Schlussmeldung
   * einer Kette scheiterte daran mit 400. Die Toleranz gilt allein der Schreibweise: Ein Name
   * außerhalb des Wertebereichs bleibt ein Vertragsbruch und wird abgewiesen. Ausgeliefert wird
   * weiterhin die große Form — das Frontend liest sie so.
   *
   * @param name der gemeldete Name, in beliebiger Schreibweise
   * @return die Stufe zu diesem Namen
   * @throws IllegalArgumentException wenn keine Stufe diesen Namen trägt
   */
  @JsonCreator
  public static NightRunStage vonName(String name) {
    return valueOf(name.toUpperCase(Locale.ROOT));
  }
}
