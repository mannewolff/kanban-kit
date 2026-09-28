package org.mwolff.manban;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
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
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Gemeinsame Basis aller {@code *IT}: eine geteilte Postgres- und SeaweedFS-Instanz für die gesamte
 * Suite (Testcontainers-Singleton-Pattern, CLAUDE-java.md §6.4) statt Container pro Testklasse —
 * spart pro Klasse Container-Start und ermöglicht Spring-Context-Caching über einheitliche
 * Konfiguration.
 *
 * <p><strong>Objektspeicher:</strong> SeaweedFS statt MinIO (Plan #1222, E1) — das MinIO-Abbild ist
 * anonym nicht mehr beziehbar, womit weder eine Neuinstallation nach {@code docs/betrieb.md} noch
 * {@code mvn verify} den Speicher hochfahren konnte. Der Client {@code io.minio} bleibt (E3): Er
 * ist ein S3-Client und an keine Bezugsquelle eines Abbilds gebunden.
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
// Zaehlbremse in IT-Kontexten grundsaetzlich aus (Issue #899): 45 IT-Klassen melden sich an, vier
// registrieren, zwei fordern ein neues Passwort an — alle von derselben Herkunft 127.0.0.1. Ihr
// Zaehlstand steht im Arbeitsspeicher und ueberlebt den TRUNCATE aus resetDatabase(), weil er gar
// nicht in der Datenbank steht. Ohne diesen Schalter liefe die Suite nach wenigen Klassen in 429.
// Wer die Bremse selbst prueft, setzt sie in seiner Testklasse wieder an.
@TestPropertySource(
    properties = {
      "manban.outbox.enabled=false",
      "manban.dev-mode=true",
      "manban.ratelimit.enabled=false",
      // Dasselbe fuer die Durchsatzbremse (Issue #1002): Ihr Zaehlstand liegt ebenfalls im
      // Arbeitsspeicher, und nach RESTART IDENTITY beginnen die userIds jeder Klasse wieder bei 1 —
      // die Klassen zaehlten also auf dasselbe Kontingent. ThroughputFilterIT schaltet sie an.
      "manban.ratelimit.throughput.enabled=false"
    })
public abstract class AbstractIntegrationTest {

  // max_connections hochgesetzt (Issue #900): Spring cached Testkontexte ueber den ganzen Lauf,
  // statt sie zu schliessen, und jeder haelt einen Hikari-Pool mit der Standardgroesse 10. Die 77
  // IT-Klassen teilen sich zwar wenige Kontexte, aber jede Klasse mit eigenem @TestPropertySource
  // macht einen neuen auf. Mit der Vorgabe 100 riss das Budget beim siebten Kontext, und zwar nicht
  // bei ihm selbst, sondern bei allem, was danach lief: `FATAL: sorry, too many clients already`,
  // ganze Klassen mit ERROR statt Failure. Bewusst hier und nicht als kleinerer Pool je Kontext —
  // das aenderte das Verhalten der vier Nebenlaeufigkeits-ITs, die parallele Verbindungen brauchen.
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.15")
          .withCommand("postgres", "-c", "max_connections=200");

  /** S3-Identitäten des Speicher-Containers; zugleich Quelle der Zugangsdaten unten. */
  private static final String IDENTITAETSDATEI = "objektspeicher-identitaeten.json";

  /** Vorgabewert aus {@code ObjectStorageProperties} — die IT-Suite überschreibt ihn nicht. */
  private static final String BUCKET = "manban";

  /** S3-Port von {@code weed server -s3}. */
  private static final int S3_PORT = 8333;

  private static final JsonNode ZUGANGSDATEN = ladeZugangsdaten();

  // SeaweedFS statt MinIO (Plan #1222, E1): Das Abbild `quay.io/minio/minio` ist anonym nicht mehr
  // beziehbar (anonymes Pull-Token mit leerer Aktionsliste, Manifest-Abruf mit 401) — damit stand
  // die gesamte IT-Suite. `chrislusf/seaweedfs` liefert sein Manifest anonym aus, ist Apache-2.0
  // und läuft als ein Prozess. Der Tag steht fest, aus demselben Grund wie zuvor: Ein beweglicher
  // `latest` ist genau das, was hier ohne Vorwarnung verschwunden ist.
  // Bewusst `GenericContainer` und nicht das abgelegte MinIO-Modul von Testcontainers (E5):
  // Dessen Container prüft den Abbildnamen im Konstruktor (assertCompatibleWith) und setzt
  // MinIO-eigene Umgebungsvariablen, die dieser Speicher nicht liest.
  // Die Wartestrategie liest das Protokoll des Containers statt den S3-Port anzufragen: Der
  // Speicher legt seine Portweiterleitung auf IPv4 **und** IPv6, antwortet aber nur über IPv4 —
  // `getHost()` liefert `localhost`, die Test-JVM löst das nach `::1` auf, und der HTTP-Client von
  // `Wait.forHttp` bekommt dort eine angenommene und sofort geschlossene Verbindung
  // (`Unexpected end of file from server`), ohne auf IPv4 auszuweichen. Die Meldung erscheint,
  // sobald der S3-Dienst lauscht; er startet laut eigenem Protokoll erst nach dem Filer, darum
  // genügt danach ein einzelner Anlauf für den Bucket.
  static final GenericContainer<?> OBJEKTSPEICHER =
      new GenericContainer<>("chrislusf/seaweedfs:4.47")
          .withExposedPorts(S3_PORT)
          .withCopyFileToContainer(
              MountableFile.forClasspathResource(IDENTITAETSDATEI), "/etc/seaweedfs/s3.json")
          .withCommand("server", "-s3", "-s3.config=/etc/seaweedfs/s3.json", "-dir=/data")
          .waitingFor(Wait.forLogMessage(".*Start Seaweed S3 API Server.*", 1));

  static {
    POSTGRES.start();
    OBJEKTSPEICHER.start();
    erzeugeBucket();
  }

  /**
   * Legt den Anhang-Bucket beim Hochfahren an — nicht die Anwendung (Plan #1222, E16). Deren
   * Identität trägt nur {@code Read}, {@code Write} und {@code List} auf diesen Bucket; {@code
   * PutBucket} verlangt in SeaweedFS das Recht {@code Admin}, und Wurzelrechte für einen einmaligen
   * Anlegeschritt wären eine Verletzung von Priorität 1.
   */
  private static void erzeugeBucket() {
    try {
      ExecResult ergebnis =
          OBJEKTSPEICHER.execInContainer(
              "sh",
              "-c",
              "echo 's3.bucket.create -name " + BUCKET + "' | weed shell -master=localhost:9333");
      if (ergebnis.getExitCode() != 0 || !ergebnis.getStdout().contains("created bucket")) {
        throw new IllegalStateException(
            "Bucket %s nicht angelegt (Exitcode %d): %s%s"
                .formatted(
                    BUCKET, ergebnis.getExitCode(), ergebnis.getStdout(), ergebnis.getStderr()));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Bucket " + BUCKET + " nicht angelegt", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Bucket " + BUCKET + " nicht angelegt", e);
    }
  }

  /** Zugangsdaten der Anwendungs-Identität — eine Quelle: die Identitätsdatei des Containers. */
  private static JsonNode ladeZugangsdaten() {
    try (InputStream datei =
        AbstractIntegrationTest.class.getResourceAsStream("/" + IDENTITAETSDATEI)) {
      if (datei == null) {
        throw new IllegalStateException(IDENTITAETSDATEI + " liegt nicht im Test-Klassenpfad");
      }
      return new ObjectMapper().readTree(datei).get("identities").get(0).get("credentials").get(0);
    } catch (IOException e) {
      throw new IllegalStateException(IDENTITAETSDATEI + " nicht lesbar", e);
    }
  }

  /** Einheitliche Storage-Konfiguration für alle Kontexte (verbessert das Context-Caching). */
  @DynamicPropertySource
  static void objectStorageProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "manban.storage.endpoint",
        () ->
            "http://%s:%d"
                .formatted(OBJEKTSPEICHER.getHost(), OBJEKTSPEICHER.getMappedPort(S3_PORT)));
    registry.add("manban.storage.access-key", () -> ZUGANGSDATEN.get("accessKey").asText());
    registry.add("manban.storage.secret-key", () -> ZUGANGSDATEN.get("secretKey").asText());
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
