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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.application.NightRunReworkService;
import org.mwolff.manban.nightrun.domain.Nacharbeit;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Karteneintrag;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Woche;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Wochenwert;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Zeile;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Zustand;
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
 * Der Endpunkt der Platte „Nacharbeit“ (Issue #1757), Service gemockt — Muster {@code
 * NightRunUsageControllerTest} mit standalone MockMvc. 403 und 404 liefert der Service über {@code
 * requireNightRunAccess}; dass der volle Kontext dieselben Codes liefert, belegt {@code
 * NightRunReworkIT}.
 */
class NightRunReworkControllerTest {

  private static final long USER = 7L;
  private static final long BOARD = 11L;
  private static final String PFAD = "/api/boards/" + BOARD + "/night-run-rework";
  private static final Instant AELTESTER = Instant.parse("2026-08-01T22:00:00Z");
  private static final Instant ERFASSUNG = Instant.parse("2026-10-15T08:00:00Z");
  private static final Instant WOCHE_ALT = Instant.parse("2026-07-23T06:00:00Z");
  private static final Instant WOCHE_NEU = Instant.parse("2026-10-08T06:00:00Z");

  private NightRunReworkService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(NightRunReworkService.class);
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(new NightRunReworkController(service))
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
  void liefertZeilenKartenUndWochen() throws Exception {
    when(service.nacharbeit(USER, BOARD))
        .thenReturn(
            new Nacharbeit(
                true,
                new Zeile(AELTESTER, 1, 14, List.of(new Karteneintrag(1712, "Paket", 2))),
                new Zeile(ERFASSUNG, 0, 0, List.of()),
                List.of(
                    new Woche(
                        WOCHE_ALT,
                        new Wochenwert(Zustand.KEINE_KARTE, 0, 0),
                        new Wochenwert(Zustand.KEINE_DATEN, 0, 0)),
                    new Woche(
                        WOCHE_NEU,
                        new Wochenwert(Zustand.QUOTE, 1, 14),
                        new Wochenwert(Zustand.KEINE_KARTE, 0, 0)))));

    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataSince").value("2026-08-01T22:00:00Z"))
        .andExpect(jsonPath("$.runsInPeriod").value(true))
        .andExpect(jsonPath("$.packages.dataSince").value("2026-08-01T22:00:00Z"))
        .andExpect(jsonPath("$.packages.affected").value(1))
        .andExpect(jsonPath("$.packages.processed").value(14))
        .andExpect(jsonPath("$.packages.cards", hasSize(1)))
        .andExpect(jsonPath("$.packages.cards[0].number").value(1712))
        .andExpect(jsonPath("$.packages.cards[0].title").value("Paket"))
        .andExpect(jsonPath("$.packages.cards[0].count").value(2))
        .andExpect(jsonPath("$.documents.dataSince").value("2026-10-15T08:00:00Z"))
        .andExpect(jsonPath("$.documents.affected").value(0))
        .andExpect(jsonPath("$.documents.processed").value(0))
        .andExpect(jsonPath("$.documents.cards", hasSize(0)))
        .andExpect(jsonPath("$.weeks", hasSize(2)))
        .andExpect(jsonPath("$.weeks[0].weekStart").value("2026-07-23T06:00:00Z"))
        .andExpect(jsonPath("$.weeks[0].packages.state").value("KEINE_KARTE"))
        .andExpect(jsonPath("$.weeks[0].documents.state").value("KEINE_DATEN"))
        .andExpect(jsonPath("$.weeks[1].weekStart").value("2026-10-08T06:00:00Z"))
        .andExpect(jsonPath("$.weeks[1].packages.state").value("QUOTE"))
        .andExpect(jsonPath("$.weeks[1].packages.affected").value(1))
        .andExpect(jsonPath("$.weeks[1].packages.processed").value(14))
        .andExpect(jsonPath("$.weeks[1].documents.state").value("KEINE_KARTE"));
  }

  @Test
  void ohneAufbewahrtenLaufIstDerDatenbeginnNull() throws Exception {
    when(service.nacharbeit(USER, BOARD))
        .thenReturn(
            new Nacharbeit(
                false,
                new Zeile(null, 0, 0, List.of()),
                new Zeile(null, 0, 0, List.of()),
                List.of()));

    mvc.perform(get(PFAD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataSince").value(nullValue()))
        .andExpect(jsonPath("$.runsInPeriod").value(false))
        .andExpect(jsonPath("$.packages.dataSince").value(nullValue()))
        .andExpect(jsonPath("$.documents.dataSince").value(nullValue()));
  }

  @Test
  @DisplayName("Szenario: Ohne Recht auf die Runs")
  void ohneRechtAufDieRuns() throws Exception {
    doThrow(new ProjectAccessDeniedException()).when(service).nacharbeit(anyLong(), anyLong());
    mvc.perform(get(PFAD)).andExpect(status().isForbidden());

    doThrow(new ProjectNotFoundException()).when(service).nacharbeit(anyLong(), anyLong());
    mvc.perform(get(PFAD)).andExpect(status().isNotFound());
  }
}
