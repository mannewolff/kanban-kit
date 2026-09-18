package org.mwolff.manban.ratelimit.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.ratelimit.application.RateLimitProperties;
import org.springframework.stereotype.Component;

/**
 * Bestimmt die Herkunft eines Requests für die Zählbremse (Plan #892, E2/E3/E7).
 *
 * <p>Die Auflösung geschieht bewusst im Modul und nicht über {@code getRemoteAddr()} nach Springs
 * {@code ForwardedHeaderFilter}: Der mitgelieferte Caddy <strong>hängt</strong> {@code
 * X-Forwarded-For} an, statt ihn zu ersetzen, und der Filter nähme den <strong>ersten</strong>
 * Eintrag. Ein Client, der {@code X-Forwarded-For: 1.2.3.4} mitschickt, wählte damit seine eigene
 * Herkunft frei — die Bremse wäre vollständig umgehbar. Gelesen wird deshalb von rechts, an Index
 * {@code laenge - trustedProxyCount}: bei einem Proxy also der Eintrag, den dieser selbst angehängt
 * hat. Bei {@code trusted-proxy-count: 0} gilt die rohe TCP-Adresse — der Fall „App direkt
 * erreichbar" (E3).
 *
 * <p>IPv6-Adressen werden auf ihr {@code /64}-Präfix zusammengefasst, IPv4 bleibt vollständig (E7).
 * Ohne diese Aggregation wäre die Bremse bei IPv6 wirkungslos: Ein einzelner Anschluss bekommt
 * regelmäßig ein ganzes {@code /64} zugeteilt und könnte beliebig viele Herkünfte erzeugen.
 *
 * <p>Gelesen werden ausschließlich Header und Peer-Adresse — <strong>nie</strong> der Request-Body.
 * Die Klasse kennt die angefragte E-Mail-Adresse damit gar nicht (AK 5 aus Issue #840).
 */
@Component
public class ClientOriginResolver {

  private static final String FORWARDED_FOR = "X-Forwarded-For";

  /** Zahl der Teile, in die genau ein Doppelpunkt einen Eintrag zerlegt — also {@code ip:port}. */
  private static final int PARTS_WITH_ONE_COLON = 2;

  private final int trustedProxyCount;

  /**
   * Nimmt die modulinterne Konfiguration entgegen (E16 erlaubt das innerhalb von {@code
   * ratelimit}).
   */
  public ClientOriginResolver(RateLimitProperties properties) {
    this.trustedProxyCount = properties.trustedProxyCount();
  }

  /**
   * Die Herkunft des Requests als Zeichenkette — Schlüssel der Zählung und Angabe im Protokoll.
   *
   * @return der vertrauenswürdige {@code X-Forwarded-For}-Eintrag, sonst die TCP-Peer-Adresse,
   *     jeweils normalisiert nach E7
   */
  public String resolve(HttpServletRequest request) {
    String forwarded = forwardedOrigin(request);
    return forwarded != null ? forwarded : peerOrigin(request.getRemoteAddr());
  }

  /**
   * Der Eintrag, den der äußerste vertrauenswürdige Proxy selbst angehängt hat.
   *
   * @return {@code null}, wenn kein Proxy vertraut wird, der Header zu wenige Einträge hat oder der
   *     gesuchte Eintrag keine Adresse ist — in allen drei Fällen gilt die Peer-Adresse
   */
  private @Nullable String forwardedOrigin(HttpServletRequest request) {
    if (trustedProxyCount == 0) {
      return null;
    }
    List<String> entries = forwardedEntries(request);
    int index = entries.size() - trustedProxyCount;
    if (index < 0) {
      return null;
    }
    InetAddress address = parseLiteral(entries.get(index));
    return address != null ? normalize(address) : null;
  }

  /** Die Peer-Adresse; bleibt unverändert, wenn der Container keine Adresse geliefert hat. */
  private static String peerOrigin(String peer) {
    InetAddress address = parseLiteral(peer);
    return address != null ? normalize(address) : peer;
  }

  /**
   * Alle Einträge des Headers in Reihenfolge, über sämtliche Header-Zeilen hinweg.
   *
   * <p>Gelesen werden alle Zeilen, nicht nur die erste: Ein Proxy, der anhängt statt zu ersetzen,
   * darf eine zweite Zeile erzeugen. Läse die Bremse nur die erste, bekäme sie genau den Wert
   * zurück, den der Client sich selbst ausgesucht hat.
   */
  private static List<String> forwardedEntries(HttpServletRequest request) {
    return Collections.list(request.getHeaders(FORWARDED_FOR)).stream()
        .flatMap(line -> Arrays.stream(line.split(",")))
        .map(String::trim)
        .filter(entry -> !entry.isEmpty())
        .toList();
  }

  /**
   * Liest einen Eintrag als Adressliteral.
   *
   * <p>{@link InetAddress#ofLiteral} löst bewusst nichts auf: Ein unbrauchbarer Eintrag darf in
   * einer Bremse gegen Massenanfragen keine Namensauflösung auslösen.
   *
   * @return {@code null}, wenn der Eintrag keine Adresse ist
   */
  private static @Nullable InetAddress parseLiteral(String raw) {
    try {
      return InetAddress.ofLiteral(stripPort(raw));
    } catch (IllegalArgumentException _) {
      return null;
    }
  }

  /**
   * Schneidet eine Portangabe ab — {@code [2001:db8::1]:443} ebenso wie {@code 203.0.113.9:51234}.
   *
   * <p>Ohne eckige Klammern entscheidet die Zahl der Doppelpunkte: Genau einer trennt Port von
   * IPv4-Adresse, mehrere gehören zu einer rohen IPv6-Adresse und bleiben stehen.
   */
  private static String stripPort(String value) {
    if (value.startsWith("[")) {
      int end = value.indexOf(']');
      return end == -1 ? value : value.substring(1, end);
    }
    String[] parts = value.split(":", -1);
    return parts.length == PARTS_WITH_ONE_COLON ? parts[0] : value;
  }

  /** IPv6 auf das {@code /64}-Präfix zusammengefasst (E7), IPv4 in voller Länge. */
  private static String normalize(InetAddress address) {
    if (!(address instanceof Inet6Address)) {
      return address.getHostAddress();
    }
    byte[] bytes = address.getAddress();
    return "%x:%x:%x:%x::/64"
        .formatted(hextet(bytes, 0), hextet(bytes, 2), hextet(bytes, 4), hextet(bytes, 6));
  }

  /** Die zwei Bytes ab {@code index} als eine der vier Gruppen des Präfixes. */
  private static int hextet(byte[] bytes, int index) {
    return ((bytes[index] & 0xFF) << 8) | (bytes[index + 1] & 0xFF);
  }
}
