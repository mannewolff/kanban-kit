package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Der Kettenstand einer Karte Ende zu Ende (Issue #1452, Plan #1447): {@code GET
 * /api/cards/{cardId}/night-chain}.
 *
 * <p>Die Spuren entstehen wie nachts über {@code /api/kanban/*} mit Token: Anforderung und Plan
 * anlegen, den Plan als geprüft markieren und den Laufstand mit dem Ausweis {@code X-Night-Run}
 * schreiben. Lesen darf, wer die Karte lesen darf (E15) — auch VIEWER und MEMBER, die die Läufe des
 * Projekts nicht sehen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NightRunKettenstandIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String JSON = "application/json";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void viewerUndMemberLesenDenKettenstandIhrerKarte() throws Exception {
    Aufbau a = aufbau("kette-rollen");
    Instant lauf = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
    Item anforderung = anlegen(a, "[Fachlich] Kette", null);
    Item plan = anlegen(a, "[Plan] Kette", anforderung.number());
    mvc.perform(
            put("/api/kanban/items/" + plan.id())
                .header(TOKEN_HEADER, a.token())
                .contentType(JSON)
                .content(json.writeValueAsString(new Inhalt("[Plan] Kette", "Plan-Review: fable"))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/kanban/items/" + anforderung.id() + "/comments")
                .header(TOKEN_HEADER, a.token())
                .header(Laufkennung.HEADER, lauf.toString())
                .contentType(JSON)
                .content(
                    json.writeValueAsString(
                        new Text(
                            "## Laufstand\n\nZiel: umsetzung\nPrüfer: 2\nzuletzt fertig: plan"
                                + " fertig für #"
                                + anforderung.number()
                                + " um "
                                + Instant.now()))))
        .andExpect(status().isCreated());

    for (ProjectRole rolle : List.of(ProjectRole.VIEWER, ProjectRole.MEMBER)) {
      Cookie wer =
          mitglied(
              a, "kette-rollen-" + rolle.name().toLowerCase(Locale.ROOT) + "@example.com", rolle);

      JsonNode antwort = kettenstand(wer, anforderung.id(), 200);

      assertThat(antwort.get("ziel").asText()).isEqualTo("UMSETZUNG");
      assertThat(antwort.get("pruefer").asInt()).isEqualTo(2);
      assertThat(antwort.get("zielErreicht").asBoolean()).isFalse();
      assertThat(antwort.get("grenze").isNull()).isTrue();
      assertThat(stationen(antwort))
          .containsExactly(
              "PLAN:ERLEDIGT",
              "REVIEW:STEHT_AUS",
              "PAKETE:STEHT_AUS",
              "ABDECKUNG:STEHT_AUS",
              "UMSETZUNG:STEHT_AUS",
              "VORBEREITUNG:NICHT_VORGESEHEN");
      assertThat(antwort.get("uebernommen").asBoolean()).isTrue();
      assertThat(antwort.get("planReviewVorhanden").asBoolean()).isTrue();
      assertThat(Instant.parse(antwort.get("lauf").asText())).isEqualTo(lauf);
    }
  }

  /** Vor dem Start: kein Laufstand, kein Anlauf — die Kette endet nach der Abdeckung. */
  @Test
  void ohneLaufstandStehtDieKetteVorDemStart() throws Exception {
    Aufbau a = aufbau("kette-leer");
    long anforderung = anlegen(a, "[Fachlich] Leer", null).id();

    JsonNode antwort = kettenstand(a.session(), anforderung, 200);

    assertThat(antwort.get("ziel").isNull()).isTrue();
    assertThat(antwort.get("lauf").isNull()).isTrue();
    assertThat(antwort.get("uebernommen").asBoolean()).isFalse();
    assertThat(antwort.get("planReviewVorhanden").asBoolean()).isFalse();
    assertThat(stationen(antwort)).last().isEqualTo("VORBEREITUNG:NICHT_VORGESEHEN");
  }

  /** Wer nicht Mitglied ist, erfährt nicht, dass es die Karte gibt — wie an jedem Kartenpfad. */
  @Test
  void einNutzerOhneProjektzugehoerigkeitBekommt404() throws Exception {
    Aufbau a = aufbau("kette-fremd");
    long anforderung = anlegen(a, "[Fachlich] Fremd", null).id();
    Cookie fremd = session("kette-fremd-x@example.com", PlatformRole.USER);

    kettenstand(fremd, anforderung, 404);
  }

  @Test
  void eineUnbekannteKarteIst404() throws Exception {
    Aufbau a = aufbau("kette-unbekannt");

    kettenstand(a.session(), Long.MAX_VALUE, 404);
  }

  // --- Aufbau ---------------------------------------------------------------------------------

  private record Aufbau(Cookie session, long projectId, long boardId, String token) {}

  private record Item(long id, int number) {}

  private record Inhalt(String title, String body) {}

  private record Text(String body) {}

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

  private Cookie mitglied(Aufbau a, String email, ProjectRole rolle) throws Exception {
    Cookie session = session(email, PlatformRole.USER);
    long userId = users.findByEmail(email).orElseThrow().requireId();
    memberships.save(new ProjectMembership(null, a.projectId(), userId, rolle, Instant.now()));
    return session;
  }

  private Item anlegen(Aufbau a, String titel, @Nullable Integer herkunft) throws Exception {
    JsonNode angelegt =
        lies(
            post("/api/kanban/items")
                .header(TOKEN_HEADER, a.token())
                .contentType(JSON)
                .content(
                    herkunft == null
                        ? "{\"title\":\"%s\"}".formatted(titel)
                        : "{\"title\":\"%s\",\"derivedFrom\":%d}".formatted(titel, herkunft)),
            201);
    return new Item(angelegt.get("id").asLong(), angelegt.get("number").asInt());
  }

  private JsonNode kettenstand(Cookie wer, long cardId, int erwartet) throws Exception {
    return lies(get("/api/cards/" + cardId + "/night-chain").cookie(wer), erwartet);
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

  private static List<String> stationen(JsonNode antwort) {
    List<String> ergebnis = new ArrayList<>();
    antwort
        .get("stationen")
        .forEach(s -> ergebnis.add(s.get("station").asText() + ":" + s.get("zustand").asText()));
    return ergebnis;
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
