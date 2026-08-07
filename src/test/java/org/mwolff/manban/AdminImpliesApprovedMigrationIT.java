package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Prüft, dass die Migration V25 den Bestand heilt (Issue #558): ein Plattform-Admin ohne Freigabe —
 * typisch für den dokumentierten Datenbank-Weg zum Erst-Admin — bekommt {@code approved_at}
 * nachgestempelt, während {@code approved_by} bewusst NULL bleibt. Läuft isoliert in einem eigenen
 * Schema: erst V1→V24 migrieren, den Bestand einfügen, dann V25 anwenden.
 */
class AdminImpliesApprovedMigrationIT extends AbstractIntegrationTest {

  private static final String SCHEMA = "admin_approved_backfill_it";
  private static final Instant APPROVED_EARLIER = Instant.parse("2020-01-01T00:00:00Z");

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
  void v25StampsPendingAdminsAndLeavesEveryoneElseUntouched() throws Exception {
    DriverManagerDataSource ds =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    try {
      // Zustand vor der Heilung: Schema bis V24, drei Bestandszeilen.
      flyway(ds, "24").migrate();
      try (Connection c = ds.getConnection();
          Statement st = c.createStatement()) {
        st.execute("SET search_path TO " + SCHEMA);
        st.execute(
            "INSERT INTO app_user (email, password_hash, display_name, email_verified,"
                + " platform_role, approved_at) VALUES"
                + " ('pending-admin@example.com', 'h', 'Pending Admin', true, 'ADMIN', NULL),"
                + " ('pending-user@example.com', 'h', 'Pending User', true, 'USER', NULL),"
                + " ('approved-admin@example.com', 'h', 'Approved Admin', true, 'ADMIN',"
                + " timestamptz '2020-01-01T00:00:00Z')");
      }

      // Heilende Migration anwenden.
      flyway(ds, "25").migrate();

      try (Connection c = ds.getConnection();
          Statement st = c.createStatement()) {
        st.execute("SET search_path TO " + SCHEMA);

        // Der wartende Admin ist jetzt freigegeben — ohne freigebenden Admin.
        assertThat(approvedAt(st, "pending-admin@example.com")).isNotNull();
        assertThat(approvedBy(st, "pending-admin@example.com")).isNull();

        // Ein wartender USER bleibt wartend.
        assertThat(approvedAt(st, "pending-user@example.com")).isNull();

        // Ein bereits freigegebener Admin behält seinen ursprünglichen Zeitstempel.
        OffsetDateTime approvedEarlier = approvedAt(st, "approved-admin@example.com");
        assertThat(approvedEarlier).isNotNull();
        assertThat(approvedEarlier.toInstant()).isEqualTo(APPROVED_EARLIER);
      }
    } finally {
      Flyway.configure().dataSource(ds).schemas(SCHEMA).cleanDisabled(false).load().clean();
    }
  }

  private static OffsetDateTime approvedAt(Statement st, String email) throws Exception {
    return column(st, "approved_at", email, OffsetDateTime.class);
  }

  private static Long approvedBy(Statement st, String email) throws Exception {
    return column(st, "approved_by", email, Long.class);
  }

  private static <T> T column(Statement st, String name, String email, Class<T> type)
      throws Exception {
    try (ResultSet rs =
        st.executeQuery("SELECT " + name + " FROM app_user WHERE email = '" + email + "'")) {
      if (!rs.next()) {
        throw new AssertionError("Bestandszeile nach Migration nicht gefunden: " + email);
      }
      return rs.getObject(name, type);
    }
  }
}
