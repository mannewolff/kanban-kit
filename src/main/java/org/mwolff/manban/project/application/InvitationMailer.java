package org.mwolff.manban.project.application;

/** Ausgehender Port für den Versand der Einladungs-E-Mail. */
public interface InvitationMailer {

  void sendInvitationEmail(String toEmail, String projectName, String invitationUrl);
}
