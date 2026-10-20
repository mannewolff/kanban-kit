package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Ende-zu-Ende-Test der Vorhaben-Massenzuordnung {@code POST /api/cards/bulk-parent} (Issue #1867,
 * Plan #1865) über HTTP und PostgreSQL: die Szenarien der fachlichen Quelle Issue #1864 auf
 * Serverseite und die Validierungsfälle, die im reinen Unit-Test des Controllers nicht entstehen.
 *
 * <p>Das Ausblenden eines Vorhabens merkt sich allein der Browser je Board; für den Server ist ein
 * ausgeblendetes Vorhaben ein gewöhnliches, nicht archiviertes Vorhaben des Boards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class VorhabenMassenzuordnungSzenarienIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;

  // --- Szenarien aus Issue #1864 ---

  @Test
  @DisplayName("Szenario: Zwanzig lose Karten in ein ausgeblendetes Sammel-Vorhaben")
  void zwanzigLoseKartenInEinSammelVorhaben() throws Exception {
    Board b = boardAnlegen("sz1@example.com");
    long epic = vorhabenAnlegen(b, "Nice to have");
    List<Long> karten = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      karten.add(karteAnlegen(b, "Lose " + i, null));
    }

    JsonNode antwort = rumpf(zuordnen(b.session(), karten, epic).andExpect(status().isOk()));

    assertThat(ids(antwort.get("changedCardIds"))).containsExactlyElementsOf(karten);
    assertThat(antwort.get("overwritten").asInt()).isZero();
    assertThat(antwort.get("skipped").asInt()).isZero();
    assertThat(vorhaben(b, epic).get("total").asInt()).isEqualTo(20);
  }

  @Test
  @DisplayName("Szenario: Eine Auswahl enthält Karten mit bestehender Zuordnung")
  void auswahlMitBestehenderZuordnung() throws Exception {
    Board b = boardAnlegen("sz2@example.com");
    long altlasten = vorhabenAnlegen(b, "Altlasten");
    long abrechnung = vorhabenAnlegen(b, "Abrechnung");
    List<Long> karten = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      karten.add(karteAnlegen(b, "Karte " + i, i < 3 ? altlasten : null));
    }

    JsonNode antwort = rumpf(zuordnen(b.session(), karten, abrechnung).andExpect(status().isOk()));

    assertThat(antwort.get("overwritten").asInt()).isEqualTo(3);
    assertThat(ids(antwort.get("changedCardIds"))).containsExactlyElementsOf(karten);
    for (long karte : karten) {
      assertThat(karte(b, karte).get("parentId").asLong()).isEqualTo(abrechnung);
    }
    assertThat(vorhaben(b, altlasten).get("rootNumbers")).isEmpty();
  }

  @Test
  @DisplayName("Szenario: Eine zugeordnete Karte bringt ihre abgeleiteten Karten mit")
  void zugeordneteKarteBringtAbgeleiteteMit() throws Exception {
    Board b = boardAnlegen("sz3@example.com");
    long abrechnung = vorhabenAnlegen(b, "Abrechnung");
    long anforderung = karteAnlegen(b, "Anforderung", null);
    long plan = karteAnlegen(b, "Plan", null);
    herkunft(b, plan, anforderung);
    List<Long> pakete = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      long paket = karteAnlegen(b, "Paket " + i, null);
      herkunft(b, paket, plan);
      pakete.add(paket);
    }

    zuordnen(b.session(), List.of(anforderung), abrechnung).andExpect(status().isOk());

    List<Integer> erwartet = new ArrayList<>();
    for (long id : List.of(anforderung, plan, pakete.get(0), pakete.get(1), pakete.get(2))) {
      erwartet.add(nummer(b, id));
    }
    assertThat(nummern(vorhaben(b, abrechnung).get("memberNumbers")))
        .containsExactlyInAnyOrderElementsOf(erwartet);
    JsonNode planNachher = karte(b, plan);
    assertThat(planNachher.get("derivedFrom").asInt()).isEqualTo(nummer(b, anforderung));
    assertThat(planNachher.get("parentId").isNull()).isTrue();
    for (long paket : pakete) {
      JsonNode paketNachher = karte(b, paket);
      assertThat(paketNachher.get("derivedFrom").asInt()).isEqualTo(nummer(b, plan));
      assertThat(paketNachher.get("parentId").isNull()).isTrue();
    }
  }

  @Test
  @DisplayName("Szenario: Eine Karte mit geerbtem Vorhaben behält ihre Anzeige")
  void karteMitGeerbtemVorhaben() throws Exception {
    Board b = boardAnlegen("sz4@example.com");
    long altlasten = vorhabenAnlegen(b, "Altlasten");
    long abrechnung = vorhabenAnlegen(b, "Abrechnung");
    long herkunft = karteAnlegen(b, "Alte Anforderung", altlasten);
    long karte = karteAnlegen(b, "Geerbt", null);
    herkunft(b, karte, herkunft);

    JsonNode antwort =
        rumpf(zuordnen(b.session(), List.of(karte), abrechnung).andExpect(status().isOk()));

    assertThat(ids(antwort.get("changedCardIds"))).containsExactly(karte);
    assertThat(antwort.get("overwritten").asInt()).isZero();
    int nummer = nummer(b, karte);
    assertThat(nummern(vorhaben(b, abrechnung).get("memberNumbers"))).contains(nummer);
    // Die geerbte Mitgliedschaft bleibt bestehen (AK 7).
    assertThat(nummern(vorhaben(b, altlasten).get("memberNumbers"))).contains(nummer);
  }

  @Test
  @DisplayName("Szenario: Die Auswahl enthält ein Vorhaben")
  void auswahlEnthaeltEinVorhaben() throws Exception {
    Board b = boardAnlegen("sz5@example.com");
    long ziel = vorhabenAnlegen(b, "Ziel");
    long mitgewaehlt = vorhabenAnlegen(b, "Mitgewählt");
    List<Long> auswahl = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      auswahl.add(karteAnlegen(b, "Karte " + i, null));
    }
    auswahl.add(mitgewaehlt);

    zuordnen(b.session(), auswahl, ziel).andExpect(status().isBadRequest());

    for (long karte : auswahl.subList(0, 4)) {
      assertThat(karte(b, karte).get("parentId").isNull()).isTrue();
    }
  }

  // --- Validierung, Rollback, Stand bei der Ausführung, Verlauf, Rechte ---

  @Test
  void leereAuswahlWirdAbgelehnt() throws Exception {
    Board b = boardAnlegen("leer@example.com");
    long epic = vorhabenAnlegen(b, "Ziel");

    zuordnen(b.session(), List.of(), epic).andExpect(status().isBadRequest());
  }

  @Test
  void mehrAlsZweihundertKartenWerdenAbgelehnt() throws Exception {
    Board b = boardAnlegen("zuviele@example.com");
    long epic = vorhabenAnlegen(b, "Ziel");
    // Eins über CardController.MAX_CARDS_PER_BATCH; die Validierung greift vor jedem Laden.
    List<Long> zuviele = IntStream.rangeClosed(1, 201).mapToObj(i -> (long) i).toList();

    zuordnen(b.session(), zuviele, epic).andExpect(status().isBadRequest());
  }

  @Test
  void fehlendesVorhabenWirdAbgelehnt() throws Exception {
    Board b = boardAnlegen("ohne-ziel@example.com");
    long karte = karteAnlegen(b, "Karte", null);

    mvc.perform(
            post("/api/cards/bulk-parent")
                .cookie(b.session())
                .contentType("application/json")
                .content("{\"cardIds\":[%d]}".formatted(karte)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void vorhabenInDerAuswahl_rolltAllesZurueck() throws Exception {
    Board b = boardAnlegen("rollback@example.com");
    long altlasten = vorhabenAnlegen(b, "Altlasten");
    long ziel = vorhabenAnlegen(b, "Ziel");
    long zugeordnet = karteAnlegen(b, "Zugeordnet", altlasten);
    long lose = karteAnlegen(b, "Lose", null);

    zuordnen(b.session(), List.of(zugeordnet, lose, altlasten), ziel)
        .andExpect(status().isBadRequest());

    assertThat(karte(b, zugeordnet).get("parentId").asLong()).isEqualTo(altlasten);
    assertThat(karte(b, lose).get("parentId").isNull()).isTrue();
    assertThat(verlauf(b, lose)).noneMatch(d -> d.contains("Ziel"));
  }

  @Test
  void karteImPapierkorb_zaehltAlsUebergangenUndBleibtUnveraendert() throws Exception {
    Board b = boardAnlegen("papierkorb@example.com");
    long epic = vorhabenAnlegen(b, "Ziel");
    long weg = karteAnlegen(b, "Weg", null);
    long da = karteAnlegen(b, "Da", null);
    mvc.perform(delete("/api/cards/" + weg).cookie(b.session())).andExpect(status().isNoContent());

    JsonNode antwort =
        rumpf(zuordnen(b.session(), List.of(weg, da), epic).andExpect(status().isOk()));

    assertThat(ids(antwort.get("changedCardIds"))).containsExactly(da);
    assertThat(antwort.get("skipped").asInt()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT parent_id FROM card WHERE id = ?", Long.class, weg))
        .isNull();
  }

  @Test
  void verlaufseintrag_nurAnGeaendertenKarten() throws Exception {
    Board b = boardAnlegen("verlauf@example.com");
    long epic = vorhabenAnlegen(b, "Abrechnung");
    long schonDrin = karteAnlegen(b, "Schon drin", epic);
    long neu = karteAnlegen(b, "Neu", null);

    JsonNode antwort =
        rumpf(zuordnen(b.session(), List.of(schonDrin, neu), epic).andExpect(status().isOk()));

    assertThat(ids(antwort.get("changedCardIds"))).containsExactly(neu);
    assertThat(verlauf(b, neu)).contains("Vorhaben „Abrechnung“ zugeordnet");
    assertThat(verlauf(b, schonDrin)).noneMatch(d -> d.contains("Abrechnung"));
  }

  @Test
  void nichtmitgliedSiehtKeinVorhaben() throws Exception {
    Board b = boardAnlegen("fremd-owner@example.com");
    long epic = vorhabenAnlegen(b, "Ziel");
    long karte = karteAnlegen(b, "Karte", null);
    Cookie fremd = anmeldung.anmelden("fremd@example.com", PlatformRole.USER);

    zuordnen(fremd, List.of(karte), epic).andExpect(status().isNotFound());

    assertThat(karte(b, karte).get("parentId").isNull()).isTrue();
  }

  @Test
  void rechtTicketUpdate_reicht_ohneEsVerboten() throws Exception {
    Board b = boardAnlegen("rechte-owner@example.com");
    long epic = vorhabenAnlegen(b, "Ziel");
    long karte = karteAnlegen(b, "Karte", null);
    Cookie mitglied = mitgliedAnlegen(b, "rechte-member@example.com", ProjectRole.MEMBER);
    Cookie leser = mitgliedAnlegen(b, "rechte-viewer@example.com", ProjectRole.VIEWER);

    // VIEWER hat kein TICKET_UPDATE; MEMBER hat es — und mehr verlangt die Aktion nicht (AK 6).
    zuordnen(leser, List.of(karte), epic).andExpect(status().isForbidden());
    assertThat(karte(b, karte).get("parentId").isNull()).isTrue();

    zuordnen(mitglied, List.of(karte), epic).andExpect(status().isOk());
    assertThat(karte(b, karte).get("parentId").asLong()).isEqualTo(epic);
  }

  // --- Hilfen ---

  private record Board(Cookie session, long projectId, long id, long backlog) {}

  private JsonNode rumpf(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private ResultActions zuordnen(Cookie session, List<Long> cardIds, long parentId)
      throws Exception {
    String ids = cardIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    return mvc.perform(
        post("/api/cards/bulk-parent")
            .cookie(session)
            .contentType("application/json")
            .content("{\"cardIds\":[%s],\"parentId\":%d}".formatted(ids, parentId)));
  }

  private static List<Long> ids(JsonNode array) {
    List<Long> ids = new ArrayList<>();
    array.forEach(n -> ids.add(n.asLong()));
    return ids;
  }

  private static List<Integer> nummern(JsonNode array) {
    List<Integer> nummern = new ArrayList<>();
    array.forEach(n -> nummern.add(n.asInt()));
    return nummern;
  }

  private JsonNode vorhaben(Board b, long epicId) throws Exception {
    JsonNode liste =
        json.readTree(
            mvc.perform(get("/api/boards/" + b.id() + "/epics").cookie(b.session()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode eintrag : liste) {
      if (eintrag.get("id").asLong() == epicId) {
        return eintrag;
      }
    }
    throw new AssertionError("Vorhaben " + epicId + " fehlt in der Liste");
  }

  private JsonNode karte(Board b, long cardId) throws Exception {
    return json.readTree(
        mvc.perform(get("/api/cards/" + cardId).cookie(b.session()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private int nummer(Board b, long cardId) throws Exception {
    return karte(b, cardId).get("number").asInt();
  }

  private List<String> verlauf(Board b, long cardId) throws Exception {
    List<String> details = new ArrayList<>();
    json.readTree(
            mvc.perform(get("/api/cards/" + cardId + "/activity").cookie(b.session()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .forEach(e -> details.add(e.get("detail").asText()));
    return details;
  }

  private void herkunft(Board b, long cardId, long herkunftId) throws Exception {
    mvc.perform(
            patch("/api/cards/" + cardId + "/derived-from")
                .cookie(b.session())
                .contentType("application/json")
                .content("{\"derivedFrom\":%d}".formatted(nummer(b, herkunftId))))
        .andExpect(status().isOk());
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

  private long karteAnlegen(Board b, String title, Long parentId) throws Exception {
    String parent = parentId == null ? "null" : parentId.toString();
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + b.id() + "/cards")
                        .cookie(b.session())
                        .contentType("application/json")
                        .content(
                            "{\"columnId\":%d,\"title\":\"%s\",\"parentId\":%s}"
                                .formatted(b.backlog(), title, parent)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private Cookie mitgliedAnlegen(Board b, String email, ProjectRole rolle) throws Exception {
    Cookie session = anmeldung.anmelden(email, PlatformRole.USER);
    memberships.save(
        new ProjectMembership(
            null,
            b.projectId(),
            users.findByEmail(email).orElseThrow().id(),
            rolle,
            Instant.now()));
    return session;
  }

  private Board boardAnlegen(String ownerEmail) throws Exception {
    Cookie session = anmeldung.anmelden(ownerEmail, PlatformRole.USER);
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
        projectId,
        board.get("id").asLong(),
        board.get("columns").get(0).get("id").asLong());
  }

  private Cookie platformAdminSession() throws Exception {
    return anmeldung.anmelden("project-admin@example.com", PlatformRole.ADMIN);
  }
}
