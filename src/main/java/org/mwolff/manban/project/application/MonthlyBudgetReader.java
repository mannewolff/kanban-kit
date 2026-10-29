package org.mwolff.manban.project.application;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Lesender Port für das Monatsbudget eines Projekts ({@code project.monthly_budget_usd}, Issue
 * #1964, Plan #1962). Das Nachtlauf-Modul rechnet daraus den Stand des laufenden Monats.
 *
 * <p><strong>Sicherheitshinweis:</strong> Dieser Port prüft <strong>keine</strong> Rechte. Der
 * Aufrufer <em>muss</em> vorher autorisiert haben — wer das Budget sieht, sieht den Verbrauch des
 * Projekts.
 *
 * <p>Der Aufruferkreis ist deshalb wie bei {@link InteractiveUsageSinceReader} maschinell begrenzt:
 * {@code PortWhitelistArchitectureTest.MONTHLY_BUDGET_READER_HAT_AUFRUFER_WHITELIST} lässt
 * ausschließlich {@code project.application} (Port und Implementierung) sowie {@code
 * nightrun.application} (autorisierender Aufrufer) zu.
 */
@FunctionalInterface
public interface MonthlyBudgetReader {

  /**
   * Das Monatsbudget des Projekts in USD; leer, solange keines festgelegt ist — und leer für ein
   * unbekanntes Projekt.
   */
  Optional<BigDecimal> monthlyBudget(long projectId);
}
