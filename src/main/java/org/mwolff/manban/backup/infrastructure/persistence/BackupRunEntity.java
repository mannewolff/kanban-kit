package org.mwolff.manban.backup.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * JPA-Abbildung der Tabelle {@code backup_run} — ausschließlich für den Lesepfad.
 *
 * <p>Geschrieben wird die Tabelle vom Sicherungs-Container per {@code INSERT} (Plan #825 E2); die
 * Entity trägt deshalb keinen öffentlichen Konstruktor und keine Setter.
 *
 * <p>{@code kind} und {@code outcome} stehen als {@link String}: Die Spalten sind {@code varchar} +
 * {@code CHECK} (V40) und tragen klein geschriebene Werte, weil ein Shell-Skript sie einsetzt. Die
 * Abbildung auf die Java-Konstanten macht der {@link BackupRunRepositoryAdapter}.
 */
@Entity
@Table(name = "backup_run")
class BackupRunEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "kind", nullable = false)
  private String kind;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private @Nullable Instant finishedAt;

  @Column(name = "outcome", nullable = false)
  private String outcome;

  @Column(name = "detail")
  private @Nullable String detail;

  @Column(name = "bytes")
  private @Nullable Long bytes;

  protected BackupRunEntity() {
    // für JPA
  }

  String getKind() {
    return kind;
  }

  Instant getStartedAt() {
    return startedAt;
  }

  @Nullable Instant getFinishedAt() {
    return finishedAt;
  }

  String getOutcome() {
    return outcome;
  }

  @Nullable String getDetail() {
    return detail;
  }

  @Nullable Long getBytes() {
    return bytes;
  }
}
