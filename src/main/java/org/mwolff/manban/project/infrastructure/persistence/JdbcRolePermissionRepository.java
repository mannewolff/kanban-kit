package org.mwolff.manban.project.infrastructure.persistence;

import org.mwolff.manban.project.application.RolePermissionRepository;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Liest die feste Rollen-Rechte-Matrix direkt aus {@code role_permission}/{@code permission}.
 * Die Matrix ist statisch geseedet (F2); ein späterer Admin-Editor bliebe reines UI.
 */
@Component
class JdbcRolePermissionRepository implements RolePermissionRepository {

    private final JdbcTemplate jdbc;

    JdbcRolePermissionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isGranted(ProjectRole role, Permission permission) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM role_permission rp JOIN permission p ON p.id = rp.permission_id "
                        + "WHERE rp.role = ? AND p.key = ?",
                Integer.class, role.name(), permission.name());
        return count != null && count > 0;
    }
}
