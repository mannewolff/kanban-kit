package org.mwolff.manban.project.application;

import java.time.Clock;
import org.mwolff.manban.auth.application.RegistrationApprovalPolicy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * project-seitige Umsetzung von {@link RegistrationApprovalPolicy}: gibt eine sich registrierende
 * E-Mail automatisch frei, wenn für sie eine offene Projekt-Einladung vorliegt (Issue #0099, C2
 * „Einladung = Freigabe"). So wird der per Token eingeladene Fremd-Nutzer nicht vom Login-Gate
 * ausgesperrt, bevor er die Einladung annehmen kann.
 */
@Component
@Primary
class InvitationRegistrationApprovalPolicy implements RegistrationApprovalPolicy {

  private final ProjectInvitationRepository invitations;
  private final Clock clock;

  InvitationRegistrationApprovalPolicy(ProjectInvitationRepository invitations, Clock clock) {
    this.invitations = invitations;
    this.clock = clock;
  }

  @Override
  public boolean shouldAutoApprove(String normalizedEmail) {
    return invitations.existsOpenInvitation(normalizedEmail, clock.instant());
  }
}
