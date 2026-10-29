package org.mwolff.manban.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Der Anwendungspfad eines Aufrufs (Issue #1973).
 *
 * <p>Geprüft wird die Sicht, auf der ein Filter seine Entscheidung trifft: Sie muss dieselbe sein
 * wie die des Routings, sonst erreicht ein anders geschriebener Pfad den Handler ungeprüft.
 */
class AnwendungspfadTest {

  private static MockHttpServletRequest request(String requestUri) {
    return new MockHttpServletRequest("POST", requestUri);
  }

  @Test
  void literalerPfadBleibtUnveraendert() {
    // Given / When / Then
    assertThat(Anwendungspfad.von(request("/api/auth/code/verify")))
        .isEqualTo("/api/auth/code/verify");
  }

  @Test
  void kodierterBuchstabeWirdDekodiert() {
    // Given — `%76` ist ein `v`; das Routing führt den Aufruf zum selben Handler.
    // When / Then
    assertThat(Anwendungspfad.von(request("/api/auth/code/%76erify")))
        .isEqualTo("/api/auth/code/verify");
  }

  @Test
  void contextPathZaehltNichtZumPfad() {
    // Given — unter einem Context-Path lautet die URI /app/api/auth/register.
    MockHttpServletRequest request = request("/app/api/auth/%72egister");
    request.setContextPath("/app");

    // When / Then
    assertThat(Anwendungspfad.von(request)).isEqualTo("/api/auth/register");
  }

  @Test
  void matrixParameterUndDoppelteSchraegstricheFallenWeg() {
    // Given / When / Then — beide Schreibweisen führen im Routing zum selben Handler.
    assertThat(Anwendungspfad.von(request("/api/auth;x=1//code/verify")))
        .isEqualTo("/api/auth/code/verify");
  }
}
