package org.mwolff.manban.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AuthProperties;
import org.springframework.http.ResponseCookie;

/** Unit-Tests des Session-Cookie-Managers (Request gemockt). */
class SessionCookieManagerTest {

  private static final Duration TTL = Duration.ofDays(7);

  private HttpServletRequest request;
  private SessionCookieManager cookies;

  private static AuthProperties properties(boolean secure) {
    return new AuthProperties(null, null, "secret", TTL, secure, null);
  }

  @BeforeEach
  void setUp() {
    request = mock(HttpServletRequest.class);
    cookies = new SessionCookieManager(properties(true));
  }

  @Test
  void create_buildsSecureHttpOnlyStrictCookieWithTtl() {
    // When
    ResponseCookie cookie = cookies.create("token123");

    // Then
    assertThat(cookie.getName()).isEqualTo(SessionCookieManager.COOKIE_NAME);
    assertThat(cookie.getValue()).isEqualTo("token123");
    assertThat(cookie.getMaxAge()).isEqualTo(TTL);
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Strict");
    assertThat(cookie.getPath()).isEqualTo("/");
  }

  @Test
  void create_honorsInsecureFlag() {
    // Given
    var insecure = new SessionCookieManager(properties(false));

    // When
    ResponseCookie cookie = insecure.create("token123");

    // Then
    assertThat(cookie.isSecure()).isFalse();
  }

  @Test
  void clear_buildsExpiredEmptyCookie() {
    // When
    ResponseCookie cookie = cookies.clear();

    // Then
    assertThat(cookie.getValue()).isEmpty();
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
  }

  @Test
  void readToken_noCookies_returnsEmpty() {
    // Given
    when(request.getCookies()).thenReturn(null);

    // When
    Optional<String> result = cookies.readToken(request);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void readToken_noMatchingName_returnsEmpty() {
    // Given
    when(request.getCookies()).thenReturn(new Cookie[] {new Cookie("other", "value")});

    // When
    Optional<String> result = cookies.readToken(request);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void readToken_nullValue_returnsEmpty() {
    // Given
    Cookie cookie = new Cookie(SessionCookieManager.COOKIE_NAME, "placeholder");
    cookie.setValue(null);
    when(request.getCookies()).thenReturn(new Cookie[] {cookie});

    // When
    Optional<String> result = cookies.readToken(request);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void readToken_blankValue_returnsEmpty() {
    // Given
    Cookie cookie = new Cookie(SessionCookieManager.COOKIE_NAME, "");
    when(request.getCookies()).thenReturn(new Cookie[] {cookie});

    // When
    Optional<String> result = cookies.readToken(request);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void readToken_matchingCookie_returnsValue() {
    // Given
    when(request.getCookies())
        .thenReturn(new Cookie[] {new Cookie(SessionCookieManager.COOKIE_NAME, "token123")});

    // When
    Optional<String> result = cookies.readToken(request);

    // Then
    assertThat(result).hasValue("token123");
  }
}
