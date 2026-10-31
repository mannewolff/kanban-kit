package org.mwolff.manban.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.nightrun.application.MeldbareLaeufe;
import org.mwolff.manban.nightrun.application.MeldbareLaeufe.MeldbarerLauf;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.Meldegrund.Grund;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.push.application.LaufMeldeService;
import org.mwolff.manban.push.application.LaufNachricht;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Der Meldeweg gegen Postgres (Issue #1998, Plan #1989 E12): Ein verstummter Lauf auf einem Board
 * wird über alle Projekte gefunden, beim ersten Mal gemeldet und plant je passendem Abo genau eine
 * Outbox-Nachricht; ein zweiter Takt plant nichts mehr.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LaufMeldeServiceIT extends AbstractIntegrationTest {

  private static final String P256DH = "B" + "A".repeat(86);
  private static final String AUTH = "C".repeat(22);

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BoardService boards;
  @Autowired private NightRunRepository runs;
  @Autowired private MeldbareLaeufe meldbare;
  @Autowired private LaufMeldeService service;

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().requireId();
  }

  private long projectOf(String ownerEmail, String adminEmail) throws Exception {
    anmeldung.anmelden(ownerEmail, PlatformRole.USER);
    Cookie admin = anmeldung.anmelden(adminEmail, PlatformRole.ADMIN);
    return json.readTree(
            mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"Meldung\",\"ownerEmail\":\"%s\"}".formatted(ownerEmail)))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private void abonniere(Cookie session, String endpoint) throws Exception {
    mvc.perform(
            put("/api/push/abo")
                .cookie(session)
                .contentType("application/json")
                .content(
                    "{\"endpoint\":\"%s\",\"p256dh\":\"%s\",\"auth\":\"%s\"}"
                        .formatted(endpoint, P256DH, AUTH)))
        .andExpect(status().isNoContent());
  }

  private long verstummterLauf(long projectId, long boardId) {
    Instant start = Instant.now().minus(Duration.ofHours(3)).truncatedTo(ChronoUnit.SECONDS);
    return runs.upsert(
            new NightRun(
                null,
                projectId,
                start,
                NightRunMode.IMPLEMENTATION,
                NightRunKind.NIGHT,
                0,
                0,
                0,
                0,
                null,
                start,
                NightRunOrigin.TOKEN,
                "runner-meldung",
                false,
                Instant.now().minus(Duration.ofMinutes(100)),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                boardId),
            List.of())
        .id();
  }

  @Test
  void einVerstummterLaufWirdEinmalGemeldetUndPlantJeAboEineNachricht() throws Exception {
    String owner = "meldung-owner@example.com";
    long projectId = projectOf(owner, "meldung-admin@example.com");
    long boardId = boards.createBoard(userId(owner), projectId, "Produkt").id();
    String endpoint = "https://push.example/meldung-geraet";
    abonniere(anmeldung.anmelden(owner, PlatformRole.USER), endpoint);
    long aboId =
        jdbc.queryForObject("SELECT id FROM push_abo WHERE endpoint = ?", Long.class, endpoint);
    long laufId = verstummterLauf(projectId, boardId);

    MeldbarerLauf lauf =
        meldbare.laeufe().stream().filter(l -> l.laufId() == laufId).findFirst().orElseThrow();
    assertThat(lauf.projectName()).isEqualTo("Meldung");
    assertThat(lauf.boardName()).isEqualTo("Produkt");
    assertThat(lauf.grund().grund()).isEqualTo(Grund.VERSTUMMT);

    assertThat(service.melde(lauf)).isEqualTo(1);
    assertThat(service.melde(lauf)).isZero();

    String schluessel = "push-lauf:" + laufId + ":" + aboId;
    List<String> payloads =
        jdbc.queryForList(
            "SELECT payload FROM outbox_entry WHERE idempotency_key = ?", String.class, schluessel);
    assertThat(payloads)
        .singleElement()
        .satisfies(
            payload -> {
              LaufNachricht nachricht = LaufNachricht.aus(payload);
              assertThat(nachricht.titel()).isEqualTo("Meldung · Produkt");
              assertThat(nachricht.grund()).isEqualTo(Grund.VERSTUMMT);
            });
    assertThat(
            jdbc.queryForObject(
                "SELECT grund FROM push_lauf_gemeldet WHERE night_run_id = ?",
                String.class,
                laufId))
        .isEqualTo("VERSTUMMT");
  }
}
