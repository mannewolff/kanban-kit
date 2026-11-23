package org.mwolff.manban.anbieter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Gesamttest „Reparaturen bleiben unsichtbar“ (Issue #2214, Plan #2206 E2, E19, Fachplan #2202
 * Kriterium 5): Ein Mitglied mit der Rolle Kunde ruft über seine Sitzung und über seinen eigenen
 * App-Zugang jeden Leseweg auf, und kein Inhalt einer Störung erreicht es — weder die
 * Reparaturkarte noch ihr Kommentar, ihr Anhang, eine Reparaturkarte im Papierkorb oder eine nicht
 * grüne Kartenprüfung. Der Owner sieht dieselben Inhalte auf denselben Wegen.
 *
 * <p>Jeder verborgene Inhalt trägt die Marke {@value #GEHEIM}; kein sichtbarer trägt sie. So prüft
 * eine Zeile je Weg, dass die Antwort an den Kunden nichts Verborgenes enthält, gleich in welchem
 * Feld es stünde. Ein neuer Leseweg gehört hier dazu (E19).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AnbietersichtIT extends AbstractIntegrationTest {

  private static final String GEHEIM = "GEHEIM";
  private static final String JSON = "application/json";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String OWNER_EMAIL = "anbieter-owner@example.com";
  private static final String KUNDE_EMAIL = "anbieter-kunde@example.com";
  private static final String REPARATUR_TITEL = "Reparatur: Tests rot GEHEIM";
  private static final String BETROFFEN_TITEL = "Betroffene Karte";
  private static final String KARTEN = "/api/cards/";
  private static final String KANBAN_ITEMS = "/api/kanban/items/";

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;

  /** Der Aufbau: Projekt, Board, die Karten und wer worüber zugreift. */
  private record Aufbau(
      long projekt,
      long board,
      long betroffen,
      int betroffenNr,
      long reparatur,
      int reparaturNr,
      long anhang,
      Cookie owner,
      Cookie kunde,
      String ownerApp,
      String kundeApp) {}

  @Test
  @DisplayName("Szenario: Reparaturen bleiben unsichtbar")
  void reparaturenBleibenUnsichtbar() throws Exception {
    // Angenommen: Eine Prüfung ist beim Anbieter rot, und eine Reparaturkarte ist entstanden.
    Aufbau a = aufbau();

    // Wenn ein Mitglied mit der Rolle Kunde das Board öffnet, dann sieht es die Reparaturkarte
    // nicht, und die betroffene Karte nennt sie nicht.
    kundeSiehtNichts(get("/api/boards/" + a.board() + "/cards").cookie(a.kunde()))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].dependencies", hasSize(0)))
        .andExpect(jsonPath("$[0].derivedFrom").doesNotExist());
    ownerSieht(get("/api/boards/" + a.board() + "/cards").cookie(a.owner()));

    // Einzelkarte und Aktivität
    kundeSiehtNichts(get(KARTEN + a.betroffen()).cookie(a.kunde()))
        .andExpect(jsonPath("$.dependencies", hasSize(0)));
    ownerSieht(get(KARTEN + a.reparatur()).cookie(a.owner()));
    nichtGefunden(get(KARTEN + a.reparatur()).cookie(a.kunde()));
    kundeSiehtNichts(get(KARTEN + a.betroffen() + "/activity").cookie(a.kunde()));
    nichtGefunden(get(KARTEN + a.reparatur() + "/activity").cookie(a.kunde()));
    mvc.perform(get(KARTEN + a.reparatur() + "/activity").cookie(a.owner()))
        .andExpect(status().isOk());

    // … und die Suche findet sie nicht: nach Nummer im Projekt, projektübergreifend, im Board.
    nichtGefunden(
        get("/api/projects/" + a.projekt() + "/cards/by-number/" + a.reparaturNr())
            .cookie(a.kunde()));
    ownerSieht(
        get("/api/projects/" + a.projekt() + "/cards/by-number/" + a.reparaturNr())
            .cookie(a.owner()));
    kundeSiehtNichts(
            get("/api/cards/search")
                .param("number", String.valueOf(a.reparaturNr()))
                .cookie(a.kunde()))
        .andExpect(jsonPath("$", hasSize(0)));
    ownerSieht(
        get("/api/cards/search")
            .param("number", String.valueOf(a.reparaturNr()))
            .cookie(a.owner()));
    // Board-Suche mit Kommentartreffern: GEHEIM steht im Titel der Reparatur, in ihrem
    // Kommentar und in der roten Kartenprüfung an der betroffenen Karte.
    kundeSiehtNichts(
            get("/api/kanban/search").param("q", GEHEIM).header(TOKEN_HEADER, a.kundeApp()))
        .andExpect(jsonPath("$.hits", hasSize(0)));
    ownerSieht(get("/api/kanban/search").param("q", GEHEIM).header(TOKEN_HEADER, a.ownerApp()))
        .andExpect(jsonPath("$.hits", hasSize(2)));

    // Herkunft: Die betroffene Karte stammt aus der Reparatur.
    kundeSiehtNichts(get(KARTEN + a.betroffen() + "/tree").cookie(a.kunde()));
    ownerSieht(get(KARTEN + a.betroffen() + "/tree").cookie(a.owner()));
    kundeSiehtNichts(
        get(KANBAN_ITEMS + "by-number/" + a.betroffenNr() + "/lineage")
            .header(TOKEN_HEADER, a.kundeApp()));
    ownerSieht(
        get(KANBAN_ITEMS + "by-number/" + a.betroffenNr() + "/lineage")
            .header(TOKEN_HEADER, a.ownerApp()));
    nichtGefunden(
        get(KANBAN_ITEMS + "by-number/" + a.reparaturNr()).header(TOKEN_HEADER, a.kundeApp()));

    // Papierkorb
    kundeSiehtNichts(get("/api/boards/" + a.board() + "/trash").cookie(a.kunde()));
    ownerSieht(get("/api/boards/" + a.board() + "/trash").cookie(a.owner()));

    // Kommentare: An der betroffenen Karte steht nur der Hinweis „beim Anbieter in Arbeit seit
    // <Zeitpunkt>“ und die grüne Prüfung; die rote fehlt.
    kundeSiehtNichts(get(KARTEN + a.betroffen() + "/comments").cookie(a.kunde()))
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(content().string(containsString("## Beim Anbieter")))
        .andExpect(content().string(containsString("Result: green")));
    ownerSieht(get(KARTEN + a.betroffen() + "/comments").cookie(a.owner()))
        .andExpect(jsonPath("$", hasSize(3)));
    nichtGefunden(get(KARTEN + a.reparatur() + "/comments").cookie(a.kunde()));
    ownerSieht(get(KARTEN + a.reparatur() + "/comments").cookie(a.owner()));
    kundeSiehtNichts(
            get(KANBAN_ITEMS + a.betroffen() + "/comments").header(TOKEN_HEADER, a.kundeApp()))
        .andExpect(jsonPath("$", hasSize(2)));
    ownerSieht(get(KANBAN_ITEMS + a.betroffen() + "/comments").header(TOKEN_HEADER, a.ownerApp()));
    nichtGefunden(
        get(KANBAN_ITEMS + a.reparatur() + "/comments").header(TOKEN_HEADER, a.kundeApp()));

    // Entscheidungskette
    kundeSiehtNichts(get(KARTEN + a.betroffen() + "/entscheidungskette").cookie(a.kunde()));
    nichtGefunden(get(KARTEN + a.reparatur() + "/entscheidungskette").cookie(a.kunde()));
    mvc.perform(get(KARTEN + a.reparatur() + "/entscheidungskette").cookie(a.owner()))
        .andExpect(status().isOk());

    // Anhänge: Liste, Abruf über die ID und über die Karte
    nichtGefunden(get(KARTEN + a.reparatur() + "/attachments").cookie(a.kunde()));
    ownerSieht(get(KARTEN + a.reparatur() + "/attachments").cookie(a.owner()));
    nichtGefunden(get("/api/attachments/" + a.anhang()).cookie(a.kunde()));
    mvc.perform(get("/api/attachments/" + a.anhang()).cookie(a.owner())).andExpect(status().isOk());
    nichtGefunden(
        get(KANBAN_ITEMS + a.reparatur() + "/attachments").header(TOKEN_HEADER, a.kundeApp()));
    nichtGefunden(
        get(KANBAN_ITEMS + a.reparatur() + "/attachments/" + a.anhang())
            .header(TOKEN_HEADER, a.kundeApp()));
    mvc.perform(
            get(KANBAN_ITEMS + a.reparatur() + "/attachments/" + a.anhang())
                .header(TOKEN_HEADER, a.ownerApp()))
        .andExpect(status().isOk());

    // Bearbeiten und Kommentieren der verborgenen Karte enden wie bei einer fremden.
    nichtGefunden(
        patch(KARTEN + a.reparatur())
            .cookie(a.kunde())
            .contentType(JSON)
            .content("{\"title\":\"x\"}"));
    nichtGefunden(
        post(KARTEN + a.reparatur() + "/comments")
            .cookie(a.kunde())
            .contentType(JSON)
            .content("{\"body\":\"Hallo\"}"));
    nichtGefunden(
        post(KANBAN_ITEMS + a.reparatur() + "/comments")
            .header(TOKEN_HEADER, a.kundeApp())
            .contentType(JSON)
            .content("{\"body\":\"Hallo\"}"));

    // Dashboards: „Alter in Arbeit“ nennt Karten mit Titel, die Kennzahlen zählen sie.
    kundeSiehtNichts(get("/api/boards/" + a.board() + "/dashboard/aging").cookie(a.kunde()));
    ownerSieht(get("/api/boards/" + a.board() + "/dashboard/aging").cookie(a.owner()));
    kundeSiehtNichts(get("/api/boards/" + a.board() + "/dashboard").cookie(a.kunde()));

    // Stand der Durchläufe
    nichtGefunden(get(KARTEN + a.reparatur() + "/night-chain").cookie(a.kunde()));

    // Der App-Zugang des Kunden liest das Board ohne Reparaturkarte.
    kundeSiehtNichts(get("/api/kanban/items").header(TOKEN_HEADER, a.kundeApp()));
    ownerSieht(get("/api/kanban/items").header(TOKEN_HEADER, a.ownerApp()));

    // Board-Ereignisse: Eine Änderung an der Reparaturkarte geht ohne ihre ID hinaus.
    MvcResult strom =
        mvc.perform(get("/api/boards/" + a.board() + "/events").cookie(a.kunde()))
            .andExpect(request().asyncStarted())
            .andReturn();
    mvc.perform(
            patch(KARTEN + a.reparatur())
                .cookie(a.owner())
                .contentType(JSON)
                .content("{\"title\":\"%s\"}".formatted(REPARATUR_TITEL + " neu")))
        .andExpect(status().isOk());
    String ereignisse = strom.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(ereignisse)
        .contains("\"boardId\":" + a.board())
        .doesNotContain(GEHEIM)
        .doesNotContain("\"cardId\":" + a.reparatur());
  }

  /** Erwartet 200 und eine Antwort ohne die Marke des Verborgenen. */
  private ResultActions kundeSiehtNichts(RequestBuilder anfrage) throws Exception {
    return mvc.perform(anfrage)
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString(GEHEIM))));
  }

  /** Erwartet 200 und eine Antwort mit der Marke: Der Weg zeigt dem Owner das Verborgene. */
  private ResultActions ownerSieht(RequestBuilder anfrage) throws Exception {
    return mvc.perform(anfrage)
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(GEHEIM)));
  }

  private void nichtGefunden(RequestBuilder anfrage) throws Exception {
    mvc.perform(anfrage)
        .andExpect(status().isNotFound())
        .andExpect(content().string(not(containsString(GEHEIM))));
  }

  // --- Aufbau ---------------------------------------------------------------

  private Aufbau aufbau() throws Exception {
    Cookie owner = anmeldung.anmelden(OWNER_EMAIL, PlatformRole.USER);
    Cookie kunde = anmeldung.anmelden(KUNDE_EMAIL, PlatformRole.USER);
    Cookie admin = anmeldung.anmelden("anbieter-admin@example.com", PlatformRole.ADMIN);
    long projekt =
        anlegen(
                admin,
                "/api/projects",
                "{\"name\":\"Anbieter\",\"ownerEmail\":\"%s\"}".formatted(OWNER_EMAIL))
            .get("id")
            .asLong();
    memberships.save(
        new ProjectMembership(
            null,
            projekt,
            users.findByEmail(KUNDE_EMAIL).orElseThrow().id(),
            ProjectRole.CUSTOMER,
            Instant.now()));
    JsonNode board = anlegen(owner, "/api/projects/" + projekt + "/boards", "{\"name\":\"B\"}");
    long boardId = board.get("id").asLong();
    // Spalte „In Progress“: „Alter in Arbeit“ nennt die Karten dort mit Titel.
    long inArbeit = board.get("columns").get(2).get("id").asLong();
    JsonNode betroffen = karteAnlegen(owner, boardId, inArbeit, BETROFFEN_TITEL);
    JsonNode reparatur = karteAnlegen(owner, boardId, inArbeit, REPARATUR_TITEL);
    JsonNode imPapierkorb =
        karteAnlegen(owner, boardId, inArbeit, "Reparatur im Papierkorb GEHEIM");
    long reparaturId = reparatur.get("id").asLong();
    int reparaturNr = reparatur.get("number").asInt();
    long betroffenId = betroffen.get("id").asLong();
    long label =
        anlegen(
                owner,
                "/api/boards/" + boardId + "/labels",
                "{\"name\":\"kit:repair\",\"color\":\"#c62828\"}")
            .get("id")
            .asLong();
    for (long karte : List.of(reparaturId, imPapierkorb.get("id").asLong())) {
      mvc.perform(post(KARTEN + karte + "/labels/" + label).cookie(owner))
          .andExpect(status().is2xxSuccessful());
    }
    mvc.perform(
            post("/api/cards/bulk-delete")
                .cookie(owner)
                .contentType(JSON)
                .content("{\"cardIds\":[%d]}".formatted(imPapierkorb.get("id").asLong())))
        .andExpect(status().is2xxSuccessful());
    // Die betroffene Karte hängt von der Reparatur ab und stammt aus ihr.
    mvc.perform(
            patch(KARTEN + betroffenId)
                .cookie(owner)
                .contentType(JSON)
                .content(
                    "{\"title\":\"%s\",\"dependencies\":[%d]}"
                        .formatted(BETROFFEN_TITEL, reparaturNr)))
        .andExpect(status().isOk());
    mvc.perform(
            patch(KARTEN + betroffenId + "/derived-from")
                .cookie(owner)
                .contentType(JSON)
                .content("{\"derivedFrom\":%d}".formatted(reparaturNr)))
        .andExpect(status().is2xxSuccessful());
    // Kommentare: die rote und die grüne Kartenprüfung und der Hinweis an der betroffenen Karte,
    // ein Kommentar an der Reparatur.
    kommentieren(
        owner,
        betroffenId,
        """
        ## Kartenprüfung

        Die Prüfung ist rot GEHEIM.

        ### Einzelheiten für den Support

        Ergebnis: rot
        """);
    kommentieren(owner, betroffenId, "## Card check\n\nAll checks passed.\n\nResult: green\n");
    kommentieren(owner, betroffenId, "## Beim Anbieter\n\nSeit: 2026-11-22T10:00:00Z\n");
    kommentieren(owner, reparaturId, "Ursache gefunden GEHEIM");
    long anhang =
        json.readTree(
                mvc.perform(
                        multipart(KARTEN + reparaturId + "/attachments")
                            .file(
                                new MockMultipartFile(
                                    "file",
                                    "protokoll-GEHEIM.txt",
                                    "text/plain",
                                    "Protokoll GEHEIM".getBytes(StandardCharsets.UTF_8)))
                            .cookie(owner))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    String ownerApp = appZugang(owner, projekt, boardId);
    String kundeApp = appZugang(kunde, projekt, boardId);
    return new Aufbau(
        projekt,
        boardId,
        betroffenId,
        betroffen.get("number").asInt(),
        reparaturId,
        reparaturNr,
        anhang,
        owner,
        kunde,
        ownerApp,
        kundeApp);
  }

  /** Der eigene App-Zugang der Person am Board, angelegt im Browser (Issue #2212). */
  private String appZugang(Cookie session, long projekt, long board) throws Exception {
    return anlegen(
            session,
            "/api/access-tokens/app",
            "{\"projectId\":%d,\"boardId\":%d}".formatted(projekt, board))
        .get("plaintext")
        .asText();
  }

  private void kommentieren(Cookie session, long karte, String text) throws Exception {
    anlegen(session, KARTEN + karte + "/comments", json.writeValueAsString(new Kommentar(text)));
  }

  private record Kommentar(String body) {}

  private JsonNode karteAnlegen(Cookie session, long boardId, long spalte, String titel)
      throws Exception {
    return anlegen(
        session,
        "/api/boards/" + boardId + "/cards",
        "{\"columnId\":%d,\"title\":\"%s\"}".formatted(spalte, titel));
  }

  private JsonNode anlegen(Cookie session, String pfad, String inhalt) throws Exception {
    return json.readTree(
        mvc.perform(post(pfad).cookie(session).contentType(JSON).content(inhalt))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8));
  }
}
