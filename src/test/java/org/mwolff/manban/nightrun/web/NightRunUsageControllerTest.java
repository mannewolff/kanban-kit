package org.mwolff.manban.nightrun.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.EpicRef;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.CardTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageService;
import org.mwolff.manban.nightrun.application.NightRunUsageService.Coverage;
import org.mwolff.manban.nightrun.application.NightRunUsageService.EpicUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.KindSplit;
import org.mwolff.manban.nightrun.application.NightRunUsageService.NightSummary;
import org.mwolff.manban.nightrun.application.NightRunUsageService.NightUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.PeriodFigures;
import org.mwolff.manban.nightrun.application.NightRunUsageService.PeriodUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.UsageSplit;
import org.mwolff.manban.nightrun.domain.NightRunPeriod;
import org.mwolff.manban.nightrun.domain.NightRunPeriodType;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
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
 * Tests der beiden Endpunkte der Verbrauchs-Auswertung (Issue #939), Service gemockt — Muster
 * {@code NightRunControllerTest} mit standalone MockMvc, damit Bindung, Validierung und
 * {@code @AuthenticationPrincipal} tatsächlich auslösen. 403 und 404 liefert der Service über
 * {@code requireOwner}; dass der volle Kontext dieselben Codes liefert, belegt {@code NightRunIT}.
 */
// Testklasse: Die Importe folgen den abgebildeten Typen.
@SuppressWarnings("PMD.ExcessiveImports")
class NightRunUsageControllerTest {

  private static final long USER = 7L;
  private static final long PROJECT = 3L;
  private static final String PFAD_NACHT = "/api/projects/" + PROJECT + "/night-run-usage/night";
  private static final String PFAD_ZEITRAUM = "/api/projects/" + PROJECT + "/night-run-usage";
  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
  private static final NightRunUsage NICHTS = new NightRunUsage(null, null, null, null);

  private NightRunUsageService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(NightRunUsageService.class);
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(new NightRunUsageController(service))
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

  /** Eine Gattung, die in der Spanne nicht vorkommt. */
  private static final UsageSplit LEER = new UsageSplit(NICHTS, NICHTS, NICHTS);

  private static NightUsageView nacht(NightRunUsage gesamt, NightRunUsage karten) {
    UsageSplit teilung = new UsageSplit(gesamt, karten, gesamt.minus(karten));
    return new NightUsageView(
        LocalDate.of(2026, 9, 15),
        2L,
        4_000L,
        1L,
        teilung,
        new KindSplit(teilung, LEER),
        true,
        List.of(new CardTotals(721, 2L, null, karten, NICHTS)));
  }

  private static PeriodUsageView zeitraum(@Nullable Instant erfassungsbeginn) {
    Clock jetzt = Clock.fixed(Instant.parse("2026-09-16T11:00:00Z"), ZoneOffset.UTC);
    NightRunPeriod monat = NightRunPeriod.of(NightRunPeriodType.MONTH, BERLIN, jetzt, 0);
    UsageSplit teilung =
        new UsageSplit(
            new NightRunUsage(new BigDecimal("9.50"), 1_000L, 100L, 250L),
            new NightRunUsage(new BigDecimal("6.00"), 800L, 80L, 200L),
            new NightRunUsage(new BigDecimal("3.50"), 200L, 20L, 50L));
    UsageSplit sitzungen =
        new UsageSplit(
            new NightRunUsage(new BigDecimal("2.50"), 400L, 40L, 100L),
            new NightRunUsage(new BigDecimal("1.00"), 300L, 30L, 80L),
            new NightRunUsage(new BigDecimal("1.50"), 100L, 10L, 20L));
    KindSplit jeGattung = new KindSplit(teilung, sitzungen);
    return new PeriodUsageView(
        new PeriodFigures(
            monat, Coverage.PARTIAL, 5L, 9_000L, 4L, teilung, jeGattung, erfassungsbeginn),
        new PeriodFigures(
            monat.previous(),
            Coverage.BEFORE_RETENTION,
            0L,
            0L,
            0L,
            LEER,
            new KindSplit(LEER, LEER),
            erfassungsbeginn),
        List.of(new NightSummary(LocalDate.of(2026, 8, 3), 2L, 1L, teilung, jeGattung, false)),
        List.of(
            new EpicUsageView(
                new EpicRef(11L, "PLANEN", "Planen"),
                2L,
                new NightRunUsage(new BigDecimal("4.00"), null, null, null))),
        new EpicUsageView(null, 1L, NICHTS),
        true);
  }

  private static PeriodUsageView zeitraum() {
    return zeitraum(Instant.parse("2026-08-14T07:00:00Z"));
  }

  // --- Nacht -----------------------------------------------------------------------------------

  @Test
  void nacht_liefertDieTagesgruppe_undReichtDatumUndZoneDurch() throws Exception {
    when(service.night(USER, PROJECT, LocalDate.of(2026, 9, 15), BERLIN))
        .thenReturn(nacht(new NightRunUsage(new BigDecimal("10"), null, null, null), NICHTS));

    mvc.perform(get(PFAD_NACHT).param("date", "2026-09-15").param("zone", "Europe/Berlin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.night").value("2026-09-15"))
        .andExpect(jsonPath("$.runCount").value(2))
        .andExpect(jsonPath("$.durationMs").value(4000))
        .andExpect(jsonPath("$.cardCount").value(1))
        .andExpect(jsonPath("$.aborted").value(true))
        .andExpect(jsonPath("$.usage.total.costUsd").value(10))
        .andExpect(jsonPath("$.usageByKind.night.total.costUsd").value(10))
        .andExpect(jsonPath("$.usageByKind.interactive.total.costUsd").value(nullValue()))
        .andExpect(jsonPath("$.cards[0].cardNumber").value(721))
        .andExpect(jsonPath("$.cards[0].attemptCount").value(2))
        .andExpect(jsonPath("$.cards[0].durationMs").value(nullValue()))
        .andExpect(jsonPath("$.cards[0].usageByKind.interactive.costUsd").value(nullValue()));
  }

  /** „Nicht gemessen" steht als {@code null} und nie als 0 (Plan E5). */
  @Test
  void nacht_eineFehlendeVerbrauchsangabeIstNull_nicht0() throws Exception {
    when(service.night(anyLong(), anyLong(), any(), any())).thenReturn(nacht(NICHTS, NICHTS));

    mvc.perform(get(PFAD_NACHT).param("date", "2026-09-15").param("zone", "Europe/Berlin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.usage.total.costUsd").value(nullValue()))
        .andExpect(jsonPath("$.usage.total.inputTokens").value(nullValue()))
        .andExpect(jsonPath("$.usage.remainder.cachedInputTokens").value(nullValue()))
        .andExpect(jsonPath("$.usage.cardShare.cachedInputSharePercent").value(nullValue()))
        .andExpect(jsonPath("$.cards[0].usage.outputTokens").value(nullValue()));
  }

  @Test
  void nacht_verbotenFuerEinMitgliedOhneOwnerRecht() throws Exception {
    when(service.night(anyLong(), anyLong(), any(), any()))
        .thenThrow(new ProjectAccessDeniedException());

    mvc.perform(get(PFAD_NACHT).param("date", "2026-09-15").param("zone", "Europe/Berlin"))
        .andExpect(status().isForbidden());
  }

  @Test
  void nacht_nichtGefundenFuerEinProjektDasEsNichtGibt() throws Exception {
    when(service.night(anyLong(), anyLong(), any(), any()))
        .thenThrow(new ProjectNotFoundException());

    mvc.perform(get(PFAD_NACHT).param("date", "2026-09-15").param("zone", "Europe/Berlin"))
        .andExpect(status().isNotFound());
  }

  @Test
  void nacht_ungueltigBeiUnbekannterZone() throws Exception {
    mvc.perform(get(PFAD_NACHT).param("date", "2026-09-15").param("zone", "Nirgendwo/Nirgends"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void nacht_ungueltigBeiOffsetZone() throws Exception {
    mvc.perform(get(PFAD_NACHT).param("date", "2026-09-15").param("zone", "+05:30"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void nacht_ungueltigBeiUnlesbaremDatum() throws Exception {
    mvc.perform(get(PFAD_NACHT).param("date", "15.09.2026").param("zone", "Europe/Berlin"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  // --- Zeitraum --------------------------------------------------------------------------------

  @Test
  void zeitraum_liefertZeitraumVorzeitraumNaechteUndVorhaben() throws Exception {
    when(service.period(USER, PROJECT, NightRunPeriodType.MONTH, 0, BERLIN)).thenReturn(zeitraum());

    mvc.perform(
            get(PFAD_ZEITRAUM)
                .param("type", "MONTH")
                .param("stepsBack", "0")
                .param("zone", "Europe/Berlin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.current.type").value("MONTH"))
        .andExpect(jsonPath("$.current.firstDay").value("2026-08-01"))
        .andExpect(jsonPath("$.current.lastDay").value("2026-08-31"))
        .andExpect(jsonPath("$.current.from").value("2026-08-01T10:00:00Z"))
        .andExpect(jsonPath("$.current.to").value("2026-09-01T10:00:00Z"))
        .andExpect(jsonPath("$.current.coverage").value("PARTIAL"))
        .andExpect(jsonPath("$.current.noRuns").value(false))
        .andExpect(jsonPath("$.current.runCount").value(5))
        .andExpect(jsonPath("$.current.durationMs").value(9000))
        .andExpect(jsonPath("$.current.cardCount").value(4))
        .andExpect(jsonPath("$.current.usage.remainder.costUsd").value(3.5))
        .andExpect(jsonPath("$.current.usage.total.cachedInputSharePercent").value(25))
        .andExpect(jsonPath("$.current.usageByKind.night.total.costUsd").value(9.5))
        .andExpect(jsonPath("$.current.usageByKind.interactive.total.costUsd").value(2.5))
        .andExpect(jsonPath("$.current.usageByKind.interactive.cardShare.inputTokens").value(300))
        .andExpect(jsonPath("$.current.usageByKind.interactive.remainder.costUsd").value(1.5))
        .andExpect(jsonPath("$.current.interactiveUsageSince").value("2026-08-14T07:00:00Z"))
        .andExpect(jsonPath("$.previous.firstDay").value("2026-07-01"))
        .andExpect(jsonPath("$.previous.coverage").value("BEFORE_RETENTION"))
        .andExpect(jsonPath("$.previous.noRuns").value(true))
        .andExpect(jsonPath("$.nights[0].night").value("2026-08-03"))
        .andExpect(jsonPath("$.nights[0].runCount").value(2))
        .andExpect(jsonPath("$.nights[0].cardCount").value(1))
        .andExpect(jsonPath("$.nights[0].aborted").value(false))
        .andExpect(jsonPath("$.nights[0].usage.cardShare.costUsd").value(6))
        .andExpect(jsonPath("$.nights[0].usageByKind.night.cardShare.costUsd").value(6))
        .andExpect(jsonPath("$.nights[0].usageByKind.interactive.cardShare.costUsd").value(1))
        .andExpect(jsonPath("$.epics[0].epicId").value(11))
        .andExpect(jsonPath("$.epics[0].shortcode").value("PLANEN"))
        .andExpect(jsonPath("$.epics[0].title").value("Planen"))
        .andExpect(jsonPath("$.epics[0].cardCount").value(2))
        .andExpect(jsonPath("$.epics[0].usage.costUsd").value(4))
        .andExpect(jsonPath("$.withoutEpic.epicId").value(nullValue()))
        .andExpect(jsonPath("$.withoutEpic.shortcode").value(nullValue()))
        .andExpect(jsonPath("$.withoutEpic.title").value(nullValue()))
        .andExpect(jsonPath("$.withoutEpic.cardCount").value(1))
        .andExpect(jsonPath("$.withoutEpic.usage.costUsd").value(nullValue()))
        .andExpect(jsonPath("$.epicsOverlap").value(true));
  }

  /** Ohne je gemeldete Sitzung steht der Erfassungsbeginn als {@code null} (Plan E18). */
  @Test
  void zeitraum_ohneErfassungsbeginnStehtDortNull() throws Exception {
    when(service.period(anyLong(), anyLong(), any(), anyInt(), any()))
        .thenReturn(zeitraum((Instant) null));

    mvc.perform(
            get(PFAD_ZEITRAUM).param("type", "DAY").param("stepsBack", "0").param("zone", "UTC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.current.interactiveUsageSince").value(nullValue()))
        .andExpect(jsonPath("$.previous.interactiveUsageSince").value(nullValue()));
  }

  @Test
  void zeitraum_derPlattformAdminBekommtEineAntwort() throws Exception {
    when(service.period(anyLong(), anyLong(), any(), anyInt(), any())).thenReturn(zeitraum());

    mvc.perform(
            get(PFAD_ZEITRAUM).param("type", "DAY").param("stepsBack", "3").param("zone", "UTC"))
        .andExpect(status().isOk());

    verify(service).period(eq(USER), eq(PROJECT), eq(NightRunPeriodType.DAY), eq(3), any());
  }

  @Test
  void zeitraum_verbotenFuerEinMitgliedOhneOwnerRecht() throws Exception {
    when(service.period(anyLong(), anyLong(), any(), anyInt(), any()))
        .thenThrow(new ProjectAccessDeniedException());

    mvc.perform(
            get(PFAD_ZEITRAUM).param("type", "WEEK").param("stepsBack", "0").param("zone", "UTC"))
        .andExpect(status().isForbidden());
  }

  @Test
  void zeitraum_nichtGefundenFuerEinProjektDasEsNichtGibt() throws Exception {
    when(service.period(anyLong(), anyLong(), any(), anyInt(), any()))
        .thenThrow(new ProjectNotFoundException());

    mvc.perform(
            get(PFAD_ZEITRAUM).param("type", "WEEK").param("stepsBack", "0").param("zone", "UTC"))
        .andExpect(status().isNotFound());
  }

  @Test
  void zeitraum_ungueltigBeiUnbekannterZone() throws Exception {
    mvc.perform(
            get(PFAD_ZEITRAUM)
                .param("type", "DAY")
                .param("stepsBack", "0")
                .param("zone", "Nirgendwo/Nirgends"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  /** {@code +05:30}, {@code Z} und {@code GMT+2} sind keine Regionszonen (Plan E4). */
  @Test
  void zeitraum_ungueltigBeiOffsetZonen() throws Exception {
    for (String zone : List.of("+05:30", "Z", "GMT+2", "UTC+01:00")) {
      mvc.perform(
              get(PFAD_ZEITRAUM).param("type", "DAY").param("stepsBack", "0").param("zone", zone))
          .andExpect(status().isBadRequest());
    }

    verifyNoInteractions(service);
  }

  @Test
  void zeitraum_ungueltigBeiNegativemRueckschritt() throws Exception {
    mvc.perform(
            get(PFAD_ZEITRAUM).param("type", "DAY").param("stepsBack", "-1").param("zone", "UTC"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void zeitraum_ungueltigOberhalbDerObergrenze_dieGrenzeSelbstIstErlaubt() throws Exception {
    when(service.period(anyLong(), anyLong(), any(), anyInt(), any())).thenReturn(zeitraum());
    String grenze = String.valueOf(NightRunUsageController.MAX_STEPS_BACK);
    String darueber = String.valueOf(NightRunUsageController.MAX_STEPS_BACK + 1);

    mvc.perform(
            get(PFAD_ZEITRAUM).param("type", "DAY").param("stepsBack", grenze).param("zone", "UTC"))
        .andExpect(status().isOk());
    mvc.perform(
            get(PFAD_ZEITRAUM)
                .param("type", "DAY")
                .param("stepsBack", darueber)
                .param("zone", "UTC"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void zeitraum_ungueltigBeiUnbekanntemTyp() throws Exception {
    mvc.perform(
            get(PFAD_ZEITRAUM)
                .param("type", "QUARTER")
                .param("stepsBack", "0")
                .param("zone", "UTC"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }
}
