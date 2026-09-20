package org.mwolff.manban.project.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Die Umbenennung am Projekt-Aggregat. */
class ProjectTest {

  private static final Instant ANGELEGT = Instant.parse("2026-09-01T08:00:00Z");
  private static final Instant ERFASST_SEIT = Instant.parse("2026-09-16T10:00:00Z");

  @Test
  void withNameTauschtNurDenNamen() {
    Project vorher = new Project(7L, "Alt", 3L, ANGELEGT, null, false);

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
    Project vorher = new Project(7L, "Alt", 3L, ANGELEGT, ERFASST_SEIT, false);

    assertThat(vorher.withName("Neu").interactiveUsageSince()).isEqualTo(ERFASST_SEIT);
  }

  /**
   * Die Teilnahme am Plattform-Leitstand (Issue #1076) überlebt die Umbenennung aus demselben Grund
   * wie der Erfassungsbeginn: Umbenennen ist kein Schaltvorgang.
   */
  @Test
  void withNameBehaeltDieTeilnahmeAmPlattformLeitstand() {
    Project vorher = new Project(7L, "Alt", 3L, ANGELEGT, null, true);

    assertThat(vorher.withName("Neu").dashboardParticipation()).isTrue();
  }
}
