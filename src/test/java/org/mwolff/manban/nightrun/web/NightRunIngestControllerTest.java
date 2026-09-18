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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunResult;
import org.mwolff.manban.nightrun.application.TokenNotBoundForIngestException;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Die Pfade des Ingest-Endpunkts, die der Integrationstest nicht erreicht (Issue #947).
 *
 * <p>Ein Aufruf ohne Anmeldung kommt dort nie an — die Filterkette weist ihn vorher ab. Der
 * Controller muss ihn trotzdem abfangen: Er verlässt sich nicht darauf, dass vor ihm jemand geprüft
 * hat.
 */
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
        START, mode, kind, 1000L, 1, 0, 0, Boolean.TRUE, usage, List.of());
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
            new NightRunUsageRequest(new BigDecimal("8.03"), 148L, 62_411L, 8_883_160L));

    assertThat(uebernommen).isNotNull();
    assertThat(uebernommen.costUsd()).isEqualByComparingTo("8.03");
    assertThat(uebernommen.cachedInputTokens()).isEqualTo(8_883_160L);
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

  private static Authentication mitPrincipal(KanbanPrincipal principal) {
    TestingAuthenticationToken token = new TestingAuthenticationToken("tok", "n", List.of());
    token.setDetails(principal);
    return token;
  }
}
