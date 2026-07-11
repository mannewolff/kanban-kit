package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Prüft den SPA-Fallback: direkte Client-Routen liefern index.html, /api bleibt unberührt. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class SpaFallbackIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @Autowired private MockMvc mvc;

  @Test
  void clientRoutesServeSpaIndex() throws Exception {
    String body =
        mvc.perform(get("/admin/bootstrap"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).contains("id=\"root\"");
    mvc.perform(get("/boards/1")).andExpect(status().isOk());
    mvc.perform(get("/roles")).andExpect(status().isOk());
  }

  @Test
  void apiPathsAreNotSwallowedBySpa() throws Exception {
    // Unbekannter /api-Pfad ohne Auth -> 401 (nicht auf die SPA umgeleitet).
    mvc.perform(get("/api/gibtsnicht")).andExpect(status().isUnauthorized());
  }
}
