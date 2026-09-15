package org.mwolff.manban.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.RequestPasswordResetService;
import org.mwolff.manban.auth.application.ResetPasswordService;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests des Passwort-Reset-Controllers (Services gemockt). Läuft über einen standalone MockMvc
 * statt über direkte Methodenaufrufe: Nur so ist sichtbar, welche {@code Set-Cookie}-Header die
 * Antworten tatsächlich tragen — und genau das unterscheidet das Anfordern eines Resets vom
 * Einlösen.
 */
class PasswordResetControllerTest {

  private RequestPasswordResetService requestReset;
  private ResetPasswordService resetPassword;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    requestReset = mock(RequestPasswordResetService.class);
    resetPassword = mock(ResetPasswordService.class);
    var cookies =
        new SessionCookieManager(
            new AuthProperties(null, null, "secret", Duration.ofDays(7), true, null));
    mvc =
        MockMvcBuilders.standaloneSetup(
                new PasswordResetController(requestReset, resetPassword, cookies))
            .build();
  }

  private MvcResult forgot() throws Exception {
    return mvc.perform(
            post("/api/auth/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.de\"}"))
        .andExpect(status().isOk())
        .andReturn();
  }

  private MvcResult reset() throws Exception {
    return mvc.perform(
            post("/api/auth/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"newPassword\":\"newpass12\"}"))
        .andExpect(status().isNoContent())
        .andReturn();
  }

  @Test
  void forgot_delegatesEmail() throws Exception {
    // When
    forgot();

    // Then
    verify(requestReset).requestReset("a@b.de");
  }

  @Test
  void forgot_leavesSessionCookieUntouched() throws Exception {
    // When
    MvcResult result = forgot();

    // Then: das blosse Anfordern beendet keine Sitzung — auch nicht die des eigenen Geräts (AK 3).
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
  }

  @Test
  void reset_delegatesTokenAndPassword() throws Exception {
    // When
    reset();

    // Then
    verify(resetPassword).reset("tok", "newpass12");
  }

  @Test
  void reset_clearsSessionCookieOfCallingDevice() throws Exception {
    // When
    MvcResult result = reset();

    // Then: auch der auslösende Browser schickt danach kein totes Cookie mehr mit.
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
        .isNotNull()
        .contains(SessionCookieManager.COOKIE_NAME + "=")
        .contains("Max-Age=0");
  }
}
