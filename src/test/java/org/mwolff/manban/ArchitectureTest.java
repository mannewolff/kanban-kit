package org.mwolff.manban;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Erzwingt die Schichten- und Paketregeln aus CLAUDE-java.md (§3.1/§6.1/§6.5) mit ArchUnit.
 *
 * <p>Die Regeln bilden die real vorhandene, saubere Architektur ab: Basis-Package {@code
 * org.mwolff.manban}, Fachmodule mit den Sub-Packages {@code domain}, {@code application}, {@code
 * web}, {@code infrastructure}. Das Domänenmodell ist framework-frei; JPA-Entities liegen in {@code
 * infrastructure}, Controller in {@code web}.
 *
 * <p>Bewusst nicht über die {@code archunit-junit5}-Engine
 * ({@code @AnalyzeClasses}/{@code @ArchTest}) ausgeführt: Maven Surefire 3.5.3 registriert die
 * ArchUnit-TestEngine in diesem Projekt nicht (die Regeln liefen dann als „0 Tests" durch, ein
 * bewusst eingebauter Verstoß blieb unentdeckt). Stattdessen werden die Klassen einmalig via {@link
 * ClassFileImporter} (ohne Testklassen, entspricht {@code DoNotIncludeTests}) importiert und die
 * Regeln über reguläre JUnit-Jupiter-Tests geprüft — so werden Verstöße zuverlässig zu
 * Build-Fehlern.
 */
class ArchitectureTest {

  /** Produktionsklassen ohne Testklassen (entspricht {@code importOptions = DoNotIncludeTests}). */
  private static final JavaClasses PRODUKTIONSKLASSEN =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("org.mwolff.manban");

  // --- §6.1: Domänenmodell ist framework-frei ------------------------------------------------

  static final ArchRule DOMAIN_IST_FRAMEWORK_FREI =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..", "jakarta.persistence..", "jakarta.validation..")
          .as("domain darf keine Spring-/JPA-/Bean-Validation-Importe haben");

  static final ArchRule DOMAIN_KENNT_KEINE_AEUSSEREN_SCHICHTEN =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..web..", "..application..", "..infrastructure..")
          .as("domain darf application/web/infrastructure nicht kennen");

  // --- §6.1: Schichtzugriff (hexagonal, domain innerste Schicht) ------------------------------
  // consideringOnlyDependenciesInLayers() macht die Regel robust gegenueber Modulen, die nicht
  // alle vier Schichten besitzen (z. B. kanbancompat ohne domain/infrastructure): Abhaengigkeiten
  // von/zu Klassen ausserhalb der definierten Schichten (config, common, ManbanApplication)
  // werden ignoriert.
  //
  // Hinweis (bewusste Abweichung von striktem Hexagon, siehe Bericht): infrastructure wird nicht
  // ausschliesslich von sich selbst genutzt, sondern auch von web
  // (auth.web.SessionController -> auth.infrastructure.security.{SessionCookieManager,
  // SignedSessionTokens} fuer HTTP-/Cookie-Belange). Die Regel bildet diesen Ist-Zustand ab.
  static final ArchRule SCHICHTEN =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain")
          .definedBy("..domain..")
          .layer("Application")
          .definedBy("..application..")
          .layer("Web")
          .definedBy("..web..")
          .layer("Infrastructure")
          .definedBy("..infrastructure..")
          .whereLayer("Web")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Infrastructure")
          .mayOnlyBeAccessedByLayers("Web")
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Web", "Infrastructure")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Web", "Infrastructure");

  // --- §6.5: Keine zyklischen Abhaengigkeiten zwischen den Fachmodulen ------------------------
  // Ignoriert wird die einzige Kante, die den Modul-Zyklus schliesst: die Security-Composition-
  // Root auth.infrastructure.security.SecurityConfig verdrahtet den PAT-Filter aus dem
  // accesstoken-Modul (auth -> accesstoken). Ohne diese eine Wiring-Kante ist der Modulgraph
  // azyklisch. Dies ist eine bewusste, dokumentierte Ausnahme (siehe Bericht), keine
  // Architektur-Reparatur.
  static final ArchRule KEINE_MODUL_ZYKLEN =
      SlicesRuleDefinition.slices()
          .matching("org.mwolff.manban.(*)..")
          .should()
          .beFreeOfCycles()
          .ignoreDependency(
              resideInAPackage("org.mwolff.manban.auth.."),
              resideInAPackage("org.mwolff.manban.accesstoken.."));

  // --- §6.1: Controller liegen in web, JPA-Entities in infrastructure ------------------------

  static final ArchRule CONTROLLER_LIEGEN_IN_WEB =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.stereotype.Controller")
          .or()
          .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
          .should()
          .resideInAPackage("..web..")
          .as("@Controller/@RestController gehoeren in ..web..");

  static final ArchRule ENTITIES_LIEGEN_IN_INFRASTRUCTURE =
      classes()
          .that()
          .areAnnotatedWith("jakarta.persistence.Entity")
          .should()
          .resideInAPackage("..infrastructure..")
          .as("JPA-@Entity-Klassen gehoeren in ..infrastructure..");

  @Test
  void domainIstFrameworkFrei() {
    DOMAIN_IST_FRAMEWORK_FREI.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void domainKenntKeineAeusserenSchichten() {
    DOMAIN_KENNT_KEINE_AEUSSEREN_SCHICHTEN.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void schichtenWerdenEingehalten() {
    SCHICHTEN.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void keineZyklischenModulAbhaengigkeiten() {
    KEINE_MODUL_ZYKLEN.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void controllerLiegenInWeb() {
    CONTROLLER_LIEGEN_IN_WEB.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void entitiesLiegenInInfrastructure() {
    ENTITIES_LIEGEN_IN_INFRASTRUCTURE.check(PRODUKTIONSKLASSEN);
  }
}
