package org.mwolff.manban.project.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Projekt — oberste Ebene (Tenant). Boards, Mitgliedschaften und alles Weitere hängen darunter.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param name Projektname
 * @param ownerUserId Ersteller/Eigentümer
 * @param createdAt Erstellzeitpunkt
 * @param interactiveUsageSince Startzeitpunkt der <b>ersten</b> gemeldeten interaktiven Sitzung;
 *     {@code null}, solange keine gemeldet wurde (Issue #1012, Plan #1007 E18). Der Wert steht ein
 *     für alle Mal — daran unterscheidet die Auswertung „nie erfasst" von „erfasst, dann
 *     verdrängt". Aus der ältesten vorhandenen Sitzung ließe sich die Grenze nicht ableiten, weil
 *     die mit dem Ringpuffer nach vorn wandert. Gesetzt wird er ausschließlich über {@code
 *     ProjectRepository#setInteractiveUsageSinceIfAbsent}, nie über {@code save}.
 * @param dashboardParticipation ob der Plattform-Leitstand Störungen dieses Projekts zeigt (Issue
 *     #1076, Plan #1072 E5). Vorgabe {@code false} für Bestand und neue Projekte; das Schalten
 *     selbst folgt in einem späteren Paket.
 * @param monthlyBudgetUsd Monatsbudget des Projekts in USD (Issue #1964, Plan #1962); {@code null}
 *     heißt „kein Budget“. Wie die Teilnahme gibt es dafür kein {@code with…}: Gesetzt wird es
 *     ausschließlich über {@code ProjectRepository#setMonthlyBudget}, nie über {@code save}.
 * @param kitStrictness zuletzt vom Kit gemeldete Strenge-Stufe ({@code light}, {@code normal} oder
 *     {@code strict}, Issue #2014); {@code null}, solange das Projekt nie eine gemeldet hat.
 *     Gesetzt wird sie ausschließlich über {@code ProjectRepository#setKitStrictness}, nie über
 *     {@code save}.
 */
public record Project(
    @Nullable Long id,
    String name,
    Long ownerUserId,
    Instant createdAt,
    @Nullable Instant interactiveUsageSince,
    boolean dashboardParticipation,
    @Nullable BigDecimal monthlyBudgetUsd,
    @Nullable String kitStrictness)
    implements Identifiable {

  /** Ein Projekt, das nie eine Strenge-Stufe gemeldet hat (Issue #2014). */
  public Project(
      @Nullable Long id,
      String name,
      Long ownerUserId,
      Instant createdAt,
      @Nullable Instant interactiveUsageSince,
      boolean dashboardParticipation,
      @Nullable BigDecimal monthlyBudgetUsd) {
    this(
        id,
        name,
        ownerUserId,
        createdAt,
        interactiveUsageSince,
        dashboardParticipation,
        monthlyBudgetUsd,
        null);
  }

  public Project withName(String newName) {
    return new Project(
        id,
        newName,
        ownerUserId,
        createdAt,
        interactiveUsageSince,
        dashboardParticipation,
        monthlyBudgetUsd,
        kitStrictness);
  }
}
