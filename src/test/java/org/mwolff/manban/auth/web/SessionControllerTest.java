package org.mwolff.manban.auth.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/** Unit-Tests des Logout-Controllers (Kollaborateure gemockt). */
class SessionControllerTest {

  private SessionCookieManager cookies;
  private HttpServletResponse response;
  private SessionController controller;

  @BeforeEach
  void setUp() {
    cookies = mock(SessionCookieManager.class);
    response = mock(HttpServletResponse.class);
    controller = new SessionController(cookies);
  }

  @Test
  void logout_clearsSessionCookie() {
    // Given
    var cookie = ResponseCookie.from("manban_session", "").maxAge(0).build();
    when(cookies.clear()).thenReturn(cookie);

    // When
    controller.logout(response);

    // Then
    verify(response).addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
