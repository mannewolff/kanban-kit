package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.nightrun.application.NightReadyAssessmentRepository;
import org.mwolff.manban.nightrun.domain.ReadyBewertung;
import org.mwolff.manban.nightrun.domain.ReadyBewertung.Eintrag;
import org.mwolff.manban.nightrun.domain.ReadyBewertung.Grund;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Der Meldeweg der Bewertung der Ready-Karten (Issue #1726, Plan #1724 Vertrag V2): {@code POST
 * /api/kanban/night-runs/ready-assessment} mit projektgebundenem Token, ohne Sitzungs-Cookie.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NightReadyAssessmentIT extends AbstractIntegrationTest {

  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String PFAD = "/api/kanban/night-runs/ready-assessment";
  private static final String T1 = "2026-10-13T22:40:00Z";
  private static final String T2 = "2026-10-13T23:40:00Z";

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private NightReadyAssessmentRepository bewertungen;

  private static String meldung(String assessedAt, String eintraege) {
    return """
        {"assessedAt":"%s","runStartedAt":"2026-10-13T22:39:12Z","label":"kit:nightrun",
         "entries":[%s]}"""
        .formatted(assessedAt, eintraege);
  }

  private static final String ZWEI_KARTEN =
      """
      {"cardNumber":1695,"position":1,"reasons":[]},
      {"cardNumber":1700,"position":null,"reasons":[
        {"code":"DEPENDENCY","cardNumbers":[1698,1699],"text":"Abhaengigkeit #1698 nicht erfuellt"},
        {"code":"NOT_A_WORK_PACKAGE","kind":"PLAN","text":"ist ein Plan"},
        {"code":"PROTECTED_FILE","paths":["a/b,c.md",".claude/settings.json"],
         "text":"geschuetzte Datei"},
        {"code":"GANZ_NEU","text":"ein Grund, den das Board noch nicht kennt"}]}""";

  private void melde(String token, String body, int erwarteterStatus) throws Exception {
    mvc.perform(
            post(PFAD).header(TOKEN_HEADER, token).contentType("application/json").content(body))
        .andExpect(status().is(erwarteterStatus));
  }

  @Test
  void dieErsteMeldungSpeichert_undLiestAlleAngabenZurueck() throws Exception {
    Aufbau aufbau = aufbau("bewertung-erste");

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(meldung(T1, ZWEI_KARTEN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").value("STORED"))
        .andExpect(jsonPath("$.assessedAt").value(T1));

    ReadyBewertung gespeichert = bewertungen.findByProjectId(aufbau.projectId()).orElseThrow();
    assertThat(gespeichert.assessedAt()).isEqualTo(Instant.parse(T1));
    assertThat(gespeichert.runStartedAt()).isEqualTo(Instant.parse("2026-10-13T22:39:12Z"));
    assertThat(gespeichert.label()).isEqualTo("kit:nightrun");
    assertThat(gespeichert.entries())
        .containsExactly(
            new Eintrag(1695, 1, List.of()),
            new Eintrag(
                1700,
                null,
                List.of(
                    new Grund(
                        "DEPENDENCY",
                        null,
                        List.of(1698, 1699),
                        List.of(),
                        "Abhaengigkeit #1698 nicht erfuellt"),
                    new Grund("NOT_A_WORK_PACKAGE", "PLAN", List.of(), List.of(), "ist ein Plan"),
                    new Grund(
                        "PROTECTED_FILE",
                        null,
                        List.of(),
                        List.of("a/b,c.md", ".claude/settings.json"),
                        "geschuetzte Datei"),
                    new Grund(
                        "GANZ_NEU",
                        null,
                        List.of(),
                        List.of(),
                        "ein Grund, den das Board noch nicht kennt"))));
  }

  @Test
  void eineJuengereErsetztVollstaendig() throws Exception {
    Aufbau aufbau = aufbau("bewertung-juenger");
    melde(aufbau.token(), meldung(T1, ZWEI_KARTEN), 200);

    melde(aufbau.token(), meldung(T2, "{\"cardNumber\":1701,\"position\":1}"), 200);

    ReadyBewertung gespeichert = bewertungen.findByProjectId(aufbau.projectId()).orElseThrow();
    assertThat(gespeichert.assessedAt()).isEqualTo(Instant.parse(T2));
    assertThat(gespeichert.entries()).containsExactly(new Eintrag(1701, 1, List.of()));
  }

  @Test
  void einProbelaufOhneEintraegeSpeichertNurDenKopf() throws Exception {
    Aufbau aufbau = aufbau("bewertung-probelauf");
    melde(aufbau.token(), meldung(T1, ZWEI_KARTEN), 200);

    melde(
        aufbau.token(),
        """
        {"assessedAt":"%s","runStartedAt":null,"label":"kit:nightrun","entries":[]}"""
            .formatted(T2),
        200);

    ReadyBewertung gespeichert = bewertungen.findByProjectId(aufbau.projectId()).orElseThrow();
    assertThat(gespeichert.assessedAt()).isEqualTo(Instant.parse(T2));
    assertThat(gespeichert.runStartedAt()).isNull();
    assertThat(gespeichert.entries()).isEmpty();
  }

  @Test
  void eineAeltereErgibt409_undLaesstDieJuengereStehen() throws Exception {
    Aufbau aufbau = aufbau("bewertung-aelter");
    melde(aufbau.token(), meldung(T2, ZWEI_KARTEN), 200);

    melde(aufbau.token(), meldung(T1, "{\"cardNumber\":1701,\"position\":1}"), 409);

    assertThat(bewertungen.findByProjectId(aufbau.projectId()).orElseThrow().assessedAt())
        .isEqualTo(Instant.parse(T2));
  }

  @Test
  void einTokenOhneProjektbindungWirdAbgewiesen() throws Exception {
    Cookie session = session("bewertung-ungebunden@example.com", PlatformRole.ADMIN);
    String ungebunden =
        json.readTree(
                mvc.perform(
                        post("/api/access-tokens")
                            .cookie(session)
                            .contentType("application/json")
                            .content("{\"name\":\"ungebunden\"}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("plaintext")
            .asText();

    melde(ungebunden, meldung(T1, ZWEI_KARTEN), 400);
  }

  @Test
  void mehrAls500EintraegeErgeben400() throws Exception {
    Aufbau aufbau = aufbau("bewertung-zuviel");
    String eintraege =
        IntStream.rangeClosed(1, 501)
            .mapToObj(karte -> "{\"cardNumber\":" + karte + "}")
            .collect(Collectors.joining(","));

    melde(aufbau.token(), meldung(T1, eintraege), 400);

    assertThat(bewertungen.findByProjectId(aufbau.projectId())).isEmpty();
  }

  @Test
  void eineDoppeltBewerteteKarteErgibt400() throws Exception {
    Aufbau aufbau = aufbau("bewertung-doppelt");
    String doppelt =
        String.join(",", Collections.nCopies(2, "{\"cardNumber\":1695,\"position\":1}"));

    melde(aufbau.token(), meldung(T1, doppelt), 400);
  }

  @Test
  void einCodeUeber40ZeichenErgibt400() throws Exception {
    Aufbau aufbau = aufbau("bewertung-code-lang");
    String zuLang =
        "{\"cardNumber\":1,\"reasons\":[{\"code\":\"%s\",\"text\":\"x\"}]}"
            .formatted("X".repeat(41));

    melde(aufbau.token(), meldung(T1, zuLang), 400);
  }

  private record Aufbau(long projectId, String token) {}

  private Aufbau aufbau(String kennung) throws Exception {
    Cookie session = session(kennung + "@example.com", PlatformRole.ADMIN);
    long projectId =
        id(
            mvc.perform(
                    post("/api/projects")
                        .cookie(session)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"%s\",\"ownerEmail\":\"%s\"}"
                                .formatted(kennung + "-Projekt", kennung + "@example.com")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    long boardId =
        id(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(session)
                        .contentType("application/json")
                        .content("{\"name\":\"Board\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode token =
        json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(session)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"%s\",\"projectId\":%d,\"boardId\":%d}"
                                .formatted(kennung + "-Token", projectId, boardId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return new Aufbau(projectId, token.get("plaintext").asText());
  }

  private long id(String antwort) throws Exception {
    return json.readTree(antwort).get("id").asLong();
  }

  private Cookie session(String email, PlatformRole role) {
    return anmeldung.anmelden(email, role);
  }
}
