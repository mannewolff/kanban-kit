package org.mwolff.manban.nightrun.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.nightrun.application.VorhabenKostenService;
import org.mwolff.manban.nightrun.application.VorhabenKostenService.AbschlussView;
import org.mwolff.manban.nightrun.application.VorhabenKostenService.LaufendView;
import org.mwolff.manban.nightrun.application.VorhabenKostenService.VergleichView;
import org.mwolff.manban.nightrun.application.VorhabenKostenService.VoraussageView;
import org.mwolff.manban.nightrun.application.VorhabenKostenService.VorhabenKostenView;
import org.mwolff.manban.nightrun.application.VorhabenNotFoundException;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Art;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Grund;
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
 * Die beiden Aufrufe der Kostenzeile (Issue #2005, Plan #1990 Paket 5), Service gemockt — Muster
 * {@code NightRunUsageControllerTest} mit standalone MockMvc. 403 und 404 liefert der Service über
 * {@code requireNightRunAccess}; die Rechte selbst prüft {@code VorhabenKostenServiceTest}.
 */
class VorhabenKostenControllerTest {

  private static final long USER = 7L;
  private static final long BOARD = 11L;
  private static final long KARTE = 400L;
  private static final String PFAD_BOARD = "/api/boards/" + BOARD + "/night-run-epic-costs";
  private static final String PFAD_KARTE = "/api/cards/" + KARTE + "/night-run-epic-costs";
  private static final Instant JETZT = Instant.parse("2026-11-02T06:00:00Z");

  private VorhabenKostenService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(VorhabenKostenService.class);
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(
                new VorhabenKostenController(service, Clock.fixed(JETZT, ZoneOffset.UTC)))
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

  private static VorhabenKostenView laufend() {
    return new VorhabenKostenView(
        KARTE,
        400,
        false,
        new LaufendView(
            true,
            new BigDecimal("0.50"),
            false,
            new VoraussageView(
                Art.PAKETE,
                new BigDecimal("14.00"),
                new BigDecimal("11.00"),
                new BigDecimal("19.50"),
                8,
                2),
            null),
        null);
  }

  private static VorhabenKostenView abgeschlossen() {
    return new VorhabenKostenView(
        500L,
        500,
        true,
        null,
        new AbschlussView(
            Instant.parse("2026-11-01T06:00:00Z"),
            new BigDecimal("18.00"),
            false,
            new VergleichView(
                true,
                Art.ANFORDERUNG,
                new BigDecimal("16.00"),
                new BigDecimal("12.00"),
                new BigDecimal("21.00"),
                13),
            new VergleichView(false, null, null, null, null, null)));
  }

  @Test
  void jeBoardLiefertJedesVorhaben() throws Exception {
    when(service.jeBoard(USER, BOARD, JETZT)).thenReturn(List.of(laufend(), abgeschlossen()));

    mvc.perform(get(PFAD_BOARD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].epicId").value(KARTE))
        .andExpect(jsonPath("$[0].number").value(400))
        .andExpect(jsonPath("$[0].archiviert").value(false))
        .andExpect(jsonPath("$[0].laufend.hatDokumentOderPaket").value(true))
        .andExpect(jsonPath("$[0].laufend.bisher").value(0.5))
        .andExpect(jsonPath("$[0].laufend.unvollstaendig").value(false))
        .andExpect(jsonPath("$[0].laufend.voraussage.art").value("PAKETE"))
        .andExpect(jsonPath("$[0].laufend.voraussage.typisch").value(14.0))
        .andExpect(jsonPath("$[0].laufend.voraussage.unten").value(11.0))
        .andExpect(jsonPath("$[0].laufend.voraussage.oben").value(19.5))
        .andExpect(jsonPath("$[0].laufend.voraussage.paketzahl").value(8))
        .andExpect(jsonPath("$[0].laufend.voraussage.ohneStufe").value(2))
        .andExpect(jsonPath("$[0].laufend.zuWenigDaten").value(nullValue()))
        .andExpect(jsonPath("$[0].abschluss").value(nullValue()))
        .andExpect(jsonPath("$[1].archiviert").value(true))
        .andExpect(jsonPath("$[1].laufend").value(nullValue()))
        .andExpect(jsonPath("$[1].abschluss.abgeschlossenAt").value("2026-11-01T06:00:00Z"))
        .andExpect(jsonPath("$[1].abschluss.tatsaechlich").value(18.0))
        .andExpect(jsonPath("$[1].abschluss.unvollstaendig").value(false))
        .andExpect(jsonPath("$[1].abschluss.anforderung.beziffert").value(true))
        .andExpect(jsonPath("$[1].abschluss.anforderung.art").value("ANFORDERUNG"))
        .andExpect(jsonPath("$[1].abschluss.anforderung.typisch").value(16.0))
        .andExpect(jsonPath("$[1].abschluss.anforderung.unten").value(12.0))
        .andExpect(jsonPath("$[1].abschluss.anforderung.oben").value(21.0))
        .andExpect(jsonPath("$[1].abschluss.anforderung.abweichungProzent").value(13))
        .andExpect(jsonPath("$[1].abschluss.pakete.beziffert").value(false))
        .andExpect(jsonPath("$[1].abschluss.pakete.typisch").value(nullValue()))
        .andExpect(jsonPath("$[1].abschluss.pakete.abweichungProzent").value(nullValue()));
  }

  @Test
  void jeBoardMitZuWenigDaten() throws Exception {
    when(service.jeBoard(USER, BOARD, JETZT))
        .thenReturn(
            List.of(
                new VorhabenKostenView(
                    KARTE,
                    400,
                    false,
                    new LaufendView(true, BigDecimal.ZERO, true, null, Grund.ZU_WENIG_VORHABEN),
                    null)));

    mvc.perform(get(PFAD_BOARD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].laufend.voraussage").value(nullValue()))
        .andExpect(jsonPath("$[0].laufend.zuWenigDaten").value("ZU_WENIG_VORHABEN"))
        .andExpect(jsonPath("$[0].laufend.unvollstaendig").value(true));
  }

  @Test
  void jeBoardOhneVerbrauchsrecht403() throws Exception {
    when(service.jeBoard(anyLong(), anyLong(), any()))
        .thenThrow(new ProjectAccessDeniedException());

    mvc.perform(get(PFAD_BOARD)).andExpect(status().isForbidden());
  }

  @Test
  void jeBoardNichtmitgliedOderUnbekanntesBoard404() throws Exception {
    when(service.jeBoard(anyLong(), eq(BOARD), any()))
        .thenThrow(new ProjectNotFoundException())
        .thenThrow(new BoardNotFoundException());

    mvc.perform(get(PFAD_BOARD)).andExpect(status().isNotFound());
    mvc.perform(get(PFAD_BOARD)).andExpect(status().isNotFound());
  }

  @Test
  void jeVorhabenLiefertEinVorhaben() throws Exception {
    when(service.jeVorhaben(USER, KARTE, JETZT)).thenReturn(laufend());

    mvc.perform(get(PFAD_KARTE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.epicId").value(KARTE))
        .andExpect(jsonPath("$.laufend.voraussage.typisch").value(14.0));
  }

  @Test
  void jeVorhabenFuerEineKarteOhneVorhaben404() throws Exception {
    when(service.jeVorhaben(anyLong(), eq(KARTE), any()))
        .thenThrow(new VorhabenNotFoundException())
        .thenThrow(new CardNotFoundException());

    mvc.perform(get(PFAD_KARTE)).andExpect(status().isNotFound());
    mvc.perform(get(PFAD_KARTE)).andExpect(status().isNotFound());
  }

  @Test
  void jeVorhabenRechte() throws Exception {
    when(service.jeVorhaben(anyLong(), anyLong(), any()))
        .thenThrow(new ProjectAccessDeniedException())
        .thenThrow(new ProjectNotFoundException());

    mvc.perform(get(PFAD_KARTE)).andExpect(status().isForbidden());
    mvc.perform(get(PFAD_KARTE)).andExpect(status().isNotFound());
  }
}
