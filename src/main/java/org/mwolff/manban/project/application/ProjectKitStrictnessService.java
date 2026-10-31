package org.mwolff.manban.project.application;

import java.util.HashMap;
import java.util.Map;
import org.mwolff.manban.project.domain.Project;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementierung der rechteprüfungsfreien Ports {@link KitStrictnessWriter} und {@link
 * KitStrictnessReader} (Issue #2014) — beide auf derselben Spalte, deshalb hier zusammen.
 *
 * <p>Bewusst eine eigene, <em>package-private</em> Klasse statt einer Methode auf {@link
 * ProjectService} — dieselbe Begründung wie bei {@link ProjectInteractiveUsageService}: Auf der
 * allgemeinen Projekt-Fassade autorisiert jede andere Methode, eine ungeprüfte Methode dort wäre
 * die Ausnahme, die man beim Lesen übersieht.
 */
@Service
class ProjectKitStrictnessService implements KitStrictnessWriter, KitStrictnessReader {

  private final ProjectRepository projects;

  ProjectKitStrictnessService(ProjectRepository projects) {
    this.projects = projects;
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
}
