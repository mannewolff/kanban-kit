package org.mwolff.manban;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Gemeinsame Basis aller {@code *IT}: eine geteilte Postgres- und MinIO-Instanz für die gesamte
 * Suite (Testcontainers-Singleton-Pattern, CLAUDE-java.md §6.4) statt Container pro Testklasse —
 * spart pro Klasse Container-Start und ermöglicht Spring-Context-Caching über einheitliche
 * Konfiguration.
 *
 * <p>Die Container werden bewusst im statischen Initialisierer gestartet (nicht über
 * {@code @Testcontainers}/{@code @Container}): die JUnit-Extension würde sie nach jeder Testklasse
 * stoppen. Ryuk räumt die Singletons am JVM-Ende ab.
 *
 * <p><strong>Datenisolation:</strong> Vor jeder Testmethode werden alle Tabellen (außer der
 * Flyway-Historie) geleert und die Sequenzen zurückgesetzt — jede Methode startet auf einer leeren
 * Datenbank, wie zuvor jede Klasse auf einem frischen Container. Die Testmethoden dieses Projekts
 * bauen ihre Fixtures selbst auf (kein methodenübergreifender Zustand, keine
 * {@code @TestMethodOrder}-Abhängigkeiten).
 */
// PMD.AbstractClassWithoutAbstractMethod: bewusst abstrakt ohne abstrakte Methode — die Klasse
// ist eine Infrastruktur-Basis (geteilte Container + DB-Reset) und darf nie selbst
// instanziiert/ausgeführt werden.
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class AbstractIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  static final MinIOContainer MINIO = new MinIOContainer("minio/minio");

  static {
    POSTGRES.start();
    MINIO.start();
  }

  /** Einheitliche Storage-Konfiguration für alle Kontexte (verbessert das Context-Caching). */
  @DynamicPropertySource
  static void objectStorageProperties(DynamicPropertyRegistry registry) {
    registry.add("manban.storage.endpoint", MINIO::getS3URL);
    registry.add("manban.storage.access-key", MINIO::getUserName);
    registry.add("manban.storage.secret-key", MINIO::getPassword);
  }

  /**
   * Leert alle Fachtabellen vor jeder Testmethode (Isolation wie zuvor: frische Datenbank).
   * Ausgenommen sind neben der Flyway-Historie die von den Migrationen geseedeten Referenztabellen
   * ({@code permission}, {@code role_permission}) — ohne deren Grants würde jede RBAC-Prüfung mit
   * 403 fehlschlagen.
   */
  @BeforeEach
  void resetDatabase() throws SQLException {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement()) {
      List<String> tables = new ArrayList<>();
      try (ResultSet resultSet =
          statement.executeQuery(
              "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"
                  + " AND tablename NOT IN"
                  + " ('flyway_schema_history', 'permission', 'role_permission')")) {
        while (resultSet.next()) {
          tables.add('"' + resultSet.getString(1) + '"');
        }
      }
      if (!tables.isEmpty()) {
        statement.execute(
            "TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
      }
    }
  }
}
