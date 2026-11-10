package org.mwolff.manban.benachrichtigung.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration der Benachrichtigungen (Issues #2078, #2081, Plan #2070 E2, E3, E14).
 *
 * @param laufLabelFrist wie lange ein Label-Ereignis mit Laufkennung wartet, ob der Lauf die Karte
 *     selbst meldet; fehlend, {@code null} oder nicht positiv ergibt 10 Minuten. Das Kit setzt das
 *     Label vor der Laufmeldung, die Frist gibt der Meldung Zeit. Nicht positiv wäre keine Frist:
 *     Die Mail ginge vor der Meldung, die sie überflüssig macht.
 * @param takt Abstand zwischen zwei Läufen des Jobs; fehlend, {@code null} oder nicht positiv
 *     ergibt eine Minute
 * @param retentionDays Tage, nach denen Anlässe samt Zustellungen gelöscht werden; fehlend oder
 *     unter {@value #MINDEST_AUFBEWAHRUNG} ergibt {@value #VORGABE_AUFBEWAHRUNG}. Kürzer ginge
 *     nicht: Ein Anlass, den das Erkennungsfenster von 48 Stunden noch sieht, entstünde nach dem
 *     Löschen neu und brächte dieselbe Mail ein zweites Mal.
 */
@ConfigurationProperties(prefix = "manban.benachrichtigung")
public record BenachrichtigungProperties(
    Duration laufLabelFrist, Duration takt, int retentionDays) {

  private static final int MINDEST_AUFBEWAHRUNG = 3;
  private static final int VORGABE_AUFBEWAHRUNG = 90;

  public BenachrichtigungProperties {
    if (laufLabelFrist == null || !laufLabelFrist.isPositive()) {
      laufLabelFrist = Duration.ofMinutes(10);
    }
    if (takt == null || !takt.isPositive()) {
      takt = Duration.ofMinutes(1);
    }
    if (retentionDays < MINDEST_AUFBEWAHRUNG) {
      retentionDays = VORGABE_AUFBEWAHRUNG;
    }
  }
}
