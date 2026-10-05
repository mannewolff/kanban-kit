package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.Laufkennung;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Der Fortschritt eines laufenden Laufs Ende zu Ende (Issue #1375, Plan #1372): {@code GET
 * /api/projects/{id}/night-runs/{runId}/progress}.
 *
 * <p>Die Spuren entstehen auf dem Weg, den das Kit nachts geht — über {@code /api/kanban/*} mit
 * Token und Kopfzeile {@code X-Agent-Model}: Plan und Pakete anlegen, Pakete bewegen, Plan als
 * geprüft markieren, Laufstand-Kommentar anlegen und ersetzen, Label setzen. Nur so belegt der
 * Test, dass die Abfragen von #1373 genau die Spuren finden, die der Runner hinterlässt.
 *
 * <p>Die fachliche Anforderung legt das Token <b>ohne</b> Kopfzeile an: wie ein Mensch in einer
 * interaktiven Sitzung. Sie zählt damit nicht zum Lauf und kommt allein über die Herkunft des Plans
 * in die Antwort.
 *
 * <p>Zwei Läufe mit demselben Token und überlappendem Fenster (Issue #1430, Plan #1423): Weisen sie
 * sich mit {@code X-Night-Run} aus, bekommt jeder genau seine Karten; ohne Ausweis stehen die
 * Karten wie bisher unter „unbekannt". Ein Session-Aufruf mit demselben Header hinterlässt keine
 * Laufkennung (A3).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NightRunFortschrittIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String AGENT_HEADER = "X-Agent-Model";
  private static final String MODELL = "claude-opus-5-5";
  private static final String JSON = "application/json";

  /**
   * Alle Feldnamen, die die Antwort tragen darf — auf jeder Ebene. Ein Feld wie {@code body},
   * {@code description} oder {@code comments} fiele hier auf (E10).
   */
  private static final Set<String> ERLAUBTE_FELDER =
      Set.of(
          "zuordnung",
          "ketten",
          "pakete",
          "unbekannt",
          "offeneFragen",
          "unbekanntOhneAusweis",
          "anforderung",
          "plan",
          "stufen",
          "aktuelleStufe",
          "endeErreicht",
          "stufe",
          "zustand",
          "karte",
          "number",
          "title",
          "boardId");

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void derFortschrittZeigtKettePlanPaketeUndOffeneFrageAusDenSpurenDesLaufs() throws Exception {
    Aufbau a = aufbau("fort-a");
    long runId = laufStarten(a);

    Item anforderung = anlegen(a, null, "[Fachlich] Fortschritt", null);
    Item plan = anlegen(a, MODELL, "[Plan] Fortschritt", anforderung.number());
    laufstand(a, anforderung, "plan fertig für #" + anforderung.number());
    aendern(a, plan, "[Plan] Fortschritt", "Plan-Review: fable, gpt-astra");
    Item paket1 = anlegen(a, MODELL, "Fortschritt 1/2: Abfragen", plan.number());
    Item paket2 = anlegen(a, MODELL, "Fortschritt 2/2: Endpunkt", plan.number());
    // Der Runner ersetzt den Laufstand an Ort und Stelle, statt einen zweiten anzulegen.
    laufstandErsetzen(a, anforderung, "abdeckung fertig für #" + plan.number());
    verschieben(a, paket1, "IN_REVIEW");
    verschieben(a, paket2, "IN_PROGRESS");
    // Das Label muss am Board stehen, bevor das Token es zuordnen kann — wie im Kit-Setup.
    lies(
        post("/api/boards/" + a.boardId() + "/labels")
            .cookie(a.session())
            .contentType(JSON)
            .content("{\"name\":\"lauf:wartet\",\"color\":\"#b87333\"}"),
        201);
    mvc.perform(
            mitAgent(post("/api/kanban/items/" + anforderung.id() + "/labels"), a)
                .contentType(JSON)
                .content("{\"name\":\"lauf:wartet\"}"))
        .andExpect(status().isNoContent());

    JsonNode antwort = fortschritt(a.session(), a.projectId(), runId, 200);

    assertThat(antwort.get("zuordnung").asText()).isEqualTo("OK");
    assertThat(antwort.get("unbekannt")).isEmpty();
    JsonNode kette = antwort.get("ketten").get(0);
    assertThat(antwort.get("ketten")).hasSize(1);
    assertThat(kette.get("anforderung").get("number").asInt()).isEqualTo(anforderung.number());
    assertThat(kette.get("anforderung").get("boardId").asLong()).isEqualTo(a.boardId());
    assertThat(kette.get("plan").get("number").asInt()).isEqualTo(plan.number());
    assertThat(kette.get("plan").get("title").asText()).isEqualTo("[Plan] Fortschritt");
    assertThat(nummernUndZustaende(kette.get("pakete")))
        .containsExactly(paket1.number() + ":FERTIG", paket2.number() + ":IN_UMSETZUNG");
    assertThat(stufen(kette))
        .containsExactly(
            "PLAN:ERREICHT", "REVIEW:ERREICHT", "PAKETE:ERREICHT", "ABDECKUNG:ERREICHT");
    assertThat(kette.get("endeErreicht").asBoolean()).isTrue();
    assertThat(nummernUndZustaende(antwort.get("pakete")))
        .containsExactly(paket1.number() + ":FERTIG", paket2.number() + ":IN_UMSETZUNG");
    assertThat(antwort.get("offeneFragen")).hasSize(1);
    assertThat(antwort.get("offeneFragen").get(0).get("number").asInt())
        .isEqualTo(anforderung.number());
    assertThat(feldnamen(antwort)).isSubsetOf(ERLAUBTE_FELDER);
  }

  /**
   * Issue #1430: Zwei Läufe mit demselben Token laufen gleichzeitig und weisen sich aus — jeder
   * sieht nur seine Karten und seinen Laufstand, nichts steht unter „unbekannt".
   */
  @Test
  void zweiAusgewieseneLaeufeSehenJeweilsNurIhreKartenUndIhrenLaufstand() throws Exception {
    Aufbau a = aufbau("fort-zwei");
    Instant startA = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
    Instant startB = Instant.now().minus(Duration.ofMinutes(4)).truncatedTo(ChronoUnit.MILLIS);
    long laufA = laufStarten(a, startA);
    long laufB = laufStarten(a, startB);

    Item anforderungA = anlegen(a, null, "[Fachlich] Kette A", null);
    Item anforderungB = anlegen(a, null, "[Fachlich] Kette B", null);
    Item planA = anlegen(a, MODELL, "[Plan] Kette A", anforderungA.number(), startA);
    Item planB = anlegen(a, MODELL, "[Plan] Kette B", anforderungB.number(), startB);
    aendern(a, planA, "[Plan] Kette A", "Plan-Review: fable", startA);
    Item paketA = anlegen(a, MODELL, "Kette A 1/1: Paket", planA.number(), startA);
    Item paketB = anlegen(a, MODELL, "Kette B 1/1: Paket", planB.number(), startB);
    verschieben(a, paketA, "IN_REVIEW", startA);
    verschieben(a, paketB, "IN_PROGRESS", startB);
    laufstand(a, anforderungA, "abdeckung fertig für #" + planA.number(), startA);
    laufstand(a, anforderungB, "plan fertig für #" + anforderungB.number(), startB);

    JsonNode antwortA = fortschritt(a.session(), a.projectId(), laufA, 200);
    JsonNode antwortB = fortschritt(a.session(), a.projectId(), laufB, 200);

    assertThat(antwortA.get("unbekannt")).isEmpty();
    assertThat(antwortB.get("unbekannt")).isEmpty();
    assertThat(antwortA.get("unbekanntOhneAusweis").asBoolean()).isFalse();
    assertThat(antwortB.get("unbekanntOhneAusweis").asBoolean()).isFalse();
    assertThat(nummernUndZustaende(antwortA.get("pakete")))
        .containsExactly(paketA.number() + ":FERTIG");
    assertThat(nummernUndZustaende(antwortB.get("pakete")))
        .containsExactly(paketB.number() + ":IN_UMSETZUNG");
    assertThat(antwortA.get("ketten")).hasSize(1);
    assertThat(antwortB.get("ketten")).hasSize(1);
    JsonNode ketteA = antwortA.get("ketten").get(0);
    JsonNode ketteB = antwortB.get("ketten").get(0);
    assertThat(ketteA.get("anforderung").get("number").asInt()).isEqualTo(anforderungA.number());
    assertThat(ketteA.get("plan").get("number").asInt()).isEqualTo(planA.number());
    assertThat(nummernUndZustaende(ketteA.get("pakete")))
        .containsExactly(paketA.number() + ":FERTIG");
    assertThat(ketteB.get("anforderung").get("number").asInt()).isEqualTo(anforderungB.number());
    assertThat(ketteB.get("plan").get("number").asInt()).isEqualTo(planB.number());
    assertThat(nummernUndZustaende(ketteB.get("pakete")))
        .containsExactly(paketB.number() + ":IN_UMSETZUNG");
    // A hat bis zur Abdeckung abgeschlossen, B erst den Plan — keiner sieht den Laufstand des
    // anderen.
    assertThat(stufen(ketteA))
        .containsExactly(
            "PLAN:ERREICHT", "REVIEW:ERREICHT", "PAKETE:ERREICHT", "ABDECKUNG:ERREICHT");
    assertThat(stufen(ketteB))
        .containsExactly("PLAN:ERREICHT", "REVIEW:LAEUFT", "PAKETE:OFFEN", "ABDECKUNG:OFFEN");
    assertThat(laufkennungen(planA)).isNotEmpty().containsOnly(startA);
    assertThat(laufkennungen(paketA)).isNotEmpty().containsOnly(startA);
    assertThat(laufkennungen(planB)).isNotEmpty().containsOnly(startB);
    assertThat(laufkennungen(paketB)).isNotEmpty().containsOnly(startB);
  }

  /** Issue #1430, A3: Ein Mensch gehört zu keinem Lauf — auch wenn er den Header mitschickt. */
  @Test
  void eineSessionBewegungMitLaufheaderTraegtKeineLaufkennung() throws Exception {
    Aufbau a = aufbau("fort-session");
    Instant start = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
    laufStarten(a, start);
    Item ziel = anlegen(a, MODELL, "Session Ziel", null, start);
    verschieben(a, ziel, "IN_PROGRESS", start);
    Item karte = anlegen(a, MODELL, "Session Karte", null, start);
    long spalte =
        lies(get("/api/cards/" + ziel.id()).cookie(a.session()), 200).get("columnId").asLong();

    lies(
        post("/api/cards/" + karte.id() + "/move")
            .cookie(a.session())
            .header(Laufkennung.HEADER, start.toString())
            .contentType(JSON)
            .content("{\"columnId\":%d,\"position\":0}".formatted(spalte)),
        200);

    List<String> sessionSpuren =
        jdbc.queryForList(
            "SELECT coalesce(run_started_at::text, 'ohne') FROM card_activity"
                + " WHERE card_id = ? AND origin = 'SESSION'",
            String.class,
            karte.id());
    assertThat(sessionSpuren).containsExactly("ohne");
    assertThat(laufkennungen(karte)).containsOnly(start);
  }

  /**
   * Issue #1430: Zwei überlappende Läufe ohne Ausweis — die Karten passen zu beiden und stehen wie
   * bisher unter „unbekannt", jetzt mit dem Hinweis {@code unbekanntOhneAusweis}.
   */
  @Test
  void zweiLaeufeOhneAusweisStellenIhreKartenUnterUnbekannt() throws Exception {
    Aufbau a = aufbau("fort-ohne");
    Instant startA = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
    Instant startB = Instant.now().minus(Duration.ofMinutes(4)).truncatedTo(ChronoUnit.MILLIS);
    long laufA = laufStarten(a, startA);
    long laufB = laufStarten(a, startB);
    Item paket = anlegen(a, MODELL, "Ohne Ausweis 1/1: Paket", null);
    verschieben(a, paket, "IN_PROGRESS");

    for (long lauf : List.of(laufA, laufB)) {
      JsonNode antwort = fortschritt(a.session(), a.projectId(), lauf, 200);

      assertThat(antwort.get("unbekannt")).hasSize(1);
      assertThat(antwort.get("unbekannt").get(0).get("number").asInt()).isEqualTo(paket.number());
      assertThat(antwort.get("pakete")).isEmpty();
      assertThat(antwort.get("unbekanntOhneAusweis").asBoolean()).isTrue();
    }
    assertThat(laufkennungen(paket)).isEmpty();
  }

  /** E10: Wer nicht Mitglied ist, erfährt nicht einmal, dass es das Projekt gibt. */
  @Test
  void einNichtmitgliedBekommt404() throws Exception {
    Aufbau a = aufbau("fort-fremd");
    long runId = laufStarten(a);
    Cookie fremd = session("fort-fremd-x@example.com", PlatformRole.USER);

    fortschritt(fremd, a.projectId(), runId, 404);
  }

  @Test
  void einMitgliedOhneOwnerRolleBekommt403() throws Exception {
    Aufbau a = aufbau("fort-member");
    long runId = laufStarten(a);
    Cookie mitglied = session("fort-member-m@example.com", PlatformRole.USER);
    long mitgliedId = users.findByEmail("fort-member-m@example.com").orElseThrow().requireId();
    memberships.save(
        new ProjectMembership(null, a.projectId(), mitgliedId, ProjectRole.MEMBER, Instant.now()));

    fortschritt(mitglied, a.projectId(), runId, 403);
  }

  /**
   * E10: Der Lauf eines anderen Projekts ist unter diesem Projekt unbekannt — auch für dessen
   * Owner.
   */
  @Test
  void derLaufEinesAnderenProjektsIst404() throws Exception {
    Aufbau a = aufbau("fort-p1");
    Aufbau b = aufbau("fort-p2");
    long fremderLauf = laufStarten(b);

    fortschritt(a.session(), a.projectId(), fremderLauf, 404);
  }

  // --- Aufbau ---------------------------------------------------------------------------------

  private record Aufbau(Cookie session, long projectId, long boardId, String token) {}

  private record Item(long id, int number) {}

  private Aufbau aufbau(String kennung) throws Exception {
    String email = kennung + "@example.com";
    Cookie session = session(email, PlatformRole.ADMIN);
    long projectId =
        lies(
                post("/api/projects")
                    .cookie(session)
                    .contentType(JSON)
                    .content(
                        "{\"name\":\"%s\",\"ownerEmail\":\"%s\"}"
                            .formatted(kennung + "-Projekt", email)),
                201)
            .get("id")
            .asLong();
    long boardId =
        lies(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType(JSON)
                    .content("{\"name\":\"Board\"}"),
                201)
            .get("id")
            .asLong();
    String token =
        lies(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType(JSON)
                    .content(
                        "{\"name\":\"%s\",\"projectId\":%d,\"boardId\":%d}"
                            .formatted(kennung + "-Token", projectId, boardId)),
                201)
            .get("plaintext")
            .asText();
    return new Aufbau(session, projectId, boardId, token);
  }

  /** Meldet einen laufenden Kettenlauf, der vor fünf Minuten begann, und liefert seine ID. */
  private long laufStarten(Aufbau a) throws Exception {
    return laufStarten(a, Instant.now().minus(Duration.ofMinutes(5)));
  }

  /** Meldet einen laufenden Kettenlauf mit diesem Start und liefert seine ID. */
  private long laufStarten(Aufbau a, Instant start) throws Exception {
    mvc.perform(
            post("/api/kanban/night-runs")
                .header(TOKEN_HEADER, a.token())
                .contentType(JSON)
                .content(
                    """
                    {"startedAt":"%s","mode":"CHAIN","durationMs":0,"processedCount":0,
                     "skippedCount":0,"unparsedCount":0,"complete":false,"items":[]}"""
                        .formatted(start)))
        .andExpect(status().isOk());
    Instant gesucht = start.truncatedTo(ChronoUnit.MILLIS);
    for (JsonNode lauf :
        lies(get("/api/projects/" + a.projectId() + "/night-runs").cookie(a.session()), 200)) {
      if (Instant.parse(lauf.get("startedAt").asText())
          .truncatedTo(ChronoUnit.MILLIS)
          .equals(gesucht)) {
        return lauf.get("id").asLong();
      }
    }
    throw new AssertionError("Lauf mit Start " + start + " nicht gemeldet");
  }

  private MockHttpServletRequestBuilder mitAgent(MockHttpServletRequestBuilder r, Aufbau a) {
    return mitAgent(r, a, null);
  }

  /** Mit Token und Modell, dazu der Ausweis des Laufs, wenn {@code lauf} gesetzt ist. */
  private MockHttpServletRequestBuilder mitAgent(
      MockHttpServletRequestBuilder r, Aufbau a, @Nullable Instant lauf) {
    MockHttpServletRequestBuilder mit =
        r.header(TOKEN_HEADER, a.token()).header(AGENT_HEADER, MODELL);
    return lauf == null ? mit : mit.header(Laufkennung.HEADER, lauf.toString());
  }

  private Item anlegen(Aufbau a, @Nullable String agent, String titel, @Nullable Integer herkunft)
      throws Exception {
    return anlegen(a, agent, titel, herkunft, null);
  }

  private Item anlegen(
      Aufbau a,
      @Nullable String agent,
      String titel,
      @Nullable Integer herkunft,
      @Nullable Instant lauf)
      throws Exception {
    MockHttpServletRequestBuilder r =
        post("/api/kanban/items")
            .header(TOKEN_HEADER, a.token())
            .contentType(JSON)
            .content(
                herkunft == null
                    ? "{\"title\":\"%s\"}".formatted(titel)
                    : "{\"title\":\"%s\",\"derivedFrom\":%d}".formatted(titel, herkunft));
    if (agent != null) {
      r = r.header(AGENT_HEADER, agent);
    }
    if (lauf != null) {
      r = r.header(Laufkennung.HEADER, lauf.toString());
    }
    JsonNode angelegt = lies(r, 201);
    return new Item(angelegt.get("id").asLong(), angelegt.get("number").asInt());
  }

  private void aendern(Aufbau a, Item item, String titel, String body) throws Exception {
    aendern(a, item, titel, body, null);
  }

  private void aendern(Aufbau a, Item item, String titel, String body, @Nullable Instant lauf)
      throws Exception {
    mvc.perform(
            mitAgent(put("/api/kanban/items/" + item.id()), a, lauf)
                .contentType(JSON)
                .content(json.writeValueAsString(new Inhalt(titel, body))))
        .andExpect(status().isOk());
  }

  private record Inhalt(String title, String body) {}

  private record Text(String body) {}

  private void verschieben(Aufbau a, Item item, String spalte) throws Exception {
    verschieben(a, item, spalte, null);
  }

  private void verschieben(Aufbau a, Item item, String spalte, @Nullable Instant lauf)
      throws Exception {
    mvc.perform(
            mitAgent(put("/api/kanban/items/" + item.id() + "/move"), a, lauf)
                .contentType(JSON)
                .content("{\"column\":\"%s\",\"position\":0}".formatted(spalte)))
        .andExpect(status().isOk());
  }

  /**
   * Ein Laufstand mit einer Stufenzeile, wie {@code stufeEndet} in {@code night.mjs} sie schreibt.
   */
  private static String laufstandText(String eintrag) {
    return "## Laufstand\n\nzuletzt fertig: " + eintrag + " um " + Instant.now();
  }

  private void laufstand(Aufbau a, Item item, String eintrag) throws Exception {
    laufstand(a, item, eintrag, null);
  }

  private void laufstand(Aufbau a, Item item, String eintrag, @Nullable Instant lauf)
      throws Exception {
    mvc.perform(
            mitAgent(post("/api/kanban/items/" + item.id() + "/comments"), a, lauf)
                .contentType(JSON)
                .content(json.writeValueAsString(new Text(laufstandText(eintrag)))))
        .andExpect(status().isCreated());
  }

  private void laufstandErsetzen(Aufbau a, Item item, String eintrag) throws Exception {
    JsonNode kommentare =
        lies(mitAgent(get("/api/kanban/items/" + item.id() + "/comments"), a), 200);
    assertThat(kommentare).hasSize(1);
    long kommentarId = kommentare.get(0).get("id").asLong();
    mvc.perform(
            mitAgent(patch("/api/kanban/items/" + item.id() + "/comments/" + kommentarId), a)
                .contentType(JSON)
                .content(json.writeValueAsString(new Text(laufstandText(eintrag)))))
        .andExpect(status().isNoContent());
  }

  /** Die gespeicherten Laufkennungen der Aktivitäten einer Karte — Aktivitäten ohne fallen weg. */
  private List<Instant> laufkennungen(Item item) {
    return jdbc
        .queryForList(
            "SELECT run_started_at FROM card_activity"
                + " WHERE card_id = ? AND run_started_at IS NOT NULL",
            Timestamp.class,
            item.id())
        .stream()
        .map(Timestamp::toInstant)
        .toList();
  }

  private JsonNode fortschritt(Cookie wer, long projectId, long runId, int erwartet)
      throws Exception {
    return lies(
        get("/api/projects/" + projectId + "/night-runs/" + runId + "/progress").cookie(wer),
        erwartet);
  }

  private JsonNode lies(MockHttpServletRequestBuilder r, int erwartet) throws Exception {
    String text =
        mvc.perform(r)
            .andExpect(status().is(erwartet))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return text.isEmpty() ? json.nullNode() : json.readTree(text);
  }

  private static List<String> nummernUndZustaende(JsonNode pakete) {
    List<String> ergebnis = new ArrayList<>();
    pakete.forEach(
        p -> ergebnis.add(p.get("karte").get("number").asInt() + ":" + p.get("zustand").asText()));
    return ergebnis;
  }

  private static List<String> stufen(JsonNode kette) {
    List<String> ergebnis = new ArrayList<>();
    kette
        .get("stufen")
        .forEach(s -> ergebnis.add(s.get("stufe").asText() + ":" + s.get("zustand").asText()));
    return ergebnis;
  }

  /** Die Feldnamen aller Objekte der Antwort, auf jeder Ebene. */
  private static Set<String> feldnamen(JsonNode knoten) {
    Set<String> namen = new TreeSet<>();
    if (knoten.isObject()) {
      knoten
          .properties()
          .forEach(
              e -> {
                namen.add(e.getKey());
                namen.addAll(feldnamen(e.getValue()));
              });
    } else if (knoten.isArray()) {
      knoten.forEach(k -> namen.addAll(feldnamen(k)));
    }
    return namen;
  }

  private Cookie session(String email, PlatformRole role) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, role));
    }
    return mvc.perform(
            post("/api/auth/login")
                .contentType(JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }
}
