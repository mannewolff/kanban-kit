package org.mwolff.manban.project.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.application.MembershipService;
import org.mwolff.manban.project.application.MembershipService.MemberView;
import org.mwolff.manban.project.domain.ProjectRole;

/** Unit-Tests des Mitglieder-Controllers (Service gemockt). */
class MemberControllerTest {

  private MembershipService service;
  private MemberController controller;

  private static MemberView member() {
    return new MemberView(5L, "a@b.de", "Alice", ProjectRole.MEMBER);
  }

  @BeforeEach
  void setUp() {
    service = mock(MembershipService.class);
    controller = new MemberController(service);
  }

  @Test
  void list_delegatesToService() {
    // Given
    List<MemberView> views = List.of(member());
    when(service.listMembers(3L, 2L)).thenReturn(views);

    // When
    List<MemberView> result = controller.list(3L, 2L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void changeRole_delegatesToService() {
    // Given
    MemberView view = member();
    var request = new MemberController.ChangeRoleRequest(ProjectRole.ADMIN);
    when(service.changeRole(3L, 2L, 5L, ProjectRole.ADMIN)).thenReturn(view);

    // When
    MemberView result = controller.changeRole(3L, 2L, 5L, request);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void remove_delegatesToService() {
    // When
    controller.remove(3L, 2L, 5L);

    // Then
    verify(service).removeMember(3L, 2L, 5L);
  }
}
