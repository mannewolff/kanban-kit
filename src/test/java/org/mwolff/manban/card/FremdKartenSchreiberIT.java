package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.application.FremdKartenBestand;
import org.mwolff.manban.card.application.FremdKartenSchreiber;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremdeKarte;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremderAkteur;
import org.mwolff.manban.card.application.KartenSicht;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Schreibweg des Spiegels eines fremden Boards (Issue #2162, Plan #2152 A1, A2, A4, A11): Karte
 * mit vorgegebener Nummer, Wechsel mit dem Zeitpunkt des fremden Systems, Herkunft und externer
 * Akteur an Aktivitäten und Label-Ereignissen, gelöscht markieren — gegen eine echte Datenbank.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FremdKartenSchreiberIT extends AbstractIntegrationTest {

  private static final Instant ANGEBUNDEN = Instant.parse("2026-11-01T08:00:00Z");
  private static final Instant WECHSEL = Instant.parse("2026-11-02T09:15:00Z");
  private static final Instant LABEL = Instant.parse("2026-11-02T10:30:00Z");
  private static final Instant GELOESCHT = Instant.parse("2026-11-03T11:45:00Z");
  private static final String ADRESSE = "https://gitlab.example.org/gruppe/projekt/-/issues/7";
  private static final FremderAkteur KIT = FremderAkteur.von("kit-bot", "kit-bot");
  private static final FremderAkteur ERIKA = FremderAkteur.von("erika", "kit-bot");

  @Autowired private FremdKartenSchreiber schreiber;
  @Autowired private FremdKartenBestand kartenBestand;
  @Autowired private CardRepository cards;
  @Autowired private KartenSicht sicht;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;
  private long boardId;
  private long readyId;
  private long inProgressId;

  @BeforeEach
  void seed() {
    long userId =
        insert(
            "INSERT INTO app_user (email, display_name) VALUES ('a@example.com', 'A')"
                + " RETURNING id");
    projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    boardId =
        insert(
            "INSERT INTO board (project_id, name) VALUES ("
                + projectId
                + ", 'GitLab') RETURNING id");
    spalte("Backlog", 0);
    readyId = spalte("Ready", 1);
    inProgressId = spalte("In progress", 2);
    spalte("In review", 3);
    spalte("Done", 4);
    jdbc.update(
        "INSERT INTO fremdboard_verbindung (project_id, art, basis_url, projekt_pfad,"
            + " zugang_verschluesselt, kit_konto, statuslabels, board_id, angebunden_am)"
            + " VALUES (?, 'GITLAB', 'https://gitlab.example.org', 'gruppe/projekt', 'geheim',"
            + " 'kit-bot', '{\"ready\":\"Ready\"}'::jsonb, ?, ?)",
        projectId,
        boardId,
        java.sql.Timestamp.from(ANGEBUNDEN));
  }

  private long spalte(String name, int position) {
    return insert(
        "INSERT INTO board_column (board_id, name, position) VALUES ("
            + boardId
            + ", '"
            + name
            + "', "
            + position
            + ") RETURNING id");
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    return id == null ? 0L : id;
  }

  private long anlegen() {
    return schreiber.anlegen(
        boardId, new FremdeKarte(7, "Export als CSV", "Text", "READY", ADRESSE), ANGEBUNDEN, ERIKA);
  }

  @Test
  void legtDieKarteMitDerNummerDesFremdenSystemsAn() {
    // When
    long cardId = anlegen();

    // Then: Nummer, Spalte, Status und Adresse wie im fremden System, Eintritt zur Anbindung (A4).
    Card karte = cards.findById(cardId).orElseThrow();
    assertThat(karte.number()).isEqualTo(7);
    assertThat(karte.projectId()).isEqualTo(projectId);
    assertThat(karte.columnId()).isEqualTo(readyId);
    assertThat(karte.status()).isEqualTo(CardStatus.READY);
    assertThat(karte.title()).isEqualTo("Export als CSV");
    assertThat(karte.description()).isEqualTo("Text");
    assertThat(karte.externUrl()).isEqualTo(ADRESSE);
    assertThat(karte.createdAt()).isEqualTo(ANGEBUNDEN);
    assertThat(karte.createdBy()).isNull();
    assertThat(zeit("SELECT entered_at FROM card_column_transition WHERE card_id = ?", cardId))
        .isEqualTo(ANGEBUNDEN);
    assertThat(zeit("SELECT entered_at FROM card_flow_stay WHERE card_id = ?", cardId))
        .isEqualTo(ANGEBUNDEN);
    Map<String, Object> angelegt =
        jdbc.queryForMap(
            "SELECT type, origin, extern_akteur, actor_user_id FROM card_activity"
                + " WHERE card_id = ?",
            cardId);
    assertThat(angelegt)
        .containsEntry("type", "CREATED")
        .containsEntry("origin", "SESSION")
        .containsEntry("extern_akteur", "erika")
        .containsEntry("actor_user_id", null);
    // Die Karten-Antwort trägt die Adresse für „In GitLab öffnen“.
    assertThat(sicht.view(karte, false).externUrl()).isEqualTo(ADRESSE);
  }

  @Test
  void wechselTraegtDenZeitpunktUndDenAkteurDesFremdenSystems() {
    // Given
    long cardId = anlegen();

    // When
    schreiber.wechseln(boardId, 7, "IN_PROGRESS", WECHSEL, KIT);

    // Then: Spalte und Status folgen, Aufenthalte und Spaltenverlauf schneiden zur GitLab-Zeit.
    Card karte = cards.findById(cardId).orElseThrow();
    assertThat(karte.columnId()).isEqualTo(inProgressId);
    assertThat(karte.status()).isEqualTo(CardStatus.IN_PROGRESS);
    assertThat(
            jdbc.queryForList(
                "SELECT status, entered_at, left_at FROM card_flow_stay WHERE card_id = ?"
                    + " ORDER BY entered_at",
                cardId))
        .extracting(z -> z.get("status"), z -> instant(z.get("entered_at")))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("READY", ANGEBUNDEN),
            org.assertj.core.groups.Tuple.tuple("IN_PROGRESS", WECHSEL));
    assertThat(
            zeit(
                "SELECT left_at FROM card_flow_stay WHERE card_id = ? AND status = 'READY'",
                cardId))
        .isEqualTo(WECHSEL);
    assertThat(
            zeit(
                "SELECT entered_at FROM card_column_transition WHERE card_id = ?"
                    + " AND column_name = 'In progress'",
                cardId))
        .isEqualTo(WECHSEL);
    // Herkunft TOKEN beim Kit-Konto, der Benutzer im fremden System als externer Akteur (A11).
    Map<String, Object> bewegt =
        jdbc.queryForMap(
            "SELECT origin, extern_akteur, actor_user_id, status_after, created_at"
                + " FROM card_activity WHERE card_id = ? AND type = 'MOVED'",
            cardId);
    assertThat(bewegt)
        .containsEntry("origin", "TOKEN")
        .containsEntry("extern_akteur", "kit-bot")
        .containsEntry("actor_user_id", null)
        .containsEntry("status_after", "IN_PROGRESS");
    assertThat(instant(bewegt.get("created_at"))).isEqualTo(WECHSEL);
  }

  @Test
  void wechselInDieselbeSpalteHinterlaesstKeineSpur() {
    // Given
    long cardId = anlegen();

    // When
    boolean gewechselt = schreiber.wechseln(boardId, 7, "READY", WECHSEL, KIT);

    // Then
    assertThat(gewechselt).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM card_flow_stay WHERE card_id = ?", Integer.class, cardId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM card_activity WHERE card_id = ? AND type = 'MOVED'",
                Integer.class,
                cardId))
        .isZero();
  }

  @Test
  void labelSetzenUndEntfernenMitHerkunftUndExternemAkteur() {
    // Given
    long cardId = anlegen();

    // When
    schreiber.labelSetzen(boardId, 7, "kit:clarify", LABEL, ERIKA);
    schreiber.labelSetzen(boardId, 7, "kit:clarify", LABEL.plusSeconds(5), ERIKA);

    // Then: einmal verknüpft, ein Ereignis mit Herkunft SESSION und GitLab-Benutzer (A11).
    assertThat(labelsDerKarte(cardId)).containsExactly("kit:clarify");
    List<Map<String, Object>> ereignisse =
        jdbc.queryForList(
            "SELECT label_name, origin, extern_akteur, actor_user_id, created_at"
                + " FROM card_label_event WHERE card_id = ?",
            cardId);
    assertThat(ereignisse).hasSize(1);
    assertThat(ereignisse.getFirst())
        .containsEntry("label_name", "kit:clarify")
        .containsEntry("origin", "SESSION")
        .containsEntry("extern_akteur", "erika")
        .containsEntry("actor_user_id", null);
    assertThat(instant(ereignisse.getFirst().get("created_at"))).isEqualTo(LABEL);

    // When
    schreiber.labelEntfernen(boardId, 7, "kit:clarify");
    schreiber.labelEntfernen(boardId, 7, "unbekannt");

    // Then
    assertThat(labelsDerKarte(cardId)).isEmpty();
  }

  @Test
  void aktualisierenUebernimmtTitelTextUndAdresse() {
    // Given
    long cardId = anlegen();

    // When
    schreiber.aktualisieren(boardId, 7, "Export als CSV und JSON", null, ADRESSE + "#neu", WECHSEL);

    // Then
    Card karte = cards.findById(cardId).orElseThrow();
    assertThat(karte.title()).isEqualTo("Export als CSV und JSON");
    assertThat(karte.description()).isNull();
    assertThat(karte.externUrl()).isEqualTo(ADRESSE + "#neu");
  }

  @Test
  void geloeschtMarkiertBehaeltDieMessungen() {
    // Given
    long cardId = anlegen();
    schreiber.wechseln(boardId, 7, "IN_PROGRESS", WECHSEL, KIT);

    // When
    schreiber.alsGeloeschtMarkieren(boardId, 7, GELOESCHT);
    schreiber.alsGeloeschtMarkieren(boardId, 7, GELOESCHT.plusSeconds(60));

    // Then: im Papierkorb zur gemeldeten Zeit, Aufenthalte bleiben (A8).
    assertThat(zeit("SELECT deleted_at FROM card WHERE id = ?", cardId)).isEqualTo(GELOESCHT);
    assertThat(cards.findByProjectIdAndNumber(projectId, 7)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM card_flow_stay WHERE card_id = ?", Integer.class, cardId))
        .isEqualTo(2);
    assertThat(
            zeit(
                "SELECT left_at FROM card_flow_stay WHERE card_id = ? AND status = 'IN_PROGRESS'",
                cardId))
        .isEqualTo(GELOESCHT);
  }

  @Test
  @DisplayName("Spätere Karte tritt mit der Anlagezeit des fremden Systems ein")
  void spaetereKarteTrittMitDerAnlagezeitEin() {
    // When
    Instant angelegtInGitLab = Instant.parse("2026-11-05T07:00:00Z");
    long cardId =
        schreiber.anlegen(
            boardId,
            new FremdeKarte(8, "[Plan] Neuer Plan", "", "BACKLOG", null),
            angelegtInGitLab,
            FremderAkteur.von(null, "kit-bot"));

    // Then: Dokumentart ohne eigenen Status, Spalte aus dem Status; kein Akteur erfasst.
    Card karte = cards.findById(cardId).orElseThrow();
    assertThat(karte.createdAt()).isEqualTo(angelegtInGitLab);
    assertThat(karte.status()).isNull();
    assertThat(karte.description()).isNull();
    assertThat(
            jdbc.queryForMap(
                "SELECT origin, extern_akteur FROM card_activity WHERE card_id = ?", cardId))
        .containsEntry("origin", "SESSION")
        .containsEntry("extern_akteur", null);
  }

  @Test
  void schreibtNichtInEinProjektOhneFremdesBoard() {
    // Given
    jdbc.update("DELETE FROM fremdboard_verbindung WHERE project_id = ?", projectId);

    // When / Then: Der Weg umgeht die Schreibsperre nur für den Spiegel (A10).
    assertThatThrownBy(this::anlegen).isInstanceOf(IllegalStateException.class);
    assertThat(cards.findByProjectIdAndNumber(projectId, 7)).isEmpty();
  }

  @Test
  void eineVergebeneNummerWirdNichtZweimalAngelegt() {
    // Given
    anlegen();

    // When / Then
    assertThatThrownBy(this::anlegen)
        .isInstanceOf(org.mwolff.manban.card.application.CardNumberConflictException.class);
  }

  @Test
  void bestandZaehltJedeZeileDesProjektsNachIhremOrt() {
    // Given: ein Projekt ohne fremdes Board — dort fragt das Anbinden (Issue #2166, A2).
    jdbc.update("DELETE FROM fremdboard_verbindung WHERE project_id = ?", projectId);
    assertThat(kartenBestand.bestand(projectId).leer()).isTrue();
    karte(1, "Aktiv", "CARD", false, false);
    karte(2, "[Idea] Pool-Idee", "CARD", false, false);
    karte(3, "Archiviert", "CARD", true, false);
    karte(4, "Weggeworfen", "CARD", false, true);
    karte(5, "Vorhaben", "EPIC", false, false);
    karte(6, "Weggeworfenes Vorhaben", "EPIC", false, true);

    // When
    FremdKartenBestand.KartenBestand bestand = kartenBestand.bestand(projectId);

    // Then
    assertThat(bestand).isEqualTo(new FremdKartenBestand.KartenBestand(2, 1, 2, 1));
    assertThat(bestand.leer()).isFalse();
  }

  @Test
  void fehlendeStatusNenntDieStatusOhneSpalte() {
    // Given: die fünf Standardspalten aus seed()
    assertThat(kartenBestand.fehlendeStatus(boardId)).isEmpty();

    // When: zwei Spalten tragen keinen Statusnamen mehr
    jdbc.update(
        "UPDATE board_column SET name = 'Erledigt' WHERE board_id = ? AND name = 'Done'", boardId);
    jdbc.update(
        "UPDATE board_column SET name = 'Prüfen' WHERE board_id = ? AND name = 'In review'",
        boardId);

    // Then
    assertThat(kartenBestand.fehlendeStatus(boardId)).containsExactly("In review", "Done");
  }

  private void karte(int nummer, String titel, String typ, boolean archiviert, boolean geloescht) {
    jdbc.update(
        "INSERT INTO card (project_id, board_id, column_id, number, title, position_in_column,"
            + " type, archived, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        projectId,
        boardId,
        readyId,
        nummer,
        titel,
        nummer,
        typ,
        archiviert,
        geloescht ? java.sql.Timestamp.from(GELOESCHT) : null);
  }

  private List<String> labelsDerKarte(long cardId) {
    return jdbc.queryForList(
        "SELECT l.name FROM card_label cl JOIN label l ON l.id = cl.label_id WHERE cl.card_id = ?",
        String.class,
        cardId);
  }

  private Instant zeit(String sql, long id) {
    return jdbc.queryForObject(sql, OffsetDateTime.class, id).toInstant();
  }

  private static Instant instant(Object wert) {
    return wert instanceof java.sql.Timestamp ts
        ? ts.toInstant()
        : ((OffsetDateTime) wert).toInstant();
  }
}
