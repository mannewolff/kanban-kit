package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verhaltenstests der einladungsbasierten Auto-Freigabe (Mockito am Invitation-Repository). */
class InvitationRegistrationApprovalPolicyTest {

  private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");

  private ProjectInvitationRepository invitations;
  private InvitationRegistrationApprovalPolicy policy;

  @BeforeEach
  void setUp() {
    invitations = mock(ProjectInvitationRepository.class);
    policy =
        new InvitationRegistrationApprovalPolicy(invitations, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void autoApproves_whenOpenInvitationExists() {
    when(invitations.existsOpenInvitation("invited@x.de", NOW)).thenReturn(true);

    assertThat(policy.shouldAutoApprove("invited@x.de")).isTrue();
  }

  @Test
  void doesNotAutoApprove_whenNoOpenInvitation() {
    when(invitations.existsOpenInvitation("stranger@x.de", NOW)).thenReturn(false);

    assertThat(policy.shouldAutoApprove("stranger@x.de")).isFalse();
  }
}
