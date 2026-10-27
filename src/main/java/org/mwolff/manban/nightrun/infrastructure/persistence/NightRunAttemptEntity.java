package org.mwolff.manban.nightrun.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * JPA-Abbildung der Tabelle {@code night_run_attempt} (Issue #1944, Plan #1905 E4) — ausschließlich
 * für den Lesepfad.
 *
 * <p>Geschrieben werden die Zeilen zusammen mit ihrem Arbeitspaket über JDBC (siehe {@link
 * NightRunRepositoryAdapter}); die Entity trägt deshalb keinen öffentlichen Konstruktor. Die
 * Vorgänge hängen aus demselben Grund wie die Stufen ({@link NightRunItemStageEntity}) als eigene
 * Zeilen am Paket und nicht als {@code @OneToMany}.
 */
@Entity
@Table(name = "night_run_attempt")
class NightRunAttemptEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "night_run_item_id", nullable = false)
  private Long nightRunItemId;

  /** Die Reihenfolge der Ausführung innerhalb des Pakets, wie gemeldet. */
  @Column(name = "position", nullable = false)
  private int position;

  /** Die Stufe; wie an {@link NightRunItemStageEntity} als Zeichenkette abgebildet. */
  @Column(name = "stage", nullable = false)
  private String stage;

  @Column(name = "card_number", nullable = false)
  private int cardNumber;

  @Column(name = "model")
  private @Nullable String model;

  @Column(name = "effort")
  private @Nullable String effort;

  @Column(name = "succeeded", nullable = false)
  private boolean succeeded;

  /** Die sechs Verbrauchsspalten; {@code null}, wenn keine davon gesetzt ist. */
  @Embedded private @Nullable VerbrauchEmbeddable verbrauch;

  /**
   * Die erste Array-Spalte im Schema (Plan #1905 E4): gelesen als {@code Integer[]}, geschrieben
   * über JDBC mit {@code Types.ARRAY}. Die Spalte ist {@code NOT NULL}; eine leere Liste ist ein
   * leeres Array.
   */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "written_card_numbers", nullable = false)
  private Integer[] writtenCardNumbers;

  /** Der Wert {@code complete} des Laufs aus der Meldung, die diesen Vorgang schrieb. */
  @Column(name = "run_complete", nullable = false)
  private boolean runComplete;

  protected NightRunAttemptEntity() {
    // für JPA
  }

  Long getNightRunItemId() {
    return nightRunItemId;
  }

  int getPosition() {
    return position;
  }

  String getStage() {
    return stage;
  }

  int getCardNumber() {
    return cardNumber;
  }

  @Nullable String getModel() {
    return model;
  }

  @Nullable String getEffort() {
    return effort;
  }

  boolean isSucceeded() {
    return succeeded;
  }

  VerbrauchEmbeddable getVerbrauch() {
    return verbrauch == null ? new VerbrauchEmbeddable() : verbrauch;
  }

  /** Eine Kopie, damit kein Leser das Feld der verwalteten Entity verändert (EI_EXPOSE_REP). */
  Integer[] getWrittenCardNumbers() {
    return writtenCardNumbers.clone();
  }

  boolean isRunComplete() {
    return runComplete;
  }
}
