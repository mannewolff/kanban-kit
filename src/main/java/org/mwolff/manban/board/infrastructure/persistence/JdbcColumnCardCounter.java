package org.mwolff.manban.board.infrastructure.persistence;

import org.mwolff.manban.board.application.ColumnCardCounter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Zählt Karten je Spalte direkt per SQL (Karten-Aggregat gehört zu B2). */
@Component
class JdbcColumnCardCounter implements ColumnCardCounter {

    private final JdbcTemplate jdbc;

    JdbcColumnCardCounter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long countByColumnId(long columnId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM card WHERE column_id = ?", Long.class, columnId);
        return count == null ? 0 : count;
    }
}
