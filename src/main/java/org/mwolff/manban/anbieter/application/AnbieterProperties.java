package org.mwolff.manban.anbieter.application;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration des Moduls {@code anbieter} (Issue #2216, Plan #2206 E12).
 *
 * @param abholfrist so lange darf das Rechenzentrum die Karten eines Boards ungelesen lassen, bevor
 *     der Kunde „gerade nicht erreichbar“ sieht; fehlend oder nicht positiv gilt die Vorgabe von 6
 *     Minuten
 */
@ConfigurationProperties(prefix = "manban.anbieter")
public record AnbieterProperties(Duration abholfrist) {

  /** Vorgabe der Abholfrist (Fachplan #2202, V10). */
  public static final Duration STANDARD_ABHOLFRIST = Duration.ofMinutes(6);

  public AnbieterProperties(@Nullable Duration abholfrist) {
    this.abholfrist =
        abholfrist == null || abholfrist.isNegative() || abholfrist.isZero()
            ? STANDARD_ABHOLFRIST
            : abholfrist;
  }
}
