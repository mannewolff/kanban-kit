package org.mwolff.manban.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die Art {@code retention} in {@code backup_run} (Issue #830, Migration V41).
 *
 * <p>{@code backup/retention.sh} räumt Basissicherungen, WAL-Archive und unreferenzierte Anhänge
 * und protokolliert seinen Ausgang in derselben Tabelle wie die Sicherungsläufe. Diese Probe hält
 * beide Hälften der Entscheidung fest: Die Zeile darf entstehen — und sie darf den Stand der
 * <em>Sicherung</em> nicht verändern. Aufräumen sichert nichts; ein gescheiterter Verfall-Lauf über
 * einem vollständig gesicherten Stand als „Sicherung fehlgeschlagen" zu melden, wäre eine falsche
 * Aussage über das, wonach der Plattform-Admin fragt.
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
class BackupRunRetentionKindIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String ENDPOINT = "/api/admin/backup/status";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbc;

  private void lauf(String art, Duration vor, String ergebnis) {
    Instant start = Instant.now().minus(vor);
    jdbc.update(
        "INSERT INTO backup_run (kind, started_at, finished_at, outcome, detail, bytes)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        art,
        Timestamp.from(start),
        Timestamp.from(start.plusSeconds(30)),
        ergebnis,
        "fehlschlag".equals(ergebnis) ? "rclone: connection refused" : null,
        4096L);
  }

  private Cookie session(String email) throws Exception {
    users.save(
        new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, PlatformRole.ADMIN));
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }

  @Test
  void verfallLaufDarfProtokolliertWerden() {
    lauf("retention", Duration.ofHours(1), "erfolg");

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM backup_run WHERE kind = 'retention'", Long.class))
        .isEqualTo(1L);
  }

  @Test
  void eineErfundeneArtBleibtAbgewiesen() {
    assertThatThrownBy(() -> lauf("aufraeumen", Duration.ofHours(1), "erfolg"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * Der Kern der Entscheidung: Ein gescheitertes Aufräumen färbt die Ampel der Sicherung nicht.
   *
   * <p>Alle vier Sicherungsarten sind frisch und gelungen; nur der Verfall-Lauf ist gescheitert.
   * Das Urteil bleibt {@code OK}, und die Antwort zählt weiterhin vier Arten — der Lesepfad fragt
   * ausschließlich nach den Arten, die er kennt.
   */
  @Test
  void einGescheiterterVerfallLaufLaesstDenStandDerSicherungUnberuehrt() throws Exception {
    Cookie admin = session("backup-verfall@example.com");
    lauf("basis", Duration.ofHours(2), "erfolg");
    lauf("wal", Duration.ofMinutes(1), "erfolg");
    lauf("spiegel", Duration.ofMinutes(1), "erfolg");
    lauf("offsite", Duration.ofHours(2), "erfolg");
    lauf("retention", Duration.ofHours(2), "fehlschlag");

    mvc.perform(get(ENDPOINT).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verdict").value("OK"))
        .andExpect(jsonPath("$.kinds.length()").value(4));
  }
}
