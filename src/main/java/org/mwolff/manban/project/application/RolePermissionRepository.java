package org.mwolff.manban.project.application;

import java.util.Set;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.ProjectRole;

/** Ausgehender Port für die feste Rollen-Rechte-Matrix ({@code role_permission}). */
public interface RolePermissionRepository {

  boolean isGranted(ProjectRole role, Permission permission);

  /** Alle einer Rolle gewährten Rechte (für die Matrix-Anzeige). */
  Set<Permission> grantedTo(ProjectRole role);
}
