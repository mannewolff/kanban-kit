package org.mwolff.manban.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.ratelimit.infrastructure.persistence.OverloadRejectionTable;
import org.mwolff.manban.ratelimit.infrastructure.persistence.OverloadRejectionTable.HourKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/admin/overload-rejections} am laufenden Stack (Issue #1003): Plattform-Admins
 * sehen die Abweisungen je Person und Stunde mit Namen, alle anderen nicht.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AdminOverloadRejectionIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String ENDPOINT = "/api/admin/overload-rejections";
  private static final Instant NINE = Instant.parse("2026-09-22T09:00:00Z");
  private static final Instant TEN = Instant.parse("2026-09-22T10:00:00Z");

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private OverloadRejectionTable table;

  @Test
  void platformAdmin_getsTheRows_withNames_newestHourFirst() throws Exception {
    Cookie admin = session("admin@example.com", PlatformRole.ADMIN);
    long alice = user("alice@example.com", "Alice");
    long bob = user("bob@example.com", "Bob");
    table.add(Map.of(new HourKey(alice, NINE), 5, new HourKey(bob, TEN), 3));

    mvc.perform(get(ENDPOINT).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].displayName").value("Bob"))
        .andExpect(jsonPath("$[0].rejections").value(3))
        .andExpect(jsonPath("$[0].hour").value("2026-09-22T10:00:00Z"))
        .andExpect(jsonPath("$[1].displayName").value("Alice"))
        .andExpect(jsonPath("$[1].userId").value(alice));
  }

  @Test
  void projectAdminWithoutPlatformRole_gets403() throws Exception {
    // Ein Projekt-Admin ist auf Plattformebene ein gewöhnlicher Nutzer.
    Cookie owner = session("owner@example.com", PlatformRole.USER);

    mvc.perform(get(ENDPOINT).cookie(owner)).andExpect(status().isForbidden());
  }

  @Test
  void accessToken_doesNotReachTheEndpoint() throws Exception {
    // Selbst das ungebundene Token eines Plattform-Admins nicht: /api/admin/** verlangt die
    // Sitzung.
    Cookie admin = session("token-admin@example.com", PlatformRole.ADMIN);
    String token = unboundToken(admin);

    mvc.perform(get(ENDPOINT).header("X-Kanban-Token", token)).andExpect(status().isForbidden());
  }

  private long user(String email, String name) {
    return users.save(new AppUser(null, email, "hash", name, true, PlatformRole.USER)).requireId();
  }

  private String unboundToken(Cookie session) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"cli\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("plaintext").asText();
  }

  private Cookie session(String email, PlatformRole role) throws Exception {
    users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, role));
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }
}
