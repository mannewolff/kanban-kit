package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardLabelEventRepository;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardLabelEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Label-Ereignisse gegen die echte Datenbank (Issue #1754, Plan #1752 E2, E7): Welche
 * Ereignisse zu einem Lauf gehören, entscheiden die {@code WHERE}-Bedingungen der Abfrage, und das
 * Löschen der Karte räumt sie über {@code ON DELETE CASCADE} — beides belegt nur Postgres.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardLabelEventRepositoryIT extends AbstractIntegrationTest {

  private static final Instant VON = Instant.parse("2026-10-14T22:00:00Z");
  private static final Instant LAUF = Instant.parse("2026-10-14T23:00:00Z");
  private static final Instant GESETZT = Instant.parse("2026-10-14T23:30:00Z");
  private static final ActorStamp LAUF_STEMPEL =
      new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "claude-opus-5-5", LAUF);

  @Autowired private CardLabelEventRepository events;
  @Autowired private JdbcTemplate jdbc;

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
                + " VALUES ('label@example.com', 'x', 'L') RETURNING id");
    long projectId =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", userId);
    boardId = id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projectId);
    columnId =
        id(
            "INSERT INTO board_column (board_id, name, position) VALUES (?, 'Ready', 0)"
                + " RETURNING id",
            boardId);
  }

  private long karte() {
    int nummer = naechsteNummer;
    naechsteNummer++;
    return id(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column)"
            + " VALUES (?, ?, ?, 'Karte', ?) RETURNING id",
        boardId,
        columnId,
        nummer,
        nummer);
  }

  @Test
  void addHaeltDasEreignisMitStempelFestUndDieLaufabfrageLiefertEs() {
    long karte = karte();

    events.add(karte, "kit:clarify", GESETZT, LAUF_STEMPEL);

    assertThat(events.findLaufEreignisse(List.of(karte), VON))
        .containsExactly(
            new CardLabelEvent(
                karte,
                "kit:clarify",
                GESETZT,
                CardActivityOrigin.TOKEN,
                "Nachtlauf",
                "claude-opus-5-5",
                LAUF));
  }

  @Test
  void einEreignisOhneLaufkennungGehoertZuKeinemLauf() {
    long karte = karte();

    events.add(
        karte,
        "kit:clarify",
        GESETZT,
        new ActorStamp(CardActivityOrigin.SESSION, null, null, null));
    events.add(karte, "run:waiting", GESETZT, ActorStamp.unknown());

    assertThat(events.findLaufEreignisse(List.of(karte), VON)).isEmpty();
  }

  @Test
  void dieLaufkennungZaehltAbVonEinschliesslich() {
    long karte = karte();
    ActorStamp davor =
        new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "m", VON.minusMillis(1));
    ActorStamp aufVon = new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "m", VON);

    events.add(karte, "davor", GESETZT, davor);
    events.add(karte, "aufVon", GESETZT, aufVon);

    assertThat(events.findLaufEreignisse(List.of(karte), VON))
        .extracting(CardLabelEvent::labelName)
        .containsExactly("aufVon");
  }

  @Test
  void nurDieGefragtenKartenChronologisch() {
    long erste = karte();
    long zweite = karte();
    long fremde = karte();
    events.add(zweite, "spaet", GESETZT.plusSeconds(60), LAUF_STEMPEL);
    events.add(erste, "frueh", GESETZT, LAUF_STEMPEL);
    events.add(fremde, "fremd", GESETZT, LAUF_STEMPEL);

    assertThat(events.findLaufEreignisse(List.of(erste, zweite), VON))
        .extracting(CardLabelEvent::labelName)
        .containsExactly("frueh", "spaet");
  }

  @Test
  void ohneKartenEineLeereListe() {
    assertThat(events.findLaufEreignisse(List.of(), VON)).isEmpty();
  }

  @Test
  void erfassungSeitLiefertDieZeileDerMigration() {
    Instant seit = Instant.parse("2026-10-15T06:00:00Z");
    jdbc.update(
        "INSERT INTO card_label_event_capture (since) VALUES (?)",
        OffsetDateTime.ofInstant(seit, ZoneOffset.UTC));

    assertThat(events.erfassungSeit()).isEqualTo(seit);
  }

  @Test
  void loeschenDerKarteEntferntIhreEreignisse() {
    long karte = karte();
    events.add(karte, "kit:clarify", GESETZT, LAUF_STEMPEL);

    jdbc.update("DELETE FROM card WHERE id = ?", karte);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM card_label_event", Integer.class))
        .isZero();
  }
}
