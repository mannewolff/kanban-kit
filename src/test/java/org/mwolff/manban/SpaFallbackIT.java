package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Prüft den SPA-Fallback: direkte Client-Routen liefern index.html, /api bleibt unberührt. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SpaFallbackIT extends AbstractIntegrationTest {

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

  @Test
  void docsRootServesVitePressNotSpa() throws Exception {
    // /docs/ liefert das VitePress-index.html der gebündelten Doku, NICHT die SPA.
    String body =
        mvc.perform(get("/docs/"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).doesNotContain("id=\"root\"");
    // Asset-Pfade müssen unter /docs/ liegen (base='/docs/').
    assertThat(body).contains("/docs/assets/");
  }

  @Test
  void docsPagesAreServedStatically() throws Exception {
    // Eine echte Doku-Seite wird direkt als statische Datei ausgeliefert (Beleg: Doku ist
    // gebündelt).
    mvc.perform(get("/docs/nutzung.html")).andExpect(status().isOk());
  }

  @Test
  void unknownDocsPathServesVitePressNotSpa() throws Exception {
    // Unbekannter /docs-Pfad -> VitePress-404, nicht der SPA-Fallback.
    String body =
        mvc.perform(get("/docs/gibtsnicht.html"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).doesNotContain("id=\"root\"");
  }
}
