package org.mwolff.manban.auth.infrastructure;

import org.mwolff.manban.auth.application.RegistrationApprovalPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Auth-Infrastruktur-Beans. */
@Configuration
class AuthConfig {

  /**
   * Fallback-Freigabe-Policy, falls kein Fachmodul eine liefert: gibt niemanden automatisch frei.
   * Das project-Modul stellt eine {@code @Primary}-Umsetzung bereit (Auto-Freigabe eingeladener
   * E-Mails, Issue #0099), die diesen Default verdrängt.
   */
  @Bean
  RegistrationApprovalPolicy defaultRegistrationApprovalPolicy() {
    return normalizedEmail -> false;
  }
}
