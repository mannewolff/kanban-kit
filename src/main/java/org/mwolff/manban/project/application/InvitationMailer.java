package org.mwolff.manban.project.application;

import org.mwolff.manban.project.domain.ProjectRole;

/** Ausgehender Port für den Versand projektbezogener E-Mails (Einladung bzw. Zuordnungs-Info). */
public interface InvitationMailer {

  /** Einladung einer noch nicht registrierten E-Mail mit Accept-Token. */
  void sendInvitationEmail(String toEmail, String projectName, String invitationUrl);

  /**
   * Benachrichtigung eines bereits registrierten Nutzers, der einem Projekt direkt zugeordnet wurde
   * (ohne Einladungs-/Accept-Schritt, Issue #0101).
   */
  void sendProjectAssignedEmail(
      String toEmail, String projectName, ProjectRole role, String projectUrl);
}
