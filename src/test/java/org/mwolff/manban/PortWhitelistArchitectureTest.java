package org.mwolff.manban;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.mwolff.manban.ArchitekturKlassen.PRODUKTIONSKLASSEN;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Aufrufer-Whitelists der rechtepruefungsfreien Ports (Issue #463, #1012, #1013, #1076, #1803,
 * #1804, #1964, #2014).
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

  // Weiterer Port derselben Bauart (Issue #1964, Plan #1962): Das Monatsbudget eines Projekts
  // liest das Nachtlauf-Modul ohne Rechtepruefung; die Autorisierung (Verbrauch des Projekts
  // sehen) liegt beim Aufrufer in nightrun.application.
  static final ArchRule MONTHLY_BUDGET_READER_HAT_AUFRUFER_WHITELIST =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "org.mwolff.manban.project.application..", "org.mwolff.manban.nightrun.application..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching(
              "org\\.mwolff\\.manban\\.project\\.application\\.MonthlyBudgetReader(\\$.*)?")
          .as(
              "MonthlyBudgetReader prueft keine Rechte: Aufrufer nur project.application "
                  + "und nightrun.application");

  // Weitere Ports derselben Bauart (Issue #2014): Die Strenge-Stufe, die das Kit im Header
  // X-Kit-Strictness meldet, legt ein Filter im accesstoken-Modul am Projekt ab, an das das Token
  // gebunden ist — die Bindung ist die ganze Autorisierung. Gelesen wird sie vom
  // Plattform-Leitstand
  // in nightrun.application, der auf Plattform-Admins beschraenkt ist.
  static final ArchRule KIT_STRICTNESS_PORTS_HABEN_AUFRUFER_WHITELIST =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "org.mwolff.manban.project.application..",
              "org.mwolff.manban.project.infrastructure..",
              "org.mwolff.manban.accesstoken.web.security..",
              "org.mwolff.manban.nightrun.application..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching(
              "org\\.mwolff\\.manban\\.project\\.application\\.KitStrictness(Writer|Reader)")
          .as(
              "KitStrictness-Ports pruefen keine Rechte: Aufrufer nur accesstoken.web.security"
                  + " (gebundenes Token) und nightrun.application (Plattform-Leitstand)");

  // Schreibend darf nur der Filter; der Leitstand liest nur.
  static final ArchRule KIT_STRICTNESS_WRITER_NUR_IM_FILTER =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "org.mwolff.manban.project.application..",
              "org.mwolff.manban.project.infrastructure..",
              "org.mwolff.manban.accesstoken.web.security..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching("org\\.mwolff\\.manban\\.project\\.application\\.KitStrictnessWriter")
          .as(
              "KitStrictnessWriter: Aufrufer nur der KitStrictnessFilter in"
                  + " accesstoken.web.security");

  // Fuenfter Port derselben Bauart (Issue #1803, Plan #1801 A2): Wer eine Code-Anforderung
  // einloest, bekommt ein Konto zurueck, ohne dass der Port irgendetwas prueft. Ausser dem Modul
  // auth selbst (Dienste in application, Adapter in infrastructure) darf ihn niemand benutzen —
  // sonst gaebe es einen Anmeldeweg an Grenze, Bremse und Kontopruefung vorbei.
  static final ArchRule LOGIN_CODE_REPOSITORY_HAT_AUFRUFER_WHITELIST =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "org.mwolff.manban.auth.application..", "org.mwolff.manban.auth.infrastructure..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching("org\\.mwolff\\.manban\\.auth\\.application\\.LoginCodeRepository")
          .as(
              "LoginCodeRepository prueft keine Rechte: Aufrufer nur auth.application und der"
                  + " Adapter in auth.infrastructure");

  // Sechster Port (Issue #1804, Plan #1801 A12): Der Mailer bekommt Code und Link im Klartext.
  // Wer ihn ausserhalb des Moduls auth aufruft, verschickte Anmeldecodes an der Grenze je Adresse
  // und am Ersetzen offener Anforderungen vorbei.
  static final ArchRule LOGIN_CODE_MAILER_HAT_AUFRUFER_WHITELIST =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "org.mwolff.manban.auth.application..", "org.mwolff.manban.auth.infrastructure..")
          .should()
          .dependOnClassesThat()
          .haveNameMatching("org\\.mwolff\\.manban\\.auth\\.application\\.LoginCodeMailer")
          .as(
              "LoginCodeMailer verschickt Anmeldecodes: Aufrufer nur auth.application und der"
                  + " Adapter in auth.infrastructure");

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

  @Test
  void monthlyBudgetReaderHatAufruferWhitelist() {
    MONTHLY_BUDGET_READER_HAT_AUFRUFER_WHITELIST.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void kitStrictnessPortsHabenAufruferWhitelist() {
    KIT_STRICTNESS_PORTS_HABEN_AUFRUFER_WHITELIST.check(PRODUKTIONSKLASSEN);
    KIT_STRICTNESS_WRITER_NUR_IM_FILTER.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void loginCodeRepositoryHatAufruferWhitelist() {
    LOGIN_CODE_REPOSITORY_HAT_AUFRUFER_WHITELIST.check(PRODUKTIONSKLASSEN);
  }

  @Test
  void loginCodeMailerHatAufruferWhitelist() {
    LOGIN_CODE_MAILER_HAT_AUFRUFER_WHITELIST.check(PRODUKTIONSKLASSEN);
  }
}
