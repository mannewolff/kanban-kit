package org.mwolff.manban;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.mwolff.manban.ArchitekturKlassen.PRODUKTIONSKLASSEN;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Aufrufer-Whitelists der rechtepruefungsfreien Ports (Issue #463, #1012, #1013, #1076).
 *
 * <p>Diese Ports pruefen bewusst keine Rechte; die Autorisierung liegt beim Aufrufer. Deshalb ist
 * ihr Aufruferkreis maschinell begrenzt: Ein neues Modul, das einen solchen Port injiziert, umginge
 * sonst stillschweigend die vorgelagerte Rechtepruefung.
 *
 * <p>Aus {@link ArchitectureTest} herausgeloest (Issue #1076): Die Regelgruppe steht fuer sich —
 * sie bindet keine Schichten, sondern einzelne Vertraege —, und beide Klassen blieben sonst ueber
 * der PMD-Schwelle {@code TooManyMethods} (30, {@code config/pmd/ruleset.xml}). Die
 * Produktionsklassen kommen aus {@link ArchitekturKlassen}, damit sie einmal importiert werden.
 */
class PortWhitelistArchitectureTest {

  // --- Aufrufer-Whitelist der rechtepruefungsfreien Schreib-Ports (Issue #463) -----------------
  // UserDisplayNameWriter und NextCardNumberWriter pruefen bewusst keine Rechte; die Autorisierung
  // liegt beim Aufrufer. Diese Zusicherung stand bisher nur im Javadoc — jedes weitere Modul, das
  // einen der Ports injiziert, umgeht damit stillschweigend die vorgelagerte Rechtepruefung.
  // Deshalb ist der Aufruferkreis hier maschinell auf genau ein autorisierendes Modul begrenzt
  // (plus das anbietende Modul selbst, in dem Port und Implementierung liegen). Ein neuer Aufrufer
  // ist kein Versehen mehr, sondern eine bewusste Aenderung dieser Regel.
  static final ArchRule USER_DISPLAY_NAME_WRITER_HAT_AUFRUFER_WHITELIST =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "org.mwolff.manban.auth.application..", "org.mwolff.manban.project.application..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching("org\\.mwolff\\.manban\\.auth\\.application\\.UserDisplayNameWriter")
          .as(
              "UserDisplayNameWriter prueft keine Rechte: Aufrufer nur project.application "
                  + "(MembershipService, MEMBER_REMOVE)");

  static final ArchRule NEXT_CARD_NUMBER_WRITER_HAT_AUFRUFER_WHITELIST =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "org.mwolff.manban.project.application..", "org.mwolff.manban.card.application..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching("org\\.mwolff\\.manban\\.project\\.application\\.NextCardNumberWriter")
          .as(
              "NextCardNumberWriter prueft keine Rechte: Aufrufer nur card.application "
                  + "(ProjectStartNumberService, PROJECT_EDIT)");

  // Dritter Port derselben Bauart (Issue #1012): Die Einlieferung einer interaktiven Sitzung setzt
  // den Erfassungsbeginn am Projekt-Aggregat. Die Autorisierung liegt beim Aufrufer —
  // NightRunService
  // .ingest prueft requireOwner als erste Anweisung, wie jeder Nachtlauf-Use-Case (Plan #718, A6).
  // Seit Issue #1013 gilt dieselbe Grenze fuer den Lese-Port derselben Spalte: Die Verbrauchs-
  // Auswertung gibt den Zeitpunkt mit, nachdem NightRunUsageService.period requireOwner geprueft
  // hat.
  static final ArchRule INTERACTIVE_USAGE_SINCE_PORTS_HABEN_AUFRUFER_WHITELIST =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "org.mwolff.manban.project.application..", "org.mwolff.manban.nightrun.application..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching(
              "org\\.mwolff\\.manban\\.project\\.application\\.InteractiveUsageSince"
                  + "(Writer|Reader)")
          .as(
              "InteractiveUsageSince-Ports pruefen keine Rechte: Aufrufer nur nightrun.application "
                  + "(NightRunService.ingest und NightRunUsageService.period, je requireOwner)");

  // Vierter Port derselben Bauart (Issue #1076, Plan #1072 E5): Der Plattform-Leitstand zeigt
  // Stoerungen nur aus teilnehmenden Projekten. Die Autorisierung liegt beim Aufrufer — der
  // Plattform-Leitstand ist bereits auf Plattform-Admins beschraenkt.
  static final ArchRule DASHBOARD_PARTICIPATION_READER_HAT_AUFRUFER_WHITELIST =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "org.mwolff.manban.project.application..", "org.mwolff.manban.nightrun.application..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching(
              "org\\.mwolff\\.manban\\.project\\.application\\.DashboardParticipationReader"
                  + "(\\$.*)?")
          .as(
              "DashboardParticipationReader prueft keine Rechte: Aufrufer nur project.application "
                  + "und nightrun.application");

  @Test
  void userDisplayNameWriterHatAufruferWhitelist() {
    USER_DISPLAY_NAME_WRITER_HAT_AUFRUFER_WHITELIST.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void nextCardNumberWriterHatAufruferWhitelist() {
    NEXT_CARD_NUMBER_WRITER_HAT_AUFRUFER_WHITELIST.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void interactiveUsageSincePortsHabenAufruferWhitelist() {
    INTERACTIVE_USAGE_SINCE_PORTS_HABEN_AUFRUFER_WHITELIST.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void dashboardParticipationReaderHatAufruferWhitelist() {
    DASHBOARD_PARTICIPATION_READER_HAT_AUFRUFER_WHITELIST.check(PRODUKTIONSKLASSEN);
  }
}
