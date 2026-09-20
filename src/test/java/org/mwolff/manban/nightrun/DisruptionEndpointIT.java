package org.mwolff.manban.nightrun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die Endpunkte des Plattform-Leitstands über HTTP (Issue #1080).
 *
 * <p>Drei Zusagen, die nur hier belegbar sind: Wer <b>nicht</b> Plattform-Admin ist, bekommt 403
 * (AK 3); ein Plattform-Admin liest die Störungen eines teilnehmenden Projekts <b>ohne jede
 * Mitgliedschaft</b> (AK 7); und ein <b>ungebundenes Token</b> erreicht beide Endpunkte nicht.
 *
 * <p>Das Letzte ist der Grund für den Pfadstamm {@code /api/admin} (Plan #1072 E24) und zieht die
 * Grenze nicht am Endpunkt, sondern in der Filterkette: {@code SecurityConfig} verlangt dort eine
 * Sitzung. Läge die Störungsliste unter {@code /api/platform/...}, fiele sie unter die Auffangregel
 * {@code /api/**} — und wäre mit einem Token erreichbar, das niemand dafür ausgestellt hat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DisruptionEndpointIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String LISTE = "/api/admin/disruptions";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;
  private long laufId;

  private Cookie session(String email, PlatformRole role) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, role));
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

  private long id(String sql, Object... args) {
    Long wert = jdbc.queryForObject(sql, Long.class, args);
    return wert == null ? 0L : wert;
  }

  /** Ein teilnehmendes Projekt mit genau einer offenen Störung — ohne Mitgliedschaft des Admins. */
  @BeforeEach
  void seed() {
    long owner =
        id(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('de-owner@example.com', 'x', 'O') RETURNING id");
    projectId =
        id(
            "INSERT INTO project (name, owner_user_id, dashboard_participation)"
                + " VALUES ('Gestoertes Projekt', ?, true) RETURNING id",
            owner);
    laufId =
        id(
            "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms,"
                + " processed_count, skipped_count, unparsed_count, created_at, origin, complete)"
                + " VALUES (?, now(), 'IMPLEMENTATION', 'NIGHT', 1, 1, 0, 0, now(), 'UPLOAD', true)"
                + " RETURNING id",
            projectId);
    jdbc.update(
        "INSERT INTO night_run_item (night_run_id, project_id, started_at, mode, kind, card_number,"
            + " title, state, error_class) VALUES (?, ?, now(), 'IMPLEMENTATION', 'NIGHT', 721,"
            + " 'Paket', 'RED', 'CHECKS_RED')",
        laufId,
        projectId);
  }

  /**
   * AK 7: kein Mitglied des Projekts, trotzdem die Störung — die Teilnahme ist die Einwilligung.
   */
  @Test
  void derPlattformAdminLiestDieStoerungOhneMitgliedschaft() throws Exception {
    Cookie admin = session("de-admin@example.com", PlatformRole.ADMIN);

    mvc.perform(get(LISTE).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].nightRunId").value(laufId))
        .andExpect(jsonPath("$[0].projectId").value(projectId))
        .andExpect(jsonPath("$[0].projectName").value("Gestoertes Projekt"))
        .andExpect(jsonPath("$[0].outcome.verdict").value("FAILED"))
        .andExpect(jsonPath("$[0].outcome.decisiveItem.cardNumber").value(721));
  }

  @Test
  void dasQuittierenRaeumtDieZeileWeg_undIstIdempotent() throws Exception {
    Cookie admin = session("de-quit@example.com", PlatformRole.ADMIN);

    mvc.perform(delete(LISTE + "/" + laufId).cookie(admin)).andExpect(status().isNoContent());
    mvc.perform(get(LISTE).cookie(admin)).andExpect(jsonPath("$.length()").value(0));

    // AK 8: Der zweite Klick zweier Admins auf dieselbe Zeile ist kein Fehler.
    mvc.perform(delete(LISTE + "/" + laufId).cookie(admin)).andExpect(status().isNoContent());
  }

  @Test
  void einUnbekannterLaufIstBeimQuittieren404() throws Exception {
    Cookie admin = session("de-404@example.com", PlatformRole.ADMIN);

    mvc.perform(delete(LISTE + "/999999").cookie(admin)).andExpect(status().isNotFound());
  }

  @Test
  void einLaufEinesNichtTeilnehmendenProjektsIstBeimQuittieren404() throws Exception {
    Cookie admin = session("de-ohne@example.com", PlatformRole.ADMIN);
    jdbc.update("UPDATE project SET dashboard_participation = false WHERE id = ?", projectId);

    mvc.perform(delete(LISTE + "/" + laufId).cookie(admin)).andExpect(status().isNotFound());
  }

  /** AK 3: Wer nicht Plattform-Admin ist, sieht weder Liste noch Quittieren. */
  @Test
  void ohnePlattformRolleAdminSindBeideEndpunkte403() throws Exception {
    Cookie nutzer = session("de-nutzer@example.com", PlatformRole.USER);

    mvc.perform(get(LISTE).cookie(nutzer)).andExpect(status().isForbidden());
    mvc.perform(delete(LISTE + "/" + laufId).cookie(nutzer)).andExpect(status().isForbidden());
  }

  /**
   * Plan E24: Die Grenze zieht die Filterkette, nicht der Endpunkt. Ein ungebundenes Token trägt
   * {@code AUTH_PAT_UNBOUND} und käme unter {@code /api/**} durch — unter {@code /api/admin/**}
   * nicht.
   */
  @Test
  void einUngebundenesTokenErreichtBeideEndpunkteNicht() throws Exception {
    Cookie admin = session("de-token@example.com", PlatformRole.ADMIN);
    String token =
        json.readTree(
                mvc.perform(
                        post("/api/access-tokens")
                            .cookie(admin)
                            .contentType("application/json")
                            .content("{\"name\":\"Leitstand-Token\"}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("plaintext")
            .asText();

    mvc.perform(get(LISTE).header(TOKEN_HEADER, token)).andExpect(status().isForbidden());
    mvc.perform(delete(LISTE + "/" + laufId).header(TOKEN_HEADER, token))
        .andExpect(status().isForbidden());
  }
}
