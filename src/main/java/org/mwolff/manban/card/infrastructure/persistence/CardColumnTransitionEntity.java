package org.mwolff.manban.card.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** JPA-Abbildung der Tabelle {@code card_column_transition} (Spaltenaufenthalte einer Karte). */
@Entity
@Table(name = "card_column_transition")
class CardColumnTransitionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "card_id", nullable = false)
  private Long cardId;

  @Column(name = "column_id")
  private @Nullable Long columnId;

  @Column(name = "column_name", nullable = false)
  private String columnName;

  @Column(name = "entered_at", nullable = false)
  private Instant enteredAt;

  @Column(name = "left_at")
  private @Nullable Instant leftAt;

  @Column(name = "duration_seconds")
  private @Nullable Long durationSeconds;

  protected CardColumnTransitionEntity() {
    // für JPA
  }

  CardColumnTransitionEntity(long cardId, long columnId, String columnName, Instant enteredAt) {
    this.cardId = cardId;
    this.columnId = columnId;
    this.columnName = columnName;
    this.enteredAt = enteredAt;
  }

  /** Schließt die offene Zeile: Austrittszeitpunkt und Verweildauer in Sekunden. */
  void close(Instant leftAt, long durationSeconds) {
    this.leftAt = leftAt;
    this.durationSeconds = durationSeconds;
  }

  @Nullable Long getId() {
    return id;
  }

  Long getCardId() {
    return cardId;
  }

  @Nullable Long getColumnId() {
    return columnId;
  }

  String getColumnName() {
    return columnName;
  }

  Instant getEnteredAt() {
    return enteredAt;
  }

  @Nullable Instant getLeftAt() {
    return leftAt;
  }

  @Nullable Long getDurationSeconds() {
    return durationSeconds;
  }
}
