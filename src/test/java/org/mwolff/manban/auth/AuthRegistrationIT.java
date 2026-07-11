package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.EmailVerificationTokenRepository;
import org.mwolff.manban.auth.application.VerificationMailer;
import org.mwolff.manban.auth.domain.EmailVerificationToken;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-End-Test der Registrierung + E-Mail-Verifikation gegen echtes Postgres. Der Mailer wird
 * durch ein Test-Double ersetzt, das den Verifikations-Link (und damit das Klartext-Token)
 * mitschneidet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AuthRegistrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @TestConfiguration
  static class MailTestConfig {
    @Bean
    @Primary
    CapturingMailer capturingMailer() {
      return new CapturingMailer();
    }
  }

  /** Fängt den Verifikations-Link ab, statt eine E-Mail zu versenden. */
  static class CapturingMailer implements VerificationMailer {
    volatile String lastUrl;

    @Override
    public void sendVerificationEmail(String toEmail, String verificationUrl) {
      this.lastUrl = verificationUrl;
    }

    String lastToken() {
      int idx = lastUrl.indexOf("token=");
      return lastUrl.substring(idx + "token=".length());
    }
  }

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository users;

  @Autowired private EmailVerificationTokenRepository tokens;

  @Autowired private CapturingMailer mailer;

  private static String registerBody(String email) {
    return """
                {"email":"%s","password":"sup3r-secret","displayName":"Alice"}
                """
        .formatted(email);
  }

  @Test
  void registerHappyPathThenVerify() throws Exception {
    String email = "happy@example.com";

    mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody(email)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.emailVerified").value(false));

    assertThat(users.findByEmail(email))
        .get()
        .satisfies(u -> assertThat(u.emailVerified()).isFalse());

    // Verifikation über das mitgeschnittene Klartext-Token.
    mvc.perform(get("/api/auth/verify").param("token", mailer.lastToken()))
        .andExpect(status().isOk());

    assertThat(users.findByEmail(email))
        .get()
        .satisfies(u -> assertThat(u.emailVerified()).isTrue());
  }

  @Test
  void duplicateEmailReturns409() throws Exception {
    String email = "dupe@example.com";
    mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody(email)))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody(email)))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidTokenReturns400() throws Exception {
    mvc.perform(get("/api/auth/verify").param("token", "does-not-exist"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void expiredTokenReturns400() throws Exception {
    String email = "expired@example.com";
    mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody(email)))
        .andExpect(status().isCreated());
    Long userId = users.findByEmail(email).orElseThrow().id();

    String plaintext = "abgelaufenes-token";
    tokens.save(
        new EmailVerificationToken(
            null,
            userId,
            SecureTokens.sha256Hex(plaintext),
            Instant.now().minusSeconds(3600),
            null));

    mvc.perform(get("/api/auth/verify").param("token", plaintext))
        .andExpect(status().isBadRequest());

    // Das abgelaufene Token darf die E-Mail NICHT verifiziert haben.
    assertThat(users.findByEmail(email))
        .get()
        .satisfies(u -> assertThat(u.emailVerified()).isFalse());
  }

  @Test
  void invalidRequestBodyReturns400() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content("{\"email\":\"not-an-email\",\"password\":\"x\",\"displayName\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
