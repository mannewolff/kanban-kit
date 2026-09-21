package org.mwolff.manban.nightrun.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mwolff.manban.nightrun.web.NightRunController.NO_WORK_REASON_MAX;
import static org.mwolff.manban.nightrun.web.NightRunIngestController.DEFAULT_FIELD_NAME_MAX;
import static org.mwolff.manban.nightrun.web.NightRunIngestController.MAX_DEFAULT_FIELDS;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.application.NightRunService.NewNightRun;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunResult;
import org.mwolff.manban.nightrun.application.TokenNotBoundForIngestException;
import org.mwolff.manban.nightrun.domain.NightRunBudget;
import org.mwolff.manban.nightrun.domain.NightRunBudgetOrigin;
import org.mwolff.manban.nightrun.domain.NightRunItemStage;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunStage;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.nightrun.web.NightRunIngestController.IngestBudgetRequest;
import org.mwolff.manban.nightrun.web.NightRunIngestController.IngestItemRequest;
import org.mwolff.manban.nightrun.web.NightRunIngestController.IngestStageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Die Pfade des Ingest-Endpunkts, die der Integrationstest nicht erreicht (Issue #947).
 *
 * <p>Ein Aufruf ohne Anmeldung kommt dort nie an — die Filterkette weist ihn vorher ab. Der
 * Controller muss ihn trotzdem abfangen: Er verlässt sich nicht darauf, dass vor ihm jemand geprüft
 * hat.
 */
// Testklasse: Die Importe folgen den geprueften Typen. Issue #1113 bringt NightRunBudget,
// NightRunBudgetOrigin, NightRunItemStage, NightRunStage und die drei neuen Request-Records dazu
// und reisst damit die Schwelle von 40.
@SuppressWarnings("PMD.ExcessiveImports")
class NightRunIngestControllerTest {

  private static final Instant START = Instant.parse("2026-09-16T22:31:00Z");

  private final NightRunService service = mock(NightRunService.class);
  private final NightRunIngestController controller = new NightRunIngestController(service);

  private static NightRunIngestController.IngestRequest anfrage(NightRunUsageRequest usage) {
    return anfrage(usage, null, NightRunMode.CHAIN);
  }

  private static NightRunIngestController.IngestRequest anfrage(
      @Nullable NightRunUsageRequest usage, @Nullable NightRunKind kind, NightRunMode mode) {
    return new NightRunIngestController.IngestRequest(
        START, mode, kind, 1000L, 1, 0, 0, Boolean.TRUE, usage, null, null, List.of());
  }

  @Test
  void ohneAnmeldungWirdAbgewiesen_undDerDienstBleibtUnberuehrt() {
    assertThatThrownBy(() -> controller.ingest(null, anfrage(null)))
        .isInstanceOf(TokenNotBoundForIngestException.class);

    verifyNoInteractions(service);
  }

  @Test
  void eineAnmeldungOhneKanbanTokenWirdAbgewiesen() {
    Authentication fremd = new TestingAuthenticationToken("wer", "auch", List.of());

    assertThatThrownBy(() -> controller.ingest(fremd, anfrage(null)))
        .isInstanceOf(TokenNotBoundForIngestException.class);

    verifyNoInteractions(service);
  }

  @Test
  void einTokenOhneProjektbindungWirdAbgewiesen() {
    Authentication ungebunden = mitPrincipal(new KanbanPrincipal(1L, 2L, null, null, "frei"));

    assertThatThrownBy(() -> controller.ingest(ungebunden, anfrage(null)))
        .isInstanceOf(TokenNotBoundForIngestException.class);

    verifyNoInteractions(service);
  }

  /** Ohne gemeldeten Verbrauch steht am Lauf „nicht gemessen" — und nicht ein Record aus Nullen. */
  @Test
  void ohneVerbrauchsangabeWirdNichtsGemeldet() {
    assertThat(NightRunUsageRequest.toDomain(null)).isNull();
  }

  @Test
  void mitVerbrauchsangabeWerdenDieWerteUebernommen() {
    NightRunUsage uebernommen =
        NightRunUsageRequest.toDomain(
            new NightRunUsageRequest(
                new BigDecimal("8.03"), 148L, 62_411L, 8_883_160L, 3_600_000L, 214));

    assertThat(uebernommen).isNotNull();
    assertThat(uebernommen.costUsd()).isEqualByComparingTo("8.03");
    assertThat(uebernommen.cachedInputTokens()).isEqualTo(8_883_160L);
    assertThat(uebernommen.modelDurationMs()).isEqualTo(3_600_000L);
    assertThat(uebernommen.turns()).isEqualTo(214);
  }

  /**
   * Die Gegenprobe zur Erweiterung (Issue #1113): Eine aeltere Kit-Kopie meldet Modellzeit und
   * Zuege nicht. Ihr Verbrauch kommt an, und die beiden neuen Felder stehen auf „nicht gemessen" —
   * nicht auf 0.
   */
  @Test
  void ohneModellzeitUndZuegeBleibenBeideLeer() {
    NightRunUsage uebernommen =
        NightRunUsageRequest.toDomain(
            new NightRunUsageRequest(new BigDecimal("8.03"), 148L, null, null, null, null));

    assertThat(uebernommen).isNotNull();
    assertThat(uebernommen.modelDurationMs()).isNull();
    assertThat(uebernommen.turns()).isNull();
  }

  @Test
  void einAngelegterLaufMeldetCreated_einErsetzterReplaced() {
    Authentication gebunden = mitPrincipal(new KanbanPrincipal(1L, 2L, 42L, 7L, "nacht"));
    when(service.ingest(anyLong(), anyLong(), anyString(), any(), any()))
        .thenReturn(new NightRunResult(START, true), new NightRunResult(START, false));

    assertThat(controller.ingest(gebunden, anfrage(null)).outcome())
        .isEqualTo(NightRunIngestController.Outcome.CREATED);
    assertThat(controller.ingest(gebunden, anfrage(null)).outcome())
        .isEqualTo(NightRunIngestController.Outcome.REPLACED);
  }

  /**
   * Die Erweiterung ist additiv (Issue #1012): Eine aeltere Kit-Kopie kennt das Feld nicht und
   * meldet unveraendert weiter — ihre Meldung ist ein Nachtlauf.
   */
  @Test
  void ohneGattungMeldetDerControllerEinenNachtlauf() {
    Authentication gebunden = mitPrincipal(new KanbanPrincipal(1L, 2L, 42L, 7L, "nacht"));
    when(service.ingest(anyLong(), anyLong(), anyString(), any(), any()))
        .thenReturn(new NightRunResult(START, true));

    controller.ingest(gebunden, anfrage(null, null, NightRunMode.CHAIN));

    verify(service).ingest(eq(1L), eq(42L), eq("nacht"), eq(NightRunKind.NIGHT), any());
  }

  /** Mit gesetztem Feld reicht der Controller die gemeldete Gattung unveraendert durch. */
  @Test
  void mitGattungReichtDerControllerSieDurch() {
    Authentication gebunden = mitPrincipal(new KanbanPrincipal(1L, 2L, 42L, 7L, "sitzung"));
    when(service.ingest(anyLong(), anyLong(), anyString(), any(), any()))
        .thenReturn(new NightRunResult(START, true));

    controller.ingest(gebunden, anfrage(null, NightRunKind.INTERACTIVE, NightRunMode.INTERACTIVE));

    verify(service).ingest(eq(1L), eq(42L), eq("sitzung"), eq(NightRunKind.INTERACTIVE), any());
  }

  /** Eine Meldung mit gemeldetem Grund ohne abgearbeitete Pakete. */
  private static NightRunIngestController.IngestRequest anfrageMitGrund(@Nullable String grund) {
    return new NightRunIngestController.IngestRequest(
        START,
        NightRunMode.CHAIN,
        NightRunKind.NIGHT,
        1000L,
        0,
        0,
        0,
        Boolean.TRUE,
        null,
        grund,
        null,
        List.of());
  }

  /**
   * Additiv wie die Gattung (E3, Vorbild Issue #1012): Eine aeltere Kit-Kopie kennt {@code
   * noWorkReason} nicht. Ihre Meldung muss die Pruefung bestehen statt an ihr zu scheitern — den
   * Text setzt dann der Server.
   */
  @Test
  void eineMeldungOhneGrundFeldBestehtDiePruefung() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(anfrageMitGrund(null))).isEmpty();
    }
  }

  /** Die Grenze wird beidseitig belegt — sonst bestuende auch eine Zusicherung ohne Obergrenze. */
  @Test
  void einGrundBisZurGrenzeBestehtUndEinerDarueberWirdAbgewiesen() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      assertThat(validator.validate(anfrageMitGrund("x".repeat(NO_WORK_REASON_MAX)))).isEmpty();
      assertThat(validator.validate(anfrageMitGrund("x".repeat(NO_WORK_REASON_MAX + 1))))
          .hasSize(1);
    }
  }

  @Test
  void derGemeldeteGrundWirdAnDenDienstDurchgereicht() {
    Authentication gebunden = mitPrincipal(new KanbanPrincipal(1L, 2L, 42L, 7L, "nacht"));
    when(service.ingest(anyLong(), anyLong(), anyString(), any(), any()))
        .thenReturn(new NightRunResult(START, true));

    controller.ingest(gebunden, anfrageMitGrund("Kein Eintrag trug das Label kit:nightrun"));

    ArgumentCaptor<NewNightRun> meldung = ArgumentCaptor.forClass(NewNightRun.class);
    verify(service).ingest(anyLong(), anyLong(), anyString(), any(), meldung.capture());
    assertThat(meldung.getValue().noWorkReason())
        .isEqualTo("Kein Eintrag trug das Label kit:nightrun");
  }

  private static Authentication mitPrincipal(KanbanPrincipal principal) {
    TestingAuthenticationToken token = new TestingAuthenticationToken("tok", "n", List.of());
    token.setDetails(principal);
    return token;
  }

  // --- Budgets, Herkunft und Stufen am Einlieferungsvertrag (Issue #1113) --------------------

  /** Eine Meldung mit Vorgaben und Stufen — der Kern dieses Pakets. */
  private static NightRunIngestController.IngestRequest anfrageMitVorgaben(
      @Nullable IngestBudgetRequest budget, @Nullable List<IngestStageRequest> stufen) {
    return new NightRunIngestController.IngestRequest(
        START,
        NightRunMode.CHAIN,
        NightRunKind.NIGHT,
        1000L,
        1,
        0,
        0,
        Boolean.TRUE,
        null,
        null,
        budget,
        List.of(
            new IngestItemRequest(
                993, "Paket 993", NightRunState.GREEN, null, 5L, null, null, null, stufen)));
  }

  private static IngestStageRequest stufe(NightRunStage stage) {
    return new IngestStageRequest(
        stage, 600_000L, new NightRunUsageRequest(new BigDecimal("0.25"), 10L, 20L, 5L, 400L, 7));
  }

  @Test
  void budgetUndStufenKommenAmDienstAn() {
    Authentication gebunden = mitPrincipal(new KanbanPrincipal(1L, 2L, 42L, 7L, "nacht"));
    when(service.ingest(anyLong(), anyLong(), anyString(), any(), any()))
        .thenReturn(new NightRunResult(START, true));

    controller.ingest(
        gebunden,
        anfrageMitVorgaben(
            new IngestBudgetRequest(
                30,
                30,
                25,
                10,
                new BigDecimal("50"),
                NightRunBudgetOrigin.DEFAULTED,
                List.of("paketeMin", "kostenUsd")),
            List.of(stufe(NightRunStage.PLAN), stufe(NightRunStage.REVIEW))));

    ArgumentCaptor<NewNightRun> meldung = ArgumentCaptor.forClass(NewNightRun.class);
    verify(service).ingest(anyLong(), anyLong(), anyString(), any(), meldung.capture());
    NightRunBudget budget = meldung.getValue().budget();
    assertThat(budget).isNotNull();
    assertThat(budget.planMin()).isEqualTo(30);
    assertThat(budget.reviewMin()).isEqualTo(30);
    assertThat(budget.paketeMin()).isEqualTo(25);
    assertThat(budget.abdeckungMin()).isEqualTo(10);
    assertThat(budget.kostenUsd()).isEqualByComparingTo("50");
    assertThat(budget.origin()).isEqualTo(NightRunBudgetOrigin.DEFAULTED);
    assertThat(budget.defaultFields()).containsExactly("paketeMin", "kostenUsd");
    assertThat(meldung.getValue().items().getFirst().stages())
        .extracting(NightRunItemStage::stage)
        .containsExactly(NightRunStage.PLAN, NightRunStage.REVIEW);
    assertThat(meldung.getValue().items().getFirst().stages().getFirst().durationMs())
        .isEqualTo(600_000L);
    assertThat(meldung.getValue().items().getFirst().stages().getFirst().usage().turns())
        .isEqualTo(7);
  }

  /**
   * Die Gegenprobe zur Additivitaet (E11): Eine aeltere Kit-Kopie kennt weder {@code budget} noch
   * {@code stages}. Ihre Meldung kommt durch, und der Lauf traegt „nicht angegeben" statt eines
   * Budgets aus lauter Nullen und eine leere Stufenliste statt {@code null}.
   */
  @Test
  void ohneBudgetUndStufenKommtNichtsAn_undNichtsWirdErfunden() {
    Authentication gebunden = mitPrincipal(new KanbanPrincipal(1L, 2L, 42L, 7L, "nacht"));
    when(service.ingest(anyLong(), anyLong(), anyString(), any(), any()))
        .thenReturn(new NightRunResult(START, true));

    controller.ingest(gebunden, anfrageMitVorgaben(null, null));

    ArgumentCaptor<NewNightRun> meldung = ArgumentCaptor.forClass(NewNightRun.class);
    verify(service).ingest(anyLong(), anyLong(), anyString(), any(), meldung.capture());
    assertThat(meldung.getValue().budget()).isNull();
    assertThat(meldung.getValue().items().getFirst().stages()).isEmpty();
  }

  /**
   * Ein Budget ohne Feldliste ist kein Budget ohne Aussage: {@code defaultFields} steht leer statt
   * {@code null} — „kein Feld kam aus den Voreinstellungen" ist eine Aussage.
   */
  @Test
  void einBudgetOhneFeldlisteTraegtDieLeereListe() {
    Authentication gebunden = mitPrincipal(new KanbanPrincipal(1L, 2L, 42L, 7L, "nacht"));
    when(service.ingest(anyLong(), anyLong(), anyString(), any(), any()))
        .thenReturn(new NightRunResult(START, true));

    controller.ingest(
        gebunden,
        anfrageMitVorgaben(
            new IngestBudgetRequest(30, 30, 25, 10, null, NightRunBudgetOrigin.CONFIGURED, null),
            List.of()));

    ArgumentCaptor<NewNightRun> meldung = ArgumentCaptor.forClass(NewNightRun.class);
    verify(service).ingest(anyLong(), anyLong(), anyString(), any(), meldung.capture());
    assertThat(meldung.getValue().budget().defaultFields()).isEmpty();
  }

  /**
   * E11 ausdruecklich: {@code defaultFields} wird <b>nicht</b> gegen eine feste Aufzaehlung
   * geprueft. Ein Name, den das Board nicht kennt, kommt an und wird erst bei der Anzeige
   * ausgelassen — eine strenge Aufzaehlung kostete den ganzen Lauf statt einer Zeile.
   */
  @Test
  void einUnbekannterFeldnameBestehtDiePruefungUndKommtAn() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(
              factory
                  .getValidator()
                  .validate(
                      anfrageMitVorgaben(
                          new IngestBudgetRequest(
                              30,
                              30,
                              25,
                              10,
                              null,
                              NightRunBudgetOrigin.DEFAULTED,
                              List.of("korrekturrunden")),
                          List.of())))
          .isEmpty();
    }
  }

  /** Die Grenze der Stufen wird beidseitig belegt: vier bestehen (E17), fuenf werden abgewiesen. */
  @Test
  void vierStufenBestehenUndFuenfWerdenAbgewiesen() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      assertThat(
              validator.validate(
                  anfrageMitVorgaben(
                      null,
                      List.of(
                          stufe(NightRunStage.PLAN),
                          stufe(NightRunStage.REVIEW),
                          stufe(NightRunStage.PAKETE),
                          stufe(NightRunStage.ABDECKUNG)))))
          .isEmpty();
      assertThat(
              validator.validate(
                  anfrageMitVorgaben(
                      null,
                      List.of(
                          stufe(NightRunStage.PLAN),
                          stufe(NightRunStage.REVIEW),
                          stufe(NightRunStage.PAKETE),
                          stufe(NightRunStage.ABDECKUNG),
                          stufe(NightRunStage.PLAN)))))
          .hasSize(1);
    }
  }

  /**
   * Die Feldliste geht als kommagetrennte Zeichenkette in {@code budget_default_fields
   * varchar(200)}. Ohne Grenze riss eine ueberlange Meldung dort in einen Serverfehler statt in
   * eine benannte Ablehnung; die Grenzen sind so bemessen, dass die verbundene Form immer
   * hineinpasst.
   */
  @Test
  void eineUeberlangeFeldlisteWirdAbgewiesen() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      assertThat(
              validator.validate(
                  anfrageMitVorgaben(budgetMitFeldern(namen(MAX_DEFAULT_FIELDS)), List.of())))
          .isEmpty();
      assertThat(
              validator.validate(
                  anfrageMitVorgaben(budgetMitFeldern(namen(MAX_DEFAULT_FIELDS + 1)), List.of())))
          .hasSize(1);
      assertThat(
              validator.validate(
                  anfrageMitVorgaben(
                      budgetMitFeldern(List.of("x".repeat(DEFAULT_FIELD_NAME_MAX + 1))),
                      List.of())))
          .hasSize(1);
    }
  }

  private static List<String> namen(int anzahl) {
    return IntStream.range(0, anzahl).mapToObj(i -> "feld" + i).toList();
  }

  private static IngestBudgetRequest budgetMitFeldern(List<String> felder) {
    return new IngestBudgetRequest(30, 30, 25, 10, null, NightRunBudgetOrigin.DEFAULTED, felder);
  }
}
