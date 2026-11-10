package org.mwolff.manban.project.application;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.mwolff.manban.project.domain.Project;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementierung der rechteprüfungsfreien Ports {@link KitStrictnessWriter} und {@link
 * KitStrictnessReader} (Issue #2014) — beide auf derselben Spalte, deshalb hier zusammen —, dazu
 * {@link KitReviewVorReadyWriter} und {@link KitReviewVorReadyReader} (Issue #2112), der zweite
 * Wert, den das Kit auf demselben Weg meldet.
 *
 * <p>Bewusst eine eigene, <em>package-private</em> Klasse statt einer Methode auf {@link
 * ProjectService} — dieselbe Begründung wie bei {@link ProjectInteractiveUsageService}: Auf der
 * allgemeinen Projekt-Fassade autorisiert jede andere Methode, eine ungeprüfte Methode dort wäre
 * die Ausnahme, die man beim Lesen übersieht.
 */
@Service
class ProjectKitStrictnessService
    implements KitStrictnessWriter,
        KitStrictnessReader,
        KitReviewVorReadyWriter,
        KitReviewVorReadyReader {

  private final ProjectRepository projects;
  private final Clock clock;

  ProjectKitStrictnessService(ProjectRepository projects, Clock clock) {
    this.projects = projects;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void reportStrictness(long projectId, String value) {
    projects.setKitStrictness(projectId, value);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, String> reportedStrictness() {
    Map<Long, String> jeProjekt = new HashMap<>();
    for (Project project : projects.findAll()) {
      String stufe = project.kitStrictness();
      if (stufe != null) {
        jeProjekt.put(project.requireId(), stufe);
      }
    }
    return jeProjekt;
  }

  @Override
  @Transactional
  public void reportReviewBeforeReady(long projectId, boolean value) {
    projects.setKitReviewBeforeReady(projectId, value, clock.instant());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Meldung> meldung(long projectId) {
    return projects.findById(projectId).flatMap(ProjectKitStrictnessService::meldungVon);
  }

  private static Optional<Meldung> meldungVon(Project project) {
    Boolean wert = project.kitReviewBeforeReady();
    return wert == null
        ? Optional.empty()
        : Optional.of(new Meldung(wert, project.kitReviewBeforeReadyChangedAt()));
  }
}
