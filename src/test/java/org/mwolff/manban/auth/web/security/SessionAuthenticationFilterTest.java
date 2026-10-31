package org.mwolff.manban.auth.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.SessionCheck;
import org.mwolff.manban.auth.application.SessionTokens;
import org.mwolff.manban.auth.application.SitzungsEnde;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Unit-Tests des Session-Authentifizierungs-Filters (Kollaborateure gemockt). */
class SessionAuthenticationFilterTest {

  private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
  private static final Duration TTL = Duration.ofDays(30);

  private SessionCookieManager cookies;
  private SessionTokens tokens;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain chain;
  private SessionAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    cookies = mock(SessionCookieManager.class);
    tokens = mock(SessionTokens.class);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    chain = mock(FilterChain.class);
    filter =
        new SessionAuthenticationFilter(
            cookies,
            tokens,
            new AuthProperties(null, "secret", TTL, true, null),
            Clock.fixed(NOW, ZoneOffset.UTC));
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_alreadyAuthenticated_doesNotReadCookie() throws Exception {
    // Given
    Authentication existing = new UsernamePasswordAuthenticationToken("someone", null, List.of());
    SecurityContextHolder.getContext().setAuthentication(existing);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    verify(cookies, never()).readToken(request);
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_noCookie_leavesContextEmpty() throws Exception {
    // Given
    when(cookies.readToken(request)).thenReturn(Optional.empty());

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_invalidToken_leavesContextEmpty() throws Exception {
    // Given
    when(cookies.readToken(request)).thenReturn(Optional.of("bad-token"));
    when(tokens.verify("bad-token")).thenReturn(Optional.empty());

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(tokens, never()).issue(anyLong());
    verify(response, never()).addHeader(anyString(), anyString());
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_validToken_setsSessionAuthentication() throws Exception {
    // Given
    when(cookies.readToken(request)).thenReturn(Optional.of("good-token"));
    when(tokens.verify("good-token")).thenReturn(Optional.of(new SessionCheck(42L, NOW.plus(TTL))));

    // When
    filter.doFilter(request, response, chain);

    // Then
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getPrincipal()).isEqualTo(42L);
    assertThat(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority))
        .containsExactly(SessionAuthenticationFilter.AUTHORITY);
    verify(chain).doFilter(request, response);
  }

  /** Ein gerade ausgestelltes Token bleibt, wie es ist: kein neues Token, kein Set-Cookie. */
  @Test
  void doFilter_freshToken_isNotRenewed() throws Exception {
    // Given
    when(cookies.readToken(request)).thenReturn(Optional.of("good-token"));
    when(tokens.verify("good-token")).thenReturn(Optional.of(new SessionCheck(42L, NOW.plus(TTL))));

    // When
    filter.doFilter(request, response, chain);

    // Then
    verify(tokens, never()).issue(anyLong());
    verify(response, never()).addHeader(anyString(), anyString());
  }

  /**
   * Grenze: Liegt die Restlaufzeit genau einen Tag unter der vollen Dauer, ist sie noch nicht „um
   * mehr als einen Tag“ darunter (Plan #1801, A10) — keine Erneuerung.
   */
  @Test
  void doFilter_remainingExactlyOneDayBelowTtl_isNotRenewed() throws Exception {
    // Given
    when(cookies.readToken(request)).thenReturn(Optional.of("good-token"));
    when(tokens.verify("good-token"))
        .thenReturn(Optional.of(new SessionCheck(42L, NOW.plus(TTL).minus(Duration.ofDays(1)))));

    // When
    filter.doFilter(request, response, chain);

    // Then
    verify(tokens, never()).issue(anyLong());
    verify(response, never()).addHeader(anyString(), anyString());
  }

  /**
   * Gleitende Sitzung (Plan #1801, A10, E1): Liegt die Restlaufzeit mehr als einen Tag unter der
   * vollen Dauer, stellt der Filter ein neues Token für dasselbe Konto aus und schreibt es als
   * Cookie zurück. Der Filter unterscheidet keine Pfade — jeder mit dem Cookie gestellte Aufruf
   * zählt als Nutzung, auch eine Hintergrundabfrage der geöffneten Oberfläche.
   */
  @Test
  void doFilter_remainingMoreThanOneDayBelowTtl_renewsCookie() throws Exception {
    // Given
    when(cookies.readToken(request)).thenReturn(Optional.of("good-token"));
    when(tokens.verify("good-token"))
        .thenReturn(
            Optional.of(
                new SessionCheck(42L, NOW.plus(TTL).minus(Duration.ofDays(1)).minusMillis(1))));
    when(tokens.issue(42L)).thenReturn("renewed-token");
    ResponseCookie renewed = ResponseCookie.from("manban_session", "renewed-token").build();
    when(cookies.create("renewed-token")).thenReturn(renewed);

    // When
    filter.doFilter(request, response, chain);

    // Then
    verify(response).addHeader(HttpHeaders.SET_COOKIE, renewed.toString());
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(42L);
    verify(chain).doFilter(request, response);
  }

  /** Ein Token kurz vor seinem Ablauf wird ebenfalls erneuert — die Sitzung gleitet weiter. */
  @Test
  void doFilter_tokenShortlyBeforeExpiry_renewsCookie() throws Exception {
    // Given
    when(cookies.readToken(request)).thenReturn(Optional.of("good-token"));
    when(tokens.verify("good-token"))
        .thenReturn(Optional.of(new SessionCheck(42L, NOW.plus(Duration.ofMinutes(5)))));
    when(tokens.issue(42L)).thenReturn("renewed-token");
    ResponseCookie renewed = ResponseCookie.from("manban_session", "renewed-token").build();
    when(cookies.create("renewed-token")).thenReturn(renewed);

    // When
    filter.doFilter(request, response, chain);

    // Then
    verify(response).addHeader(HttpHeaders.SET_COOKIE, renewed.toString());
  }

  /**
   * Sitzungsende als Details (Issue #1997, Plan #1989 E14): Ein nicht erneuertes Token endet, wann
   * es im Token steht.
   */
  @Test
  void doFilter_notRenewed_setsSitzungsEndeOfToken() throws Exception {
    // Given
    Instant ablauf = NOW.plus(TTL).minus(Duration.ofDays(1));
    when(cookies.readToken(request)).thenReturn(Optional.of("good-token"));
    when(tokens.verify("good-token")).thenReturn(Optional.of(new SessionCheck(42L, ablauf)));

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
        .isEqualTo(new SitzungsEnde(ablauf));
  }

  /** Ein frisch ausgestelltes Token trägt sein eigenes Ende als Details. */
  @Test
  void doFilter_freshToken_setsSitzungsEndeOfToken() throws Exception {
    // Given
    when(cookies.readToken(request)).thenReturn(Optional.of("good-token"));
    when(tokens.verify("good-token")).thenReturn(Optional.of(new SessionCheck(42L, NOW.plus(TTL))));

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
        .isEqualTo(new SitzungsEnde(NOW.plus(TTL)));
  }

  /**
   * Nach einer Erneuerung gilt das Ende des neuen Tokens — jetzt plus die volle Sitzungsdauer,
   * nicht das alte Ende aus dem Cookie der Anfrage.
   */
  @Test
  void doFilter_renewed_setsSitzungsEndeOfRenewedToken() throws Exception {
    // Given
    when(cookies.readToken(request)).thenReturn(Optional.of("good-token"));
    when(tokens.verify("good-token"))
        .thenReturn(Optional.of(new SessionCheck(42L, NOW.plus(Duration.ofMinutes(5)))));
    when(tokens.issue(42L)).thenReturn("renewed-token");
    when(cookies.create("renewed-token"))
        .thenReturn(ResponseCookie.from("manban_session", "renewed-token").build());

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
        .isEqualTo(new SitzungsEnde(NOW.plus(TTL)));
  }
}
