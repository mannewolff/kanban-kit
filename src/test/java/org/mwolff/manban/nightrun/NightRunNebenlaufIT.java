package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Nebenläufige Meldungen gegen den Ringpuffer (Issue #1090).
 *
 * <p><b>Der Fehler, den diese Klasse abschließt:</b> Melden zwei <em>verschiedene</em> Läufe
 * desselben Projekts gleichzeitig, sieht unter {@code READ COMMITTED} jede Transaktion beim
 * Nachziehen des Ringpuffers nur ihren eigenen, noch nicht festgeschriebenen Lauf. Keine zählt den
 * Lauf der anderen mit, und nach beiden Commits liegt ein Lauf zu viel da. Die Obergrenze ist aber
 * die Zusage, auf die sich {@code DisruptionRepositoryAdapter} verlässt, wenn er ausdrücklich auf
 * ein {@code LIMIT} verzichtet.
 *
 * <p><b>Warum ein ausgespähtes Repository und kein Zufallsrennen:</b> Der Fehler entsteht nur in
 * einer bestimmten Verschränkung — beide Transaktionen müssen ihre Verdrängung abgesetzt haben,
 * bevor eine festschreibt. Ein Rennen ohne Haltepunkt träfe sie selten und machte den Test zum
 * Glücksspiel. Der erste Melder wird deshalb nach seiner letzten Datenbankaktion festgehalten; der
 * zweite läuft an und hängt sich — mit der Sperre an der Projektzeile, ohne sie an der Zeile, die
 * der erste gerade löscht. <b>Dass</b> er hängt, liest der Test an {@code pg_stat_activity} ab und
 * nicht an einer Wartezeit: Eine Wartezeit wäre entweder zu kurz (und der Test flackerte) oder zu
 * lang (und die Suite bezahlte sie bei jedem Lauf).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    // Ein kleiner Ringpuffer statt der 190 aus der Vorgabe: Sonst müsste der Aufbau 190 Läufe
    // anlegen, nur damit die Grenze überhaupt greift.
    properties = "manban.nightrun.max-per-project=3")
// Der @MockitoSpyBean macht diesen Kontext einzigartig — dieselbe Begruendung wie in
// CardOpenEpicIT: Er haelt sonst bis Suite-Ende einen eigenen Connection-Pool.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NightRunNebenlaufIT extends AbstractIntegrationTest {

  /** Name des Melders, der festgehalten wird — der Haltepunkt gilt nur für ihn. */
  private static final String ERSTER = "nl-melder-1";

  private static final String ZWEITER = "nl-melder-2";

  private static final Instant O1 = Instant.parse("2026-09-01T22:00:00Z");
  private static final Instant O2 = Instant.parse("2026-09-02T22:00:00Z");
  private static final Instant O3 = Instant.parse("2026-09-03T22:00:00Z");
  private static final Instant R1 = Instant.parse("2026-09-04T22:00:00Z");
  private static final Instant R2 = Instant.parse("2026-09-05T22:00:00Z");
  private static final Instant ANGELEGT = Instant.parse("2026-09-06T06:00:00Z");

  @MockitoSpyBean private NightRunRepository runs;
  @Autowired private NightRunService service;
  @Autowired private JdbcTemplate jdbc;

  private final CountDownLatch ersterHaeltDieTransaktion = new CountDownLatch(1);
  private final CountDownLatch freigabe = new CountDownLatch(1);
  private final List<Throwable> fehler = Collections.synchronizedList(new ArrayList<>());

  private long userId;
  private long projektA;

  @BeforeEach
  void aufbau() {
    userId =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('nebenlauf@example.com', 'x', 'N') RETURNING id");
    projektA = projekt("Projekt A");
  }

  /**
   * Der Fall, um den es geht: Zwei verschiedene Läufe desselben Projekts melden gleichzeitig in
   * einen bereits vollen Ringpuffer. Danach stehen <b>genau</b> drei Läufe da — die Obergrenze
   * dieses Kontexts —, und die beiden gemeldeten sind die jüngsten: verdrängt wurden ausschließlich
   * ältere.
   *
   * <p>Gegenprobe: Ohne {@code lockProject} bleiben vier statt drei Läufe stehen, weil der zweite
   * Melder die Verdrängung des ersten nicht sieht und der erste seinen Lauf nicht mitzählt.
   */
  @Test
  void zweiVerschiedeneLaeufeGleichzeitig_lassenGenauDieObergrenzeZurueck() throws Exception {
    altenLaufAnlegen(projektA, O1);
    altenLaufAnlegen(projektA, O2);
    altenLaufAnlegen(projektA, O3);
    haltDenErstenNachSeinerLetztenAktion();

    Thread erster = melder(ERSTER, projektA, R1);
    Thread zweiter = melder(ZWEITER, projektA, R2);
    erster.start();
    assertThat(ersterHaeltDieTransaktion.await(30, TimeUnit.SECONDS)).isTrue();
    zweiter.start();
    warteBisEinBackendAufEineSperreWartet();
    freigabe.countDown();
    erster.join(30_000);
    zweiter.join(30_000);

    assertThat(fehler).isEmpty();
    assertThat(startzeitpunkte(projektA)).containsExactly(R2, R1, O3);
  }

  /**
   * Die Sperre nimmt dem {@code FOR UPDATE} auf der Laufzeile nichts ab: Zwei gleichzeitige
   * Meldungen <b>desselben</b> Laufs enden weiterhin mit genau einem Lauf — ohne Verletzung des
   * eindeutigen Schlüssels (das belegt die leere Fehlerliste) und ohne Verklemmung (das belegt,
   * dass beide Fäden überhaupt enden).
   */
  @Test
  void zweiMeldungenDesselbenLaufsGleichzeitig_endenMitGenauEinemLauf() throws Exception {
    haltDenErstenNachSeinerLetztenAktion();

    Thread erster = melder(ERSTER, projektA, R1);
    Thread zweiter = melder(ZWEITER, projektA, R1);
    erster.start();
    assertThat(ersterHaeltDieTransaktion.await(30, TimeUnit.SECONDS)).isTrue();
    zweiter.start();
    warteBisEinBackendAufEineSperreWartet();
    freigabe.countDown();
    erster.join(30_000);
    zweiter.join(30_000);

    assertThat(fehler).isEmpty();
    assertThat(startzeitpunkte(projektA)).containsExactly(R1);
  }

  /**
   * Die Sperre gilt je Projekt und nicht global: Eine Meldung in einem anderen Projekt läuft durch,
   * während die erste ihre Transaktion offen hält. Ohne diesen Fall bliebe eine Sperre auf einer
   * gemeinsamen Zeile — die jede Meldung der Plattform hinter jede andere stellte — unentdeckt.
   */
  @Test
  void eineMeldungInEinemAnderenProjekt_wartetNichtAufDieOffeneMeldung() throws Exception {
    long projektB = projekt("Projekt B");
    haltDenErstenNachSeinerLetztenAktion();

    Thread erster = melder(ERSTER, projektA, R1);
    Thread zweiter = melder(ZWEITER, projektB, R1);
    erster.start();
    assertThat(ersterHaeltDieTransaktion.await(30, TimeUnit.SECONDS)).isTrue();
    zweiter.start();
    zweiter.join(15_000);

    assertThat(zweiter.isAlive()).as("Meldung in Projekt B wartet auf Projekt A").isFalse();
    assertThat(startzeitpunkte(projektB)).containsExactly(R1);
    freigabe.countDown();
    erster.join(30_000);
    assertThat(fehler).isEmpty();
  }

  /**
   * Hält den ersten Melder fest, <b>nachdem</b> er seine letzte Datenbankaktion abgesetzt hat: Er
   * hat dann die Projektzeile gesperrt, seinen Lauf geschrieben und verdrängt — und schreibt nur
   * noch nicht fest. Genau in diesem Fenster läuft der zweite Melder an.
   */
  private void haltDenErstenNachSeinerLetztenAktion() {
    doAnswer(
            aufruf -> {
              Object ergebnis = aufruf.callRealMethod();
              if (ERSTER.equals(Thread.currentThread().getName())) {
                ersterHaeltDieTransaktion.countDown();
                if (!freigabe.await(30, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("Freigabe des ersten Melders blieb aus");
                }
              }
              return ergebnis;
            })
        .when(runs)
        .deleteOrphanItemsOlderThanNewest(anyLong(), any(NightRunKind.class), anyInt());
  }

  /**
   * Ein meldender Faden. Was in ihm schiefgeht, sammelt der Uncaught-Handler ein und nicht ein
   * {@code catch}: Ein Fehlschlag im Faden bliebe sonst am Hauptfaden unsichtbar, und der Test
   * bestaende mit einer Meldung, die nie ankam.
   */
  private Thread melder(String name, long projectId, Instant startedAt) {
    Thread faden =
        new Thread(
            () -> service.ingest(userId, projectId, name, NightRunKind.NIGHT, meldung(startedAt)),
            name);
    faden.setUncaughtExceptionHandler((wer, ursache) -> fehler.add(ursache));
    return faden;
  }

  /**
   * Wartet, bis ein Backend dieser Datenbank auf eine Sperre wartet — der Beleg dafür, dass der
   * zweite Melder tatsächlich hängt und nicht nur langsam ist. Die eigene Verbindung zählt nicht
   * mit.
   */
  private void warteBisEinBackendAufEineSperreWartet() throws InterruptedException {
    long ende = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < ende) {
      Integer wartende =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                  + " AND wait_event_type = 'Lock' AND pid <> pg_backend_pid()",
              Integer.class);
      if (wartende != null && wartende > 0) {
        return;
      }
      TimeUnit.MILLISECONDS.sleep(25);
    }
    throw new AssertionError("Kein Backend wartet auf eine Sperre — der zweite Melder hing nie");
  }

  private List<Instant> startzeitpunkte(long projectId) {
    return jdbc.query(
        "SELECT started_at FROM night_run WHERE project_id = ? AND kind = 'NIGHT'"
            + " ORDER BY started_at DESC",
        (rs, zeile) -> rs.getTimestamp("started_at").toInstant(),
        projectId);
  }

  private long projekt(String name) {
    long id =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('"
                + name
                + "', "
                + userId
                + ") RETURNING id");
    // Direkt angelegt statt ueber eine Einladung: Gebraucht wird allein die Rolle, nicht der Weg
    // dorthin — requireOwner verlangt OWNER.
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, 'OWNER')",
        id,
        userId);
    return id;
  }

  /** Ein bereits aufbewahrter Lauf — er füllt den Ringpuffer, damit die Grenze überhaupt greift. */
  private void altenLaufAnlegen(long projectId, Instant startedAt) {
    runs.insertIfAbsent(
        new NightRun(
            null,
            projectId,
            startedAt,
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
            null,
            null),
        List.of());
  }

  private static NightRunService.NewNightRun meldung(Instant startedAt) {
    return new NightRunService.NewNightRun(
        startedAt, NightRunMode.CHAIN, 1_000L, 1, 0, 0, null, true, null, null, List.of());
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    return id == null ? 0L : id;
  }
}
