package org.mwolff.manban.deployment.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.deployment.application.DeploymentService;
import org.mwolff.manban.deployment.application.GemeldeterStand;
import org.mwolff.manban.deployment.application.ZuletztGemeldet;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Der Endpunkt der jüngsten Vorschau und Veröffentlichung (Issue #2215, Plan #2206 E10), Dienst
 * gemockt — Muster {@code DeploymentDashboardControllerTest}.
 */
class AuslieferungZuletztControllerTest {

  private static final long USER = 7L;
  private static final long PROJEKT = 3L;
  private static final String PFAD = "/api/projects/" + PROJEKT + "/auslieferung/zuletzt";
  private static final Instant VORSCHAU_AM = Instant.parse("2026-11-22T10:00:00Z");
  private static final Instant VEROEFFENTLICHT_AM = Instant.parse("2026-11-21T09:00:00Z");
  private static final Instant EINGANG = Instant.parse("2026-11-22T10:01:00Z");

  private DeploymentService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(DeploymentService.class);
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(new AuslieferungZuletztController(service))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setMessageConverters(jackson)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(USER, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void liefertVorschauUndVeroeffentlichungMitAdresseStandUndZeit() throws Exception {
    when(service.zuletzt(USER, PROJEKT))
        .thenReturn(
            new ZuletztGemeldet(
                new GemeldeterStand(
                    "abc123", "https://vorschau.example.org/42", VORSCHAU_AM, EINGANG),
                new GemeldeterStand("def456", null, VEROEFFENTLICHT_AM, EINGANG)));

    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vorschau.url").value("https://vorschau.example.org/42"))
        .andExpect(jsonPath("$.vorschau.revision").value("abc123"))
        .andExpect(jsonPath("$.vorschau.finishedAt").value("2026-11-22T10:00:00Z"))
        .andExpect(jsonPath("$.vorschau.eingang").doesNotExist())
        .andExpect(jsonPath("$.veroeffentlichung.url").value(nullValue()))
        .andExpect(jsonPath("$.veroeffentlichung.revision").value("def456"))
        .andExpect(jsonPath("$.veroeffentlichung.finishedAt").value("2026-11-21T09:00:00Z"));
  }

  @Test
  void ohneMeldungStehtBeidesAufNull() throws Exception {
    when(service.zuletzt(USER, PROJEKT)).thenReturn(new ZuletztGemeldet(null, null));

    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vorschau").value(nullValue()))
        .andExpect(jsonPath("$.veroeffentlichung").value(nullValue()));
  }

  @Test
  void einNichtmitgliedBekommt404() throws Exception {
    doThrow(new ProjectNotFoundException()).when(service).zuletzt(anyLong(), anyLong());

    mvc.perform(get(PFAD)).andExpect(status().isNotFound());
  }
}
