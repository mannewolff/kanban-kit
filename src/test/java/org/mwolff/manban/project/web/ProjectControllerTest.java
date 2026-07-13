package org.mwolff.manban.project.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.application.MembershipService;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.ProjectView;
import org.mwolff.manban.project.domain.ProjectRole;

/** Unit-Tests des Projekt-Controllers (Service gemockt). */
class ProjectControllerTest {

  private ProjectService service;
  private MembershipService memberships;
  private ProjectController controller;

  private static ProjectView project() {
    return new ProjectView(1L, "Project", ProjectRole.OWNER, Instant.EPOCH);
  }

  @BeforeEach
  void setUp() {
    service = mock(ProjectService.class);
    memberships = mock(MembershipService.class);
    controller = new ProjectController(service, memberships);
  }

  @Test
  void create_delegatesToService() {
    // Given
    ProjectView view = project();
    var request = new ProjectController.CreateProjectRequest("Project", "owner@b.de");
    when(service.create(3L, "Project", "owner@b.de")).thenReturn(view);

    // When
    ProjectView result = controller.create(3L, request);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void list_delegatesToService() {
    // Given
    List<ProjectView> views = List.of(project());
    when(service.list(3L)).thenReturn(views);

    // When
    List<ProjectView> result = controller.list(3L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void rename_delegatesToService() {
    // Given
    ProjectView view = project();
    when(service.rename(3L, 5L, "New")).thenReturn(view);

    // When
    ProjectView result = controller.rename(3L, 5L, new ProjectController.ProjectRequest("New"));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void delete_delegatesToService() {
    // When
    controller.delete(3L, 5L);

    // Then
    verify(service).delete(3L, 5L);
  }

  @Test
  void transferOwner_delegatesToMembershipService() {
    // When
    controller.transferOwner(3L, 5L, new ProjectController.TransferOwnerRequest(8L));

    // Then
    verify(memberships).transferOwnership(3L, 5L, 8L);
  }
}
