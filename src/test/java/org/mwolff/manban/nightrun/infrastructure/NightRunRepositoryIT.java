package org.mwolff.manban.nightrun.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.application.NightRunRepository.UpsertResult;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Adapter-Test der Nachtlauf-Persistenz gegen Postgres (Issue #721).
 *
 * <p>Belegt die Zusagen, die nur die echte Datenbank einlösen kann: die Duplikatserkennung über
 * {@code ON CONFLICT (project_id, started_at) DO NOTHING RETURNING id}, das Loeschverhalten an Lauf
 * und Projekt, die Auszugsgrenze aus {@link NightRunLimits#EXCERPT_MAX} — die JaCoCo als
 * Spaltenzusicherung nicht misst — und die Verdrängung des Ringpuffers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
// Testklasse: Jede Methode ist ein Fall, und Faelle werden nicht zusammengelegt, um eine
// Zahl zu druecken. Issue #944 bringt drei Faelle fuer Herkunft, Vollstaendigkeit und
// Verbrauch dazu, Issue #964 drei fuer das verwaiste Arbeitspaket, Issue #965 zwei fuer dessen
// Wiedererkennung.
@SuppressWarnings("PMD.TooManyMethods")
class NightRunRepositoryIT extends AbstractIntegrationTest {

  private static final Instant T1 = Instant.parse("2026-09-01T22:00:00Z");
  private static final Instant T2 = Instant.parse("2026-09-02T22:00:00Z");
  private static final Instant T3 = Instant.parse("2026-09-03T22:00:00Z");
  private static final Instant ANGELEGT = Instant.parse("2026-09-04T06:00:00Z");

  /**
   * Absichtlich falsche Werte an den einzuliefernden Paketen (Issue #964, um die Gattung erweitert
   * in #1010): Projekt, Startzeitpunkt, Lauf-Art und Gattung eines Pakets schreibt der Adapter aus
   * dem Lauf, zu dem es gehoert, und nie aus dem Paket. Stuenden hier die Werte des Laufs, bewiese
   * kein Test, woher der Adapter sie nimmt.
   */
  private static final long PLATZHALTER_PROJEKT = -1L;

  private static final Instant PLATZHALTER_START = Instant.EPOCH;
  private static final NightRunMode PLATZHALTER_MODUS = NightRunMode.REVIEW;
  private static final NightRunKind PLATZHALTER_GATTUNG = NightRunKind.INTERACTIVE;

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
    return lauf(startedAt, NightRunKind.NIGHT);
  }

  /** Derselbe Lauf in der genannten Gattung — die Verdrängung kappt je Gattung (Issue #1011). */
  private NightRun lauf(Instant startedAt, NightRunKind kind) {
    return new NightRun(
        null,
        projectId,
        startedAt,
        kind == NightRunKind.INTERACTIVE ? NightRunMode.INTERACTIVE : NightRunMode.IMPLEMENTATION,
        kind,
        3_600_000L,
        2,
        1,
        0,
        null,
        ANGELEGT,
        NightRunOrigin.UPLOAD,
        null,
        true,
        null,
        null);
  }

  private static NightRunItem paket(int cardNumber, NightRunState state) {
    return new NightRunItem(
        null,
        null,
        PLATZHALTER_PROJEKT,
        PLATZHALTER_START,
        PLATZHALTER_MODUS,
        PLATZHALTER_GATTUNG,
        cardNumber,
        "Paket " + cardNumber,
        state,
        state == NightRunState.GREEN ? null : NightRunErrorClass.CHECKS_RED,
        state == NightRunState.GREY ? null : 60_000L,
        state == NightRunState.GREEN ? "4c9f42a" : null,
        "  #" + cardNumber + " -> " + state,
        null);
  }

  private long anlegen(Instant startedAt, List<NightRunItem> items) {
    return runs.insertIfAbsent(lauf(startedAt), items).orElseThrow();
  }

  private long anlegen(Instant startedAt, NightRunKind kind, List<NightRunItem> items) {
    return runs.insertIfAbsent(lauf(startedAt, kind), items).orElseThrow();
  }

  // --- Anlegen und Lesen ---------------------------------------------------------------------

  @Test
  void insertIfAbsentLegtDenLaufAnUndLiefertSeineId() {
    long id = anlegen(T1, List.of(paket(721, NightRunState.GREEN)));

    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT))
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

  /**
   * Belegt, dass {@code ck_night_run_mode} und {@code ck_night_run_item_error_class} nach {@code
   * V30__night_run_kette.sql} die beiden neuen Werte tatsächlich zulassen (Issue #853). Nur die
   * echte Datenbank kann das einlösen: Ein fehlender {@code CHECK}-Wert fällt weder beim Übersetzen
   * noch im Service auf, sondern erst hier — und ohne diesen Test erst nachts am hochgeladenen
   * Auszug.
   */
  @Test
  void einKettenLaufMitZeitbudgetAbbruchWirdAngenommenUndZurueckgelesen() {
    NightRun kette =
        new NightRun(
            null,
            projectId,
            T1,
            NightRunMode.CHAIN,
            NightRunKind.NIGHT,
            3_600_000L,
            1,
            0,
            0,
            null,
            ANGELEGT,
            NightRunOrigin.UPLOAD,
            null,
            true,
            null,
            null);

    long id =
        runs.insertIfAbsent(kette, List.of(mitKlasse(853, NightRunErrorClass.TIME_BUDGET_EXCEEDED)))
            .orElseThrow();

    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT))
        .extracting(NightRun::mode)
        .containsExactly(NightRunMode.CHAIN);
    assertThat(runs.findItemsByRunIds(List.of(id)))
        .extracting(NightRunItem::errorClass)
        .containsExactly(NightRunErrorClass.TIME_BUDGET_EXCEEDED);
  }

  // --- Gattung (Issue #1010) ------------------------------------------------------------------

  /**
   * Der Roundtrip der interaktiven Sitzung: Gattung und Laufart gehen als {@code INTERACTIVE}
   * hinein und kommen an Lauf <b>und</b> Arbeitspaket so zurueck. Nur die echte Datenbank loest das
   * ein — ein fehlender {@code CHECK}-Wert oder eine zu kurze Spalte faellt weder beim Uebersetzen
   * noch im Service auf.
   */
  @Test
  void eineSitzungKommtMitGattungUndLaufartInteractiveZurueck() {
    NightRun sitzung =
        new NightRun(
            null,
            projectId,
            T1,
            NightRunMode.INTERACTIVE,
            NightRunKind.INTERACTIVE,
            1_000L,
            1,
            0,
            0,
            null,
            ANGELEGT,
            NightRunOrigin.TOKEN,
            "sitzungs-token",
            true,
            null,
            null);

    long id = runs.insertIfAbsent(sitzung, List.of(paket(1010, NightRunState.GREEN))).orElseThrow();

    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.INTERACTIVE))
        .extracting(NightRun::mode, NightRun::kind)
        .containsExactly(tuple(NightRunMode.INTERACTIVE, NightRunKind.INTERACTIVE));
    assertThat(runs.findItemsByRunIds(List.of(id)))
        .extracting(NightRunItem::mode, NightRunItem::kind)
        .containsExactly(tuple(NightRunMode.INTERACTIVE, NightRunKind.INTERACTIVE));
  }

  /**
   * Eine Zeile, die ohne Gattung eingefuegt wird, liest sich als Nachtlauf — der Vorgabewert aus
   * {@code V34} (Issue #1009). Bestandszeilen leben davon; sie haben nie eine Gattung geschrieben.
   */
  @Test
  void einLaufOhneGattungLiestSichSamtPaketAlsNight() {
    long runId =
        insert(
            "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
                + " skipped_count, unparsed_count, created_at) VALUES ("
                + projectId
                + ", timestamptz '2026-09-08T22:00:00Z', 'IMPLEMENTATION', 1000, 1, 0, 0,"
                + " timestamptz '2026-09-08T23:00:00Z') RETURNING id");
    jdbc.update(
        "INSERT INTO night_run_item (night_run_id, project_id, started_at, mode, card_number,"
            + " title, state) VALUES (?, ?, timestamptz '2026-09-08T22:00:00Z',"
            + " 'IMPLEMENTATION', ?, ?, ?)",
        runId,
        projectId,
        902,
        "Altpaket ohne Gattung",
        "GREEN");

    assertThat(gelesen(runId).kind()).isEqualTo(NightRunKind.NIGHT);
    assertThat(runs.findItemsByRunIds(List.of(runId)))
        .extracting(NightRunItem::kind)
        .containsExactly(NightRunKind.NIGHT);
  }

  @Test
  void findByProjectOrderByStartedAtDescLiefertDenJuengstenLaufZuerst() {
    anlegen(T1, List.of());
    anlegen(T3, List.of());
    anlegen(T2, List.of());

    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT))
        .extracting(NightRun::startedAt)
        .containsExactly(T3, T2, T1);
  }

  @Test
  void findByProjectOrderByStartedAtDescKenntNurDasEigeneProjekt() {
    anlegen(T1, List.of());

    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId + 999, NightRunKind.NIGHT))
        .isEmpty();
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
            null,
            anderesProjekt,
            T1,
            NightRunMode.REVIEW,
            NightRunKind.NIGHT,
            1_000L,
            0,
            0,
            0,
            null,
            ANGELEGT,
            NightRunOrigin.UPLOAD,
            null,
            true,
            null,
            null);

    assertThat(runs.insertIfAbsent(fremd, List.of())).isPresent();
  }

  // --- Loeschen: Projekt nimmt alles mit, Lauf laesst Pakete verwaist stehen (Issue #964) ----

  /**
   * Das Projekt nimmt alles mit — auch ein Paket, dessen Lauf schon verdraengt ist. Ohne eigenen
   * Fremdschluessel auf {@code project} ueberlebte ein verwaistes Paket sein Projekt (Issue #964).
   */
  @Test
  void dasLoeschenDesProjektsEntferntLaeufeUndItems_auchVerwaiste() {
    long verdraengt = anlegen(T1, List.of(paket(720, NightRunState.RED)));
    anlegen(T2, List.of(paket(721, NightRunState.GREEN)));
    jdbc.update("DELETE FROM night_run WHERE id = ?", verdraengt);
    assertThat(zeilen("night_run_item")).isEqualTo(2);

    jdbc.update("DELETE FROM project WHERE id = ?", projectId);

    assertThat(zeilen("night_run")).isZero();
    assertThat(zeilen("night_run_item")).isZero();
  }

  /**
   * Der Lauf verschwindet, seine Pakete bleiben verwaist stehen und tragen Projekt, Startzeitpunkt
   * und Lauf-Art weiter (Issue #964) — die Messwerte einer Karte ueberdauern die Aufbewahrung.
   */
  @Test
  void dasLoeschenEinesLaufsLaesstSeineItemsVerwaistStehen() {
    long id = anlegen(T1, List.of(paket(721, NightRunState.GREEN)));

    jdbc.update("DELETE FROM night_run WHERE id = ?", id);

    assertThat(
            jdbc.queryForMap(
                "SELECT night_run_id, project_id, started_at, mode FROM night_run_item"
                    + " WHERE card_number = 721"))
        .containsEntry("night_run_id", null)
        .containsEntry("project_id", projectId)
        .containsEntry("mode", "IMPLEMENTATION")
        .extractingByKey("started_at")
        .isEqualTo(Timestamp.from(T1));
  }

  /** Ueber den Upload-Weg: Das Paket traegt die vier Werte seines Laufs, nicht seine eigenen. */
  @Test
  void insertIfAbsentSchreibtProjektStartArtUndGattungDesLaufsAnsPaket() {
    long id = anlegen(T1, List.of(paket(721, NightRunState.GREEN)));

    assertThat(runs.findItemsByRunIds(List.of(id)))
        .extracting(
            NightRunItem::projectId,
            NightRunItem::startedAt,
            NightRunItem::mode,
            NightRunItem::kind)
        .containsExactly(tuple(projectId, T1, NightRunMode.IMPLEMENTATION, NightRunKind.NIGHT));
  }

  /** Ueber den meldenden Weg, beim Anlegen wie beim Ersetzen. */
  @Test
  void upsertSchreibtProjektStartUndArtDesLaufsAnsPaket_auchBeimErsetzen() {
    Instant start = Instant.parse("2026-09-15T22:00:00Z");
    UpsertResult erster =
        runs.upsert(meldung(start, false), List.of(paket(101, NightRunState.GREEN)));
    assertThat(runs.findItemsByRunIds(List.of(erster.id())))
        .extracting(NightRunItem::projectId, NightRunItem::startedAt, NightRunItem::mode)
        .containsExactly(tuple(projectId, start, NightRunMode.CHAIN));

    runs.upsert(meldung(start, true), List.of(paket(102, NightRunState.RED)));

    assertThat(runs.findItemsByRunIds(List.of(erster.id())))
        .extracting(NightRunItem::projectId, NightRunItem::startedAt, NightRunItem::mode)
        .containsExactly(tuple(projectId, start, NightRunMode.CHAIN));
  }

  /**
   * Nur verwaiste Pakete mit genau diesem Startzeitpunkt fallen (Issue #965). Die Pakete eines
   * vorhandenen Laufs mit demselben Startzeitpunkt bleiben, ebenso verwaiste eines anderen Laufs.
   */
  @Test
  void deleteOrphanItemsOfRunLoeschtNurDieVerwaistenDiesesStartzeitpunkts() {
    jdbc.update(
        "DELETE FROM night_run WHERE id = ?", anlegen(T1, List.of(paket(720, NightRunState.RED))));
    jdbc.update(
        "DELETE FROM night_run WHERE id = ?", anlegen(T2, List.of(paket(730, NightRunState.RED))));
    long vorhanden = anlegen(T1, List.of(paket(721, NightRunState.GREEN)));

    assertThat(runs.deleteOrphanItemsOfRun(projectId, T1)).isEqualTo(1);

    assertThat(runs.findItemsByRunIds(List.of(vorhanden)))
        .extracting(NightRunItem::cardNumber)
        .containsExactly(721);
    assertThat(
            jdbc.queryForList(
                "SELECT card_number FROM night_run_item ORDER BY card_number", Integer.class))
        .containsExactly(721, 730);
  }

  @Test
  void deleteOrphanItemsOfRunLaesstAndereProjekteUnberuehrt() {
    jdbc.update(
        "DELETE FROM night_run WHERE id = ?", anlegen(T1, List.of(paket(720, NightRunState.RED))));

    assertThat(runs.deleteOrphanItemsOfRun(projectId + 999, T1)).isZero();
    assertThat(zeilen("night_run_item")).isEqualTo(1);
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
        null,
        null,
        PLATZHALTER_PROJEKT,
        PLATZHALTER_START,
        PLATZHALTER_MODUS,
        PLATZHALTER_GATTUNG,
        721,
        "Paket",
        NightRunState.GREEN,
        null,
        null,
        null,
        excerpt,
        null);
  }

  private NightRun mitProbe(Instant startedAt, int laenge) {
    return new NightRun(
        null,
        projectId,
        startedAt,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        1_000L,
        0,
        0,
        1,
        "y".repeat(laenge),
        ANGELEGT,
        NightRunOrigin.UPLOAD,
        null,
        true,
        null,
        null);
  }

  // --- Korrelation Zustand <-> Fehlerklasse ---------------------------------------------------

  @Test
  void gruenTraegtKeineFehlerklasse_jederAndereZustandGenauEineDerAcht() {
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

  // --- Kappung verwaister Pakete (Issue #966) ------------------------------------------------

  /** Verdraengt einen gerade angelegten Lauf, damit seine Pakete verwaist stehen bleiben. */
  private void verwaist(Instant startedAt, NightRunItem... items) {
    verwaist(startedAt, NightRunKind.NIGHT, items);
  }

  private void verwaist(Instant startedAt, NightRunKind kind, NightRunItem... items) {
    jdbc.update("DELETE FROM night_run WHERE id = ?", anlegen(startedAt, kind, List.of(items)));
  }

  @Test
  void deleteOrphanItemsOlderThanNewestBehaeltDieJuengstenVerwaistenNachStartzeitpunkt() {
    // Absichtlich nicht in Zeitfolge angelegt: gemessen wird an started_at, nicht an der ID.
    verwaist(T2, paket(2, NightRunState.GREEN));
    verwaist(T1, paket(1, NightRunState.GREEN));
    verwaist(T3, paket(3, NightRunState.GREEN));

    assertThat(runs.deleteOrphanItemsOlderThanNewest(projectId, NightRunKind.NIGHT, 2))
        .isEqualTo(1);

    assertThat(
            jdbc.queryForList(
                "SELECT card_number FROM night_run_item ORDER BY card_number", Integer.class))
        .containsExactly(2, 3);
  }

  /**
   * Ein aufbewahrter Lauf mit der Hoechstzahl an Paketen bleibt bei voller Grenze unversehrt: Seine
   * Pakete zaehlen nicht mit und werden nicht gekappt (Issue #966).
   */
  @Test
  void einAufbewahrterLaufMitVollerPaketzahlBleibtBeiVollerGrenzeUnversehrt() {
    List<NightRunItem> zweihundert =
        IntStream.rangeClosed(1, 200).mapToObj(n -> paket(n, NightRunState.GREEN)).toList();
    long aufbewahrt = anlegen(T3, zweihundert);
    verwaist(T1, paket(1001, NightRunState.RED));
    verwaist(T2, paket(1002, NightRunState.RED));

    assertThat(runs.deleteOrphanItemsOlderThanNewest(projectId, NightRunKind.NIGHT, 1))
        .isEqualTo(1);

    assertThat(runs.findItemsByRunIds(List.of(aufbewahrt))).hasSize(200);
    assertThat(
            jdbc.queryForList(
                "SELECT card_number FROM night_run_item WHERE night_run_id IS NULL", Integer.class))
        .containsExactly(1002);
  }

  @Test
  void deleteOrphanItemsOlderThanNewestLaesstAndereProjekteUnberuehrt() {
    verwaist(T1, paket(1, NightRunState.GREEN));

    assertThat(runs.deleteOrphanItemsOlderThanNewest(projectId + 999, NightRunKind.NIGHT, 1))
        .isZero();
    assertThat(zeilen("night_run_item")).isEqualTo(1);
  }

  // --- Getrennte Kappung je Gattung (Issue #1011) --------------------------------------------

  /**
   * Die Sitzungs-Grenze kappt nur Sitzungen. Die verwaisten Pakete der Nachtlaeufe stehen daneben
   * und werden von ihr nicht gezaehlt — sonst risse eine haeufige Gattung die seltene mit sich.
   */
  @Test
  void deleteOrphanItemsOlderThanNewestKapptNurDieEigeneGattung() {
    verwaist(T1, NightRunKind.NIGHT, paket(1, NightRunState.GREEN));
    verwaist(T2, NightRunKind.NIGHT, paket(2, NightRunState.GREEN));
    verwaist(T1, NightRunKind.INTERACTIVE, paket(11, NightRunState.GREEN));
    verwaist(T2, NightRunKind.INTERACTIVE, paket(12, NightRunState.GREEN));
    verwaist(T3, NightRunKind.INTERACTIVE, paket(13, NightRunState.GREEN));

    assertThat(runs.deleteOrphanItemsOlderThanNewest(projectId, NightRunKind.INTERACTIVE, 1))
        .isEqualTo(2);

    assertThat(
            jdbc.queryForList(
                "SELECT card_number FROM night_run_item ORDER BY card_number", Integer.class))
        .containsExactly(1, 2, 13);
  }

  /** Und umgekehrt: Die Nachtlauf-Grenze laesst die verwaisten Pakete der Sitzungen stehen. */
  @Test
  void deleteOrphanItemsOlderThanNewestLaesstDieAndereGattungStehen() {
    verwaist(T1, NightRunKind.INTERACTIVE, paket(11, NightRunState.GREEN));
    verwaist(T2, NightRunKind.INTERACTIVE, paket(12, NightRunState.GREEN));
    verwaist(T1, NightRunKind.NIGHT, paket(1, NightRunState.GREEN));
    verwaist(T2, NightRunKind.NIGHT, paket(2, NightRunState.GREEN));

    assertThat(runs.deleteOrphanItemsOlderThanNewest(projectId, NightRunKind.NIGHT, 1))
        .isEqualTo(1);

    assertThat(
            jdbc.queryForList(
                "SELECT card_number FROM night_run_item ORDER BY card_number", Integer.class))
        .containsExactly(2, 11, 12);
  }

  // --- Anlaeufe einer Karte (Issue #967) ----------------------------------------------------

  @Test
  void findByCardLiefertDieAnlaeufeAusMehrerenLaeufen_juengsterZuerst_auchVerwaiste() {
    verwaist(T1, paket(721, NightRunState.RED));
    anlegen(T3, List.of(paket(721, NightRunState.GREEN), paket(722, NightRunState.GREEN)));
    anlegen(T2, List.of(paket(721, NightRunState.YELLOW)));

    assertThat(runs.findByCard(projectId, 721))
        .extracting(NightRunItem::startedAt, NightRunItem::state, NightRunItem::projectId)
        .containsExactly(
            tuple(T3, NightRunState.GREEN, projectId),
            tuple(T2, NightRunState.YELLOW, projectId),
            tuple(T1, NightRunState.RED, projectId));
  }

  @Test
  void findByCardKenntNurDasEigeneProjekt() {
    anlegen(T1, List.of(paket(721, NightRunState.GREEN)));
    long andererUser =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('c@example.com', 'x', 'C') RETURNING id");
    long anderesProjekt =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('R', "
                + andererUser
                + ") RETURNING id");
    runs.insertIfAbsent(
        new NightRun(
            null,
            anderesProjekt,
            T2,
            NightRunMode.IMPLEMENTATION,
            NightRunKind.NIGHT,
            1_000L,
            1,
            0,
            0,
            null,
            ANGELEGT,
            NightRunOrigin.UPLOAD,
            null,
            true,
            null,
            null),
        List.of(paket(721, NightRunState.RED)));

    assertThat(runs.findByCard(projectId, 721))
        .extracting(NightRunItem::startedAt)
        .containsExactly(T1);
  }

  // --- Ringpuffer und Zählung -----------------------------------------------------------------

  @Test
  void deleteOlderThanNewestBehaeltDieJuengstenLaeufeUndMeldetDieVerdraengten() {
    anlegen(T1, List.of(paket(721, NightRunState.RED)));
    anlegen(T2, List.of());
    anlegen(T3, List.of());

    assertThat(runs.deleteOlderThanNewest(projectId, NightRunKind.NIGHT, 2)).isEqualTo(1);
    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT))
        .extracting(NightRun::startedAt)
        .containsExactly(T3, T2);
  }

  @Test
  void deleteOlderThanNewestLaesstAndereProjekteUnberuehrt() {
    anlegen(T1, List.of());

    assertThat(runs.deleteOlderThanNewest(projectId + 999, NightRunKind.NIGHT, 0)).isZero();
    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT))
        .hasSize(1);
  }

  // --- Getrennte Verdraengung je Gattung (Issue #1011) ---------------------------------------

  /**
   * Fuenf Sitzungen bei einer Sitzungs-Grenze von drei: Es bleiben die drei juengsten Sitzungen,
   * und <b>kein</b> gleichzeitig vorhandener Nachtlauf faellt. Unter einer gemeinsamen Grenze
   * haetten die haeufigeren Sitzungen die Nachtlauf-Auswertung binnen Tagen ausgeraeumt.
   */
  @Test
  void deleteOlderThanNewestVerdraengtNurDieEigeneGattung() {
    anlegen(T1, NightRunKind.NIGHT, List.of());
    anlegen(T2, NightRunKind.NIGHT, List.of());
    List<Instant> sitzungen =
        List.of(
            Instant.parse("2026-09-05T08:00:00Z"),
            Instant.parse("2026-09-05T09:00:00Z"),
            Instant.parse("2026-09-05T10:00:00Z"),
            Instant.parse("2026-09-05T11:00:00Z"),
            Instant.parse("2026-09-05T12:00:00Z"));
    sitzungen.forEach(s -> anlegen(s, NightRunKind.INTERACTIVE, List.of()));

    assertThat(runs.deleteOlderThanNewest(projectId, NightRunKind.INTERACTIVE, 3)).isEqualTo(2);

    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.INTERACTIVE))
        .extracting(NightRun::startedAt)
        .containsExactly(sitzungen.get(4), sitzungen.get(3), sitzungen.get(2));
    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT))
        .extracting(NightRun::startedAt)
        .containsExactly(T2, T1);
  }

  /** Und umgekehrt: Nachtlaeufe jenseits ihrer Grenze verdraengen keine Sitzung. */
  @Test
  void deleteOlderThanNewestLaesstDieAndereGattungStehen() {
    anlegen(T1, NightRunKind.INTERACTIVE, List.of());
    anlegen(T2, NightRunKind.INTERACTIVE, List.of());
    anlegen(Instant.parse("2026-09-05T08:00:00Z"), NightRunKind.NIGHT, List.of());
    anlegen(Instant.parse("2026-09-05T09:00:00Z"), NightRunKind.NIGHT, List.of());
    anlegen(Instant.parse("2026-09-05T10:00:00Z"), NightRunKind.NIGHT, List.of());

    assertThat(runs.deleteOlderThanNewest(projectId, NightRunKind.NIGHT, 1)).isEqualTo(2);

    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT))
        .hasSize(1);
    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.INTERACTIVE))
        .extracting(NightRun::startedAt)
        .containsExactly(T2, T1);
  }

  /**
   * Die Laufliste der Nachtlauf-Seite sieht nur Nachtlaeufe (Issue #1012, Nicht-Ziel): Eine Sitzung
   * im selben Projekt darf dort nicht auftauchen, sonst stuende sie als „letzter Lauf" da.
   */
  @Test
  void findByProjectAndKindOrderByStartedAtDescLiefertNurDieGenannteGattung() {
    anlegen(T1, NightRunKind.NIGHT, List.of());
    anlegen(T2, NightRunKind.INTERACTIVE, List.of());
    anlegen(T3, NightRunKind.INTERACTIVE, List.of());

    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT))
        .extracting(NightRun::startedAt)
        .containsExactly(T1);
    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.INTERACTIVE))
        .extracting(NightRun::startedAt)
        .containsExactly(T3, T2);
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

    assertThat(runs.countRunsByErrorClass(projectId, NightRunKind.NIGHT))
        .containsOnly(
            entry(NightRunErrorClass.CHECKS_RED, 2L), entry(NightRunErrorClass.HARD_ABORT, 1L));
  }

  @Test
  void countRunsByErrorClassLaesstVerdraengteLaeufeUndGrueneItemsAusserAcht() {
    anlegen(T1, List.of(mitKlasse(1, NightRunErrorClass.CHECKS_RED)));
    anlegen(T2, List.of(paket(2, NightRunState.GREEN)));
    runs.deleteOlderThanNewest(projectId, NightRunKind.NIGHT, 1);

    assertThat(runs.countRunsByErrorClass(projectId, NightRunKind.NIGHT)).isEmpty();
  }

  @Test
  void countRunsByErrorClassKenntNurDasEigeneProjekt() {
    anlegen(T1, List.of(mitKlasse(1, NightRunErrorClass.CHECKS_RED)));

    assertThat(runs.countRunsByErrorClass(projectId + 999, NightRunKind.NIGHT)).isEqualTo(Map.of());
  }

  /**
   * Die Platte „Abbruchgruende" zaehlt nur Nachtlaeufe (Issue #1012, Nicht-Ziel). Eine rote Sitzung
   * im selben Projekt traegt dieselbe Fehlerklasse und wuerde die Zahl sonst still verdoppeln.
   */
  @Test
  void countRunsByErrorClassZaehltNurDieGenannteGattung() {
    anlegen(T1, NightRunKind.NIGHT, List.of(mitKlasse(1, NightRunErrorClass.CHECKS_RED)));
    anlegen(T2, NightRunKind.INTERACTIVE, List.of(mitKlasse(2, NightRunErrorClass.CHECKS_RED)));

    assertThat(runs.countRunsByErrorClass(projectId, NightRunKind.NIGHT))
        .containsOnly(entry(NightRunErrorClass.CHECKS_RED, 1L));
    assertThat(runs.countRunsByErrorClass(projectId, NightRunKind.INTERACTIVE))
        .containsOnly(entry(NightRunErrorClass.CHECKS_RED, 1L));
  }

  private static NightRunItem mitKlasse(int cardNumber, NightRunErrorClass errorClass) {
    return new NightRunItem(
        null,
        null,
        PLATZHALTER_PROJEKT,
        PLATZHALTER_START,
        PLATZHALTER_MODUS,
        PLATZHALTER_GATTUNG,
        cardNumber,
        "Paket " + cardNumber,
        NightRunState.RED,
        errorClass,
        null,
        null,
        null,
        null);
  }

  /**
   * Die Vorgaben der Migration: Eine Zeile, die ohne die neuen Spalten eingefuegt wird, traegt
   * danach die menschliche Herkunft, gilt als vollstaendig und hat in allen vier Verbrauchsspalten
   * {@code NULL} — „nicht gemessen" und nicht Null. Fuer Altlaeufe sind die Werte nicht
   * rekonstruierbar, und eine 0 behauptete, der Lauf habe nichts verbraucht.
   */
  @Test
  void eineZeileOhneDieNeuenSpaltenTraegtDieVorgabenDerMigration() {
    long runId =
        insert(
            "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
                + " skipped_count, unparsed_count, created_at) VALUES ("
                + projectId
                + ", timestamptz '2026-09-05T22:00:00Z', 'IMPLEMENTATION', 1000, 1, 0, 0,"
                + " timestamptz '2026-09-05T23:00:00Z') RETURNING id");
    jdbc.update(
        "INSERT INTO night_run_item (night_run_id, project_id, started_at, mode, card_number,"
            + " title, state) VALUES (?, ?, timestamptz '2026-09-05T22:00:00Z', ?, ?, ?, ?)",
        runId,
        projectId,
        "IMPLEMENTATION",
        901,
        "Altpaket",
        "GREEN");

    NightRun gelesen =
        runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT).stream()
            .filter(r -> Objects.equals(r.id(), runId))
            .findFirst()
            .orElseThrow();

    assertThat(gelesen.origin()).isEqualTo(NightRunOrigin.UPLOAD);
    assertThat(gelesen.complete()).isTrue();
    assertThat(gelesen.tokenName()).isNull();
    assertThat(gelesen.updatedAt()).isNull();
    assertThat(gelesen.usage()).isNull();
    assertThat(runs.findItemsByRunIds(List.of(runId)))
        .singleElement()
        .extracting(NightRunItem::usage)
        .isNull();
  }

  /** Verbrauch kommt an Lauf und Arbeitspaket unveraendert zurueck. */
  @Test
  void gemeldeterVerbrauchKommtAnLaufUndPaketZurueck() {
    NightRunUsage laufVerbrauch =
        new NightRunUsage(new BigDecimal("8.032575"), 148L, 62_411L, 8_883_160L);
    NightRunUsage paketVerbrauch = new NightRunUsage(new BigDecimal("0.940000"), 12L, 34L, 56L);
    NightRun mitVerbrauch =
        new NightRun(
            null,
            projectId,
            Instant.parse("2026-09-06T22:00:00Z"),
            NightRunMode.CHAIN,
            NightRunKind.NIGHT,
            1000L,
            1,
            0,
            0,
            null,
            ANGELEGT,
            NightRunOrigin.UPLOAD,
            null,
            true,
            null,
            laufVerbrauch);
    NightRunItem paket =
        new NightRunItem(
            null,
            null,
            PLATZHALTER_PROJEKT,
            PLATZHALTER_START,
            PLATZHALTER_MODUS,
            PLATZHALTER_GATTUNG,
            944,
            "Mit Verbrauch",
            NightRunState.GREEN,
            null,
            5L,
            null,
            null,
            paketVerbrauch);

    long runId = runs.insertIfAbsent(mitVerbrauch, List.of(paket)).orElseThrow();

    NightRun gelesen =
        runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT).stream()
            .filter(r -> Objects.equals(r.id(), runId))
            .findFirst()
            .orElseThrow();
    assertThat(gelesen.usage()).isNotNull();
    assertThat(gelesen.usage().costUsd())
        .usingComparator(BigDecimal::compareTo)
        .isEqualTo(laufVerbrauch.costUsd());
    assertThat(gelesen.usage().inputTokens()).isEqualTo(148L);
    assertThat(gelesen.usage().outputTokens()).isEqualTo(62_411L);
    assertThat(gelesen.usage().cachedInputTokens()).isEqualTo(8_883_160L);

    NightRunItem gelesenesPaket = runs.findItemsByRunIds(List.of(runId)).getFirst();
    assertThat(gelesenesPaket.usage()).isNotNull();
    assertThat(gelesenesPaket.usage().costUsd())
        .usingComparator(BigDecimal::compareTo)
        .isEqualTo(paketVerbrauch.costUsd());
  }

  /** Maschinelle Herkunft samt Tokenname und Fortschreibungszeitpunkt. */
  @Test
  void maschinelleHerkunftKommtMitTokennamenUndZeitpunktZurueck() {
    String langerName = "x".repeat(120);
    Instant fortgeschrieben = Instant.parse("2026-09-07T03:22:00Z");
    NightRun maschinell =
        new NightRun(
            null,
            projectId,
            Instant.parse("2026-09-07T22:00:00Z"),
            NightRunMode.IMPLEMENTATION,
            NightRunKind.NIGHT,
            1000L,
            1,
            0,
            0,
            null,
            ANGELEGT,
            NightRunOrigin.TOKEN,
            langerName,
            false,
            fortgeschrieben,
            null);

    long runId = runs.insertIfAbsent(maschinell, List.of()).orElseThrow();

    NightRun gelesen =
        runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT).stream()
            .filter(r -> Objects.equals(r.id(), runId))
            .findFirst()
            .orElseThrow();
    assertThat(gelesen.origin()).isEqualTo(NightRunOrigin.TOKEN);
    assertThat(gelesen.tokenName()).isEqualTo(langerName);
    assertThat(gelesen.complete()).isFalse();
    assertThat(gelesen.updatedAt()).isEqualTo(fortgeschrieben);
  }

  // --- upsert: der meldende Weg (Issue #945) ---------------------------------------------

  private NightRun meldung(Instant startedAt, boolean complete) {
    return new NightRun(
        null,
        projectId,
        startedAt,
        NightRunMode.CHAIN,
        NightRunKind.NIGHT,
        1000L,
        1,
        0,
        0,
        null,
        ANGELEGT,
        NightRunOrigin.TOKEN,
        "nacht-token",
        complete,
        Instant.parse("2026-09-10T03:22:00Z"),
        null);
  }

  /**
   * Ein Lauf ueber die ID, unabhaengig von seiner Gattung: Die Laufliste des Ports filtert seit
   * Issue #1012 auf eine Gattung, und mancher Fall hier schreibt die Gattung gerade um.
   */
  private NightRun gelesen(long runId) {
    return Arrays.stream(NightRunKind.values())
        .flatMap(k -> runs.findByProjectAndKindOrderByStartedAtDesc(projectId, k).stream())
        .filter(r -> Objects.equals(r.id(), runId))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void ersterUpsertLegtDenLaufAnUndMeldetCreated() {
    Instant start = Instant.parse("2026-09-10T22:00:00Z");

    UpsertResult ergebnis =
        runs.upsert(meldung(start, false), List.of(paket(101, NightRunState.GREEN)));

    assertThat(ergebnis.created()).isTrue();
    assertThat(gelesen(ergebnis.id()).origin()).isEqualTo(NightRunOrigin.TOKEN);
    assertThat(runs.findItemsByRunIds(List.of(ergebnis.id()))).hasSize(1);
  }

  @Test
  void zweiterUpsertMeldetDieselbeIdUndVerdoppeltNichts() {
    Instant start = Instant.parse("2026-09-11T22:00:00Z");
    UpsertResult erster =
        runs.upsert(meldung(start, false), List.of(paket(101, NightRunState.GREEN)));

    UpsertResult zweiter =
        runs.upsert(meldung(start, true), List.of(paket(101, NightRunState.GREEN)));

    assertThat(zweiter.created()).isFalse();
    assertThat(zweiter.id()).isEqualTo(erster.id());
    assertThat(runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT))
        .filteredOn(r -> Objects.equals(r.startedAt(), start))
        .hasSize(1);
    assertThat(runs.findItemsByRunIds(List.of(erster.id()))).hasSize(1);
  }

  /** Der gemeldete Stand ist vollstaendig: Was die zweite Meldung nicht mehr fuehrt, ist fort. */
  @Test
  void einZweiterUpsertErsetztDenStandVollstaendig() {
    Instant start = Instant.parse("2026-09-12T22:00:00Z");
    UpsertResult erster =
        runs.upsert(
            meldung(start, false),
            List.of(paket(101, NightRunState.GREEN), paket(102, NightRunState.RED)));

    runs.upsert(meldung(start, true), List.of(paket(103, NightRunState.GREEN)));

    assertThat(runs.findItemsByRunIds(List.of(erster.id())))
        .extracting(NightRunItem::cardNumber)
        .containsExactly(103);
  }

  /**
   * Die Gattung gehoert zum gemeldeten Stand und wird wie die Lauf-Art fortgeschrieben (Issue
   * #1010) — am Lauf und an seinen neu geschriebenen Arbeitspaketen.
   */
  @Test
  void einZweiterUpsertSchreibtDieGattungFort() {
    Instant start = Instant.parse("2026-09-16T22:00:00Z");
    UpsertResult erster =
        runs.upsert(meldung(start, false), List.of(paket(101, NightRunState.GREEN)));

    NightRun alsSitzung =
        new NightRun(
            null,
            projectId,
            start,
            NightRunMode.INTERACTIVE,
            NightRunKind.INTERACTIVE,
            2000L,
            1,
            0,
            0,
            null,
            ANGELEGT,
            NightRunOrigin.TOKEN,
            "sitzungs-token",
            true,
            null,
            null);
    runs.upsert(alsSitzung, List.of(paket(102, NightRunState.GREEN)));

    assertThat(gelesen(erster.id()).kind()).isEqualTo(NightRunKind.INTERACTIVE);
    assertThat(runs.findItemsByRunIds(List.of(erster.id())))
        .extracting(NightRunItem::kind)
        .containsExactly(NightRunKind.INTERACTIVE);
  }

  @Test
  void beimErsetzenBleibtCreatedAtStehenUndUpdatedAtWaechst() {
    Instant start = Instant.parse("2026-09-13T22:00:00Z");
    UpsertResult erster = runs.upsert(meldung(start, false), List.of());
    Instant spaeter = Instant.parse("2026-09-13T04:00:00Z");

    NightRun zweite =
        new NightRun(
            null,
            projectId,
            start,
            NightRunMode.CHAIN,
            NightRunKind.NIGHT,
            2000L,
            2,
            0,
            0,
            null,
            Instant.parse("2026-09-14T06:00:00Z"),
            NightRunOrigin.TOKEN,
            "nacht-token",
            true,
            spaeter,
            null);
    runs.upsert(zweite, List.of());

    NightRun nachher = gelesen(erster.id());
    assertThat(nachher.createdAt()).isEqualTo(ANGELEGT);
    assertThat(nachher.updatedAt()).isEqualTo(spaeter);
    assertThat(nachher.complete()).isTrue();
  }

  /** Der Upload-Weg plaettet keinen reicheren Stand: insertIfAbsent laesst ihn unangetastet. */
  @Test
  void nachEinemUpsertLaesstInsertIfAbsentDenLaufUnangetastet() {
    Instant start = Instant.parse("2026-09-14T22:00:00Z");
    UpsertResult gemeldet =
        runs.upsert(meldung(start, true), List.of(paket(101, NightRunState.GREEN)));

    Optional<Long> nochmal =
        runs.insertIfAbsent(lauf(start), List.of(paket(999, NightRunState.RED)));

    assertThat(nochmal).isEmpty();
    NightRun nachher = gelesen(gemeldet.id());
    assertThat(nachher.origin()).isEqualTo(NightRunOrigin.TOKEN);
    assertThat(nachher.tokenName()).isEqualTo("nacht-token");
    assertThat(runs.findItemsByRunIds(List.of(gemeldet.id())))
        .extracting(NightRunItem::cardNumber)
        .containsExactly(101);
  }
}
