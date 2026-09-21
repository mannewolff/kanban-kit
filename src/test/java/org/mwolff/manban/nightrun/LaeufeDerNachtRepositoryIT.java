package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.nightrun.application.DisruptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Abfrage der Läufe einer Nacht (Issue #1094, Plan #1088 E4).
 *
 * <p>Gegen die echte Datenbank, weil hier nur sie etwas belegt: Welche Läufe die Abfrage aufnimmt,
 * entscheiden ihre {@code WHERE}-Bedingungen und ihre Sortierung — nicht der Code darum herum.
 *
 * <p>Eigene Klasse neben {@code DisruptionRepositoryIT}, obwohl beide denselben Port prüfen: Dort
 * steht die Störungsliste samt Lebensdauer der Quittung, hier die zweite Abfrage. Zusammen wäre es
 * eine Klasse, die zwei Zusagen nebeneinander trägt und beim Lesen nicht mehr verrät, welche Zeile
 * zu welcher gehört. Der eine Test, der beide vergleicht, steht deshalb hier — er ist die
 * Abgrenzung selbst.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LaeufeDerNachtRepositoryIT extends AbstractIntegrationTest {

  /** Die Spanne der laufenden Nacht. */
  private static final Instant NACHT_VON = Instant.parse("2026-09-19T18:00:00Z");

  private static final Instant NACHT_BIS = Instant.parse("2026-09-20T10:00:00Z");

  /** Ein Startzeitpunkt mitten in der Nacht. */
  private static final Instant DRIN = Instant.parse("2026-09-19T22:00:00Z");

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
                + " VALUES ('nacht@example.com', 'x', 'N') RETURNING id");
    projectId =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", userId);
  }

  private long lauf(Instant startedAt, String kind, boolean complete) {
    return lauf(projectId, startedAt, kind, complete);
  }

  private long lauf(long projekt, Instant startedAt, String kind, boolean complete) {
    return id(
        "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms, processed_count,"
            + " skipped_count, unparsed_count, created_at, origin, complete)"
            + " VALUES (?, ?, 'IMPLEMENTATION', ?, 1, 1, 0, 0, now(), 'UPLOAD', ?) RETURNING id",
        projekt,
        OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC),
        kind,
        complete);
  }

  private void teilnahme(boolean teilnehmend) {
    teilnahme(projectId, teilnehmend);
  }

  private void teilnahme(long projekt, boolean teilnehmend) {
    jdbc.update(
        "UPDATE project SET dashboard_participation = ? WHERE id = ?", teilnehmend, projekt);
  }

  private void letzteMeldung(long laufId, Instant at) {
    jdbc.update(
        "UPDATE night_run SET updated_at = ? WHERE id = ?",
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC),
        laufId);
  }

  private List<Long> nacht() {
    return disruptions.candidatesOfNight(NACHT_VON, NACHT_BIS).stream()
        .map(DisruptionRepository.DisruptionCandidate::nightRunId)
        .toList();
  }

  /** Kriterium 9: Der Startzeitpunkt entscheidet, {@code from} einschließlich, {@code to} nicht. */
  @Test
  void dieNachtNimmtNurLaeufeVonEinschliesslichBisAusschliesslich() {
    lauf(NACHT_VON.minusMillis(1), "NIGHT", true);
    long aufVon = lauf(NACHT_VON, "NIGHT", true);
    long drin = lauf(DRIN, "NIGHT", true);
    lauf(NACHT_BIS, "NIGHT", true);
    lauf(NACHT_BIS.plusSeconds(1), "NIGHT", true);
    teilnahme(true);

    assertThat(nacht()).containsExactly(drin, aufVon);
  }

  /** Kriterium 2: Ein laufender Lauf gehört in die Nacht — die Störungsliste kennt ihn nicht. */
  @Test
  void einUnabgeschlossenerLaufDerNachtErscheint() {
    long laufend = lauf(DRIN, "NIGHT", false);
    teilnahme(true);

    assertThat(nacht()).containsExactly(laufend);
    assertThat(disruptions.openCandidates()).isEmpty();
  }

  /** Kriterium 13: Das Quittieren ändert den Ausgang nicht — der Lauf bleibt in der Nacht. */
  @Test
  void einQuittierterLaufDerNachtErscheint() {
    long quittiert = lauf(DRIN, "NIGHT", true);
    teilnahme(true);
    disruptions.acknowledge(quittiert, userId, Instant.now());

    assertThat(nacht()).containsExactly(quittiert);
    assertThat(disruptions.openCandidates()).isEmpty();
  }

  /** Kriterium 15/16: weder interaktive Sitzungen noch Läufe abgehakter Projekte. */
  @Test
  void interaktiveSitzungenUndNichtTeilnehmendeProjekteFehlen() {
    lauf(DRIN, "INTERACTIVE", true);
    long nachtlauf = lauf(DRIN.plusSeconds(60), "NIGHT", true);
    teilnahme(true);

    assertThat(nacht()).containsExactly(nachtlauf);

    teilnahme(false);
    assertThat(nacht()).isEmpty();
  }

  /**
   * Kriterium 9: jüngster Startzeitpunkt zuoberst, bei Gleichstand die höhere Id.
   *
   * <p>Den Gleichstand gibt es nur über Projektgrenzen hinweg — innerhalb eines Projekts verbietet
   * {@code uq_night_run_project_started} zwei Läufe zur selben Zeit.
   */
  @Test
  void dieLaeufeDerNachtStehenJuengsterZuoberst() {
    long zweitesProjekt =
        id("INSERT INTO project (name, owner_user_id) VALUES ('Q', ?) RETURNING id", userId);
    long frueh = lauf(NACHT_VON, "NIGHT", true);
    long gleichNiedrigereId = lauf(DRIN, "NIGHT", true);
    long gleichHoehereId = lauf(zweitesProjekt, DRIN, "NIGHT", true);
    teilnahme(true);
    teilnahme(zweitesProjekt, true);

    assertThat(nacht()).containsExactly(gleichHoehereId, gleichNiedrigereId, frueh);
  }

  /** Beide Felder, die der Dienst für {@code NightRunOutcome.of} braucht — auch ihr Fehlen. */
  @Test
  void derKandidatDerNachtTraegtAbschlussUndLetzteMeldung() {
    long hochgeladen = lauf(DRIN, "NIGHT", true);
    long laufend = lauf(DRIN.plusSeconds(60), "NIGHT", false);
    letzteMeldung(laufend, LETZTE_MELDUNG);
    teilnahme(true);

    assertThat(disruptions.candidatesOfNight(NACHT_VON, NACHT_BIS))
        .satisfiesExactly(
            k -> {
              assertThat(k.nightRunId()).isEqualTo(laufend);
              assertThat(k.complete()).isFalse();
              assertThat(k.updatedAt()).isEqualTo(LETZTE_MELDUNG);
            },
            k -> {
              assertThat(k.nightRunId()).isEqualTo(hochgeladen);
              assertThat(k.complete()).isTrue();
              assertThat(k.updatedAt()).isNull();
            });
  }

  /** Dieselben Spalten wie die Störungsliste — Projekt, Startzeitpunkt, Grund. */
  @Test
  void derKandidatDerNachtTraegtProjektnamenUndGrund() {
    jdbc.update("UPDATE project SET name = 'Mein Projekt' WHERE id = ?", projectId);
    long laufId = lauf(DRIN, "NIGHT", true);
    jdbc.update("UPDATE night_run SET no_work_reason = 'Ready war leer' WHERE id = ?", laufId);
    teilnahme(true);

    assertThat(disruptions.candidatesOfNight(NACHT_VON, NACHT_BIS))
        .singleElement()
        .satisfies(
            k -> {
              assertThat(k.projectId()).isEqualTo(projectId);
              assertThat(k.projectName()).isEqualTo("Mein Projekt");
              assertThat(k.startedAt()).isEqualTo(DRIN);
              assertThat(k.noWorkReason()).isEqualTo("Ready war leer");
            });
  }

  /**
   * Kriterium 17: Die Störungsliste behält ihr Verhalten. Von denselben drei Läufen nimmt sie nur
   * den abgeschlossenen, nicht quittierten — die Nacht nimmt alle drei.
   */
  @Test
  void dieStoerungslisteBleibtBeiDenAbgeschlossenenUndNichtQuittierten() {
    long offen = lauf(DRIN, "NIGHT", true);
    long laufend = lauf(DRIN.plusSeconds(60), "NIGHT", false);
    long quittiert = lauf(DRIN.plusSeconds(120), "NIGHT", true);
    teilnahme(true);
    disruptions.acknowledge(quittiert, userId, Instant.now());

    assertThat(disruptions.openCandidates())
        .extracting(DisruptionRepository.DisruptionCandidate::nightRunId)
        .containsExactly(offen);
    assertThat(nacht()).containsExactlyInAnyOrder(offen, laufend, quittiert);
  }
}
