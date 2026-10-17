package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.DoneRetentionService;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die vier Szenarien der fachlichen Quelle #1751, die am Zusammenspiel von Erfassung (Issue #1768)
 * und Endpunkten (Issue #1773) hängen (Issue #1774, Plan #1765 E3, E4, E10): durchgehend über die
 * Schreibwege, die das Board benutzt, und gelesen über {@code /dashboard/flow} und {@code
 * /dashboard/aging}.
 *
 * <p>Jeder Fall stellt die Uhr zuerst auf den nächsten Mittag in der Zone des Lesers, damit kein
 * Vorher-nachher-Vergleich über eine Tagesgrenze läuft.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(StellbareUhrConfig.class)
class FlussUndAlterSzenarienIT extends AbstractIntegrationTest {

  private static final String OWNER = "szenario-owner@example.com";
  private static final String ZONE = "Europe/Berlin";
  private static final int LETZTER_TAG = 83;

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ProjectService projects;
  @Autowired private BoardService boards;
  @Autowired private CardService cardService;
  @Autowired private DoneRetentionService doneRetention;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock uhr;

  private long owner;
  private BoardView board;
  private Cookie login;

  @BeforeEach
  void seed() throws Exception {
    long admin = nutzer("szenario-admin@example.com", PlatformRole.ADMIN);
    owner = nutzer(OWNER, PlatformRole.USER);
    long projectId = projects.create(admin, "Szenarien", OWNER).id();
    board = boardMitBacklogSpalten(boards.createBoard(owner, projectId, "B"));
    login = anmeldung.anmelden(OWNER, PlatformRole.USER);
    mittag();
    // Erfasst und nachgetragen seit langem: Jeder Tag der zwölf Wochen ist bekannt.
    marke(uhr.instant().minus(Duration.ofDays(200)));
  }

  @Test
  @DisplayName("Szenario: Sortieren im Backlog ist kein Fluss")
  void sortierenImBacklogIstKeinFluss() throws Exception {
    long karte = anlegen("Backlog", "Paket");
    weiter(Duration.ofHours(1));
    List<Map<String, Object>> aufenthalteVorher = aufenthalte(karte);
    String flussVorher = fluss();

    verschieben(karte, "Anstehend");
    verschieben(karte, "Parken");
    verschieben(karte, "Backlog");

    assertThat(aufenthalte(karte)).isEqualTo(aufenthalteVorher);
    assertThat(fluss()).isEqualTo(flussVorher);
    mvc.perform(get(flow()).param("zone", ZONE).cookie(login))
        .andExpect(jsonPath(tag("backlog")).value(1))
        .andExpect(jsonPath(tag("ready")).value(0));
  }

  @Test
  @DisplayName("Szenario: Zustand vor Spalte")
  void zustandVorSpalte() throws Exception {
    // Ohne Prozessspalte „Ready“ setzt PUT …/status nur den Zustand und verschiebt nicht.
    boards.updateColumn(owner, spalte("Ready"), "Bereit", null);
    long karte = anlegen("Anstehend", "Paket");
    weiter(Duration.ofHours(3));

    mvc.perform(
            put("/api/cards/" + karte + "/status")
                .cookie(login)
                .contentType("application/json")
                .content("{\"status\":\"READY\"}"))
        .andExpect(status().isNoContent());
    weiter(Duration.ofHours(2));

    assertThat(jdbc.queryForObject("SELECT column_id FROM card WHERE id = ?", Long.class, karte))
        .isEqualTo(spalte("Anstehend"));
    mvc.perform(get(flow()).param("zone", ZONE).cookie(login))
        .andExpect(status().isOk())
        .andExpect(jsonPath(tag("ready")).value(1))
        .andExpect(jsonPath(tag("backlog")).value(0));
    mvc.perform(get(aging()).cookie(login))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready.cards.length()").value(1))
        .andExpect(jsonPath("$.ready.cards[0].id").value(karte))
        .andExpect(jsonPath("$.ready.cards[0].ageSeconds").value(Duration.ofHours(2).toSeconds()))
        .andExpect(jsonPath("$.ready.cards[0].ageIsMinimum").value(false));
  }

  @Test
  @DisplayName("Szenario: Aufräumen ist kein Wiederöffnen")
  void aufraeumenIstKeinWiederoeffnen() throws Exception {
    long karte = anlegen("In Review", "Paket");
    weiter(Duration.ofDays(1));
    verschieben(karte, "Done");
    weiter(Duration.ofDays(21));
    String flussVorher = fluss();
    String alterVorher = alter();
    mvc.perform(get(flow()).param("zone", ZONE).cookie(login))
        .andExpect(jsonPath(tag("done")).value(1));
    mvc.perform(get(aging()).cookie(login))
        .andExpect(jsonPath("$.inReview.comparisonCount").value(1));

    assertThat(doneRetention.archiveExpiredDoneCards(uhr.instant(), 14)).isEqualTo(1);

    assertThat(jdbc.queryForObject("SELECT archived FROM card WHERE id = ?", Boolean.class, karte))
        .isTrue();
    assertThat(fluss()).isEqualTo(flussVorher);
    assertThat(alter()).isEqualTo(alterVorher);
  }

  @Test
  @DisplayName("Szenario: Dokumente bleiben draußen")
  void dokumenteBleibenDraussen() throws Exception {
    long plan = anlegen("In Review", "[Plan] Leitstand");
    long paket = anlegen("In Review", "Paket");
    weiter(Duration.ofDays(21));

    mvc.perform(get(flow()).param("zone", ZONE).cookie(login))
        .andExpect(status().isOk())
        .andExpect(jsonPath(tag("inReview")).value(1));
    mvc.perform(get(aging()).cookie(login))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inReview.cards.length()").value(1))
        .andExpect(jsonPath("$.inReview.cards[0].id").value(paket));
    assertThat(aufenthalte(plan)).isEmpty();
  }

  // --- Hilfen --------------------------------------------------------------------------------

  private String flow() {
    return "/api/boards/" + board.id() + "/dashboard/flow";
  }

  private String aging() {
    return "/api/boards/" + board.id() + "/dashboard/aging";
  }

  private static String tag(String band) {
    return "$.days[" + LETZTER_TAG + "]." + band;
  }

  private String fluss() throws Exception {
    return mvc.perform(get(flow()).param("zone", ZONE).cookie(login))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String alter() throws Exception {
    return mvc.perform(get(aging()).cookie(login))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void verschieben(long karte, String spalte) throws Exception {
    mvc.perform(
            post("/api/cards/" + karte + "/move")
                .cookie(login)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"position\":0}".formatted(spalte(spalte))))
        .andExpect(status().isOk());
  }

  private long anlegen(String spalte, String titel) {
    return cardService.create(owner, board.id(), spalte(spalte), titel, null, null, null).id();
  }

  private long spalte(String name) {
    return boards.getBoard(owner, board.id()).columns().stream()
        .filter(c -> name.equals(c.name()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Spalte fehlt: " + name))
        .id();
  }

  /** Board mit den Backlog-Spalten des Owners „Anstehend“ und „Parken“ direkt hinter Backlog. */
  private BoardView boardMitBacklogSpalten(BoardView neu) {
    long anstehend = boards.addColumn(owner, neu.id(), "Anstehend", null).id();
    long parken = boards.addColumn(owner, neu.id(), "Parken", null).id();
    List<Long> standard = neu.columns().stream().map(ColumnView::id).toList();
    boards.reorderColumns(
        owner,
        neu.id(),
        List.of(
            standard.get(0),
            anstehend,
            parken,
            standard.get(1),
            standard.get(2),
            standard.get(3),
            standard.get(4)));
    return boards.getBoard(owner, neu.id());
  }

  private List<Map<String, Object>> aufenthalte(long karte) {
    return jdbc.queryForList(
        "SELECT board_id, status, entered_at, left_at, entered_known, ended_by"
            + " FROM card_flow_stay WHERE card_id = ? ORDER BY id",
        karte);
  }

  /** Stellt die Uhr vor auf den nächsten Mittag in der Zone des Lesers. */
  private void mittag() {
    ZonedDateTime jetzt = uhr.instant().atZone(ZoneId.of(ZONE));
    ZonedDateTime mittag =
        jetzt.toLocalDate().plusDays(1).atTime(LocalTime.NOON).atZone(jetzt.getZone());
    uhr.advance(Duration.between(jetzt, mittag));
  }

  /** Lässt Zeit verstreichen; die Sitzung läuft nach sieben Tagen ab und wird darum erneuert. */
  private void weiter(Duration dauer) throws Exception {
    uhr.advance(dauer);
    login = anmeldung.anmelden(OWNER, PlatformRole.USER);
  }

  /** Die Erfassungsmarke; der Reset vor jeder Methode leert auch {@code card_flow_capture}. */
  private void marke(Instant since) {
    OffsetDateTime zeitpunkt = OffsetDateTime.ofInstant(since, ZoneOffset.UTC);
    jdbc.update("DELETE FROM card_flow_capture");
    jdbc.update(
        "INSERT INTO card_flow_capture (since, reconstructed_at) VALUES (?, ?)",
        zeitpunkt,
        zeitpunkt);
  }

  private long nutzer(String email, PlatformRole rolle) {
    return anmeldung.konto(email, rolle).requireId();
  }
}
