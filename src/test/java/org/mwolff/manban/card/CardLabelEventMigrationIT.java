package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V55__card_label_event.sql} (Issue #1754, Plan #1752 E7): Der Beginn der
 * Label-Erfassung steht danach in genau einer Zeile.
 *
 * <p>Eigener Container statt {@link org.mwolff.manban.AbstractIntegrationTest}: Deren Reset leert
 * vor jeder Methode alle Tabellen — ob die Migration die Zeile anlegt, ist dort nicht mehr zu
 * sehen.
 */
class CardLabelEventMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.15");

  private static JdbcTemplate jdbc;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void migriereBisV55() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target("55")
        .load()
        .migrate();
  }

  @Test
  void derErfassungsbeginnStehtInGenauEinerZeile() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM card_label_event_capture", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT since FROM card_label_event_capture", OffsetDateTime.class))
        .isNotNull();
  }
}
