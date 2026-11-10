package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.LaufAnlass;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.NeueZustellung;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Das Löschen abgelaufener Anlässe gegen echtes PostgreSQL (Issue #2081, Plan #2070 E14). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AnlassAufbewahrungIT extends AbstractIntegrationTest {

  private static final Instant EINTRITT = Instant.parse("2026-11-09T08:00:00Z");
  private static final Instant ALT = Instant.parse("2026-11-09T08:01:00Z");
  private static final Instant GRENZE = Instant.parse("2026-11-09T08:02:00Z");

  @Autowired private AnlassRepository anlaesse;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private long person;
  private long projekt;
  private long lauf;

  @BeforeEach
  void legeBestandAn() {
    jdbc = new JdbcTemplate(dataSource);
    person =
        id("INSERT INTO app_user (email, display_name) VALUES ('p@example.com', 'N') RETURNING id");
    projekt = id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", person);
    lauf =
        id(
            "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
                + " skipped_count, unparsed_count, created_at)"
                + " VALUES (?, now(), 'IMPLEMENTATION', 1000, 1, 0, 0, now()) RETURNING id",
            projekt);
  }

  @Test
  void dasLoeschenNimmtAlteAnlaesseMitIhrenZustellungenUndLaesstJuengereStehen() {
    long alt = anlass(AnlassArt.UEBERSICHT, ALT);
    long aufDerGrenze = anlass(AnlassArt.STOERUNG, GRENZE);

    assertThat(anlaesse.loescheAelterAls(GRENZE)).isEqualTo(1);

    assertThat(zustellungen(alt)).isZero();
    assertThat(zustellungen(aufDerGrenze)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM benachrichtigung_anlass", Integer.class))
        .isEqualTo(1);
  }

  private long anlass(AnlassArt art, Instant angelegtAt) {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              long anlass =
                  anlaesse
                      .legeAnlassZumLaufAn(
                          new LaufAnlass(art, projekt, lauf, null, EINTRITT, angelegtAt))
                      .orElseThrow();
              anlaesse.legeZustellungAn(new NeueZustellung(anlass, person, EINTRITT, false));
              return anlass;
            });
  }

  private Integer zustellungen(long anlass) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM benachrichtigung_zustellung WHERE anlass_id = ?",
        Integer.class,
        anlass);
  }

  private long id(String sql, Object... argumente) {
    Long generated = jdbc.queryForObject(sql, Long.class, argumente);
    return generated == null ? 0L : generated;
  }
}
