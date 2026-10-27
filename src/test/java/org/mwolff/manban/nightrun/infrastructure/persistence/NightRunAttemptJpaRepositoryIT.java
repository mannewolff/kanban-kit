package org.mwolff.manban.nightrun.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunAttempt;
import org.mwolff.manban.nightrun.domain.NightRunAttemptStage;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Der Lesepfad der Vorgänge gegen echtes Postgres (Issue #1944, Plan #1905 E4): Was der Adapter
 * über JDBC mit {@code Types.ARRAY} schreibt, liest die Entity über {@code @JdbcTypeCode(ARRAY)}
 * als {@code Integer[]} zurück — beide Bindungswege der ersten Array-Spalte im Schema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NightRunAttemptJpaRepositoryIT extends AbstractIntegrationTest {

  private static final Instant START = Instant.parse("2026-10-27T22:00:00Z");

  @Autowired private NightRunRepository runs;
  @Autowired private NightRunAttemptJpaRepository attempts;
  @Autowired private JdbcTemplate jdbc;

  private long projekt() {
    Long userId =
        jdbc.queryForObject(
            "INSERT INTO app_user (email, display_name) VALUES ('a@example.com', 'A')"
                + " RETURNING id",
            Long.class);
    Long projectId =
        jdbc.queryForObject(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id",
            Long.class,
            userId);
    return projectId == null ? 0L : projectId;
  }

  private static NightRun lauf(long projectId) {
    return new NightRun(
        null,
        projectId,
        START,
        NightRunMode.CHAIN,
        NightRunKind.NIGHT,
        1000L,
        1,
        0,
        0,
        null,
        START,
        NightRunOrigin.TOKEN,
        "nacht-token",
        false,
        START,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static NightRunItem paket(long projectId, NightRunAttempt... vorgaenge) {
    return new NightRunItem(
        null,
        null,
        projectId,
        START,
        NightRunMode.CHAIN,
        NightRunKind.NIGHT,
        1905,
        "Plan",
        NightRunState.GREEN,
        null,
        60_000L,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(vorgaenge));
  }

  @Test
  void dieEntityLiestJedeSpalteEinesVorgangsZurueck() {
    long projectId = projekt();
    runs.upsert(
        lauf(projectId),
        List.of(
            paket(
                projectId,
                new NightRunAttempt(
                    NightRunAttemptStage.PLAN,
                    1905,
                    "claude-opus-5-5",
                    "high",
                    true,
                    new NightRunUsage(new BigDecimal("1.500000"), 100L, 20L, 50L, 60_000L, 4),
                    List.of(1946, 1944)),
                new NightRunAttempt(
                    NightRunAttemptStage.REVIEW, 1905, null, null, false, null, List.of()))));
    Long itemId = jdbc.queryForObject("SELECT id FROM night_run_item", Long.class);

    List<NightRunAttemptEntity> gelesen =
        attempts.findByNightRunItemIdInOrderByNightRunItemIdAscPositionAsc(List.of(itemId));

    assertThat(gelesen).hasSize(2);
    NightRunAttemptEntity plan = gelesen.get(0);
    assertThat(plan.getNightRunItemId()).isEqualTo(itemId);
    assertThat(plan.getPosition()).isZero();
    assertThat(plan.getStage()).isEqualTo("PLAN");
    assertThat(plan.getCardNumber()).isEqualTo(1905);
    assertThat(plan.getModel()).isEqualTo("claude-opus-5-5");
    assertThat(plan.getEffort()).isEqualTo("high");
    assertThat(plan.isSucceeded()).isTrue();
    assertThat(plan.getVerbrauch().getCostUsd()).isEqualByComparingTo("1.5");
    assertThat(plan.getVerbrauch().getModelDurationMs()).isEqualTo(60_000L);
    assertThat(plan.getWrittenCardNumbers()).containsExactly(1946, 1944);
    assertThat(plan.isRunComplete()).isFalse();

    NightRunAttemptEntity pruefung = gelesen.get(1);
    assertThat(pruefung.getPosition()).isEqualTo(1);
    assertThat(pruefung.getModel()).isNull();
    assertThat(pruefung.getEffort()).isNull();
    assertThat(pruefung.isSucceeded()).isFalse();
    assertThat(pruefung.getVerbrauch().getCostUsd()).isNull();
    assertThat(pruefung.getWrittenCardNumbers()).isEmpty();
  }
}
