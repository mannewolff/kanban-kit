package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.LaufKarteView;
import org.mwolff.manban.card.application.CardService.TokenActivityView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Lese-Abfragen der Karten für den Fortschritt eines laufenden Laufs (Issue #1373, Plan #1372
 * E2).
 *
 * <p>Gegen die echte Datenbank, weil hier nur sie etwas belegt: Welche Aktivitäten zum Lauf zählen,
 * entscheiden die {@code WHERE}-Bedingungen der Abfrage — Projekt, Token, Fenster und vor allem das
 * gesetzte {@code agent}, das nur der Nachtbetrieb mitschickt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardTokenActivityWindowIT extends AbstractIntegrationTest {

  private static final Instant VON = Instant.parse("2026-10-03T15:00:00Z");
  private static final Instant DRIN = Instant.parse("2026-10-03T15:30:00Z");
  private static final Instant BIS = Instant.parse("2026-10-03T16:00:00Z");
  private static final String TOKEN = "Nachtlauf";
  private static final String MODELL = "claude-opus-5-5";

  @Autowired private CardService cards;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;
  private long boardId;
  private long columnId;
  private int naechsteNummer = 1;

  private long id(String sql, Object... args) {
    Long wert = jdbc.queryForObject(sql, Long.class, args);
    return wert == null ? 0L : wert;
  }

  @BeforeEach
  void seed() {
    long userId =
        id(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('spur@example.com', 'x', 'S') RETURNING id");
    projectId =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", userId);
    boardId = board(projectId);
    columnId = spalte(boardId);
  }

  private long board(long projekt) {
    return id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projekt);
  }

  private long spalte(long board) {
    return id(
        "INSERT INTO board_column (board_id, name, position) VALUES (?, 'Ready', 0)"
            + " RETURNING id",
        board);
  }

  private long karte(long board, long spalte, String titel) {
    int nummer = naechsteNummer;
    naechsteNummer++;
    return id(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column)"
            + " VALUES (?, ?, ?, ?, ?) RETURNING id",
        board,
        spalte,
        nummer,
        titel,
        nummer);
  }

  private long karte(String titel) {
    return karte(boardId, columnId, titel);
  }

  private void aktivitaet(
      long cardId,
      String type,
      Instant at,
      @Nullable String origin,
      @Nullable String token,
      @Nullable String agent) {
    jdbc.update(
        "INSERT INTO card_activity (card_id, type, detail, created_at, origin, token_name, agent)"
            + " VALUES (?, ?, '', ?, ?, ?, ?)",
        cardId,
        type,
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
        origin,
        token,
        agent);
  }

  private void laufAktivitaet(long cardId, String type, Instant at) {
    aktivitaet(cardId, type, at, "TOKEN", TOKEN, MODELL);
  }

  private List<TokenActivityView> imFenster() {
    return cards.tokenActivitiesInWindow(projectId, TOKEN, VON, BIS);
  }

  @Test
  void liefertDieAktivitaetDesLaufsMitKarteTypUndZeitpunkt() {
    long karte = karte("Plan");
    laufAktivitaet(karte, "CREATED", DRIN);
    laufAktivitaet(karte, "MOVED", DRIN.plusSeconds(60));

    assertThat(imFenster())
        .containsExactly(
            new TokenActivityView(karte, "CREATED", DRIN),
            new TokenActivityView(karte, "MOVED", DRIN.plusSeconds(60)));
  }

  /** AK: Eine Aktivität ohne {@code agent} stammt nicht vom Nachtbetrieb und zählt nicht (E2). */
  @Test
  void eineAktivitaetOhneAgentWirdNichtGeliefert() {
    long karte = karte("Interaktiv angefasst");
    aktivitaet(karte, "MOVED", DRIN, "TOKEN", TOKEN, null);

    assertThat(imFenster()).isEmpty();
  }

  @Test
  void dieFenstergrenzenGeltenEinschliesslich() {
    long davor = karte("Davor");
    long aufVon = karte("Auf von");
    long aufBis = karte("Auf bis");
    long danach = karte("Danach");
    laufAktivitaet(davor, "MOVED", VON.minusMillis(1));
    laufAktivitaet(aufVon, "MOVED", VON);
    laufAktivitaet(aufBis, "MOVED", BIS);
    laufAktivitaet(danach, "MOVED", BIS.plusMillis(1));

    assertThat(imFenster()).extracting(TokenActivityView::cardId).containsExactly(aufVon, aufBis);
  }

  @Test
  void einFremdesTokenWirdNichtGeliefert() {
    long karte = karte("Anderes Token");
    aktivitaet(karte, "MOVED", DRIN, "TOKEN", "Anderes", MODELL);

    assertThat(imFenster()).isEmpty();
  }

  @Test
  void eineSitzungsaktivitaetWirdNichtGeliefert() {
    long karte = karte("Sitzung");
    aktivitaet(karte, "MOVED", DRIN, "SESSION", TOKEN, MODELL);

    assertThat(imFenster()).isEmpty();
  }

  @Test
  void einFremdesProjektWirdNichtGeliefert() {
    long fremdesProjekt =
        id(
            "INSERT INTO project (name, owner_user_id)"
                + " SELECT 'Q', owner_user_id FROM project WHERE id = ? RETURNING id",
            projectId);
    long fremdesBoard = board(fremdesProjekt);
    long fremd = karte(fremdesBoard, spalte(fremdesBoard), "Fremd");
    laufAktivitaet(fremd, "MOVED", DRIN);

    assertThat(imFenster()).isEmpty();
  }

  @Test
  void cardsByIdsLiefertDieAngabenDerErmittlung() {
    long anforderung = karte("[Fachlich] Fortschritt");
    long plan = karte("[Plan] Fortschritt");
    long paket = karte("Paket 1/7");
    jdbc.update("UPDATE card SET derived_from_card_id = ? WHERE id = ?", anforderung, plan);
    jdbc.update(
        "UPDATE card SET description = ?, derived_from_card_id = ?, status = 'READY' WHERE id = ?",
        "Plan-Review: fable",
        plan,
        paket);
    long label =
        id(
            "INSERT INTO label (board_id, name, color) VALUES (?, 'lauf:laeuft', '#000000')"
                + " RETURNING id",
            boardId);
    jdbc.update("INSERT INTO card_label (card_id, label_id) VALUES (?, ?)", paket, label);

    List<LaufKarteView> gefunden = cards.cardsByIds(List.of(plan, paket));

    assertThat(gefunden)
        .filteredOn(k -> k.id() == paket)
        .singleElement()
        .satisfies(
            k -> {
              assertThat(k.boardId()).isEqualTo(boardId);
              assertThat(k.title()).isEqualTo("Paket 1/7");
              assertThat(k.status()).isEqualTo("READY");
              assertThat(k.labels()).containsExactly("lauf:laeuft");
              assertThat(k.derivedFromCardId()).isEqualTo(plan);
              assertThat(k.type()).isEqualTo("CARD");
              assertThat(k.arbeitspaket()).isTrue();
              assertThat(k.description()).isEqualTo("Plan-Review: fable");
            });
    assertThat(gefunden)
        .filteredOn(k -> k.id() == plan)
        .singleElement()
        .satisfies(
            k -> {
              assertThat(k.arbeitspaket()).isFalse();
              assertThat(k.labels()).isEmpty();
              assertThat(k.derivedFromCardId()).isEqualTo(anforderung);
            });
  }
}
