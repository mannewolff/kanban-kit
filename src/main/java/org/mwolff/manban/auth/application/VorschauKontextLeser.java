package org.mwolff.manban.auth.application;

import java.util.Optional;

/**
 * Port für den {@link VorschauKontext} der laufenden Anfrage (Issue #1918, Plan #1914 E6). Hält
 * Rechteprüfungen anderer Module frei von {@code auth.web}.
 */
@FunctionalInterface
public interface VorschauKontextLeser {

  /** Der Kontext der Vorschau; leer, wenn die Anfrage nicht in einer Vorschau läuft. */
  Optional<VorschauKontext> aktuell();
}
