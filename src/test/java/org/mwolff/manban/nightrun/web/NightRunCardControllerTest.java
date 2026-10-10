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
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.nightrun.application.KettenstandDerKarte;
import org.mwolff.manban.nightrun.application.NightRunProgressService;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.domain.KettenStand;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.nightrun.domain.ProgressStage;
import org.mwolff.manban.nightrun.domain.StationStand;
import org.mwolff.manban.nightrun.domain.StationsZustand;
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

  private static final long CARD = 812L;
  private static final String KETTE = "/api/cards/" + CARD + "/night-chain";

  private NightRunService service;
  private NightRunProgressService fortschritt;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(NightRunService.class);
    fortschritt = mock(NightRunProgressService.class);
    // Wie in NightRunControllerTest: Instants als ISO-Text, so wie in der laufenden Anwendung.
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(new NightRunCardController(service, fortschritt))
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
    return anlauf(startedAt, mode, NightRunKind.NIGHT, state, errorClass, usage);
  }

  private static NightRunItem anlauf(
      Instant startedAt,
      NightRunMode mode,
      NightRunKind kind,
      NightRunState state,
      NightRunErrorClass errorClass,
      NightRunUsage usage) {
    return new NightRunItem(
        31L,
        null,
        PROJECT,
        startedAt,
        mode,
        kind,
        967,
        "Endpunkt",
        state,
        errorClass,
        1_122_000L,
        state == NightRunState.GREEN ? "4c9f42a" : null,
        "Auszug",
        usage,
        null,
        List.of());
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
                    new NightRunUsage(
                        new BigDecimal("0.940000"), 412_000L, 3_100L, 380_000L, null, null)),
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
        .andExpect(jsonPath("$[0].kind").value("NIGHT"))
        .andExpect(jsonPath("$[1].startedAt").value("2026-09-01T22:00:00Z"))
        .andExpect(jsonPath("$[1].mode").value("IMPLEMENTATION"))
        .andExpect(jsonPath("$[1].errorClass").value("CHECKS_RED"));
  }

  /**
   * Die Gattung steht am Anlauf, statt ihn zu filtern (Issue #1015, Plan #1007 E10): Nachtlauf und
   * interaktive Sitzung stehen nebeneinander an derselben Karte, jeder mit seiner eigenen. Erst
   * daran erkennt die Anzeige eine Sitzung — {@code mode} allein trüge sie nicht, denn eine Sitzung
   * aus der Zeit vor der eigenen Lauf-Art gäbe sich damit als Nachtlauf aus.
   */
  @Test
  void jederAnlaufTraegtSeineGattung() throws Exception {
    when(service.anlaeufeDerKarte(USER, PROJECT, 967))
        .thenReturn(
            List.of(
                anlauf(
                    JUENGER,
                    NightRunMode.INTERACTIVE,
                    NightRunKind.INTERACTIVE,
                    NightRunState.GREEN,
                    null,
                    null),
                anlauf(
                    AELTER,
                    NightRunMode.CHAIN,
                    NightRunKind.NIGHT,
                    NightRunState.GREEN,
                    null,
                    null)));

    mvc.perform(get(PATH).param("cardNumber", "967"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].kind").value("INTERACTIVE"))
        .andExpect(jsonPath("$[1].kind").value("NIGHT"));
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
                    new NightRunUsage(new BigDecimal("1.500000"), null, null, null, null, null))));

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

  // --- Kettenstand (Issue #1452) -------------------------------------------------------------

  @Test
  void kettenstandLiefertZielGrenzeStationenUndDieAngabenDerLeiste() throws Exception {
    when(fortschritt.kettenstand(USER, CARD))
        .thenReturn(
            new KettenstandDerKarte(
                new KettenStand(
                    ProgressStage.UMSETZUNG,
                    2,
                    false,
                    new KettenStand.Projektgrenze(
                        ProgressStage.PAKETE, "wartet: Übergang x im Projekt nicht freigegeben"),
                    List.of(
                        new StationStand(
                            ProgressStage.PLAN, StationsZustand.ERLEDIGT, "erledigt", null),
                        new StationStand(
                            ProgressStage.ABDECKUNG,
                            StationsZustand.WARTET,
                            "Projektgrenze",
                            "wartet: Übergang x im Projekt nicht freigegeben"))),
                true,
                true,
                JUENGER));

    mvc.perform(get(KETTE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ziel").value("UMSETZUNG"))
        .andExpect(jsonPath("$.pruefer").value(2))
        .andExpect(jsonPath("$.zielErreicht").value(false))
        .andExpect(jsonPath("$.grenze.stufe").value("PAKETE"))
        .andExpect(
            jsonPath("$.grenze.grund").value("wartet: Übergang x im Projekt nicht freigegeben"))
        .andExpect(jsonPath("$.stationen.length()").value(2))
        .andExpect(jsonPath("$.stationen[0].station").value("PLAN"))
        .andExpect(jsonPath("$.stationen[0].zustand").value("ERLEDIGT"))
        .andExpect(jsonPath("$.stationen[0].text").value("erledigt"))
        .andExpect(jsonPath("$.stationen[0].grund").value(nullValue()))
        .andExpect(jsonPath("$.stationen[1].station").value("ABDECKUNG"))
        .andExpect(jsonPath("$.stationen[1].zustand").value("WARTET"))
        .andExpect(jsonPath("$.stationen[1].text").value("Projektgrenze"))
        .andExpect(
            jsonPath("$.stationen[1].grund")
                .value("wartet: Übergang x im Projekt nicht freigegeben"))
        .andExpect(jsonPath("$.uebernommen").value(true))
        .andExpect(jsonPath("$.planReviewVorhanden").value(true))
        .andExpect(jsonPath("$.lauf").value("2026-09-02T22:00:00Z"));
  }

  /** Vor dem Start: ohne Ziel, Prüferzahl, Grenze und Lauf — die Felder stehen als {@code null}. */
  @Test
  void kettenstandVorDemStartTraegtNullStattFehlenderAngaben() throws Exception {
    when(fortschritt.kettenstand(USER, CARD))
        .thenReturn(
            new KettenstandDerKarte(
                new KettenStand(null, null, false, null, List.of()), false, false, null));

    mvc.perform(get(KETTE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ziel").value(nullValue()))
        .andExpect(jsonPath("$.pruefer").value(nullValue()))
        .andExpect(jsonPath("$.grenze").value(nullValue()))
        .andExpect(jsonPath("$.lauf").value(nullValue()))
        .andExpect(jsonPath("$.uebernommen").value(false))
        .andExpect(jsonPath("$.planReviewVorhanden").value(false));
  }

  @Test
  void kettenstandReichtDieRechteablehnungDurch() throws Exception {
    when(fortschritt.kettenstand(USER, CARD)).thenThrow(new ProjectAccessDeniedException());

    mvc.perform(get(KETTE)).andExpect(status().isForbidden());
  }

  @Test
  void kettenstandEinerUnbekanntenKarteIst404() throws Exception {
    when(fortschritt.kettenstand(USER, CARD)).thenThrow(new CardNotFoundException());

    mvc.perform(get(KETTE)).andExpect(status().isNotFound());
  }
}
