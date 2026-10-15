package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.assertj.core.groups.Tuple;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardFlowStayRepository;
import org.mwolff.manban.card.application.CardFlowStayRepository.ErfassungsMarke;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.FlussStay;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;
import org.mwolff.manban.card.domain.Flusslage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Die Tabelle der Zustandsaufenthalte gegen die echte Datenbank (Issue #1767, Plan #1765 E1, E3,
 * E5, E20).
 *
 * <p>Die Saat von {@code V56__card_flow_stay.sql} prüft ein eigener Container wie in {@link
 * CardColumnTransitionBackfillIT}: bis {@code V55} migrieren, Bestand anlegen, dann {@code V56}.
 * Der Reset aus {@link AbstractIntegrationTest} leert vor jeder Methode alle Tabellen — was die
 * Migration sät, wäre dort nicht mehr zu sehen. Die Port-Methoden, Check-Bedingungen und die
 * Kaskade laufen gegen den Container der Testbasis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardFlowStayRepositoryAdapterIT extends AbstractIntegrationTest {

  private static final PostgreSQLContainer<?> SAAT = new PostgreSQLContainer<>("postgres:16.15");

  private static final Instant FERTIG = Instant.parse("2026-09-30T10:00:00Z");
  private static final Instant T1 = Instant.parse("2026-10-01T08:00:00Z");
  private static final Instant T2 = Instant.parse("2026-10-02T08:00:00Z");
  private static final Instant T3 = Instant.parse("2026-10-03T08:00:00Z");

  private static JdbcTemplate saat;
  private static Map<String, Long> saatKarten = Map.of();
  private static Map<String, Object> marke = Map.of();

  static {
    SAAT.start();
  }

  @Autowired private CardFlowStayRepository stays;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate tx;
  @Autowired private DataSource dataSource;

  private long boardId;
  private long zweitesBoardId;
  private long columnId;
  private int naechsteNummer = 1;

  @BeforeAll
  static void saeeBestandUndMigriereV56() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(SAAT.getJdbcUrl(), SAAT.getUsername(), SAAT.getPassword());
    saat = new JdbcTemplate(dataSource);
    migriere(dataSource, "55");
    long board = boardMitSpalte(saat, "saat@example.com");
    long spalte = id(saat, "SELECT id FROM board_column WHERE board_id = ?", board);
    saatKarten =
        Map.of(
            "ready", saatKarte(spalte, board, 1, "[Task] Ready", "READY", false, null, null),
            "fertig", saatKarte(spalte, board, 2, "[Task] Fertig", "DONE", false, FERTIG, null),
            "fertigArchiv",
                saatKarte(spalte, board, 3, "[Task] Fertig alt", "DONE", true, FERTIG, null),
            "fertigOhneStempel",
                saatKarte(spalte, board, 4, "[Task] Fertig ohne", "DONE", false, null, null),
            "dokument", saatKarte(spalte, board, 5, "[Plan] Weg", null, false, null, null),
            "papierkorb", saatKarte(spalte, board, 6, "[Task] Weg", "IN_REVIEW", false, null, T1),
            "papierkorbFertig",
                saatKarte(spalte, board, 7, "[Task] Weg fertig", "DONE", false, FERTIG, T1),
            "archiv", saatKarte(spalte, board, 8, "[Task] Archiv", "READY", true, null, null));
    migriere(dataSource, "56");
    marke = saat.queryForMap("SELECT since, reconstructed_at FROM card_flow_capture");
  }

  private static void migriere(DriverManagerDataSource dataSource, String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(version)
        .load()
        .migrate();
  }

  private static long saatKarte(
      long spalte,
      long board,
      int nummer,
      String titel,
      @Nullable String status,
      boolean archived,
      @Nullable Instant fertig,
      @Nullable Instant geloescht) {
    return id(
        saat,
        "INSERT INTO card (board_id, column_id, number, title, position_in_column, status,"
            + " archived, moved_to_done_at, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " RETURNING id",
        board,
        spalte,
        nummer,
        titel,
        nummer,
        status,
        archived,
        zeit(fertig),
        zeit(geloescht));
  }

  private static @Nullable OffsetDateTime zeit(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }

  private static long boardMitSpalte(JdbcTemplate jdbc, String email) {
    long userId =
        id(
            jdbc,
            "INSERT INTO app_user (email, password_hash, display_name) VALUES (?, 'x', 'F')"
                + " RETURNING id",
            email);
    long projectId =
        id(jdbc, "INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", userId);
    long board =
        id(jdbc, "INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projectId);
    id(
        jdbc,
        "INSERT INTO board_column (board_id, name, position) VALUES (?, 'Ready', 0) RETURNING id",
        board);
    return board;
  }

  private static long id(JdbcTemplate jdbc, String sql, Object... args) {
    Long wert = jdbc.queryForObject(sql, Long.class, args);
    return wert == null ? 0L : wert;
  }

  private static List<Map<String, Object>> saatVon(String karte) {
    return saat.queryForList(
        "SELECT board_id, status, entered_at, left_at, entered_known, ended_by"
            + " FROM card_flow_stay WHERE card_id = ?",
        saatKarten.get(karte));
  }

  // --- Saat von V56 -------------------------------------------------------------------------

  @Test
  void dieMarkeStehtInGenauEinerZeileMitBeginnUndOhneNachtrag() {
    assertThat(saat.queryForObject("SELECT count(*) FROM card_flow_capture", Integer.class))
        .isEqualTo(1);
    assertThat(marke.get("since")).isNotNull();
    assertThat(marke.get("reconstructed_at")).isNull();
  }

  @Test
  void eineKarteImFlussBekommtGenauEinenOffenenAufenthaltAbMigrationMitUnbekanntemEintritt() {
    List<Map<String, Object>> zeilen = saatVon("ready");

    assertThat(zeilen).hasSize(1);
    Map<String, Object> zeile = zeilen.getFirst();
    assertThat(zeile.get("status")).isEqualTo("READY");
    assertThat(zeile.get("entered_at")).isEqualTo(marke.get("since"));
    assertThat(zeile.get("entered_known")).isEqualTo(false);
    assertThat(zeile.get("left_at")).isNull();
    assertThat(zeile.get("ended_by")).isNull();
    assertThat(zeile.get("board_id"))
        .isEqualTo(
            saat.queryForObject(
                "SELECT board_id FROM card WHERE id = ?", Long.class, saatKarten.get("ready")));
  }

  @Test
  void eineFertigeKarteMitDoneStempelBeginntAmStempelMitBekanntemEintrittAuchArchiviert() {
    for (String karte : List.of("fertig", "fertigArchiv")) {
      List<Map<String, Object>> zeilen = saatVon(karte);

      assertThat(zeilen).as(karte).hasSize(1);
      assertThat(zeilen.getFirst().get("status")).isEqualTo("DONE");
      assertThat(((Timestamp) zeilen.getFirst().get("entered_at")).toInstant()).isEqualTo(FERTIG);
      assertThat(zeilen.getFirst().get("entered_known")).isEqualTo(true);
      assertThat(zeilen.getFirst().get("left_at")).isNull();
    }
  }

  @Test
  void eineFertigeKarteOhneDoneStempelBeginntAmMigrationszeitpunktUnbekannt() {
    List<Map<String, Object>> zeilen = saatVon("fertigOhneStempel");

    assertThat(zeilen).hasSize(1);
    assertThat(zeilen.getFirst().get("entered_at")).isEqualTo(marke.get("since"));
    assertThat(zeilen.getFirst().get("entered_known")).isEqualTo(false);
  }

  @Test
  void dokumentePapierkorbUndArchivierteNichtFertigeKartenBekommenKeinenAufenthalt() {
    for (String karte : List.of("dokument", "papierkorb", "papierkorbFertig", "archiv")) {
      assertThat(saatVon(karte)).as(karte).isEmpty();
    }
  }

  // --- Port gegen den Container der Testbasis -----------------------------------------------

  @BeforeEach
  void seed() {
    boardId = boardMitSpalte(jdbc, "fluss@example.com");
    zweitesBoardId =
        id(
            jdbc,
            "INSERT INTO board (project_id, name) SELECT project_id, 'B2' FROM board WHERE id = ?"
                + " RETURNING id",
            boardId);
    columnId = id(jdbc, "SELECT id FROM board_column WHERE board_id = ?", boardId);
    jdbc.update("INSERT INTO card_flow_capture (since) VALUES (?)", zeit(T1));
  }

  private long karte() {
    int nummer = naechsteNummer;
    naechsteNummer++;
    return id(
        jdbc,
        "INSERT INTO card (board_id, column_id, number, title, position_in_column, status)"
            + " VALUES (?, ?, ?, '[Task] Paket', ?, 'READY') RETURNING id",
        boardId,
        columnId,
        nummer,
        nummer);
  }

  @Test
  void openLegtEinenOffenenAufenthaltMitBekanntemEintrittAnUndOpenForLiefertIhn() {
    long karte = karte();

    stays.open(karte, new Flusslage(boardId, CardStatus.READY), T1);

    assertThat(stays.openFor(karte))
        .hasValueSatisfying(
            stay -> {
              assertThat(stay.id()).isNotNull();
              assertThat(stay)
                  .usingRecursiveComparison()
                  .ignoringFields("id")
                  .isEqualTo(
                      new FlussStay(null, karte, boardId, CardStatus.READY, T1, null, true, null));
            });
  }

  @Test
  void openForIstLeerOhneOffenenAufenthalt() {
    long karte = karte();
    stays.open(karte, new Flusslage(boardId, CardStatus.READY), T1);
    stays.close(karte, T2, EndedBy.EXIT);

    assertThat(stays.openFor(karte)).isEmpty();
    assertThat(stays.openFor(karte + 1000)).isEmpty();
  }

  @Test
  void closeSchliesstNurDenOffenenAufenthaltMitZeitpunktUndGrund() {
    long karte = karte();
    stays.open(karte, new Flusslage(boardId, CardStatus.READY), T1);
    stays.close(karte, T2, EndedBy.STATUS);
    stays.open(karte, new Flusslage(boardId, CardStatus.IN_PROGRESS), T2);
    stays.close(karte, T3, EndedBy.BOARD);

    assertThat(stays.findByBoardId(boardId))
        .extracting(FlussStay::status, FlussStay::leftAt, FlussStay::endedBy)
        .containsExactly(
            Tuple.tuple(CardStatus.READY, T2, EndedBy.STATUS),
            Tuple.tuple(CardStatus.IN_PROGRESS, T3, EndedBy.BOARD));
  }

  @Test
  void closeOhneOffenenAufenthaltAendertNichts() {
    long karte = karte();
    stays.open(karte, new Flusslage(boardId, CardStatus.READY), T1);
    stays.close(karte, T2, EndedBy.STATUS);

    stays.close(karte, T3, EndedBy.EXIT);

    assertThat(stays.findByBoardId(boardId))
        .singleElement()
        .satisfies(
            stay -> {
              assertThat(stay.leftAt()).isEqualTo(T2);
              assertThat(stay.endedBy()).isEqualTo(EndedBy.STATUS);
            });
  }

  @Test
  void findByBoardIdLiefertNurDieAufenthalteDesBoardsNachEintritt() {
    long a = karte();
    long b = karte();
    stays.open(b, new Flusslage(boardId, CardStatus.READY), T2);
    stays.open(a, new Flusslage(boardId, CardStatus.DONE), T1);
    stays.open(karte(), new Flusslage(zweitesBoardId, CardStatus.READY), T1);

    assertThat(stays.findByBoardId(boardId))
        .extracting(FlussStay::cardId, FlussStay::boardId, FlussStay::enteredAt)
        .containsExactly(Tuple.tuple(a, boardId, T1), Tuple.tuple(b, boardId, T2));
    assertThat(stays.findByBoardId(zweitesBoardId)).hasSize(1);
  }

  @Test
  void nachtragenSchreibtAufenthalteMitUnbekanntemZustandUndUnbekanntemEintritt() {
    long karte = karte();

    stays.nachtragen(
        List.of(
            new FlussStay(null, karte, boardId, null, T1, T2, false, EndedBy.STATUS),
            new FlussStay(null, karte, boardId, CardStatus.READY, T2, T3, true, EndedBy.EXIT)));

    assertThat(stays.findByBoardId(boardId))
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .containsExactly(
            new FlussStay(null, karte, boardId, null, T1, T2, false, EndedBy.STATUS),
            new FlussStay(null, karte, boardId, CardStatus.READY, T2, T3, true, EndedBy.EXIT));
  }

  @Test
  void nachtragenOhneAufenthalteSchreibtNichts() {
    stays.nachtragen(List.of());

    assertThat(stays.findByBoardId(boardId)).isEmpty();
  }

  @Test
  void dieMarkeLiefertBeginnUndOffenenNachtragUndMarkeSetzenSchliesstIhn() {
    assertThat(stays.marke()).isEqualTo(new ErfassungsMarke(T1, null));

    stays.markeSetzen(T3);

    assertThat(stays.marke()).isEqualTo(new ErfassungsMarke(T1, T3));
  }

  @Test
  void markeSperrenLiefertDieMarkeUndHaeltDieZeileBisZumEndeDerTransaktion() {
    tx.executeWithoutResult(
        status -> {
          assertThat(stays.markeSperren()).isEqualTo(new ErfassungsMarke(T1, null));
          assertThatThrownBy(this::sperreAusZweiterVerbindung)
              .isInstanceOf(SQLException.class)
              .hasMessageContaining("lock");
        });
  }

  private void sperreAusZweiterVerbindung() throws SQLException {
    try (Connection zweite = dataSource.getConnection();
        Statement statement = zweite.createStatement()) {
      statement.execute("SELECT since FROM card_flow_capture FOR UPDATE NOWAIT");
    }
  }

  @Test
  void dieCheckBedingungenWeisenUnbekannteZustaendeUndGruendeAb() {
    long karte = karte();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO card_flow_stay (card_id, board_id, status, entered_at,"
                        + " entered_known) VALUES (?, ?, 'PARKEN', ?, true)",
                    karte,
                    boardId,
                    zeit(T1)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_card_flow_stay_status");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO card_flow_stay (card_id, board_id, status, entered_at, left_at,"
                        + " entered_known, ended_by) VALUES (?, ?, 'READY', ?, ?, true, 'MOVE')",
                    karte,
                    boardId,
                    zeit(T1),
                    zeit(T2)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_card_flow_stay_ended_by");
  }

  @Test
  void eineEndgueltigGeloeschteKarteNimmtIhreAufenthalteMit() {
    long karte = karte();
    long bleibt = karte();
    stays.open(karte, new Flusslage(boardId, CardStatus.READY), T1);
    stays.open(bleibt, new Flusslage(boardId, CardStatus.READY), T1);

    jdbc.update("DELETE FROM card WHERE id = ?", karte);

    assertThat(stays.findByBoardId(boardId)).extracting(FlussStay::cardId).containsExactly(bleibt);
  }
}
