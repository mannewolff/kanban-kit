package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Lesender Archivzugang der Kanban-Strecke über ein board-gebundenes Token (Plan #1574):
 * Einzelabruf nach Nummer, Suche nach Text, Art, Vorhaben und Zeitraum und die Herkunftskette.
 *
 * <p>Archiv und Papierkorb werden per SQL gesetzt ({@code archived}, {@code deleted_at}): Der Test
 * prüft die Lesesicht, nicht die Wege dorthin.
 */
// PMD.TooManyMethods, PMD.GodClass: je Fall der Archivstrecke ein Test plus die Request-Hilfen —
// der Testumfang folgt den Kriterien des Plans; mit der Herkunftskette (Issue #1580) misst PMD die
// vielen JSON-Zugriffe der Prüfungen als God Class.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.GodClass"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatArchivIT extends AbstractIntegrationTest {

  private static final String TOKEN_HEADER = "X-Kanban-Token";

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  // --- Einzelabruf nach Nummer (Kriterium 1) ---------------------------------

  @Test
  void archivierteKarte_istNachNummerAbrufbar_mitAllenKommentaren() throws Exception {
    Fixture f = fixture("ar-einzel");
    long vorfahr = ingest(f.token, "[Fachlich] Herkunft", null);
    long id = ingest(f.token, "[Plan] Archivierter Plan", numberOf(vorfahr));
    comment(f.token, id, "## Review\nerster");
    comment(f.token, id, "## Abschluss\nzweiter");
    jdbc.update("UPDATE card SET archived = true, moved_to_done_at = now() WHERE id = ?", id);

    JsonNode detail = byNumber(f.token, numberOf(id), status().isOk());

    assertThat(detail.get("archived").asBoolean()).isTrue();
    assertThat(detail.get("createdAt").isNull()).isFalse();
    assertThat(detail.get("movedToDoneAt").isNull()).isFalse();
    JsonNode item = detail.get("item");
    assertThat(item.get("id").asLong()).isEqualTo(id);
    assertThat(item.get("number").asInt()).isEqualTo(numberOf(id));
    assertThat(item.get("title").asText()).isEqualTo("[Plan] Archivierter Plan");
    assertThat(item.get("type").asText()).isEqualTo("card");
    assertThat(item.get("derivedFrom").asInt()).isEqualTo(numberOf(vorfahr));
    List<String> bodies = new ArrayList<>();
    detail.get("comments").forEach(c -> bodies.add(c.get("body").asText()));
    assertThat(bodies).containsExactly("## Review\nerster", "## Abschluss\nzweiter");
    // Herkunft der Kommentare (Issue #1623): beide über den Token, ohne gemeldetes Modell
    detail
        .get("comments")
        .forEach(
            c -> {
              assertThat(c.get("origin").asText()).isEqualTo("TOKEN");
              assertThat(c.get("tokenName").asText()).isEqualTo("archiv-token");
              assertThat(c.get("agent").isNull()).isTrue();
            });
  }

  @Test
  void uebersicht_zeigtDieArchivierteKarteWeiterhinNicht() throws Exception {
    Fixture f = fixture("ar-uebersicht");
    long aktiv = ingest(f.token, "Aktiv", null);
    long archiviert = ingest(f.token, "Archiviert", null);
    jdbc.update("UPDATE card SET archived = true WHERE id = ?", archiviert);

    List<Long> ids = new ArrayList<>();
    json.readTree(
            mvc.perform(get("/api/kanban/items").header(TOKEN_HEADER, f.token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .forEach(spalte -> spalte.forEach(item -> ids.add(item.get("id").asLong())));

    assertThat(ids).contains(aktiv).doesNotContain(archiviert);
  }

  @Test
  void aktiveKarte_istEbenfallsNachNummerAbrufbar() throws Exception {
    Fixture f = fixture("ar-aktiv");
    long id = ingest(f.token, "Aktiv", null);

    JsonNode detail = byNumber(f.token, numberOf(id), status().isOk());

    assertThat(detail.get("archived").asBoolean()).isFalse();
    assertThat(detail.get("item").get("column").asText()).isEqualTo("BACKLOG");
    assertThat(detail.get("comments")).isEmpty();
  }

  @Test
  void papierkorbNummer_istNichtGefunden() throws Exception {
    Fixture f = fixture("ar-papierkorb");
    long id = ingest(f.token, "Gelöscht", null);
    jdbc.update("UPDATE card SET deleted_at = now() WHERE id = ?", id);

    String papierkorb = byNumberRaw(f.token, numberOf(id), status().isNotFound());
    String unbekannt = byNumberRaw(f.token, 999_999, status().isNotFound());

    assertThat(json.readTree(papierkorb).get("detail"))
        .isEqualTo(json.readTree(unbekannt).get("detail"));
  }

  @Test
  void nummerEinesAnderenBoards_istNichtGefunden() throws Exception {
    Fixture f = fixture("ar-fremd");
    long anderesBoard = createBoard(f.owner, f.projectId, "Anderes Board");
    String andererToken = boundToken(f.owner, f.projectId, anderesBoard);
    long fremd = ingest(andererToken, "Auf dem anderen Board", null);
    jdbc.update("UPDATE card SET archived = true WHERE id = ?", fremd);

    String antwort = byNumberRaw(f.token, numberOf(fremd), status().isNotFound());
    String unbekannt = byNumberRaw(f.token, 999_999, status().isNotFound());

    assertThat(json.readTree(antwort).get("detail"))
        .isEqualTo(json.readTree(unbekannt).get("detail"));
  }

  @Test
  void ungebundenesToken_wirdMit409Abgewiesen() throws Exception {
    Fixture f = fixture("ar-ungebunden");
    long id = ingest(f.token, "Karte", null);

    byNumberRaw(unboundToken(f.owner), numberOf(id), status().isConflict());
  }

  // --- Suche nach Art, Vorhaben und Zeitraum (Kriterien 2 und 4) ----------------

  @Test
  void suche_nachArt_liefertArchivierteTreffer_mitAngaben() throws Exception {
    Fixture f = fixture("su-art");
    long fachlich = ingest(f.token, "[Fachlich] Alter Fachplan", null);
    long plan = ingest(f.token, "[Plan] Plan", null);
    ingest(f.token, "Arbeitspaket", null);
    jdbc.update(
        "UPDATE card SET archived = true, moved_to_done_at = now(), description = ? WHERE id = ?",
        "## Ziel\nFrüher entschieden",
        fachlich);

    JsonNode page = search(f.token, "?kind=FACHLICH&kind=PLAN", status().isOk());

    List<Long> ids = new ArrayList<>();
    page.get("hits").forEach(h -> ids.add(h.get("id").asLong()));
    assertThat(ids).containsExactly(plan, fachlich);
    assertThat(page.get("nextCursor").isNull()).isTrue();
    JsonNode hit = page.get("hits").get(1);
    assertThat(hit.get("number").asInt()).isEqualTo(numberOf(fachlich));
    assertThat(hit.get("title").asText()).isEqualTo("[Fachlich] Alter Fachplan");
    assertThat(hit.get("kind").asText()).isEqualTo("FACHLICH");
    assertThat(hit.get("column").asText()).isEqualTo("BACKLOG");
    assertThat(hit.get("archived").asBoolean()).isTrue();
    assertThat(hit.get("createdAt").isNull()).isFalse();
    assertThat(hit.get("movedToDoneAt").isNull()).isFalse();
    assertThat(hit.get("excerpt").get("text").asText()).isEqualTo("## Ziel Früher entschieden");
    assertThat(hit.get("excerpt").get("source").asText()).isEqualTo("BODY");
    assertThat(hit.get("excerpt").get("commentId").isNull()).isTrue();
  }

  @Test
  void suche_unbekannteArt_ist400() throws Exception {
    Fixture f = fixture("su-unbekannt");

    search(f.token, "?kind=FACHPLAN", status().isBadRequest());
  }

  @Test
  void suche_ungueltigeGrenzen_sind400() throws Exception {
    Fixture f = fixture("su-grenzen");

    search(f.token, "?limit=0", status().isBadRequest());
    search(f.token, "?limit=201", status().isBadRequest());
    search(f.token, "?cursor=0", status().isBadRequest());
    search(
        f.token,
        "?createdFrom=2026-02-01T00:00:00Z&createdTo=2026-01-01T00:00:00Z",
        status().isBadRequest());
  }

  @Test
  void suche_ungebundenesToken_wirdMit409Abgewiesen() throws Exception {
    Fixture f = fixture("su-ungebunden");

    search(unboundToken(f.owner), "", status().isConflict());
  }

  @Test
  void suche_nachVorhaben_schliesstArchiviertesMitgliedEin() throws Exception {
    Fixture f = fixture("su-vorhaben");
    long vorhaben = ingest(f.token, "Vorhaben", null);
    jdbc.update("UPDATE card SET type = 'EPIC', status = NULL WHERE id = ?", vorhaben);
    long wurzel = ingest(f.token, "[Plan] Wurzel", null);
    long kind = ingest(f.token, "Paket", numberOf(wurzel));
    ingest(f.token, "Fremd", null);
    jdbc.update("UPDATE card SET parent_id = ? WHERE id = ?", vorhaben, wurzel);
    jdbc.update("UPDATE card SET archived = true WHERE id = ?", kind);

    JsonNode page = search(f.token, "?epic=" + numberOf(vorhaben), status().isOk());
    JsonNode keinVorhaben = search(f.token, "?epic=" + numberOf(wurzel), status().isOk());

    List<Long> ids = new ArrayList<>();
    page.get("hits").forEach(h -> ids.add(h.get("id").asLong()));
    assertThat(ids).containsExactly(kind, wurzel);
    assertThat(keinVorhaben.get("hits")).isEmpty();
  }

  @Test
  void suche_nachZeitraum_istHalboffen() throws Exception {
    Fixture f = fixture("su-zeitraum");
    long frueh = ingest(f.token, "Früh", null);
    long mitte = ingest(f.token, "Mitte", null);
    long spaet = ingest(f.token, "Spät", null);
    setCreated(frueh, "2026-01-01T00:00:00Z");
    setCreated(mitte, "2026-02-01T00:00:00Z");
    setCreated(spaet, "2026-03-01T00:00:00Z");

    JsonNode page =
        search(
            f.token,
            "?createdFrom=2026-02-01T00:00:00Z&createdTo=2026-03-01T00:00:00Z",
            status().isOk());

    List<Long> ids = new ArrayList<>();
    page.get("hits").forEach(h -> ids.add(h.get("id").asLong()));
    assertThat(ids).containsExactly(mitte);
  }

  @Test
  void suche_zweihundertfuenfzigKarten_inTeilen_vollstaendigUndOhneDoppel() throws Exception {
    Fixture f = fixture("su-teile");
    Set<Long> angelegt = new HashSet<>();
    for (int i = 0; i < 250; i++) {
      angelegt.add(ingest(f.token, "Karte " + i, null));
    }
    jdbc.update("UPDATE card SET archived = true WHERE id IN (?, ?)", min(angelegt), max(angelegt));

    List<Long> gesammelt = new ArrayList<>();
    String cursor = null;
    int teile = 0;
    do {
      JsonNode page =
          search(
              f.token, "?limit=50" + (cursor == null ? "" : "&cursor=" + cursor), status().isOk());
      assertThat(page.get("hits").size()).isLessThanOrEqualTo(50);
      page.get("hits").forEach(h -> gesammelt.add(h.get("id").asLong()));
      cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
      teile++;
    } while (cursor != null);

    assertThat(teile).isEqualTo(5);
    assertThat(gesammelt).hasSize(250).doesNotHaveDuplicates();
    assertThat(new HashSet<>(gesammelt)).isEqualTo(angelegt);
  }

  // --- Textsuche (Kriterium 2, E3, E4, E16) -----------------------------------

  @Test
  void suche_nachText_findetTitelTextUndKommentarArchivierterKarten() throws Exception {
    Fixture f = fixture("tx-orte");
    long imTitel = ingest(f.token, "[Fachlich] Archivsuche im Titel", null);
    long imText = ingest(f.token, "[Plan] Plan", null);
    long imKommentar = ingest(f.token, "Paket", null);
    ingest(f.token, "Unbeteiligt", null);
    jdbc.update(
        "UPDATE card SET description = ? WHERE id = ?",
        "## Ziel\nWir bauen die ARCHIVSUCHE aus",
        imText);
    comment(f.token, imKommentar, "Ohne das Wort");
    comment(f.token, imKommentar, "## Review\nDie Archivsuche passt");
    comment(f.token, imKommentar, "Noch einmal Archivsuche");
    jdbc.update(
        "UPDATE card SET archived = true WHERE id IN (?, ?, ?)", imTitel, imText, imKommentar);
    Long ersterTreffer =
        jdbc.queryForObject(
            "SELECT min(id) FROM comment WHERE card_id = ? AND body LIKE '## Review%'",
            Long.class, imKommentar);

    JsonNode page = searchText(f.token, "  archivsuche ", status().isOk());

    List<Long> ids = new ArrayList<>();
    page.get("hits").forEach(h -> ids.add(h.get("id").asLong()));
    assertThat(ids).containsExactly(imKommentar, imText, imTitel);
    JsonNode kommentar = page.get("hits").get(0).get("excerpt");
    assertThat(kommentar.get("source").asText()).isEqualTo("COMMENT");
    assertThat(kommentar.get("commentId").asLong()).isEqualTo(ersterTreffer);
    assertThat(kommentar.get("text").asText()).isEqualTo("## Review Die Archivsuche passt");
    JsonNode text = page.get("hits").get(1).get("excerpt");
    assertThat(text.get("source").asText()).isEqualTo("BODY");
    assertThat(text.get("text").asText()).isEqualTo("## Ziel Wir bauen die ARCHIVSUCHE aus");
    assertThat(text.get("commentId").isNull()).isTrue();
    JsonNode titel = page.get("hits").get(2).get("excerpt");
    assertThat(titel.get("source").asText()).isEqualTo("TITLE");
    assertThat(titel.get("text").asText()).isEqualTo("[Fachlich] Archivsuche im Titel");
  }

  @Test
  void suche_nachText_wirktMitDenAnderenFilternZusammen() throws Exception {
    Fixture f = fixture("tx-art");
    long fachlich = ingest(f.token, "[Fachlich] Export", null);
    ingest(f.token, "Paket Export", null);

    JsonNode page = searchText(f.token, "export", "FACHLICH", status().isOk());

    assertThat(page.get("hits")).hasSize(1);
    assertThat(page.get("hits").get(0).get("id").asLong()).isEqualTo(fachlich);
  }

  @Test
  void suche_prozentUndUnterstrich_geltenWoertlich() throws Exception {
    Fixture f = fixture("tx-zeichen");
    long prozent = ingest(f.token, "Zu 100% fertig", null);
    ingest(f.token, "Zu 1000 fertig", null);
    long unterstrich = ingest(f.token, "Feld a_b", null);
    ingest(f.token, "Feld axb", null);
    long kommentiert = ingest(f.token, "Kommentiert", null);
    comment(f.token, kommentiert, "Rabatt 5% auf x_y");
    long kommentiertOhne = ingest(f.token, "Kommentiert ohne", null);
    comment(f.token, kommentiertOhne, "Rabatt 50 auf xzy");

    assertThat(ids(searchText(f.token, "100%", status().isOk()))).containsExactly(prozent);
    assertThat(ids(searchText(f.token, "a_b", status().isOk()))).containsExactly(unterstrich);
    assertThat(ids(searchText(f.token, "5% auf x_y", status().isOk())))
        .containsExactly(kommentiert);
  }

  @Test
  void suche_kommentarImPapierkorbOderAufAnderemBoard_istKeinTreffer() throws Exception {
    Fixture f = fixture("tx-grenze");
    long geloescht = ingest(f.token, "Gelöscht", null);
    comment(f.token, geloescht, "Geheimwort im Papierkorb");
    jdbc.update("UPDATE card SET deleted_at = now() WHERE id = ?", geloescht);
    long anderesBoard = createBoard(f.owner, f.projectId, "Anderes Board");
    String andererToken = boundToken(f.owner, f.projectId, anderesBoard);
    long fremd = ingest(andererToken, "Fremd", null);
    comment(andererToken, fremd, "Geheimwort auf dem anderen Board");

    assertThat(searchText(f.token, "geheimwort", status().isOk()).get("hits")).isEmpty();
    assertThat(ids(searchText(andererToken, "geheimwort", status().isOk()))).containsExactly(fremd);
  }

  @Test
  void suche_suchbegriffUeber200Zeichen_ist400() throws Exception {
    Fixture f = fixture("tx-lang");

    searchText(f.token, "x".repeat(200), status().isOk());
    searchText(f.token, "x".repeat(201), status().isBadRequest());
  }

  // --- Herkunftskette (Kriterium 3, E11–E13) ---------------------------------

  @Test
  void kette_einesArbeitspakets_reichtUeberDenArchiviertenPlanBisZumFachlich() throws Exception {
    Fixture f = fixture("ke-archiv");
    long fachlich = ingest(f.token, "[Fachlich] Anlass", null);
    long plan = ingest(f.token, "[Plan] Weg", numberOf(fachlich));
    long paket = ingest(f.token, "Paket", numberOf(plan));
    jdbc.update("UPDATE card SET archived = true WHERE id = ?", plan);

    JsonNode kette = lineage(f.token, numberOf(paket), status().isOk());

    assertThat(kette.get("card").get("number").asInt()).isEqualTo(numberOf(paket));
    assertThat(kette.get("card").get("kind").asText()).isEqualTo("ARBEITSPAKET");
    assertThat(kette.get("card").get("depth").asInt()).isZero();
    JsonNode vorfahren = kette.get("ancestors");
    assertThat(vorfahren).hasSize(2);
    assertThat(vorfahren.get(0).get("number").asInt()).isEqualTo(numberOf(plan));
    assertThat(vorfahren.get(0).get("title").asText()).isEqualTo("[Plan] Weg");
    assertThat(vorfahren.get(0).get("kind").asText()).isEqualTo("PLAN");
    assertThat(vorfahren.get(0).get("column").asText()).isEqualTo("BACKLOG");
    assertThat(vorfahren.get(0).get("archived").asBoolean()).isTrue();
    assertThat(vorfahren.get(0).get("depth").asInt()).isEqualTo(1);
    assertThat(vorfahren.get(0).get("gap").asBoolean()).isFalse();
    assertThat(vorfahren.get(1).get("number").asInt()).isEqualTo(numberOf(fachlich));
    assertThat(vorfahren.get(1).get("kind").asText()).isEqualTo("FACHLICH");
    assertThat(vorfahren.get(1).get("depth").asInt()).isEqualTo(2);
    assertThat(kette.get("descendants")).isEmpty();

    JsonNode vonOben = lineage(f.token, numberOf(fachlich), status().isOk());
    List<Integer> nachfahren = new ArrayList<>();
    vonOben.get("descendants").forEach(n -> nachfahren.add(n.get("number").asInt()));
    assertThat(nachfahren).containsExactly(numberOf(plan), numberOf(paket));
    assertThat(vonOben.get("ancestors")).isEmpty();
  }

  @Test
  void kette_ueberEinenPlanImPapierkorb_zeigtDieLueckeUndDasFachlichDarueber() throws Exception {
    Fixture f = fixture("ke-papierkorb");
    long fachlich = ingest(f.token, "[Fachlich] Anlass", null);
    long plan = ingest(f.token, "[Plan] Weg", numberOf(fachlich));
    long paket = ingest(f.token, "Paket", numberOf(plan));
    jdbc.update("UPDATE card SET deleted_at = now() WHERE id = ?", plan);

    JsonNode vorfahren = lineage(f.token, numberOf(paket), status().isOk()).get("ancestors");

    assertThat(vorfahren).hasSize(2);
    JsonNode luecke = vorfahren.get(0);
    assertThat(luecke.get("gap").asBoolean()).isTrue();
    assertThat(luecke.get("number").asInt()).isEqualTo(numberOf(plan));
    assertThat(luecke.get("depth").asInt()).isEqualTo(1);
    assertThat(luecke.path("title").isNull()).isTrue();
    assertThat(luecke.path("kind").isNull()).isTrue();
    assertThat(luecke.path("column").isNull()).isTrue();
    assertThat(vorfahren.get(1).get("number").asInt()).isEqualTo(numberOf(fachlich));
    assertThat(vorfahren.get(1).get("gap").asBoolean()).isFalse();
    assertThat(vorfahren.get(1).get("depth").asInt()).isEqualTo(2);
    // Ein Nachfahr im Papierkorb fehlt samt Teilbaum (E13).
    assertThat(lineage(f.token, numberOf(fachlich), status().isOk()).get("descendants")).isEmpty();
  }

  @Test
  void kette_papierkorbNummer_istNichtGefunden() throws Exception {
    Fixture f = fixture("ke-404");
    long id = ingest(f.token, "Gelöscht", null);
    jdbc.update("UPDATE card SET deleted_at = now() WHERE id = ?", id);

    JsonNode papierkorb = lineage(f.token, numberOf(id), status().isNotFound());
    JsonNode unbekannt = lineage(f.token, 999_999, status().isNotFound());

    assertThat(papierkorb.get("detail")).isEqualTo(unbekannt.get("detail"));
    lineage(unboundToken(f.owner), numberOf(id), status().isConflict());
  }

  // --- Hilfen ---------------------------------------------------------------

  private record Fixture(long projectId, Cookie owner, String token) {}

  private Fixture fixture(String prefix) throws Exception {
    Cookie owner = session(prefix + "-owner@example.com", PlatformRole.USER);
    Cookie admin = session(prefix + "-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "P-" + prefix, prefix + "-owner@example.com");
    long boardId = createBoard(owner, projectId, "B");
    return new Fixture(projectId, owner, boundToken(owner, projectId, boardId));
  }

  private JsonNode byNumber(String token, int number, ResultMatcher expected) throws Exception {
    return json.readTree(byNumberRaw(token, number, expected));
  }

  private String byNumberRaw(String token, int number, ResultMatcher expected) throws Exception {
    return mvc.perform(get("/api/kanban/items/by-number/" + number).header(TOKEN_HEADER, token))
        .andExpect(expected)
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private JsonNode lineage(String token, int number, ResultMatcher expected) throws Exception {
    return json.readTree(
        mvc.perform(
                get("/api/kanban/items/by-number/" + number + "/lineage")
                    .header(TOKEN_HEADER, token))
            .andExpect(expected)
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode search(String token, String query, ResultMatcher expected) throws Exception {
    return json.readTree(
        mvc.perform(get("/api/kanban/search" + query).header(TOKEN_HEADER, token))
            .andExpect(expected)
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode searchText(String token, String q, ResultMatcher expected) throws Exception {
    return json.readTree(
        mvc.perform(get("/api/kanban/search").param("q", q).header(TOKEN_HEADER, token))
            .andExpect(expected)
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode searchText(String token, String q, String kind, ResultMatcher expected)
      throws Exception {
    return json.readTree(
        mvc.perform(
                get("/api/kanban/search")
                    .param("q", q)
                    .param("kind", kind)
                    .header(TOKEN_HEADER, token))
            .andExpect(expected)
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static List<Long> ids(JsonNode page) {
    List<Long> ids = new ArrayList<>();
    page.get("hits").forEach(h -> ids.add(h.get("id").asLong()));
    return ids;
  }

  private void setCreated(long cardId, String instant) {
    jdbc.update(
        "UPDATE card SET created_at = CAST(? AS timestamptz) WHERE id = ?", instant, cardId);
  }

  private static long min(Set<Long> ids) {
    return ids.stream().mapToLong(Long::longValue).min().orElseThrow();
  }

  private static long max(Set<Long> ids) {
    return ids.stream().mapToLong(Long::longValue).max().orElseThrow();
  }

  private long ingest(String token, String title, Integer derivedFrom) throws Exception {
    String body =
        derivedFrom == null
            ? "{\"title\":\"%s\"}".formatted(title)
            : "{\"title\":\"%s\",\"derivedFrom\":%d}".formatted(title, derivedFrom);
    return json.readTree(
            mvc.perform(
                    post("/api/kanban/items")
                        .header(TOKEN_HEADER, token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private void comment(String token, long cardId, String text) throws Exception {
    mvc.perform(
            post("/api/kanban/items/" + cardId + "/comments")
                .header(TOKEN_HEADER, token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("body", text))))
        .andExpect(status().is2xxSuccessful());
  }

  private int numberOf(long cardId) {
    Integer n = jdbc.queryForObject("SELECT number FROM card WHERE id = ?", Integer.class, cardId);
    if (n == null) {
      throw new IllegalStateException("Karte ohne Nummer: " + cardId);
    }
    return n;
  }

  private String boundToken(Cookie session, long projectId, long boardId) throws Exception {
    return token(
        session,
        "{\"name\":\"archiv-token\",\"projectId\":%d,\"boardId\":%d}"
            .formatted(projectId, boardId));
  }

  private String unboundToken(Cookie session) throws Exception {
    return token(session, "{\"name\":\"archiv-ungebunden\"}");
  }

  private String token(Cookie session, String request) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(session)
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("plaintext")
        .asText();
  }

  private long createProject(Cookie admin, String name, String ownerEmail) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private long createBoard(Cookie session, long projectId, String name) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(session)
                        .contentType("application/json")
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private Cookie session(String email, PlatformRole role) {
    return anmeldung.anmelden(email, role);
  }
}
