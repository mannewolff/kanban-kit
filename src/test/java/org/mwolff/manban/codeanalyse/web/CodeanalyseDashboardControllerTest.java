package org.mwolff.manban.codeanalyse.web;

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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.codeanalyse.application.BefunddichteService;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte.NichtZaehlend;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte.Summe;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte.Woche;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Der Endpunkt der Platte „Codeanalyse“ (Issue #2276, Plan #2272 E9), Service gemockt — Muster
 * {@code DeploymentDashboardControllerTest} mit standalone MockMvc.
 */
class CodeanalyseDashboardControllerTest {

  private static final long USER = 7L;
  private static final long BOARD = 11L;
  private static final String PFAD = "/api/boards/" + BOARD + "/dashboard/code-analysis";
  private static final Instant ERSTE_WOCHE = Instant.parse("2026-09-07T00:00:00Z");
  private static final Instant SEIT = Instant.parse("2026-10-14T08:30:00Z");

  private BefunddichteService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(BefunddichteService.class);
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(new CodeanalyseDashboardController(service))
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

  /** AK 6: ohne Messung „noch keine Messung“, zwölf Wochen ohne Werte. */
  @Test
  void einProjektOhneMessungLiefertZwoelfWochenOhneWerte() throws Exception {
    when(service.wochenwerte(USER, BOARD))
        .thenReturn(new Wochenwerte(false, null, leereWochen(List.of())));

    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasMeasurements").value(false))
        .andExpect(jsonPath("$.dataSince").value(nullValue()))
        .andExpect(jsonPath("$.weeks", hasSize(12)))
        .andExpect(jsonPath("$.weeks[0].weekStart").value("2026-09-07T00:00:00Z"))
        .andExpect(jsonPath("$.weeks[11].weekStart").value("2026-11-23T00:00:00Z"))
        .andExpect(jsonPath("$.weeks[0].countingMeasurements").value(0))
        .andExpect(jsonPath("$.weeks[0].cards").value(nullValue()))
        .andExpect(jsonPath("$.weeks[0].before").value(nullValue()))
        .andExpect(jsonPath("$.weeks[0].final").value(nullValue()))
        .andExpect(jsonPath("$.weeks[0].reductionPercent").value(nullValue()))
        .andExpect(jsonPath("$.weeks[0].notCounting", hasSize(0)));
  }

  /** AK 1 bis AK 4: Zahlen, Dichten je Art und als Summe, Rückgang. */
  @Test
  void eineWocheMitZaehlenderMessungTraegtZahlenDichtenUndRueckgang() throws Exception {
    Woche woche =
        new Woche(
            wochenbeginn(11),
            2,
            5L,
            new Summe(2, 1, 5, 8, 2_000, 1.0, 0.5, 2.5, 4.0),
            new Summe(1, 0, 1, 2, 2_000, 0.5, 0.0, 0.5, 1.0),
            75.0,
            List.of(new NichtZaehlend("missing-final", 1)));
    when(service.wochenwerte(USER, BOARD))
        .thenReturn(new Wochenwerte(true, SEIT, leereWochen(List.of(woche))));

    ResultActions antwort = mvc.perform(get(PFAD)).andExpect(status().isOk());

    antwort
        .andExpect(jsonPath("$.hasMeasurements").value(true))
        .andExpect(jsonPath("$.dataSince").value("2026-10-14T08:30:00Z"))
        .andExpect(jsonPath("$.weeks", hasSize(12)))
        .andExpect(jsonPath("$.weeks[11].weekStart").value("2026-11-23T00:00:00Z"))
        .andExpect(jsonPath("$.weeks[11].countingMeasurements").value(2))
        .andExpect(jsonPath("$.weeks[11].cards").value(5))
        .andExpect(jsonPath("$.weeks[11].before.bugs").value(2))
        .andExpect(jsonPath("$.weeks[11].before.vulnerabilities").value(1))
        .andExpect(jsonPath("$.weeks[11].before.codeSmells").value(5))
        .andExpect(jsonPath("$.weeks[11].before.findings").value(8))
        .andExpect(jsonPath("$.weeks[11].before.changedLines").value(2_000))
        .andExpect(jsonPath("$.weeks[11].before.bugsDensity").value(1.0))
        .andExpect(jsonPath("$.weeks[11].before.vulnerabilitiesDensity").value(0.5))
        .andExpect(jsonPath("$.weeks[11].before.codeSmellsDensity").value(2.5))
        .andExpect(jsonPath("$.weeks[11].before.findingsDensity").value(4.0))
        .andExpect(jsonPath("$.weeks[11].final.bugs").value(1))
        .andExpect(jsonPath("$.weeks[11].final.vulnerabilities").value(0))
        .andExpect(jsonPath("$.weeks[11].final.codeSmells").value(1))
        .andExpect(jsonPath("$.weeks[11].final.findings").value(2))
        .andExpect(jsonPath("$.weeks[11].final.changedLines").value(2_000))
        .andExpect(jsonPath("$.weeks[11].final.bugsDensity").value(0.5))
        .andExpect(jsonPath("$.weeks[11].final.vulnerabilitiesDensity").value(0.0))
        .andExpect(jsonPath("$.weeks[11].final.codeSmellsDensity").value(0.5))
        .andExpect(jsonPath("$.weeks[11].final.findingsDensity").value(1.0))
        .andExpect(jsonPath("$.weeks[11].reductionPercent").value(75.0))
        .andExpect(jsonPath("$.weeks[11].notCounting", hasSize(1)))
        .andExpect(jsonPath("$.weeks[11].notCounting[0].reason").value("missing-final"))
        .andExpect(jsonPath("$.weeks[11].notCounting[0].count").value(1));
  }

  /** AK 6: Eine Woche nur mit nicht zählender Messung zeigt die Lücke und die Gründe. */
  @Test
  void eineWocheNurMitNichtZaehlenderMessungTraegtKeineWerteAberDieGruende() throws Exception {
    Woche woche =
        new Woche(
            wochenbeginn(10),
            0,
            null,
            null,
            null,
            null,
            List.of(new NichtZaehlend("cards-removed", 2), new NichtZaehlend("unbekannt-x", 1)));
    when(service.wochenwerte(USER, BOARD))
        .thenReturn(new Wochenwerte(true, null, leereWochen(List.of(woche))));

    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeks[10].weekStart").value("2026-11-16T00:00:00Z"))
        .andExpect(jsonPath("$.weeks[10].countingMeasurements").value(0))
        .andExpect(jsonPath("$.weeks[10].cards").value(nullValue()))
        .andExpect(jsonPath("$.weeks[10].before").value(nullValue()))
        .andExpect(jsonPath("$.weeks[10].final").value(nullValue()))
        .andExpect(jsonPath("$.weeks[10].reductionPercent").value(nullValue()))
        .andExpect(jsonPath("$.weeks[10].notCounting", hasSize(2)))
        .andExpect(jsonPath("$.weeks[10].notCounting[0].reason").value("cards-removed"))
        .andExpect(jsonPath("$.weeks[10].notCounting[0].count").value(2))
        .andExpect(jsonPath("$.weeks[10].notCounting[1].reason").value("unbekannt-x"))
        .andExpect(jsonPath("$.weeks[10].notCounting[1].count").value(1));
  }

  /** AK 2: Ohne geänderte Zeilen stehen Funde und Zeilen, aber keine Dichte. */
  @Test
  void eineWocheMitNullZeilenTraegtFundeAberKeineDichten() throws Exception {
    Woche woche =
        new Woche(
            wochenbeginn(11),
            1,
            1L,
            new Summe(1, 0, 2, 3, 0, null, null, null, null),
            new Summe(0, 0, 0, 0, 0, null, null, null, null),
            100.0,
            List.of());
    when(service.wochenwerte(USER, BOARD))
        .thenReturn(new Wochenwerte(true, null, leereWochen(List.of(woche))));

    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeks[11].before.findings").value(3))
        .andExpect(jsonPath("$.weeks[11].before.changedLines").value(0))
        .andExpect(jsonPath("$.weeks[11].before.bugsDensity").value(nullValue()))
        .andExpect(jsonPath("$.weeks[11].before.vulnerabilitiesDensity").value(nullValue()))
        .andExpect(jsonPath("$.weeks[11].before.codeSmellsDensity").value(nullValue()))
        .andExpect(jsonPath("$.weeks[11].before.findingsDensity").value(nullValue()))
        .andExpect(jsonPath("$.weeks[11].final.findings").value(0))
        .andExpect(jsonPath("$.weeks[11].final.findingsDensity").value(nullValue()))
        .andExpect(jsonPath("$.weeks[11].reductionPercent").value(100.0));
  }

  /** AK 7: „seit“ steht als Zeitpunkt, wenn die erste Messung nach dem Fensterbeginn liegt. */
  @Test
  void dataSinceStehtAlsZeitpunktOderNull() throws Exception {
    when(service.wochenwerte(USER, BOARD))
        .thenReturn(new Wochenwerte(true, SEIT, leereWochen(List.of())));
    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataSince").value("2026-10-14T08:30:00Z"));

    when(service.wochenwerte(USER, BOARD))
        .thenReturn(new Wochenwerte(true, null, leereWochen(List.of())));
    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasMeasurements").value(true))
        .andExpect(jsonPath("$.dataSince").value(nullValue()));
  }

  @Test
  void einUnbekanntesBoardBekommt404() throws Exception {
    doThrow(new BoardNotFoundException()).when(service).wochenwerte(anyLong(), anyLong());

    mvc.perform(get(PFAD)).andExpect(status().isNotFound());
  }

  @Test
  void einNichtmitgliedBekommt404() throws Exception {
    doThrow(new ProjectNotFoundException()).when(service).wochenwerte(anyLong(), anyLong());

    mvc.perform(get(PFAD)).andExpect(status().isNotFound());
  }

  private static Instant wochenbeginn(int index) {
    return ERSTE_WOCHE.plus(7L * index, ChronoUnit.DAYS);
  }

  /** Zwölf Wochen ohne zählende Messung; gefüllte Wochen ersetzen die ihres Beginns. */
  private static List<Woche> leereWochen(List<Woche> gefuellt) {
    List<Woche> wochen = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      Instant beginn = wochenbeginn(i);
      wochen.add(
          gefuellt.stream()
              .filter(w -> w.wochenbeginn().equals(beginn))
              .findFirst()
              .orElse(new Woche(beginn, 0, null, null, null, null, List.of())));
    }
    return wochen;
  }
}
