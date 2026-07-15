package org.mwolff.manban.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Unit-Tests des Sperr-Guard-Filters (AppUserRepository gemockt). */
class DisabledUserGuardFilterTest {

  private AppUserRepository users;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain chain;
  private DisabledUserGuardFilter filter;

  private static AppUser user(long id, java.time.Instant disabledAt) {
    return new AppUser(
        id, "a@b.de", "h", "A", true, PlatformRole.USER, java.time.Instant.EPOCH, null, disabledAt);
  }

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    chain = mock(FilterChain.class);
    filter = new DisabledUserGuardFilter(users);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
  }

  @Test
  void clearsContext_whenUserDisabled() throws Exception {
    authenticate(42L);
    when(users.findById(42L)).thenReturn(Optional.of(user(42L, java.time.Instant.EPOCH)));

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void keepsContext_whenUserActive() throws Exception {
    authenticate(42L);
    when(users.findById(42L)).thenReturn(Optional.of(user(42L, null)));

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void keepsContext_whenUserUnknown() throws Exception {
    authenticate(42L);
    when(users.findById(42L)).thenReturn(Optional.empty());

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void ignoresRequestsWithoutAuthentication() throws Exception {
    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void ignoresNonLongPrincipal() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("anonymous", null, List.of()));

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    verify(users, never()).findById(anyLong());
    verify(chain).doFilter(request, response);
  }
}
