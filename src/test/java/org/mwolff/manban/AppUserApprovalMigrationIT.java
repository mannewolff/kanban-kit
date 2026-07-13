package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Prüft, dass die Freigabe-Migration V6 bei bereits bestehenden Benutzern {@code approved_at}
 * nachträgt (Backfill, Issue #0097). Läuft isoliert in einem eigenen Schema: erst V1→V5 migrieren,
 * einen „Alt-Benutzer" einfügen, dann V6 anwenden und {@code approved_at} prüfen.
 */
class AppUserApprovalMigrationIT extends AbstractIntegrationTest {

  private static final String SCHEMA = "approval_backfill_it";

  private static Flyway flyway(DriverManagerDataSource ds, String target) {
    return Flyway.configure()
        .dataSource(ds)
        .schemas(SCHEMA)
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .target(target)
        .load();
  }

  @Test
  void v6BackfillsApprovedAtFromCreatedAt() throws Exception {
    DriverManagerDataSource ds =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    try {
      // Zustand vor der Freigabe-Migration: Schema bis V5, ein bestehender Benutzer.
      flyway(ds, "5").migrate();
      try (Connection c = ds.getConnection();
          Statement st = c.createStatement()) {
        st.execute("SET search_path TO " + SCHEMA);
        st.execute(
            "INSERT INTO app_user (email, password_hash, display_name, email_verified,"
                + " platform_role) VALUES ('legacy@example.com', 'h', 'Legacy', true, 'USER')");
      }

      // Freigabe-Migration anwenden.
      flyway(ds, "6").migrate();

      // Bestandsnutzer gilt danach als freigegeben: approved_at = created_at.
      try (Connection c = ds.getConnection();
          Statement st = c.createStatement()) {
        st.execute("SET search_path TO " + SCHEMA);
        try (ResultSet rs =
            st.executeQuery(
                "SELECT approved_at, created_at FROM app_user"
                    + " WHERE email = 'legacy@example.com'")) {
          if (!rs.next()) {
            throw new AssertionError("Bestandsnutzer nach Migration nicht gefunden");
          }
          OffsetDateTime approvedAt = rs.getObject("approved_at", OffsetDateTime.class);
          OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
          assertThat(approvedAt).isNotNull().isEqualTo(createdAt);
        }
      }
    } finally {
      Flyway.configure().dataSource(ds).schemas(SCHEMA).cleanDisabled(false).load().clean();
    }
  }
}
