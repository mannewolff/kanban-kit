package org.mwolff.manban.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.ratelimit.infrastructure.persistence.OverloadRejectionTable;
import org.mwolff.manban.ratelimit.infrastructure.persistence.OverloadRejectionTable.HourKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Abweisungs-Aggregate gegen echtes PostgreSQL (Issue #1000, Plan #995 E14/E23): eine Zeile je
 * Person und Stunde, fortgeschrieben per {@code INSERT … ON CONFLICT DO UPDATE}, und nach 90 Tagen
 * aufgeräumt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OverloadRejectionIT extends AbstractIntegrationTest {

  private static final Instant HOUR = Instant.parse("2026-09-22T10:00:00Z");

  @Autowired private OverloadRejectionTable table;
  @Autowired private AppUserRepository users;
  @Autowired private DataSource dataSource;

  @Test
  void samePersonAndHour_accumulateInOneRow() {
    long user = user("a@example.com");

    table.add(Map.of(new HourKey(user, HOUR), 1));
    table.add(Map.of(new HourKey(user, HOUR), 1));

    assertThat(rows()).containsExactly(row(user, HOUR, 2));
  }

  @Test
  void differentHours_andDifferentPersons_getTheirOwnRows() {
    long first = user("a@example.com");
    long second = user("b@example.com");

    table.add(Map.of(new HourKey(first, HOUR), 1, new HourKey(second, HOUR), 4));
    table.add(Map.of(new HourKey(first, HOUR.plus(Duration.ofHours(1))), 2));

    assertThat(rows())
        .containsExactlyInAnyOrder(
            row(first, HOUR, 1),
            row(second, HOUR, 4),
            row(first, HOUR.plus(Duration.ofHours(1)), 2));
  }

  @Test
  void concurrentWrites_ofTheSamePersonAndHour_loseNoCount() throws Exception {
    // Given: Zwei Schreiber starten gleichzeitig auf eine noch nicht vorhandene Zeile — genau das
    // Rennen, das der Unique-Index zusammen mit ON CONFLICT DO UPDATE entscheiden muss.
    long user = user("race@example.com");
    int perWriter = 50;
    CountDownLatch gate = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      List<Future<?>> writers =
          List.of(
              pool.submit(() -> writeRepeatedly(gate, user, perWriter)),
              pool.submit(() -> writeRepeatedly(gate, user, perWriter)));
      gate.countDown();
      for (Future<?> writer : writers) {
        writer.get();
      }
    }

    assertThat(rows()).containsExactly(row(user, HOUR, 2 * perWriter));
  }

  @Test
  void deleteOlderThan_removesOnlyRowsBeforeTheCutoff() {
    long user = user("old@example.com");
    Instant cutoff = HOUR.minus(Duration.ofDays(90));
    table.add(
        Map.of(
            new HourKey(user, cutoff.minus(Duration.ofHours(1))), 1,
            new HourKey(user, cutoff), 2,
            new HourKey(user, HOUR), 3));

    int deleted = table.deleteOlderThan(cutoff);

    assertThat(deleted).isEqualTo(1);
    assertThat(rows()).containsExactlyInAnyOrder(row(user, cutoff, 2), row(user, HOUR, 3));
  }

  private void writeRepeatedly(CountDownLatch gate, long user, int times) {
    try {
      gate.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
    for (int i = 0; i < times; i++) {
      table.add(Map.of(new HourKey(user, HOUR), 1));
    }
  }

  private long user(String email) {
    return users.save(new AppUser(null, email, "hash", "A", true, PlatformRole.USER)).requireId();
  }

  private static String row(long user, Instant hour, int rejections) {
    return user + "|" + hour.truncatedTo(ChronoUnit.HOURS) + "|" + rejections;
  }

  private List<String> rows() {
    return new JdbcTemplate(dataSource)
        .query(
            "SELECT user_id, hour_bucket, rejections FROM overload_rejection",
            (rs, n) ->
                row(
                    rs.getLong("user_id"),
                    rs.getObject("hour_bucket", java.time.OffsetDateTime.class).toInstant(),
                    rs.getInt("rejections")));
  }
}
