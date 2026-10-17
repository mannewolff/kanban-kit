package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.card.application.EpicService;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die fünf Szenarien der fachlichen Quelle #1782 gegen die Datenbank (Issue #1827, Plan #1819 A1,
 * A2, A5, A7, E8, E12): Pakete entstehen und wandern über die Schreibwege des Boards, gelesen wird
 * über {@code /dashboard/epic-forecast}.
 *
 * <p>Jeder Fall stellt die Uhr zuerst auf den nächsten Mittag in der Zone des Lesers. Der Durchsatz
 * entsteht, indem je Tag ein Paket eine Stunde nach Mittag In Review erreicht — so liegt jedes
 * Erreichen in einem eigenen Tag von 12 bis 12 Uhr, und der jüngste im letzten abgeschlossenen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(StellbareUhrConfig.class)
class VorhabenPrognoseSzenarienIT extends AbstractIntegrationTest {

  private static final String OWNER = "prognose-owner@example.com";
  private static final String ZONE = "Europe/Berlin";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ProjectService projects;
  @Autowired private BoardService boards;
  @Autowired private CardService cardService;
  @Autowired private EpicService epicService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock uhr;

  private long owner;
  private BoardView board;
  private Cookie login;

  @BeforeEach
  void seed() throws Exception {
    long admin = nutzer("prognose-admin@example.com", PlatformRole.ADMIN);
    owner = nutzer(OWNER, PlatformRole.USER);
    long projectId = projects.create(admin, "Prognose", OWNER).id();
    board = boards.createBoard(owner, projectId, "B");
    login = anmeldung.anmelden(OWNER, PlatformRole.USER);
    mittag();
    // Erfasst seit langem: Jeder Eintritt der Pakete ist bekannt.
    marke(Duration.ofDays(200));
  }

  @Test
  @DisplayName("Szenario: Prognose für ein laufendes Vorhaben")
  void prognoseFuerEinLaufendesVorhaben() throws Exception {
    durchsatzAn(20);
    CardView vorhaben = vorhaben("Leitstand");
    pakete(vorhaben, 12);

    JsonNode prognose = prognose();
    JsonNode epic = epic(prognose, vorhaben);

    assertThat(prognose.get("throughputDays").asInt()).isEqualTo(20);
    assertThat(epic.get("state").asText()).isEqualTo("FORECAST");
    assertThat(epic.get("open").asInt()).isEqualTo(12);
    assertThat(epic.get("finished").asInt()).isZero();
    LocalDate p50 = datum(epic, "p50");
    LocalDate p85 = datum(epic, "p85");
    assertThat(p50).isNotNull();
    assertThat(p85).isNotNull().isAfterOrEqualTo(p50);
    assertThat(epic.get("earliest").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("Szenario: Umgesetzte Pakete zählen beim nächsten Laden")
  void umgesetztePaketeZaehlenBeimNaechstenLaden() throws Exception {
    durchsatzAn(20);
    CardView vorhaben = vorhaben("Leitstand");
    List<Long> pakete = pakete(vorhaben, 12);
    JsonNode vorher = epic(prognose(), vorhaben);
    assertThat(vorher.get("open").asInt()).isEqualTo(12);

    for (long paket : pakete.subList(0, 3)) {
      verschieben(paket, "In Review");
    }
    weiter(Duration.ofMinutes(5));

    JsonNode nachher = epic(prognose(), vorhaben);
    assertThat(nachher.get("state").asText()).isEqualTo("FORECAST");
    assertThat(nachher.get("open").asInt()).isEqualTo(9);
    assertThat(nachher.get("finished").asInt()).isEqualTo(3);
    assertThat(datum(nachher, "p50")).isBeforeOrEqualTo(datum(vorher, "p50"));
    assertThat(datum(nachher, "p85")).isBeforeOrEqualTo(datum(vorher, "p85"));
  }

  @Test
  @DisplayName("Szenario: Zu wenig Durchsatz")
  void zuWenigDurchsatz() throws Exception {
    durchsatzAn(3);
    CardView erstes = vorhaben("Erstes");
    pakete(erstes, 4);
    CardView zweites = vorhaben("Zweites");
    pakete(zweites, 1);

    JsonNode prognose = prognose();

    assertThat(prognose.get("throughputDays").asInt()).isEqualTo(3);
    for (CardView v : List.of(erstes, zweites)) {
      JsonNode epic = epic(prognose, v);
      assertThat(epic.get("state").asText()).isEqualTo("INSUFFICIENT_THROUGHPUT");
      assertThat(epic.get("open").asInt()).isPositive();
      assertThat(epic.get("p50").isNull()).isTrue();
      assertThat(epic.get("p85").isNull()).isTrue();
    }
  }

  @Test
  @DisplayName("Szenario: Vorhaben wächst noch")
  void vorhabenWaechstNoch() throws Exception {
    durchsatzAn(20);
    CardView vorhaben = vorhaben("Leitstand");
    // Ein geschnittener Plan mit seinen fünf Paketen und ein Plan ohne Pakete.
    CardView geschnitten = karte("Backlog", "[Plan] Erster Schnitt", vorhaben.id(), null);
    for (int i = 1; i <= 5; i++) {
      karte("Backlog", "Paket " + i, null, geschnitten.number());
    }
    CardView offen = karte("Backlog", "[Plan] Zweiter Schnitt", vorhaben.id(), null);

    JsonNode epic = epic(prognose(), vorhaben);

    assertThat(epic.get("state").asText()).isEqualTo("FORECAST");
    assertThat(epic.get("open").asInt()).isEqualTo(5);
    assertThat(epic.get("earliest").asBoolean()).isTrue();
    assertThat(datum(epic, "p50")).isNotNull();
    JsonNode dokumente = epic.get("pendingDocuments");
    assertThat(dokumente).hasSize(1);
    assertThat(dokumente.get(0).get("number").asInt()).isEqualTo(offen.number());
    assertThat(dokumente.get(0).get("title").asText()).isEqualTo("[Plan] Zweiter Schnitt");
  }

  @Test
  @DisplayName("Szenario: Mehrere Vorhaben gleichzeitig")
  void mehrereVorhabenGleichzeitig() throws Exception {
    durchsatzAn(20);
    List<CardView> laufende = new ArrayList<>();
    for (int i = 1; i <= 3; i++) {
      CardView v = vorhaben("Vorhaben " + i);
      pakete(v, i * 2);
      laufende.add(v);
    }
    // Ein Vorhaben ohne offenes Paket teilt sich den Durchsatz nicht (E12).
    CardView umgesetzt = vorhaben("Umgesetzt");
    karte("In Review", "Fertig", umgesetzt.id(), null);

    JsonNode prognose = prognose();

    for (CardView v : laufende) {
      JsonNode epic = epic(prognose, v);
      assertThat(epic.get("state").asText()).isEqualTo("FORECAST");
      assertThat(epic.get("otherEpicsSharing").asInt()).isEqualTo(2);
    }
    assertThat(epic(prognose, umgesetzt).get("state").asText()).isEqualTo("ALL_DONE");
  }

  // --- Hilfen --------------------------------------------------------------------------------

  /**
   * Lässt an {@code tage} aufeinanderfolgenden Tagen je ein Paket ohne Vorhaben In Review erreichen
   * (AK 14) und stellt die Uhr danach in den folgenden Tag; das jüngste Erreichen liegt damit im
   * letzten abgeschlossenen Tag.
   */
  private void durchsatzAn(int tage) throws Exception {
    weiter(Duration.ofHours(1));
    List<Long> pakete = new ArrayList<>();
    for (int i = 0; i < tage; i++) {
      pakete.add(karte("Backlog", "Durchsatz " + i, null, null).id());
    }
    for (long paket : pakete) {
      weiter(Duration.ofDays(1));
      verschieben(paket, "In Review");
    }
    weiter(Duration.ofDays(1));
  }

  private JsonNode prognose() throws Exception {
    String antwort =
        mvc.perform(
                get("/api/boards/" + board.id() + "/dashboard/epic-forecast")
                    .param("zone", ZONE)
                    .cookie(login))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(antwort);
  }

  private static JsonNode epic(JsonNode prognose, CardView vorhaben) {
    for (JsonNode epic : prognose.get("epics")) {
      if (epic.get("id").asLong() == vorhaben.id()) {
        return epic;
      }
    }
    throw new AssertionError("Vorhaben fehlt in der Prognose: " + vorhaben.title());
  }

  private static @Nullable LocalDate datum(JsonNode epic, String feld) {
    JsonNode wert = epic.get(feld);
    return wert.isNull() ? null : LocalDate.parse(wert.asText());
  }

  private CardView vorhaben(String titel) {
    return epicService.createEpic(owner, board.id(), titel, null, null);
  }

  private List<Long> pakete(CardView vorhaben, int anzahl) {
    List<Long> ids = new ArrayList<>();
    for (int i = 1; i <= anzahl; i++) {
      ids.add(karte("Backlog", vorhaben.title() + " Paket " + i, vorhaben.id(), null).id());
    }
    return ids;
  }

  private CardView karte(
      String spalte, String titel, @Nullable Long vorhaben, @Nullable Integer herkunft) {
    return cardService.create(
        owner, board.id(), spalte(spalte), titel, null, null, vorhaben, null, null, null, herkunft);
  }

  private void verschieben(long karte, String spalte) throws Exception {
    mvc.perform(
            post("/api/cards/" + karte + "/move")
                .cookie(login)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"position\":0}".formatted(spalte(spalte))))
        .andExpect(status().isOk());
  }

  private long spalte(String name) {
    return boards.getBoard(owner, board.id()).columns().stream()
        .filter(c -> name.equals(c.name()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Spalte fehlt: " + name))
        .id();
  }

  /** Stellt die Uhr vor auf den nächsten Mittag in der Zone des Lesers. */
  private void mittag() {
    ZonedDateTime jetzt = uhr.instant().atZone(ZoneId.of(ZONE));
    ZonedDateTime mittag = jetzt.toLocalDate().plusDays(1).atTime(12, 0).atZone(jetzt.getZone());
    uhr.advance(Duration.between(jetzt, mittag));
  }

  /** Lässt Zeit verstreichen; die Sitzung läuft nach sieben Tagen ab und wird darum erneuert. */
  private void weiter(Duration dauer) throws Exception {
    uhr.advance(dauer);
    login = anmeldung.anmelden(OWNER, PlatformRole.USER);
  }

  /** Die Erfassungsmarke; der Reset vor jeder Methode leert auch {@code card_flow_capture}. */
  private void marke(Duration vorlauf) {
    jdbc.update("DELETE FROM card_flow_capture");
    jdbc.update(
        "INSERT INTO card_flow_capture (since, reconstructed_at) VALUES (?, ?)",
        OffsetDateTime.ofInstant(uhr.instant().minus(vorlauf), ZoneId.of("UTC")),
        OffsetDateTime.ofInstant(uhr.instant().minus(vorlauf), ZoneId.of("UTC")));
  }

  private long nutzer(String email, PlatformRole rolle) {
    return anmeldung.konto(email, rolle).requireId();
  }
}
