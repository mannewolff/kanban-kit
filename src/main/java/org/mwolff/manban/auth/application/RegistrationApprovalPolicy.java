package org.mwolff.manban.auth.application;

/**
 * Ausgehender Port: entscheidet bei der Registrierung, ob ein Benutzer sofort (ohne Admin-Aktion)
 * freigegeben wird. Die Standard-Antwort ist {@code false} (Selbst-Registrierer warten auf
 * Admin-Freigabe, Issue #0097). Das project-Modul liefert eine Umsetzung, die per Token eingeladene
 * E-Mail-Adressen automatisch freigibt (Issue #0099) — die Einladung durch einen Owner gilt als
 * Freigabe.
 */
@FunctionalInterface
public interface RegistrationApprovalPolicy {

  /**
   * Entscheidet, ob die angegebene E-Mail bei der Registrierung sofort freigegeben wird.
   *
   * @param normalizedEmail bereits normalisierte (trim + lowercase) E-Mail-Adresse
   * @return {@code true}, wenn der Benutzer bei der Registrierung sofort freigegeben werden soll
   */
  boolean shouldAutoApprove(String normalizedEmail);
}
