package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.domain.Project;

/**
 * Verhaltenstest der rechteprüfungsfreien Ports für die Strenge-Stufe des Kits (Issue #2014). Liegt
 * im selben Paket wie die package-private Implementierung — von außen sind nur die Ports {@link
 * KitStrictnessWriter} und {@link KitStrictnessReader} injizierbar.
 */
class ProjectKitStrictnessServiceTest {

  private static final Instant ANGELEGT = Instant.parse("2026-10-01T10:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-11-10T12:00:00Z");
  private static final Clock UHR = Clock.fixed(JETZT, ZoneOffset.UTC);

  @Test
  void reportStrictness_reichtAnDasRepositoryDurch() {
    ProjectRepository projects = mock(ProjectRepository.class);
    KitStrictnessWriter service = new ProjectKitStrictnessService(projects, UHR);

    service.reportStrictness(9L, "strict");

    verify(projects).setKitStrictness(9L, "strict");
  }

  @Test
  void reportedStrictness_nenntNurProjekteMitMeldung() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findAll())
        .thenReturn(List.of(projekt(1L, "light"), projekt(2L, null), projekt(3L, "strict")));
    KitStrictnessReader service = new ProjectKitStrictnessService(projects, UHR);

    assertThat(service.reportedStrictness()).isEqualTo(Map.of(1L, "light", 3L, "strict"));
  }

  @Test
  void reportReviewBeforeReady_reichtWertUndZeitpunktDurch() {
    ProjectRepository projects = mock(ProjectRepository.class);
    KitReviewVorReadyWriter service = new ProjectKitStrictnessService(projects, UHR);

    service.reportReviewBeforeReady(9L, true);

    verify(projects).setKitReviewBeforeReady(9L, true, JETZT);
  }

  @Test
  void meldung_nieGemeldet_istLeer() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(5L)).thenReturn(Optional.of(projektMitReview(5L, null, null)));
    KitReviewVorReadyReader service = new ProjectKitStrictnessService(projects, UHR);

    assertThat(service.meldung(5L)).isEmpty();
  }

  @Test
  void meldung_unbekanntesProjekt_istLeer() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(5L)).thenReturn(Optional.empty());
    KitReviewVorReadyReader service = new ProjectKitStrictnessService(projects, UHR);

    assertThat(service.meldung(5L)).isEmpty();
  }

  @Test
  void meldung_liefertWertUndAenderungszeitpunkt() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(5L)).thenReturn(Optional.of(projektMitReview(5L, false, JETZT)));
    KitReviewVorReadyReader service = new ProjectKitStrictnessService(projects, UHR);

    assertThat(service.meldung(5L)).contains(new KitReviewVorReadyReader.Meldung(false, JETZT));
  }

  @Test
  void meldung_ersteMeldung_hatKeinenAenderungszeitpunkt() {
    ProjectRepository projects = mock(ProjectRepository.class);
    when(projects.findById(5L)).thenReturn(Optional.of(projektMitReview(5L, true, null)));
    KitReviewVorReadyReader service = new ProjectKitStrictnessService(projects, UHR);

    assertThat(service.meldung(5L)).contains(new KitReviewVorReadyReader.Meldung(true, null));
  }

  private static Project projektMitReview(
      long id, @Nullable Boolean wert, @Nullable Instant geaendertAm) {
    return new Project(id, "P" + id, 1L, ANGELEGT, null, false, null, null, wert, geaendertAm);
  }

  private static Project projekt(long id, @Nullable String stufe) {
    return new Project(id, "P" + id, 1L, ANGELEGT, null, false, null, stufe);
  }
}
