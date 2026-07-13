package org.mwolff.manban.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Prüft den Rollen-Matrix-Endpunkt gegen den geseedeten Stand (#51). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RoleMatrixIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository users;

  @Autowired private PasswordEncoder passwordEncoder;

  private Cookie loginAs(String email) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.USER));
    }
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }

  @Test
  void matrixReflectsSeededGrants() throws Exception {
    Cookie session = loginAs("matrix-user@example.com");

    String body =
        mvc.perform(get("/api/roles/matrix").cookie(session))
            .andExpect(status().isOk())
            // Rollen in Anzeige-Reihenfolge
            .andExpect(jsonPath("$.roles.length()").value(4))
            .andExpect(jsonPath("$.roles[0]").value("VIEWER"))
            .andExpect(jsonPath("$.roles[3]").value("OWNER"))
            // Rechte inkl. abgeleiteter Ressource/Operation
            .andExpect(jsonPath("$.permissions.length()").value(24))
            // Grants je Rolle passend zum V4-Seed
            .andExpect(jsonPath("$.grants.VIEWER.length()").value(5))
            .andExpect(jsonPath("$.grants.MEMBER.length()").value(16))
            .andExpect(jsonPath("$.grants.ADMIN.length()").value(22))
            .andExpect(jsonPath("$.grants.OWNER.length()").value(24))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Rechte inkl. abgeleiteter Ressource/Operation
    assertThat(
            JsonPath.<List<Object>>read(body, "$.permissions[?(@.key=='BOARD_CREATE')].resource"))
        .contains("BOARD");
    assertThat(
            JsonPath.<List<Object>>read(body, "$.permissions[?(@.key=='BOARD_CREATE')].operation"))
        .contains("CREATE");
    // Grants je Rolle passend zum V4-Seed
    assertThat(JsonPath.<List<Object>>read(body, "$.grants.VIEWER")).contains("BOARD_READ");
    assertThat(JsonPath.<List<Object>>read(body, "$.grants.MEMBER")).contains("TICKET_CREATE");
    assertThat(JsonPath.<List<Object>>read(body, "$.grants.MEMBER"))
        .doesNotContain("COMMENT_DELETE");
    assertThat(JsonPath.<List<Object>>read(body, "$.grants.ADMIN")).contains("COMMENT_DELETE");
  }

  @Test
  void matrixRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/roles/matrix")).andExpect(status().isUnauthorized());
  }
}
