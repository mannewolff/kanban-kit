package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Farbwahl am Konto (Issue #1911) gegen die laufende Anwendung samt Filterkette und echtem
 * Postgres: Vorgabe eines frischen Kontos, Speichern über {@code PUT /api/me/color-scheme},
 * Problem-Detail bei unbekanntem Wert und 401 ohne Anmeldung. Dazu die Migration V61 an einem
 * Bestandskonto, das vor ihr angelegt wurde.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MeColorSchemeIT extends AbstractIntegrationTest {

  private static final String PFAD = "/api/me/color-scheme";
  private static final String SCHEMA = "color_scheme_it";

  @Autowired private MockMvc mvc;

  @Autowired private TestAnmeldung anmeldung;

  @Test
  void freshAccountReportsSystem() throws Exception {
    anmeldung.konto("frisch@example.com", PlatformRole.USER);
    Cookie session = anmeldung.perCode(mvc, "frisch@example.com");

    mvc.perform(get("/api/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.colorScheme").value("SYSTEM"));
  }

  @Test
  void putDarkIsStoredAndReturned() throws Exception {
    anmeldung.konto("dunkel@example.com", PlatformRole.USER);
    Cookie session = anmeldung.perCode(mvc, "dunkel@example.com");

    mvc.perform(
            put(PFAD)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"colorScheme\":\"DARK\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.colorScheme").value("DARK"))
        .andExpect(jsonPath("$.email").value("dunkel@example.com"));

    mvc.perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.colorScheme").value("DARK"));
  }

  @Test
  void unknownValueIsBadRequestAsProblemDetail() throws Exception {
    anmeldung.konto("unbekannt@example.com", PlatformRole.USER);
    Cookie session = anmeldung.perCode(mvc, "unbekannt@example.com");

    mvc.perform(
            put(PFAD)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"colorScheme\":\"PURPLE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(ApiSchemas.PROBLEM_JSON));

    mvc.perform(get("/api/me").cookie(session))
        .andExpect(jsonPath("$.colorScheme").value("SYSTEM"));
  }

  @Test
  void withoutLoginIsUnauthorized() throws Exception {
    mvc.perform(
            put(PFAD).contentType(MediaType.APPLICATION_JSON).content("{\"colorScheme\":\"DARK\"}"))
        .andExpect(status().isUnauthorized());
  }

  /** Ein Konto, das vor V61 bestand, trägt danach „System“ — also das bisherige Verhalten. */
  @Test
  void migrationGivesExistingAccountSystem() {
    DriverManagerDataSource ds =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    ds.setSchema(SCHEMA);
    try {
      flyway(ds, "60").migrate();
      JdbcTemplate jdbc = new JdbcTemplate(ds);
      jdbc.update(
          "INSERT INTO app_user (email, display_name, email_verified, platform_role)"
              + " VALUES ('bestand@example.com', 'Bestand', true, 'USER')");

      flyway(ds, "61").migrate();

      assertThat(
              jdbc.queryForObject(
                  "SELECT color_scheme FROM app_user WHERE email = 'bestand@example.com'",
                  String.class))
          .isEqualTo("SYSTEM");
    } finally {
      Flyway.configure().dataSource(ds).schemas(SCHEMA).cleanDisabled(false).load().clean();
    }
  }

  private static Flyway flyway(DriverManagerDataSource ds, String target) {
    return Flyway.configure()
        .dataSource(ds)
        .schemas(SCHEMA)
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .target(target)
        .load();
  }
}
