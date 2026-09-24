package org.mwolff.manban.nightrun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die Endpunkte des Plattform-Leitstands über HTTP (Issue #1080, auf einen Endpunkt für drei Listen
 * umgestellt in #1095).
 *
 * <p>Vier Zusagen, die nur hier belegbar sind: Wer <b>nicht</b> Plattform-Admin ist, bekommt 403
 * (AK 3); ein Plattform-Admin liest die Störungen eines teilnehmenden Projekts <b>ohne jede
 * Mitgliedschaft</b> (AK 7); ein <b>ungebundenes Token</b> erreicht beide Endpunkte nicht; und die
 * Zone wird an der Bindung geprüft — eine Offset-Zone ist 400 wie bei der Verbrauchs-Auswertung.
 *
 * <p>Das Token ist der Grund für den Pfadstamm {@code /api/admin} (Plan #1072 E24) und zieht die
 * Grenze nicht am Endpunkt, sondern in der Filterkette: {@code SecurityConfig} verlangt dort eine
 * Sitzung. Läge der Leitstand unter {@code /api/platform/...}, fiele er unter die Auffangregel
 * {@code /api/**} — und wäre mit einem Token erreichbar, das niemand dafür ausgestellt hat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DisruptionEndpointIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String LEITSTAND = "/api/admin/leitstand";
  private static final String QUITTIEREN = "/api/admin/disruptions";
  private static final String ZONE = "Europe/Berlin";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;
  private long laufId;

  private Cookie session(String email, PlatformRole role) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, role));
    }
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }

  private long id(String sql, Object... args) {
    Long wert = jdbc.queryForObject(sql, Long.class, args);
    return wert == null ? 0L : wert;
  }

  /** Ein teilnehmendes Projekt mit genau einer offenen Störung — ohne Mitgliedschaft des Admins. */
  @BeforeEach
  void seed() {
    long owner =
        id(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('de-owner@example.com', 'x', 'O') RETURNING id");
    projectId =
        id(
            "INSERT INTO project (name, owner_user_id, dashboard_participation)"
                + " VALUES ('Gestoertes Projekt', ?, true) RETURNING id",
            owner);
    laufId =
        id(
            "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms,"
                + " processed_count, skipped_count, unparsed_count, created_at, origin, complete)"
                + " VALUES (?, now(), 'IMPLEMENTATION', 'NIGHT', 1, 1, 0, 0, now(), 'UPLOAD', true)"
                + " RETURNING id",
            projectId);
    jdbc.update(
        "INSERT INTO night_run_item (night_run_id, project_id, started_at, mode, kind, card_number,"
            + " title, state, error_class) VALUES (?, ?, now(), 'IMPLEMENTATION', 'NIGHT', 721,"
            + " 'Paket', 'RED', 'CHECKS_RED')",
        laufId,
        projectId);
  }

  /**
   * AK 7: kein Mitglied des Projekts, trotzdem die Störung — die Teilnahme ist die Einwilligung.
   *
   * <p>Derselbe Lauf steht zugleich unter den <b>durchgeführten</b>: Er ist abgeschlossen und
   * startete eben, liegt also in der laufenden Nacht. Dass beide Listen aus <b>einer</b> Antwort
   * kommen, ist die Zusage aus Plan #1088 E5.
   */
  @Test
  void derPlattformAdminLiestDieStoerungOhneMitgliedschaft() throws Exception {
    Cookie admin = session("de-admin@example.com", PlatformRole.ADMIN);

    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stoerungen.length()").value(1))
        .andExpect(jsonPath("$.stoerungen[0].nightRunId").value(laufId))
        .andExpect(jsonPath("$.stoerungen[0].projectId").value(projectId))
        .andExpect(jsonPath("$.stoerungen[0].projectName").value("Gestoertes Projekt"))
        .andExpect(jsonPath("$.stoerungen[0].outcome.verdict").value("FAILED"))
        .andExpect(jsonPath("$.stoerungen[0].outcome.decisiveItem.cardNumber").value(721))
        .andExpect(jsonPath("$.laufende.length()").value(0))
        .andExpect(jsonPath("$.durchgefuehrte.length()").value(1))
        .andExpect(jsonPath("$.durchgefuehrte[0].nightRunId").value(laufId))
        // Issue #1135: Die Antwort trägt die beendeten Läufe des vorigen Zyklus als eigene Liste.
        .andExpect(jsonPath("$.durchgefuehrteVoriger").isArray());
  }

  /**
   * Issue #1121: Ein Lauf, der nichts zu tun fand, steht unter den beendeten Läufen mit dem Ausgang
   * {@code NO_WORK} — und erscheint <b>nicht</b> unter den Störungen.
   *
   * <p>Hier und nicht nur am Dienst: Die Störungsabfrage liefert den Lauf sehr wohl (sie filtert
   * auf {@code complete} und die fehlende Quittung, nicht auf den Ausgang). Dass er trotzdem aus
   * der Liste fällt, ist eine Zusage über den Weg durch die Datenbank bis in die Antwort.
   */
  @Test
  void einLaufOhneArbeitMitGemeldetemGrundIstBeendetAberKeineStoerung() throws Exception {
    Cookie admin = session("de-ruhig@example.com", PlatformRole.ADMIN);
    long ruhig =
        id(
            "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms,"
                + " processed_count, skipped_count, unparsed_count, created_at, origin, complete,"
                + " no_work_reason) VALUES (?, now() - interval '1 minute', 'IMPLEMENTATION',"
                + " 'NIGHT', 1, 0, 0, 0, now(), 'TOKEN', true, 'Ready ist leer — nichts zu tun.')"
                + " RETURNING id",
            projectId);

    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stoerungen.length()").value(1))
        .andExpect(jsonPath("$.stoerungen[0].nightRunId").value(laufId))
        .andExpect(jsonPath("$.durchgefuehrte.length()").value(2))
        .andExpect(jsonPath("$.durchgefuehrte[1].nightRunId").value(ruhig))
        .andExpect(jsonPath("$.durchgefuehrte[1].outcome.verdict").value("NO_WORK"))
        .andExpect(
            jsonPath("$.durchgefuehrte[1].outcome.noWorkReason")
                .value("Ready ist leer — nichts zu tun."));
  }

  /**
   * Issue #1185, Kriterium 6: Derselbe Fall <b>ohne</b> gemeldeten Grund — der Lauf trägt den
   * Rückfalltext des Servers und steht trotzdem unter den beendeten Läufen, nicht in der
   * Störungsliste.
   *
   * <p>Hier und nicht nur am Dienst, aus demselben Grund wie beim gemeldeten Grund: Die
   * Störungsabfrage liefert den Lauf sehr wohl. Dass er aus der Liste fällt, ist eine Zusage über
   * den Weg durch die Datenbank bis in die Antwort — und der Lauf liegt in der Datenbank, ohne je
   * neu eingeliefert worden zu sein (Kriterium 8).
   */
  @Test
  void einLaufOhneArbeitMitRueckfalltextIstBeendetAberKeineStoerung() throws Exception {
    Cookie admin = session("de-rueckfall@example.com", PlatformRole.ADMIN);
    long rueckfall =
        id(
            "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms,"
                + " processed_count, skipped_count, unparsed_count, created_at, origin, complete,"
                + " no_work_reason) VALUES (?, now() - interval '1 minute', 'IMPLEMENTATION',"
                + " 'NIGHT', 1, 0, 0, 0, now(), 'UPLOAD', true,"
                + " 'Nichts abgearbeitet — Grund unbekannt') RETURNING id",
            projectId);

    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stoerungen.length()").value(1))
        .andExpect(jsonPath("$.stoerungen[0].nightRunId").value(laufId))
        .andExpect(jsonPath("$.durchgefuehrte.length()").value(2))
        .andExpect(jsonPath("$.durchgefuehrte[1].nightRunId").value(rueckfall))
        .andExpect(jsonPath("$.durchgefuehrte[1].outcome.verdict").value("NO_WORK"))
        .andExpect(
            jsonPath("$.durchgefuehrte[1].outcome.noWorkReason")
                .value("Nichts abgearbeitet — Grund unbekannt"));
  }

  /**
   * Issue #1185, Kriterium 3: Der Lauf, um den es dem Vorhaben ging — er stellte alle Pakete zurück
   * und meldete deshalb keinen Grund, also trägt er den Rückfalltext. Maßgeblich ist das
   * zurückgestellte Paket: Er bleibt eine Störung, aber „mit Vorbehalt" statt rot.
   */
  @Test
  void einZurueckgestelltesPaketBleibtEineStoerungTrotzRueckfalltext() throws Exception {
    Cookie admin = session("de-vorbehalt@example.com", PlatformRole.ADMIN);
    long wartend =
        id(
            "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms,"
                + " processed_count, skipped_count, unparsed_count, created_at, origin, complete,"
                + " no_work_reason) VALUES (?, now() - interval '1 minute', 'IMPLEMENTATION',"
                + " 'NIGHT', 1, 0, 0, 0, now(), 'TOKEN', true,"
                + " 'Nichts abgearbeitet — Grund unbekannt') RETURNING id",
            projectId);
    jdbc.update(
        "INSERT INTO night_run_item (night_run_id, project_id, started_at, mode, kind, card_number,"
            + " title, state, error_class) VALUES (?, ?, now(), 'IMPLEMENTATION', 'NIGHT', 722,"
            + " 'Zurueckgestellt', 'GREY', 'DEPENDENCY_UNMET')",
        wartend,
        projectId);

    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stoerungen.length()").value(2))
        .andExpect(jsonPath("$.stoerungen[1].nightRunId").value(wartend))
        .andExpect(jsonPath("$.stoerungen[1].outcome.verdict").value("WAITING"))
        .andExpect(jsonPath("$.stoerungen[1].outcome.decisiveItem.cardNumber").value(722))
        .andExpect(jsonPath("$.stoerungen[1].outcome.noWorkReason").doesNotExist());
  }

  /**
   * AK 6 der fachlichen Quelle #1074: Ein Lauf, der seinen harten Abbruch gemeldet hat, steht in
   * der Störungsliste und lässt sich quittieren — obwohl er kein einziges nicht-grünes Paket hat.
   *
   * <p>Hier und nicht nur am Dienst: Der Grund reist aus der Spalte {@code abort_reason} über beide
   * Abfragen bis in die Antwort. Fiele er unterwegs weg, sähe der Dienst einen gelungenen Lauf —
   * und die Zeile fehlte still.
   */
  @Test
  void einAbgebrochenerLaufStehtInDerStoerungsliste_undLaesstSichQuittieren() throws Exception {
    Cookie admin = session("de-abbruch@example.com", PlatformRole.ADMIN);
    long abgebrochen =
        id(
            "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms,"
                + " processed_count, skipped_count, unparsed_count, created_at, origin, complete,"
                + " abort_reason) VALUES (?, now() - interval '1 minute', 'CHAIN', 'NIGHT', 1, 3,"
                + " 0, 0, now(), 'TOKEN', true, 'Dirty-Guard: uncommittete Reste') RETURNING id",
            projectId);

    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stoerungen.length()").value(2))
        .andExpect(jsonPath("$.stoerungen[1].nightRunId").value(abgebrochen))
        .andExpect(jsonPath("$.stoerungen[1].outcome.verdict").value("FAILED"))
        .andExpect(
            jsonPath("$.stoerungen[1].outcome.abortReason")
                .value("Dirty-Guard: uncommittete Reste"));

    mvc.perform(delete(QUITTIEREN + "/" + abgebrochen).cookie(admin))
        .andExpect(status().isNoContent());

    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(admin))
        .andExpect(jsonPath("$.stoerungen.length()").value(1))
        .andExpect(jsonPath("$.stoerungen[0].nightRunId").value(laufId));
  }

  /**
   * Issue #1128: Jede Zeile aller drei Listen trägt die Art ihres Laufs — geprüft an einer
   * laufenden Kette, an dem beendeten Umsetzungslauf und an seiner Störung.
   */
  @Test
  void jedeZeileTraegtDieArtIhresLaufs() throws Exception {
    Cookie admin = session("de-art@example.com", PlatformRole.ADMIN);
    long kette =
        id(
            "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms,"
                + " processed_count, skipped_count, unparsed_count, created_at, updated_at, origin,"
                + " complete) VALUES (?, now() - interval '2 minute', 'CHAIN', 'NIGHT', 1, 0, 0, 0,"
                + " now(), now(), 'TOKEN', false) RETURNING id",
            projectId);

    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.laufende.length()").value(1))
        .andExpect(jsonPath("$.laufende[0].nightRunId").value(kette))
        .andExpect(jsonPath("$.laufende[0].mode").value("CHAIN"))
        .andExpect(jsonPath("$.durchgefuehrte[0].nightRunId").value(laufId))
        .andExpect(jsonPath("$.durchgefuehrte[0].mode").value("IMPLEMENTATION"))
        .andExpect(jsonPath("$.stoerungen[0].mode").value("IMPLEMENTATION"));
  }

  /** AK 10 und Kriterium 13: Die Quittung räumt die Störung weg, den Ausgang lässt sie stehen. */
  @Test
  void dasQuittierenRaeumtDieZeileWeg_undIstIdempotent() throws Exception {
    Cookie admin = session("de-quit@example.com", PlatformRole.ADMIN);

    mvc.perform(delete(QUITTIEREN + "/" + laufId).cookie(admin)).andExpect(status().isNoContent());
    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(admin))
        .andExpect(jsonPath("$.stoerungen.length()").value(0))
        .andExpect(jsonPath("$.durchgefuehrte.length()").value(1));

    // AK 8: Der zweite Klick zweier Admins auf dieselbe Zeile ist kein Fehler.
    mvc.perform(delete(QUITTIEREN + "/" + laufId).cookie(admin)).andExpect(status().isNoContent());
  }

  @Test
  void einUnbekannterLaufIstBeimQuittieren404() throws Exception {
    Cookie admin = session("de-404@example.com", PlatformRole.ADMIN);

    mvc.perform(delete(QUITTIEREN + "/999999").cookie(admin)).andExpect(status().isNotFound());
  }

  /**
   * Plan E6: Die Zone kommt vom Browser und wird wie in {@code NightRunUsageController} auf eine
   * Regionszone eingegrenzt — ein fester Offset wäre eine verschobene Nachtgrenze, kein Ort.
   */
  @Test
  void eineOffsetZoneUndEineFehlendeZoneSind400() throws Exception {
    Cookie admin = session("de-zone@example.com", PlatformRole.ADMIN);

    mvc.perform(get(LEITSTAND).param("zone", "+05:30").cookie(admin))
        .andExpect(status().isBadRequest());
    mvc.perform(get(LEITSTAND).cookie(admin)).andExpect(status().isBadRequest());
  }

  @Test
  void einLaufEinesNichtTeilnehmendenProjektsIstBeimQuittieren404() throws Exception {
    Cookie admin = session("de-ohne@example.com", PlatformRole.ADMIN);
    jdbc.update("UPDATE project SET dashboard_participation = false WHERE id = ?", projectId);

    mvc.perform(delete(QUITTIEREN + "/" + laufId).cookie(admin)).andExpect(status().isNotFound());
  }

  /** AK 3: Wer nicht Plattform-Admin ist, sieht weder den Leitstand noch das Quittieren. */
  @Test
  void ohnePlattformRolleAdminSindBeideEndpunkte403() throws Exception {
    Cookie nutzer = session("de-nutzer@example.com", PlatformRole.USER);

    mvc.perform(get(LEITSTAND).param("zone", ZONE).cookie(nutzer))
        .andExpect(status().isForbidden());
    mvc.perform(delete(QUITTIEREN + "/" + laufId).cookie(nutzer)).andExpect(status().isForbidden());
  }

  /**
   * Plan E24: Die Grenze zieht die Filterkette, nicht der Endpunkt. Ein ungebundenes Token trägt
   * {@code AUTH_PAT_UNBOUND} und käme unter {@code /api/**} durch — unter {@code /api/admin/**}
   * nicht.
   */
  @Test
  void einUngebundenesTokenErreichtBeideEndpunkteNicht() throws Exception {
    Cookie admin = session("de-token@example.com", PlatformRole.ADMIN);
    String token =
        json.readTree(
                mvc.perform(
                        post("/api/access-tokens")
                            .cookie(admin)
                            .contentType("application/json")
                            .content("{\"name\":\"Leitstand-Token\"}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("plaintext")
            .asText();

    mvc.perform(get(LEITSTAND).param("zone", ZONE).header(TOKEN_HEADER, token))
        .andExpect(status().isForbidden());
    mvc.perform(delete(QUITTIEREN + "/" + laufId).header(TOKEN_HEADER, token))
        .andExpect(status().isForbidden());
  }
}
