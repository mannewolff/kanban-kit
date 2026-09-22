package org.mwolff.manban.backup;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/admin/backup/status} am laufenden Stack (Issue #826): Tabelle, Lesepfad und
 * Autorisierung zusammen.
 *
 * <p>Die Warnfristen ergeben sich aus dem hier eingestellten Rhythmus (Plan #825 E13): 03:00
 * täglich → 48 h für Basissicherung und Kopie außer Haus, 5 min Spiegeltakt → 10 min für Spiegel
 * und WAL-Archiv.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "manban.backup.enabled=true",
      "manban.backup.base-cron=0 0 3 * * *",
      "manban.backup.mirror-interval=PT5M",
      "manban.backup.target-label=Nextcloud"
    })
class BackupStatusIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String ENDPOINT = "/api/admin/backup/status";

  /** Reihenfolge der Arten in der Antwort — die Deklarationsreihenfolge von {@code BackupKind}. */
  private static final int BASIS = 0;

  private static final int WAL = 1;
  private static final int SPIEGEL = 2;
  private static final int OFFSITE = 3;

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbc;

  private void lauf(String art, Duration vor, String ergebnis) {
    Instant start = Instant.now().minus(vor);
    boolean gescheitert = "fehlschlag".equals(ergebnis);
    jdbc.update(
        "INSERT INTO backup_run (kind, started_at, finished_at, outcome, detail, bytes)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        art,
        Timestamp.from(start),
        Timestamp.from(start.plusSeconds(30)),
        ergebnis,
        gescheitert ? "rclone: connection refused" : null,
        gescheitert ? null : 4096L);
  }

  private void alleArtenFrisch() {
    lauf("basis", Duration.ofHours(2), "erfolg");
    lauf("wal", Duration.ofMinutes(1), "erfolg");
    lauf("spiegel", Duration.ofMinutes(1), "erfolg");
    lauf("offsite", Duration.ofHours(2), "erfolg");
  }

  /**
   * Meldet {@code email} mit der Rolle an. Die Sitzung entsteht über den übergebenen {@code
   * MockMvc}, damit die verschachtelte Klasse ihren eigenen Kontext benutzt statt des äußeren.
   */
  private Cookie session(MockMvc ueber, String email, PlatformRole role) throws Exception {
    users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, role));
    return ueber
        .perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }

  @Test
  void ok_wennJedeArtEinenFrischenErfolgHat() throws Exception {
    Cookie admin = session(mvc, "backup-ok@example.com", PlatformRole.ADMIN);
    alleArtenFrisch();

    mvc.perform(get(ENDPOINT).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verdict").value("OK"))
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.targetLabel").value("Nextcloud"))
        .andExpect(jsonPath("$.kinds.length()").value(4))
        .andExpect(jsonPath("$.kinds[" + BASIS + "].kind").value("BASIS"))
        .andExpect(jsonPath("$.kinds[" + BASIS + "].lastOutcome").value("ERFOLG"))
        .andExpect(jsonPath("$.kinds[" + BASIS + "].bytes").value(4096))
        .andExpect(jsonPath("$.kinds[" + BASIS + "].detail").isEmpty())
        .andExpect(jsonPath("$.kinds[" + BASIS + "].lastStartedAt").exists())
        .andExpect(jsonPath("$.kinds[" + BASIS + "].lastFinishedAt").exists())
        .andExpect(jsonPath("$.kinds[" + BASIS + "].warnAfterSeconds").value(172_800))
        .andExpect(jsonPath("$.kinds[" + BASIS + "].stale").value(false))
        .andExpect(jsonPath("$.kinds[" + WAL + "].kind").value("WAL"))
        .andExpect(jsonPath("$.kinds[" + WAL + "].warnAfterSeconds").value(600))
        .andExpect(jsonPath("$.kinds[" + SPIEGEL + "].kind").value("SPIEGEL"))
        .andExpect(jsonPath("$.kinds[" + SPIEGEL + "].warnAfterSeconds").value(600))
        .andExpect(jsonPath("$.kinds[" + OFFSITE + "].kind").value("OFFSITE"))
        .andExpect(jsonPath("$.kinds[" + OFFSITE + "].warnAfterSeconds").value(172_800));
  }

  @Test
  void veraltet_wennDerSpiegelLaengerAlsSeineWarnfristSchweigt() throws Exception {
    Cookie admin = session(mvc, "backup-alt@example.com", PlatformRole.ADMIN);
    lauf("basis", Duration.ofHours(2), "erfolg");
    lauf("wal", Duration.ofMinutes(1), "erfolg");
    lauf("offsite", Duration.ofHours(2), "erfolg");
    lauf("spiegel", Duration.ofMinutes(30), "erfolg");

    mvc.perform(get(ENDPOINT).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verdict").value("VERALTET"))
        .andExpect(jsonPath("$.kinds[" + SPIEGEL + "].stale").value(true))
        .andExpect(jsonPath("$.kinds[" + BASIS + "].stale").value(false));
  }

  @Test
  void veraltet_auchWennNochNieEinLaufProtokolliertWurde() throws Exception {
    Cookie admin = session(mvc, "backup-leer@example.com", PlatformRole.ADMIN);

    mvc.perform(get(ENDPOINT).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verdict").value("VERALTET"))
        .andExpect(jsonPath("$.kinds[" + BASIS + "].lastStartedAt").isEmpty())
        .andExpect(jsonPath("$.kinds[" + BASIS + "].lastSuccessAt").isEmpty())
        .andExpect(jsonPath("$.kinds[" + BASIS + "].ageSeconds").isEmpty())
        .andExpect(jsonPath("$.kinds[" + BASIS + "].stale").value(true));
  }

  @Test
  void fehlgeschlagen_wennDerJuengsteLaufEinerArtScheiterte() throws Exception {
    Cookie admin = session(mvc, "backup-kaputt@example.com", PlatformRole.ADMIN);
    alleArtenFrisch();
    lauf("offsite", Duration.ofMinutes(1), "fehlschlag");

    mvc.perform(get(ENDPOINT).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verdict").value("FEHLGESCHLAGEN"))
        .andExpect(jsonPath("$.kinds[" + OFFSITE + "].lastOutcome").value("FEHLSCHLAG"))
        .andExpect(jsonPath("$.kinds[" + OFFSITE + "].detail").value("rclone: connection refused"))
        .andExpect(jsonPath("$.kinds[" + OFFSITE + "].bytes").isEmpty())
        // Der ältere Erfolg bleibt sichtbar — an ihm hängt die Frist, nicht am Fehlschlag.
        .andExpect(jsonPath("$.kinds[" + OFFSITE + "].lastSuccessAt").exists())
        .andExpect(jsonPath("$.kinds[" + OFFSITE + "].stale").value(false));
  }

  @Test
  void werNurGewoehnlicherNutzerIstBekommt403() throws Exception {
    Cookie nutzer = session(mvc, "backup-fremd@example.com", PlatformRole.USER);

    mvc.perform(get(ENDPOINT).cookie(nutzer)).andExpect(status().isForbidden());
  }

  /**
   * Der ausgelieferte Zustand (Plan #825 E6): ohne das Backup-Overlay läuft keine Sicherung, und
   * die Ansicht sagt das unübersehbar — statt „kaputt" zu melden, was sie nicht ist.
   *
   * <p>Eigenes {@code @TestPropertySource} und damit ein zweiter Spring-Kontext: Der Schalter
   * steckt in einer {@code @ConfigurationProperties}-Bean, die beim Kontextstart entsteht.
   */
  @Nested
  @TestPropertySource(properties = "manban.backup.enabled=false")
  class OhneEingeschalteteSicherung {

    @Autowired private MockMvc ausgeschaltetesMvc;

    @Test
    void abgeschaltet_auchWennEinGescheiterterLaufImProtokollSteht() throws Exception {
      Cookie admin = session(ausgeschaltetesMvc, "backup-aus@example.com", PlatformRole.ADMIN);
      lauf("basis", Duration.ofMinutes(1), "fehlschlag");

      ausgeschaltetesMvc
          .perform(get(ENDPOINT).cookie(admin))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.verdict").value("ABGESCHALTET"))
          .andExpect(jsonPath("$.enabled").value(false));
    }
  }
}
