package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End des Herkunftsbaums <b>im Karten-Detail</b> (Issue #1985, Plan #1980): {@code GET
 * /api/cards/{cardId}/tree}.
 *
 * <p>Die Rechnung selbst — Zweigauswahl, Korrektur des Anfangs, fremde Herkunft — prüfen {@code
 * DerivationTreeTest} und {@code KartenZweigServiceTest}, weil PIT nur Unit-Tests misst. Hier geht
 * es um das Zusammenspiel an echten Daten und um die Rechteprüfung.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KartenZweigIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;

  /**
   * Anforderung → Plan → zwei Pakete, dazu eine unbeteiligte Anforderung: Am Plan kommt seine Kette
   * in Baumreihenfolge, ohne die andere Anforderung.
   */
  @Test
  @DisplayName("Szenario: Plan zeigt seine Kette")
  void planZeigtSeineKette() throws Exception {
    Fixture f = fixture("kz-kette");
    JsonNode anforderung = karte(f, "[Requirement] Anforderung", null);
    JsonNode plan = karte(f, "[Plan] Plan", number(anforderung));
    JsonNode paket1 = karte(f, "[Task] Paket eins", number(plan));
    JsonNode paket2 = karte(f, "[Task] Paket zwei", number(plan));
    karte(f, "[Requirement] Andere", null);

    JsonNode baum = baum(f, id(plan));

    assertThat(nummern(baum))
        .containsExactly(number(anforderung), number(plan), number(paket1), number(paket2));
    assertThat(tiefen(baum)).containsExactly(0, 1, 2, 2);
    assertThat(baum.get(0).get("externalOrigin").asBoolean()).isFalse();
    assertThat(baum.get(1).get("derivedFrom").asInt()).isEqualTo(number(anforderung));
  }

  /** E5: Eine Karte ohne Kette bekommt eine leere Liste, keinen Fehler. */
  @Test
  void karteOhneKette_liefertLeereListe() throws Exception {
    Fixture f = fixture("kz-leer");
    long allein = id(karte(f, "[Task] Allein", null));

    assertThat(baum(f, allein)).isEmpty();
  }

  /**
   * E6: Ein Fremder bekommt dieselbe Antwort wie für eine Karte, die es nicht gibt — und erfährt
   * damit nicht, ob es sie gibt.
   */
  @Test
  void fremdeUndUnbekannteKarte_liefern404() throws Exception {
    Fixture f = fixture("kz-recht");
    JsonNode anforderung = karte(f, "[Requirement] Anforderung", null);
    long plan = id(karte(f, "[Plan] Plan", number(anforderung)));

    Cookie fremd = anmeldung.anmelden("kz-fremd@example.com", PlatformRole.USER);

    mvc.perform(get(pfad(plan)).cookie(fremd)).andExpect(status().isNotFound());
    mvc.perform(get(pfad(999_999L)).cookie(f.session)).andExpect(status().isNotFound());
    // Gegenprobe: Für das Mitglied liefert derselbe Aufruf den Baum.
    mvc.perform(get(pfad(plan)).cookie(f.session)).andExpect(status().isOk());
  }

  // --- Helfer ---------------------------------------------------------------

  private record Fixture(Cookie session, long boardId, long columnId) {}

  private static String pfad(long cardId) {
    return "/api/cards/" + cardId + "/tree";
  }

  private JsonNode baum(Fixture f, long cardId) throws Exception {
    String body =
        mvc.perform(get(pfad(cardId)).cookie(f.session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private static List<Integer> nummern(JsonNode baum) {
    List<Integer> werte = new ArrayList<>();
    baum.forEach(z -> werte.add(z.get("number").asInt()));
    return werte;
  }

  private static List<Integer> tiefen(JsonNode baum) {
    List<Integer> werte = new ArrayList<>();
    baum.forEach(z -> werte.add(z.get("depth").asInt()));
    return werte;
  }

  private static long id(JsonNode karte) {
    return karte.get("id").asLong();
  }

  private static int number(JsonNode karte) {
    return karte.get("number").asInt();
  }

  private JsonNode karte(Fixture f, String titel, Integer herkunft) throws Exception {
    String h = herkunft == null ? "" : ",\"derivedFrom\":" + herkunft;
    return legeAn(f, "{\"columnId\":%d,\"title\":\"%s\"%s}".formatted(f.columnId, titel, h));
  }

  /** Nicht `post`: Der Name verdeckte sonst den statischen Import von MockMvcRequestBuilders. */
  private JsonNode legeAn(Fixture f, String body) throws Exception {
    String antwort =
        mvc.perform(
                post("/api/boards/" + f.boardId + "/cards")
                    .cookie(f.session)
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(antwort);
  }

  private Fixture fixture(String prefix) throws Exception {
    String email = prefix + "-owner@example.com";
    Cookie session = anmeldung.anmelden(email, PlatformRole.USER);
    long projectId = createProject(email, "P-" + prefix);
    JsonNode board = createBoard(session, projectId);
    return new Fixture(
        session, board.get("id").asLong(), board.get("columns").get(0).get("id").asLong());
  }

  private long createProject(String ownerEmail, String name) throws Exception {
    Cookie admin = anmeldung.anmelden("kz-admin@example.com", PlatformRole.ADMIN);
    String body =
        mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private JsonNode createBoard(Cookie session, long projectId) throws Exception {
    String body =
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"B\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }
}
