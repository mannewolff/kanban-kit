package org.mwolff.manban.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Prüft den Rollen-Matrix-Endpunkt gegen den geseedeten Stand (#51). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RoleMatrixIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private TestAnmeldung anmeldung;

  private Cookie loginAs(String email) {
    return anmeldung.anmelden(email, PlatformRole.USER);
  }

  @Test
  void matrixReflectsSeededGrants() throws Exception {
    Cookie session = loginAs("matrix-user@example.com");

    String body =
        mvc.perform(get("/api/roles/matrix").cookie(session))
            .andExpect(status().isOk())
            // Rollen in Anzeige-Reihenfolge
            .andExpect(jsonPath("$.roles.length()").value(5))
            .andExpect(jsonPath("$.roles[0]").value("VIEWER"))
            .andExpect(jsonPath("$.roles[1]").value("CUSTOMER"))
            .andExpect(jsonPath("$.roles[2]").value("MEMBER"))
            .andExpect(jsonPath("$.roles[4]").value("OWNER"))
            // Rechte inkl. abgeleiteter Ressource/Operation
            .andExpect(jsonPath("$.permissions.length()").value(28))
            // Grants je Rolle passend zum V4-Seed, den drei Sonderregeln aus V45 (nur OWNER)
            // und REPAIR_READ aus V79 (alle außer CUSTOMER)
            .andExpect(jsonPath("$.grants.VIEWER.length()").value(6))
            .andExpect(jsonPath("$.grants.CUSTOMER.length()").value(16))
            .andExpect(jsonPath("$.grants.MEMBER.length()").value(17))
            .andExpect(jsonPath("$.grants.ADMIN.length()").value(23))
            .andExpect(jsonPath("$.grants.OWNER.length()").value(28))
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
    // Störungen: eigene Ressource REPAIR, Kunden ausgenommen (Plan #2206, E2)
    assertThat(JsonPath.<List<Object>>read(body, "$.permissions[?(@.key=='REPAIR_READ')].resource"))
        .containsExactly("REPAIR");
    assertThat(JsonPath.<List<Object>>read(body, "$.grants.VIEWER")).contains("REPAIR_READ");
    assertThat(JsonPath.<List<Object>>read(body, "$.grants.CUSTOMER"))
        .contains("TICKET_CREATE")
        .doesNotContain("REPAIR_READ");
  }

  /**
   * Die vormaligen Sonderregeln der Matrix sind echte Rechte (Issue #1165) — und zwar
   * ausschließlich beim OWNER. Die Regel selbst ändert sich dabei nicht: Bis hierher verlangte
   * jeder dieser drei Wege die Projekt-Rolle OWNER, und genau das steht jetzt als Schlüssel in der
   * Matrix. Ein Häkchen bei VIEWER, MEMBER oder ADMIN wäre eine stillschweigende Rechteausweitung.
   */
  @Test
  void matrixFuehrtDieSonderregelnAlsEchteRechteUndZwarNurBeimOwner() throws Exception {
    Cookie session = loginAs("matrix-sonderregeln@example.com");

    String body =
        mvc.perform(get("/api/roles/matrix").cookie(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    for (String key : List.of("NIGHT_RUN_READ", "NIGHT_RUN_SUBMIT", "CARD_MOVE_PROJECT")) {
      assertThat(JsonPath.<List<Object>>read(body, "$.permissions[?(@.key=='" + key + "')].key"))
          .as("Recht %s fehlt in der Matrix", key)
          .containsExactly(key);
      assertThat(JsonPath.<List<Object>>read(body, "$.grants.OWNER"))
          .as("OWNER ohne %s", key)
          .contains(key);
      for (String rolle : List.of("VIEWER", "CUSTOMER", "MEMBER", "ADMIN")) {
        assertThat(JsonPath.<List<Object>>read(body, "$.grants." + rolle))
            .as("%s hätte %s nicht bekommen dürfen", rolle, key)
            .doesNotContain(key);
      }
    }
  }

  @Test
  void matrixRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/roles/matrix")).andExpect(status().isUnauthorized());
  }
}
