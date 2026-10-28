package org.mwolff.manban.auth.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.auth.application.VorschauKontext;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit-Tests der Schreibsperre in der Vorschau „Ansicht als XY“ (Issue #1920, Plan #1914 E4, E5).
 */
class VorschauSchreibsperreFilterTest {

  private static final VorschauKontext KONTEXT =
      new VorschauKontext("kennung", 1L, 7L, Instant.parse("2026-11-15T10:00:00Z"));

  private Optional<VorschauKontext> kontext;
  private Sprache eingestellt;
  private FilterChain chain;
  private VorschauSchreibsperreFilter filter;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    kontext = Optional.of(KONTEXT);
    eingestellt = Sprache.DE;
    chain = mock(FilterChain.class);
    filter = new VorschauSchreibsperreFilter(() -> kontext, SprachFixture.mit(() -> eingestellt));
    response = new MockHttpServletResponse();
  }

  private MockHttpServletRequest run(String method, String path) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    filter.doFilter(request, response, chain);
    return request;
  }

  private void assertPassed(MockHttpServletRequest request) throws Exception {
    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString()).isEmpty();
  }

  private void assertRejected(String typ, String detail) throws Exception {
    verify(chain, never()).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"type\":\"%s\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"%s\"}"
                .formatted(typ, detail));
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
  void inDerVorschau_veraendernd_wirdAbgewiesen(String method) throws Exception {
    // When
    run(method, "/api/projects/3/boards");

    // Then
    assertRejected(
        VorschauSchreibsperreFilter.NUR_LESEND,
        "Die Vorschau ist nur lesend. Ändern ist hier nicht möglich.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"TRACE", "PROPFIND"})
  void inDerVorschau_sonstigeMethode_wirdAbgewiesen(String method) throws Exception {
    // When
    run(method, "/api/projects");

    // Then
    assertRejected(
        VorschauSchreibsperreFilter.NUR_LESEND,
        "Die Vorschau ist nur lesend. Ändern ist hier nicht möglich.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
  void inDerVorschau_lesend_gehtDurch(String method) throws Exception {
    // When
    MockHttpServletRequest request = run(method, "/api/projects/3/boards");

    // Then
    assertPassed(request);
  }

  @Test
  void inDerVorschau_abmelden_gehtDurch() throws Exception {
    // When
    MockHttpServletRequest request = run("POST", "/api/auth/logout");

    // Then
    assertPassed(request);
  }

  @Test
  void inDerVorschau_beenden_gehtDurch() throws Exception {
    // When
    MockHttpServletRequest request = run("DELETE", "/api/vorschau");

    // Then
    assertPassed(request);
  }

  @ParameterizedTest
  @CsvSource({
    "PUT, /api/auth/logout",
    "POST, /api/auth/logout/x",
    "POST, /api/auth/logout/",
    "POST, /api/vorschau",
    "DELETE, /api/vorschau/",
    "DELETE, /api/vorschau/x"
  })
  void inDerVorschau_ausnahmenGeltenNurGenau(String method, String path) throws Exception {
    // When
    run(method, path);

    // Then
    assertRejected(
        VorschauSchreibsperreFilter.NUR_LESEND,
        "Die Vorschau ist nur lesend. Ändern ist hier nicht möglich.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/auth/logout", "/api/vorschau"})
  void inDerVorschau_ausnahmePfadeLesend_gehenDurch(String path) throws Exception {
    // When
    MockHttpServletRequest request = run("GET", path);

    // Then
    assertPassed(request);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/access-tokens",
        "/api/access-tokens/",
        "/api/access-tokens/5",
        "/api/access-tokens/5/revoke",
        "/api/me/vorschau-protokoll",
        "/api/me/vorschau-protokoll/"
      })
  void inDerVorschau_persoenlicherPfad_wirdAuchLesendAbgewiesen(String path) throws Exception {
    // When
    run("GET", path);

    // Then
    assertRejected(
        VorschauSchreibsperreFilter.PERSOENLICH,
        "Dieser persönliche Bereich wird in der Vorschau nicht gezeigt.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"HEAD", "OPTIONS"})
  void inDerVorschau_persoenlicherPfad_auchHeadUndOptions(String method) throws Exception {
    // When
    run(method, "/api/access-tokens");

    // Then
    assertRejected(
        VorschauSchreibsperreFilter.PERSOENLICH,
        "Dieser persönliche Bereich wird in der Vorschau nicht gezeigt.");
  }

  @Test
  void inDerVorschau_persoenlicherPfadVeraendernd_istNurLesend() throws Exception {
    // When
    run("POST", "/api/access-tokens");

    // Then
    assertRejected(
        VorschauSchreibsperreFilter.NUR_LESEND,
        "Die Vorschau ist nur lesend. Ändern ist hier nicht möglich.");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/api/access-tokensx", "/api/me/vorschau-protokollx", "/api/me", "/api/me/x"})
  void inDerVorschau_aehnlicherPfad_istNichtPersoenlich(String path) throws Exception {
    // When
    MockHttpServletRequest request = run("GET", path);

    // Then
    assertPassed(request);
  }

  @Test
  void inDerVorschau_mitKontextpfad_prueftDenPfadDahinter() throws Exception {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/manban/api/access-tokens");
    request.setContextPath("/manban");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertRejected(
        VorschauSchreibsperreFilter.PERSOENLICH,
        "Dieser persönliche Bereich wird in der Vorschau nicht gezeigt.");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/%61ccess-tokens",
        "/api/access-tokens/%35",
        "/api/me/%76orschau-protokoll",
        "/api/me/vorschau-protokoll;x=1",
        "/api//access-tokens"
      })
  void inDerVorschau_persoenlicherPfadKodiert_wirdAbgewiesen(String path) throws Exception {
    // Given: Das Routing sieht den dekodierten Pfad (Issue #1974)

    // When
    run("GET", path);

    // Then
    assertRejected(
        VorschauSchreibsperreFilter.PERSOENLICH,
        "Dieser persönliche Bereich wird in der Vorschau nicht gezeigt.");
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /api/%61ccess-tokens",
    "DELETE, /api/access-tokens/%35",
    "POST, /api/auth/logout/%78",
    "DELETE, /api/vorschau/%78"
  })
  void inDerVorschau_veraenderndKodiert_bleibtNurLesend(String method, String path)
      throws Exception {
    // When
    run(method, path);

    // Then
    assertRejected(
        VorschauSchreibsperreFilter.NUR_LESEND,
        "Die Vorschau ist nur lesend. Ändern ist hier nicht möglich.");
  }

  @ParameterizedTest
  @CsvSource({"POST, /api/auth/%6Cogout", "DELETE, /api/%76orschau"})
  void inDerVorschau_ausnahmeKodiert_gehtDurch(String method, String path) throws Exception {
    // When
    MockHttpServletRequest request = run(method, path);

    // Then
    assertPassed(request);
  }

  @Test
  void inDerVorschau_abweisungInDerEingestelltenSprache() throws Exception {
    // Given
    eingestellt = Sprache.EN;

    // When
    run("PUT", "/api/cards/4");

    // Then
    assertRejected(
        VorschauSchreibsperreFilter.NUR_LESEND,
        "The preview is read-only. Nothing can be changed.");
  }

  @Test
  void inDerVorschau_persoenlichInDerEingestelltenSprache() throws Exception {
    // Given
    eingestellt = Sprache.EN;

    // When
    run("GET", "/api/me/vorschau-protokoll");

    // Then
    assertRejected(
        VorschauSchreibsperreFilter.PERSOENLICH, "This personal area is not shown in the preview.");
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /api/projects",
    "PUT, /api/cards/4",
    "PATCH, /api/cards/4",
    "DELETE, /api/cards/4",
    "GET, /api/access-tokens",
    "GET, /api/me/vorschau-protokoll"
  })
  void ausserhalbDerVorschau_gehtAllesDurch(String method, String path) throws Exception {
    // Given
    kontext = Optional.empty();

    // When
    MockHttpServletRequest request = run(method, path);

    // Then
    assertPassed(request);
  }
}
