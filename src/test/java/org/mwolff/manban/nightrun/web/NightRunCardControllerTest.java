package org.mwolff.manban.nightrun.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests des Endpunkts für die Anläufe einer Karte (Issue #967), Service gemockt — Muster {@code
 * NightRunControllerTest} mit standalone MockMvc, damit Pfad, {@code @RequestParam} und
 * {@code @AuthenticationPrincipal} tatsächlich auslösen. Die Rechte-Matrix gegen den vollen Kontext
 * belegt {@code NightRunIT}.
 */
class NightRunCardControllerTest {

  private static final long USER = 7L;
  private static final long PROJECT = 3L;
  private static final String PATH = "/api/projects/" + PROJECT + "/night-runs/items";
  private static final Instant JUENGER = Instant.parse("2026-09-02T22:00:00Z");
  private static final Instant AELTER = Instant.parse("2026-09-01T22:00:00Z");

  private NightRunService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(NightRunService.class);
    // Wie in NightRunControllerTest: Instants als ISO-Text, so wie in der laufenden Anwendung.
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(new NightRunCardController(service))
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

  private static NightRunItem anlauf(
      Instant startedAt,
      NightRunMode mode,
      NightRunState state,
      NightRunErrorClass errorClass,
      NightRunUsage usage) {
    return new NightRunItem(
        31L,
        null,
        PROJECT,
        startedAt,
        mode,
        NightRunKind.NIGHT,
        967,
        "Endpunkt",
        state,
        errorClass,
        1_122_000L,
        state == NightRunState.GREEN ? "4c9f42a" : null,
        "Auszug",
        usage);
  }

  @Test
  void liefertJeAnlaufAlleFelder_inDerReihenfolgeDesService() throws Exception {
    when(service.anlaeufeDerKarte(USER, PROJECT, 967))
        .thenReturn(
            List.of(
                anlauf(
                    JUENGER,
                    NightRunMode.CHAIN,
                    NightRunState.GREEN,
                    null,
                    new NightRunUsage(new BigDecimal("0.940000"), 412_000L, 3_100L, 380_000L)),
                anlauf(
                    AELTER,
                    NightRunMode.IMPLEMENTATION,
                    NightRunState.RED,
                    NightRunErrorClass.CHECKS_RED,
                    null)));

    mvc.perform(get(PATH).param("cardNumber", "967"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].startedAt").value("2026-09-02T22:00:00Z"))
        .andExpect(jsonPath("$[0].mode").value("CHAIN"))
        .andExpect(jsonPath("$[0].state").value("GREEN"))
        .andExpect(jsonPath("$[0].errorClass").isEmpty())
        .andExpect(jsonPath("$[0].durationMs").value(1_122_000))
        .andExpect(jsonPath("$[0].commitHash").value("4c9f42a"))
        .andExpect(jsonPath("$[0].usage.costUsd").value(0.94))
        .andExpect(jsonPath("$[0].usage.inputTokens").value(412_000))
        .andExpect(jsonPath("$[0].usage.outputTokens").value(3_100))
        .andExpect(jsonPath("$[0].usage.cachedInputTokens").value(380_000))
        .andExpect(jsonPath("$[1].startedAt").value("2026-09-01T22:00:00Z"))
        .andExpect(jsonPath("$[1].mode").value("IMPLEMENTATION"))
        .andExpect(jsonPath("$[1].errorClass").value("CHECKS_RED"));
  }

  /** „Nicht gemessen" ist {@code null} und nie 0 — eine 0 behauptete, es sei nichts verbraucht. */
  @Test
  void nichtGemessenerVerbrauchStehtAlsNull() throws Exception {
    when(service.anlaeufeDerKarte(USER, PROJECT, 967))
        .thenReturn(
            List.of(
                anlauf(JUENGER, NightRunMode.IMPLEMENTATION, NightRunState.GREEN, null, null),
                anlauf(
                    AELTER,
                    NightRunMode.IMPLEMENTATION,
                    NightRunState.GREEN,
                    null,
                    new NightRunUsage(new BigDecimal("1.500000"), null, null, null))));

    mvc.perform(get(PATH).param("cardNumber", "967"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].usage").value(nullValue()))
        .andExpect(jsonPath("$[1].usage.costUsd").value(1.5))
        .andExpect(jsonPath("$[1].usage.inputTokens").value(nullValue()))
        .andExpect(jsonPath("$[1].usage.outputTokens").value(nullValue()))
        .andExpect(jsonPath("$[1].usage.cachedInputTokens").value(nullValue()));
  }

  @Test
  void reichtDieRechteablehnungDurch() throws Exception {
    when(service.anlaeufeDerKarte(USER, PROJECT, 967))
        .thenThrow(new ProjectAccessDeniedException());

    mvc.perform(get(PATH).param("cardNumber", "967")).andExpect(status().isForbidden());
  }

  @Test
  void ohneKartennummerIst400() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }
}
