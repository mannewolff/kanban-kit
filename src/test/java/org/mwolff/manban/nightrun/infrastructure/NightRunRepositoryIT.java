package org.mwolff.manban.nightrun.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Adapter-Test der Nachtlauf-Persistenz gegen Postgres (Issue #721).
 *
 * <p>Belegt die Zusagen, die nur die echte Datenbank einlösen kann: die Duplikatserkennung über
 * {@code ON CONFLICT (project_id, started_at) DO NOTHING RETURNING id}, beide Richtungen des {@code
 * ON DELETE CASCADE}, die Auszugsgrenze aus {@link NightRunLimits#EXCERPT_MAX} — die JaCoCo als
 * Spaltenzusicherung nicht misst — und die Verdrängung des Ringpuffers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NightRunRepositoryIT extends AbstractIntegrationTest {

  private static final Instant T1 = Instant.parse("2026-09-01T22:00:00Z");
  private static final Instant T2 = Instant.parse("2026-09-02T22:00:00Z");
  private static final Instant T3 = Instant.parse("2026-09-03T22:00:00Z");
  private static final Instant ANGELEGT = Instant.parse("2026-09-04T06:00:00Z");

  @Autowired private NightRunRepository runs;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;

  @BeforeEach
  void seed() {
    long userId =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('a@example.com', 'x', 'A') RETURNING id");
    projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    return id == null ? 0L : id;
  }

  private NightRun lauf(Instant startedAt) {
    return new NightRun(
        null,
        projectId,
        startedAt,
        NightRunMode.IMPLEMENTATION,
        3_600_000L,
        2,
        1,
        0,
        null,
        ANGELEGT);
  }

  private static NightRunItem paket(int cardNumber, NightRunState state) {
    return new NightRunItem(
        null,
        null,
        cardNumber,
        "Paket " + cardNumber,
        state,
        state == NightRunState.GREEN ? null : NightRunErrorClass.CHECKS_RED,
        state == NightRunState.GREY ? null : 60_000L,
        state == NightRunState.GREEN ? "4c9f42a" : null,
        "  #" + cardNumber + " -> " + state);
  }

  private long anlegen(Instant startedAt, List<NightRunItem> items) {
    return runs.insertIfAbsent(lauf(startedAt), items).orElseThrow();
  }

  // --- Anlegen und Lesen ---------------------------------------------------------------------

  @Test
  void insertIfAbsentLegtDenLaufAnUndLiefertSeineId() {
    long id = anlegen(T1, List.of(paket(721, NightRunState.GREEN)));

    assertThat(runs.findByProjectOrderByStartedAtDesc(projectId))
        .extracting(NightRun::id, NightRun::startedAt, NightRun::mode, NightRun::processedCount)
        .containsExactly(tuple(id, T1, NightRunMode.IMPLEMENTATION, 2));
  }

  @Test
  void insertIfAbsentSchreibtDieItemsMitDerVergebenenLaufId() {
    long id = anlegen(T1, List.of(paket(721, NightRunState.GREEN), paket(722, NightRunState.GREY)));

    assertThat(runs.findItemsByRunIds(List.of(id)))
        .extracting(NightRunItem::nightRunId, NightRunItem::cardNumber, NightRunItem::state)
        .containsExactly(tuple(id, 721, NightRunState.GREEN), tuple(id, 722, NightRunState.GREY));
  }

  @Test
  void gespeicherteItemsTragenJedesOptionaleFeldZurueck() {
    long id = anlegen(T1, List.of(paket(721, NightRunState.GREEN)));

    assertThat(runs.findItemsByRunIds(List.of(id)))
        .singleElement()
        .extracting(
            NightRunItem::title,
            NightRunItem::errorClass,
            NightRunItem::durationMs,
            NightRunItem::commitHash,
            NightRunItem::excerpt)
        .containsExactly("Paket 721", null, 60_000L, "4c9f42a", "  #721 -> GREEN");
  }

  @Test
  void findByProjectOrderByStartedAtDescLiefertDenJuengstenLaufZuerst() {
    anlegen(T1, List.of());
    anlegen(T3, List.of());
    anlegen(T2, List.of());

    assertThat(runs.findByProjectOrderByStartedAtDesc(projectId))
        .extracting(NightRun::startedAt)
        .containsExactly(T3, T2, T1);
  }

  @Test
  void findByProjectOrderByStartedAtDescKenntNurDasEigeneProjekt() {
    anlegen(T1, List.of());

    assertThat(runs.findByProjectOrderByStartedAtDesc(projectId + 999)).isEmpty();
  }

  @Test
  void findItemsByRunIdsFragtBeiLeererEingabeNichtNach() {
    anlegen(T1, List.of(paket(721, NightRunState.GREEN)));

    assertThat(runs.findItemsByRunIds(List.of())).isEmpty();
  }

  // --- Duplikatserkennung (ON CONFLICT DO NOTHING RETURNING id) -------------------------------

  @Test
  void einZweiterLaufMitGleichemStartZeitpunktLiefertEinLeeresOptional() {
    anlegen(T1, List.of(paket(721, NightRunState.GREEN)));

    assertThat(runs.insertIfAbsent(lauf(T1), List.of(paket(999, NightRunState.RED)))).isEmpty();
  }

  @Test
  void beiEinemDuplikatBleibenDieItemsDesVorhandenenLaufsUnveraendert() {
    long id = anlegen(T1, List.of(paket(721, NightRunState.GREEN)));

    runs.insertIfAbsent(lauf(T1), List.of(paket(999, NightRunState.RED)));

    assertThat(runs.findItemsByRunIds(List.of(id)))
        .extracting(NightRunItem::cardNumber)
        .containsExactly(721);
  }

  @Test
  void derselbeStartZeitpunktInEinemAnderenProjektIstKeinDuplikat() {
    anlegen(T1, List.of());
    long andererUser =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('b@example.com', 'x', 'B') RETURNING id");
    long anderesProjekt =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('Q', "
                + andererUser
                + ") RETURNING id");

    NightRun fremd =
        new NightRun(
            null, anderesProjekt, T1, NightRunMode.REVIEW, 1_000L, 0, 0, 0, null, ANGELEGT);

    assertThat(runs.insertIfAbsent(fremd, List.of())).isPresent();
  }

  // --- ON DELETE CASCADE in beide Richtungen --------------------------------------------------

  @Test
  void dasLoeschenDesProjektsEntferntLaeufeUndItems() {
    anlegen(T1, List.of(paket(721, NightRunState.GREEN)));

    jdbc.update("DELETE FROM project WHERE id = ?", projectId);

    assertThat(zeilen("night_run")).isZero();
    assertThat(zeilen("night_run_item")).isZero();
  }

  @Test
  void dasLoeschenEinesLaufsEntferntSeineItems() {
    long id = anlegen(T1, List.of(paket(721, NightRunState.GREEN)));

    jdbc.update("DELETE FROM night_run WHERE id = ?", id);

    assertThat(zeilen("night_run_item")).isZero();
  }

  private long zeilen(String tabelle) {
    Long anzahl = jdbc.queryForObject("SELECT count(*) FROM " + tabelle, Long.class);
    return anzahl == null ? 0L : anzahl;
  }

  // --- Auszugsgrenze (Plan #718, A16) ---------------------------------------------------------

  @Test
  void einAuszugAnDerGrenzeWirdAngenommen_einerDarueberAbgewiesen() {
    String anDerGrenze = "x".repeat(NightRunLimits.EXCERPT_MAX);
    String darueber = "x".repeat(NightRunLimits.EXCERPT_MAX + 1);

    assertThatCode(() -> runs.insertIfAbsent(lauf(T1), List.of(mitAuszug(anDerGrenze))))
        .doesNotThrowAnyException();

    assertThatThrownBy(() -> runs.insertIfAbsent(lauf(T2), List.of(mitAuszug(darueber))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void einUnparsedSampleAnDerGrenzeWirdAngenommen_einesDarueberAbgewiesen() {
    assertThatCode(() -> runs.insertIfAbsent(mitProbe(T1, NightRunLimits.EXCERPT_MAX), List.of()))
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () -> runs.insertIfAbsent(mitProbe(T2, NightRunLimits.EXCERPT_MAX + 1), List.of()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static NightRunItem mitAuszug(String excerpt) {
    return new NightRunItem(
        null, null, 721, "Paket", NightRunState.GREEN, null, null, null, excerpt);
  }

  private NightRun mitProbe(Instant startedAt, int laenge) {
    return new NightRun(
        null,
        projectId,
        startedAt,
        NightRunMode.IMPLEMENTATION,
        1_000L,
        0,
        0,
        1,
        "y".repeat(laenge),
        ANGELEGT);
  }

  // --- Korrelation Zustand <-> Fehlerklasse ---------------------------------------------------

  @Test
  void gruenTraegtKeineFehlerklasse_jederAndereZustandGenauEineDerSieben() {
    long id =
        anlegen(
            T1,
            List.of(
                paket(1, NightRunState.GREEN),
                paket(2, NightRunState.YELLOW),
                paket(3, NightRunState.RED),
                paket(4, NightRunState.GREY)));

    List<NightRunItem> gespeichert = runs.findItemsByRunIds(List.of(id));

    assertThat(gespeichert)
        .filteredOn(item -> item.state() == NightRunState.GREEN)
        .allSatisfy(item -> assertThat(item.errorClass()).isNull());
    assertThat(gespeichert)
        .filteredOn(item -> item.state() != NightRunState.GREEN)
        .isNotEmpty()
        .allSatisfy(
            item -> assertThat(item.errorClass()).isIn(Arrays.asList(NightRunErrorClass.values())));
  }

  // --- Ringpuffer und Zählung -----------------------------------------------------------------

  @Test
  void deleteOlderThanNewestBehaeltDieJuengstenLaeufeUndMeldetDieVerdraengten() {
    anlegen(T1, List.of(paket(721, NightRunState.RED)));
    anlegen(T2, List.of());
    anlegen(T3, List.of());

    assertThat(runs.deleteOlderThanNewest(projectId, 2)).isEqualTo(1);
    assertThat(runs.findByProjectOrderByStartedAtDesc(projectId))
        .extracting(NightRun::startedAt)
        .containsExactly(T3, T2);
  }

  @Test
  void deleteOlderThanNewestLaesstAndereProjekteUnberuehrt() {
    anlegen(T1, List.of());

    assertThat(runs.deleteOlderThanNewest(projectId + 999, 0)).isZero();
    assertThat(runs.findByProjectOrderByStartedAtDesc(projectId)).hasSize(1);
  }

  @Test
  void countRunsByErrorClassZaehltJedenLaufJeKlasseHoechstensEinmal() {
    anlegen(
        T1,
        List.of(
            mitKlasse(1, NightRunErrorClass.CHECKS_RED),
            mitKlasse(2, NightRunErrorClass.CHECKS_RED),
            mitKlasse(3, NightRunErrorClass.HARD_ABORT)));
    anlegen(T2, List.of(mitKlasse(4, NightRunErrorClass.CHECKS_RED)));

    assertThat(runs.countRunsByErrorClass(projectId))
        .containsOnly(
            entry(NightRunErrorClass.CHECKS_RED, 2L), entry(NightRunErrorClass.HARD_ABORT, 1L));
  }

  @Test
  void countRunsByErrorClassLaesstVerdraengteLaeufeUndGrueneItemsAusserAcht() {
    anlegen(T1, List.of(mitKlasse(1, NightRunErrorClass.CHECKS_RED)));
    anlegen(T2, List.of(paket(2, NightRunState.GREEN)));
    runs.deleteOlderThanNewest(projectId, 1);

    assertThat(runs.countRunsByErrorClass(projectId)).isEmpty();
  }

  @Test
  void countRunsByErrorClassKenntNurDasEigeneProjekt() {
    anlegen(T1, List.of(mitKlasse(1, NightRunErrorClass.CHECKS_RED)));

    assertThat(runs.countRunsByErrorClass(projectId + 999)).isEqualTo(Map.of());
  }

  private static NightRunItem mitKlasse(int cardNumber, NightRunErrorClass errorClass) {
    return new NightRunItem(
        null,
        null,
        cardNumber,
        "Paket " + cardNumber,
        NightRunState.RED,
        errorClass,
        null,
        null,
        null);
  }
}
