package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardArchiveService;
import org.mwolff.manban.card.application.CardIngestService;
import org.mwolff.manban.card.application.CardIngestService.DirectCard;
import org.mwolff.manban.card.application.CardMoveService;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.DoneRetentionService;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Erfassung der Zustandsaufenthalte im {@code CardRepositoryAdapter} (Issue #1768, Plan #1765 E2,
 * E3, E4, E5, E12): Je Schreibweg entsteht genau der erwartete Wechsel in {@code card_flow_stay},
 * gestempelt mit der injizierten Uhr. Nach jedem Fall gilt die Invariante — jede Karte im Fluss hat
 * genau einen offenen Aufenthalt mit ihrem Zustand und Board, jede andere keinen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(StellbareUhrConfig.class)
class FlussErfassungIT extends AbstractIntegrationTest {

  private static final String OWNER = "fluss-owner@example.com";

  @Autowired private AppUserRepository users;
  @Autowired private ProjectService projects;
  @Autowired private BoardService boards;
  @Autowired private CardService cardService;
  @Autowired private CardMoveService moves;
  @Autowired private CardArchiveService archiv;
  @Autowired private CardIngestService ingest;
  @Autowired private KanbanCompatService kanban;
  @Autowired private DoneRetentionService doneRetention;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock uhr;

  private long owner;
  private long projectId;

  /** Ein Aufenthalt, wie er in {@code card_flow_stay} steht. */
  private record Aufenthalt(
      long boardId,
      @Nullable String status,
      Instant enteredAt,
      @Nullable Instant leftAt,
      boolean enteredKnown,
      @Nullable String endedBy) {}

  @BeforeEach
  void projektAnlegen() {
    long admin =
        users
            .save(new AppUser(null, "fluss-admin@example.com", "A", true, PlatformRole.ADMIN))
            .id();
    owner = users.save(new AppUser(null, OWNER, "O", true, PlatformRole.USER)).id();
    projectId = projects.create(admin, "Fluss", OWNER).id();
  }

  // --- Anlegen -----------------------------------------------------------------------------------

  @Test
  void anlegenEinesPaketsOeffnetEinenAufenthaltMitBekanntemEintritt() {
    BoardView board = board("B");
    long karte = anlegen(board, "Backlog", "Paket");

    Instant createdAt = createdAt(karte);
    assertThat(aufenthalte(karte))
        .containsExactly(new Aufenthalt(board.id(), "BACKLOG", createdAt, null, true, null));
    assertThat(createdAt).isEqualTo(uhr.instant());
    invariante(karte);
  }

  @Test
  void anlegenEinesDokumentsSchreibtNichts() {
    BoardView board = board("B");
    long karte = anlegen(board, "In Review", "[Requirement] Story");

    assertThat(aufenthalte(karte)).isEmpty();
    invariante(karte);
  }

  // --- Verschieben -------------------------------------------------------------------------------

  @Test
  void verschiebenMitZustandswechselSchliesstUndOeffnet() {
    BoardView board = board("B");
    long karte = anlegen(board, "Backlog", "Paket");
    Instant angelegt = uhr.instant();

    Instant gezogen = weiter();
    moves.move(owner, karte, spalte(board, "Ready"), 0);

    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(board.id(), "BACKLOG", angelegt, gezogen, true, "STATUS"),
            new Aufenthalt(board.id(), "READY", gezogen, null, true, null));
    invariante(karte);
  }

  @Test
  void verschiebenZwischenBacklogAnstehendUndParkenSchreibtNichts_ausReadyNachParkenWirdBacklog() {
    BoardView board = boardMitBacklogSpalten();
    long karte = anlegen(board, "Backlog", "Paket");
    Instant angelegt = uhr.instant();

    weiter();
    moves.move(owner, karte, spalte(board, "Anstehend"), 0);
    weiter();
    moves.move(owner, karte, spalte(board, "Parken"), 0);
    weiter();
    moves.move(owner, karte, spalte(board, "Backlog"), 0);
    assertThat(aufenthalte(karte))
        .containsExactly(new Aufenthalt(board.id(), "BACKLOG", angelegt, null, true, null));
    invariante(karte);

    Instant nachReady = weiter();
    moves.move(owner, karte, spalte(board, "Ready"), 0);
    Instant nachParken = weiter();
    moves.move(owner, karte, spalte(board, "Parken"), 0);

    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(board.id(), "BACKLOG", angelegt, nachReady, true, "STATUS"),
            new Aufenthalt(board.id(), "READY", nachReady, nachParken, true, "STATUS"),
            new Aufenthalt(board.id(), "BACKLOG", nachParken, null, true, null));
    invariante(karte);
  }

  @Test
  void setStatusOhneSpaltenwechselWirdErfasst() {
    BoardView board = board("B");
    // Ohne Prozessspalte für In Review setzt setStatus nur den Status — ein reiner JPA-Merge.
    boards.updateColumn(owner, spalte(board, "In Review"), "Abnahme", null);
    long karte = anlegen(board, "Backlog", "Paket");
    Instant angelegt = uhr.instant();

    Instant gesetzt = weiter();
    moves.setStatus(owner, karte, "IN_REVIEW");

    assertThat(jdbc.queryForObject("SELECT column_id FROM card WHERE id = ?", Long.class, karte))
        .isEqualTo(spalte(board, "Backlog"));
    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(board.id(), "BACKLOG", angelegt, gesetzt, true, "STATUS"),
            new Aufenthalt(board.id(), "IN_REVIEW", gesetzt, null, true, null));
    invariante(karte);
  }

  // --- Übertragen --------------------------------------------------------------------------------

  @Test
  void uebertragenSchliesstMitBoardUndOeffnetAufDemZielOhneNullaufenthalt() {
    BoardView quelle = board("Quelle");
    BoardView ziel = board("Ziel");
    long karte = anlegen(quelle, "Ready", "Paket");
    Instant angelegt = uhr.instant();

    // Zustand und Board wechseln zugleich — transfer setzt beides in zwei Zugriffen.
    Instant umgezogen = weiter();
    moves.transfer(owner, karte, ziel.id(), spalte(ziel, "In Progress"));

    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(quelle.id(), "READY", angelegt, umgezogen, true, "BOARD"),
            new Aufenthalt(ziel.id(), "IN_PROGRESS", umgezogen, null, true, null));
    invariante(karte);
  }

  @Test
  void uebertragenOhneZustandswechselSchliesstMitBoard() {
    BoardView quelle = board("Quelle");
    BoardView ziel = board("Ziel");
    long karte = anlegen(quelle, "Ready", "Paket");
    Instant angelegt = uhr.instant();

    Instant umgezogen = weiter();
    moves.transfer(owner, karte, ziel.id(), spalte(ziel, "Ready"));

    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(quelle.id(), "READY", angelegt, umgezogen, true, "BOARD"),
            new Aufenthalt(ziel.id(), "READY", umgezogen, null, true, null));
    invariante(karte);
  }

  // --- Artwechsel --------------------------------------------------------------------------------

  @Test
  void artwechselZumDokumentVerlaesstDenFluss_zurueckZumPaketOeffnetNeu() {
    BoardView board = board("B");
    long karte = anlegen(board, "Ready", "Paket");
    Instant angelegt = uhr.instant();

    Instant zumDokument = weiter();
    cardService.update(owner, karte, "[Plan] Weg", null, null, null, null, null);
    assertThat(aufenthalte(karte))
        .containsExactly(new Aufenthalt(board.id(), "READY", angelegt, zumDokument, true, "EXIT"));
    invariante(karte);

    Instant zumPaket = weiter();
    cardService.update(owner, karte, "Paket", null, null, null, null, null);
    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(board.id(), "READY", angelegt, zumDokument, true, "EXIT"),
            new Aufenthalt(board.id(), "READY", zumPaket, null, true, null));
    invariante(karte);
  }

  // --- Archiv ------------------------------------------------------------------------------------

  @Test
  void archivEinerNichtFertigenKarteVerlaesstDenFluss_rueckholungOeffnetNeu() {
    BoardView board = board("B");
    long karte = anlegen(board, "In Progress", "Paket");
    Instant angelegt = uhr.instant();

    Instant archiviert = weiter();
    archiv.archive(owner, karte);
    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(board.id(), "IN_PROGRESS", angelegt, archiviert, true, "EXIT"));
    invariante(karte);

    Instant zurueck = weiter();
    archiv.restore(owner, karte);
    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(board.id(), "IN_PROGRESS", angelegt, archiviert, true, "EXIT"),
            new Aufenthalt(board.id(), "IN_PROGRESS", zurueck, null, true, null));
    invariante(karte);
  }

  @Test
  void archivEinerFertigenKarteSchreibtNichts() {
    BoardView board = board("B");
    long karte = anlegen(board, "Done", "Paket");
    Instant angelegt = uhr.instant();

    weiter();
    archiv.archive(owner, karte);

    assertThat(aufenthalte(karte))
        .containsExactly(new Aufenthalt(board.id(), "DONE", angelegt, null, true, null));
    invariante(karte);
  }

  @Test
  void doneAufraeumungSchreibtNichts() {
    BoardView board = board("B");
    long karte = anlegen(board, "Done", "Paket");
    Instant angelegt = uhr.instant();

    Instant spaeter = uhr.instant().plus(Duration.ofDays(40));
    assertThat(doneRetention.archiveExpiredDoneCards(spaeter, 30)).isEqualTo(1);

    assertThat(aufenthalte(karte))
        .containsExactly(new Aufenthalt(board.id(), "DONE", angelegt, null, true, null));
    invariante(karte);
  }

  // --- Papierkorb --------------------------------------------------------------------------------

  @Test
  void papierkorbVerlaesstDenFluss_rueckholungOeffnetMitBekanntemEintritt() {
    BoardView board = board("B");
    long karte = anlegen(board, "Ready", "Paket");
    Instant angelegt = uhr.instant();

    Instant geloescht = weiter();
    archiv.delete(owner, karte);
    assertThat(aufenthalte(karte))
        .containsExactly(new Aufenthalt(board.id(), "READY", angelegt, geloescht, true, "EXIT"));
    invariante(karte);

    Instant zurueck = weiter();
    archiv.restoreFromTrash(owner, karte);
    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(board.id(), "READY", angelegt, geloescht, true, "EXIT"),
            new Aufenthalt(board.id(), "READY", zurueck, null, true, null));
    invariante(karte);
  }

  @Test
  void papierkorbNimmtAuchEineFertigeKarteHeraus() {
    BoardView board = board("B");
    long karte = anlegen(board, "Done", "Paket");
    Instant angelegt = uhr.instant();

    Instant geloescht = weiter();
    archiv.delete(owner, karte);

    assertThat(aufenthalte(karte))
        .containsExactly(new Aufenthalt(board.id(), "DONE", angelegt, geloescht, true, "EXIT"));
    invariante(karte);
  }

  @Test
  void endgueltigLoeschenNimmtDieAufenthalteMit() {
    BoardView board = board("B");
    long karte = anlegen(board, "Backlog", "Paket");
    weiter();
    moves.move(owner, karte, spalte(board, "Ready"), 0);
    archiv.delete(owner, karte);
    assertThat(aufenthalte(karte)).hasSize(2);

    archiv.purge(owner, karte);

    assertThat(aufenthalte(karte)).isEmpty();
  }

  // --- Ingest und kanbancompat -------------------------------------------------------------------

  @Test
  void ingestWirdErfasst() {
    BoardView board = board("B");
    long karte =
        ingest
            .createDirect(
                owner,
                board.id(),
                spalte(board, "Ready"),
                new DirectCard("Paket", null, "fluss-1", null, null))
            .view()
            .id();
    Instant angelegt = uhr.instant();
    assertThat(aufenthalte(karte))
        .containsExactly(new Aufenthalt(board.id(), "READY", angelegt, null, true, null));

    Instant zumDokument = weiter();
    ingest.updateContent(owner, karte, "[Plan] Weg", null);
    assertThat(aufenthalte(karte))
        .containsExactly(new Aufenthalt(board.id(), "READY", angelegt, zumDokument, true, "EXIT"));
    invariante(karte);
  }

  @Test
  void kanbancompatStatuswechselWirdErfasst() {
    BoardView board = board("B");
    long karte = anlegen(board, "Ready", "Paket");
    Instant angelegt = uhr.instant();
    KanbanPrincipal principal = new KanbanPrincipal(owner, 1L, projectId, board.id(), "Token");

    Instant gezogen = weiter();
    kanban.move(principal, karte, "IN_PROGRESS", 0);

    assertThat(aufenthalte(karte))
        .containsExactly(
            new Aufenthalt(board.id(), "READY", angelegt, gezogen, true, "STATUS"),
            new Aufenthalt(board.id(), "IN_PROGRESS", gezogen, null, true, null));
    invariante(karte);
  }

  // --- Hilfen ------------------------------------------------------------------------------------

  private BoardView board(String name) {
    return boards.createBoard(owner, projectId, name);
  }

  /** Board mit den Backlog-Spalten des Owners „Anstehend“ und „Parken“ direkt hinter Backlog. */
  private BoardView boardMitBacklogSpalten() {
    BoardView board = board("B");
    long anstehend = boards.addColumn(owner, board.id(), "Anstehend", null).id();
    long parken = boards.addColumn(owner, board.id(), "Parken", null).id();
    List<Long> standard = board.columns().stream().map(ColumnView::id).toList();
    boards.reorderColumns(
        owner,
        board.id(),
        List.of(
            standard.get(0),
            anstehend,
            parken,
            standard.get(1),
            standard.get(2),
            standard.get(3),
            standard.get(4)));
    return boards.getBoard(owner, board.id());
  }

  private long anlegen(BoardView board, String spalte, String titel) {
    weiter();
    return cardService
        .create(owner, board.id(), spalte(board, spalte), titel, null, null, null)
        .id();
  }

  private static long spalte(BoardView board, String name) {
    return board.columns().stream()
        .filter(c -> name.equals(c.name()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Spalte fehlt: " + name))
        .id();
  }

  /** Lässt eine Minute verstreichen und liefert den neuen Zeitpunkt der Uhr. */
  private Instant weiter() {
    uhr.advance(Duration.ofMinutes(1));
    return uhr.instant();
  }

  private Instant createdAt(long karte) {
    return jdbc.queryForObject(
            "SELECT created_at FROM card WHERE id = ?", OffsetDateTime.class, karte)
        .toInstant();
  }

  private List<Aufenthalt> aufenthalte(long karte) {
    return jdbc.query(
        "SELECT board_id, status, entered_at, left_at, entered_known, ended_by"
            + " FROM card_flow_stay WHERE card_id = ? ORDER BY id",
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

  /**
   * Die Invariante der Erfassung: Eine Karte im Fluss (Zustand gesetzt, nicht im Papierkorb, nicht
   * archiviert oder fertig) hat genau einen offenen Aufenthalt mit ihrem Zustand und Board, jede
   * andere keinen.
   */
  private void invariante(long karte) {
    Map<String, Object> zeile =
        jdbc.queryForMap(
            "SELECT board_id, status,"
                + " (status IS NOT NULL AND deleted_at IS NULL"
                + "  AND (NOT archived OR status = 'DONE')) AS im_fluss"
                + " FROM card WHERE id = ?",
            karte);
    List<Aufenthalt> offen = aufenthalte(karte).stream().filter(a -> a.leftAt() == null).toList();
    if (Boolean.TRUE.equals(zeile.get("im_fluss"))) {
      assertThat(offen).hasSize(1);
      assertThat(offen.getFirst().boardId())
          .isEqualTo(((Number) zeile.get("board_id")).longValue());
      assertThat(offen.getFirst().status()).isEqualTo(zeile.get("status"));
    } else {
      assertThat(offen).isEmpty();
    }
  }
}
