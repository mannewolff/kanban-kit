package org.mwolff.manban.auth.infrastructure.persistence;

import java.util.List;
import org.mwolff.manban.auth.application.ProjectMembershipReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Liest Projekt-Mitgliedschaften direkt per SQL. Bis P1/P2 gibt es noch keine
 * Mitgliedschaften anzulegen; die Abfrage liefert dann eine leere Liste.
 */
@Component
class JdbcProjectMembershipReader implements ProjectMembershipReader {

    private final JdbcTemplate jdbc;

    JdbcProjectMembershipReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Membership> findByUserId(long userId) {
        return jdbc.query(
                "SELECT project_id, role FROM project_membership WHERE user_id = ? ORDER BY project_id",
                (rs, rowNum) -> new Membership(rs.getLong("project_id"), rs.getString("role")),
                userId);
    }
}
