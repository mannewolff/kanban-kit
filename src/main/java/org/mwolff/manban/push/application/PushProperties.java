package org.mwolff.manban.push.application;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration von Web Push (Issue #1996, Plan #1989 E16). Push ist abschaltbar: Ohne Schlüssel
 * startet eine Installation wie bisher, nur Benachrichtigungen bleiben aus. Halbe oder ungültige
 * Angaben weist die Startprüfung {@code PushSchluesselStartupCheck} ab.
 *
 * <p>Leere Werte gelten wie fehlende — so kommen ungesetzte Umgebungsvariablen aus {@code
 * application.yml} an.
 *
 * @param vapidPublicKey öffentlicher VAPID-Schlüssel, unkomprimierter P-256-Punkt als base64url
 * @param vapidPrivateKey privater VAPID-Schlüssel, P-256-Skalar als base64url
 * @param subject Kontakt für Push-Dienste nach RFC 8292 §2.1, {@code mailto:} oder {@code https:}
 * @param meldeTakt Abstand zwischen zwei Läufen des Meldejobs; Vorgabe eine Minute
 */
@ConfigurationProperties(prefix = "manban.push")
public record PushProperties(
    @Nullable String vapidPublicKey,
    @Nullable String vapidPrivateKey,
    @Nullable String subject,
    Duration meldeTakt) {

  private static final Duration STANDARD_MELDE_TAKT = Duration.ofMinutes(1);

  public PushProperties(
      @Nullable String vapidPublicKey,
      @Nullable String vapidPrivateKey,
      @Nullable String subject,
      @Nullable Duration meldeTakt) {
    this.vapidPublicKey = ohneLeere(vapidPublicKey);
    this.vapidPrivateKey = ohneLeere(vapidPrivateKey);
    this.subject = ohneLeere(subject);
    this.meldeTakt =
        meldeTakt == null || meldeTakt.isZero() || meldeTakt.isNegative()
            ? STANDARD_MELDE_TAKT
            : meldeTakt;
  }

  /** Wahr, wenn beide Schlüssel und {@code subject} gesetzt sind. */
  public boolean aktiv() {
    return vapidPublicKey != null && vapidPrivateKey != null && subject != null;
  }

  private static @Nullable String ohneLeere(@Nullable String wert) {
    return wert == null || wert.isBlank() ? null : wert.strip();
  }
}
