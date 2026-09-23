package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.nightrun.application.DisruptionRepository;
import org.mwolff.manban.nightrun.domain.NightRunMode;
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

  /** Bezugszeitpunkt der Abfrage; er liegt wie in der Wirklichkeit innerhalb der Nacht. */
  private static final Instant JETZT = Instant.parse("2026-09-20T09:00:00Z");

  /** Die Stille, die ein unfertiger Lauf sich erlauben darf. */
  private static final Duration STILLE_FRIST = Duration.ofMinutes(90);

  /** Ein Lebenszeichen genau auf der Frist — der Lauf gilt damit noch als lebendig. */
  private static final Instant AUF_DER_FRIST = JETZT.minus(STILLE_FRIST);

  /** Ein Startzeitpunkt vor der Nacht — ein Lauf, der zur Nacht davor gehört. */
  private static final Instant VOR_DER_NACHT = NACHT_VON.minus(Duration.ofHours(2));

  /** Ein frisches Lebenszeichen: deutlich innerhalb der Frist. */
  private static final Instant FRISCH = JETZT.minus(Duration.ofMinutes(10));

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

  private List<DisruptionRepository.DisruptionCandidate> kandidaten() {
    return disruptions.candidatesOfNight(NACHT_VON, NACHT_BIS, JETZT, STILLE_FRIST);
  }

  private List<Long> nacht() {
    return ids(kandidaten());
  }

  private static List<Long> ids(List<DisruptionRepository.DisruptionCandidate> kandidaten) {
    return kandidaten.stream().map(DisruptionRepository.DisruptionCandidate::nightRunId).toList();
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

    assertThat(kandidaten())
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

  /**
   * Issue #1123: Auch diese Abfrage muss die Laufart mitbringen — sie entscheidet, welches von zwei
   * gleichrangigen Paketen maßgeblich ist. Beide Abfragen teilen sich einen {@code RowMapper}, aber
   * nicht ihre Spaltenliste.
   */
  @Test
  void derKandidatDerNachtTraegtDieLaufartAusDerDatenbank() {
    long laufId = lauf(DRIN, "NIGHT", true);
    jdbc.update("UPDATE night_run SET mode = 'CHAIN' WHERE id = ?", laufId);
    teilnahme(true);

    assertThat(kandidaten())
        .singleElement()
        .extracting(DisruptionRepository.DisruptionCandidate::mode)
        .isEqualTo(NightRunMode.CHAIN);
  }

  /** Dieselben Spalten wie die Störungsliste — Projekt, Startzeitpunkt, Grund. */
  @Test
  void derKandidatDerNachtTraegtProjektnamenUndGrund() {
    jdbc.update("UPDATE project SET name = 'Mein Projekt' WHERE id = ?", projectId);
    long laufId = lauf(DRIN, "NIGHT", true);
    jdbc.update("UPDATE night_run SET no_work_reason = 'Ready war leer' WHERE id = ?", laufId);
    teilnahme(true);

    assertThat(kandidaten())
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
   * Issue #1143: Auch der Abbruchgrund muss aus <b>dieser</b> Abfrage kommen — beide Abfragen
   * teilen sich einen {@code RowMapper}, aber nicht ihre Spaltenliste. Fehlte die Spalte hier,
   * stünde derselbe abgebrochene Lauf unter den durchgeführten Läufen als gelungen und in der
   * Störungsliste als gescheitert (AK 8 der fachlichen Quelle #1074).
   */
  @Test
  void derKandidatDerNachtTraegtDenAbbruchgrund() {
    long laufId = lauf(DRIN, "NIGHT", true);
    jdbc.update(
        "UPDATE night_run SET abort_reason = 'Dirty-Guard: uncommittete Reste' WHERE id = ?",
        laufId);
    teilnahme(true);

    assertThat(kandidaten())
        .singleElement()
        .extracting(DisruptionRepository.DisruptionCandidate::abortReason)
        .isEqualTo("Dirty-Guard: uncommittete Reste");
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

  // --- Der zweite Zweig: was noch arbeitet, gleich wann es begann (Issue #1109) ---------------

  /**
   * Kriterium 1: Ein Lauf, der vor der Nacht begann und noch arbeitet, kommt über den zweiten Zweig
   * herein. Über den ersten käme er nie — sein Start liegt außerhalb der Spanne.
   */
  @Test
  void einLaufVonVorDerNachtMitFrischemLebenszeichenErscheint() {
    long laufend = lauf(VOR_DER_NACHT, "NIGHT", false);
    letzteMeldung(laufend, FRISCH);
    teilnahme(true);

    assertThat(nacht()).containsExactly(laufend);
  }

  /**
   * Kriterium 3: Derselbe Lauf, dessen Stille die Frist überschreitet, bleibt draußen — er gehört
   * zu einer Nacht, die vorbei ist, und die Auswertung sähe ihn als verstummt.
   */
  @Test
  void einVerstummterLaufVonVorDerNachtFehlt() {
    long verstummt = lauf(VOR_DER_NACHT, "NIGHT", false);
    letzteMeldung(verstummt, AUF_DER_FRIST.minusMillis(1));
    teilnahme(true);

    assertThat(nacht()).isEmpty();
  }

  /**
   * Der Rand der Frist liegt gleich wie in {@code NightRunOutcome}: genau <b>auf</b> der Frist lebt
   * der Lauf noch, erst darüber ist er still. Zöge die Abfrage ihn hier schon ab, verschwände ein
   * Lauf vom Leitstand, den die Auswertung des Projekts noch als laufend zeigt (#1086 AK 8).
   */
  @Test
  void aufDerStillefristGiltDerLaufNochAlsLebendig() {
    long amRand = lauf(VOR_DER_NACHT, "NIGHT", false);
    letzteMeldung(amRand, AUF_DER_FRIST);
    teilnahme(true);

    assertThat(nacht()).containsExactly(amRand);
  }

  /**
   * Ohne {@code updated_at} zählt der Start als Lebenszeichen — dieselbe Rückfallregel, die {@code
   * NightRunOutcome} für den Upload-Weg trägt. Die Nacht ist hier eng gezogen und enthält keinen
   * der beiden Läufe, damit allein der zweite Zweig entscheidet.
   */
  @Test
  void ohneLetzteMeldungIstDerStartDasLebenszeichen() {
    long frischGestartet = lauf(FRISCH, "NIGHT", false);
    long laengstStill = lauf(AUF_DER_FRIST.minusSeconds(1), "NIGHT", false);
    teilnahme(true);

    assertThat(ids(disruptions.candidatesOfNight(JETZT, NACHT_BIS, JETZT, STILLE_FRIST)))
        .containsExactly(frischGestartet)
        .doesNotContain(laengstStill);
  }

  /**
   * Der zweite Zweig gilt nur unfertigen Läufen: Ein abgeschlossener Lauf einer früheren Nacht
   * bleibt draußen, auch wenn seine letzte Meldung frisch ist. Sonst hielte sich jeder eben
   * beendete Lauf der alten Nacht noch die Stillefrist lang auf dem Leitstand (#1086 AK 9).
   */
  @Test
  void einAbgeschlossenerLaufVonVorDerNachtFehltTrotzFrischerMeldung() {
    long beendet = lauf(VOR_DER_NACHT, "NIGHT", true);
    letzteMeldung(beendet, FRISCH);
    teilnahme(true);

    assertThat(nacht()).isEmpty();
  }

  /**
   * Kriterium 5: Ein Lauf, der <b>beide</b> Zweige trifft — Start in der Nacht und frisches
   * Lebenszeichen —, steht genau einmal in der Antwort. Das leistet das {@code OR} in einer
   * Abfrage; eine zweite, angehängte Abfrage lieferte ihn zweimal.
   */
  @Test
  void einLaufDerBeideZweigeTrifftStehtGenauEinmal() {
    long doppelt = lauf(DRIN, "NIGHT", false);
    letzteMeldung(doppelt, FRISCH);
    teilnahme(true);

    assertThat(nacht()).containsExactly(doppelt);
  }

  /**
   * Kriterium 6: Gattung und Teilnahme gelten für <b>beide</b> Zweige. Eine falsch geklammerte
   * Bedingung ließe einen laufenden Lauf an ihnen vorbei — eine interaktive Sitzung oder ein Lauf
   * eines abgehakten Projekts stünde auf dem Plattform-Leitstand.
   */
  @Test
  void derZweiteZweigLaesstWederInteraktiveSitzungenNochFremdeProjekteDurch() {
    long interaktiv = lauf(VOR_DER_NACHT, "INTERACTIVE", false);
    long nachtlauf = lauf(VOR_DER_NACHT.plusSeconds(60), "NIGHT", false);
    letzteMeldung(interaktiv, FRISCH);
    letzteMeldung(nachtlauf, FRISCH);
    teilnahme(true);

    assertThat(nacht()).containsExactly(nachtlauf);

    teilnahme(false);
    assertThat(nacht()).isEmpty();
  }
}
