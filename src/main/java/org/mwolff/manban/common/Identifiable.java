package org.mwolff.manban.common;

import org.jspecify.annotations.Nullable;

/**
 * Domänenobjekt mit technischer ID, die erst bei der Persistierung vergeben wird (Issue #0080).
 *
 * <p>Die Domänen-Records deklarieren ihre {@code id}-Komponente als {@code @Nullable} ({@code null}
 * vor der Persistierung). Code, der nachweislich mit persistierten Instanzen arbeitet (z. B.
 * View-Mapping nach {@code repository.save(...)}), holt die ID über {@link #requireId()} — das
 * macht die Annahme „ist persistiert" explizit und für NullAway prüfbar, statt sie über verstreute
 * {@code requireNonNull}-Aufrufe zu verstecken.
 */
// PMD.ImplicitFunctionalInterface: bewusst KEIN @FunctionalInterface — die einzige abstrakte
// Methode id() wird von den Domänen-Records als Accessor erfüllt; das Interface ist eine
// Typ-Abstraktion, kein Lambda-Ziel.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface Identifiable {

  /** Technische ID; {@code null} vor der Persistierung. */
  @Nullable Long id();

  /**
   * ID einer persistierten Instanz.
   *
   * @throws IllegalStateException wenn die Instanz noch keine ID trägt (nicht persistiert)
   */
  default Long requireId() {
    Long value = id();
    if (value == null) {
      throw new IllegalStateException("Instanz ist noch nicht persistiert (keine ID)");
    }
    return value;
  }
}
