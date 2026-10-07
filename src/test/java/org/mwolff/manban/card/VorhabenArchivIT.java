package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.application.VorhabenArchivierung;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ende-zu-Ende-Test des Vorhaben-Archivs (Plan #1504, Issue #1509) über HTTP und PostgreSQL: leere
 * Vorhaben verschwinden aus der Vorhaben-Liste, kehren beim Zurückholen einer Karte zurück und
 * werden nie gelöscht. Die Nummern im Methodennamen verweisen auf die fachlichen Akzeptanzkriterien
 * aus Issue #1494.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class VorhabenArchivIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CardRepository cards;
  @Autowired private VorhabenArchivierung vorhaben;
  @Autowired private ApplicationContext context;

  @Test
  void ak1_vorhabenMitNurArchiviertenKarten_verschwindetAusDerListe() throws Exception {
    Board b = boardAnlegen("ak1@example.com");
    long epic = vorhabenAnlegen(b, "Hülle");
    long karte = karteAnlegen(b, b.backlog(), "A", epic);
    archivieren(b, karte);

    assertThat(epicIds(b)).containsExactly(epic);
    assertThat(vorhaben.gleicheAlleAb()).isEqualTo(1);

    assertThat(epicIds(b)).isEmpty();
  }

  @Test
  void ak2_vorhabenOhneKarte_verschwindetAusDerListe() throws Exception {
    Board b = boardAnlegen("ak2@example.com");
    long epic = vorhabenAnlegen(b, "Nie befüllt");

    assertThat(vorhaben.gleicheAlleAb()).isEqualTo(1);

    assertThat(epicIds(b)).isEmpty();
    assertThat(cards.findById(epic).orElseThrow().archived()).isTrue();
  }

  @Test
  void ak3_aufbewahrungNull_jobArchiviertVorhabenNachHandArchivierung() throws Exception {
    Board b = boardAnlegen("ak3@example.com");
    mvc.perform(
            put("/api/admin/done-retention")
                .cookie(platformAdminSession())
                .contentType("application/json")
                .content("{\"days\":0}"))
        .andExpect(status().isOk());
    long epic = vorhabenAnlegen(b, "Von Hand");
    long karte = karteAnlegen(b, b.backlog(), "A", epic);
    archivieren(b, karte);

    // Der Job ist paketintern (card.infrastructure); aufgerufen wird die Bean aus dem Kontext, so
    // wie der Scheduler sie aufruft — mit ihrer echten Verdrahtung.
    ReflectionTestUtils.invokeMethod(context.getBean("doneRetentionJob"), "run");

    assertThat(cards.findById(epic).orElseThrow().archived()).isTrue();
    assertThat(epicIds(b)).isEmpty();
  }

  @Test
  void ak5_archiviertesVorhaben_istKeinZuordnungszielMehr() throws Exception {
    Board b = boardAnlegen("ak5@example.com");
    long epic = vorhabenAnlegen(b, "Archiviert");
    long karte = karteAnlegen(b, b.backlog(), "Frei", null);
    vorhaben.gleicheAlleAb();
    assertThat(cards.findById(epic).orElseThrow().archived()).isTrue();

    mvc.perform(
            post("/api/boards/" + b.id() + "/cards")
                .cookie(b.session())
                .contentType("application/json")
                .content(
                    "{\"columnId\":%d,\"title\":\"Neu\",\"parentId\":%d}"
                        .formatted(b.backlog(), epic)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/cards/" + karte + "/parent")
                .cookie(b.session())
                .contentType("application/json")
                .content("{\"parentId\":%d}".formatted(epic)))
        .andExpect(status().isBadRequest());

    assertThat(cards.findById(karte).orElseThrow().parentId()).isNull();
  }

  @Test
  void ak6_karteAusDemArchivZurueckgeholt_holtVorhabenMitZurueck() throws Exception {
    Board b = boardAnlegen("ak6-archiv@example.com");
    Vorhaben v = vorhabenMitAnforderung(b);
    archivieren(b, v.karte());
    vorhaben.gleicheAlleAb();
    assertThat(epicIds(b)).isEmpty();

    mvc.perform(post("/api/cards/" + v.karte() + "/restore").cookie(b.session()))
        .andExpect(status().isOk());

    assertZurueck(b, v);
  }

  @Test
  void ak6_karteAusDemPapierkorbZurueckgeholt_holtVorhabenMitZurueck() throws Exception {
    // E12: Das Zurückholen aus dem Papierkorb schreibt per JDBC an der JPA-Sicht vorbei; der
    // Abgleich danach muss die Karte trotzdem wieder als Mitglied sehen.
    Board b = boardAnlegen("ak6-papierkorb@example.com");
    Vorhaben v = vorhabenMitAnforderung(b);
    mvc.perform(delete("/api/cards/" + v.karte()).cookie(b.session()))
        .andExpect(status().isNoContent());
    vorhaben.gleicheAlleAb();
    assertThat(epicIds(b)).isEmpty();

    mvc.perform(post("/api/cards/" + v.karte() + "/restore-deleted").cookie(b.session()))
        .andExpect(status().isOk());

    assertZurueck(b, v);
  }

  @Test
  void ak7_vorhabenMitFertigerNichtArchivierterKarte_bleibtInDerListe() throws Exception {
    Board b = boardAnlegen("ak7@example.com");
    long epic = vorhabenAnlegen(b, "Fertig, wartet");
    long karte = karteAnlegen(b, b.backlog(), "A", epic);
    mvc.perform(
            post("/api/cards/" + karte + "/move")
                .cookie(b.session())
                .contentType("application/json")
                .content("{\"columnId\":%d,\"position\":0}".formatted(b.done())))
        .andExpect(status().isOk());

    assertThat(vorhaben.gleicheAlleAb()).isZero();

    JsonNode liste = epics(b);
    assertThat(liste).hasSize(1);
    assertThat(liste.get(0).get("done").asInt()).isEqualTo(1);
    assertThat(liste.get(0).get("total").asInt()).isEqualTo(1);
  }

  @Test
  void findBoardIdsWithEpics_liefertArchivierteVorhabenBoards_ohnePapierkorbVorhaben()
      throws Exception {
    Board archiviert = boardAnlegen("boards-archiv@example.com");
    vorhabenAnlegen(archiviert, "Archiviert");
    vorhaben.gleicheAlleAb();
    Board sichtbar = boardAnlegen("boards-sichtbar@example.com");
    long epic = vorhabenAnlegen(sichtbar, "Sichtbar");
    karteAnlegen(sichtbar, sichtbar.backlog(), "A", epic);
    Board papierkorb = boardAnlegen("boards-papierkorb@example.com");
    long weg = vorhabenAnlegen(papierkorb, "Im Papierkorb");
    mvc.perform(delete("/api/cards/" + weg).cookie(papierkorb.session()))
        .andExpect(status().isNoContent());
    Board ohne = boardAnlegen("boards-ohne@example.com");
    karteAnlegen(ohne, ohne.backlog(), "Keine Vorhaben", null);

    assertThat(cards.findBoardIdsWithEpics())
        .containsExactlyInAnyOrder(archiviert.id(), sichtbar.id());
  }

  @Test
  void keinLoeschen_archiviertesVorhabenBleibtAlsDatensatzErhalten() throws Exception {
    Board b = boardAnlegen("kein-loeschen@example.com");
    long epic = vorhabenAnlegen(b, "Bleibt");
    vorhaben.gleicheAlleAb();
    vorhaben.gleicheAlleAb();

    Map<String, Object> zeile =
        jdbc.queryForMap("SELECT archived, deleted_at FROM card WHERE id = ?", epic);
    assertThat(zeile.get("archived")).isEqualTo(true);
    assertThat(zeile.get("deleted_at")).isNull();
  }

  // --- Hilfen ---

  private record Board(Cookie session, long id, long backlog, long done) {}

  private record Vorhaben(long epic, long karte, int karteNummer, int anforderungNummer) {}

  /** Vorhaben mit einer Mitgliedskarte und einer Anforderung, die selbst nicht Mitglied ist. */
  private Vorhaben vorhabenMitAnforderung(Board b) throws Exception {
    long epic = vorhabenAnlegen(b, "Mit Anforderung");
    long anforderung = karteAnlegen(b, b.backlog(), "Anforderung", null);
    long karte = karteAnlegen(b, b.backlog(), "Paket", epic);
    int anforderungNummer = nummer(b, anforderung);
    mvc.perform(
            patch("/api/cards/" + epic + "/requirement")
                .cookie(b.session())
                .contentType("application/json")
                .content("{\"requirementCardNumber\":%d}".formatted(anforderungNummer)))
        .andExpect(status().isOk());
    return new Vorhaben(epic, karte, nummer(b, karte), anforderungNummer);
  }

  private void assertZurueck(Board b, Vorhaben v) throws Exception {
    JsonNode liste = epics(b);
    assertThat(liste).hasSize(1);
    JsonNode eintrag = liste.get(0);
    assertThat(eintrag.get("id").asLong()).isEqualTo(v.epic());
    List<Integer> mitglieder = new ArrayList<>();
    eintrag.get("memberNumbers").forEach(n -> mitglieder.add(n.asInt()));
    assertThat(mitglieder).containsExactly(v.karteNummer());
    assertThat(eintrag.get("requirementCardNumber").asInt()).isEqualTo(v.anforderungNummer());
    assertThat(cards.findById(v.epic()).orElseThrow().archived()).isFalse();
  }

  private List<Long> epicIds(Board b) throws Exception {
    List<Long> ids = new ArrayList<>();
    epics(b).forEach(e -> ids.add(e.get("id").asLong()));
    return ids;
  }

  private JsonNode epics(Board b) throws Exception {
    return json.readTree(
        mvc.perform(get("/api/boards/" + b.id() + "/epics").cookie(b.session()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void archivieren(Board b, long cardId) throws Exception {
    mvc.perform(post("/api/cards/" + cardId + "/archive").cookie(b.session()))
        .andExpect(status().isOk());
  }

  private int nummer(Board b, long cardId) throws Exception {
    return json.readTree(
            mvc.perform(get("/api/cards/" + cardId).cookie(b.session()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("number")
        .asInt();
  }

  private long vorhabenAnlegen(Board b, String title) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + b.id() + "/cards")
                        .cookie(b.session())
                        .contentType("application/json")
                        .content("{\"type\":\"EPIC\",\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private long karteAnlegen(Board b, long columnId, String title, Long parentId) throws Exception {
    String parent = parentId == null ? "null" : parentId.toString();
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + b.id() + "/cards")
                        .cookie(b.session())
                        .contentType("application/json")
                        .content(
                            "{\"columnId\":%d,\"title\":\"%s\",\"parentId\":%s}"
                                .formatted(columnId, title, parent)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private Board boardAnlegen(String ownerEmail) throws Exception {
    Cookie session = login(ownerEmail, PlatformRole.USER);
    long projectId =
        json.readTree(
                mvc.perform(
                        post("/api/projects")
                            .cookie(platformAdminSession())
                            .contentType("application/json")
                            .content(
                                "{\"name\":\"P\",\"ownerEmail\":\"%s\"}".formatted(ownerEmail)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    JsonNode board =
        json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(session)
                        .contentType("application/json")
                        .content("{\"name\":\"B\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return new Board(
        session,
        board.get("id").asLong(),
        board.get("columns").get(0).get("id").asLong(),
        board.get("columns").get(4).get("id").asLong());
  }

  private Cookie platformAdminSession() throws Exception {
    return login("project-admin@example.com", PlatformRole.ADMIN);
  }

  private Cookie login(String email, PlatformRole role) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "Person", true, role));
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
}
