package org.mwolff.manban.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.push.application.PushAboRepository;
import org.mwolff.manban.push.application.PushLaufMeldungRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Push-Abos über HTTP gegen Postgres (Issue #1997, Plan #1989 E12, E13, E14): anlegen mit dem
 * Sitzungsende, Board-Schalter je Gerät, nur das eigene Abo, und die Löschketten der drei Tabellen.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PushAboIT extends AbstractIntegrationTest {

  private static final String P256DH = "B" + "A".repeat(86);
  private static final String AUTH = "C".repeat(22);

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BoardService boards;
  @Autowired private NightRunRepository runs;
  @Autowired private PushLaufMeldungRepository meldungen;
  @Autowired private PushAboRepository abos;

  private Cookie session(String email, PlatformRole role) {
    return anmeldung.anmelden(email, role);
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().requireId();
  }

  /** Legt ein Projekt mit dem genannten Owner an; ein Plattform-Admin legt es an. */
  private long projectOf(String ownerEmail, String adminEmail) throws Exception {
    session(ownerEmail, PlatformRole.USER);
    Cookie admin = session(adminEmail, PlatformRole.ADMIN);
    return json.readTree(
            mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType("application/json")
                        .content("{\"name\":\"Push\",\"ownerEmail\":\"%s\"}".formatted(ownerEmail)))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private ResultActions abonniere(Cookie session, String endpoint) throws Exception {
    return mvc.perform(
        put("/api/push/abo")
            .cookie(session)
            .contentType("application/json")
            .content(
                "{\"endpoint\":\"%s\",\"p256dh\":\"%s\",\"auth\":\"%s\"}"
                    .formatted(endpoint, P256DH, AUTH)));
  }

  private ResultActions schalte(Cookie session, String endpoint, long boardId, boolean an)
      throws Exception {
    return mvc.perform(
        put("/api/push/boards/{boardId}", boardId)
            .cookie(session)
            .contentType("application/json")
            .content("{\"endpoint\":\"%s\",\"an\":%s}".formatted(endpoint, an)));
  }

  private ResultActions beende(Cookie session, String endpoint) throws Exception {
    return mvc.perform(
        delete("/api/push/abo")
            .cookie(session)
            .contentType("application/json")
            .content("{\"endpoint\":\"%s\"}".formatted(endpoint)));
  }

  private long aboId(String endpoint) {
    return jdbc.queryForObject("SELECT id FROM push_abo WHERE endpoint = ?", Long.class, endpoint);
  }

  private int anzahl(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  @Test
  void ownerAbonniertSchaltetEinBoardAbUndBeendet() throws Exception {
    String owner = "push-owner@example.com";
    long projectId = projectOf(owner, "push-admin@example.com");
    Cookie session = session(owner, PlatformRole.USER);
    long produkt = boards.createBoard(userId(owner), projectId, "Produkt").id();
    long betrieb = boards.createBoard(userId(owner), projectId, "Betrieb").id();
    String endpoint = "https://push.example/owner-geraet";

    abonniere(session, endpoint).andExpect(status().isNoContent());

    // gilt_bis ist das Ende der Sitzung (E14): eine volle Sitzungsdauer von 30 Tagen ab jetzt.
    OffsetDateTime giltBis =
        jdbc.queryForObject(
            "SELECT gilt_bis FROM push_abo WHERE endpoint = ?", OffsetDateTime.class, endpoint);
    assertThat(giltBis.toInstant())
        .isBetween(
            Instant.now().plus(Duration.ofDays(29)), Instant.now().plus(Duration.ofDays(31)));

    mvc.perform(get("/api/push/boards").param("endpoint", endpoint).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.boardId == %d)].an".formatted(produkt)).value(true))
        .andExpect(jsonPath("$[?(@.boardId == %d)].an".formatted(betrieb)).value(true));

    schalte(session, endpoint, betrieb, false).andExpect(status().isNoContent());

    mvc.perform(get("/api/push/boards").param("endpoint", endpoint).cookie(session))
        .andExpect(jsonPath("$[?(@.boardId == %d)].an".formatted(produkt)).value(true))
        .andExpect(jsonPath("$[?(@.boardId == %d)].an".formatted(betrieb)).value(false))
        .andExpect(
            jsonPath("$[?(@.boardId == %d)].projectId".formatted(betrieb)).value((int) projectId));
    long aboId = aboId(endpoint);
    assertThat(anzahl("SELECT count(*) FROM push_abo_board_aus WHERE abo_id = ?", aboId))
        .isEqualTo(1);

    // Auffrischen behält den Schalter (E14).
    abonniere(session, endpoint).andExpect(status().isNoContent());
    assertThat(anzahl("SELECT count(*) FROM push_abo_board_aus WHERE abo_id = ?", aboId))
        .isEqualTo(1);

    // Beenden nimmt die Schalter mit.
    beende(session, endpoint).andExpect(status().isNoContent());
    assertThat(anzahl("SELECT count(*) FROM push_abo WHERE endpoint = ?", endpoint)).isZero();
    assertThat(anzahl("SELECT count(*) FROM push_abo_board_aus WHERE abo_id = ?", aboId)).isZero();
  }

  @Test
  void einFremdesAboBleibtUnberuehrt() throws Exception {
    String owner = "push-fremd-owner@example.com";
    long projectId = projectOf(owner, "push-fremd-admin@example.com");
    long boardId = boards.createBoard(userId(owner), projectId, "Produkt").id();
    Cookie eigentuemer = session(owner, PlatformRole.USER);
    Cookie anderer = session("push-anderer@example.com", PlatformRole.USER);
    String endpoint = "https://push.example/fremdes-geraet";
    abonniere(eigentuemer, endpoint).andExpect(status().isNoContent());

    abonniere(anderer, endpoint).andExpect(status().isConflict());
    beende(anderer, endpoint).andExpect(status().isNotFound());
    mvc.perform(get("/api/push/boards").param("endpoint", endpoint).cookie(anderer))
        .andExpect(status().isNotFound());
    schalte(anderer, endpoint, boardId, false).andExpect(status().isNotFound());

    assertThat(
            jdbc.queryForObject(
                "SELECT user_id FROM push_abo WHERE endpoint = ?", Long.class, endpoint))
        .isEqualTo(userId(owner));
    assertThat(anzahl("SELECT count(*) FROM push_abo_board_aus WHERE abo_id = ?", aboId(endpoint)))
        .isZero();
  }

  @Test
  void findById_liefertDasAboUndLeerFuerEinFehlendes() throws Exception {
    String email = "push-findbyid@example.com";
    Cookie session = session(email, PlatformRole.USER);
    String endpoint = "https://push.example/findbyid";
    abonniere(session, endpoint).andExpect(status().isNoContent());
    long aboId = aboId(endpoint);

    assertThat(abos.findById(aboId))
        .hasValueSatisfying(
            abo -> {
              assertThat(abo.endpoint()).isEqualTo(endpoint);
              assertThat(abo.userId()).isEqualTo(userId(email));
            });
    assertThat(abos.findById(aboId + 1_000_000)).isEmpty();
  }

  /**
   * Ein Board, dessen Läufe das Konto nicht sehen darf, ist nicht schaltbar und fehlt in der Liste.
   */
  @Test
  void einBoardOhneSichtAufLaeufeIstNichtSchaltbar() throws Exception {
    String owner = "push-sicht-owner@example.com";
    String fremderOwner = "push-sicht-fremd@example.com";
    long eigenes = projectOf(owner, "push-sicht-admin@example.com");
    long fremdes = projectOf(fremderOwner, "push-sicht-admin@example.com");
    long eigenesBoard = boards.createBoard(userId(owner), eigenes, "Eigen").id();
    long fremdesBoard = boards.createBoard(userId(fremderOwner), fremdes, "Fremd").id();
    Cookie session = session(owner, PlatformRole.USER);
    String endpoint = "https://push.example/sicht-geraet";
    abonniere(session, endpoint).andExpect(status().isNoContent());

    schalte(session, endpoint, fremdesBoard, false).andExpect(status().isNotFound());
    schalte(session, endpoint, 999_999L, false).andExpect(status().isNotFound());
    mvc.perform(get("/api/push/boards").param("endpoint", endpoint).cookie(session))
        .andExpect(jsonPath("$[?(@.boardId == %d)].an".formatted(eigenesBoard)).value(true))
        .andExpect(jsonPath("$[?(@.boardId == %d)]".formatted(fremdesBoard)).isEmpty());
  }

  @Test
  void einEndpunktOhneHttpsOderMitFalschenSchluesselnWirdAbgewiesen() throws Exception {
    Cookie session = session("push-form@example.com", PlatformRole.USER);

    abonniere(session, "http://push.example/klartext").andExpect(status().isBadRequest());
    abonniere(session, "https://nutzer@push.example/anmeldedaten")
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/push/abo")
                .cookie(session)
                .contentType("application/json")
                .content(
                    "{\"endpoint\":\"https://push.example/x\",\"p256dh\":\"kurz\",\"auth\":\"%s\"}"
                        .formatted(AUTH)))
        .andExpect(status().isBadRequest());
    assertThat(anzahl("SELECT count(*) FROM push_abo WHERE endpoint LIKE '%push.example/x'"))
        .isZero();
  }

  /** Ohne VAPID-Schlüssel ist Push abgeschaltet, und der Schlüssel-Aufruf antwortet 404 (E16). */
  @Test
  void ohneSchluesselIstPushNichtEingerichtet() throws Exception {
    Cookie session = session("push-schluessel@example.com", PlatformRole.USER);

    mvc.perform(get("/api/push/schluessel").cookie(session)).andExpect(status().isNotFound());
  }

  @Test
  void ohneSitzungGibtEsKeinAbo() throws Exception {
    mvc.perform(
            put("/api/push/abo")
                .contentType("application/json")
                .content(
                    "{\"endpoint\":\"https://push.example/ohne\",\"p256dh\":\"%s\",\"auth\":\"%s\"}"
                        .formatted(P256DH, AUTH)))
        .andExpect(status().isUnauthorized());
  }

  // --- Löschketten ------------------------------------------------------------------------

  @Test
  void loeschketten_kontoAboUndBoard() throws Exception {
    String owner = "push-kette-owner@example.com";
    long projectId = projectOf(owner, "push-kette-admin@example.com");
    long boardId = boards.createBoard(userId(owner), projectId, "Produkt").id();
    Cookie session = session(owner, PlatformRole.USER);
    String endpoint = "https://push.example/kette-board";
    abonniere(session, endpoint).andExpect(status().isNoContent());
    schalte(session, endpoint, boardId, false).andExpect(status().isNoContent());
    long aboId = aboId(endpoint);

    // Board gelöscht → sein Schalter geht mit, das Abo bleibt.
    jdbc.update("DELETE FROM board WHERE id = ?", boardId);
    assertThat(anzahl("SELECT count(*) FROM push_abo_board_aus WHERE abo_id = ?", aboId)).isZero();
    assertThat(anzahl("SELECT count(*) FROM push_abo WHERE id = ?", aboId)).isEqualTo(1);

    // Konto gelöscht → sein Abo geht mit.
    String nurAbo = "push-kette-konto@example.com";
    String kontoEndpoint = "https://push.example/kette-konto";
    abonniere(session(nurAbo, PlatformRole.USER), kontoEndpoint).andExpect(status().isNoContent());
    jdbc.update("DELETE FROM app_user WHERE id = ?", userId(nurAbo));
    assertThat(anzahl("SELECT count(*) FROM push_abo WHERE endpoint = ?", kontoEndpoint)).isZero();
  }

  /** Ein Lauf wird höchstens einmal gemeldet; mit dem Lauf geht seine Meldung (E12). */
  @Test
  void loeschkette_laufUndHoechstensEineMeldung() throws Exception {
    String owner = "push-lauf-owner@example.com";
    long projectId = projectOf(owner, "push-lauf-admin@example.com");
    Instant start = Instant.now().minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.SECONDS);
    long laufId =
        runs.upsert(
                new NightRun(
                    null,
                    projectId,
                    start,
                    NightRunMode.IMPLEMENTATION,
                    NightRunKind.NIGHT,
                    1_800_000,
                    0,
                    0,
                    0,
                    null,
                    start,
                    NightRunOrigin.TOKEN,
                    "runner-push",
                    false,
                    Instant.now(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null),
                List.of())
            .id();

    assertThat(meldungen.merke(laufId, Instant.now(), "GESCHEITERT")).isTrue();
    assertThat(meldungen.merke(laufId, Instant.now(), "WARTET")).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT grund FROM push_lauf_gemeldet WHERE night_run_id = ?",
                String.class,
                laufId))
        .isEqualTo("GESCHEITERT");

    jdbc.update("DELETE FROM night_run WHERE id = ?", laufId);
    assertThat(anzahl("SELECT count(*) FROM push_lauf_gemeldet WHERE night_run_id = ?", laufId))
        .isZero();
  }
}
