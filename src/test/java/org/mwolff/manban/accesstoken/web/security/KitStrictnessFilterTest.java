package org.mwolff.manban.accesstoken.web.security;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.project.application.KitReviewVorReadyWriter;
import org.mwolff.manban.project.application.KitStrictnessWriter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit-Tests des Filters, der die gemeldete Strenge-Stufe des Kits am Projekt ablegt (Issue #2014).
 * Gemeldet zählt nur, was ein an ein Projekt gebundenes Token mit einem der drei Werte schickt; die
 * Anfrage läuft in jedem Fall unverändert weiter.
 */
class KitStrictnessFilterTest {

  private static final long PROJECT_ID = 7L;

  private KitStrictnessWriter writer;
  private KitReviewVorReadyWriter reviewWriter;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain chain;
  private KitStrictnessFilter filter;

  @BeforeEach
  void setUp() {
    writer = mock(KitStrictnessWriter.class);
    reviewWriter = mock(KitReviewVorReadyWriter.class);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    chain = mock(FilterChain.class);
    filter = new KitStrictnessFilter(writer, reviewWriter);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void gebundenesToken_strict_schreibtStrict() throws Exception {
    authentifiziereMitToken(PROJECT_ID);
    header("strict");

    filter.doFilter(request, response, chain);

    verify(writer).reportStrictness(PROJECT_ID, "strict");
    verify(chain).doFilter(request, response);
  }

  @Test
  void wertWirdGetrimmtUndKleingeschrieben() throws Exception {
    authentifiziereMitToken(PROJECT_ID);
    header(" Normal ");

    filter.doFilter(request, response, chain);

    verify(writer).reportStrictness(PROJECT_ID, "normal");
    verify(chain).doFilter(request, response);
  }

  @Test
  void leicht_alsLight_wirdGeschrieben() throws Exception {
    authentifiziereMitToken(PROJECT_ID);
    header("LIGHT");

    filter.doFilter(request, response, chain);

    verify(writer).reportStrictness(PROJECT_ID, "light");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "streng", "bogus", "strictly"})
  void ungueltigerOderFehlenderWert_schreibtNicht(@Nullable String wert) throws Exception {
    authentifiziereMitToken(PROJECT_ID);
    header(wert);

    filter.doFilter(request, response, chain);

    verify(writer, never()).reportStrictness(anyLong(), anyString());
    verify(chain).doFilter(request, response);
  }

  @Test
  void sessionAnfrage_schreibtNicht() throws Exception {
    // Die Session-Authentifizierung trägt keinen KanbanPrincipal in den details.
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    header("strict");

    filter.doFilter(request, response, chain);

    verify(writer, never()).reportStrictness(anyLong(), anyString());
    verify(chain).doFilter(request, response);
  }

  @Test
  void ungebundenesToken_schreibtNicht() throws Exception {
    authentifiziereMitToken(null);
    header("strict");

    filter.doFilter(request, response, chain);

    verify(writer, never()).reportStrictness(anyLong(), anyString());
    verify(chain).doFilter(request, response);
  }

  @Test
  void ohneAuthentifizierung_schreibtNicht() throws Exception {
    header("strict");

    filter.doFilter(request, response, chain);

    verify(writer, never()).reportStrictness(anyLong(), anyString());
    verify(chain).doFilter(request, response);
  }

  @Test
  void fehlerImWriter_laesstDieKetteWeiterlaufen() throws Exception {
    authentifiziereMitToken(PROJECT_ID);
    header("strict");
    doThrow(new IllegalStateException("Datenbank weg"))
        .when(writer)
        .reportStrictness(PROJECT_ID, "strict");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      ignoreLeadingAndTrailingWhitespace = false,
      value = {
        "true|true",
        "false|false",
        "TRUE|true",
        "False|false",
        " true |true",
        "\tFALSE |false"
      })
  void reviewVorReady_gueltigerWert_wirdAbgelegt(String wert, boolean erwartet) throws Exception {
    authentifiziereMitToken(PROJECT_ID);
    reviewHeader(wert);

    filter.doFilter(request, response, chain);

    verify(reviewWriter).reportReviewBeforeReady(PROJECT_ID, erwartet);
    verify(chain).doFilter(request, response);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "ja", "1", "truee", "strict"})
  void reviewVorReady_andererOderFehlenderWert_schreibtNicht(@Nullable String wert)
      throws Exception {
    authentifiziereMitToken(PROJECT_ID);
    reviewHeader(wert);

    filter.doFilter(request, response, chain);

    verify(reviewWriter, never()).reportReviewBeforeReady(anyLong(), anyBoolean());
    verify(chain).doFilter(request, response);
  }

  @Test
  void reviewVorReady_ungebundenesToken_schreibtNicht() throws Exception {
    authentifiziereMitToken(null);
    reviewHeader("true");

    filter.doFilter(request, response, chain);

    verify(reviewWriter, never()).reportReviewBeforeReady(anyLong(), anyBoolean());
    verify(chain).doFilter(request, response);
  }

  @Test
  void reviewVorReady_sessionAnfrage_schreibtNicht() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    reviewHeader("true");

    filter.doFilter(request, response, chain);

    verify(reviewWriter, never()).reportReviewBeforeReady(anyLong(), anyBoolean());
    verify(chain).doFilter(request, response);
  }

  @Test
  void reviewVorReady_fehlerBeimAblegen_laesstDieAnfrageDurch() throws Exception {
    authentifiziereMitToken(PROJECT_ID);
    reviewHeader("true");
    doThrow(new IllegalStateException("Datenbank weg"))
        .when(reviewWriter)
        .reportReviewBeforeReady(PROJECT_ID, true);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  /** Die beiden Meldungen sind unabhängig: Ein Fehler an der einen verhindert die andere nicht. */
  @Test
  void fehlerBeiDerStufe_legtDieReviewMeldungTrotzdemAb() throws Exception {
    authentifiziereMitToken(PROJECT_ID);
    header("strict");
    reviewHeader("false");
    doThrow(new IllegalStateException("Datenbank weg"))
        .when(writer)
        .reportStrictness(PROJECT_ID, "strict");

    filter.doFilter(request, response, chain);

    verify(reviewWriter).reportReviewBeforeReady(PROJECT_ID, false);
    verify(chain).doFilter(request, response);
  }

  private void reviewHeader(@Nullable String wert) {
    when(request.getHeader(KitStrictnessFilter.REVIEW_HEADER)).thenReturn(wert);
  }

  private void header(@Nullable String wert) {
    when(request.getHeader(KitStrictnessFilter.HEADER)).thenReturn(wert);
  }

  private static void authentifiziereMitToken(@Nullable Long projectId) {
    var authentication = new UsernamePasswordAuthenticationToken(1L, null, List.of());
    authentication.setDetails(
        new KanbanPrincipal(1L, 2L, projectId, projectId == null ? null : 3L, "kit"));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
