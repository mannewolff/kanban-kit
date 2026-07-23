package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/** Autorisierung des Live-Ideen-Pool-Abos (Mockito am PermissionChecker). */
class ProjectIdeaEventServiceTest {

  private static final long USER = 7L;
  private static final long PROJECT_ID = 5L;

  private PermissionChecker permissions;
  private ProjectIdeaEventService service;

  @BeforeEach
  void setUp() {
    permissions = mock(PermissionChecker.class);
    service = new ProjectIdeaEventService(permissions);
  }

  @Test
  void requireSubscribable_passesForMember() {
    assertThatNoException().isThrownBy(() -> service.requireSubscribable(USER, PROJECT_ID));

    verify(permissions).requireMembership(USER, PROJECT_ID);
  }

  @Test
  void requireSubscribable_propagatesMembershipRejection() {
    when(permissions.requireMembership(USER, PROJECT_ID)).thenThrow(new ProjectNotFoundException());

    assertThatExceptionOfType(ProjectNotFoundException.class)
        .isThrownBy(() -> service.requireSubscribable(USER, PROJECT_ID));
  }
}
