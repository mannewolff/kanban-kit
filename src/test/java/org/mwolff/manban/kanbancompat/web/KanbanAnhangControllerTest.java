package org.mwolff.manban.kanbancompat.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.attachment.application.AttachmentNotFoundException;
import org.mwolff.manban.attachment.application.AttachmentService.AttachmentView;
import org.mwolff.manban.attachment.application.AttachmentService.Download;
import org.mwolff.manban.kanbancompat.application.KanbanAnhangService;
import org.mwolff.manban.kanbancompat.application.TokenNotBoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests der lesenden Anhang-Aufrufe der Kanban-Strecke (Issue #1699, Plan #1694), Dienst gemockt.
 * Standalone-MockMvc nach dem Muster {@code NightRunCardControllerTest}, damit Pfad, Kopfzeilen und
 * die {@code @ResponseStatus}-Abbildung der Ausnahmen tatsächlich auslösen. Die Board-Grenze gegen
 * den vollen Kontext belegt der Integrationstest des Folgepakets.
 */
class KanbanAnhangControllerTest {

  private static final KanbanPrincipal PRINCIPAL = new KanbanPrincipal(7L, 1L, 2L, 3L, "Token");
  private static final long CARD = 4711L;
  private static final long ANHANG = 91L;
  private static final String LISTE = "/api/kanban/items/" + CARD + "/attachments";
  private static final String EINZELN = LISTE + "/" + ANHANG;

  private KanbanAnhangService service;
  private MockMvc mvc;
  private UsernamePasswordAuthenticationToken gebunden;

  @BeforeEach
  void setUp() {
    service = mock(KanbanAnhangService.class);
    // Instants als ISO-Text, so wie in der laufenden Anwendung.
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(new KanbanAnhangController(service))
            .setMessageConverters(new ResourceHttpMessageConverter(), jackson)
            .build();
    gebunden = new UsernamePasswordAuthenticationToken(PRINCIPAL.userId(), null, List.of());
    gebunden.setDetails(PRINCIPAL);
  }

  @Test
  void list_liefertDieAnhaengeMitIhrenMetadaten() throws Exception {
    // Given
    Instant hochgeladen = Instant.parse("2026-10-13T08:15:00Z");
    when(service.list(PRINCIPAL, CARD))
        .thenReturn(
            List.of(new AttachmentView(ANHANG, CARD, "skizze.png", "image/png", 48L, hochgeladen)));

    // When / Then
    mvc.perform(get(LISTE).principal(gebunden))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(ANHANG))
        .andExpect(jsonPath("$[0].cardId").value(CARD))
        .andExpect(jsonPath("$[0].filename").value("skizze.png"))
        .andExpect(jsonPath("$[0].contentType").value("image/png"))
        .andExpect(jsonPath("$[0].size").value(48))
        .andExpect(jsonPath("$[0].createdAt").value("2026-10-13T08:15:00Z"));
  }

  @Test
  void download_liefertDieBytesAlsAttachmentMitDateinameTypUndLaenge() throws Exception {
    // Given
    byte[] bytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x00, (byte) 0xFF};
    when(service.download(PRINCIPAL, CARD, ANHANG))
        .thenReturn(
            new Download("skizze.png", "image/png", bytes.length, new ByteArrayInputStream(bytes)));

    // When / Then
    mvc.perform(get(EINZELN).principal(gebunden))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"skizze.png\""))
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
        .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, bytes.length))
        .andExpect(content().bytes(bytes));
  }

  @Test
  void list_ohneBindung_ergibt409() throws Exception {
    // Given
    when(service.list(PRINCIPAL, CARD)).thenThrow(new TokenNotBoundException());

    // When / Then
    mvc.perform(get(LISTE).principal(gebunden)).andExpect(status().isConflict());
  }

  @Test
  void download_ohneBindung_ergibt409() throws Exception {
    // Given
    when(service.download(PRINCIPAL, CARD, ANHANG)).thenThrow(new TokenNotBoundException());

    // When / Then
    mvc.perform(get(EINZELN).principal(gebunden)).andExpect(status().isConflict());
  }

  @Test
  void ohneKanbanPrincipal_ergibt409() throws Exception {
    // When / Then: eine Authentication ohne KanbanPrincipal in den Details gilt als ungebunden
    mvc.perform(get(LISTE).principal(new UsernamePasswordAuthenticationToken(7L, null, List.of())))
        .andExpect(status().isConflict());
  }

  @Test
  void list_unerreichbar_ergibt404MitMeldung() throws Exception {
    // Given
    when(service.list(PRINCIPAL, CARD)).thenThrow(new AttachmentNotFoundException());

    // When / Then
    mvc.perform(get(LISTE).principal(gebunden))
        .andExpect(status().isNotFound())
        .andExpect(
            result ->
                assertThat(result.getResolvedException()).hasMessage("Anhang nicht gefunden"));
  }

  @Test
  void download_unerreichbar_ergibt404MitMeldung() throws Exception {
    // Given
    when(service.download(PRINCIPAL, CARD, ANHANG)).thenThrow(new AttachmentNotFoundException());

    // When / Then
    mvc.perform(get(EINZELN).principal(gebunden))
        .andExpect(status().isNotFound())
        .andExpect(
            result ->
                assertThat(result.getResolvedException()).hasMessage("Anhang nicht gefunden"));
  }
}
