package org.mwolff.manban.project.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.application.InviteOutcome;
import org.mwolff.manban.project.application.MembershipService;
import org.mwolff.manban.project.application.MembershipService.MemberView;
import org.mwolff.manban.project.domain.ProjectRole;

/** Unit-Tests des Einladungs-Controllers (Service gemockt). */
class InvitationControllerTest {

  private MembershipService service;
  private InvitationController controller;

  @BeforeEach
  void setUp() {
    service = mock(MembershipService.class);
    controller = new InvitationController(service);
  }

  @Test
  void invite_delegatesToService_andMapsAddedOutcome() {
    // Given
    var request = new InvitationController.InviteRequest("a@b.de", ProjectRole.MEMBER);
    when(service.invite(3L, 5L, "a@b.de", ProjectRole.MEMBER)).thenReturn(InviteOutcome.ADDED);

    // When
    InvitationController.InviteResponse response = controller.invite(3L, 5L, request);

    // Then
    verify(service).invite(3L, 5L, "a@b.de", ProjectRole.MEMBER);
    assertThat(response.status()).isEqualTo("added");
  }

  @Test
  void invite_mapsInvitedOutcome() {
    // Given
    var request = new InvitationController.InviteRequest("new@b.de", ProjectRole.VIEWER);
    when(service.invite(3L, 5L, "new@b.de", ProjectRole.VIEWER)).thenReturn(InviteOutcome.INVITED);

    // When
    InvitationController.InviteResponse response = controller.invite(3L, 5L, request);

    // Then
    assertThat(response.status()).isEqualTo("invited");
  }

  @Test
  void accept_delegatesToService() {
    // Given
    var view = new MemberView(3L, "a@b.de", "Alice", ProjectRole.MEMBER);
    when(service.accept(3L, "tok")).thenReturn(view);

    // When
    MemberView result =
        controller.accept(3L, new InvitationController.AcceptInvitationRequest("tok"));

    // Then
    assertThat(result).isSameAs(view);
  }
}
