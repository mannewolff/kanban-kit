package org.mwolff.manban.project.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Projekt-Konfiguration.
 *
 * @param invitationTtl Gültigkeitsdauer einer Einladung
 */
@ConfigurationProperties(prefix = "manban.project")
public record ProjectProperties(Duration invitationTtl) {

  public ProjectProperties {
    if (invitationTtl == null) {
      invitationTtl = Duration.ofDays(7);
    }
  }
}
