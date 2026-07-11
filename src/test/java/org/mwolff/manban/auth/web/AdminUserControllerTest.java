package org.mwolff.manban.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminService;
import org.mwolff.manban.auth.application.AdminService.UserView;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Unit-Tests des Admin-Nutzer-Controllers (Service gemockt). */
class AdminUserControllerTest {

  private AdminService service;
  private AdminUserController controller;

  @BeforeEach
  void setUp() {
    service = mock(AdminService.class);
    controller = new AdminUserController(service);
  }

  @Test
  void list_delegatesToService() {
    // Given
    List<UserView> users = List.of(new UserView(1L, "a@b.de", "A", PlatformRole.USER, true));
    when(service.listUsers(3L)).thenReturn(users);

    // When
    List<UserView> result = controller.list(3L);

    // Then
    assertThat(result).isSameAs(users);
  }

  @Test
  void changeRole_delegatesToService() {
    // Given
    UserView user = new UserView(5L, "a@b.de", "A", PlatformRole.ADMIN, true);
    var request = new AdminUserController.ChangeRoleRequest(PlatformRole.ADMIN);
    when(service.changePlatformRole(3L, 5L, PlatformRole.ADMIN)).thenReturn(user);

    // When
    UserView result = controller.changeRole(3L, 5L, request);

    // Then
    assertThat(result).isSameAs(user);
  }
}
