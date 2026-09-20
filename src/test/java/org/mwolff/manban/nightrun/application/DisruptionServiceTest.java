package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.nightrun.application.DisruptionRepository.AckTarget;
import org.mwolff.manban.nightrun.application.DisruptionRepository.DisruptionCandidate;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOutcome;
import org.mwolff.manban.nightrun.domain.NightRunState;

/**
 * Die Use-Cases des Plattform-Leitstands (Issue #1080).
 *
 * <p>Was die Datenbank entscheidet — Gattung, Abschluss, Teilnahme, vorhandene Quittung — steht
 * nicht hier, sondern in {@code DisruptionRepositoryIT}: Das sind Zusagen über eine Abfrage, und
 * ein Mock, der sie nachbaut, bewiese nur, dass der Test die Abfrage kennt. Hier steht, was der
 * Dienst mit den Kandidaten macht: Rechte, Maßstab, Reihenfolge, Idempotenz.
 */
class DisruptionServiceTest {

  private static final Instant JETZT = Instant.parse("2026-09-20T08:00:00Z");
  private static final long ADMIN = 1L;
  private static final long NIEMAND = 2L;

  private DisruptionRepository disruptions;
  private NightRunRepository runs;
  private PlatformAdminChecker platformAdminChecker;
  private DisruptionService service;

  private static DisruptionCandidate kandidat(long laufId, Instant startedAt) {
    return new DisruptionCandidate(laufId, 9L, "Projekt", startedAt, null);
  }

  private static NightRunItem paket(
      long laufId, NightRunState state, @Nullable NightRunErrorClass errorClass) {
    return new NightRunItem(
        laufId * 100,
        laufId,
        9L,
        JETZT,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        721,
        "Paket",
        state,
        errorClass,
        1L,
        null,
        null,
        null);
  }

  @BeforeEach
  void setUp() {
    disruptions = mock(DisruptionRepository.class);
    runs = mock(NightRunRepository.class);
    platformAdminChecker = mock(PlatformAdminChecker.class);
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    service =
        new DisruptionService(
            disruptions, runs, platformAdminChecker, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  // --- Rechte (AK 3, AK 7) -------------------------------------------------------------------

  @Test
  void dieListeVerlangtDenPlattformAdmin_undLiestOhneIhnNichts() {
    assertThatThrownBy(() -> service.disruptions(NIEMAND))
        .isInstanceOf(AdminAccessDeniedException.class);
    verifyNoInteractions(disruptions);
  }

  @Test
  void dasQuittierenVerlangtDenPlattformAdmin_undSchreibtOhneIhnNichts() {
    assertThatThrownBy(() -> service.acknowledge(NIEMAND, 5L))
        .isInstanceOf(AdminAccessDeniedException.class);
    verifyNoInteractions(disruptions);
  }

  /** AK 7: Der Plattform-Admin liest die Störungen ohne jede Projekt-Mitgliedschaft. */
  @Test
  void derPlattformAdminLiestOhneProjektMitgliedschaft() {
    when(disruptions.openCandidates()).thenReturn(List.of(kandidat(5L, JETZT)));
    when(runs.findItemsByRunIds(List.of(5L)))
        .thenReturn(List.of(paket(5L, NightRunState.RED, NightRunErrorClass.CHECKS_RED)));

    assertThat(service.disruptions(ADMIN)).hasSize(1);
  }

  // --- Der Maßstab entscheidet ---------------------------------------------------------------

  @Test
  void einGelungenerLaufIstKandidat_aberKeineStoerung() {
    when(disruptions.openCandidates())
        .thenReturn(List.of(kandidat(5L, JETZT), kandidat(6L, JETZT.minusSeconds(60))));
    when(runs.findItemsByRunIds(List.of(5L, 6L)))
        .thenReturn(
            List.of(
                paket(5L, NightRunState.GREEN, null),
                paket(6L, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(service.disruptions(ADMIN))
        .extracting(DisruptionService.DisruptionView::nightRunId)
        .containsExactly(6L);
  }

  @Test
  void einZurueckgestelltesPaketIstEineStoerung() {
    when(disruptions.openCandidates()).thenReturn(List.of(kandidat(5L, JETZT)));
    when(runs.findItemsByRunIds(List.of(5L)))
        .thenReturn(List.of(paket(5L, NightRunState.GREY, NightRunErrorClass.DEPENDENCY_UNMET)));

    assertThat(service.disruptions(ADMIN))
        .singleElement()
        .extracting(v -> v.outcome().verdict())
        .isEqualTo(NightRunOutcome.Verdict.WAITING);
  }

  /** Ein Lauf ohne Arbeit hat kein Paket — der Grund ist der Text, und er reicht. */
  @Test
  void einLaufOhneArbeitIstEineStoerungAuchOhnePaket() {
    when(disruptions.openCandidates())
        .thenReturn(List.of(new DisruptionCandidate(5L, 9L, "Projekt", JETZT, "Ready war leer")));
    when(runs.findItemsByRunIds(List.of(5L))).thenReturn(List.of());

    assertThat(service.disruptions(ADMIN))
        .singleElement()
        .extracting(v -> v.outcome().noWorkReason())
        .isEqualTo("Ready war leer");
  }

  @Test
  void ohneKandidatenWirdNichtNachPaketenGefragt() {
    when(disruptions.openCandidates()).thenReturn(List.of());

    assertThat(service.disruptions(ADMIN)).isEmpty();
    verifyNoInteractions(runs);
  }

  /** AK 13: Die Reihenfolge der Abfrage bleibt erhalten — jüngste zuoberst. */
  @Test
  void dieReihenfolgeDerAbfrageBleibtErhalten() {
    when(disruptions.openCandidates())
        .thenReturn(
            List.of(
                kandidat(7L, JETZT),
                kandidat(6L, JETZT.minusSeconds(60)),
                kandidat(5L, JETZT.minusSeconds(120))));
    when(runs.findItemsByRunIds(List.of(7L, 6L, 5L)))
        .thenReturn(
            List.of(
                paket(7L, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                paket(6L, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                paket(5L, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(service.disruptions(ADMIN))
        .extracting(DisruptionService.DisruptionView::nightRunId)
        .containsExactly(7L, 6L, 5L);
  }

  @Test
  void dieStoerzeileTraegtProjektUndZeitpunkt() {
    when(disruptions.openCandidates())
        .thenReturn(List.of(new DisruptionCandidate(5L, 9L, "Mein Projekt", JETZT, null)));
    when(runs.findItemsByRunIds(List.of(5L)))
        .thenReturn(List.of(paket(5L, NightRunState.RED, NightRunErrorClass.CHECKS_RED)));

    assertThat(service.disruptions(ADMIN))
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.projectId()).isEqualTo(9L);
              assertThat(v.projectName()).isEqualTo("Mein Projekt");
              assertThat(v.startedAt()).isEqualTo(JETZT);
            });
  }

  // --- Quittieren (AK 8, 10) -----------------------------------------------------------------

  @Test
  void dasQuittierenSchreibtDieQuittungMitNutzerUndUhr() {
    when(disruptions.ackTarget(5L)).thenReturn(Optional.of(new AckTarget(5L, 9L)));

    service.acknowledge(ADMIN, 5L);

    verify(disruptions).acknowledge(5L, ADMIN, JETZT);
  }

  /** AK 10: Der Lauf selbst wird nicht angefasst — die Quittung liegt daneben. */
  @Test
  void dasQuittierenLaesstDenLaufUnberuehrt() {
    when(disruptions.ackTarget(5L)).thenReturn(Optional.of(new AckTarget(5L, 9L)));

    service.acknowledge(ADMIN, 5L);

    verifyNoInteractions(runs);
  }

  /** AK 8: Zwei Admins räumen dieselbe Zeile weg — der zweite darf nichts Rotes sehen. */
  @Test
  void dasZweiteQuittierenIstKeinFehler() {
    when(disruptions.ackTarget(5L)).thenReturn(Optional.of(new AckTarget(5L, 9L)));

    service.acknowledge(ADMIN, 5L);

    assertThatCode(() -> service.acknowledge(ADMIN, 5L)).doesNotThrowAnyException();
  }

  @Test
  void einUnbekannterOderVerdraengterLaufIst404_undSchreibtNichts() {
    when(disruptions.ackTarget(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.acknowledge(ADMIN, 5L))
        .isInstanceOf(DisruptionNotFoundException.class);
    verify(disruptions, never()).acknowledge(5L, ADMIN, JETZT);
  }
}
