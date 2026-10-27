package org.mwolff.manban.auth.web.security;

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
    return new AuthProperties(null, "secret", TTL, secure, null);
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

  @Test
  void createVorschau_buildsSecureHttpOnlyStrictBrowserSessionCookie() {
    // When
    ResponseCookie cookie = cookies.createVorschau("kennung", "geheimnis");

    // Then
    assertThat(cookie.getName())
        .isEqualTo(SessionCookieManager.VORSCHAU_COOKIE_NAME)
        .isEqualTo("manban_vorschau");
    assertThat(cookie.getValue()).isEqualTo("kennung.geheimnis");
    assertThat(cookie.getMaxAge().isNegative()).isTrue();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Strict");
    assertThat(cookie.getPath()).isEqualTo("/");
  }

  @Test
  void createVorschau_honorsInsecureFlag() {
    // Given
    var insecure = new SessionCookieManager(properties(false));

    // When
    ResponseCookie cookie = insecure.createVorschau("kennung", "geheimnis");

    // Then
    assertThat(cookie.isSecure()).isFalse();
  }

  @Test
  void clearVorschau_buildsExpiredEmptyCookieOfTheSameName() {
    // When
    ResponseCookie cookie = cookies.clearVorschau();

    // Then
    assertThat(cookie.getName()).isEqualTo(SessionCookieManager.VORSCHAU_COOKIE_NAME);
    assertThat(cookie.getValue()).isEmpty();
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Strict");
    assertThat(cookie.getPath()).isEqualTo("/");
  }

  @Test
  void readVorschau_matchingCookie_returnsKennungAndGeheimnis() {
    // Given
    when(request.getCookies())
        .thenReturn(
            new Cookie[] {
              new Cookie(SessionCookieManager.COOKIE_NAME, "token123"),
              new Cookie(SessionCookieManager.VORSCHAU_COOKIE_NAME, "kennung.geheimnis")
            });

    // When
    Optional<VorschauCookie> result = cookies.readVorschau(request);

    // Then
    assertThat(result).hasValue(new VorschauCookie("kennung", "geheimnis"));
  }

  @Test
  void readVorschau_doesNotTakeTheSessionCookie() {
    // Given
    when(request.getCookies())
        .thenReturn(new Cookie[] {new Cookie(SessionCookieManager.COOKIE_NAME, "a.b")});

    // When / Then
    assertThat(cookies.readVorschau(request)).isEmpty();
  }

  @Test
  void readToken_doesNotTakeTheVorschauCookie() {
    // Given
    when(request.getCookies())
        .thenReturn(new Cookie[] {new Cookie(SessionCookieManager.VORSCHAU_COOKIE_NAME, "a.b")});

    // When / Then
    assertThat(cookies.readToken(request)).isEmpty();
  }

  @Test
  void readVorschau_noCookies_returnsEmpty() {
    // Given
    when(request.getCookies()).thenReturn(null);

    // When / Then
    assertThat(cookies.readVorschau(request)).isEmpty();
  }

  @Test
  void readVorschau_malformedValues_returnEmpty() {
    for (String wert : new String[] {"", "ohnePunkt", ".geheimnis", "kennung.", "a.b.c"}) {
      // Given
      when(request.getCookies())
          .thenReturn(new Cookie[] {new Cookie(SessionCookieManager.VORSCHAU_COOKIE_NAME, wert)});

      // When / Then
      assertThat(cookies.readVorschau(request)).as(wert).isEmpty();
    }
  }

  @Test
  void readVorschau_nullValue_returnsEmpty() {
    // Given
    Cookie cookie = new Cookie(SessionCookieManager.VORSCHAU_COOKIE_NAME, "placeholder");
    cookie.setValue(null);
    when(request.getCookies()).thenReturn(new Cookie[] {cookie});

    // When / Then
    assertThat(cookies.readVorschau(request)).isEmpty();
  }

  @Test
  void vorschauCookie_toStringOmitsTheGeheimnis() {
    assertThat(new VorschauCookie("kennung", "geheimnis"))
        .hasToString("VorschauCookie[kennung=kennung]");
  }
}
