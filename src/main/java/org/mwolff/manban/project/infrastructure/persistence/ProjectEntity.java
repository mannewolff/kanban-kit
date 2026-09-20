package org.mwolff.manban.project.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** JPA-Abbildung der Tabelle {@code project}. */
@Entity
@Table(name = "project")
class ProjectEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "owner_user_id", nullable = false)
  private Long ownerUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /**
   * Der Erfassungsbeginn der interaktiven Sitzungen (Issue #1012) — <b>nur lesbar</b>. {@code
   * insertable}/{@code updatable} stehen auf {@code false}, damit der allgemeine Schreibweg ({@code
   * save}) ihn weder setzen noch überschreiben kann: Geschrieben wird er ausschließlich über den
   * eng geschnittenen Port-Aufruf {@code setInteractiveUsageSinceIfAbsent}, dessen „falls noch
   * leer" in der {@code WHERE}-Bedingung des {@code UPDATE} steht und damit auch bei zwei
   * gleichzeitigen Meldungen trägt.
   */
  @Column(name = "interactive_usage_since", insertable = false, updatable = false)
  private @Nullable Instant interactiveUsageSince;

  /**
   * Ob der Plattform-Leitstand Störungen dieses Projekts zeigt (Issue #1076) — <b>nur lesbar</b>,
   * dieselbe Begründung wie bei {@link #interactiveUsageSince}: Der allgemeine Schreibweg ({@code
   * save}) darf die Teilnahme nicht mit einem veralteten Wert überschreiben, etwa beim Umbenennen.
   * Geschrieben wird sie über einen eigenen, engen Schreibweg (folgt in einem späteren Paket).
   */
  @Column(name = "dashboard_participation", insertable = false, updatable = false)
  private boolean dashboardParticipation;

  protected ProjectEntity() {
    // für JPA
  }

  ProjectEntity(@Nullable Long id, String name, Long ownerUserId, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.ownerUserId = ownerUserId;
    this.createdAt = createdAt;
  }

  @Nullable Long getId() {
    return id;
  }

  String getName() {
    return name;
  }

  Long getOwnerUserId() {
    return ownerUserId;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  @Nullable Instant getInteractiveUsageSince() {
    return interactiveUsageSince;
  }

  boolean isDashboardParticipation() {
    return dashboardParticipation;
  }
}
