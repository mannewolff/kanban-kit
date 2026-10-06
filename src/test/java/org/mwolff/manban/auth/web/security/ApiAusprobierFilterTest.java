package org.mwolff.manban.auth.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.auth.application.SessionTokens;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit-Tests des Ausprobier-Filters (Kollaborateure gemockt, Issue #1438). */
class ApiAusprobierFilterTest {

  private static final String SESSION = SessionCookieManager.COOKIE_NAME;

  private SessionCookieManager cookies;
  private SessionTokens tokens;
  private PlatformAdminChecker admins;
  private FilterChain chain;
  private ApiAusprobierFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    cookies = mock(SessionCookieManager.class);
    tokens = mock(SessionTokens.class);
    admins = mock(PlatformAdminChecker.class);
    chain = mock(FilterChain.class);
    filter = new ApiAusprobierFilter(cookies, tokens, admins);
    request = new MockHttpServletRequest("POST", "/api/projects");
    response = new MockHttpServletResponse();
  }

  /** Gekennzeichnete Anfrage eines angemeldeten Nutzers mit gültigem Session-Token. */
  private void signedInWithFlag(long userId, boolean admin) {
    request.addHeader(ApiAusprobierFilter.HEADER, "1");
    when(cookies.readToken(any(HttpServletRequest.class))).thenReturn(Optional.of("good"));
    when(tokens.verify("good")).thenReturn(OptionalLong.of(userId));
    when(admins.isActivePlatformAdmin(userId)).thenReturn(admin);
  }

  private HttpServletRequest forwardedRequest() throws Exception {
    ArgumentCaptor<ServletRequest> captor = ArgumentCaptor.forClass(ServletRequest.class);
    verify(chain).doFilter(captor.capture(), any(ServletResponse.class));
    return (HttpServletRequest) captor.getValue();
  }

  @Test
  void doFilter_withoutHeader_passesSameRequestUnchanged() throws Exception {
    // When
    filter.doFilter(request, response, chain);

    // Then
    verify(chain).doFilter(request, response);
    verifyNoInteractions(cookies, tokens, admins);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void doFilter_headerWithoutCookie_rejectsWith401() throws Exception {
    // Given
    request.addHeader(ApiAusprobierFilter.HEADER, "1");
    when(cookies.readToken(request)).thenReturn(Optional.empty());

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(any(), any());
    verifyNoInteractions(admins);
  }

  @Test
  void doFilter_headerWithInvalidSessionToken_rejectsWith401() throws Exception {
    // Given
    request.addHeader(ApiAusprobierFilter.HEADER, "1");
    when(cookies.readToken(request)).thenReturn(Optional.of("bad"));
    when(tokens.verify("bad")).thenReturn(OptionalLong.empty());

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(any(), any());
    verifyNoInteractions(admins);
  }

  @Test
  void doFilter_nonAdmin_rejectsWith403ProblemDetail() throws Exception {
    // Given
    signedInWithFlag(7L, false);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                + "\"detail\":\"Das Ausprobieren aus der API-Übersicht ist Plattform-Admins"
                + " vorbehalten.\"}");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void doFilter_adminWithoutProjectToken_passesSameRequest() throws Exception {
    // Given
    signedInWithFlag(1L, true);
    request.setCookies(new Cookie(SESSION, "good"));

    // When
    filter.doFilter(request, response, chain);

    // Then
    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void doFilter_adminWithEmptyProjectToken_passesSameRequest() throws Exception {
    // Given
    signedInWithFlag(1L, true);
    request.addHeader(ApiAusprobierFilter.PROJEKT_TOKEN_HEADER, "");

    // When
    filter.doFilter(request, response, chain);

    // Then
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_adminWithBlankProjectToken_passesSameRequest() throws Exception {
    // Given
    signedInWithFlag(1L, true);
    request.addHeader(ApiAusprobierFilter.PROJEKT_TOKEN_HEADER, "   ");

    // When
    filter.doFilter(request, response, chain);

    // Then
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_adminWithProjectToken_forwardsRequestWithoutSessionCookie() throws Exception {
    // Given
    signedInWithFlag(1L, true);
    request.addHeader(ApiAusprobierFilter.PROJEKT_TOKEN_HEADER, "pat-123");
    request.setCookies(new Cookie("theme", "dark"), new Cookie(SESSION, "good"));
    request.removeHeader("Cookie");
    request.addHeader("Cookie", "theme=dark; " + SESSION + "=good");
    request.addHeader("Cookie", SESSION + " =good;; lang=de;");

    // When
    filter.doFilter(request, response, chain);

    // Then
    HttpServletRequest forwarded = forwardedRequest();
    assertThat(forwarded).isNotSameAs(request);
    assertThat(Arrays.stream(forwarded.getCookies()).map(Cookie::getName)).containsExactly("theme");
    assertThat(forwarded.getHeader("Cookie")).isEqualTo("theme=dark");
    assertThat(forwarded.getHeader("cookie")).isEqualTo("theme=dark");
    assertThat(Collections.list(forwarded.getHeaders("Cookie")))
        .containsExactly("theme=dark", "lang=de");
    assertThat(Collections.list(forwarded.getHeaders("COOKIE")))
        .containsExactly("theme=dark", "lang=de");
    assertThat(forwarded.getHeader(ApiAusprobierFilter.PROJEKT_TOKEN_HEADER)).isEqualTo("pat-123");
    assertThat(Collections.list(forwarded.getHeaders(ApiAusprobierFilter.PROJEKT_TOKEN_HEADER)))
        .containsExactly("pat-123");
  }

  @Test
  void doFilter_adminWithProjectTokenAndOnlySessionCookie_leavesNoCookies() throws Exception {
    // Given
    signedInWithFlag(1L, true);
    request.addHeader(ApiAusprobierFilter.PROJEKT_TOKEN_HEADER, "pat-123");
    request.setCookies(new Cookie(SESSION, "good"));
    request.removeHeader("Cookie");
    request.addHeader("Cookie", " " + SESSION + "=good ");

    // When
    filter.doFilter(request, response, chain);

    // Then
    HttpServletRequest forwarded = forwardedRequest();
    assertThat(forwarded.getCookies()).isNull();
    assertThat(forwarded.getHeader("Cookie")).isNull();
    assertThat(Collections.list(forwarded.getHeaders("Cookie"))).isEmpty();
  }

  @Test
  void doFilter_adminWithProjectTokenWithoutCookies_forwardsWrapperWithoutCookies()
      throws Exception {
    // Given — Token kommt aus einem gemockten Lesezugriff, die Anfrage selbst trägt kein Cookie
    signedInWithFlag(1L, true);
    request.addHeader(ApiAusprobierFilter.PROJEKT_TOKEN_HEADER, "pat-123");

    // When
    filter.doFilter(request, response, chain);

    // Then
    HttpServletRequest forwarded = forwardedRequest();
    assertThat(forwarded).isNotSameAs(request);
    assertThat(forwarded.getCookies()).isNull();
    assertThat(forwarded.getHeader("Cookie")).isNull();
    assertThat(Collections.list(forwarded.getHeaders("Cookie"))).isEmpty();
  }

  @Test
  void doFilter_headerValueZero_countsAsFlag() throws Exception {
    // Given
    request.addHeader(ApiAusprobierFilter.HEADER, "0");
    when(cookies.readToken(request)).thenReturn(Optional.of("good"));
    when(tokens.verify("good")).thenReturn(OptionalLong.of(7L));
    when(admins.isActivePlatformAdmin(anyLong())).thenReturn(false);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(response.getStatus()).isEqualTo(403);
    verify(admins).isActivePlatformAdmin(7L);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void doFilter_emptyHeaderValue_countsAsFlag() throws Exception {
    // Given
    request.addHeader(ApiAusprobierFilter.HEADER, "");
    when(cookies.readToken(request)).thenReturn(Optional.empty());

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(any(), any());
  }
}
