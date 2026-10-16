package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.FlussNachtrag;
import org.mwolff.manban.card.application.FlussNachtragQuelle;
import org.mwolff.manban.card.application.FlussNachtragQuelle.Bestand;
import org.mwolff.manban.card.application.FlussNachtragQuelle.Kartenverlauf;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Kartenstand;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenort;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenzeile;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Der einmalige Nachtrag der Zustandsaufenthalte aus dem Spaltenverlauf (Issue #1770, Plan #1765
 * E6, E7, E8, E9, E20) gegen vorbereiteten Bestand.
 *
 * <p>Der Bestand entsteht wie vor {@code V56}: Karten über den Dienst, danach Spaltenverlauf,
 * Archiv-Ereignisse und Kartenstand per SQL — am Erfassungsweg vorbei —, zuletzt die Saat mit
 * derselben Anweisung wie in {@code V56__card_flow_stay.sql}. Der Reset der Testbasis leert vor
 * jeder Methode auch {@code card_flow_capture}; die Saat legt die Marke darum selbst wieder an.
 */
// PMD.ExcessiveImports, PMD.CouplingBetweenObjects: Der Test baut den Bestand über die Dienste
// (Board, Projekt, Karte), prüft Runner und Dienst und reicht für den Rückroll-Fall einen Bestand
// aus den Eingabe-Records der Rekonstruktion durch — die Kopplung zählt diese Typen.
@SuppressWarnings({"PMD.ExcessiveImports", "PMD.CouplingBetweenObjects"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(StellbareUhrConfig.class)
class FlussNachtragIT extends AbstractIntegrationTest {

  private static final String OWNER = "nachtrag-owner@example.com";

  /** Migrationszeitpunkt: ab hier erfasst die Erfassung (V56). */
  private static final Instant SINCE = Instant.parse("2026-06-01T00:00:00Z");

  private static final Instant T0 = Instant.parse("2026-03-01T08:00:00Z");
  private static final Instant T1 = Instant.parse("2026-03-02T08:00:00Z");
  private static final Instant T2 = Instant.parse("2026-03-03T08:00:00Z");

  /** Ein Zeitpunkt nach der Migration, vor dem Nachtrag. */
  private static final Instant DANACH = Instant.parse("2026-06-02T08:00:00Z");

  /** Die Saat aus {@code V56__card_flow_stay.sql}, wörtlich. */
  private static final String SAAT =
      "INSERT INTO card_flow_stay (card_id, board_id, status, entered_at, entered_known)"
          + " SELECT c.id, c.board_id, c.status,"
          + " CASE WHEN c.status = 'DONE' AND c.moved_to_done_at IS NOT NULL"
          + " THEN c.moved_to_done_at ELSE f.since END,"
          + " c.status = 'DONE' AND c.moved_to_done_at IS NOT NULL"
          + " FROM card c CROSS JOIN card_flow_capture f"
          + " WHERE c.status IS NOT NULL AND c.deleted_at IS NULL"
          + " AND (NOT c.archived OR c.status = 'DONE')";

  @Autowired private AppUserRepository users;
  @Autowired private ProjectService projects;
  @Autowired private BoardService boards;
  @Autowired private CardService cardService;
  @Autowired private FlussNachtrag nachtrag;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
  @Autowired private MutableClock uhr;
  @MockitoSpyBean private FlussNachtragQuelle quelle;

  @Autowired
  @Qualifier("flussNachtragRunner")
  private ApplicationRunner runner;

  private long owner;
  private long projectId;
  private BoardView board;
  private BoardView zweitesBoard;
  private int naechstePosition = 100;

  /** Ein Aufenthalt, wie er in {@code card_flow_stay} steht. */
  private record Aufenthalt(
      long boardId,
      @Nullable String status,
      Instant enteredAt,
      @Nullable Instant leftAt,
      boolean enteredKnown,
      @Nullable String endedBy) {}

  @BeforeEach
  void bestandAnlegen() {
    long admin =
        users
            .save(new AppUser(null, "nachtrag-admin@example.com", "A", true, PlatformRole.ADMIN))
            .id();
    owner = users.save(new AppUser(null, OWNER, "O", true, PlatformRole.USER)).id();
    projectId = projects.create(admin, "Nachtrag", OWNER).id();
    board = boardMitPruefung("B");
    zweitesBoard = boards.createBoard(owner, projectId, "Zweites");
  }

  // --- Rekonstruktion vor der Saat ---------------------------------------------------------------

  @Test
  void derNachtragSetztDenVerlaufVorDieSaatUndKorrigiertIhrenEintritt() {
    long bekannt = karte(board, "In Progress", "IN_PROGRESS", T0);
    spaltenzeile(bekannt, board, "Backlog", T0, T1);
    spaltenzeile(bekannt, board, "Ready", T1, T2);
    spaltenzeile(bekannt, board, "In Progress", T2, null);
    long alt = karte(board, "Ready", "READY", T0);
    spaltenzeile(alt, board, "Ready", T1, null);
    long zusatz = karte(board, "Prüfung", "IN_PROGRESS", T0);
    spaltenzeile(zusatz, board, "Backlog", T0, T1);
    spaltenzeile(zusatz, board, "Prüfung", T1, null);
    saeen();

    nachtragen();

    assertThat(aufenthalte(bekannt))
        .containsExactly(
            new Aufenthalt(board.id(), "BACKLOG", T0, T1, true, "STATUS"),
            new Aufenthalt(board.id(), "READY", T1, T2, true, "STATUS"),
            new Aufenthalt(board.id(), "IN_PROGRESS", T2, null, true, null));
    // Anlage vor V9: Die Zeit vor der ersten Spaltenzeile ist unbekannt, der Eintritt auch (E7,
    // E8).
    assertThat(aufenthalte(alt))
        .containsExactly(
            new Aufenthalt(board.id(), null, T0, T1, false, "STATUS"),
            new Aufenthalt(board.id(), "READY", T1, null, false, null));
    // Eine Zusatzspalte zählt nach der nächsten Standardspalte links von ihr.
    assertThat(aufenthalte(zusatz))
        .containsExactly(
            new Aufenthalt(board.id(), "BACKLOG", T0, T1, true, "STATUS"),
            new Aufenthalt(board.id(), "IN_PROGRESS", T1, null, true, null));
    assertThat(marke()).isEqualTo(uhr.instant());
  }

  @Test
  void dasBoardKommtAusDerSpalteNichtAusDemHeutigenBoardDerKarte() {
    long gewechselt = karte(board, "In Review", "IN_REVIEW", T0);
    spaltenzeile(gewechselt, zweitesBoard, "Ready", T0, T1);
    spaltenzeile(gewechselt, board, "In Review", T1, null);
    long geloeschteSpalte = karte(board, "In Progress", "IN_PROGRESS", T0);
    geloeschteSpaltenzeile(geloeschteSpalte, "Alt", T0, T1);
    spaltenzeile(geloeschteSpalte, board, "In Progress", T1, null);
    saeen();

    nachtragen();

    assertThat(aufenthalte(gewechselt))
        .containsExactly(
            new Aufenthalt(zweitesBoard.id(), "READY", T0, T1, true, "BOARD"),
            new Aufenthalt(board.id(), "IN_REVIEW", T1, null, true, null));
    // Gelöschte Spalte: Zustand unbekannt, Board des Nachbarn (E7 b).
    assertThat(aufenthalte(geloeschteSpalte))
        .containsExactly(
            new Aufenthalt(board.id(), null, T0, T1, false, "STATUS"),
            new Aufenthalt(board.id(), "IN_PROGRESS", T1, null, false, null));
  }

  @Test
  void einVorDemNachtragGeschlossenerSaatAufenthaltBekommtDenRekonstruiertenEintritt() {
    long karte = karte(board, "Ready", "READY", T0);
    spaltenzeile(karte, board, "Backlog", T0, T1);
    spaltenzeile(karte, board, "Ready", T1, null);
    saeen();
    // Zwischen Migration und Nachtrag erfasst: Die Saat endet, ein neuer Aufenthalt beginnt.
    jdbc.update(
        "UPDATE card_flow_stay SET left_at = ?, ended_by = 'STATUS' WHERE card_id = ?",
        zeit(DANACH),
        karte);
    jdbc.update(
        "INSERT INTO card_flow_stay (card_id, board_id, status, entered_at, entered_known)"
            + " VALUES (?, ?, 'IN_PROGRESS', ?, true)",
        karte,
        board.id(),
        zeit(DANACH));

    nachtragen();

    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(board.id(), "BACKLOG", T0, T1, true, "STATUS"),
            new Aufenthalt(board.id(), "READY", T1, DANACH, true, "STATUS"),
            new Aufenthalt(board.id(), "IN_PROGRESS", DANACH, null, true, null));
  }

  @Test
  void archivUndPapierkorbEndenDenVerlaufUndDieSaatFertigerKartenBleibtBekannt() {
    long archiviert = karte(board, "Ready", "READY", T0);
    spaltenzeile(archiviert, board, "Ready", T0, null);
    ereignis(archiviert, "ARCHIVED", "Archiviert", T1);
    jdbc.update("UPDATE card SET archived = true WHERE id = ?", archiviert);
    long papierkorb = karte(board, "In Progress", "IN_PROGRESS", T0);
    spaltenzeile(papierkorb, board, "In Progress", T0, null);
    jdbc.update("UPDATE card SET deleted_at = ? WHERE id = ?", zeit(T1), papierkorb);
    long fertigImArchiv = karte(board, "Done", "DONE", T0);
    spaltenzeile(fertigImArchiv, board, "Backlog", T0, T1);
    spaltenzeile(fertigImArchiv, board, "Done", T1, null);
    jdbc.update(
        "UPDATE card SET moved_to_done_at = ?, archived = true WHERE id = ?",
        zeit(T1),
        fertigImArchiv);
    ereignis(fertigImArchiv, "ARCHIVED", "Archiviert", T2);
    long spaeterFertig = karte(board, "Done", "DONE", T0);
    spaltenzeile(spaeterFertig, board, "Done", T0, null);
    jdbc.update("UPDATE card SET moved_to_done_at = ? WHERE id = ?", zeit(T2), spaeterFertig);
    saeen();

    nachtragen();

    assertThat(aufenthalte(archiviert))
        .containsExactly(new Aufenthalt(board.id(), "READY", T0, T1, true, "EXIT"));
    assertThat(aufenthalte(papierkorb))
        .containsExactly(new Aufenthalt(board.id(), "IN_PROGRESS", T0, T1, true, "EXIT"));
    // Die Saat aus V56 beginnt an moved_to_done_at mit bekanntem Eintritt — sie bleibt.
    assertThat(aufenthalte(fertigImArchiv))
        .containsExactly(
            new Aufenthalt(board.id(), "BACKLOG", T0, T1, true, "STATUS"),
            new Aufenthalt(board.id(), "DONE", T1, null, true, null));
    assertThat(aufenthalte(spaeterFertig))
        .containsExactly(new Aufenthalt(board.id(), "DONE", T2, null, true, null));
  }

  @Test
  void widersprichtDerVerlaufDemStandBeiDerMigrationBleibtDieZeitDavorUnbekannt() {
    long andererZustand = karte(board, "Ready", "READY", T0);
    spaltenzeile(andererZustand, board, "In Progress", T0, null);
    long anderesBoard = karte(board, "Ready", "READY", T0);
    spaltenzeile(anderesBoard, zweitesBoard, "Ready", T0, null);
    // Archiviert ohne Spur im Verlauf: Der Verlauf hält die Karte bis zur Migration im Fluss.
    long ohneSpur = karte(board, "Ready", "READY", T0);
    spaltenzeile(ohneSpur, board, "Ready", T0, null);
    jdbc.update("UPDATE card SET archived = true WHERE id = ?", ohneSpur);
    saeen();

    nachtragen();

    assertThat(aufenthalte(andererZustand))
        .containsExactly(
            new Aufenthalt(board.id(), null, T0, SINCE, false, "STATUS"),
            new Aufenthalt(board.id(), "READY", SINCE, null, false, null));
    assertThat(aufenthalte(anderesBoard))
        .containsExactly(
            new Aufenthalt(zweitesBoard.id(), null, T0, SINCE, false, "BOARD"),
            new Aufenthalt(board.id(), "READY", SINCE, null, false, null));
    assertThat(aufenthalte(ohneSpur))
        .containsExactly(new Aufenthalt(board.id(), null, T0, SINCE, false, "EXIT"));
  }

  @Test
  void eineNachDerMigrationAngelegteKarteBleibtUnberuehrt() {
    saeen();
    long neu = karte(board, "Ready", "READY", DANACH);
    spaltenzeile(neu, board, "Backlog", DANACH, DANACH.plus(Duration.ofHours(1)));
    spaltenzeile(neu, board, "Ready", DANACH.plus(Duration.ofHours(1)), null);
    jdbc.update(
        "INSERT INTO card_flow_stay (card_id, board_id, status, entered_at, entered_known)"
            + " VALUES (?, ?, 'READY', ?, true)",
        neu,
        board.id(),
        zeit(DANACH));

    nachtragen();

    assertThat(aufenthalte(neu))
        .containsExactly(new Aufenthalt(board.id(), "READY", DANACH, null, true, null));
  }

  // --- Marke und Mehrfachstart (E20) -------------------------------------------------------------

  @Test
  void einZweiterStartAendertNichts() {
    long karte = karte(board, "Ready", "READY", T0);
    spaltenzeile(karte, board, "Backlog", T0, T1);
    spaltenzeile(karte, board, "Ready", T1, null);
    saeen();
    nachtragen();
    List<Aufenthalt> nachErstemLauf = aufenthalte(karte);
    Instant ersteMarke = marke();

    uhr.advance(Duration.ofHours(1));
    assertThat(nachtrag.nachtragen()).isFalse();

    assertThat(aufenthalte(karte)).isEqualTo(nachErstemLauf);
    assertThat(marke()).isEqualTo(ersteMarke);
  }

  @Test
  void einGleichzeitigerNachtragWartetAufDieSperreUndTutDannNichts() throws Exception {
    long karte = karte(board, "Ready", "READY", T0);
    spaltenzeile(karte, board, "Backlog", T0, T1);
    spaltenzeile(karte, board, "Ready", T1, null);
    saeen();
    List<Aufenthalt> saat = aufenthalte(karte);

    CompletableFuture<Boolean> zweiter;
    try (Connection erster = dataSource.getConnection()) {
      erster.setAutoCommit(false);
      try (Statement statement = erster.createStatement()) {
        statement.execute("SELECT since FROM card_flow_capture FOR UPDATE");
        statement.execute("UPDATE card_flow_capture SET reconstructed_at = now()");
      }
      zweiter = CompletableFuture.supplyAsync(nachtrag::nachtragen);
      wartetAufSperre();
      assertThat(zweiter).isNotDone();
      erster.commit();
    }

    assertThat(zweiter.get(30, TimeUnit.SECONDS)).isFalse();
    assertThat(aufenthalte(karte)).isEqualTo(saat);
  }

  // --- Fehlerfall --------------------------------------------------------------------------------

  @Test
  void wirftDieRekonstruktionBleibtDieMarkeOffenUndDerStartLaeuftWeiter() {
    long karte = karte(board, "Ready", "READY", T0);
    spaltenzeile(karte, board, "Backlog", T0, T1);
    spaltenzeile(karte, board, "Ready", T1, null);
    saeen();
    List<Aufenthalt> saat = aufenthalte(karte);
    doThrow(new IllegalStateException("Rekonstruktion gescheitert")).when(quelle).lesen();

    assertThatCode(() -> runner.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

    assertThat(marke()).isNull();
    assertThat(aufenthalte(karte)).isEqualTo(saat);
  }

  @Test
  void scheitertDasSchreibenRolltDerNachtragGanzZurueck() {
    long karte = karte(board, "Ready", "READY", T0);
    spaltenzeile(karte, board, "Backlog", T0, T1);
    spaltenzeile(karte, board, "Ready", T1, null);
    saeen();
    List<Aufenthalt> saat = aufenthalte(karte);
    // Ein Verlauf auf einem Board, das es nicht gibt — der Fremdschlüssel weist ihn ab.
    doAnswer(
            aufruf -> {
              Bestand echt = (Bestand) aufruf.callRealMethod();
              List<Kartenverlauf> karten = new ArrayList<>(echt.karten());
              karten.add(
                  new Kartenverlauf(
                      new Kartenstand(karte, CardStatus.READY, T0, null, null),
                      List.of(new Spaltenzeile(new Spaltenort(-1, -1), "Ready", T0, T1)),
                      List.of()));
              return new Bestand(karten, echt.folgen());
            })
        .when(quelle)
        .lesen();

    assertThatThrownBy(nachtrag::nachtragen).isInstanceOf(DataIntegrityViolationException.class);

    assertThat(marke()).isNull();
    assertThat(aufenthalte(karte)).isEqualTo(saat);
  }

  @Test
  void derRunnerTraegtNachUndLaesstEineGesetzteMarkeStehen() throws Exception {
    long karte = karte(board, "Ready", "READY", T0);
    spaltenzeile(karte, board, "Backlog", T0, T1);
    spaltenzeile(karte, board, "Ready", T1, null);
    saeen();

    runner.run(new DefaultApplicationArguments());
    Instant ersteMarke = marke();
    uhr.advance(Duration.ofHours(1));
    runner.run(new DefaultApplicationArguments());

    assertThat(ersteMarke).isNotNull();
    assertThat(marke()).isEqualTo(ersteMarke);
    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(board.id(), "BACKLOG", T0, T1, true, "STATUS"),
            new Aufenthalt(board.id(), "READY", T1, null, true, null));
  }

  // --- Bestand -----------------------------------------------------------------------------------

  /** Board mit der Zusatzspalte „Prüfung“ zwischen In Progress und In Review. */
  private BoardView boardMitPruefung(String name) {
    BoardView neu = boards.createBoard(owner, projectId, name);
    long pruefung = boards.addColumn(owner, neu.id(), "Prüfung", null).id();
    List<Long> standard = neu.columns().stream().map(ColumnView::id).toList();
    boards.reorderColumns(
        owner,
        neu.id(),
        List.of(
            standard.get(0),
            standard.get(1),
            standard.get(2),
            pruefung,
            standard.get(3),
            standard.get(4)));
    return boards.getBoard(owner, neu.id());
  }

  /**
   * Legt eine Karte über den Dienst an und setzt danach ihren Stand wie vor der Migration: Spalte,
   * Zustand, Anlagezeitpunkt; ihr Verlauf und ihre Aufenthalte werden geleert.
   */
  private long karte(BoardView ziel, String spalte, String status, Instant angelegt) {
    long karte =
        cardService
            .create(owner, ziel.id(), spalte(ziel, "Backlog"), "Paket", null, null, null)
            .id();
    jdbc.update(
        "UPDATE card SET column_id = ?, status = ?, created_at = ?, position_in_column = ?"
            + " WHERE id = ?",
        spalte(ziel, spalte),
        status,
        zeit(angelegt),
        naechstePosition,
        karte);
    naechstePosition++;
    jdbc.update("DELETE FROM card_column_transition WHERE card_id = ?", karte);
    jdbc.update("DELETE FROM card_flow_stay WHERE card_id = ?", karte);
    jdbc.update("DELETE FROM card_activity WHERE card_id = ?", karte);
    return karte;
  }

  private void spaltenzeile(
      long karte, BoardView ort, String spalte, Instant von, @Nullable Instant bis) {
    jdbc.update(
        "INSERT INTO card_column_transition (card_id, column_id, column_name, entered_at, left_at)"
            + " VALUES (?, ?, ?, ?, ?)",
        karte,
        spalte(ort, spalte),
        spalte,
        zeit(von),
        zeit(bis));
  }

  private void geloeschteSpaltenzeile(long karte, String spalte, Instant von, Instant bis) {
    jdbc.update(
        "INSERT INTO card_column_transition (card_id, column_id, column_name, entered_at, left_at)"
            + " VALUES (?, NULL, ?, ?, ?)",
        karte,
        spalte,
        zeit(von),
        zeit(bis));
  }

  private void ereignis(long karte, String typ, String detail, Instant at) {
    jdbc.update(
        "INSERT INTO card_activity (card_id, type, detail, created_at) VALUES (?, ?, ?, ?)",
        karte,
        typ,
        detail,
        zeit(at));
  }

  /** Die Migration V56: Marke ab {@link #SINCE}, Saat je Karte im Fluss. */
  private void saeen() {
    jdbc.update("DELETE FROM card_flow_capture");
    jdbc.update("INSERT INTO card_flow_capture (since) VALUES (?)", zeit(SINCE));
    jdbc.update(SAAT);
  }

  private void nachtragen() {
    uhr.advance(Duration.ofMinutes(1));
    assertThat(nachtrag.nachtragen()).isTrue();
  }

  /** Wartet, bis eine Anfrage an einer Zeilensperre hängt. */
  private void wartetAufSperre() throws SQLException, InterruptedException {
    long ende = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    try (Connection beobachter = dataSource.getConnection();
        PreparedStatement wartende =
            beobachter.prepareStatement("SELECT count(*) FROM pg_locks WHERE NOT granted")) {
      while (System.nanoTime() < ende) {
        try (var rs = wartende.executeQuery()) {
          rs.next();
          if (rs.getLong(1) > 0) {
            return;
          }
        }
        TimeUnit.MILLISECONDS.sleep(20);
      }
    }
    throw new AssertionError("Der zweite Nachtrag wartet nicht auf die Sperre");
  }

  private @Nullable Instant marke() {
    OffsetDateTime wert =
        jdbc.queryForObject("SELECT reconstructed_at FROM card_flow_capture", OffsetDateTime.class);
    return wert == null ? null : wert.toInstant();
  }

  private static long spalte(BoardView ziel, String name) {
    return ziel.columns().stream()
        .filter(c -> name.equals(c.name()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Spalte fehlt: " + name))
        .id();
  }

  private List<Aufenthalt> aufenthalte(long karte) {
    return jdbc.query(
        "SELECT board_id, status, entered_at, left_at, entered_known, ended_by"
            + " FROM card_flow_stay WHERE card_id = ? ORDER BY entered_at, id",
        (rs, i) -> {
          OffsetDateTime left = rs.getObject("left_at", OffsetDateTime.class);
          return new Aufenthalt(
              rs.getLong("board_id"),
              rs.getString("status"),
              rs.getObject("entered_at", OffsetDateTime.class).toInstant(),
              left == null ? null : left.toInstant(),
              rs.getBoolean("entered_known"),
              rs.getString("ended_by"));
        },
        karte);
  }

  private static @Nullable Timestamp zeit(@Nullable Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }
}
