package org.mwolff.manban.nightrun;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunPeriod;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Das Monatsbudget über HTTP (Issue #1967, Plan #1962, fachliche Quelle #1957 AK 1 bis 3 und 12):
 * Festlegen über {@code PUT/DELETE …/monthly-budget} im Projektmodul, Lesen über {@code GET
 * …/night-run-usage/budget} im Nachtlauf-Modul — mit echter Rechteprüfung und echter Datenbank.
 *
 * <p>Über HTTP und nicht am Dienst, weil die <b>Statuscodes</b> die Zusage sind: 403 für das echte
 * Mitglied ohne Owner-Rolle, 404 für den Plattform-Admin ohne Mitgliedschaft beim Schreiben, und
 * beim Lesen 200 für ihn, sobald das Projekt am Plattform-Leitstand teilnimmt.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MonatsbudgetIT extends AbstractIntegrationTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private NightRunRepository runs;
  @Autowired private Clock clock;

  private Cookie owner;
  private Cookie projektAdmin;
  private Cookie plattformAdmin;
  private long projectId;

  @BeforeEach
  void seed() throws Exception {
    owner = anmeldung.anmelden("mb-owner@example.com", PlatformRole.USER);
    projektAdmin = anmeldung.anmelden("mb-projektadmin@example.com", PlatformRole.USER);
    plattformAdmin = anmeldung.anmelden("mb-plattformadmin@example.com", PlatformRole.ADMIN);
    projectId =
        json.readTree(
                mvc.perform(
                        post("/api/projects")
                            .cookie(plattformAdmin)
                            .contentType("application/json")
                            .content(
                                "{\"name\":\"Budget\",\"ownerEmail\":\"mb-owner@example.com\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    long projektAdminId =
        anmeldung.konto("mb-projektadmin@example.com", PlatformRole.USER).requireId();
    memberships.save(
        new ProjectMembership(null, projectId, projektAdminId, ProjectRole.ADMIN, Instant.now()));
    // Direkt in der Spalte: Hier geht es um das Lesen durch den Betreiber, nicht ums Schalten.
    jdbc.update("UPDATE project SET dashboard_participation = TRUE WHERE id = ?", projectId);
  }

  private ResultActions festlegen(Cookie wer, String betrag) throws Exception {
    return mvc.perform(
        put("/api/projects/" + projectId + "/monthly-budget")
            .cookie(wer)
            .contentType("application/json")
            .content("{\"amountUsd\":\"%s\"}".formatted(betrag)));
  }

  private ResultActions stand(Cookie wer) throws Exception {
    return mvc.perform(
        get("/api/projects/" + projectId + "/night-run-usage/budget")
            .cookie(wer)
            .param("zone", BERLIN.getId()));
  }

  /** Ein Nachtlauf mit Kosten genau am Beginn des laufenden Monats — liegt also immer darin. */
  private void laufImLaufendenMonat(String kosten) {
    Instant beginn = NightRunPeriod.laufenderMonat(clock.instant(), BERLIN).from();
    runs.insertIfAbsent(
        new NightRun(
            null,
            projectId,
            beginn,
            NightRunMode.IMPLEMENTATION,
            NightRunKind.NIGHT,
            1_000L,
            0,
            0,
            0,
            null,
            beginn,
            NightRunOrigin.TOKEN,
            "t",
            true,
            null,
            new NightRunUsage(new BigDecimal(kosten), null, null, null, null, null),
            null,
            null,
            null,
            null,
            null),
        List.of());
  }

  @Test
  @DisplayName("Szenario: Nur der Owner legt das Budget fest")
  void nurDerOwnerLegtDasBudgetFest() throws Exception {
    festlegen(owner, "100.00").andExpect(status().isNoContent());
    festlegen(projektAdmin, "200.00").andExpect(status().isForbidden());
    festlegen(plattformAdmin, "200.00").andExpect(status().isNotFound());

    stand(plattformAdmin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.budgetUsd", comparesEqualTo(100.0)))
        .andExpect(jsonPath("$.editable").value(false));
    stand(owner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.budgetUsd", comparesEqualTo(100.0)))
        .andExpect(jsonPath("$.editable").value(true));
  }

  /** AK 2: Ungültige Beträge werden abgewiesen, das bisherige Budget bleibt; Entfernen leert. */
  @Test
  void ungueltigeBetraegeLassenDasBudgetStehen_entfernenLeertEs() throws Exception {
    festlegen(owner, "100.00").andExpect(status().isNoContent());

    for (String ungueltig : List.of("0", "-5.00", "1.234", "10000000000.00")) {
      festlegen(owner, ungueltig).andExpect(status().isBadRequest());
    }
    stand(owner).andExpect(jsonPath("$.budgetUsd", comparesEqualTo(100.0)));

    mvc.perform(delete("/api/projects/" + projectId + "/monthly-budget").cookie(owner))
        .andExpect(status().isNoContent());
    stand(owner).andExpect(status().isOk()).andExpect(jsonPath("$.budgetUsd").value(nullValue()));
  }

  /** AK 3: Eine Änderung gilt sofort für den laufenden Monat, samt neuem Anteil. */
  @Test
  void eineAenderungGiltSofortFuerDenLaufendenMonat() throws Exception {
    laufImLaufendenMonat("10.00");
    festlegen(owner, "100.00").andExpect(status().isNoContent());
    stand(owner)
        .andExpect(jsonPath("$.spentUsd", comparesEqualTo(10.0)))
        .andExpect(jsonPath("$.percent").value(10));

    festlegen(owner, "50.00").andExpect(status().isNoContent());

    stand(owner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.budgetUsd", comparesEqualTo(50.0)))
        .andExpect(jsonPath("$.percent").value(20));
  }

  /** AK 12: Wer den Verbrauch nicht sieht, sieht auch das Budget nicht. */
  @Test
  void einMitgliedOhneLeserechtBekommt403() throws Exception {
    festlegen(owner, "100.00").andExpect(status().isNoContent());

    stand(projektAdmin).andExpect(status().isForbidden());
  }
}
