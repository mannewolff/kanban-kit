package org.mwolff.manban.card.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.Label;

/** JPA-Abbildung der Tabelle {@code label}. */
@Entity
@Table(name = "label")
class LabelEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "board_id", nullable = false)
  private Long boardId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "color", nullable = false)
  private String color;

  @Column(name = "count_on_epic_tile", nullable = false)
  private boolean countOnEpicTile;

  protected LabelEntity() {
    // für JPA
  }

  LabelEntity(Label l) {
    this.id = l.id();
    this.boardId = l.boardId();
    this.name = l.name();
    this.color = l.color();
    this.countOnEpicTile = l.countOnEpicTile();
  }

  @Nullable Long getId() {
    return id;
  }

  Long getBoardId() {
    return boardId;
  }

  String getName() {
    return name;
  }

  String getColor() {
    return color;
  }

  boolean isCountOnEpicTile() {
    return countOnEpicTile;
  }
}
