package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-End-Test des echten SMTP-Sendepfads (Issue #78): Registrierung und Passwort-Reset
 * versenden über {@code mailSender.send()} echte E-Mails an einen Mailpit-Container; ein
 * SMTP-Fehler rollt die Registrierung zurück. Anders als die übrigen Auth-ITs läuft hier KEIN
 * Mailer-Test-Double — geprüft wird der komplette Pfad API → JavaMail → SMTP → Mailpit-Postfach.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SmtpMailIT extends AbstractIntegrationTest {

  private static final int SMTP_PORT = 1025;
  private static final int HTTP_API_PORT = 8025;
  private static final String FROM = "no-reply@manban.test";
  private static final String VERIFY_SUBJECT = "manban: E-Mail bestätigen";
  private static final String RESET_SUBJECT = "manban: Passwort zurücksetzen";
  private static final String APPROVAL_NOTIFICATION_SUBJECT =
      "manban: Neue Registrierung wartet auf Freigabe";

  @Container
  static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27"))
          .withExposedPorts(SMTP_PORT, HTTP_API_PORT);

  @DynamicPropertySource
  static void mailProperties(DynamicPropertyRegistry registry) {
    registry.add("manban.mail.enabled", () -> "true");
    registry.add("manban.mail.from", () -> FROM);
    registry.add("spring.mail.host", MAILPIT::getHost);
    registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(SMTP_PORT));
    // Mailpit spricht plain SMTP — Auth/STARTTLS (Produktions-Default: an) hier deaktivieren.
    registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
    registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
    registry.add("spring.mail.properties.mail.smtp.starttls.required", () -> "false");
    // EHLO-Name und Message-ID-Domain fest vorgeben: erspart pro send() zwei minutenlange
    // macOS-Lookups des eigenen Hostnamens (InetAddress.getLocalHost) in Jakarta Mail.
    registry.add("spring.mail.properties.mail.smtp.localhost", () -> "localhost");
    registry.add("spring.mail.properties.mail.from", () -> FROM);
  }

  @LocalServerPort private int port;

  @Autowired private AppUserRepository users;

  @Autowired private JavaMailSender mailSender;

  private final HttpClient http = HttpClient.newHttpClient();

  private static String registerBody(String email) {
    return """
                {"email":"%s","password":"sup3r-secret","displayName":"Alice"}
                """
        .formatted(email);
  }

  private HttpResponse<String> post(String path, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private String mailpitGet(String path) throws Exception {
    URI uri =
        URI.create(
            "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(HTTP_API_PORT) + path);
    return http.send(
            HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
        .body();
  }

  /**
   * Liefert das Mailpit-Message-Detail (Volltext) der Mail an {@code email} mit {@code subject};
   * pollt kurz, weil Mailpit die Mail nach dem SMTP-Handshake asynchron ablegen kann.
   */
  private String awaitMessage(String email, String subject) throws Exception {
    String filter =
        "$.messages[?(@.To[0].Address=='" + email + "' && @.Subject=='" + subject + "')].ID";
    for (int attempt = 0; attempt < 50; attempt++) {
      List<String> ids = JsonPath.read(mailpitGet("/api/v1/messages"), filter);
      if (!ids.isEmpty()) {
        assertThat(ids).hasSize(1);
        return mailpitGet("/api/v1/message/" + ids.get(0));
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Keine Mail an " + email + " mit Betreff '" + subject + "' gefunden");
  }

  private static String tokenFrom(String mailText) {
    return mailText.substring(mailText.indexOf("token=") + "token=".length()).trim();
  }

  /** Legt einen bereits freigegebenen Admin an (Bequem-Konstruktor, kein Registrierungspfad). */
  private long seedApprovedAdmin(String email) {
    return users
        .save(new AppUser(null, email, "hash", "Admin", true, PlatformRole.ADMIN))
        .requireId();
  }

  @Test
  void registrationSendsVerificationMailOverSmtp() throws Exception {
    String email = "smtp-register@example.com";

    assertThat(post("/api/auth/register", registerBody(email)).statusCode()).isEqualTo(201);

    String message = awaitMessage(email, VERIFY_SUBJECT);
    assertThat(JsonPath.<String>read(message, "$.From.Address")).isEqualTo(FROM);
    assertThat(JsonPath.<String>read(message, "$.To[0].Address")).isEqualTo(email);
    String text = JsonPath.read(message, "$.Text");
    // Mail verlinkt auf die Frontend-Seite /verify (nicht den API-Endpunkt).
    assertThat(text).contains("/verify?token=").doesNotContain("/api/auth/verify");

    // Der Token aus der Mail funktioniert am API-Endpunkt: einlösen, User ist danach verifiziert.
    assertThat(get("/api/auth/verify?token=" + tokenFrom(text)).statusCode()).isEqualTo(200);
    assertThat(users.findByEmail(email))
        .get()
        .satisfies(u -> assertThat(u.emailVerified()).isTrue());
  }

  @Test
  void passwordResetSendsMailOverSmtp() throws Exception {
    String email = "smtp-reset@example.com";
    assertThat(post("/api/auth/register", registerBody(email)).statusCode()).isEqualTo(201);

    assertThat(post("/api/auth/forgot", "{\"email\":\"%s\"}".formatted(email)).statusCode())
        .isEqualTo(200);

    String message = awaitMessage(email, RESET_SUBJECT);
    assertThat(JsonPath.<String>read(message, "$.From.Address")).isEqualTo(FROM);
    assertThat(JsonPath.<String>read(message, "$.To[0].Address")).isEqualTo(email);
    // Mail verlinkt auf die Frontend-Seite /reset (nicht den API-Endpunkt).
    assertThat(JsonPath.<String>read(message, "$.Text"))
        .contains("/reset?token=")
        .doesNotContain("/api/auth/reset");
  }

  @Test
  void smtpFailureFailsRegistrationWithoutUserZombie() throws Exception {
    String email = "smtp-dead@example.com";
    JavaMailSenderImpl sender = (JavaMailSenderImpl) mailSender;
    int mailpitPort = sender.getPort();
    sender.setPort(deadPort());
    try {
      assertThat(post("/api/auth/register", registerBody(email)).statusCode()).isEqualTo(500);
    } finally {
      sender.setPort(mailpitPort);
    }
    // Rollback bewiesen: trotz angelegtem Datensatz vor dem Sendeversuch existiert kein User.
    assertThat(users.findByEmail(email)).isEmpty();
  }

  @Test
  void verifyingPendingUserNotifiesAllAdminsOverSmtp() throws Exception {
    seedApprovedAdmin("admin1-notify@example.com");
    seedApprovedAdmin("admin2-notify@example.com");

    String email = "smtp-pending@example.com";
    assertThat(post("/api/auth/register", registerBody(email)).statusCode()).isEqualTo(201);
    String verifyMail = awaitMessage(email, VERIFY_SUBJECT);
    String token = tokenFrom(JsonPath.read(verifyMail, "$.Text"));

    assertThat(get("/api/auth/verify?token=" + token).statusCode()).isEqualTo(200);

    // Je Admin genau eine Freigabe-Benachrichtigung mit den Daten des neuen Nutzers.
    String adminMail1 = awaitMessage("admin1-notify@example.com", APPROVAL_NOTIFICATION_SUBJECT);
    String adminMail2 = awaitMessage("admin2-notify@example.com", APPROVAL_NOTIFICATION_SUBJECT);
    assertThat(JsonPath.<String>read(adminMail1, "$.Text")).contains(email).contains("Alice");
    assertThat(JsonPath.<String>read(adminMail2, "$.Text")).contains(email);
  }

  @Test
  void verifyingAlreadyApprovedUserSendsNoAdminNotification() throws Exception {
    seedApprovedAdmin("admin3-notify@example.com");

    String email = "smtp-approved@example.com";
    assertThat(post("/api/auth/register", registerBody(email)).statusCode()).isEqualTo(201);
    String verifyMail = awaitMessage(email, VERIFY_SUBJECT);
    String token = tokenFrom(JsonPath.read(verifyMail, "$.Text"));

    // Nutzer ist vor der E-Mail-Bestätigung bereits freigegeben — Stellvertreter für
    // "bereits freigegeben" (z. B. per Einladung, #0099), ohne die Invitation-Maschinerie
    // dieses Auth-Tests zu belasten.
    AppUser user = users.findByEmail(email).orElseThrow();
    users.save(user.withApproved(Instant.now(), null));

    assertThat(get("/api/auth/verify?token=" + token).statusCode()).isEqualTo(200);

    // Negativer Nachweis: kurz warten (Mailpit legt Mails asynchron ab), dann sicherstellen,
    // dass keine Freigabe-Benachrichtigung ankam.
    Thread.sleep(500);
    List<String> ids =
        JsonPath.read(
            mailpitGet("/api/v1/messages"),
            "$.messages[?(@.To[0].Address=='admin3-notify@example.com')].ID");
    assertThat(ids).isEmpty();
  }

  /** Reserviert kurz einen freien Port und gibt ihn geschlossen zurück — dort lauscht niemand. */
  private static int deadPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
