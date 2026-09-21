package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.nightrun.application.DisruptionRepository;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Abfrage des Plattform-Leitstands und die Lebensdauer einer Quittung (Issue #1080).
 *
 * <p>Gegen die echte Datenbank, weil hier nur sie etwas belegt: Welche Läufe die Abfrage aufnimmt,
 * entscheidet ein {@code LEFT JOIN … IS NULL} über vier Tabellen, und was mit einer Quittung
 * geschieht, wenn ihr Lauf verdrängt oder ihr Projekt gelöscht wird, entscheiden die Fremdschlüssel
 * — nicht der Code. Plan #1072 E1 hängt genau daran: Weil die Störung aus den Läufen
 * <em>abgeleitet</em> wird, braucht AK 12, 18 und 19 keine eigene Regel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DisruptionRepositoryIT extends AbstractIntegrationTest {

  private static final Instant T1 = Instant.parse("2026-09-18T22:00:00Z");
  private static final Instant T2 = Instant.parse("2026-09-19T22:00:00Z");

  /** Letzte Meldung eines Laufs — was {@code night_run.updated_at} trägt. */
  private static final Instant LETZTE_MELDUNG = Instant.parse("2026-09-19T23:30:00Z");

  @Autowired private DisruptionRepository disruptions;
  @Autowired private JdbcTemplate jdbc;

  private long userId;
  private long projectId;

  private long id(String sql, Object... args) {
    Long wert = jdbc.queryForObject(sql, Long.class, args);
    return wert == null ? 0L : wert;
  }

  @BeforeEach
  void seed() {
    userId =
        id(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('stoerung@example.com', 'x', 'S') RETURNING id");
    projectId =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", userId);
  }

  private long lauf(Instant startedAt, String kind, boolean complete) {
    return id(
        "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms, processed_count,"
            + " skipped_count, unparsed_count, created_at, origin, complete)"
            + " VALUES (?, ?, 'IMPLEMENTATION', ?, 1, 1, 0, 0, now(), 'UPLOAD', ?) RETURNING id",
        projectId,
        OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC),
        kind,
        complete);
  }

  private void teilnahme(boolean teilnehmend) {
    jdbc.update(
        "UPDATE project SET dashboard_participation = ? WHERE id = ?", teilnehmend, projectId);
  }

  private void laufart(long laufId, String mode) {
    jdbc.update("UPDATE night_run SET mode = ? WHERE id = ?", mode, laufId);
  }

  private void letzteMeldung(long laufId, Instant at) {
    jdbc.update(
        "UPDATE night_run SET updated_at = ? WHERE id = ?",
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
        laufId);
  }

  // --- Was die Abfrage aufnimmt --------------------------------------------------------------

  /** AK 17/19: Ohne Haken erscheint kein Lauf des Projekts. */
  @Test
  void einNichtTeilnehmendesProjektLiefertKeineKandidaten() {
    lauf(T1, "NIGHT", true);

    assertThat(disruptions.openCandidates()).isEmpty();
  }

  /** AK 18: Beim Anhaken erscheinen auch Läufe aus der Zeit davor. */
  @Test
  void dasAnhakenNimmtAuchAeltereLaeufeAuf() {
    long alt = lauf(T1, "NIGHT", true);

    teilnahme(true);

    assertThat(disruptions.openCandidates())
        .extracting(DisruptionRepository.DisruptionCandidate::nightRunId)
        .containsExactly(alt);
  }

  /** AK 19: Abhaken nimmt die Zeilen weg, erneutes Anhaken bringt die nicht quittierten zurück. */
  @Test
  void abhakenNimmtWeg_undWiederanhakenBringtDieNichtQuittiertenZurueck() {
    long behalten = lauf(T1, "NIGHT", true);
    long quittiert = lauf(T2, "NIGHT", true);
    teilnahme(true);
    disruptions.acknowledge(quittiert, userId, Instant.now());

    teilnahme(false);
    assertThat(disruptions.openCandidates()).isEmpty();

    teilnahme(true);
    assertThat(disruptions.openCandidates())
        .extracting(DisruptionRepository.DisruptionCandidate::nightRunId)
        .containsExactly(behalten);
  }

  /** Nicht-Ziel der fachlichen Quelle: Interaktive Sitzungen erzeugen keine Störungen. */
  @Test
  void eineInteraktiveSitzungIstKeinKandidat() {
    lauf(T1, "INTERACTIVE", true);
    teilnahme(true);

    assertThat(disruptions.openCandidates()).isEmpty();
  }

  /** Vorspann der fachlichen Kriterien: Ein laufender Lauf hat noch keinen Ausgang. */
  @Test
  void einUnabgeschlossenerLaufIstKeinKandidat() {
    lauf(T1, "NIGHT", false);
    teilnahme(true);

    assertThat(disruptions.openCandidates()).isEmpty();
  }

  /** AK 13: jüngster Lauf zuoberst. */
  @Test
  void dieKandidatenStehenJuengsterZuoberst() {
    long aelter = lauf(T1, "NIGHT", true);
    long juenger = lauf(T2, "NIGHT", true);
    teilnahme(true);

    assertThat(disruptions.openCandidates())
        .extracting(DisruptionRepository.DisruptionCandidate::nightRunId)
        .containsExactly(juenger, aelter);
  }

  @Test
  void derKandidatTraegtProjektnamenUndGrund() {
    jdbc.update("UPDATE project SET name = 'Mein Projekt' WHERE id = ?", projectId);
    long laufId = lauf(T1, "NIGHT", true);
    jdbc.update("UPDATE night_run SET no_work_reason = 'Ready war leer' WHERE id = ?", laufId);
    teilnahme(true);

    assertThat(disruptions.openCandidates())
        .singleElement()
        .satisfies(
            k -> {
              assertThat(k.projectName()).isEqualTo("Mein Projekt");
              assertThat(k.noWorkReason()).isEqualTo("Ready war leer");
              assertThat(k.startedAt()).isEqualTo(T1);
            });
  }

  /** Die Störungsliste liest die beiden neuen Felder mit; {@code complete} ist dort stets wahr. */
  @Test
  void derKandidatTraegtAbschlussUndLetzteMeldung() {
    long laufId = lauf(T1, "NIGHT", true);
    letzteMeldung(laufId, LETZTE_MELDUNG);
    teilnahme(true);

    assertThat(disruptions.openCandidates())
        .singleElement()
        .satisfies(
            k -> {
              assertThat(k.complete()).isTrue();
              assertThat(k.updatedAt()).isEqualTo(LETZTE_MELDUNG);
            });
  }

  /**
   * Issue #1123: Die Laufart entscheidet, welches von zwei gleichrangigen Paketen maßgeblich ist —
   * sie muss deshalb aus der Datenbank kommen und nicht aus einem Festwert im Adapter.
   */
  @Test
  void derKandidatTraegtDieLaufartAusDerDatenbank() {
    long laufId = lauf(T1, "NIGHT", true);
    laufart(laufId, "CHAIN");
    teilnahme(true);

    assertThat(disruptions.openCandidates())
        .singleElement()
        .extracting(DisruptionRepository.DisruptionCandidate::mode)
        .isEqualTo(NightRunMode.CHAIN);
  }

  // --- Quittieren ----------------------------------------------------------------------------

  /** AK 9: Die Quittung eines Nutzers wirkt für alle — sie hängt am Lauf, nicht am Nutzer. */
  @Test
  void eineQuittungNimmtDenLaufFuerAlleAusDerListe() {
    long laufId = lauf(T1, "NIGHT", true);
    teilnahme(true);

    disruptions.acknowledge(laufId, userId, Instant.now());

    assertThat(disruptions.openCandidates()).isEmpty();
  }

  @Test
  void dasZweiteQuittierenLaesstDenErstenVermerkStehen() {
    long laufId = lauf(T1, "NIGHT", true);
    teilnahme(true);
    long zweiter =
        id(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('zweiter@example.com', 'x', 'Z') RETURNING id");
    disruptions.acknowledge(laufId, userId, Instant.now());

    disruptions.acknowledge(laufId, zweiter, Instant.now());

    assertThat(
            jdbc.queryForObject(
                "SELECT acknowledged_by FROM night_run_disruption_ack WHERE night_run_id = ?",
                Long.class,
                laufId))
        .isEqualTo(userId);
  }

  @Test
  void dasAckZielKenntNurLaeufeTeilnehmenderProjekte() {
    long laufId = lauf(T1, "NIGHT", true);

    assertThat(disruptions.ackTarget(laufId)).isEmpty();

    teilnahme(true);
    assertThat(disruptions.ackTarget(laufId)).isPresent();
  }

  @Test
  void dasAckZielKenntKeinenUnbekanntenLauf() {
    teilnahme(true);

    assertThat(disruptions.ackTarget(999_999L)).isEmpty();
  }

  // --- Lebensdauer der Quittung (AK 12, Plan E23) ---------------------------------------------

  /** AK 12: Verschwindet der Lauf, verschwindet die Quittung — sonst bliebe sie verwaist. */
  @Test
  void dasLoeschenDesLaufsNimmtDieQuittungMit() {
    long laufId = lauf(T1, "NIGHT", true);
    teilnahme(true);
    disruptions.acknowledge(laufId, userId, Instant.now());

    jdbc.update("DELETE FROM night_run WHERE id = ?", laufId);

    assertThat(anzahlQuittungen()).isZero();
  }

  /** AK 12: Mit dem Projekt gehen seine Läufe und damit die Quittungen. */
  @Test
  void dasLoeschenDesProjektsNimmtDieQuittungMit() {
    long laufId = lauf(T1, "NIGHT", true);
    teilnahme(true);
    disruptions.acknowledge(laufId, userId, Instant.now());

    jdbc.update("DELETE FROM project WHERE id = ?", projectId);

    assertThat(anzahlQuittungen()).isZero();
  }

  /**
   * Plan E23: Wer quittiert hat, ist nachrangig. Die Quittung überlebt das Löschen des Nutzers —
   * sie sagt „gesehen", und das bleibt wahr, auch wenn der Sehende gegangen ist.
   */
  @Test
  void dasLoeschenDesNutzersLaesstDieQuittungStehen() {
    long laufId = lauf(T1, "NIGHT", true);
    teilnahme(true);
    long quittierer =
        id(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('geht@example.com', 'x', 'G') RETURNING id");
    disruptions.acknowledge(laufId, quittierer, Instant.now());

    jdbc.update("DELETE FROM app_user WHERE id = ?", quittierer);

    assertThat(anzahlQuittungen()).isEqualTo(1);
    assertThat(disruptions.openCandidates()).isEmpty();
  }

  private long anzahlQuittungen() {
    Long wert =
        jdbc.queryForObject(
            "SELECT count(*) FROM night_run_disruption_ack WHERE night_run_id IN"
                + " (SELECT id FROM night_run WHERE project_id = ?)",
            Long.class,
            projectId);
    return wert == null ? 0L : wert;
  }
}
