package org.mwolff.manban.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminVorschauService;
import org.mwolff.manban.auth.application.VorschauKontext;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.mwolff.manban.auth.web.security.VorschauCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/** Unit-Tests des Logout-Controllers (Kollaborateure gemockt). */
class SessionControllerTest {

  private static final ResponseCookie SESSION_LEER =
      ResponseCookie.from("manban_session", "").maxAge(0).build();
  private static final ResponseCookie VORSCHAU_LEER =
      ResponseCookie.from("manban_vorschau", "").maxAge(0).build();

  private SessionCookieManager cookies;
  private AdminVorschauService vorschauen;
  private Optional<VorschauKontext> kontext;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private SessionController controller;

  @BeforeEach
  void setUp() {
    cookies = mock(SessionCookieManager.class);
    vorschauen = mock(AdminVorschauService.class);
    kontext = Optional.empty();
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    controller = new SessionController(cookies, vorschauen, () -> kontext);
    when(cookies.clear()).thenReturn(SESSION_LEER);
    when(cookies.clearVorschau()).thenReturn(VORSCHAU_LEER);
    when(cookies.readVorschau(request)).thenReturn(Optional.empty());
  }

  @Test
  void logout_clearsSessionCookie() {
    // When
    controller.logout(request, response);

    // Then
    verify(response).addHeader(HttpHeaders.SET_COOKIE, SESSION_LEER.toString());
  }

  @Test
  void logout_clearsVorschauCookieToo() {
    // When
    controller.logout(request, response);

    // Then
    verify(response).addHeader(HttpHeaders.SET_COOKIE, VORSCHAU_LEER.toString());
    verify(vorschauen, never()).beendeBeimAbmelden(any(), any(), anyLong());
  }

  @Test
  void logout_inVorschau_endsItWithAbmeldungAndClearsBothCookies() {
    // Given
    kontext =
        Optional.of(new VorschauKontext("kennung", 1L, 7L, Instant.parse("2026-10-27T10:30:00Z")));
    when(cookies.readVorschau(request))
        .thenReturn(Optional.of(new VorschauCookie("kennung", "geheimnis")));

    // When
    controller.logout(request, response);

    // Then
    verify(vorschauen).beendeBeimAbmelden("kennung", "geheimnis", 1L);
    verify(response).addHeader(HttpHeaders.SET_COOKIE, SESSION_LEER.toString());
    verify(response).addHeader(HttpHeaders.SET_COOKIE, VORSCHAU_LEER.toString());
  }

  @Test
  void logout_withCookieButWithoutValidVorschau_endsNothing() {
    // Given: Cookie ohne gültige Vorschau — der Filter hat keinen Kontext abgelegt.
    when(cookies.readVorschau(request))
        .thenReturn(Optional.of(new VorschauCookie("kennung", "geheimnis")));

    // When
    controller.logout(request, response);

    // Then
    verify(vorschauen, never()).beendeBeimAbmelden(any(), any(), anyLong());
    verify(response).addHeader(HttpHeaders.SET_COOKIE, VORSCHAU_LEER.toString());
  }

  @Test
  void logout_withVorschauKontextButWithoutCookie_endsNothing() {
    // Given
    kontext =
        Optional.of(new VorschauKontext("kennung", 1L, 7L, Instant.parse("2026-10-27T10:30:00Z")));

    // When
    controller.logout(request, response);

    // Then
    verify(vorschauen, never()).beendeBeimAbmelden(any(), any(), anyLong());
    verify(response).addHeader(HttpHeaders.SET_COOKIE, VORSCHAU_LEER.toString());
  }
}
