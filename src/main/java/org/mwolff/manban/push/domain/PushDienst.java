package org.mwolff.manban.push.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * Die Push-Dienste, an die ein Abo zeigen darf (Issue #2290): die der Browser von Google (auch
 * Chromium-Ableger wie Brave und Opera), Mozilla, Apple und Microsoft. Jede andere Adresse — ein
 * Rechner im Netz des Servers, Loopback, eine private IP-Adresse, ein beliebiger Host — ist kein
 * Push-Dienst, und der Server baut dorthin keine Verbindung auf.
 *
 * <p>Erlaubt ist ein Endpunkt mit {@code https}, ohne Zugangsdaten vor dem Rechnernamen, ohne
 * fremden Port (nur keiner oder 443) und mit einem Rechnernamen, der eine der Domänen ist oder auf
 * {@code .<Domäne>} endet. IP-Adressen treffen keine Domäne; die Namensauflösung der Domänen liegt
 * bei ihren Betreibern, nicht beim Absender eines Abos.
 */
public final class PushDienst {

  /** Die Domänen der Push-Dienste; ein Endpunkt liegt auf einer davon oder auf einer Subdomäne. */
  static final List<String> DOMAENEN =
      List.of(
          "fcm.googleapis.com",
          "push.services.mozilla.com",
          "push.apple.com",
          "notify.windows.com");

  private static final String HTTPS = "https";
  private static final int HTTPS_PORT = 443;
  private static final int OHNE_PORT = -1;

  private PushDienst() {}

  /** {@link #erlaubt(URI)} für einen Endpunkt als Text; keine gültige Adresse ist nicht erlaubt. */
  public static boolean erlaubt(String endpunkt) {
    try {
      return erlaubt(new URI(endpunkt));
    } catch (URISyntaxException e) {
      return false;
    }
  }

  /** Wahr, wenn {@code endpunkt} auf einen der Push-Dienste zeigt. */
  public static boolean erlaubt(URI endpunkt) {
    if (!HTTPS.equalsIgnoreCase(endpunkt.getScheme()) || endpunkt.getRawUserInfo() != null) {
      return false;
    }
    int port = endpunkt.getPort();
    if (port != OHNE_PORT && port != HTTPS_PORT) {
      return false;
    }
    String host = endpunkt.getHost();
    if (host == null) {
      return false;
    }
    String name = host.toLowerCase(Locale.ROOT);
    return DOMAENEN.stream().anyMatch(d -> name.equals(d) || name.endsWith("." + d));
  }
}
