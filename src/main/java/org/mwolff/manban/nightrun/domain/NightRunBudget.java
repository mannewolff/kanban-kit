package org.mwolff.manban.nightrun.domain;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die Vorgaben, unter denen ein Kettenlauf angetreten ist (Issue #1112, Plan #1110 E2).
 *
 * <p>Zu einem Lauf gehört genau ein Budget; es steht deshalb in den Spalten von {@code night_run}
 * und nicht in einer eigenen Tabelle. Ein Lauf ohne gemeldete Vorgaben trägt gar kein Budget
 * ({@code null} am Lauf) statt eines Budgets aus lauter fehlenden Feldern — sonst brauchte jede
 * Anzeigestelle eine zweite Fallunterscheidung.
 *
 * <p><b>Jedes Feld darf fehlen.</b> {@code null} heißt „nicht angegeben" und nie 0 — eine 0
 * behauptete, der Lauf habe für diese Stufe keine Zeit bekommen.
 *
 * @param planMin Zeitvorgabe der Stufe {@link NightRunStage#PLAN} in Minuten
 * @param reviewMin Zeitvorgabe der Stufe {@link NightRunStage#REVIEW} in Minuten
 * @param paketeMin Zeitvorgabe der Stufe {@link NightRunStage#PAKETE} in Minuten
 * @param abdeckungMin Zeitvorgabe der Stufe {@link NightRunStage#ABDECKUNG} in Minuten
 * @param kostenUsd Kostenbudget des ganzen Laufs
 * @param origin Herkunft der Vorgaben; {@code null} heißt „nicht angegeben" (Plan #1110 E3)
 * @param defaultFields die Felder, die aus den Voreinstellungen kamen — nur bei {@link
 *     NightRunBudgetOrigin#DEFAULTED} gefüllt. Leer statt {@code null}, denn „kein Feld kam aus den
 *     Voreinstellungen" ist eine Aussage.
 */
public record NightRunBudget(
    @Nullable Integer planMin,
    @Nullable Integer reviewMin,
    @Nullable Integer paketeMin,
    @Nullable Integer abdeckungMin,
    @Nullable BigDecimal kostenUsd,
    @Nullable NightRunBudgetOrigin origin,
    List<String> defaultFields) {

  /** Die Feldliste wird beim Anlegen kopiert und unveränderlich gemacht. */
  public NightRunBudget {
    defaultFields = List.copyOf(defaultFields);
  }
}
