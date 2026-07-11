package org.mwolff.manban.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Unit-Tests des Session-Authentifizierungs-Filters (Kollaborateure gemockt). */
class SessionAuthenticationFilterTest {

  private SessionCookieManager cookies;
  private SignedSessionTokens tokens;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain chain;
  private SessionAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    cookies = mock(SessionCookieManager.class);
    tokens = mock(SignedSessionTokens.class);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    chain = mock(FilterChain.class);
    filter = new SessionAuthenticationFilter(cookies, tokens);
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
    when(tokens.verify("bad-token")).thenReturn(OptionalLong.empty());

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_validToken_setsSessionAuthentication() throws Exception {
    // Given
    when(cookies.readToken(request)).thenReturn(Optional.of("good-token"));
    when(tokens.verify("good-token")).thenReturn(OptionalLong.of(42L));

    // When
    filter.doFilter(request, response, chain);

    // Then
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getPrincipal()).isEqualTo(42L);
    assertThat(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority))
        .containsExactly(SessionAuthenticationFilter.AUTHORITY);
    verify(chain).doFilter(request, response);
  }
}
