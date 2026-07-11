package org.mwolff.manban.auth.application;

/** Ausgehender Port für den Versand der Verifikations-E-Mail. */
public interface VerificationMailer {

  void sendVerificationEmail(String toEmail, String verificationUrl);
}
