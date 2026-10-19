package org.mwolff.manban.deployment.web;

import static org.hamcrest.Matchers.hasSize;
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
import org.mwolff.manban.deployment.application.DeploymentKennzahlenService;
import org.mwolff.manban.deployment.domain.Auslieferungskennzahlen;
import org.mwolff.manban.deployment.domain.Auslieferungskennzahlen.Woche;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Der Endpunkt der Platte „Auslieferung“ (Issue #1835, Plan #1831 E5), Service gemockt — Muster
 * {@code NightRunReworkControllerTest} mit standalone MockMvc. Dass der volle Kontext jedem
 * Mitglied liest und einem Nichtmitglied 404 gibt, belegt {@code AuslieferungSzenarienIT}.
 */
class DeploymentDashboardControllerTest {

  private static final long USER = 7L;
  private static final long BOARD = 11L;
  private static final String PFAD = "/api/boards/" + BOARD + "/dashboard/delivery";
  private static final Instant SEIT = Instant.parse("2026-09-26T10:00:00Z");
  private static final Instant AUSFALL = Instant.parse("2026-10-17T09:00:00Z");
  private static final Instant WOCHE_ALT = Instant.parse("2026-07-25T12:00:00Z");
  private static final Instant WOCHE_NEU = Instant.parse("2026-10-10T12:00:00Z");

  private DeploymentKennzahlenService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(DeploymentKennzahlenService.class);
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(new DeploymentDashboardController(service))
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
  void liefertAlleKennzahlenUndDieWochenleiste() throws Exception {
    when(service.kennzahlen(USER, BOARD))
        .thenReturn(
            new Auslieferungskennzahlen(
                true,
                SEIT,
                4,
                172_800L,
                20,
                7_200L,
                AUSFALL,
                List.of(new Woche(WOCHE_ALT, 0, 0), new Woche(WOCHE_NEU, 3, 1))));

    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasReports").value(true))
        .andExpect(jsonPath("$.dataSince").value("2026-09-26T10:00:00Z"))
        .andExpect(jsonPath("$.deploymentCount").value(4))
        .andExpect(jsonPath("$.leadTimeMedianSeconds").value(172_800))
        .andExpect(jsonPath("$.changeFailurePercent").value(20))
        .andExpect(jsonPath("$.timeToRestoreMedianSeconds").value(7_200))
        .andExpect(jsonPath("$.outageSince").value("2026-10-17T09:00:00Z"))
        .andExpect(jsonPath("$.weeks", hasSize(2)))
        .andExpect(jsonPath("$.weeks[0].weekStart").value("2026-07-25T12:00:00Z"))
        .andExpect(jsonPath("$.weeks[0].successCount").value(0))
        .andExpect(jsonPath("$.weeks[0].failureCount").value(0))
        .andExpect(jsonPath("$.weeks[1].weekStart").value("2026-10-10T12:00:00Z"))
        .andExpect(jsonPath("$.weeks[1].successCount").value(3))
        .andExpect(jsonPath("$.weeks[1].failureCount").value(1));
  }

  /** E9: „keine Grundlage“ ist null, nicht 0; die Frequenz bleibt eine echte 0. */
  @Test
  void ohneGrundlageStehenNullwerteStattNullen() throws Exception {
    when(service.kennzahlen(USER, BOARD))
        .thenReturn(new Auslieferungskennzahlen(false, null, 0, null, null, null, null, List.of()));

    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasReports").value(false))
        .andExpect(jsonPath("$.dataSince").value(nullValue()))
        .andExpect(jsonPath("$.deploymentCount").value(0))
        .andExpect(jsonPath("$.leadTimeMedianSeconds").value(nullValue()))
        .andExpect(jsonPath("$.changeFailurePercent").value(nullValue()))
        .andExpect(jsonPath("$.timeToRestoreMedianSeconds").value(nullValue()))
        .andExpect(jsonPath("$.outageSince").value(nullValue()))
        .andExpect(jsonPath("$.weeks", hasSize(0)));
  }

  @Test
  void einNichtmitgliedBekommt404() throws Exception {
    doThrow(new ProjectNotFoundException()).when(service).kennzahlen(anyLong(), anyLong());

    mvc.perform(get(PFAD)).andExpect(status().isNotFound());
  }
}
