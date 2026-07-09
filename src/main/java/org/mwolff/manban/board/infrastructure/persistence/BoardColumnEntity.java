package org.mwolff.manban.board.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA-Abbildung der Tabelle {@code board_column}. */
@Entity
@Table(name = "board_column")
class BoardColumnEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "wip_limit")
    private Integer wipLimit;

    protected BoardColumnEntity() {
        // für JPA
    }

    BoardColumnEntity(Long id, Long boardId, String name, int position, Integer wipLimit) {
        this.id = id;
        this.boardId = boardId;
        this.name = name;
        this.position = position;
        this.wipLimit = wipLimit;
    }

    Long getId() {
        return id;
    }

    Long getBoardId() {
        return boardId;
    }

    String getName() {
        return name;
    }

    int getPosition() {
        return position;
    }

    Integer getWipLimit() {
        return wipLimit;
    }
}
