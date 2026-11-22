package org.mwolff.manban.card;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Szenario „Reparaturen bleiben unsichtbar“ (Issue #2211, Fachplan #2202 Kriterium 5): Ein Mitglied
 * mit der Rolle Kunde sieht die Reparaturkarte weder im Board noch in der Suche, und der
 * Einzelabruf wie das Bearbeiten enden mit 404 wie bei einer fremden Karte. Der Owner sieht alles.
 * Prüft zugleich die Abfrage der Reparatur-Nummern gegen die echte Datenbank.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ReparaturkarteVerborgenIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;

  @Test
  void reparaturenBleibenDemKundenUnsichtbar() throws Exception {
    // Angenommen: eine Prüfung ist beim Anbieter rot, und eine Reparaturkarte ist entstanden.
    Cookie owner = anmeldung.anmelden("rep-owner@example.com", PlatformRole.USER);
    Cookie kunde = anmeldung.anmelden("rep-kunde@example.com", PlatformRole.USER);
    Cookie admin = anmeldung.anmelden("rep-admin@example.com", PlatformRole.ADMIN);
    long projekt = projektAnlegen(admin, "rep-owner@example.com");
    memberships.save(
        new ProjectMembership(
            null,
            projekt,
            users.findByEmail("rep-kunde@example.com").orElseThrow().id(),
            ProjectRole.CUSTOMER,
            Instant.now()));
    JsonNode board = anlegen(owner, "/api/projects/" + projekt + "/boards", "{\"name\":\"B\"}");
    long boardId = board.get("id").asLong();
    long spalte = board.get("columns").get(0).get("id").asLong();
    JsonNode betroffen = karteAnlegen(owner, boardId, spalte, "Betroffene Karte");
    JsonNode reparatur = karteAnlegen(owner, boardId, spalte, "Reparatur: Tests rot");
    long reparaturId = reparatur.get("id").asLong();
    int reparaturNr = reparatur.get("number").asInt();
    long label =
        anlegen(
                owner,
                "/api/boards/" + boardId + "/labels",
                "{\"name\":\"kit:repair\",\"color\":\"#c62828\"}")
            .get("id")
            .asLong();
    mvc.perform(post("/api/cards/" + reparaturId + "/labels/" + label).cookie(owner))
        .andExpect(status().is2xxSuccessful());
    // Die betroffene Karte hängt von der Reparatur ab.
    mvc.perform(
            patch("/api/cards/" + betroffen.get("id").asLong())
                .cookie(owner)
                .contentType("application/json")
                .content(
                    "{\"title\":\"Betroffene Karte\",\"dependencies\":[%d]}"
                        .formatted(reparaturNr)))
        .andExpect(status().isOk());

    // Wenn ein Mitglied mit der Rolle Kunde das Board öffnet, dann sieht es die Reparaturkarte
    // nicht, und die betroffene Karte nennt sie nicht.
    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(kunde))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].title").value("Betroffene Karte"))
        .andExpect(jsonPath("$[0].dependencies", hasSize(0)));
    // … und die Suche findet sie nicht.
    mvc.perform(get("/api/cards/search").param("number", String.valueOf(reparaturNr)).cookie(kunde))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
    // Einzelabruf, Verlauf und Bearbeiten enden wie bei einer fremden Karte.
    mvc.perform(get("/api/cards/" + reparaturId).cookie(kunde)).andExpect(status().isNotFound());
    mvc.perform(get("/api/cards/" + reparaturId + "/activity").cookie(kunde))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/cards/" + reparaturId)
                .cookie(kunde)
                .contentType("application/json")
                .content("{\"title\":\"x\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/cards/" + reparaturId + "/labels")
                .cookie(kunde)
                .contentType("application/json")
                .content("{\"labels\":[]}"))
        .andExpect(status().isNotFound());

    // Der Owner sieht weiter alles.
    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
    mvc.perform(get("/api/cards/" + reparaturId).cookie(owner)).andExpect(status().isOk());
    mvc.perform(get("/api/cards/search").param("number", String.valueOf(reparaturNr)).cookie(owner))
        .andExpect(jsonPath("$", hasSize(1)));
  }

  private long projektAnlegen(Cookie admin, String ownerEmail) throws Exception {
    return anlegen(
            admin,
            "/api/projects",
            "{\"name\":\"Reparatur\",\"ownerEmail\":\"%s\"}".formatted(ownerEmail))
        .get("id")
        .asLong();
  }

  private JsonNode karteAnlegen(Cookie session, long boardId, long spalte, String titel)
      throws Exception {
    return anlegen(
        session,
        "/api/boards/" + boardId + "/cards",
        "{\"columnId\":%d,\"title\":\"%s\"}".formatted(spalte, titel));
  }

  private JsonNode anlegen(Cookie session, String pfad, String inhalt) throws Exception {
    return json.readTree(
        mvc.perform(post(pfad).cookie(session).contentType("application/json").content(inhalt))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
