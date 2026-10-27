package org.mwolff.manban.nightrun.web;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.nightrun.application.NightRunModelsService;
import org.mwolff.manban.nightrun.domain.Modellvergleich;
import org.mwolff.manban.nightrun.domain.Modellvergleich.Kopf;
import org.mwolff.manban.nightrun.domain.Modellvergleich.Stufe;
import org.mwolff.manban.nightrun.domain.Modellvergleich.Zeile;
import org.mwolff.manban.nightrun.domain.NightRunAttemptStage;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Der Endpunkt der Platte „Modelle“ (Issue #1947, Plan #1905 E13), Service gemockt — Muster {@code
 * NightRunReworkControllerTest} mit standalone MockMvc. 403 und 404 liefert der Service über {@code
 * requireProjectId} und {@code requireNightRunAccess}.
 */
class NightRunModelsControllerTest {

  private static final long USER = 7L;
  private static final long BOARD = 11L;
  private static final String PFAD = "/api/boards/" + BOARD + "/night-run-models";
  private static final Instant BEGINN = Instant.parse("2026-08-01T22:00:00Z");

  private NightRunModelsService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(NightRunModelsService.class);
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(new NightRunModelsController(service))
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
  void liefertKopfStufenUndZeilen() throws Exception {
    when(service.modelle(USER, BOARD))
        .thenReturn(
            new Modellvergleich(
                new Kopf(BEGINN, false, 3, 13),
                List.of(
                    new Stufe(
                        NightRunAttemptStage.PLAN,
                        List.of(
                            new Zeile(
                                "claude-opus-5-5",
                                "medium",
                                12,
                                9,
                                true,
                                new BigDecimal("1.250000"),
                                9,
                                95_000L,
                                12),
                            new Zeile("fable", null, 1, 1, false, null, 0, null, 0))),
                    new Stufe(NightRunAttemptStage.REVIEW, List.of()))));

    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataSince").value("2026-08-01T22:00:00Z"))
        .andExpect(jsonPath("$.lastTwelveWeeks").value(false))
        .andExpect(jsonPath("$.withoutModel").value(3))
        .andExpect(jsonPath("$.attemptsInPeriod").value(13))
        .andExpect(jsonPath("$.stages", hasSize(2)))
        .andExpect(jsonPath("$.stages[0].stage").value("PLAN"))
        .andExpect(jsonPath("$.stages[0].rows", hasSize(2)))
        .andExpect(jsonPath("$.stages[0].rows[0].model").value("claude-opus-5-5"))
        .andExpect(jsonPath("$.stages[0].rows[0].effort").value("medium"))
        .andExpect(jsonPath("$.stages[0].rows[0].attempts").value(12))
        .andExpect(jsonPath("$.stages[0].rows[0].succeeded").value(9))
        .andExpect(jsonPath("$.stages[0].rows[0].enoughData").value(true))
        .andExpect(jsonPath("$.stages[0].rows[0].costMedianUsd").value(1.25))
        .andExpect(jsonPath("$.stages[0].rows[0].costFrom").value(9))
        .andExpect(jsonPath("$.stages[0].rows[0].modelTimeMedianMs").value(95_000))
        .andExpect(jsonPath("$.stages[0].rows[0].modelTimeFrom").value(12))
        .andExpect(jsonPath("$.stages[0].rows[1].model").value("fable"))
        .andExpect(jsonPath("$.stages[0].rows[1].effort").value(nullValue()))
        .andExpect(jsonPath("$.stages[0].rows[1].enoughData").value(false))
        .andExpect(jsonPath("$.stages[0].rows[1].costMedianUsd").value(nullValue()))
        .andExpect(jsonPath("$.stages[0].rows[1].costFrom").value(0))
        .andExpect(jsonPath("$.stages[0].rows[1].modelTimeMedianMs").value(nullValue()))
        .andExpect(jsonPath("$.stages[0].rows[1].modelTimeFrom").value(0))
        .andExpect(jsonPath("$.stages[1].stage").value("REVIEW"))
        .andExpect(jsonPath("$.stages[1].rows", hasSize(0)));
  }

  @Test
  void ohneVorgangIstDerDatenbeginnNull() throws Exception {
    when(service.modelle(USER, BOARD))
        .thenReturn(new Modellvergleich(new Kopf(null, false, 0, 0), List.of()));

    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataSince").value(nullValue()))
        .andExpect(jsonPath("$.attemptsInPeriod").value(0))
        .andExpect(jsonPath("$.stages", hasSize(0)));
  }

  @Test
  void ohneRechtAufDieLaeufe403() throws Exception {
    doThrow(new ProjectAccessDeniedException()).when(service).modelle(anyLong(), anyLong());

    mvc.perform(get(PFAD)).andExpect(status().isForbidden());
  }

  @Test
  void unbekanntesBoardOderNichtmitglied404() throws Exception {
    doThrow(new BoardNotFoundException()).when(service).modelle(anyLong(), anyLong());
    mvc.perform(get(PFAD)).andExpect(status().isNotFound());

    doThrow(new ProjectNotFoundException()).when(service).modelle(anyLong(), anyLong());
    mvc.perform(get(PFAD)).andExpect(status().isNotFound());
  }
}
