package org.mwolff.manban.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.domain.AdminVorschau;
import org.mwolff.manban.auth.domain.VorschauEndgrund;

/** JPA-Abbildung der Tabelle {@code admin_vorschau} (Issue #1917, Plan #1914 E2, E12). */
@Entity
@Table(name = "admin_vorschau")
class AdminVorschauEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "kennung", nullable = false)
  private String kennung;

  @Column(name = "geheimnis_hash", nullable = false)
  private String geheimnisHash;

  @Column(name = "admin_id")
  private @Nullable Long adminId;

  @Column(name = "admin_name", nullable = false)
  private String adminName;

  @Column(name = "ziel_id", nullable = false)
  private long zielId;

  @Column(name = "sitzungs_generation", nullable = false)
  private long sitzungsGeneration;

  @Column(name = "gestartet_am", nullable = false)
  private Instant gestartetAm;

  @Column(name = "laeuft_ab_am", nullable = false)
  private Instant laeuftAbAm;

  @Column(name = "beendet_am")
  private @Nullable Instant beendetAm;

  @Enumerated(EnumType.STRING)
  @Column(name = "endgrund")
  private @Nullable VorschauEndgrund endgrund;

  protected AdminVorschauEntity() {
    // für JPA
  }

  AdminVorschauEntity(AdminVorschau vorschau) {
    this.id = vorschau.id();
    this.kennung = vorschau.kennung();
    this.geheimnisHash = vorschau.geheimnisHash();
    this.adminId = vorschau.adminId();
    this.adminName = vorschau.adminName();
    this.zielId = vorschau.zielId();
    this.sitzungsGeneration = vorschau.sitzungsGeneration();
    this.gestartetAm = vorschau.gestartetAm();
    this.laeuftAbAm = vorschau.laeuftAbAm();
    this.beendetAm = vorschau.beendetAm();
    this.endgrund = vorschau.endgrund();
  }

  AdminVorschau toDomain() {
    return new AdminVorschau(
        id,
        kennung,
        geheimnisHash,
        adminId,
        adminName,
        zielId,
        sitzungsGeneration,
        gestartetAm,
        laeuftAbAm,
        beendetAm,
        endgrund);
  }
}
