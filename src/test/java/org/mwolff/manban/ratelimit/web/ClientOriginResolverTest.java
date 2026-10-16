package org.mwolff.manban.ratelimit.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.ratelimit.application.RateLimitProperties;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Spoofsichere Bestimmung der Herkunft (Issue #898, Plan #892 E2/E3/E7).
 *
 * <p>Der Kern ist der erste Test: Ein Client, der {@code X-Forwarded-For} selbst mitschickt, darf
 * seine Herkunft nicht wählen können — sonst wäre die Zählbremse vollständig umgehbar.
 *
 * <p>{@code PMD.AvoidUsingHardCodedIP} ist hier bewusst abgeschaltet: Die Adressliterale sind der
 * Testgegenstand, nicht eine hartcodierte Umgebungsangabe. Bis auf die gespoofte {@code 1.2.3.4},
 * die das Akzeptanzkriterium aus Issue #898 wörtlich vorgibt, stammen sie aus den für Dokumentation
 * reservierten Bereichen {@code 203.0.113.0/24} (RFC 5737) und {@code 2001:db8::/32} (RFC 3849)
 * oder sind privat.
 */
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class ClientOriginResolverTest {

  private static final String FORWARDED_FOR = "X-Forwarded-For";
  private static final String PEER = "10.0.0.1";

  private static ClientOriginResolver resolverWith(int trustedProxyCount) {
    return new ClientOriginResolver(
        new RateLimitProperties(true, trustedProxyCount, null, null, null, null));
  }

  private static MockHttpServletRequest requestFrom(String peer, String... forwardedFor) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(peer);
    for (String value : forwardedFor) {
      request.addHeader(FORWARDED_FOR, value);
    }
    return request;
  }

  @Test
  void spoofedFirstEntryDoesNotChooseTheOrigin() {
    // Given — der Client hat 1.2.3.4 selbst mitgeschickt, der Proxy 203.0.113.9 angehängt.
    MockHttpServletRequest request = requestFrom(PEER, "1.2.3.4, 203.0.113.9");

    // When / Then — gelesen wird von rechts, nicht von links.
    assertThat(resolverWith(1).resolve(request)).isEqualTo("203.0.113.9");
  }

  @Test
  void withoutTheHeaderThePeerAddressIsTheOrigin() {
    // Given
    MockHttpServletRequest request = requestFrom("203.0.113.9");

    // When / Then
    assertThat(resolverWith(1).resolve(request)).isEqualTo("203.0.113.9");
  }

  @Test
  void withoutTrustedProxyTheHeaderIsIgnored() {
    // Given — trusted-proxy-count 0 ist der Fall „App direkt erreichbar" (E3).
    MockHttpServletRequest request = requestFrom("203.0.113.9", "1.2.3.4");

    // When / Then
    assertThat(resolverWith(0).resolve(request)).isEqualTo("203.0.113.9");
  }

  @Test
  void fewerEntriesThanTrustedProxiesFallBackToThePeerAddress() {
    // Given — zwei Proxies erwartet, nur ein Eintrag da: der Header ist unvollständig.
    MockHttpServletRequest request = requestFrom(PEER, "203.0.113.9");

    // When / Then
    assertThat(resolverWith(2).resolve(request)).isEqualTo(PEER);
  }

  @Test
  void theOnlyEntryIsTheOriginWhenOneProxyIsTrusted() {
    // Given — Grenzfall zum vorigen Test: genau so viele Einträge wie erwartete Proxies.
    MockHttpServletRequest request = requestFrom(PEER, "203.0.113.9");

    // When / Then
    assertThat(resolverWith(1).resolve(request)).isEqualTo("203.0.113.9");
  }

  @Test
  void ipv6AddressesOfTheSamePrefixShareAnOrigin() {
    // Given / When — ein Anschluss bekommt regelmäßig ein ganzes /64 zugeteilt (E7).
    String first = resolverWith(1).resolve(requestFrom(PEER, "2001:db8::1"));
    String second = resolverWith(1).resolve(requestFrom(PEER, "2001:db8::2"));
    String other = resolverWith(1).resolve(requestFrom(PEER, "2001:db9::1"));

    // Then
    assertThat(first).isEqualTo("2001:db8:0:0::/64").isEqualTo(second).isNotEqualTo(other);
    assertThat(other).isEqualTo("2001:db9:0:0::/64");
  }

  @Test
  void peerAddressIsAggregatedLikeForwardedOne() {
    // Given — dieselbe Normalisierung gilt auf dem Rückfallweg, sonst wäre der Fall „App direkt
    // erreichbar" bei IPv6 wirkungslos.
    MockHttpServletRequest request = requestFrom("2001:db8::1");

    // When / Then
    assertThat(resolverWith(0).resolve(request)).isEqualTo("2001:db8:0:0::/64");
  }

  @Test
  void portSuffixesAndBracketsAreStripped() {
    // Given / When
    String ipv4 = resolverWith(1).resolve(requestFrom(PEER, "203.0.113.9:51234"));
    String ipv6 = resolverWith(1).resolve(requestFrom(PEER, "[2001:db8::1]:443"));

    // Then
    assertThat(ipv4).isEqualTo("203.0.113.9");
    assertThat(ipv6).isEqualTo("2001:db8:0:0::/64");
  }

  @Test
  void anUnusableEntryFallsBackToThePeerAddressInsteadOfThrowing() {
    // Given
    MockHttpServletRequest request = requestFrom(PEER, "nicht-eine-adresse");

    // When / Then
    assertThat(resolverWith(1).resolve(request)).isEqualTo(PEER);
  }

  @Test
  void anUnclosedBracketFallsBackToThePeerAddress() {
    // Given — abgeschnittener Header; die eckige Klammer wird nie geschlossen.
    MockHttpServletRequest request = requestFrom(PEER, "[2001:db8::1");

    // When / Then
    assertThat(resolverWith(1).resolve(request)).isEqualTo(PEER);
  }

  @Test
  void whitespaceAndEmptyEntriesAreTolerated() {
    // Given — Leerzeichen um die Einträge und ein leerer Eintrag am Ende der Liste.
    MockHttpServletRequest request = requestFrom(PEER, " 1.2.3.4 , 203.0.113.9 , ");

    // When / Then — der leere Eintrag verschiebt die Zählung von rechts nicht.
    assertThat(resolverWith(1).resolve(request)).isEqualTo("203.0.113.9");
  }

  @Test
  void headerWithoutUsableEntryFallsBackToThePeerAddress() {
    // Given
    MockHttpServletRequest request = requestFrom(PEER, " , ");

    // When / Then
    assertThat(resolverWith(1).resolve(request)).isEqualTo(PEER);
  }

  @Test
  void severalHeaderLinesAreReadAsOneList() {
    // Given — ein Proxy, der anhängt statt zu ersetzen, kann eine zweite Header-Zeile erzeugen.
    // Nur die erste zu lesen gäbe dem Client seine selbst gewählte Adresse zurück.
    MockHttpServletRequest request = requestFrom(PEER, "1.2.3.4", "203.0.113.9");

    // When / Then
    assertThat(resolverWith(1).resolve(request)).isEqualTo("203.0.113.9");
  }

  @Test
  void anUnparsablePeerAddressIsUsedAsItIs() {
    // Given — kein Servlet-Container liefert das, aber die Bremse darf daran nicht scheitern.
    MockHttpServletRequest request = requestFrom("nicht-eine-adresse");

    // When / Then
    assertThat(resolverWith(0).resolve(request)).isEqualTo("nicht-eine-adresse");
  }
}
