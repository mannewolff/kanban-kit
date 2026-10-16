package org.mwolff.manban.auth.application;

/** Ausgehender Port für den Versand der Mail mit Anmeldecode und Link (Plan #1801, A12). */
@FunctionalInterface
public interface LoginCodeMailer {

  void sendLoginCode(String toEmail, String code, String linkUrl);
}
