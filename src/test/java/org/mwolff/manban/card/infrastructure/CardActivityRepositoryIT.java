package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardActivityRepository;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.LaufBewegung;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Adapter-Test für den Karten-Aktivitätsverlauf (add/findByCardId) und die Lauf-Bewegungen (Issue
 * #1755, Plan #1752 E1, E16): Deren Vorgängerstatus entsteht per {@code LAG} über die ganze
 * Kartenhistorie, erst danach schränkt die Abfrage auf Laufkennung und Zeitfenster ein — diese
 * Reihenfolge belegt nur Postgres.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardActivityRepositoryIT extends AbstractIntegrationTest {

  private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-01-01T11:00:00Z");
  private static final Instant VON = Instant.parse("2026-10-14T22:00:00Z");
  private static final Instant LAUF = Instant.parse("2026-10-14T23:00:00Z");
  private static final ActorStamp LAUF_STEMPEL =
      new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "claude-opus-5-5", LAUF);
  private static final ActorStamp MENSCH =
      new ActorStamp(CardActivityOrigin.SESSION, null, null, null);

  @Autowired private CardActivityRepository activity;
  @Autowired private JdbcTemplate jdbc;

  private long cardId;
  private long userId;
  private long boardId;
  private long columnId;
  private int naechsteNummer = 2;

  @BeforeEach
  void seed() {
    userId =
        insert(
            "INSERT INTO app_user (email, display_name) "
                + "VALUES ('a@example.com', 'A') RETURNING id");
    long projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    boardId =
        insert("INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
    columnId =
        insert(
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + boardId
                + ", 'Ready', 0) RETURNING id");
    cardId =
        insert(
            "INSERT INTO card (board_id, column_id, number, title, position_in_column) "
                + "VALUES ("
                + boardId
                + ", "
                + columnId
                + ", 1, 'A', 0) RETURNING id");
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    return id == null ? 0L : id;
  }

  /** Eine weitere Karte desselben Boards. */
  private long karte() {
    int nummer = naechsteNummer;
    naechsteNummer++;
    Long id =
        jdbc.queryForObject(
            "INSERT INTO card (board_id, column_id, number, title, position_in_column)"
                + " VALUES (?, ?, ?, 'Karte', ?) RETURNING id",
            Long.class,
            boardId,
            columnId,
            nummer,
            nummer);
    return id == null ? 0L : id;
  }

  @Test
  void addAndFindReturnsChronologicalHistory() {
    activity.add(
        cardId, userId, CardActivityType.CREATED, "Karte angelegt", T1, ActorStamp.unknown());
    activity.add(
        cardId, userId, CardActivityType.MOVED, "Verschoben nach Done", T2, ActorStamp.unknown());

    List<CardActivity> history = activity.findByCardId(cardId);

    assertThat(history).hasSize(2);
    assertThat(history.get(0).type()).isEqualTo(CardActivityType.CREATED);
    assertThat(history.get(0).detail()).isEqualTo("Karte angelegt");
    assertThat(history.get(0).actorUserId()).isEqualTo(userId);
    assertThat(history.get(0).createdAt()).isEqualTo(T1);
    assertThat(history.get(1).type()).isEqualTo(CardActivityType.MOVED);
  }

  @Test
  void addPersistsActorStampRoundTrip() {
    // Voller Stempel (Token-Herkunft samt Selbstauskunft) übersteht den Persistenz-Roundtrip.
    activity.add(
        cardId,
        userId,
        CardActivityType.CREATED,
        "Idee angelegt",
        T1,
        new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "claude-opus-5", null));

    CardActivity entry = activity.findByCardId(cardId).get(0);

    assertThat(entry.origin()).isEqualTo(CardActivityOrigin.TOKEN);
    assertThat(entry.tokenName()).isEqualTo("Nachtlauf");
    assertThat(entry.agent()).isEqualTo("claude-opus-5");
  }

  @Test
  void addPersistsEmptyStampAsNulls() {
    // Unbekannte Herkunft wird wie ein Alt-Eintrag gespeichert: alle drei Spalten NULL.
    activity.add(
        cardId, userId, CardActivityType.CREATED, "Karte angelegt", T1, ActorStamp.unknown());

    CardActivity entry = activity.findByCardId(cardId).get(0);

    assertThat(entry.origin()).isNull();
    assertThat(entry.tokenName()).isNull();
    assertThat(entry.agent()).isNull();
    assertThat(entry.laufStart()).isNull();
    assertThat(entry.statusAfter()).isNull();
  }

  @Test
  void addPersistsRunAndStatusAfterRoundTrip() {
    // Laufkennung aus dem Stempel und Status nach der Bewegung überstehen den Roundtrip (#1426).
    Instant laufStart = Instant.parse("2026-01-01T09:58:22.123Z");
    activity.add(
        cardId,
        userId,
        CardActivityType.MOVED,
        "Verschoben nach In review",
        T1,
        new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "claude-opus-5", laufStart),
        CardStatus.IN_REVIEW);

    CardActivity entry = activity.findByCardId(cardId).get(0);

    assertThat(entry.laufStart()).isEqualTo(laufStart);
    assertThat(entry.statusAfter()).isEqualTo(CardStatus.IN_REVIEW);
  }

  @Test
  void statusAfterOutsideValueListFailsAtConstraint() {
    // Die Werteliste von chk_card_activity_status_after ist die von chk_card_status (V46).
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO card_activity (card_id, actor_user_id, type, detail, created_at,"
                        + " status_after) VALUES (?, ?, 'MOVED', 'x', now(), 'ARCHIVED')",
                    cardId,
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_card_activity_status_after");
  }

  @Test
  void findByCardIdReturnsEmptyForUnknownCard() {
    assertThat(activity.findByCardId(cardId + 999)).isEmpty();
  }

  private void bewegung(long karte, Instant zeitpunkt, ActorStamp stamp, CardStatus status) {
    activity.add(karte, userId, CardActivityType.MOVED, "", zeitpunkt, stamp, status);
  }

  @Test
  void liefertJeLaufbewegungKarteArtKennungStatusUndVorgaenger() {
    long karte = karte();
    bewegung(karte, VON.plusSeconds(60), LAUF_STEMPEL, CardStatus.IN_PROGRESS);
    activity.add(
        karte,
        userId,
        CardActivityType.STATUS_CHANGED,
        "",
        VON.plusSeconds(120),
        LAUF_STEMPEL,
        CardStatus.READY);

    assertThat(activity.findLaufBewegungen(List.of(karte), VON))
        .containsExactly(
            new LaufBewegung(
                karte,
                CardActivityType.MOVED,
                VON.plusSeconds(60),
                LAUF,
                CardStatus.IN_PROGRESS,
                null),
            new LaufBewegung(
                karte,
                CardActivityType.STATUS_CHANGED,
                VON.plusSeconds(120),
                LAUF,
                CardStatus.READY,
                CardStatus.IN_PROGRESS));
  }

  @Test
  void derVorgaengerStammtAuchAusEinerBewegungVorVonOhneLaufkennung() {
    long karte = karte();
    bewegung(karte, VON.minusSeconds(3600), MENSCH, CardStatus.IN_PROGRESS);
    bewegung(karte, VON.plusSeconds(60), LAUF_STEMPEL, CardStatus.BACKLOG);

    assertThat(activity.findLaufBewegungen(List.of(karte), VON))
        .extracting(LaufBewegung::statusAfter, LaufBewegung::statusVorher)
        .containsExactly(tuple(CardStatus.BACKLOG, CardStatus.IN_PROGRESS));
  }

  @Test
  void aktivitaetenOhneStatusZaehlenNichtAlsVorgaenger() {
    long karte = karte();
    bewegung(karte, VON.minusSeconds(60), MENSCH, CardStatus.IN_PROGRESS);
    activity.add(karte, userId, CardActivityType.UPDATED, "", VON, LAUF_STEMPEL);
    activity.add(karte, userId, CardActivityType.CREATED, "", VON.plusSeconds(1), LAUF_STEMPEL);
    bewegung(karte, VON.plusSeconds(60), LAUF_STEMPEL, CardStatus.READY);

    assertThat(activity.findLaufBewegungen(List.of(karte), VON))
        .extracting(LaufBewegung::type, LaufBewegung::statusVorher)
        .containsExactly(tuple(CardActivityType.MOVED, CardStatus.IN_PROGRESS));
  }

  @Test
  void bewegungenOhneLaufkennungOderVorVonFehlen() {
    long karte = karte();
    bewegung(
        karte,
        VON.minusMillis(1),
        new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "m", VON.minusSeconds(60)),
        CardStatus.IN_PROGRESS);
    bewegung(karte, VON.plusSeconds(10), MENSCH, CardStatus.READY);
    bewegung(karte, VON, LAUF_STEMPEL, CardStatus.BACKLOG);

    assertThat(activity.findLaufBewegungen(List.of(karte), VON))
        .extracting(LaufBewegung::createdAt, LaufBewegung::statusVorher)
        .containsExactly(tuple(VON, CardStatus.IN_PROGRESS));
  }

  @Test
  void derVorgaengerBleibtJeKarteUndDieAuswahlBeiDenGefragtenKarten() {
    long erste = karte();
    long zweite = karte();
    long fremde = karte();
    bewegung(erste, VON.minusSeconds(60), MENSCH, CardStatus.IN_PROGRESS);
    bewegung(zweite, VON.plusSeconds(30), LAUF_STEMPEL, CardStatus.READY);
    bewegung(erste, VON.plusSeconds(60), LAUF_STEMPEL, CardStatus.READY);
    bewegung(fremde, VON.plusSeconds(90), LAUF_STEMPEL, CardStatus.READY);

    assertThat(activity.findLaufBewegungen(List.of(erste, zweite), VON))
        .extracting(LaufBewegung::cardId, LaufBewegung::statusVorher)
        .containsExactly(tuple(zweite, null), tuple(erste, CardStatus.IN_PROGRESS));
  }

  @Test
  void beiGleichemZeitpunktEntscheidetDieId() {
    long karte = karte();
    Instant gleich = VON.plusSeconds(60);
    bewegung(karte, gleich, LAUF_STEMPEL, CardStatus.IN_PROGRESS);
    bewegung(karte, gleich, LAUF_STEMPEL, CardStatus.READY);

    assertThat(activity.findLaufBewegungen(List.of(karte), VON))
        .extracting(LaufBewegung::statusAfter, LaufBewegung::statusVorher)
        .containsExactly(
            tuple(CardStatus.IN_PROGRESS, null), tuple(CardStatus.READY, CardStatus.IN_PROGRESS));
  }

  @Test
  void ohneKartenEineLeereListe() {
    assertThat(activity.findLaufBewegungen(List.of(), VON)).isEmpty();
  }
}
