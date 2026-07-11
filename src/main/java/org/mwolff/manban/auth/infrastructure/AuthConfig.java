package org.mwolff.manban.auth.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Auth-Infrastruktur-Beans. */
@Configuration
class AuthConfig {

  /** Argon2id-Passwort-Encoder mit den empfohlenen Spring-Security-Parametern. */
  @Bean
  PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }
}
