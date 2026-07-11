package org.mwolff.manban.accesstoken.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.AccessTokenService;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Unit-Tests des PAT-Authentifizierungs-Filters (Service + Servlet-API gemockt). */
class PatAuthenticationFilterTest {

  private AccessTokenService accessTokens;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain chain;
  private PatAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    accessTokens = mock(AccessTokenService.class);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    chain = mock(FilterChain.class);
    filter = new PatAuthenticationFilter(accessTokens);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_missingHeader_leavesContextEmptyAndContinues() throws Exception {
    // Given
    when(request.getHeader(PatAuthenticationFilter.HEADER)).thenReturn(null);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_blankHeader_leavesContextEmpty() throws Exception {
    // Given
    when(request.getHeader(PatAuthenticationFilter.HEADER)).thenReturn("   ");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_alreadyAuthenticated_doesNotResolveToken() throws Exception {
    // Given
    Authentication existing =
        new UsernamePasswordAuthenticationToken("someone", null, java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(existing);
    when(request.getHeader(PatAuthenticationFilter.HEADER)).thenReturn("tk_x");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    verify(accessTokens, never()).resolveBinding(org.mockito.ArgumentMatchers.anyString());
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_unknownToken_leavesContextEmpty() throws Exception {
    // Given
    when(request.getHeader(PatAuthenticationFilter.HEADER)).thenReturn("tk_x");
    when(accessTokens.resolveBinding("tk_x")).thenReturn(Optional.empty());

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_validToken_setsPatAuthenticationWithPrincipalDetails() throws Exception {
    // Given
    var principal = new KanbanPrincipal(7L, 1L, 2L, 3L);
    when(request.getHeader(PatAuthenticationFilter.HEADER)).thenReturn("tk_x");
    when(accessTokens.resolveBinding("tk_x")).thenReturn(Optional.of(principal));

    // When
    filter.doFilter(request, response, chain);

    // Then
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getPrincipal()).isEqualTo(7L);
    assertThat(authentication.getDetails()).isSameAs(principal);
    assertThat(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority))
        .containsExactly(PatAuthenticationFilter.AUTHORITY);
    verify(chain).doFilter(request, response);
  }
}
