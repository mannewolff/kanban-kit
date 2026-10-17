package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** IT der Admin-Endpoints für die globale Done-Aufbewahrung inkl. Autorisierung und Validierung. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DoneRetentionSettingIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;

  @Test
  void adminCanReadDefaultThenSetOverride_andConfigReflectsIt() throws Exception {
    Cookie admin = anmeldung.anmelden("retention-admin@example.com", PlatformRole.ADMIN);

    // Ohne Override liefert GET den Env-Default (30) und override=null.
    mvc.perform(get("/api/admin/done-retention").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effective").value(30))
        .andExpect(jsonPath("$.override").doesNotExist());

    // Override auf 7 setzen.
    mvc.perform(
            put("/api/admin/done-retention")
                .cookie(admin)
                .contentType("application/json")
                .content("{\"days\":7}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effective").value(7))
        .andExpect(jsonPath("$.override").value(7));

    // /api/config spiegelt den effektiven Wert.
    mvc.perform(get("/api/config").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.doneRetentionDays").value(7));
  }

  @Test
  void adminCanTurnAutoArchiveOff_withZero() throws Exception {
    Cookie admin = anmeldung.anmelden("retention-off-admin@example.com", PlatformRole.ADMIN);

    mvc.perform(
            put("/api/admin/done-retention")
                .cookie(admin)
                .contentType("application/json")
                .content("{\"days\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effective").value(0))
        .andExpect(jsonPath("$.override").value(0));

    mvc.perform(get("/api/config").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.doneRetentionDays").value(0));
  }

  @Test
  void nonAdminIsForbidden() throws Exception {
    Cookie user = anmeldung.anmelden("retention-user@example.com", PlatformRole.USER);

    mvc.perform(get("/api/admin/done-retention").cookie(user)).andExpect(status().isForbidden());
    mvc.perform(
            put("/api/admin/done-retention")
                .cookie(user)
                .contentType("application/json")
                .content("{\"days\":7}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void negativeValueIsRejected() throws Exception {
    Cookie admin = anmeldung.anmelden("retention-neg-admin@example.com", PlatformRole.ADMIN);

    mvc.perform(
            put("/api/admin/done-retention")
                .cookie(admin)
                .contentType("application/json")
                .content("{\"days\":-1}"))
        .andExpect(status().isBadRequest());
  }
}
