package org.mwolff.manban.codeanalyse.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.codeanalyse.application.MessungService;
import org.mwolff.manban.codeanalyse.application.MessungStore;
import org.mwolff.manban.codeanalyse.domain.Messung;
import org.mwolff.manban.codeanalyse.domain.Zustand;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.web.FehlerbehandlungFixture;
import org.mwolff.manban.config.SpracheLocaleConfig;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Der Meldeweg der Codeanalyse über HTTP (Issue #2277, Plan #2272 Vertrag V1, E3, E4, E5, E8): Er
 * läuft gegen den echten Dienst mit einem Speicher im Arbeitsspeicher, damit Wiederholung und
 * berichtigte Meldung wirklich am gespeicherten Stand geprüft werden. Dass der Speicher selbst
 * hält, was er zusagt, belegt {@code JdbcMessungStoreIT}.
 */
// PMD.TooManyMethods: Je Abweisung und Annahme ein Test, damit ein Fehlschlag seine Regel aus V1
// und E8 nennt; zusammengelegt verlöre der Test diese Auskunft.
@SuppressWarnings("PMD.TooManyMethods")
class MessungIngestControllerTest {

  private static final String PFAD = "/api/kanban/code-analysis-measurements";
  private static final Instant JETZT = Instant.parse("2026-11-26T12:00:00Z");
  private static final long PROJEKT = 42L;
  private static final long INHABER = 1L;

  private static final String ZUSTAND_FEHLT_DE =
      "Eine zählende Messung braucht beide Zustände, vor der Codeanalyse und den Endzustand.";
  private static final String ZUSTAND_FEHLT_EN =
      "A counting measurement needs both states, before the code analysis and the final state.";
  private static final String GRUND_FEHLT_DE = "Eine nicht zählende Messung braucht einen Grund.";
  private static final String GRUND_FEHLT_EN = "A measurement that does not count needs a reason.";

  private static final String VORHER = zustand(20, 1, 4, 2000);
  private static final String ENDE = zustand(4, 0, 1, 2200);

  private final SpeicherImArbeitsspeicher store = new SpeicherImArbeitsspeicher();
  private final PermissionChecker permissions = mock(PermissionChecker.class);
  private final MessungService service =
      new MessungService(store, permissions, Clock.fixed(JETZT, ZoneOffset.UTC));
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    eingestellt(Sprache.DE);
  }

  @AfterEach
  void tearDown() {
    SprachFixture.abmelden();
  }

  private void eingestellt(Sprache sprache) {
    var texte = SprachFixture.mit(sprache);
    mvc =
        MockMvcBuilders.standaloneSetup(new MessungIngestController(service))
            .setControllerAdvice(FehlerbehandlungFixture.handler(texte))
            .setLocaleResolver(new SpracheLocaleConfig().localeResolver(texte))
            .build();
  }

  // --- Bausteine der Meldung ---------------------------------------------------------------

  private static String zustand(int bugs, int vulnerabilities, int codeSmells, int changedLines) {
    return """
        {"bugs": %d, "vulnerabilities": %d, "codeSmells": %d, "changedLines": %d}"""
        .formatted(bugs, vulnerabilities, codeSmells, changedLines);
  }

  private static String meldung(
      String publishedCommit,
      String publishedAt,
      String baseCommit,
      String kitVersion,
      String counts,
      String reason,
      String cards,
      String before,
      String fin) {
    return """
        {"publishedAt": %s, "publishedCommit": %s, "baseCommit": %s, "kitVersion": %s,
         "counts": %s, "reason": %s, "cards": %s, "before": %s, "final": %s}"""
        .formatted(
            publishedAt,
            publishedCommit,
            baseCommit,
            kitVersion,
            counts,
            reason,
            cards,
            before,
            fin);
  }

  private static String zaehlend(String before, String fin) {
    return meldung(
        "\"abc123\"",
        "\"2026-11-26T11:00:00Z\"",
        "\"base000\"",
        "\"2.4.0\"",
        "true",
        "null",
        "3",
        before,
        fin);
  }

  private static String zaehlend() {
    return zaehlend(VORHER, ENDE);
  }

  private static String nichtZaehlend(String reason) {
    return meldung(
        "\"abc123\"",
        "\"2026-11-26T11:00:00Z\"",
        "\"base000\"",
        "\"2.4.0\"",
        "false",
        reason,
        "2",
        "null",
        "null");
  }

  private static String text(String laenge) {
    return "\"" + laenge + "\"";
  }

  private static Authentication mitPrincipal(KanbanPrincipal principal) {
    TestingAuthenticationToken auth = new TestingAuthenticationToken("token", null, List.of());
    auth.setDetails(principal);
    return auth;
  }

  private static Authentication gebunden() {
    return mitPrincipal(new KanbanPrincipal(INHABER, 2L, PROJEKT, 7L, "kit"));
  }

  private ResultActions melde(String rumpf) throws Exception {
    return melde(rumpf, gebunden(), null);
  }

  private ResultActions melde(
      String rumpf, @Nullable Authentication auth, @Nullable String idempotencyKey)
      throws Exception {
    var anfrage = post(PFAD).contentType(MediaType.APPLICATION_JSON).content(rumpf);
    if (auth != null) {
      anfrage.principal(auth);
    }
    if (idempotencyKey != null) {
      anfrage.header("Idempotency-Key", idempotencyKey);
    }
    return mvc.perform(anfrage);
  }

  private ResultActions abgewiesen(String rumpf) throws Exception {
    return melde(rumpf)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  // --- Angenommene Meldungen ---------------------------------------------------------------

  @Test
  void eineGueltigeZaehlendeMeldungEndetMit200UndPublishedCommitUndCounts() throws Exception {
    melde(zaehlend())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publishedCommit").value("abc123"))
        .andExpect(jsonPath("$.counts").value(true));

    assertThat(store.gespeichert)
        .containsExactly(
            new Gespeichert(
                PROJEKT,
                "kit",
                new Messung(
                    "abc123",
                    Instant.parse("2026-11-26T11:00:00Z"),
                    "base000",
                    "2.4.0",
                    true,
                    null,
                    3,
                    new Zustand(20, 1, 4, 2000),
                    new Zustand(4, 0, 1, 2200))));
    verify(permissions).require(INHABER, PROJEKT, Permission.TICKET_CREATE);
  }

  @Test
  void eineGueltigeNichtZaehlendeMeldungEndetMit200UndPublishedCommitUndCounts() throws Exception {
    melde(nichtZaehlend("\"cards-removed\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publishedCommit").value("abc123"))
        .andExpect(jsonPath("$.counts").value(false));

    assertThat(store.gespeichert)
        .singleElement()
        .satisfies(
            g -> {
              assertThat(g.messung().zaehlt()).isFalse();
              assertThat(g.messung().grund()).isEqualTo("cards-removed");
              assertThat(g.messung().vorher()).isNull();
              assertThat(g.messung().endzustand()).isNull();
            });
  }

  @Test
  void eineNichtZaehlendeMeldungDarfZustaendeTragen() throws Exception {
    melde(
            meldung(
                "\"abc123\"",
                "\"2026-11-26T11:00:00Z\"",
                "\"base000\"",
                "\"2.4.0\"",
                "false",
                "\"missing-final\"",
                "2",
                VORHER,
                "null"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counts").value(false));

    assertThat(store.gespeichert.getFirst().messung().vorher())
        .isEqualTo(new Zustand(20, 1, 4, 2000));
  }

  @Test
  void dieWiederholungLiefertDieselbeAntwortUndAendertNichts() throws Exception {
    melde(zaehlend()).andExpect(status().isOk());

    melde(nichtZaehlend("\"incomplete\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publishedCommit").value("abc123"))
        .andExpect(jsonPath("$.counts").value(true));

    assertThat(store.gespeichert).hasSize(1);
    assertThat(store.gespeichert.getFirst().messung().zaehlt()).isTrue();
  }

  @Test
  void einUnbekannterErlaubterGrundWirdAngenommen() throws Exception {
    melde(nichtZaehlend("\"neuer-grund-2\"")).andExpect(status().isOk());

    assertThat(store.gespeichert.getFirst().messung().grund()).isEqualTo("neuer-grund-2");
  }

  @Test
  void einGrundBeiZaehlenderMeldungWirdNichtGespeichert() throws Exception {
    melde(
            meldung(
                "\"abc123\"",
                "\"2026-11-26T11:00:00Z\"",
                "\"base000\"",
                "\"2.4.0\"",
                "true",
                "\"incomplete\"",
                "3",
                VORHER,
                ENDE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counts").value(true));

    assertThat(store.gespeichert.getFirst().messung().grund()).isNull();
  }

  @Test
  void einGleicherKopfIdempotencyKeyWirdAngenommen() throws Exception {
    melde(zaehlend(), gebunden(), "abc123").andExpect(status().isOk());

    assertThat(store.gespeichert).hasSize(1);
  }

  @Test
  void dieGrenzenSelbstWerdenAngenommen() throws Exception {
    melde(
            meldung(
                text("a".repeat(64)),
                "\"2026-11-26T12:05:00Z\"",
                text("b".repeat(64)),
                text("v".repeat(50)),
                "false",
                text("x".repeat(100)),
                "0",
                zustand(0, 0, 0, 0),
                zustand(0, 0, 0, 0)))
        .andExpect(status().isOk());
  }

  // --- Abweisungen der Eingabe (E8, AK 16) -------------------------------------------------

  @Test
  void zaehlendOhneBeforeWirdMitFeldBeforeAbgewiesen() throws Exception {
    abgewiesen(zaehlend("null", ENDE))
        .andExpect(jsonPath("$.fieldErrors.before").value(ZUSTAND_FEHLT_DE))
        .andExpect(jsonPath("$.fieldErrors.final").doesNotExist());

    eingestellt(Sprache.EN);
    abgewiesen(zaehlend("null", ENDE))
        .andExpect(jsonPath("$.fieldErrors.before").value(ZUSTAND_FEHLT_EN));

    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void zaehlendOhneFinalWirdMitFeldFinalAbgewiesen() throws Exception {
    abgewiesen(zaehlend(VORHER, "null"))
        .andExpect(jsonPath("$.fieldErrors.final").value(ZUSTAND_FEHLT_DE))
        .andExpect(jsonPath("$.fieldErrors.before").doesNotExist());

    eingestellt(Sprache.EN);
    abgewiesen(zaehlend(VORHER, "null"))
        .andExpect(jsonPath("$.fieldErrors.final").value(ZUSTAND_FEHLT_EN));

    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void zaehlendOhneBeideZustaendeNenntBeide() throws Exception {
    abgewiesen(zaehlend("null", "null"))
        .andExpect(jsonPath("$.fieldErrors.before").value(ZUSTAND_FEHLT_DE))
        .andExpect(jsonPath("$.fieldErrors.final").value(ZUSTAND_FEHLT_DE));
  }

  @Test
  void nichtZaehlendOhneReasonWirdMitFeldReasonAbgewiesen() throws Exception {
    abgewiesen(nichtZaehlend("null"))
        .andExpect(jsonPath("$.fieldErrors.reason").value(GRUND_FEHLT_DE));

    eingestellt(Sprache.EN);
    abgewiesen(nichtZaehlend("null"))
        .andExpect(jsonPath("$.fieldErrors.reason").value(GRUND_FEHLT_EN));

    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void nichtZaehlendMitLeeremReasonWirdAbgewiesen() throws Exception {
    abgewiesen(nichtZaehlend("\"\"")).andExpect(jsonPath("$.fieldErrors.reason").exists());
  }

  @Test
  void ohneCountsWirdMitFeldCountsAbgewiesen() throws Exception {
    abgewiesen(
            meldung(
                "\"abc123\"",
                "\"2026-11-26T11:00:00Z\"",
                "\"base000\"",
                "\"2.4.0\"",
                "null",
                "null",
                "3",
                VORHER,
                ENDE))
        .andExpect(jsonPath("$.fieldErrors.counts").exists())
        .andExpect(jsonPath("$.fieldErrors.before").doesNotExist())
        .andExpect(jsonPath("$.fieldErrors.reason").doesNotExist());
  }

  @Test
  void jedesPflichtfeldFehltBenannt() throws Exception {
    abgewiesen("{}")
        .andExpect(jsonPath("$.fieldErrors.publishedAt").exists())
        .andExpect(jsonPath("$.fieldErrors.publishedCommit").exists())
        .andExpect(jsonPath("$.fieldErrors.baseCommit").exists())
        .andExpect(jsonPath("$.fieldErrors.kitVersion").exists())
        .andExpect(jsonPath("$.fieldErrors.counts").exists())
        .andExpect(jsonPath("$.fieldErrors.cards").exists());
  }

  @Test
  void negativeZahlenWerdenAbgewiesen() throws Exception {
    abgewiesen(
            meldung(
                "\"abc123\"",
                "\"2026-11-26T11:00:00Z\"",
                "\"base000\"",
                "\"2.4.0\"",
                "true",
                "null",
                "-1",
                zustand(-1, -1, -1, -1),
                zustand(-1, -1, -1, -1)))
        .andExpect(jsonPath("$.fieldErrors.cards").exists())
        .andExpect(jsonPath("$.fieldErrors['before.bugs']").exists())
        .andExpect(jsonPath("$.fieldErrors['before.vulnerabilities']").exists())
        .andExpect(jsonPath("$.fieldErrors['before.codeSmells']").exists())
        .andExpect(jsonPath("$.fieldErrors['before.changedLines']").exists())
        .andExpect(jsonPath("$.fieldErrors['final.bugs']").exists())
        .andExpect(jsonPath("$.fieldErrors['final.vulnerabilities']").exists())
        .andExpect(jsonPath("$.fieldErrors['final.codeSmells']").exists())
        .andExpect(jsonPath("$.fieldErrors['final.changedLines']").exists());

    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void einZustandMitFehlenderZahlWirdAbgewiesen() throws Exception {
    abgewiesen(zaehlend("{\"bugs\": 1}", ENDE))
        .andExpect(jsonPath("$.fieldErrors['before.vulnerabilities']").exists())
        .andExpect(jsonPath("$.fieldErrors['before.codeSmells']").exists())
        .andExpect(jsonPath("$.fieldErrors['before.changedLines']").exists());
  }

  @Test
  void ueberlangeKennungenUndFassungWerdenAbgewiesen() throws Exception {
    abgewiesen(
            meldung(
                text("a".repeat(65)),
                "\"2026-11-26T11:00:00Z\"",
                text("b".repeat(65)),
                text("v".repeat(51)),
                "true",
                "null",
                "3",
                VORHER,
                ENDE))
        .andExpect(jsonPath("$.fieldErrors.publishedCommit").exists())
        .andExpect(jsonPath("$.fieldErrors.baseCommit").exists())
        .andExpect(jsonPath("$.fieldErrors.kitVersion").exists());

    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void einReasonMitUnerlaubtenZeichenWirdAbgewiesen() throws Exception {
    abgewiesen(nichtZaehlend("\"Cards_Removed\""))
        .andExpect(jsonPath("$.fieldErrors.reason").exists());
    abgewiesen(nichtZaehlend("\"karten entfernt\""))
        .andExpect(jsonPath("$.fieldErrors.reason").exists());
    abgewiesen(nichtZaehlend(text("x".repeat(101))))
        .andExpect(jsonPath("$.fieldErrors.reason").exists());

    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void einAbweichenderKopfIdempotencyKeyWirdAufDeutschUndEnglischAbgewiesen() throws Exception {
    melde(zaehlend(), gebunden(), "anderer")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Der Kopf Idempotency-Key nennt einen anderen Stand als publishedCommit im"
                        + " Rumpf."));

    eingestellt(Sprache.EN);
    melde(zaehlend(), gebunden(), "anderer")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "The Idempotency-Key header names a different state than publishedCommit in"
                        + " the body."));

    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void publishedAtInDerZukunftWirdAufDeutschUndEnglischAbgewiesen() throws Exception {
    String zukunft =
        meldung(
            "\"abc123\"",
            "\"2026-11-26T12:05:01Z\"",
            "\"base000\"",
            "\"2.4.0\"",
            "true",
            "null",
            "3",
            VORHER,
            ENDE);

    abgewiesen(zukunft)
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Der Zeitpunkt der Veröffentlichung liegt mehr als 5 Minuten in der"
                        + " Zukunft."));

    eingestellt(Sprache.EN);
    abgewiesen(zukunft)
        .andExpect(
            jsonPath("$.detail")
                .value("The publication time is more than 5 minutes in the future."));

    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void nachEinerAbgewiesenenMeldungWirdDieselbeVeroeffentlichungBerichtigtAngenommen()
      throws Exception {
    abgewiesen(zaehlend("null", ENDE));
    abgewiesen(nichtZaehlend("null"));

    melde(zaehlend())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publishedCommit").value("abc123"))
        .andExpect(jsonPath("$.counts").value(true));

    assertThat(store.gespeichert).hasSize(1);
  }

  // --- Zugang und Rechte (E5) --------------------------------------------------------------

  @Test
  void einZugangOhneBindungEndetMit409UndNichtsWirdGespeichert() throws Exception {
    Authentication ungebunden = mitPrincipal(new KanbanPrincipal(INHABER, 2L, null, null, "frei"));

    melde(zaehlend(), ungebunden, null)
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Dieser Zugang gehört zu keinem Board. Bitte einen Zugang verwenden, der an"
                        + " ein Board gebunden ist."));

    assertThat(store.gespeichert).isEmpty();
    verifyNoInteractions(permissions);
  }

  @Test
  void ohneKanbanAnmeldungEndetMit409() throws Exception {
    melde(zaehlend(), null, null).andExpect(status().isConflict());
    melde(zaehlend(), new TestingAuthenticationToken("wer", "auch", List.of()), null)
        .andExpect(status().isConflict());

    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void ohneRechtEndetMit403UndNichtsWirdGespeichert() throws Exception {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(INHABER, PROJEKT, Permission.TICKET_CREATE);

    melde(zaehlend()).andExpect(status().isForbidden());

    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void ohneProjektEndetMit404() throws Exception {
    doThrow(new ProjectNotFoundException())
        .when(permissions)
        .require(INHABER, PROJEKT, Permission.TICKET_CREATE);

    melde(zaehlend()).andExpect(status().isNotFound());

    assertThat(store.gespeichert).isEmpty();
  }

  // --- Doppelgänger des Ports ---------------------------------------------------------------

  private record Gespeichert(long projectId, String tokenName, Messung messung) {}

  /** Hält, was {@link MessungStore#speichereErste} zusagt: Die erste Meldung je Stand gilt. */
  private static final class SpeicherImArbeitsspeicher implements MessungStore {

    private final List<Gespeichert> gespeichert = new ArrayList<>();

    @Override
    public Messung speichereErste(
        long projectId, String tokenName, Messung messung, Instant eingang) {
      for (Gespeichert g : gespeichert) {
        if (g.projectId() == projectId
            && g.messung().publishedCommit().equals(messung.publishedCommit())) {
          return g.messung();
        }
      }
      gespeichert.add(new Gespeichert(projectId, tokenName, messung));
      return messung;
    }

    @Override
    public List<Messung> ladeAb(long projectId, Instant ab) {
      throw new UnsupportedOperationException("nicht Teil des Meldewegs");
    }

    @Override
    public Optional<Instant> erstesPublishedAt(long projectId) {
      throw new UnsupportedOperationException("nicht Teil des Meldewegs");
    }

    @Override
    public boolean hatMessungen(long projectId) {
      throw new UnsupportedOperationException("nicht Teil des Meldewegs");
    }
  }
}
