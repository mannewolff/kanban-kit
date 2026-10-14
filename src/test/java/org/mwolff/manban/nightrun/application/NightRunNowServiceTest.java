package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mwolff.manban.nightrun.domain.ReadyBewertung;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;

/** Der Meldeweg der Bewertung der Ready-Karten (Issue #1726). */
class NightRunNowServiceTest {

  private static final long USER = 7L;
  private static final long PROJECT = 42L;
  private static final Instant T1 = Instant.parse("2026-10-13T22:40:00Z");

  private final NightReadyAssessmentRepository bewertungen =
      mock(NightReadyAssessmentRepository.class);
  private final NightRunRepository runs = mock(NightRunRepository.class);
  private final PermissionChecker permissions = mock(PermissionChecker.class);
  private final NightRunNowService service = new NightRunNowService(bewertungen, runs, permissions);

  private static ReadyBewertung bewertung(Instant am) {
    return new ReadyBewertung(
        am, null, "kit:nightrun", List.of(new ReadyBewertung.Eintrag(1695, 1, List.of())));
  }

  /** Die Signatur nimmt Primitive (ArchitectureTest: nightrun kennt accesstoken nicht). */
  @Test
  void meldeBewertung_nimmtPrimitiveUndKeinenPrincipal() throws NoSuchMethodException {
    assertThat(
            NightRunNowService.class
                .getMethod("meldeBewertung", long.class, long.class, ReadyBewertung.class)
                .getParameterTypes())
        .containsExactly(long.class, long.class, ReadyBewertung.class);
  }

  @Test
  void meldeBewertung_verlangtDenBesitzer_undSchreibtSonstNichts() {
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireOwner(USER, PROJECT);

    assertThatThrownBy(() -> service.meldeBewertung(USER, PROJECT, bewertung(T1)))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verifyNoInteractions(bewertungen, runs);
  }

  @Test
  void meldeBewertung_sperrtDasProjektVorDemLesenUndErsetzt() {
    when(bewertungen.findByProjectId(PROJECT)).thenReturn(Optional.empty());

    service.meldeBewertung(USER, PROJECT, bewertung(T1));

    InOrder reihenfolge = inOrder(permissions, runs, bewertungen);
    reihenfolge.verify(permissions).requireOwner(USER, PROJECT);
    reihenfolge.verify(runs).lockProject(PROJECT);
    reihenfolge.verify(bewertungen).findByProjectId(PROJECT);
    reihenfolge.verify(bewertungen).ersetze(PROJECT, bewertung(T1));
  }

  @Test
  void meldeBewertung_ersetztEineGleichAlte_alsWiederholung() {
    when(bewertungen.findByProjectId(PROJECT)).thenReturn(Optional.of(bewertung(T1)));

    service.meldeBewertung(USER, PROJECT, bewertung(T1));

    verify(bewertungen).ersetze(PROJECT, bewertung(T1));
  }

  @Test
  void meldeBewertung_weistEineAeltereAb_undLaesstDieGespeicherteStehen() {
    when(bewertungen.findByProjectId(PROJECT))
        .thenReturn(Optional.of(bewertung(T1.plusSeconds(60))));

    assertThatThrownBy(() -> service.meldeBewertung(USER, PROJECT, bewertung(T1)))
        .isInstanceOf(NightReadyAssessmentOutdatedException.class);

    verify(bewertungen, never()).ersetze(anyLong(), ArgumentMatchers.any());
  }
}
