package org.mwolff.manban.nightrun.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.nightrun.application.NightRunNotFoundException;
import org.mwolff.manban.nightrun.application.NightRunProgressService;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.application.NightRunService.CardTitleView;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRun;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRunItem;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunItemView;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunResult;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunView;
import org.mwolff.manban.nightrun.application.NightRunService.ReleasePreparationView;
import org.mwolff.manban.nightrun.domain.CardRef;
import org.mwolff.manban.nightrun.domain.ChainProgress;
import org.mwolff.manban.nightrun.domain.NachtFreigabe;
import org.mwolff.manban.nightrun.domain.NachtFreigabe.Startstation;
import org.mwolff.manban.nightrun.domain.NightRunBudget;
import org.mwolff.manban.nightrun.domain.NightRunBudgetOrigin;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItemStage;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunOutcome;
import org.mwolff.manban.nightrun.domain.NightRunProgress;
import org.mwolff.manban.nightrun.domain.NightRunStage;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.nightrun.domain.PackageProgress;
import org.mwolff.manban.nightrun.domain.PackageState;
import org.mwolff.manban.nightrun.domain.ProgressAssignment;
import org.mwolff.manban.nightrun.domain.ProgressStage;
import org.mwolff.manban.nightrun.domain.ReleasePreparationResult;
import org.mwolff.manban.nightrun.domain.StageProgress;
import org.mwolff.manban.nightrun.domain.StageState;
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
// Testklasse: Die Importe folgen den geprueften Typen. Issue #944 bringt NightRunOrigin
// dazu und reisst damit die Schwelle von 40. Jede Methode ist ein Fall eines Endpunkts; Issue #1375
// bringt die Faelle des Fortschritts dazu und reisst damit die Methoden-Schwelle.
@SuppressWarnings({"PMD.ExcessiveImports", "PMD.TooManyMethods"})
class NightRunControllerTest {

  private static final long USER = 7L;
  private static final long PROJECT = 3L;
  private static final String PATH = "/api/projects/" + PROJECT + "/night-runs";
  private static final String JSON = "application/json";
  private static final Instant ERSTER = Instant.parse("2026-08-31T22:00:00Z");
  private static final Instant ZWEITER = Instant.parse("2026-09-01T22:00:00Z");

  private NightRunService service;
  private NightRunProgressService progress;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(NightRunService.class);
    progress = mock(NightRunProgressService.class);
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
        MockMvcBuilders.standaloneSetup(new NightRunController(service, progress))
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
                true,
                null,
                null,
                null,
                null,
                null,
                List.of(
                    new NewNightRunItem(
                        721,
                        "Persistenz",
                        NightRunState.RED,
                        NightRunErrorClass.CHECKS_RED,
                        900L,
                        "abc1234",
                        "mvn verify rot",
                        null,
                        List.of()))));
    assertThat(uebergeben.get(1))
        .isEqualTo(
            new NewNightRun(
                ZWEITER,
                NightRunMode.REVIEW,
                10L,
                0,
                0,
                0,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                List.of()));
  }

  /**
   * Der Ketten-Lauf und der Abbruch am Zeitbudget kommen durch die Bindung (Issue #853): Beides
   * sind Enum-Werte am Request-Body, und ein fehlender Enum-Wert wäre hier ein 400 statt einer
   * Weitergabe an den Service.
   */
  @Test
  // Siehe submit_passesEveryFieldToService_andAnswersInRequestOrder: derselbe Grund.
  @SuppressWarnings("unchecked")
  void submit_passesChainRunWithTimeBudgetExceeded_toService() throws Exception {
    when(service.submit(eq(USER), eq(PROJECT), anyList()))
        .thenReturn(List.of(new NightRunResult(ERSTER, true)));

    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content(
                    """
                    {"runs":[{"startedAt":"2026-08-31T22:00:00Z","mode":"CHAIN","durationMs":1,
                              "processedCount":1,"skippedCount":0,"unparsedCount":0,
                              "items":[{"cardNumber":853,"title":"Kette","state":"RED",
                                        "errorClass":"TIME_BUDGET_EXCEEDED"}]}]}
                    """))
        .andExpect(status().isOk());

    ArgumentCaptor<List<NewNightRun>> captor = ArgumentCaptor.forClass(List.class);
    verify(service).submit(eq(USER), eq(PROJECT), captor.capture());
    assertThat(captor.getValue())
        .singleElement()
        .isEqualTo(
            new NewNightRun(
                ERSTER,
                NightRunMode.CHAIN,
                1L,
                1,
                0,
                0,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                List.of(
                    new NewNightRunItem(
                        853,
                        "Kette",
                        NightRunState.RED,
                        NightRunErrorClass.TIME_BUDGET_EXCEEDED,
                        null,
                        null,
                        null,
                        null,
                        List.of()))));
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
                    NightRunOrigin.UPLOAD,
                    null,
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    NightRunOutcome.of(
                        true,
                        null,
                        null,
                        null,
                        NightRunMode.IMPLEMENTATION,
                        List.of(),
                        ERSTER,
                        null,
                        ERSTER,
                        Duration.ofMinutes(90)),
                    List.of(
                        new NightRunItemView(
                            21L,
                            721,
                            "Persistenz",
                            NightRunState.RED,
                            NightRunErrorClass.CHECKS_RED,
                            900L,
                            "abc1234",
                            "mvn verify rot",
                            null,
                            List.of())))));

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
        .andExpect(jsonPath("$[0].items[0].errorClass").value("CHECKS_RED"))
        .andExpect(jsonPath("$[0].releasePreparation").value(nullValue()));

    verify(service).list(USER, PROJECT);
  }

  /**
   * Die Morgenmeldung in der Laufliste (Issue #1457): alle Felder samt Eingang, die Karten mit
   * Nummer und Titel; eine Nummer ohne Karte trägt title null.
   */
  @Test
  void list_returnsReleasePreparationWithCardTitles() throws Exception {
    when(service.list(USER, PROJECT))
        .thenReturn(
            List.of(
                new NightRunView(
                    14L,
                    ERSTER,
                    NightRunMode.CHAIN,
                    1L,
                    1,
                    0,
                    0,
                    null,
                    Instant.parse("2026-09-01T06:00:00Z"),
                    NightRunOrigin.TOKEN,
                    "nacht",
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new ReleasePreparationView(
                        ReleasePreparationResult.RED,
                        "c3bf41a7",
                        "1.4.0",
                        "mvn verify",
                        List.of("Mutationsprüfung Frontend"),
                        Instant.parse("2026-09-01T05:12:00Z"),
                        List.of(new CardTitleView(1449, "Paket A"), new CardTitleView(4711, null)),
                        List.of(new CardTitleView(1450, "Paket aus fremder Kette"))),
                    NightRunOutcome.of(
                        true,
                        null,
                        null,
                        null,
                        NightRunMode.CHAIN,
                        List.of(),
                        ERSTER,
                        null,
                        ERSTER,
                        Duration.ofMinutes(90)),
                    List.of())));

    mvc.perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].releasePreparation.result").value("RED"))
        .andExpect(jsonPath("$[0].releasePreparation.commitHash").value("c3bf41a7"))
        .andExpect(jsonPath("$[0].releasePreparation.version").value("1.4.0"))
        .andExpect(jsonPath("$[0].releasePreparation.redCheck").value("mvn verify"))
        .andExpect(
            jsonPath("$[0].releasePreparation.pending[0]").value("Mutationsprüfung Frontend"))
        .andExpect(jsonPath("$[0].releasePreparation.receivedAt").value("2026-09-01T05:12:00Z"))
        .andExpect(jsonPath("$[0].releasePreparation.cards[0].number").value(1449))
        .andExpect(jsonPath("$[0].releasePreparation.cards[0].title").value("Paket A"))
        .andExpect(jsonPath("$[0].releasePreparation.cards[1].number").value(4711))
        .andExpect(jsonPath("$[0].releasePreparation.cards[1].title").value(nullValue()))
        .andExpect(jsonPath("$[0].releasePreparation.redCards[0].number").value(1450))
        .andExpect(
            jsonPath("$[0].releasePreparation.redCards[0].title").value("Paket aus fremder Kette"));
  }

  /** Die Morgenmeldung trägt je Karte nur Nummer und Titel, nie Inhalte (Issue #1457). */
  @Test
  void releasePreparationView_carriesNoCardContentFields() {
    assertThat(felder(ReleasePreparationView.class))
        .containsExactly(
            "result",
            "commitHash",
            "version",
            "redCheck",
            "pending",
            "receivedAt",
            "cards",
            "redCards");
    assertThat(felder(CardTitleView.class)).containsExactly("number", "title");
  }

  /**
   * Die Ausgabeseite der beiden neuen Werte (Issue #853) — als Enum-Name, nicht als Ordinalzahl.
   */
  @Test
  void list_returnsChainRunWithTimeBudgetExceeded() throws Exception {
    when(service.list(USER, PROJECT))
        .thenReturn(
            List.of(
                new NightRunView(
                    12L,
                    ERSTER,
                    NightRunMode.CHAIN,
                    1L,
                    1,
                    0,
                    0,
                    null,
                    Instant.parse("2026-09-01T06:00:00Z"),
                    NightRunOrigin.UPLOAD,
                    null,
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    NightRunOutcome.of(
                        true,
                        null,
                        null,
                        null,
                        NightRunMode.IMPLEMENTATION,
                        List.of(),
                        ERSTER,
                        null,
                        ERSTER,
                        Duration.ofMinutes(90)),
                    List.of(
                        new NightRunItemView(
                            22L,
                            853,
                            "Kette",
                            NightRunState.RED,
                            NightRunErrorClass.TIME_BUDGET_EXCEEDED,
                            null,
                            null,
                            null,
                            null,
                            List.of())))));

    mvc.perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].mode").value("CHAIN"))
        .andExpect(jsonPath("$[0].items[0].errorClass").value("TIME_BUDGET_EXCEEDED"));
  }

  @Test
  void list_propagatesNotFound_forNonMember() throws Exception {
    when(service.list(USER, PROJECT)).thenThrow(new ProjectNotFoundException());

    mvc.perform(get(PATH)).andExpect(status().isNotFound());
  }

  @Test
  void tonight_liefertJeKarteNummerTitelBoardStartZielUndPruefer() throws Exception {
    when(service.heuteNacht(USER, PROJECT))
        .thenReturn(
            List.of(
                new NachtFreigabe(
                    12,
                    "[Fachlich] Import",
                    "Entwicklung",
                    Startstation.FACHPLAN,
                    ProgressStage.PAKETE,
                    2),
                new NachtFreigabe(
                    30,
                    "[Plan] Export",
                    "Betrieb",
                    Startstation.PLAN,
                    ProgressStage.UMSETZUNG,
                    null)));

    mvc.perform(get(PATH + "/tonight"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].number").value(12))
        .andExpect(jsonPath("$[0].title").value("[Fachlich] Import"))
        .andExpect(jsonPath("$[0].boardName").value("Entwicklung"))
        .andExpect(jsonPath("$[0].start").value("FACHPLAN"))
        .andExpect(jsonPath("$[0].ziel").value("PAKETE"))
        .andExpect(jsonPath("$[0].pruefer").value(2))
        .andExpect(jsonPath("$[1].start").value("PLAN"))
        .andExpect(jsonPath("$[1].ziel").value("UMSETZUNG"))
        .andExpect(jsonPath("$[1].pruefer").doesNotExist());
  }

  @Test
  void tonight_propagatesForbidden_forMemberWithoutOwnerRole() throws Exception {
    when(service.heuteNacht(USER, PROJECT)).thenThrow(new ProjectAccessDeniedException());

    mvc.perform(get(PATH + "/tonight")).andExpect(status().isForbidden());
  }

  @Test
  void tonight_propagatesNotFound_forNonMember() throws Exception {
    when(service.heuteNacht(USER, PROJECT)).thenThrow(new ProjectNotFoundException());

    mvc.perform(get(PATH + "/tonight")).andExpect(status().isNotFound());
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

  /**
   * Der gemeldete Kostenwert kommt als {@link NightRunUsage} beim Use-Case an — je Lauf und je
   * Arbeitspaket (Issue #948). Bis hierher übergab der Controller an beiden Stellen {@code null}.
   */
  @Test
  // Siehe submit_passesEveryFieldToService_andAnswersInRequestOrder: derselbe Grund.
  @SuppressWarnings("unchecked")
  void submit_passesReportedCost_toService() throws Exception {
    when(service.submit(eq(USER), eq(PROJECT), anyList()))
        .thenReturn(List.of(new NightRunResult(ERSTER, true)));

    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content(
                    """
                    {"runs":[
                      {"startedAt":"2026-08-31T22:00:00Z","mode":"CHAIN","durationMs":1,
                       "processedCount":1,"skippedCount":0,"unparsedCount":0,
                       "usage":{"costUsd":25.983293},
                       "items":[{"cardNumber":791,"title":"Paket","state":"GREEN",
                                 "excerpt":"Auszug","usage":{"costUsd":11.5228115}}]}]}
                    """))
        .andExpect(status().isOk());

    ArgumentCaptor<List<NewNightRun>> captor = ArgumentCaptor.forClass(List.class);
    verify(service).submit(eq(USER), eq(PROJECT), captor.capture());
    NewNightRun uebergeben = captor.getValue().getFirst();
    assertThat(uebergeben.usage())
        .isEqualTo(new NightRunUsage(new BigDecimal("25.983293"), null, null, null, null, null));
    assertThat(uebergeben.items().getFirst().usage())
        .isEqualTo(new NightRunUsage(new BigDecimal("11.5228115"), null, null, null, null, null));
  }

  /** Ohne {@code usage} bleibt es bei „nicht gemessen" — kein Record aus lauter Nullen. */
  @Test
  // Siehe submit_passesEveryFieldToService_andAnswersInRequestOrder: derselbe Grund.
  @SuppressWarnings("unchecked")
  void submit_withoutUsage_passesNull_toService() throws Exception {
    when(service.submit(eq(USER), eq(PROJECT), anyList()))
        .thenReturn(List.of(new NightRunResult(ERSTER, true)));

    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content(
                    "{\"runs\":[%s]}"
                        .formatted(run("2026-08-31T22:00:00Z", item("Paket", "Auszug")))))
        .andExpect(status().isOk());

    ArgumentCaptor<List<NewNightRun>> captor = ArgumentCaptor.forClass(List.class);
    verify(service).submit(eq(USER), eq(PROJECT), captor.capture());
    NewNightRun uebergeben = captor.getValue().getFirst();
    assertThat(uebergeben.usage()).isNull();
    assertThat(uebergeben.items().getFirst().usage()).isNull();
  }

  /**
   * E14: Der Browser-Upload führt weder Budgets noch Stufen — er setzt beides fest auf „nicht
   * gemeldet", wie er es bei {@code complete} und {@code noWorkReason} schon tut. Ein Rumpf, der
   * sie trotzdem trüge, ändert daran nichts: Die Request-Records kennen die Felder nicht, und
   * Jackson verwirft unbekannte Felder.
   */
  @Test
  // Siehe submit_passesEveryFieldToService_andAnswersInRequestOrder: derselbe Grund.
  @SuppressWarnings("unchecked")
  void submit_passesNoBudgetAndNoStages_toService() throws Exception {
    when(service.submit(eq(USER), eq(PROJECT), anyList()))
        .thenReturn(List.of(new NightRunResult(ERSTER, true)));

    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content(
                    """
                    {"runs":[
                      {"startedAt":"2026-08-31T22:00:00Z","mode":"CHAIN","durationMs":1,
                       "processedCount":1,"skippedCount":0,"unparsedCount":0,
                       "budget":{"planMin":30},
                       "items":[{"cardNumber":993,"title":"Paket","state":"GREEN",
                                 "stages":[{"stage":"PLAN","durationMs":600000}]}]}]}
                    """))
        .andExpect(status().isOk());

    ArgumentCaptor<List<NewNightRun>> captor = ArgumentCaptor.forClass(List.class);
    verify(service).submit(eq(USER), eq(PROJECT), captor.capture());
    NewNightRun uebergeben = captor.getValue().getFirst();
    assertThat(uebergeben.budget()).isNull();
    assertThat(uebergeben.items().getFirst().stages()).isEmpty();
  }

  /**
   * Modellzeit und Züge nimmt der Upload-Weg über den geteilten {@link NightRunUsageRequest}
   * <b>formal</b> an (E14) — der Browser sendet sie nicht, aber wer sie sendet, bekommt sie
   * durchgereicht statt verworfen.
   */
  @Test
  // Siehe submit_passesEveryFieldToService_andAnswersInRequestOrder: derselbe Grund.
  @SuppressWarnings("unchecked")
  void submit_passesModelDurationAndTurns_toService() throws Exception {
    when(service.submit(eq(USER), eq(PROJECT), anyList()))
        .thenReturn(List.of(new NightRunResult(ERSTER, true)));

    mvc.perform(
            post(PATH)
                .contentType(JSON)
                .content(
                    """
                    {"runs":[
                      {"startedAt":"2026-08-31T22:00:00Z","mode":"CHAIN","durationMs":1,
                       "processedCount":1,"skippedCount":0,"unparsedCount":0,
                       "usage":{"modelDurationMs":3600000,"turns":214},
                       "items":[{"cardNumber":993,"title":"Paket","state":"GREEN",
                                 "usage":{"modelDurationMs":900000,"turns":42}}]}]}
                    """))
        .andExpect(status().isOk());

    ArgumentCaptor<List<NewNightRun>> captor = ArgumentCaptor.forClass(List.class);
    verify(service).submit(eq(USER), eq(PROJECT), captor.capture());
    NewNightRun uebergeben = captor.getValue().getFirst();
    assertThat(uebergeben.usage())
        .isEqualTo(new NightRunUsage(null, null, null, null, 3_600_000L, 214));
    assertThat(uebergeben.items().getFirst().usage())
        .isEqualTo(new NightRunUsage(null, null, null, null, 900_000L, 42));
  }

  /** Die Ausgabeseite: Budget und Stufen stehen in der Antwort der Laufliste (AK 1, AK 2). */
  @Test
  void list_returnsBudgetAndStages() throws Exception {
    when(service.list(USER, PROJECT))
        .thenReturn(
            List.of(
                new NightRunView(
                    13L,
                    ERSTER,
                    NightRunMode.CHAIN,
                    1L,
                    1,
                    0,
                    0,
                    null,
                    Instant.parse("2026-09-01T06:00:00Z"),
                    NightRunOrigin.TOKEN,
                    "nacht",
                    true,
                    null,
                    null,
                    null,
                    new NightRunBudget(
                        30,
                        30,
                        25,
                        10,
                        new BigDecimal("50"),
                        NightRunBudgetOrigin.DEFAULTED,
                        List.of("paketeMin", "kostenUsd")),
                    null,
                    null,
                    NightRunOutcome.of(
                        true,
                        null,
                        null,
                        null,
                        NightRunMode.CHAIN,
                        List.of(),
                        ERSTER,
                        null,
                        ERSTER,
                        Duration.ofMinutes(90)),
                    List.of(
                        new NightRunItemView(
                            23L,
                            993,
                            "Paket",
                            NightRunState.GREEN,
                            null,
                            5L,
                            null,
                            null,
                            new NightRunUsage(null, null, null, null, 900_000L, 42),
                            List.of(
                                new NightRunItemStage(
                                    NightRunStage.PLAN,
                                    600_000L,
                                    new NightRunUsage(
                                        new BigDecimal("0.25"), null, null, null, null, 7))))))));

    mvc.perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].budget.planMin").value(30))
        .andExpect(jsonPath("$[0].budget.abdeckungMin").value(10))
        .andExpect(jsonPath("$[0].budget.kostenUsd").value(50))
        .andExpect(jsonPath("$[0].budget.origin").value("DEFAULTED"))
        .andExpect(jsonPath("$[0].budget.defaultFields[0]").value("paketeMin"))
        .andExpect(jsonPath("$[0].items[0].usage.modelDurationMs").value(900_000))
        .andExpect(jsonPath("$[0].items[0].usage.turns").value(42))
        .andExpect(jsonPath("$[0].items[0].stages[0].stage").value("PLAN"))
        .andExpect(jsonPath("$[0].items[0].stages[0].durationMs").value(600_000))
        .andExpect(jsonPath("$[0].items[0].stages[0].usage.turns").value(7));
  }

  // --- Fortschritt (Issue #1375) ----------------------------------------------------------

  /** Die View spiegelt den Fortschritt Feld für Feld, Enums als Namen. */
  @Test
  void progress_returnsTheView() throws Exception {
    CardRef anforderung = new CardRef(1364, "[Fachlich] Fortschritt", 5L);
    CardRef plan = new CardRef(1372, "[Plan] Fortschritt", 5L);
    PackageProgress paket =
        new PackageProgress(new CardRef(1375, "Paket 3/7", 5L), PackageState.IN_UMSETZUNG);
    when(progress.progress(USER, PROJECT, 11L))
        .thenReturn(
            new NightRunProgress(
                ProgressAssignment.OK,
                List.of(
                    new ChainProgress(
                        anforderung,
                        plan,
                        List.of(paket),
                        List.of(new StageProgress(ProgressStage.PLAN, StageState.ERREICHT)),
                        ProgressStage.ABDECKUNG,
                        false)),
                List.of(paket),
                List.of(new CardRef(1400, "Fremd", 6L)),
                List.of(anforderung),
                true));

    mvc.perform(get(PATH + "/11/progress"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zuordnung").value("OK"))
        .andExpect(jsonPath("$.ketten[0].anforderung.number").value(1364))
        .andExpect(jsonPath("$.ketten[0].anforderung.title").value("[Fachlich] Fortschritt"))
        .andExpect(jsonPath("$.ketten[0].anforderung.boardId").value(5))
        .andExpect(jsonPath("$.ketten[0].plan.number").value(1372))
        .andExpect(jsonPath("$.ketten[0].pakete[0].karte.number").value(1375))
        .andExpect(jsonPath("$.ketten[0].pakete[0].zustand").value("IN_UMSETZUNG"))
        .andExpect(jsonPath("$.ketten[0].stufen[0].stufe").value("PLAN"))
        .andExpect(jsonPath("$.ketten[0].stufen[0].zustand").value("ERREICHT"))
        .andExpect(jsonPath("$.ketten[0].aktuelleStufe").value("ABDECKUNG"))
        .andExpect(jsonPath("$.ketten[0].endeErreicht").value(false))
        .andExpect(jsonPath("$.pakete[0].karte.title").value("Paket 3/7"))
        .andExpect(jsonPath("$.unbekannt[0].number").value(1400))
        .andExpect(jsonPath("$.unbekannt[0].boardId").value(6))
        .andExpect(jsonPath("$.offeneFragen[0].number").value(1364))
        .andExpect(jsonPath("$.unbekanntOhneAusweis").value(true));
  }

  /** Eine Kette ohne bekannten Plan trägt {@code plan: null}, und das Ende ist erreicht. */
  @Test
  void progress_mapsChainWithoutPlan() throws Exception {
    when(progress.progress(USER, PROJECT, 11L))
        .thenReturn(
            new NightRunProgress(
                ProgressAssignment.UNBEKANNT,
                List.of(
                    new ChainProgress(
                        new CardRef(1, "[Fachlich] A", 5L),
                        null,
                        List.of(),
                        List.of(),
                        null,
                        true)),
                List.of(),
                List.of(),
                List.of(),
                false));

    mvc.perform(get(PATH + "/11/progress"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zuordnung").value("UNBEKANNT"))
        .andExpect(jsonPath("$.unbekanntOhneAusweis").value(false))
        .andExpect(jsonPath("$.ketten[0].plan").doesNotExist())
        .andExpect(jsonPath("$.ketten[0].aktuelleStufe").doesNotExist())
        .andExpect(jsonPath("$.ketten[0].endeErreicht").value(true));
  }

  @Test
  void progress_mapsUnknownRunToNotFound() throws Exception {
    when(progress.progress(USER, PROJECT, 11L)).thenThrow(new NightRunNotFoundException());

    mvc.perform(get(PATH + "/11/progress")).andExpect(status().isNotFound());
  }

  @Test
  void progress_propagatesNotFound_forNonMember() throws Exception {
    when(progress.progress(USER, PROJECT, 11L)).thenThrow(new ProjectNotFoundException());

    mvc.perform(get(PATH + "/11/progress")).andExpect(status().isNotFound());
  }

  @Test
  void progress_propagatesForbidden_forMemberWithoutOwnerRole() throws Exception {
    when(progress.progress(USER, PROJECT, 11L)).thenThrow(new ProjectAccessDeniedException());

    mvc.perform(get(PATH + "/11/progress")).andExpect(status().isForbidden());
  }

  /**
   * E10, Nicht-Ziel „Inhalte nicht wiedergeben": Die View trägt keine Karteninhalte. Geprüft an den
   * Feldnamen jeder Ebene, nicht an einer Beispielantwort — ein neues Feld fiele hier auf.
   */
  @Test
  void progressView_carriesNoCardContentFields() {
    assertThat(felder(NightRunProgressView.class))
        .containsExactly(
            "zuordnung", "ketten", "pakete", "unbekannt", "offeneFragen", "unbekanntOhneAusweis");
    assertThat(felder(NightRunProgressView.ChainProgressView.class))
        .containsExactly(
            "anforderung", "plan", "pakete", "stufen", "aktuelleStufe", "endeErreicht");
    assertThat(felder(NightRunProgressView.PackageProgressView.class))
        .containsExactly("karte", "zustand");
    assertThat(felder(NightRunProgressView.StageProgressView.class))
        .containsExactly("stufe", "zustand");
    assertThat(felder(NightRunProgressView.CardRefView.class))
        .containsExactly("number", "title", "boardId");
  }

  private static List<String> felder(Class<? extends Record> typ) {
    return Arrays.stream(typ.getRecordComponents()).map(RecordComponent::getName).toList();
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
