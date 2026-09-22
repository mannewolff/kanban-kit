package org.mwolff.manban.ratelimit.application;

import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.ratelimit.domain.PersonBudget;

/**
 * Port auf den Zustand der Durchsatzbremse; Schlüssel ist die {@code userId} (Issue #999, Plan #995
 * E4).
 *
 * <p>Der Zustand liegt im Prozessspeicher: Ein Schreibzugriff je Befehl machte die Bremse selbst
 * zum Lastvektor. Anders als {@link AttemptStore} bietet der Port kein getrenntes Lesen und
 * Schreiben, sondern nur die atomare Änderung: Zehn gleichzeitige Befehle einer Person lesen sonst
 * alle denselben Zähler, kommen alle am Deckel vorbei, und ihre Freigaben überschreiben einander —
 * der Zähler leckte in beide Richtungen.
 */
// PMD.ImplicitFunctionalInterface: bewusst KEIN @FunctionalInterface — ein Port mit
// zustandsbehafteter Implementierung, kein Lambda-Ziel.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface PersonBudgetStore {

  /**
   * Ändert den Zustand der Person atomar.
   *
   * @param userId die Person
   * @param change bekommt den bisherigen Zustand ({@code null}, wenn keiner verfolgt wird) und
   *     liefert den neuen; {@code null} verwirft den Eintrag
   * @return der neue Zustand
   */
  @Nullable PersonBudget update(long userId, UnaryOperator<@Nullable PersonBudget> change);
}
