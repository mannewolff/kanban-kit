package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.ProjectRole;

/** Verhaltenstests der Rollen-Rechte-Matrix (Mockito am RolePermissionRepository-Port). */
class RoleMatrixServiceTest {

  private RolePermissionRepository rolePermissions;
  private RoleMatrixService service;

  @BeforeEach
  void setUp() {
    rolePermissions = mock(RolePermissionRepository.class);
    service = new RoleMatrixService(rolePermissions);
    when(rolePermissions.grantedTo(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of());
  }

  @Test
  void matrix_listsRolesInDisplayOrder() {
    // When
    RoleMatrixService.RoleMatrixView view = service.matrix();

    // Then
    assertThat(view.roles()).containsExactly("VIEWER", "MEMBER", "ADMIN", "OWNER");
  }

  @Test
  void matrix_listsEveryPermission() {
    // When
    RoleMatrixService.RoleMatrixView view = service.matrix();

    // Then
    assertThat(view.permissions()).hasSize(Permission.values().length);
  }

  @Test
  void matrix_derivesResourceAndOperationFromPermissionKey() {
    // When
    RoleMatrixService.RoleMatrixView view = service.matrix();

    // Then
    assertThat(view.permissions())
        .contains(new RoleMatrixService.PermissionView("BOARD_CREATE", "BOARD", "CREATE"));
  }

  @Test
  void matrix_reflectsGrantsPerRoleFromRepository() {
    // Given
    when(rolePermissions.grantedTo(ProjectRole.OWNER)).thenReturn(Set.of(Permission.BOARD_CREATE));

    // When
    RoleMatrixService.RoleMatrixView view = service.matrix();

    // Then
    assertThat(view.grants().get("OWNER")).containsExactly("BOARD_CREATE");
  }

  @Test
  void matrix_leavesGrantsEmpty_whenRoleHasNoPermissions() {
    // When
    RoleMatrixService.RoleMatrixView view = service.matrix();

    // Then
    assertThat(view.grants().get("VIEWER")).isEmpty();
  }
}
