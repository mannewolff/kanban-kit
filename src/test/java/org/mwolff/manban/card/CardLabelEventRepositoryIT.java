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
import org.mwolff.manban.card.domain.LabelEreignisMitPerson;
import org.mwolff.manban.card.domain.LabelEreignisZeile;
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

  private long userId;
  private long boardId;
  private long columnId;
  private int naechsteNummer = 1;

  private long id(String sql, Object... args) {
    Long wert = jdbc.queryForObject(sql, Long.class, args);
    return wert == null ? 0L : wert;
  }

  @BeforeEach
  void seed() {
    userId =
        id(
            "INSERT INTO app_user (email, display_name)"
                + " VALUES ('label@example.com', 'L') RETURNING id");
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

    events.add(karte, "kit:clarify", GESETZT, LAUF_STEMPEL, userId);

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
  void einEreignisOhneHerkunftKommtMitHerkunftNullZurueck() {
    long karte = karte();

    events.add(karte, "kit:clarify", GESETZT, new ActorStamp(null, null, null, LAUF), userId);

    assertThat(events.findLaufEreignisse(List.of(karte), VON))
        .containsExactly(new CardLabelEvent(karte, "kit:clarify", GESETZT, null, null, null, LAUF));
  }

  @Test
  void einEreignisOhneLaufkennungGehoertZuKeinemLauf() {
    long karte = karte();

    events.add(
        karte,
        "kit:clarify",
        GESETZT,
        new ActorStamp(CardActivityOrigin.SESSION, null, null, null),
        userId);
    events.add(karte, "run:waiting", GESETZT, ActorStamp.unknown(), userId);

    assertThat(events.findLaufEreignisse(List.of(karte), VON)).isEmpty();
  }

  @Test
  void dieLaufkennungZaehltAbVonEinschliesslich() {
    long karte = karte();
    ActorStamp davor =
        new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "m", VON.minusMillis(1));
    ActorStamp aufVon = new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "m", VON);

    events.add(karte, "davor", GESETZT, davor, userId);
    events.add(karte, "aufVon", GESETZT, aufVon, userId);

    assertThat(events.findLaufEreignisse(List.of(karte), VON))
        .extracting(CardLabelEvent::labelName)
        .containsExactly("aufVon");
  }

  @Test
  void nurDieGefragtenKartenChronologisch() {
    long erste = karte();
    long zweite = karte();
    long fremde = karte();
    events.add(zweite, "spaet", GESETZT.plusSeconds(60), LAUF_STEMPEL, userId);
    events.add(erste, "frueh", GESETZT, LAUF_STEMPEL, userId);
    events.add(fremde, "fremd", GESETZT, LAUF_STEMPEL, userId);

    assertThat(events.findLaufEreignisse(List.of(erste, zweite), VON))
        .extracting(CardLabelEvent::labelName)
        .containsExactly("frueh", "spaet");
  }

  @Test
  void ohneKartenEineLeereListe() {
    assertThat(events.findLaufEreignisse(List.of(), VON)).isEmpty();
  }

  private long zeilenId(long karte, String labelName) {
    return id(
        "SELECT id FROM card_label_event WHERE card_id = ? AND label_name = ?", karte, labelName);
  }

  @Test
  void nachLabelNamenLiefertZeilenIdUndLaufkennungAuchOhneLauf() {
    long karte = karte();
    events.add(karte, "kit:clarify", GESETZT, LAUF_STEMPEL, userId);
    events.add(karte, "kit:klaeren", GESETZT.plusSeconds(1), ActorStamp.unknown(), userId);

    assertThat(events.findNachLabelNamenSeit(List.of("kit:clarify", "kit:klaeren"), VON))
        .containsExactly(
            new LabelEreignisZeile(
                zeilenId(karte, "kit:clarify"), karte, "kit:clarify", GESETZT, LAUF),
            new LabelEreignisZeile(
                zeilenId(karte, "kit:klaeren"),
                karte,
                "kit:klaeren",
                GESETZT.plusSeconds(1),
                null));
  }

  @Test
  void nachLabelNamenLaesstAndereNamenWeg() {
    long karte = karte();
    events.add(karte, "kit:clarify", GESETZT, LAUF_STEMPEL, userId);
    events.add(karte, "bug", GESETZT, LAUF_STEMPEL, userId);

    assertThat(events.findNachLabelNamenSeit(List.of("kit:clarify"), VON))
        .extracting(LabelEreignisZeile::labelName)
        .containsExactly("kit:clarify");
  }

  @Test
  void nachLabelNamenZaehltDenAnlagezeitpunktAbSeitEinschliesslich() {
    long karte = karte();
    // Die Laufkennung liegt bewusst vor der Grenze: Es zaehlt created_at, nicht run_started_at.
    ActorStamp frueherLauf =
        new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "m", GESETZT.minusSeconds(3600));
    events.add(karte, "davor", GESETZT.minusMillis(1), LAUF_STEMPEL, userId);
    events.add(karte, "aufSeit", GESETZT, frueherLauf, userId);

    assertThat(events.findNachLabelNamenSeit(List.of("davor", "aufSeit"), GESETZT))
        .extracting(LabelEreignisZeile::labelName)
        .containsExactly("aufSeit");
  }

  @Test
  void nachLabelNamenBeiGleichstandNachZeilenId() {
    long karte = karte();
    events.add(karte, "zuerst", GESETZT, LAUF_STEMPEL, userId);
    events.add(karte, "danach", GESETZT, LAUF_STEMPEL, userId);

    assertThat(events.findNachLabelNamenSeit(List.of("danach", "zuerst"), VON))
        .extracting(LabelEreignisZeile::labelName)
        .containsExactly("zuerst", "danach");
  }

  @Test
  void nachLabelNamenOhneNamenEineLeereListe() {
    events.add(karte(), "kit:clarify", GESETZT, LAUF_STEMPEL, userId);

    assertThat(events.findNachLabelNamenSeit(List.of(), VON)).isEmpty();
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
    events.add(karte, "kit:clarify", GESETZT, LAUF_STEMPEL, userId);

    jdbc.update("DELETE FROM card WHERE id = ?", karte);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM card_label_event", Integer.class))
        .isZero();
  }

  private long weitererNutzer() {
    return id(
        "INSERT INTO app_user (email, display_name)"
            + " VALUES ('weg@example.com', 'W') RETURNING id");
  }

  @Test
  void mitPersonLiefertPersonHerkunftTokenNameUndZeitpunkt() {
    long karte = karte();

    events.add(karte, "kit:nightrun", GESETZT, LAUF_STEMPEL, userId);

    assertThat(events.findMitPerson(List.of(karte)))
        .containsExactly(
            new LabelEreignisMitPerson(
                zeilenId(karte, "kit:nightrun"),
                karte,
                "kit:nightrun",
                GESETZT,
                userId,
                CardActivityOrigin.TOKEN,
                "Nachtlauf"));
  }

  @Test
  void mitPersonLiefertNurDieGefragtenKartenChronologischUndBeiGleichstandNachId() {
    long erste = karte();
    long zweite = karte();
    long fremde = karte();
    events.add(zweite, "spaet", GESETZT.plusSeconds(60), LAUF_STEMPEL, userId);
    events.add(erste, "zuerst", GESETZT, LAUF_STEMPEL, userId);
    events.add(zweite, "danach", GESETZT, LAUF_STEMPEL, userId);
    events.add(fremde, "fremd", GESETZT, LAUF_STEMPEL, userId);

    assertThat(events.findMitPerson(List.of(erste, zweite)))
        .extracting(LabelEreignisMitPerson::labelName)
        .containsExactly("zuerst", "danach", "spaet");
  }

  @Test
  void mitPersonOhneKartenEineLeereListe() {
    events.add(karte(), "kit:nightrun", GESETZT, LAUF_STEMPEL, userId);

    assertThat(events.findMitPerson(List.of())).isEmpty();
  }

  /** Plan #2108 E5: {@code ON DELETE SET NULL} wie {@code card_activity.actor_user_id}. */
  @Test
  void loeschenDesNutzersLeertDiePersonUndLaesstDasEreignisStehen() {
    long karte = karte();
    long nutzer = weitererNutzer();
    events.add(
        karte,
        "kit:nightrun",
        GESETZT,
        new ActorStamp(CardActivityOrigin.SESSION, null, null, null),
        nutzer);

    jdbc.update("DELETE FROM app_user WHERE id = ?", nutzer);

    assertThat(events.findMitPerson(List.of(karte)))
        .containsExactly(
            new LabelEreignisMitPerson(
                zeilenId(karte, "kit:nightrun"),
                karte,
                "kit:nightrun",
                GESETZT,
                null,
                CardActivityOrigin.SESSION,
                null));
  }

  /** Ein Ereignis von vor der Einführung trägt keine Person und bleibt lesbar (Kriterium 9). */
  @Test
  void einEreignisOhnePersonBleibtLesbar() {
    long karte = karte();
    jdbc.update(
        "INSERT INTO card_label_event (card_id, label_name, created_at) VALUES (?, 'alt', ?)",
        karte,
        OffsetDateTime.ofInstant(GESETZT, ZoneOffset.UTC));

    assertThat(events.findMitPerson(List.of(karte)))
        .containsExactly(
            new LabelEreignisMitPerson(
                zeilenId(karte, "alt"), karte, "alt", GESETZT, null, null, null));
  }
}
