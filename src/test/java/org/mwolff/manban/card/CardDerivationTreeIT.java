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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End des Herkunftsbaums (Issue #609): {@code GET /api/boards/{boardId}/derivation-tree}.
 *
 * <p>Reiner Lesepfad. Geprüft werden die Ableitungen an echten Daten — Präorder und Tiefe, die
 * Sichtbarkeitsregeln (Archiv, Papierkorb, Ideen-Speicher), die Symmetrie der Board-Grenze für
 * Herkunft <em>und</em> Abhängigkeiten, sowie die beiden Zyklusarten. Der Herkunftszyklus wird am
 * Schreibpfad vorbei direkt in die Datenbank geschrieben, weil {@code DerivedFrom.resolve} ihn
 * sonst ablehnt; der Abhängigkeitszyklus entsteht über die normale API, weil {@code
 * setDependencies} Selbstbezug und Existenz prüft, aber keinen Zyklus.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardDerivationTreeIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void dreistufigeKette_kommtInPraeorderMitTiefe() throws Exception {
    Fixture f = fixture("dt-kette3");
    int a = number(karte(f, "A", null));
    int b = number(karte(f, "B", a));
    karte(f, "C", b);

    JsonNode baum = baum(f);

    assertThat(nummern(baum)).containsExactly(a, b, b + 1);
    assertThat(tiefen(baum)).containsExactly(0, 1, 2);
  }

  @Test
  void einstufigeKette_kommtMitWurzelUndKind() throws Exception {
    Fixture f = fixture("dt-kette1");
    int a = number(karte(f, "A", null));
    int b = number(karte(f, "B", a));

    JsonNode baum = baum(f);

    assertThat(nummern(baum)).containsExactly(a, b);
    assertThat(tiefen(baum)).containsExactly(0, 1);
  }

  @Test
  void karteOhneHerkunftUndOhneNachfahren_erscheintNicht() throws Exception {
    Fixture f = fixture("dt-einzeln");
    karte(f, "Allein", null);

    assertThat(baum(f)).isEmpty();
  }

  @Test
  void vorfahrImArchiv_erscheintWeiterhimImBaum() throws Exception {
    Fixture f = fixture("dt-archiv");
    JsonNode a = karte(f, "A", null);
    int b = number(karte(f, "B", number(a)));

    mvc.perform(post("/api/cards/" + id(a) + "/archive").cookie(f.session))
        .andExpect(status().isOk());

    assertThat(nummern(baum(f))).containsExactly(number(a), b);
  }

  @Test
  void karteImPapierkorb_erscheintNicht() throws Exception {
    Fixture f = fixture("dt-papierkorb");
    int a = number(karte(f, "A", null));
    int b = number(karte(f, "B", a));
    JsonNode c = karte(f, "C", a);

    mvc.perform(
            post("/api/cards/bulk-delete")
                .cookie(f.session)
                .contentType("application/json")
                .content("{\"cardIds\":[%d]}".formatted(id(c))))
        .andExpect(status().isNoContent());

    assertThat(nummern(baum(f))).containsExactly(a, b);
  }

  @Test
  void karteImIdeenSpeicher_erscheintNicht() throws Exception {
    Fixture f = fixture("dt-idee");
    int a = number(karte(f, "A", null));
    int b = number(karte(f, "B", a));
    JsonNode c = karte(f, "C", a);

    mvc.perform(post("/api/cards/" + id(c) + "/idea-storage").cookie(f.session))
        .andExpect(status().isOk());

    assertThat(nummern(baum(f))).containsExactly(a, b);
  }

  /**
   * {@code ON DELETE SET NULL} aus V26: Der Vorfahr verschwindet, das Kind verliert die Herkunft
   * und wird selbst zur Wurzel — sein Teilbaum bleibt vollständig.
   */
  @Test
  void geloeschterVorfahr_machtDasKindZurWurzel() throws Exception {
    Fixture f = fixture("dt-purge");
    JsonNode a = karte(f, "A", null);
    int b = number(karte(f, "B", number(a)));
    int c = number(karte(f, "C", b));

    mvc.perform(
            post("/api/cards/bulk-delete")
                .cookie(f.session)
                .contentType("application/json")
                .content("{\"cardIds\":[%d]}".formatted(id(a))))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/cards/" + id(a) + "/purge").cookie(f.session))
        .andExpect(status().isNoContent());

    JsonNode baum = baum(f);
    assertThat(nummern(baum)).containsExactly(b, c);
    assertThat(tiefen(baum)).containsExactly(0, 1);
    assertThat(baum.get(0).get("derivedFrom").isNull()).isTrue();
  }

  /** Symmetrie-Test 1 zu E4: eine Herkunftskante über die Board-Grenze wird nicht aufgelöst. */
  @Test
  void boardFremdeHerkunftOhneKinder_istWurzelMitExternalOrigin() throws Exception {
    Fixture f = fixture("dt-extern-herkunft");
    JsonNode zweites = createBoard(f.session, f.projectId);
    long zweiteSpalte = zweites.get("columns").get(0).get("id").asLong();
    int fremd =
        number(
            karteAuf(f.session, zweites.get("id").asLong(), zweiteSpalte, "Fremder Vorfahr", null));

    karte(f, "Kind", fremd);

    JsonNode baum = baum(f);
    assertThat(baum).hasSize(1);
    assertThat(baum.get(0).get("externalOrigin").asBoolean()).isTrue();
    assertThat(baum.get(0).get("depth").asInt()).isZero();
    assertThat(baum.get(0).get("derivedFrom").asInt()).isEqualTo(fremd);
  }

  /**
   * Symmetrie-Test 2 zu E4: eine Abhängigkeitsnummer über die Board-Grenze wird nicht aufgelöst.
   */
  @Test
  void boardFremdeAbhaengigkeitsnummer_istExternUndBlocktNicht() throws Exception {
    Fixture f = fixture("dt-extern-dep");
    JsonNode zweites = createBoard(f.session, f.projectId);
    long zweiteSpalte = zweites.get("columns").get(0).get("id").asLong();
    int fremd =
        number(karteAuf(f.session, zweites.get("id").asLong(), zweiteSpalte, "Fremde Karte", null));

    int a = number(karte(f, "A", null));
    JsonNode b = karte(f, "B", a);
    setzeAbhaengigkeiten(f.session, id(b), List.of(fremd));

    JsonNode kind = baum(f).get(1);
    assertThat(kind.get("externalDependencies").get(0).asInt()).isEqualTo(fremd);
    assertThat(kind.get("dependencies")).isEmpty();
    assertThat(kind.get("blocked").asBoolean()).isFalse();
  }

  /**
   * Der Normalfall einer Abhängigkeit: Die Zielkarte liegt auf dem Board, aber ohne Herkunftsbezug
   * und damit außerhalb des Baums. Aufgelöst wird gegen die Board-Menge — gegen die Baummenge wäre
   * sie fälschlich extern.
   */
  @Test
  void abhaengigkeitAufBoardKarteAusserhalbDesBaums_blocktUndIstNichtExtern() throws Exception {
    Fixture f = fixture("dt-dep-ausserhalb");
    int fremdImBoard = number(karte(f, "Ohne Herkunft", null));
    int a = number(karte(f, "A", null));
    JsonNode b = karte(f, "B", a);
    setzeAbhaengigkeiten(f.session, id(b), List.of(fremdImBoard));

    JsonNode kind = baum(f).get(1);
    assertThat(kind.get("dependencies").get(0).asInt()).isEqualTo(fremdImBoard);
    assertThat(kind.get("externalDependencies")).isEmpty();
    assertThat(kind.get("blocked").asBoolean()).isTrue();
  }

  @Test
  void offeneAbhaengigkeitBlockt_inDoneNichtMehr() throws Exception {
    Fixture f = fixture("dt-blocked");
    int a = number(karte(f, "A", null));
    JsonNode blocker = karte(f, "Blocker", a);
    JsonNode abhaengig = karte(f, "Abhaengig", a);
    setzeAbhaengigkeiten(f.session, id(abhaengig), List.of(number(blocker)));

    assertThat(zeile(baum(f), number(abhaengig)).get("blocked").asBoolean()).isTrue();

    verschiebeNachDone(f, id(blocker));

    assertThat(zeile(baum(f), number(abhaengig)).get("blocked").asBoolean()).isFalse();
  }

  @Test
  void geschwisterKommenTopologischSortiertBeiGleichstandNachNummer() throws Exception {
    Fixture f = fixture("dt-topo");
    int a = number(karte(f, "A", null));
    JsonNode b = karte(f, "B", a);
    JsonNode c = karte(f, "C", a);
    JsonNode d = karte(f, "D", a);
    // B haengt an C -> C muss vor B stehen, D ordnet sich per Nummer dahinter.
    setzeAbhaengigkeiten(f.session, id(b), List.of(number(c)));

    assertThat(nummern(baum(f))).containsExactly(a, number(c), number(b), number(d));
  }

  /**
   * Ein Herkunftsring ist über die API nicht erzeugbar. Der Lesepfad muss ihn trotzdem aushalten:
   * In der Spalte kann stehen, was nie durch diese Anwendung ging, und ein Ring wäre sonst eine
   * Endlosschleife im Server.
   */
  @Test
  void herkunftszyklusAusDerDatenbank_wirdVollstaendigUndAlsBrokenGeliefert() throws Exception {
    Fixture f = fixture("dt-zyklus");
    JsonNode x = karte(f, "X", null);
    JsonNode y = karte(f, "Y", number(x));
    // Den Ring schliessen: X stammt jetzt von Y ab. Am Schreibpfad vorbei.
    jdbc.update("UPDATE card SET derived_from_card_id = ? WHERE id = ?", id(y), id(x));

    JsonNode baum = baum(f);

    assertThat(nummern(baum)).containsExactly(number(x), number(y));
    assertThat(tiefen(baum)).containsExactly(0, 1);
    for (JsonNode zeile : baum) {
      assertThat(zeile.get("broken").asBoolean()).isTrue();
    }
  }

  @Test
  void abhaengigkeitszyklusUeberDieApi_bleibtDeterministisch() throws Exception {
    Fixture f = fixture("dt-dep-zyklus");
    int a = number(karte(f, "A", null));
    JsonNode b = karte(f, "B", a);
    JsonNode c = karte(f, "C", a);
    setzeAbhaengigkeiten(f.session, id(b), List.of(number(c)));
    setzeAbhaengigkeiten(f.session, id(c), List.of(number(b)));

    assertThat(nummern(baum(f))).containsExactly(a, number(b), number(c));
    assertThat(nummern(baum(f))).containsExactly(a, number(b), number(c));
  }

  @Test
  void nichtmitglied_erhaeltDenselbenStatusWieAufDerKartenliste() throws Exception {
    Fixture f = fixture("dt-fremd");
    Cookie fremde = loginAs("dt-fremd-gast@example.com");

    int kartenStatus =
        mvc.perform(get("/api/boards/" + f.boardId + "/cards").cookie(fremde))
            .andReturn()
            .getResponse()
            .getStatus();

    mvc.perform(get("/api/boards/" + f.boardId + "/derivation-tree").cookie(fremde))
        .andExpect(status().is(kartenStatus));
  }

  // --- Helfer ---------------------------------------------------------------

  private record Fixture(Cookie session, long projectId, long boardId, long columnId) {}

  private JsonNode baum(Fixture f) throws Exception {
    String body =
        mvc.perform(get("/api/boards/" + f.boardId + "/derivation-tree").cookie(f.session))
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

  private static JsonNode zeile(JsonNode baum, int nummer) {
    for (JsonNode z : baum) {
      if (z.get("number").asInt() == nummer) {
        return z;
      }
    }
    throw new AssertionError("Nummer " + nummer + " nicht im Baum");
  }

  private static int number(JsonNode karte) {
    return karte.get("number").asInt();
  }

  private static long id(JsonNode karte) {
    return karte.get("id").asLong();
  }

  private void setzeAbhaengigkeiten(Cookie session, long cardId, List<Integer> nummern)
      throws Exception {
    String liste = nummern.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    mvc.perform(
            patch("/api/cards/" + cardId)
                .cookie(session)
                .contentType("application/json")
                .content("{\"title\":\"unveraendert\",\"dependencies\":[%s]}".formatted(liste)))
        .andExpect(status().isOk());
  }

  private void verschiebeNachDone(Fixture f, long cardId) throws Exception {
    String body =
        mvc.perform(get("/api/boards/" + f.boardId).cookie(f.session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode spalten = json.readTree(body).get("columns");
    long done = spalten.get(spalten.size() - 1).get("id").asLong();
    mvc.perform(
            post("/api/cards/" + cardId + "/move")
                .cookie(f.session)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"position\":0}".formatted(done)))
        .andExpect(status().isOk());
  }

  private Fixture fixture(String prefix) throws Exception {
    String email = prefix + "-owner@example.com";
    Cookie session = loginAs(email);
    long projectId = createProject(email, "P-" + prefix);
    JsonNode board = createBoard(session, projectId);
    return new Fixture(
        session,
        projectId,
        board.get("id").asLong(),
        board.get("columns").get(0).get("id").asLong());
  }

  private Cookie loginAs(String email) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.USER));
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

  private long createProject(String ownerEmail, String name) throws Exception {
    String body =
        mvc.perform(
                post("/api/projects")
                    .cookie(platformAdminSession())
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private Cookie platformAdminSession() throws Exception {
    String email = "dt-admin@example.com";
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.ADMIN));
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

  private JsonNode karte(Fixture f, String titel, Integer derivedFrom) throws Exception {
    return karteAuf(f.session, f.boardId, f.columnId, titel, derivedFrom);
  }

  private JsonNode karteAuf(
      Cookie session, long boardId, long columnId, String titel, Integer derivedFrom)
      throws Exception {
    String herkunft = derivedFrom == null ? "" : ",\"derivedFrom\":" + derivedFrom;
    String body =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"columnId\":%d,\"title\":\"%s\"%s}"
                            .formatted(columnId, titel, herkunft)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }
}
