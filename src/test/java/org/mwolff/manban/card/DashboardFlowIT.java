package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die Endpunkte {@code /dashboard/flow}, {@code /dashboard/aging} und {@code
 * /dashboard/epic-forecast} über HTTP (Issue #1773, #1826, Plan #1765 E13, E14): Recht wie {@code
 * /dashboard} (AK 10), Abweisung einer Offset-Zone mit 400 und die Antwortform.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(StellbareUhrConfig.class)
class DashboardFlowIT extends AbstractIntegrationTest {

  private static final String OWNER = "flow-owner@example.com";
  private static final String FREMD = "flow-fremd@example.com";
  private static final String ZONE = "Europe/Berlin";

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ProjectService projects;
  @Autowired private BoardService boards;
  @Autowired private CardService cardService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock uhr;

  private long owner;
  private BoardView board;

  @BeforeEach
  void seed() {
    long admin = nutzer("flow-admin@example.com", PlatformRole.ADMIN);
    owner = nutzer(OWNER, PlatformRole.USER);
    nutzer(FREMD, PlatformRole.USER);
    long projectId = projects.create(admin, "Fluss", OWNER).id();
    board = boards.createBoard(owner, projectId, "B");
  }

  // --- Recht ---------------------------------------------------------------------------------

  @Test
  void einMitgliedLiestBeidePlatten() throws Exception {
    marke(uhr.instant().minus(Duration.ofDays(200)), true);
    Cookie mitglied = anmeldung.anmelden(OWNER, PlatformRole.USER);

    mvc.perform(get(flow()).param("zone", ZONE).cookie(mitglied)).andExpect(status().isOk());
    mvc.perform(get(aging()).cookie(mitglied)).andExpect(status().isOk());
  }

  @Test
  void einNichtmitgliedBekommt404() throws Exception {
    marke(uhr.instant().minus(Duration.ofDays(200)), true);
    Cookie fremd = anmeldung.anmelden(FREMD, PlatformRole.USER);

    mvc.perform(get(flow()).param("zone", ZONE).cookie(fremd)).andExpect(status().isNotFound());
    mvc.perform(get(aging()).cookie(fremd)).andExpect(status().isNotFound());
  }

  @Test
  void einArchiviertesBoardIst404() throws Exception {
    marke(uhr.instant().minus(Duration.ofDays(200)), true);
    boards.deleteBoard(owner, board.id());
    Cookie mitglied = anmeldung.anmelden(OWNER, PlatformRole.USER);

    mvc.perform(get(flow()).param("zone", ZONE).cookie(mitglied)).andExpect(status().isNotFound());
    mvc.perform(get(aging()).cookie(mitglied)).andExpect(status().isNotFound());
  }

  @Test
  void eineOffsetZoneIst400() throws Exception {
    marke(uhr.instant().minus(Duration.ofDays(200)), true);
    Cookie mitglied = anmeldung.anmelden(OWNER, PlatformRole.USER);

    mvc.perform(get(flow()).param("zone", "+05:30").cookie(mitglied))
        .andExpect(status().isBadRequest());
  }

  @Test
  void epicForecastLiestNurEinMitglied() throws Exception {
    Cookie mitglied = anmeldung.anmelden(OWNER, PlatformRole.USER);
    Cookie fremd = anmeldung.anmelden(FREMD, PlatformRole.USER);

    mvc.perform(get(epicForecast()).param("zone", ZONE).cookie(mitglied))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.throughputDays").value(0))
        .andExpect(jsonPath("$.epics").isEmpty());
    mvc.perform(get(epicForecast()).param("zone", ZONE).cookie(fremd))
        .andExpect(status().isNotFound());
  }

  @Test
  void epicForecastWeistEineFehlendeUnbekannteOderOffsetZoneAb() throws Exception {
    Cookie mitglied = anmeldung.anmelden(OWNER, PlatformRole.USER);

    mvc.perform(get(epicForecast()).cookie(mitglied)).andExpect(status().isBadRequest());
    mvc.perform(get(epicForecast()).param("zone", "Mars/Olymp").cookie(mitglied))
        .andExpect(status().isBadRequest());
    mvc.perform(get(epicForecast()).param("zone", "+05:30").cookie(mitglied))
        .andExpect(status().isBadRequest());
  }

  // --- Antwortform ---------------------------------------------------------------------------

  @Test
  void flowLiefert84TageUndDenDatenbeginn() throws Exception {
    Instant since = uhr.instant().minus(Duration.ofDays(10));
    marke(since, false);
    anlegen("Ready", "Paket");
    // Der Stand „jetzt“ zählt, was vor dem Stichzeitpunkt begann.
    uhr.advance(Duration.ofMinutes(1));
    Cookie mitglied = anmeldung.anmelden(OWNER, PlatformRole.USER);
    LocalDate heute = LocalDate.ofInstant(uhr.instant(), ZoneId.of(ZONE));

    mvc.perform(get(flow()).param("zone", ZONE).cookie(mitglied))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days.length()").value(84))
        .andExpect(
            jsonPath("$.dataSince").value(LocalDate.ofInstant(since, ZoneId.of(ZONE)).toString()))
        .andExpect(jsonPath("$.days[0].known").value(false))
        .andExpect(jsonPath("$.days[0].backlog").isEmpty())
        .andExpect(jsonPath("$.days[83].date").value(heute.toString()))
        .andExpect(jsonPath("$.days[83].known").value(true))
        .andExpect(jsonPath("$.days[83].backlog").value(0))
        .andExpect(jsonPath("$.days[83].ready").value(1))
        .andExpect(jsonPath("$.days[83].inProgress").value(0))
        .andExpect(jsonPath("$.days[83].inReview").value(0))
        .andExpect(jsonPath("$.days[83].done").value(0));
  }

  @Test
  void agingLiefertDreiZustaendeMitDenKarten() throws Exception {
    marke(uhr.instant().minus(Duration.ofDays(200)), true);
    long karte = anlegen("In Progress", "Paket in Arbeit");
    int nummer = nummer(karte);
    uhr.advance(Duration.ofHours(2));
    Cookie mitglied = anmeldung.anmelden(OWNER, PlatformRole.USER);

    mvc.perform(get(aging()).cookie(mitglied))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready.comparisonCount").value(0))
        .andExpect(jsonPath("$.ready.thresholdSeconds").isEmpty())
        .andExpect(jsonPath("$.ready.cards").isEmpty())
        .andExpect(jsonPath("$.inProgress.cards.length()").value(1))
        .andExpect(jsonPath("$.inProgress.cards[0].id").value(karte))
        .andExpect(jsonPath("$.inProgress.cards[0].number").value(nummer))
        .andExpect(jsonPath("$.inProgress.cards[0].title").value("Paket in Arbeit"))
        .andExpect(jsonPath("$.inProgress.cards[0].ageSeconds").value(7200))
        .andExpect(jsonPath("$.inProgress.cards[0].ageIsMinimum").value(false))
        .andExpect(jsonPath("$.inProgress.cards[0].overThreshold").value(false))
        .andExpect(jsonPath("$.inReview.cards").isEmpty());
  }

  // --- Hilfen --------------------------------------------------------------------------------

  private String flow() {
    return "/api/boards/" + board.id() + "/dashboard/flow";
  }

  private String epicForecast() {
    return "/api/boards/" + board.id() + "/dashboard/epic-forecast";
  }

  private String aging() {
    return "/api/boards/" + board.id() + "/dashboard/aging";
  }

  private long nutzer(String email, PlatformRole rolle) {
    return anmeldung.konto(email, rolle).requireId();
  }

  /** Die Erfassungsmarke; der Reset vor jeder Methode leert auch {@code card_flow_capture}. */
  private void marke(Instant since, boolean nachgetragen) {
    jdbc.update("DELETE FROM card_flow_capture");
    jdbc.update(
        "INSERT INTO card_flow_capture (since, reconstructed_at) VALUES (?, ?)",
        OffsetDateTime.ofInstant(since, ZoneOffset.UTC),
        nachgetragen ? OffsetDateTime.ofInstant(since, ZoneOffset.UTC) : null);
  }

  private long anlegen(String spalte, String titel) {
    long spaltenId =
        board.columns().stream()
            .filter(c -> spalte.equals(c.name()))
            .findFirst()
            .orElseThrow()
            .id();
    return cardService.create(owner, board.id(), spaltenId, titel, null, null, null).id();
  }

  private int nummer(long karte) {
    Integer nummer =
        jdbc.queryForObject("SELECT number FROM card WHERE id = ?", Integer.class, karte);
    return nummer == null ? 0 : nummer;
  }
}
