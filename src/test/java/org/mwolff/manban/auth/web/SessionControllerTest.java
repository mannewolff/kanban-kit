package org.mwolff.manban.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.LoginService;
import org.mwolff.manban.auth.application.MeService;
import org.mwolff.manban.auth.application.MeService.MeView;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.infrastructure.security.SessionCookieManager;
import org.mwolff.manban.auth.infrastructure.security.SignedSessionTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/** Unit-Tests des Login/Logout-Controllers (Kollaborateure gemockt). */
class SessionControllerTest {

  private LoginService loginService;
  private MeService meService;
  private SignedSessionTokens tokens;
  private SessionCookieManager cookies;
  private HttpServletResponse response;
  private SessionController controller;

  @BeforeEach
  void setUp() {
    loginService = mock(LoginService.class);
    meService = mock(MeService.class);
    tokens = mock(SignedSessionTokens.class);
    cookies = mock(SessionCookieManager.class);
    response = mock(HttpServletResponse.class);
    controller = new SessionController(loginService, meService, tokens, cookies);
  }

  @Test
  void login_returnsMeView() {
    // Given
    var user = new AppUser(7L, "a@b.de", "hash", "Alice", true, PlatformRole.USER);
    var meView = new MeView(7L, "a@b.de", "Alice", PlatformRole.USER, List.of());
    when(loginService.login("a@b.de", "pw")).thenReturn(user);
    when(tokens.issue(7L)).thenReturn("token123");
    when(cookies.create("token123")).thenReturn(ResponseCookie.from("manban_session", "x").build());
    when(meService.load(7L)).thenReturn(meView);

    // When
    MeView result = controller.login(new LoginRequest("a@b.de", "pw"), response);

    // Then
    assertThat(result).isSameAs(meView);
  }

  @Test
  void login_setsSessionCookieHeader() {
    // Given
    var user = new AppUser(7L, "a@b.de", "hash", "Alice", true, PlatformRole.USER);
    var cookie = ResponseCookie.from("manban_session", "x").build();
    when(loginService.login("a@b.de", "pw")).thenReturn(user);
    when(tokens.issue(7L)).thenReturn("token123");
    when(cookies.create("token123")).thenReturn(cookie);
    when(meService.load(7L))
        .thenReturn(new MeView(7L, "a@b.de", "Alice", PlatformRole.USER, List.of()));

    // When
    controller.login(new LoginRequest("a@b.de", "pw"), response);

    // Then
    verify(response).addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
