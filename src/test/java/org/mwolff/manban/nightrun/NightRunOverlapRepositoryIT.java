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
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Lese-Abfragen der Läufe für den Fortschritt eines laufenden Laufs (Issue #1373, Plan #1372
 * E3).
 *
 * <p>Gegen die echte Datenbank, weil die Überlappung allein in der {@code WHERE}-Bedingung steht:
 * Gattung, Token, Projekt und das Ende eines gemeldeten Laufs aus Start und Dauer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NightRunOverlapRepositoryIT extends AbstractIntegrationTest {

  private static final Instant VON = Instant.parse("2026-10-03T15:00:00Z");
  private static final Instant BIS = Instant.parse("2026-10-03T17:00:00Z");
  private static final String TOKEN = "Nachtlauf";
  private static final Duration STUNDE = Duration.ofHours(1);

  @Autowired private NightRunRepository runs;
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
                + " VALUES ('ueberlapp@example.com', 'x', 'U') RETURNING id");
    projectId = projekt("P");
  }

  private long projekt(String name) {
    return id("INSERT INTO project (name, owner_user_id) VALUES (?, ?) RETURNING id", name, userId);
  }

  private long lauf(
      long projekt,
      Instant startedAt,
      Duration dauer,
      String kind,
      String token,
      boolean complete) {
    return id(
        "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms, processed_count,"
            + " skipped_count, unparsed_count, created_at, origin, token_name, complete)"
            + " VALUES (?, ?, 'CHAIN', ?, ?, 0, 0, 0, now(), 'TOKEN', ?, ?) RETURNING id",
        projekt,
        OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC),
        kind,
        dauer.toMillis(),
        token,
        complete);
  }

  private long nacht(Instant startedAt, Duration dauer) {
    return lauf(projectId, startedAt, dauer, "NIGHT", TOKEN, true);
  }

  private List<Long> ueberlappend() {
    return runs.findOverlapping(projectId, TOKEN, VON, BIS).stream()
        .map(NightRun::requireId)
        .toList();
  }

  @Test
  void einUeberlappenderNachtlaufWirdGeliefert() {
    long ueberlappt = nacht(VON.minus(STUNDE), STUNDE.plusMinutes(30));

    assertThat(ueberlappend()).containsExactly(ueberlappt);
  }

  @Test
  void dieGrenzenGeltenEinschliesslich() {
    long endetAufVon = nacht(VON.minus(STUNDE), STUNDE);
    long beginntAufBis = nacht(BIS, STUNDE);

    assertThat(ueberlappend()).containsExactly(endetAufVon, beginntAufBis);
  }

  @Test
  void einNichtUeberlappenderNachtlaufWirdNichtGeliefert() {
    nacht(VON.minus(STUNDE), STUNDE.minusMillis(1));
    nacht(BIS.plusMillis(1), STUNDE);

    assertThat(ueberlappend()).isEmpty();
  }

  /**
   * Ein unfertiger Lauf trägt sein Ende noch nicht: Er wird geliefert, sobald er vor dem
   * Fensterende begann — ob er noch läuft oder verstummt ist, entscheidet die Ermittlung.
   */
  @Test
  void einUnfertigerLaufGiltAbSeinemStartAlsUeberlappend() {
    long unfertig =
        lauf(projectId, VON.minus(STUNDE.multipliedBy(5)), Duration.ZERO, "NIGHT", TOKEN, false);
    lauf(projectId, BIS.plusMillis(1), Duration.ZERO, "NIGHT", TOKEN, false);

    assertThat(ueberlappend()).containsExactly(unfertig);
  }

  /** AK: Eine interaktive Sitzung mit demselben Token überlappt nie (E3, A5). */
  @Test
  void eineInteraktiveSitzungMitGleichemTokenWirdNichtGeliefert() {
    lauf(projectId, VON.plus(Duration.ofMinutes(10)), STUNDE, "INTERACTIVE", TOKEN, true);

    assertThat(ueberlappend()).isEmpty();
  }

  @Test
  void einLaufMitFremdemTokenWirdNichtGeliefert() {
    lauf(projectId, VON.plus(Duration.ofMinutes(10)), STUNDE, "NIGHT", "Anderes", true);

    assertThat(ueberlappend()).isEmpty();
  }

  @Test
  void einLaufEinesFremdenProjektsWirdNichtGeliefert() {
    lauf(projekt("Q"), VON.plus(Duration.ofMinutes(10)), STUNDE, "NIGHT", TOKEN, true);

    assertThat(ueberlappend()).isEmpty();
  }

  @Test
  void findByIdAndProjectIdLiefertDenLaufNurImEigenenProjekt() {
    long lauf = nacht(VON, STUNDE);
    long fremdesProjekt = projekt("Q");

    assertThat(runs.findByIdAndProjectId(lauf, projectId))
        .hasValueSatisfying(
            r -> {
              assertThat(r.kind()).isEqualTo(NightRunKind.NIGHT);
              assertThat(r.tokenName()).isEqualTo(TOKEN);
              assertThat(r.startedAt()).isEqualTo(VON);
            });
    assertThat(runs.findByIdAndProjectId(lauf, fremdesProjekt)).isEmpty();
  }
}
