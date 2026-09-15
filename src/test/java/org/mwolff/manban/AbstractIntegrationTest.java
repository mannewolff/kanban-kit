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
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

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
// Outbox-Worker in IT-Kontexten grundsätzlich aus (Issue #502): Spring cacht die Kontexte über
// Klassen hinweg, und ein weiterlaufender @Scheduled-Worker eines FRÜHEREN Kontexts würde
// Einträge in der geteilten Datenbank per SKIP LOCKED wegschnappen — Tests, die den Durchlauf
// deterministisch selbst aufrufen, verlören dann sporadisch ihre Einträge. Bewusst per
// @TestPropertySource statt @DynamicPropertySource: Nur hier überschreibt eine Subklassen-
// Deklaration (SmtpMailIT testet den echten Worker-Pfad) verlässlich den Basiswert.
// "Test" ist die Betriebsart, die AK 2 aus Issue #839 von der Startprüfung des Sitzungsschlüssels
// ausnimmt (Issue #890): Der Testbetrieb signiert mit dem Standardschlüssel, und das ist hier
// gewollt. Der Schalter steht in der gemeinsamen Basis, weil alle @SpringBootTest-Klassen von ihr
// erben und Spring @TestPropertySource über die Klassenhierarchie zusammenführt (inheritProperties
// ist per Vorgabe true) — die vier Subklassen mit eigener Deklaration (MailOutboxIT, OutboxIT,
// SmtpMailIT, BootstrapIT) setzen andere Schlüssel, keine davon manban.dev-mode. Bewusst KEINE
// src/test/resources/application.yml: Sie überlagerte die Produktions-application.yml global und
// verschöbe damit auch jede künftige Vorgabe unbemerkt.
@TestPropertySource(properties = {"manban.outbox.enabled=false", "manban.dev-mode=true"})
public abstract class AbstractIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  // Das Image kommt von quay.io, nicht von Docker Hub: `minio/minio` existiert dort nicht mehr
  // (404 beim Pull), was die gesamte IT-Suite lahmlegte. Der Tag steht fest — ein beweglicher
  // `latest` war genau das, was hier ohne Vorwarnung verschwunden ist.
  // `asCompatibleSubstituteFor` ist nötig, weil MinIOContainer den Namen im Konstruktor gegen
  // `minio/minio` prüft (assertCompatibleWith) und eine fremde Registry sonst ablehnt.
  static final MinIOContainer MINIO =
      new MinIOContainer(
          DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z")
              .asCompatibleSubstituteFor("minio/minio"));

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
