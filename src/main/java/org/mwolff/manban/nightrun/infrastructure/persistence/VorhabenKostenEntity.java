package org.mwolff.manban.nightrun.infrastructure.persistence;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository.VorhabenKosten;

/**
 * JPA-Abbildung der Tabelle {@code vorhaben_kosten} (Issue #2004, Plan #1990 E8 bis E10): eine
 * Zeile je Vorhaben, Schlüssel ist die ID der Vorhaben-Karte.
 *
 * <p>Die beiden Voraussagen sind dasselbe {@link VoraussageEmbeddable} mit den Präfixen {@code
 * anforderung_} und {@code pakete_}. Die Kostenkarten liegen wie {@code written_card_numbers} an
 * {@link NightRunAttemptEntity} als {@code integer[]}.
 */
@Entity
@Table(name = "vorhaben_kosten")
class VorhabenKostenEntity {

  @Id
  @Column(name = "epic_id", nullable = false)
  private Long epicId;

  @Column(name = "project_id", nullable = false)
  private long projectId;

  @Embedded
  @AttributeOverride(name = "stand", column = @Column(name = "anforderung_stand"))
  @AttributeOverride(name = "art", column = @Column(name = "anforderung_art"))
  @AttributeOverride(name = "typischUsd", column = @Column(name = "anforderung_typisch_usd"))
  @AttributeOverride(name = "untenUsd", column = @Column(name = "anforderung_unten_usd"))
  @AttributeOverride(name = "obenUsd", column = @Column(name = "anforderung_oben_usd"))
  @AttributeOverride(
      name = "festgehaltenAt",
      column = @Column(name = "anforderung_festgehalten_at"))
  private @Nullable VoraussageEmbeddable anforderung;

  @Embedded
  @AttributeOverride(name = "stand", column = @Column(name = "pakete_stand"))
  @AttributeOverride(name = "art", column = @Column(name = "pakete_art"))
  @AttributeOverride(name = "typischUsd", column = @Column(name = "pakete_typisch_usd"))
  @AttributeOverride(name = "untenUsd", column = @Column(name = "pakete_unten_usd"))
  @AttributeOverride(name = "obenUsd", column = @Column(name = "pakete_oben_usd"))
  @AttributeOverride(name = "festgehaltenAt", column = @Column(name = "pakete_festgehalten_at"))
  private @Nullable VoraussageEmbeddable pakete;

  @Column(name = "abgeschlossen_at")
  private @Nullable Instant abgeschlossenAt;

  @Column(name = "tatsaechlich_usd")
  private @Nullable BigDecimal tatsaechlichUsd;

  @Column(name = "tatsaechlich_unvollstaendig", nullable = false)
  private boolean tatsaechlichUnvollstaendig;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "kosten_karten", nullable = false)
  private Integer[] kostenKarten;

  protected VorhabenKostenEntity() {
    // für JPA
  }

  VorhabenKostenEntity(VorhabenKosten kosten) {
    this.epicId = kosten.epicId();
    this.projectId = kosten.projectId();
    this.anforderung = VoraussageEmbeddable.aus(kosten.anforderung());
    this.pakete = VoraussageEmbeddable.aus(kosten.pakete());
    this.abgeschlossenAt = kosten.abgeschlossenAt();
    this.tatsaechlichUsd = kosten.tatsaechlichUsd();
    this.tatsaechlichUnvollstaendig = kosten.tatsaechlichUnvollstaendig();
    this.kostenKarten = kosten.kostenKarten().toArray(Integer[]::new);
  }

  VorhabenKosten toDomain() {
    return new VorhabenKosten(
        epicId,
        projectId,
        anforderung == null ? null : anforderung.toDomain(),
        pakete == null ? null : pakete.toDomain(),
        abgeschlossenAt,
        tatsaechlichUsd,
        tatsaechlichUnvollstaendig,
        Arrays.asList(kostenKarten));
  }
}
