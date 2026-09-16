package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.PasswordResetMailer;
import org.mwolff.manban.auth.application.PasswordResetTokenRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PasswordResetToken;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-End-Test des Passwort-Resets gegen echtes Postgres. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PasswordResetIT extends AbstractIntegrationTest {

  private static final String OLD_PASSWORD = "old-password-123";
  private static final String NEW_PASSWORD = "new-password-456";

  @TestConfiguration
  static class MailTestConfig {
    @Bean
    @Primary
    CapturingResetMailer capturingResetMailer() {
      return new CapturingResetMailer();
    }
  }

  static class CapturingResetMailer implements PasswordResetMailer {
    volatile String lastUrl;

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
      this.lastUrl = resetUrl;
    }

    String lastToken() {
      return lastUrl.substring(lastUrl.indexOf("token=") + "token=".length());
    }
  }

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository users;

  @Autowired private PasswordResetTokenRepository tokens;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private CapturingResetMailer mailer;

  private void createVerifiedUser(String email) {
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null,
              email,
              passwordEncoder.encode(OLD_PASSWORD),
              "Person",
              true,
              PlatformRole.USER));
    }
  }

  private void forgot(String email) throws Exception {
    mvc.perform(
            post("/api/auth/forgot")
                .contentType("application/json")
                .content("{\"email\":\"%s\"}".formatted(email)))
        .andExpect(status().isOk());
  }

  private void login(String email, String password, int expectedStatus) throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
        .andExpect(status().is(expectedStatus));
  }

  private void reset(String token, String newPassword, int expectedStatus) throws Exception {
    mvc.perform(
            post("/api/auth/reset")
                .contentType("application/json")
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)))
        .andExpect(status().is(expectedStatus));
  }

  /** Meldet an und gibt das Session-Cookie der Antwort zurück. */
  private Cookie loginForCookie(String email, String password) throws Exception {
    MvcResult login =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();

    Cookie session = login.getResponse().getCookie("manban_session");
    assertThat(session).isNotNull();
    return session;
  }

  private void me(Cookie session, int expectedStatus) throws Exception {
    mvc.perform(get("/api/me").cookie(session)).andExpect(status().is(expectedStatus));
  }

  /** AK 1 + AK 2: Der Reset beendet jede Sitzung des Kontos, auch die des auslösenden Geräts. */
  @Test
  void resetEndsEverySessionOfTheAccount() throws Exception {
    String email = "reset-sessions@example.com";
    createVerifiedUser(email);

    Cookie phone = loginForCookie(email, OLD_PASSWORD);
    Cookie laptop = loginForCookie(email, OLD_PASSWORD);
    me(phone, 200);
    me(laptop, 200);

    forgot(email);
    reset(mailer.lastToken(), NEW_PASSWORD, 204);

    me(phone, 401);
    me(laptop, 401);
  }

  /** AK 3: Das blosse Anfordern eines Resets beendet noch keine Sitzung. */
  @Test
  void forgotAloneKeepsSessionsAlive() throws Exception {
    String email = "reset-forgot-only@example.com";
    createVerifiedUser(email);

    Cookie session = loginForCookie(email, OLD_PASSWORD);

    forgot(email);

    me(session, 200);
  }

  /** Nach dem Reset trägt die neue Anmeldung wieder eine gültige Sitzung. */
  @Test
  void loginAfterResetYieldsWorkingSession() throws Exception {
    String email = "reset-relogin@example.com";
    createVerifiedUser(email);

    forgot(email);
    reset(mailer.lastToken(), NEW_PASSWORD, 204);

    me(loginForCookie(email, NEW_PASSWORD), 200);
  }

  /** Die Entwertung ist kontogebunden — fremde Sitzungen bleiben unberührt. */
  @Test
  void resetLeavesOtherAccountsSessionsAlive() throws Exception {
    String owner = "reset-owner@example.com";
    String bystander = "reset-bystander@example.com";
    createVerifiedUser(owner);
    createVerifiedUser(bystander);

    Cookie bystanderSession = loginForCookie(bystander, OLD_PASSWORD);

    forgot(owner);
    reset(mailer.lastToken(), NEW_PASSWORD, 204);

    me(bystanderSession, 200);
  }

  @Test
  void forgotThenResetThenLoginWithNewPassword() throws Exception {
    String email = "reset-happy@example.com";
    createVerifiedUser(email);

    forgot(email);
    String token = mailer.lastToken();

    reset(token, NEW_PASSWORD, 204);

    login(email, NEW_PASSWORD, 200);
    login(email, OLD_PASSWORD, 401);
  }

  @Test
  void forgotUnknownEmailReturns200AndSendsNothing() throws Exception {
    mailer.lastUrl = null;
    forgot("nobody-here@example.com");
    assertThat(mailer.lastUrl).isNull();
  }

  @Test
  void resetWithInvalidTokenReturns400() throws Exception {
    reset("does-not-exist", NEW_PASSWORD, 400);
  }

  @Test
  void resetWithExpiredTokenReturns400() throws Exception {
    String email = "reset-expired@example.com";
    createVerifiedUser(email);
    Long userId = users.findByEmail(email).orElseThrow().id();

    String plaintext = "abgelaufener-reset";
    tokens.save(
        new PasswordResetToken(
            null,
            userId,
            SecureTokens.sha256Hex(plaintext),
            Instant.now().minusSeconds(3600),
            null));

    reset(plaintext, NEW_PASSWORD, 400);
    login(email, OLD_PASSWORD, 200); // Passwort unverändert
  }

  @Test
  void resetTokenIsSingleUse() throws Exception {
    String email = "reset-single@example.com";
    createVerifiedUser(email);

    forgot(email);
    String token = mailer.lastToken();

    reset(token, NEW_PASSWORD, 204);
    reset(token, "another-password-789", 400); // zweite Einlösung schlägt fehl
  }
}
