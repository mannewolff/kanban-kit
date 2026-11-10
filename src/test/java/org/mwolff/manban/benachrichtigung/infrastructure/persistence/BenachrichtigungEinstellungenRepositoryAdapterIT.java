package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Die Projektschalter gegen echtes PostgreSQL (Issue #2091). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BenachrichtigungEinstellungenRepositoryAdapterIT extends AbstractIntegrationTest {

  private static final Instant SEIT = Instant.parse("2026-11-09T08:00:00Z");

  @Autowired private BenachrichtigungEinstellungenRepository einstellungen;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  @Test
  void vonDenAngefragtenProjektenKommenNurDieEingeschalteten() {
    long besitzer =
        id(
            "INSERT INTO app_user (email, display_name) VALUES (?, 'N') RETURNING id",
            "einstellungen@example.com");
    long eingeschaltet = projekt("E", besitzer);
    long nichtAngefragt = projekt("N", besitzer);
    long ausgeschaltet = projekt("A", besitzer);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              einstellungen.schalteProjektEin(eingeschaltet, SEIT);
              einstellungen.schalteProjektEin(nichtAngefragt, SEIT);
            });

    assertThat(einstellungen.eingeschalteteProjekte(List.of(eingeschaltet, ausgeschaltet)))
        .containsExactly(eingeschaltet);
  }

  private long projekt(String name, long besitzer) {
    return id(
        "INSERT INTO project (name, owner_user_id) VALUES (?, ?) RETURNING id", name, besitzer);
  }

  private long id(String sql, Object... argumente) {
    Long generated = new JdbcTemplate(dataSource).queryForObject(sql, Long.class, argumente);
    return generated == null ? 0L : generated;
  }
}
