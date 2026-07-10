package org.mwolff.manban.project.application;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Liefert die Rollen-Rechte-Matrix als eine Quelle der Wahrheit für die {@code /roles}-Ansicht
 * (und die konfigurierbaren Rollen in 2.0). Rollen, Rechte (mit abgeleiteter Ressource/Operation)
 * und die Grants je Rolle stammen direkt aus dem geseedeten {@code role_permission}.
 */
@Service
public class RoleMatrixService {

    /** Anzeige-Reihenfolge der eingebauten Rollen (von wenig zu viel Rechten). */
    private static final List<ProjectRole> DISPLAY_ORDER =
            List.of(ProjectRole.VIEWER, ProjectRole.MEMBER, ProjectRole.ADMIN, ProjectRole.OWNER);

    private final RolePermissionRepository rolePermissions;

    public RoleMatrixService(RolePermissionRepository rolePermissions) {
        this.rolePermissions = rolePermissions;
    }

    @Transactional(readOnly = true)
    public RoleMatrixView matrix() {
        List<String> roles = DISPLAY_ORDER.stream().map(Enum::name).toList();
        List<PermissionView> permissions = Arrays.stream(Permission.values())
                .map(RoleMatrixService::toView)
                .toList();

        Map<String, List<String>> grants = new LinkedHashMap<>();
        for (ProjectRole role : DISPLAY_ORDER) {
            Set<Permission> granted = rolePermissions.grantedTo(role);
            // Stabile Reihenfolge über die Enum-Reihenfolge (nach Ressource gruppiert).
            List<String> keys = Arrays.stream(Permission.values())
                    .filter(granted::contains)
                    .map(Enum::name)
                    .toList();
            grants.put(role.name(), keys);
        }
        return new RoleMatrixView(roles, permissions, grants);
    }

    /** Leitet Ressource + Operation aus dem Permission-Key ab ({@code <RESSOURCE>_<OPERATION>}). */
    private static PermissionView toView(Permission permission) {
        String[] parts = permission.name().split("_", 2);
        return new PermissionView(permission.name(), parts[0], parts.length > 1 ? parts[1] : "");
    }

    public record RoleMatrixView(List<String> roles, List<PermissionView> permissions,
                                 Map<String, List<String>> grants) {
    }

    public record PermissionView(String key, String resource, String operation) {
    }
}
