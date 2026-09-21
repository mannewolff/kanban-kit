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
 * Die Endpunkte des Plattform-Leitstands über HTTP (Issue #1080, auf einen Endpunkt für drei Listen
 * umgestellt in #1095).
 *
 * <p>Vier Zusagen, die nur hier belegbar sind: Wer <b>nicht</b> Plattform-Admin ist, bekommt 403
 * (AK 3); ein Plattform-Admin liest die Störungen eines teilnehmenden Projekts <b>ohne jede
 * Mitgliedschaft</b> (AK 7); ein <b>ungebundenes Token</b> erreicht beide Endpunkte nicht; und die
 * Zone wird an der Bindung geprüft — eine Offset-Zone ist 400 wie bei der Verbrauchs-Auswertung.
 *
 * <p>Das Token ist der Grund für den Pfadstamm {@code /api/admin} (Plan #1072 E24) und zieht die
 * Grenze nicht am Endpunkt, sondern in der Filterkette: {@code SecurityConfig} verlangt dort eine
 * Sitzung. Läge der Leitstand unter {@code /api/platform/...}, fiele er unter die Auffangregel
 * {@code /api/**} — und wäre mit einem Token erreichbar, das niemand dafür ausgestellt hat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DisruptionEndpointIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String LEITSTAND = "/api/admin/leitstand";
  private static final String QUITTIEREN = "/api/admin/disruptions";
  private static final String ZONE = "Europe/Berlin";

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
   *
   * <p>Derselbe Lauf steht zugleich unter den <b>durchgeführten</b>: Er ist abgeschlossen und
   * startete eben, liegt also in der laufenden Nacht. Dass beide Listen aus <b>einer</b> Antwort
   * kommen, ist die Zusage aus Plan #1088 E5.
   */
  @Test
  void derPlattformAdminLiestDieStoerungOhneMitgliedschaft() throws Exception {
    Cookie admin = session("de-admin@example.com", PlatformRole.ADMIN);

    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stoerungen.length()").value(1))
        .andExpect(jsonPath("$.stoerungen[0].nightRunId").value(laufId))
        .andExpect(jsonPath("$.stoerungen[0].projectId").value(projectId))
        .andExpect(jsonPath("$.stoerungen[0].projectName").value("Gestoertes Projekt"))
        .andExpect(jsonPath("$.stoerungen[0].outcome.verdict").value("FAILED"))
        .andExpect(jsonPath("$.stoerungen[0].outcome.decisiveItem.cardNumber").value(721))
        .andExpect(jsonPath("$.laufende.length()").value(0))
        .andExpect(jsonPath("$.durchgefuehrte.length()").value(1))
        .andExpect(jsonPath("$.durchgefuehrte[0].nightRunId").value(laufId));
  }

  /** AK 10 und Kriterium 13: Die Quittung räumt die Störung weg, den Ausgang lässt sie stehen. */
  @Test
  void dasQuittierenRaeumtDieZeileWeg_undIstIdempotent() throws Exception {
    Cookie admin = session("de-quit@example.com", PlatformRole.ADMIN);

    mvc.perform(delete(QUITTIEREN + "/" + laufId).cookie(admin)).andExpect(status().isNoContent());
    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(admin))
        .andExpect(jsonPath("$.stoerungen.length()").value(0))
        .andExpect(jsonPath("$.durchgefuehrte.length()").value(1));

    // AK 8: Der zweite Klick zweier Admins auf dieselbe Zeile ist kein Fehler.
    mvc.perform(delete(QUITTIEREN + "/" + laufId).cookie(admin)).andExpect(status().isNoContent());
  }

  @Test
  void einUnbekannterLaufIstBeimQuittieren404() throws Exception {
    Cookie admin = session("de-404@example.com", PlatformRole.ADMIN);

    mvc.perform(delete(QUITTIEREN + "/999999").cookie(admin)).andExpect(status().isNotFound());
  }

  /**
   * Plan E6: Die Zone kommt vom Browser und wird wie in {@code NightRunUsageController} auf eine
   * Regionszone eingegrenzt — ein fester Offset wäre eine verschobene Nachtgrenze, kein Ort.
   */
  @Test
  void eineOffsetZoneUndEineFehlendeZoneSind400() throws Exception {
    Cookie admin = session("de-zone@example.com", PlatformRole.ADMIN);

    mvc.perform(get(LEITSTAND).param("zone", "+05:30").cookie(admin))
        .andExpect(status().isBadRequest());
    mvc.perform(get(LEITSTAND).cookie(admin)).andExpect(status().isBadRequest());
  }

  @Test
  void einLaufEinesNichtTeilnehmendenProjektsIstBeimQuittieren404() throws Exception {
    Cookie admin = session("de-ohne@example.com", PlatformRole.ADMIN);
    jdbc.update("UPDATE project SET dashboard_participation = false WHERE id = ?", projectId);

    mvc.perform(delete(QUITTIEREN + "/" + laufId).cookie(admin)).andExpect(status().isNotFound());
  }

  /** AK 3: Wer nicht Plattform-Admin ist, sieht weder den Leitstand noch das Quittieren. */
  @Test
  void ohnePlattformRolleAdminSindBeideEndpunkte403() throws Exception {
    Cookie nutzer = session("de-nutzer@example.com", PlatformRole.USER);

    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(nutzer))
        .andExpect(status().isForbidden());
    mvc.perform(delete(QUITTIEREN + "/" + laufId).cookie(nutzer)).andExpect(status().isForbidden());
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

    mvc.perform(get(LEITSTAND).param("zone", ZONE).header(TOKEN_HEADER, token))
        .andExpect(status().isForbidden());
    mvc.perform(delete(QUITTIEREN + "/" + laufId).header(TOKEN_HEADER, token))
        .andExpect(status().isForbidden());
  }
}
