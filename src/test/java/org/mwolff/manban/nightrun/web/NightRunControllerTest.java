package org.mwolff.manban.nightrun.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRun;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRunItem;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunItemView;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunResult;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunView;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
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
 * Tests der drei Nachtlauf-Endpoints (Service gemockt).
 *
 * <p>Läuft über einen standalone MockMvc statt über direkte Methodenaufrufe — Muster {@code
 * ProjectStartNumberControllerTest}, nicht {@code CommentControllerTest}: Nur so lösen
 * Mapping-Pfad, {@code @PathVariable}, {@code @AuthenticationPrincipal} und vor allem das
 * {@code @Valid} am Request-Body tatsächlich aus. Die {@code fieldErrors}-Zusage der Ablehnungen
 * belegt {@code NightRunIT}: {@code GlobalExceptionHandler} ist package-private in {@code
 * common.web} und hier nicht im Spiel, es käme nur der Statuscode an.
 */
class NightRunControllerTest {

  private static final long USER = 7L;
  private static final long PROJECT = 3L;
  private static final String PATH = "/api/projects/" + PROJECT + "/night-runs";
  private static final String JSON = "application/json";
  private static final Instant ERSTER = Instant.parse("2026-08-31T22:00:00Z");
  private static final Instant ZWEITER = Instant.parse("2026-09-01T22:00:00Z");

  private NightRunService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(NightRunService.class);
    // Der standalone MockMvc bringt Spring Boots Jackson-Konfiguration nicht mit; ohne die beiden
    // Einstellungen schriebe er Instants als Zeitstempel-Zahlen statt als ISO-Text. Nachgezogen
    // wird genau das, was `JacksonAutoConfiguration` in der laufenden Anwendung tut — die dortige
    // Form belegt zusätzlich `NightRunIT` gegen den vollen Kontext.
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(new NightRunController(service))
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

  /**
   * Belegt zugleich die Übersetzung Request → Service-Eingabe Feld für Feld und die Antwortform:
   * ein Ergebnis je übergebenem Lauf, in Anfragereihenfolge.
   */
  @Test
  // Ein ArgumentCaptor auf einen generischen Typ ist in Java nicht typsicher erzeugbar; der Cast
  // ist der übliche Weg und hier ungefährlich, weil der Controller nur List<NewNightRun> übergibt.
  @SuppressWarnings("unchecked")
  void submit_passesEveryFieldToService_andAnswersInRequestOrder() throws Exception {
    when(service.submit(eq(USER), eq(PROJECT), anyList()))
        .thenReturn(List.of(new NightRunResult(ERSTER, true), new NightRunResult(ZWEITER, false)));

    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content(
                    """
                    {"runs":[
                      {"startedAt":"2026-08-31T22:00:00Z","mode":"IMPLEMENTATION","durationMs":1234,
                       "processedCount":2,"skippedCount":1,"unparsedCount":3,
                       "unparsedSample":"Fehler: kaputt",
                       "items":[{"cardNumber":721,"title":"Persistenz","state":"RED",
                                 "errorClass":"CHECKS_RED","durationMs":900,
                                 "commitHash":"abc1234","excerpt":"mvn verify rot"}]},
                      {"startedAt":"2026-09-01T22:00:00Z","mode":"REVIEW","durationMs":10,
                       "processedCount":0,"skippedCount":0,"unparsedCount":0,"items":[]}]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].startedAt").value("2026-08-31T22:00:00Z"))
        .andExpect(jsonPath("$[0].created").value(true))
        .andExpect(jsonPath("$[1].startedAt").value("2026-09-01T22:00:00Z"))
        .andExpect(jsonPath("$[1].created").value(false));

    ArgumentCaptor<List<NewNightRun>> captor = ArgumentCaptor.forClass(List.class);
    verify(service).submit(eq(USER), eq(PROJECT), captor.capture());
    List<NewNightRun> uebergeben = captor.getValue();
    assertThat(uebergeben).hasSize(2);
    assertThat(uebergeben.get(0))
        .isEqualTo(
            new NewNightRun(
                ERSTER,
                NightRunMode.IMPLEMENTATION,
                1234L,
                2,
                1,
                3,
                "Fehler: kaputt",
                List.of(
                    new NewNightRunItem(
                        721,
                        "Persistenz",
                        NightRunState.RED,
                        NightRunErrorClass.CHECKS_RED,
                        900L,
                        "abc1234",
                        "mvn verify rot"))));
    assertThat(uebergeben.get(1))
        .isEqualTo(new NewNightRun(ZWEITER, NightRunMode.REVIEW, 10L, 0, 0, 0, null, List.of()));
  }

  @Test
  void submit_rejectsEmptyList_beforeReachingService() throws Exception {
    mvc.perform(post(PATH).contentType(JSON).content("{\"runs\":[]}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void submit_rejectsMoreRunsThanAllowed_beforeReachingService() throws Exception {
    String zuViele =
        "{\"runs\":[%s]}"
            .formatted(
                String.join(
                    ",",
                    IntStream.rangeClosed(0, NightRunController.MAX_RUNS_PER_REQUEST)
                        .mapToObj(i -> run("2026-08-%02dT22:00:00Z".formatted(1 + i % 28), ""))
                        .toList()));

    mvc.perform(post(PATH).contentType(JSON).content(zuViele)).andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void submit_rejectsMoreItemsThanAllowed_beforeReachingService() throws Exception {
    String zuVieleItems =
        String.join(
            ",",
            IntStream.rangeClosed(0, NightRunController.MAX_ITEMS_PER_RUN)
                .mapToObj(i -> item("Paket " + i, "kurz"))
                .toList());

    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content("{\"runs\":[%s]}".formatted(run("2026-08-31T22:00:00Z", zuVieleItems))))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  /**
   * Beide Auszugsfelder tragen dieselbe Grenze — der Item-Auszug nur, wenn das {@code @Valid} an
   * der inneren Liste steht.
   */
  @Test
  void submit_rejectsTooLongItemExcerpt_beforeReachingService() throws Exception {
    String body =
        "{\"runs\":[%s]}"
            .formatted(
                run(
                    "2026-08-31T22:00:00Z",
                    item("Paket", "x".repeat(NightRunLimits.EXCERPT_MAX + 1))));

    mvc.perform(post(PATH).contentType(JSON).content(body)).andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void submit_rejectsTooLongUnparsedSample_beforeReachingService() throws Exception {
    String body =
        """
        {"runs":[{"startedAt":"2026-08-31T22:00:00Z","mode":"IMPLEMENTATION","durationMs":1,
                  "processedCount":0,"skippedCount":0,"unparsedCount":1,
                  "unparsedSample":"%s","items":[]}]}
        """
            .formatted("u".repeat(NightRunLimits.EXCERPT_MAX + 1));

    mvc.perform(post(PATH).contentType(JSON).content(body)).andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void submit_rejectsTooLongTitleAndCommitHash_beforeReachingService() throws Exception {
    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content(
                    "{\"runs\":[%s]}"
                        .formatted(run("2026-08-31T22:00:00Z", item("t".repeat(301), "kurz")))))
        .andExpect(status().isBadRequest());

    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content(
                    """
                    {"runs":[{"startedAt":"2026-08-31T22:00:00Z","mode":"IMPLEMENTATION",
                              "durationMs":1,"processedCount":0,"skippedCount":0,"unparsedCount":0,
                              "items":[{"cardNumber":1,"title":"Paket","state":"GREEN",
                                        "commitHash":"%s"}]}]}
                    """
                        .formatted("c".repeat(41))))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void submit_rejectsMissingTitle_beforeReachingService() throws Exception {
    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content(
                    """
                    {"runs":[{"startedAt":"2026-08-31T22:00:00Z","mode":"IMPLEMENTATION",
                              "durationMs":1,"processedCount":0,"skippedCount":0,"unparsedCount":0,
                              "items":[{"cardNumber":1,"title":"  ","state":"GREEN"}]}]}
                    """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void submit_rejectsMissingStartedAtAndMode_beforeReachingService() throws Exception {
    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content(
                    """
                    {"runs":[{"durationMs":1,"processedCount":0,"skippedCount":0,
                              "unparsedCount":0,"items":[]}]}
                    """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void submit_rejectsNullRunInList_beforeReachingService() throws Exception {
    mvc.perform(post(PATH).contentType(JSON).content("{\"runs\":[null]}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void submit_propagatesForbidden_forMemberWithoutOwnerRole() throws Exception {
    when(service.submit(eq(USER), eq(PROJECT), anyList()))
        .thenThrow(new ProjectAccessDeniedException());

    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content("{\"runs\":[%s]}".formatted(run("2026-08-31T22:00:00Z", ""))))
        .andExpect(status().isForbidden());
  }

  @Test
  void list_returnsRunsWithItems() throws Exception {
    when(service.list(USER, PROJECT))
        .thenReturn(
            List.of(
                new NightRunView(
                    11L,
                    ERSTER,
                    NightRunMode.IMPLEMENTATION,
                    1234L,
                    2,
                    1,
                    3,
                    "Fehler: kaputt",
                    Instant.parse("2026-09-01T06:00:00Z"),
                    List.of(
                        new NightRunItemView(
                            21L,
                            721,
                            "Persistenz",
                            NightRunState.RED,
                            NightRunErrorClass.CHECKS_RED,
                            900L,
                            "abc1234",
                            "mvn verify rot")))));

    mvc.perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(11))
        .andExpect(jsonPath("$[0].startedAt").value("2026-08-31T22:00:00Z"))
        .andExpect(jsonPath("$[0].mode").value("IMPLEMENTATION"))
        .andExpect(jsonPath("$[0].durationMs").value(1234))
        .andExpect(jsonPath("$[0].processedCount").value(2))
        .andExpect(jsonPath("$[0].skippedCount").value(1))
        .andExpect(jsonPath("$[0].unparsedCount").value(3))
        .andExpect(jsonPath("$[0].unparsedSample").value("Fehler: kaputt"))
        .andExpect(jsonPath("$[0].createdAt").value("2026-09-01T06:00:00Z"))
        .andExpect(jsonPath("$[0].items[0].cardNumber").value(721))
        .andExpect(jsonPath("$[0].items[0].errorClass").value("CHECKS_RED"));

    verify(service).list(USER, PROJECT);
  }

  @Test
  void list_propagatesNotFound_forNonMember() throws Exception {
    when(service.list(USER, PROJECT)).thenThrow(new ProjectNotFoundException());

    mvc.perform(get(PATH)).andExpect(status().isNotFound());
  }

  @Test
  void errorClassCounts_returnsCountPerErrorClass() throws Exception {
    when(service.countRunsByErrorClass(USER, PROJECT))
        .thenReturn(
            Map.of(NightRunErrorClass.CHECKS_RED, 2L, NightRunErrorClass.AWAITING_DECISION, 1L));

    mvc.perform(get(PATH + "/error-class-counts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.CHECKS_RED").value(2))
        .andExpect(jsonPath("$.AWAITING_DECISION").value(1));

    verify(service).countRunsByErrorClass(USER, PROJECT);
  }

  @Test
  void errorClassCounts_propagatesNotFound_forNonMember() throws Exception {
    when(service.countRunsByErrorClass(USER, PROJECT)).thenThrow(new ProjectNotFoundException());

    mvc.perform(get(PATH + "/error-class-counts")).andExpect(status().isNotFound());
  }

  private static String run(String startedAt, String items) {
    return """
        {"startedAt":"%s","mode":"IMPLEMENTATION","durationMs":1,"processedCount":0,
         "skippedCount":0,"unparsedCount":0,"items":[%s]}"""
        .formatted(startedAt, items);
  }

  private static String item(String title, String excerpt) {
    return """
        {"cardNumber":1,"title":"%s","state":"GREEN","excerpt":"%s"}"""
        .formatted(title, excerpt);
  }
}
