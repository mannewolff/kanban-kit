package org.mwolff.manban.auth.application;

/** Ausgehender Port für Admin-Benachrichtigungen rund um die Nutzerverwaltung. */
@FunctionalInterface
public interface AdminNotificationMailer {

  /**
   * Benachrichtigt einen Plattform-Admin, dass ein neu registrierter, verifizierter Nutzer auf
   * Freigabe wartet (Issue #0098).
   */
  void sendNewUserPendingApproval(
      String adminEmail, String newUserEmail, String newUserDisplayName);
}
