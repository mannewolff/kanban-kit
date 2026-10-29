package org.mwolff.manban.push.infrastructure;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import org.mwolff.manban.common.ExcludeFromJacocoGeneratedReport;

/**
 * Baut die {@code Authorization}-Kopfzeile nach RFC 8292: ein mit ES256 signiertes JWT, dazu der
 * öffentliche Schlüssel des Servers (Issue #1996).
 *
 * <p>Ansprüche: {@code aud} ist der Ursprung des Endpunkts (Schema, Host, Port ohne Standardport),
 * {@code exp} liegt {@link #GUELTIGKEIT} in der Zukunft — RFC 8292 §2 erlaubt höchstens 24 Stunden,
 * die halbe Spanne lässt Platz für eine abweichende Uhr des Push-Dienstes —, {@code sub} ist die
 * Kontaktadresse aus {@code manban.push.subject}.
 */
final class VapidSignatur {

  /** Wie lange ein Token gilt; RFC 8292 §2 erlaubt höchstens 24 Stunden. */
  static final Duration GUELTIGKEIT = Duration.ofHours(12);

  private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
  private static final String KOPF =
      BASE64URL.encodeToString(
          "{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.US_ASCII));

  private final VapidSchluessel schluessel;
  private final String subject;
  private final Clock clock;

  VapidSignatur(VapidSchluessel schluessel, String subject, Clock clock) {
    this.schluessel = schluessel;
    this.subject = subject;
    this.clock = clock;
  }

  /** {@code vapid t=<jwt>, k=<öffentlicher Schlüssel>} für einen Endpunkt. */
  String kopfzeile(URI endpunkt) {
    String ansprueche =
        "{\"aud\":\""
            + zeichenkette(ursprung(endpunkt))
            + "\",\"exp\":"
            + clock.instant().plus(GUELTIGKEIT).getEpochSecond()
            + ",\"sub\":\""
            + zeichenkette(subject)
            + "\"}";
    String unsigniert =
        KOPF + "." + BASE64URL.encodeToString(ansprueche.getBytes(StandardCharsets.UTF_8));
    String signatur = BASE64URL.encodeToString(signieren(unsigniert));
    return "vapid t=" + unsigniert + "." + signatur + ", k=" + schluessel.oeffentlich();
  }

  /** Maskiert einen Wert für eine JSON-Zeichenkette, ohne die Anführungszeichen. */
  private static String zeichenkette(String wert) {
    return new String(JsonStringEncoder.getInstance().quoteAsString(wert));
  }

  /**
   * Ursprung nach RFC 6454: Schema und Host klein, Port nur, wenn er nicht der Standardport ist.
   */
  private static String ursprung(URI endpunkt) {
    String schema = endpunkt.getScheme().toLowerCase(Locale.ROOT);
    String ursprung = schema + "://" + endpunkt.getHost().toLowerCase(Locale.ROOT);
    int port = endpunkt.getPort();
    int standardport = "https".equals(schema) ? 443 : 80;
    return port == -1 || port == standardport ? ursprung : ursprung + ":" + port;
  }

  // Coverage-Ausnahme (CLAUDE-java.md §5.4): Der catch-Zweig ist unerreichbar — VapidSchluessel#aus
  // hat mit genau diesem Schlüssel schon eine Probe signiert, und eine frisch initialisierte
  // Signature wirft beim Signieren nicht. Den Erfolgszweig prüft VapidSignaturTest mit der JDK.
  @ExcludeFromJacocoGeneratedReport
  private byte[] signieren(String unsigniert) {
    try {
      Signature signatur = VapidSchluessel.signatur();
      signatur.initSign(schluessel.schluesselpaar().getPrivate());
      signatur.update(unsigniert.getBytes(StandardCharsets.US_ASCII));
      return signatur.sign();
    } catch (InvalidKeyException | SignatureException e) {
      throw new IllegalStateException("VAPID-Signatur fehlgeschlagen", e);
    }
  }
}
