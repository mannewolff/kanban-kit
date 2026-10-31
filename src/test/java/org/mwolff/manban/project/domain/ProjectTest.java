package org.mwolff.manban.project.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Die Umbenennung am Projekt-Aggregat. */
class ProjectTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant ERFASST_SEIT = Instant.parse("2026-09-16T10:00:00Z");

  @Test
  void withNameTauschtNurDenNamen() {
    Project vorher = new Project(7L, "Alt", 3L, ANGELEGT, null, false, null);

    Project nachher = vorher.withName("Neu");

    assertThat(nachher.name()).isEqualTo("Neu");
    assertThat(nachher.id()).isEqualTo(7L);
    assertThat(nachher.ownerUserId()).isEqualTo(3L);
    assertThat(nachher.createdAt()).isEqualTo(ANGELEGT);
  }

  /**
   * Der Erfassungsbeginn der interaktiven Sitzungen (Issue #1012) überlebt die Umbenennung. Ginge
   * er dabei verloren, unterschiede die Anzeige „nie erfasst" nicht mehr von „erfasst, dann
   * verdrängt" — und zwar ausgerechnet für Projekte, die jemand umbenannt hat.
   */
  @Test
  void withNameBehaeltDenErfassungsbeginn() {
    Project vorher = new Project(7L, "Alt", 3L, ANGELEGT, ERFASST_SEIT, false, null);

    assertThat(vorher.withName("Neu").interactiveUsageSince()).isEqualTo(ERFASST_SEIT);
  }

  /**
   * Die Teilnahme am Plattform-Leitstand (Issue #1076) überlebt die Umbenennung aus demselben Grund
   * wie der Erfassungsbeginn: Umbenennen ist kein Schaltvorgang.
   */
  @Test
  void withNameBehaeltDieTeilnahmeAmPlattformLeitstand() {
    Project vorher = new Project(7L, "Alt", 3L, ANGELEGT, null, true, null);

    assertThat(vorher.withName("Neu").dashboardParticipation()).isTrue();
  }

  /**
   * Das Monatsbudget (Issue #1964, Plan #1962) steht am Projekt; {@code null} heißt „kein Budget“.
   * Ein {@code withMonthlyBudget} gibt es bewusst nicht — geschrieben wird es nur über den
   * gezielten Weg {@code ProjectRepository#setMonthlyBudget}.
   */
  @Test
  void dasMonatsbudgetKommtAusDemKonstruktor() {
    Project mitBudget = new Project(7L, "P", 3L, ANGELEGT, null, false, new BigDecimal("100.00"));
    Project ohneBudget = new Project(7L, "P", 3L, ANGELEGT, null, false, null);

    assertThat(mitBudget.monthlyBudgetUsd()).isEqualByComparingTo("100.00");
    assertThat(ohneBudget.monthlyBudgetUsd()).isNull();
  }

  /** Umbenennen ist keine Budgetänderung — derselbe Grund wie bei der Teilnahme. */
  @Test
  void withNameBehaeltDasMonatsbudget() {
    Project vorher = new Project(7L, "Alt", 3L, ANGELEGT, null, false, new BigDecimal("100.00"));

    assertThat(vorher.withName("Neu").monthlyBudgetUsd()).isEqualByComparingTo("100.00");
  }

  /**
   * Die Strenge-Stufe des Kits (Issue #2014) überlebt die Umbenennung; der Konstruktor ohne Stufe
   * steht für ein Projekt, das nie eine gemeldet hat.
   */
  @Test
  void withNameBehaeltDieStrengeStufe() {
    Project vorher = new Project(7L, "Alt", 3L, ANGELEGT, null, false, null, "strict");

    assertThat(vorher.withName("Neu").kitStrictness()).isEqualTo("strict");
    assertThat(new Project(7L, "P", 3L, ANGELEGT, null, false, null).kitStrictness()).isNull();
  }
}
