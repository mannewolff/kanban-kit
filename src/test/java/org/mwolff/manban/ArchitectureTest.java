package org.mwolff.manban;

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
  // PMD.LooseCoupling: JavaClasses ist der konkrete ArchUnit-API-Typ (kein Interface verfügbar).
  @SuppressWarnings("PMD.LooseCoupling")
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

  // --- Modul-Grenze: auth ist unabhaengig vom project-Modul (Port-Inversion, Issue #0099) ------
  // Die Auto-Freigabe eingeladener Nutzer haengt an einem im auth-Modul definierten Port
  // (RegistrationApprovalPolicy), den das project-Modul implementiert. auth darf project daher
  // nicht kennen.
  static final ArchRule AUTH_HAENGT_NICHT_VON_PROJECT_AB =
      noClasses()
          .that()
          .resideInAPackage("org.mwolff.manban.auth..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.mwolff.manban.project..")
          .as("auth darf das project-Modul nicht kennen (Port-Inversion)");

  // --- Modul-Grenze: auth ist unabhaengig vom accesstoken-Modul (Issue #438) ------------------
  // Die Security-Filterkette wird nicht mehr im auth-Modul, sondern in der anwendungsweiten
  // Composition-Root org.mwolff.manban.config.SecurityConfig verdrahtet. Damit verschwindet die
  // Kante auth -> accesstoken, die zusammen mit accesstoken -> board -> project -> auth den
  // Modulzyklus schloss.
  static final ArchRule AUTH_HAENGT_NICHT_VON_ACCESSTOKEN_AB =
      noClasses()
          .that()
          .resideInAPackage("org.mwolff.manban.auth..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.mwolff.manban.accesstoken..")
          .as(
              "auth darf das accesstoken-Modul nicht kennen (Wiring gehoert in die "
                  + "Composition-Root)");

  // --- Modul-Grenze: card-Fassade (Issue #458) ------------------------------------------------
  // Das Kartenmodell und die Karten-/Label-Ports sind modulintern. Fremde Module gehen ueber die
  // fachliche card.application-Fassade (CardService/LabelService) — sonst haengt jede fremde
  // Rechtepruefung am Aggregat und an dessen Persistenz-Ports statt an einem Use-Case.
  static final ArchRule CARD_DOMAIN_IST_MODULINTERN =
      noClasses()
          .that()
          .resideOutsideOfPackage("org.mwolff.manban.card..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.mwolff.manban.card.domain..")
          .as("card.domain ist modulintern (Zugriff nur ueber die card.application-Fassade)");

  static final ArchRule CARD_REPOSITORIES_SIND_MODULINTERN =
      noClasses()
          .that()
          .resideOutsideOfPackage("org.mwolff.manban.card..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching("org\\.mwolff\\.manban\\.card\\.application\\..*Repository")
          .as("card-Repository-Ports sind modulintern (nur ueber CardService/LabelService)");

  // --- §6.1: Schichtzugriff (hexagonal, domain innerste Schicht) ------------------------------
  // consideringOnlyDependenciesInLayers() macht die Regel robust gegenueber Modulen, die nicht
  // alle vier Schichten besitzen (z. B. kanbancompat ohne domain/infrastructure): Abhaengigkeiten
  // von/zu Klassen ausserhalb der definierten Schichten (config, common, ManbanApplication)
  // werden ignoriert. Insbesondere die Composition-Root config.SecurityConfig darf die Adapter
  // beider Seiten verdrahten, ohne die Schichtenregel zu verletzen.
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
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Web", "Infrastructure")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Web", "Infrastructure");

  // --- §6.1: web ist ein eingehender Adapter und kennt keine Infrastruktur --------------------
  // Redundant zur Schichtenregel, aber mit praeziser Fehlermeldung: Ein Web-Adapter, der einen
  // Infrastruktur-Typ direkt verwendet, bindet die HTTP-Schicht an ein Persistenz-/Krypto-Detail.
  // Der Weg fuehrt ueber einen Application-Port (z. B. auth.application.SessionTokens).
  static final ArchRule WEB_KENNT_KEINE_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("..web..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure..")
          .as("web darf infrastructure nicht kennen (Zugriff nur ueber Application-Ports)");

  // --- §6.5: Keine zyklischen Abhaengigkeiten zwischen den Fachmodulen ------------------------
  // Ohne ignoreDependency (Issue #438): Der Modulgraph ist vollstaendig zyklusfrei, seit die
  // Security-Verdrahtung in der Composition-Root ausserhalb der Fachmodule liegt.
  static final ArchRule KEINE_MODUL_ZYKLEN =
      SlicesRuleDefinition.slices().matching("org.mwolff.manban.(*)..").should().beFreeOfCycles();

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
  void authHaengtNichtVonProjectAb() {
    AUTH_HAENGT_NICHT_VON_PROJECT_AB.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void authHaengtNichtVonAccesstokenAb() {
    AUTH_HAENGT_NICHT_VON_ACCESSTOKEN_AB.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void cardDomainIstModulintern() {
    CARD_DOMAIN_IST_MODULINTERN.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void cardRepositoriesSindModulintern() {
    CARD_REPOSITORIES_SIND_MODULINTERN.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void schichtenWerdenEingehalten() {
    SCHICHTEN.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void webKenntKeineInfrastructure() {
    WEB_KENNT_KEINE_INFRASTRUCTURE.check(PRODUKTIONSKLASSEN);
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
