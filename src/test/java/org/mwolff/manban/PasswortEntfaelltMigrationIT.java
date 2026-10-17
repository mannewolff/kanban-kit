package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prüft {@code V58__passwort_entfaellt.sql} (Issue #1817, Plan #1801 A11, A14) gegen einen
 * Datenbestand, der <b>vor</b> der Migration angelegt wurde: in einem eigenen Schema bis V57
 * migriert, Bestand angelegt, dann V58 angewendet.
 *
 * <p>Was die Migration an der Sitzung bewirkt, zeigt der zweite Teil des Szenarios an der laufenden
 * Anwendung: Ihr Schema steht schon auf V58, darum erhöht der Test dort die Sitzungs-Generation des
 * Kontos genau so, wie es die Migration für alle Konten tut, und prüft Cookie, Code-Anmeldung und
 * Zugriffstoken über HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PasswortEntfaelltMigrationIT extends AbstractIntegrationTest {

  private static final String SCHEMA = "passwort_entfaellt_it";
  private static final String KONTO = "bestand@example.com";
  private static final String TOKEN_HEADER = "X-Kanban-Token";

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ProjectService projects;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private DriverManagerDataSource bestand;
  private JdbcTemplate imBestand;

  @BeforeEach
  void bestandBisV57() {
    bestand =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    bestand.setSchema(SCHEMA);
    imBestand = new JdbcTemplate(bestand);
    flyway("57").migrate();
  }

  @AfterEach
  void schemaRaeumen() {
    Flyway.configure().dataSource(bestand).schemas(SCHEMA).cleanDisabled(false).load().clean();
  }

  @Test
  @DisplayName("Szenario: Bestehendes Konto nach der Umstellung")
  void bestehendesKontoNachDerUmstellung() throws Exception {
    // Bestand vor V58: Konto mit Passwort, Owner eines Projekts, mit Zugriffstoken.
    long konto =
        imBestand.queryForObject(
            "INSERT INTO app_user (email, password_hash, display_name, email_verified,"
                + " platform_role) VALUES (?, 'argon2-hash', 'Bestand', false, 'USER')"
                + " RETURNING id",
            Long.class,
            KONTO);
    long projekt =
        imBestand.queryForObject(
            "INSERT INTO project (name, owner_user_id) VALUES ('Bestand', ?) RETURNING id",
            Long.class,
            konto);
    imBestand.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, 'OWNER')",
        projekt,
        konto);
    imBestand.update(
        "INSERT INTO kanban_access_token (user_id, name, token_hash) VALUES (?, 'Runner', 'h')",
        konto);
    long generationVorher = generation(konto);

    flyway("58").migrate();

    assertThat(
            imBestand.queryForList(
                "SELECT column_name FROM information_schema.columns"
                    + " WHERE table_schema = ? AND column_name LIKE '%password%'",
                String.class, SCHEMA))
        .as("keine Spalte mehr, die nach einem Passwort fragt")
        .isEmpty();
    assertThat(
            imBestand.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?"
                    + " AND table_name IN ('password_reset_token', 'email_verification_token')",
                String.class,
                SCHEMA))
        .as("Token-Tabellen von Passwort-Reset und Verifikation")
        .isEmpty();
    assertThat(
            imBestand.query(
                "SELECT email, display_name, email_verified, platform_role FROM app_user",
                (rs, row) ->
                    tuple(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getString(4))))
        .as("das Konto bleibt, wie es war")
        .containsExactly(tuple(KONTO, "Bestand", false, "USER"));
    assertThat(
            imBestand.query(
                "SELECT project_id, user_id, role FROM project_membership",
                (rs, row) -> tuple(rs.getLong(1), rs.getLong(2), rs.getString(3))))
        .as("Mitgliedschaft und Rolle bleiben")
        .containsExactly(tuple(projekt, konto, "OWNER"));
    assertThat(generation(konto)).as("alle Sitzungen enden").isEqualTo(generationVorher + 1);
    assertThat(
            imBestand.queryForObject(
                "SELECT count(*) FROM kanban_access_token WHERE user_id = ? AND NOT revoked",
                Integer.class,
                konto))
        .as("das Zugriffstoken bleibt")
        .isOne();

    // An der laufenden Anwendung: angemeldet, Owner, mit Zugriffstoken.
    long kontoId = anmeldung.konto(KONTO, PlatformRole.USER).requireId();
    long admin = anmeldung.konto("admin@example.com", PlatformRole.ADMIN).requireId();
    long projektId = projects.create(admin, "Projekt des Bestands", KONTO).id();
    Cookie altesCookie = anmeldung.anmelden(KONTO, PlatformRole.USER);
    String token = zugriffstoken(altesCookie);

    // Was V58 für jedes Konto tut.
    jdbc.update(
        "UPDATE app_user SET session_generation = session_generation + 1 WHERE id = ?", kontoId);

    mvc.perform(get("/api/me").cookie(altesCookie)).andExpect(status().isUnauthorized());
    Cookie neuesCookie = anmeldung.perCode(mvc, KONTO);
    JsonNode ich = me(neuesCookie);
    assertThat(ich.get("email").asText()).isEqualTo(KONTO);
    assertThat(ich.toString())
        .as("weiterhin Owner desselben Projekts")
        .contains("\"projectId\":%d,\"role\":\"OWNER\"".formatted(projektId));
    mvc.perform(get("/api/me").header(TOKEN_HEADER, token)).andExpect(status().isOk());
  }

  @Test
  void offeneMailsDerWeggefallenenTypenEntfallen() {
    outbox("mail.password-reset", "PENDING");
    outbox("mail.verification", "PENDING");
    outbox("mail.verification", "DONE");
    outbox("mail.login-code", "PENDING");

    flyway("58").migrate();

    assertThat(
            imBestand.query(
                "SELECT event_type, status FROM outbox_entry ORDER BY id",
                (rs, row) -> tuple(rs.getString(1), rs.getString(2))))
        .as("nur offene Einträge der weggefallenen Typen entfallen")
        .containsExactly(tuple("mail.verification", "DONE"), tuple("mail.login-code", "PENDING"));
  }

  private Flyway flyway(String target) {
    return Flyway.configure()
        .dataSource(bestand)
        .schemas(SCHEMA)
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .target(target)
        .load();
  }

  private long generation(long konto) {
    return imBestand.queryForObject(
        "SELECT session_generation FROM app_user WHERE id = ?", Long.class, konto);
  }

  private void outbox(String typ, String status) {
    imBestand.update(
        "INSERT INTO outbox_entry (event_type, idempotency_key, payload, status)"
            + " VALUES (?, ?, 'nutzlast', ?)",
        typ,
        typ + ":" + status,
        status);
  }

  private String zugriffstoken(Cookie sitzung) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(sitzung)
                    .contentType("application/json")
                    .content("{\"name\":\"Runner\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("plaintext").asText();
  }

  private JsonNode me(Cookie sitzung) throws Exception {
    return json.readTree(
        mvc.perform(get("/api/me").cookie(sitzung))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8));
  }
}
