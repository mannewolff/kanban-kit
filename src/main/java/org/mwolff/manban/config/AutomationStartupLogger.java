package org.mwolff.manban.config;

import java.util.Comparator;
import java.util.List;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.mwolff.manban.common.automation.AutomationStatusContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Schreibt nach dem Start je Automatik eine Zeile ins Protokoll (Issue #907, fachlich #841).
 *
 * <p>Der Betreiber soll ablesen können, welche Automatik in <em>diesem</em> Lauf arbeitet. Die
 * abgeschalteten stehen ausdrücklich mit dabei: Fehlte eine Zeile, wäre „fehlt" nicht von „läuft
 * nicht" zu unterscheiden, und ein vergessener Schalter sähe aus wie ein Ausfall.
 *
 * <p><b>Eine Zeile je Automatik, sortiert nach Bezeichnung.</b> Spring sichert die Reihenfolge
 * einer injizierten {@code List} nicht zu — ohne Sortierung wechselte die Reihenfolge zwischen
 * Läufen, und zwei Protokolle wären nicht vergleichbar. Eine Sammelzeile wäre im Log umgebrochen
 * und je Automatik nicht greppbar.
 *
 * <p>Diese Klasse kennt nur den Port aus {@code common.automation} — keinen Domänentyp, kein
 * Repository, keine fremde Property-Klasse. Sie nennt auch keinen Property-Namen: Was der Betreiber
 * bedient, ist die Umgebungsvariable, und die bringt der meldende Zustand selbst mit.
 */
@Component
class AutomationStartupLogger {

  private static final Logger log = LoggerFactory.getLogger(AutomationStartupLogger.class);

  private final List<AutomationStatusContributor> contributors;

  AutomationStartupLogger(List<AutomationStatusContributor> contributors) {
    this.contributors = List.copyOf(contributors);
  }

  /**
   * Meldet den Stand, sobald die Anwendung bereit ist.
   *
   * <p>Eine leere Liste ist kein Fehler: Solange kein Modul beiträgt, schweigt der Sammler.
   */
  @EventListener(ApplicationReadyEvent.class)
  void protokolliereAutomatiken() {
    contributors.stream()
        .flatMap(contributor -> contributor.statuses().stream())
        .sorted(Comparator.comparing(AutomationStatus::bezeichnung))
        .forEach(status -> log.info("{}", zeile(status)));
  }

  private static String zeile(AutomationStatus status) {
    String lage = status.eingeschaltet() ? "eingeschaltet" : "abgeschaltet";
    String detail = status.detail().isBlank() ? "" : " — " + status.detail();
    return "Automatik \"%s\": %s%s; Schalter %s"
        .formatted(status.bezeichnung(), lage, detail, status.schalterVariable());
  }
}
