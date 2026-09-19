package org.mwolff.manban;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Die einmal importierten Produktionsklassen für alle ArchUnit-Prüfungen.
 *
 * <p>Der Import liest den gesamten Klassenpfad unterhalb von {@code org.mwolff.manban} und ist der
 * teure Teil einer ArchUnit-Prüfung. Er steht deshalb hier und nicht in einer der Testklassen: Seit
 * die Regeln auf {@link ArchitectureTest} und {@link PortWhitelistArchitectureTest} verteilt sind
 * (Issue #1076), würde er sonst je Klasse einmal laufen.
 *
 * <p>Testklassen bleiben ausgeschlossen (entspricht {@code importOptions = DoNotIncludeTests}) —
 * die Regeln beschreiben die Architektur des Produktionscodes, nicht die der Tests.
 */
final class ArchitekturKlassen {

  /** Produktionsklassen ohne Testklassen. */
  // PMD.LooseCoupling: JavaClasses ist der konkrete ArchUnit-API-Typ (kein Interface verfügbar).
  @SuppressWarnings("PMD.LooseCoupling")
  static final JavaClasses PRODUKTIONSKLASSEN =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("org.mwolff.manban");

  private ArchitekturKlassen() {
    // Nur Träger der importierten Klassen; nicht instanziierbar.
  }
}
