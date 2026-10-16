package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Prüft den App-Config-Endpunkt (Done-Retention für den Archiv-Countdown). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ConfigIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;

  @Test
  void configReturnsDoneRetentionDays() throws Exception {
    Cookie alice = anmeldung.anmelden("config@example.com", PlatformRole.USER);
    mvc.perform(get("/api/config").cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.doneRetentionDays").value(30));
  }

  @Test
  void configRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/config")).andExpect(status().isUnauthorized());
  }
}
