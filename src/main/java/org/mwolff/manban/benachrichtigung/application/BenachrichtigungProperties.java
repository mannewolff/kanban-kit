package org.mwolff.manban.benachrichtigung.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration der Benachrichtigungen (Issue #2078, Plan #2070 E3).
 *
 * @param laufLabelFrist wie lange ein Label-Ereignis mit Laufkennung wartet, ob der Lauf die Karte
 *     selbst meldet; fehlend, {@code null} oder nicht positiv ergibt 10 Minuten. Das Kit setzt das
 *     Label vor der Laufmeldung, die Frist gibt der Meldung Zeit. Nicht positiv wäre keine Frist:
 *     Die Mail ginge vor der Meldung, die sie überflüssig macht.
 */
@ConfigurationProperties(prefix = "manban.benachrichtigung")
public record BenachrichtigungProperties(Duration laufLabelFrist) {

  public BenachrichtigungProperties {
    if (laufLabelFrist == null || !laufLabelFrist.isPositive()) {
      laufLabelFrist = Duration.ofMinutes(10);
    }
  }
}
