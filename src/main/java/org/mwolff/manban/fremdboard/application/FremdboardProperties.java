package org.mwolff.manban.fremdboard.application;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration des fremden Boards (Issue #2157, Plan #2152 A13, A14, A17).
 *
 * <p>Leere Werte gelten wie fehlende — so kommen ungesetzte Umgebungsvariablen aus {@code
 * application.yml} an. Nicht positive Dauern fallen auf ihre Vorgabe zurück.
 *
 * @param secretKey Schlüssel für den hinterlegten Zugang, 32 Byte hex aus {@code
 *     MANBAN_SECRET_KEY}; {@code null}, wenn keiner gesetzt ist — dann ist das Anbinden gesperrt
 * @param hosts erlaubte Hosts aus {@code MANBAN_FREMDBOARD_HOSTS}, klein geschrieben; Vorgabe
 *     {@code gitlab.com}
 * @param takt Abstand zwischen zwei Läufen des Abgleichs; Vorgabe 60 Sekunden
 * @param mindestabstand kürzester Abstand zwischen zwei Abgleichen desselben Projekts; Vorgabe 2
 *     Minuten
 * @param vollabgleich Abstand zwischen zwei vollständigen Abgleichen eines Projekts; Vorgabe 60
 *     Minuten
 */
@ConfigurationProperties(prefix = "manban.fremdboard")
public record FremdboardProperties(
    @Nullable String secretKey,
    List<String> hosts,
    Duration takt,
    Duration mindestabstand,
    Duration vollabgleich) {

  /** Host, der ohne Angabe erlaubt ist. */
  public static final String STANDARD_HOST = "gitlab.com";

  private static final Duration STANDARD_TAKT = Duration.ofSeconds(60);
  private static final Duration STANDARD_MINDESTABSTAND = Duration.ofMinutes(2);
  private static final Duration STANDARD_VOLLABGLEICH = Duration.ofMinutes(60);

  public FremdboardProperties(
      @Nullable String secretKey,
      @Nullable List<String> hosts,
      @Nullable Duration takt,
      @Nullable Duration mindestabstand,
      @Nullable Duration vollabgleich) {
    this.secretKey = secretKey == null || secretKey.isBlank() ? null : secretKey.strip();
    this.hosts = normalisierteHosts(hosts);
    this.takt = positivOder(takt, STANDARD_TAKT);
    this.mindestabstand = positivOder(mindestabstand, STANDARD_MINDESTABSTAND);
    this.vollabgleich = positivOder(vollabgleich, STANDARD_VOLLABGLEICH);
  }

  /**
   * Die Hosts ohne Leerzeichen am Rand, klein geschrieben, ohne leere Einträge; ist danach keiner
   * übrig, gilt {@link #STANDARD_HOST}.
   */
  public static List<String> normalisierteHosts(@Nullable List<String> hosts) {
    List<String> bereinigt =
        hosts == null
            ? List.of()
            : hosts.stream()
                .map(host -> host.strip().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .toList();
    return bereinigt.isEmpty() ? List.of(STANDARD_HOST) : bereinigt;
  }

  private static Duration positivOder(@Nullable Duration wert, Duration vorgabe) {
    return wert == null || wert.isZero() || wert.isNegative() ? vorgabe : wert;
  }
}
