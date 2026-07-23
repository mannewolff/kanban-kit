package org.mwolff.manban.project.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.project.domain.Project;

/** Ausgehender Port für die Persistenz von Projekten. */
public interface ProjectRepository {

  Project save(Project project);

  Optional<Project> findById(long id);

  /** Alle Projekte (für die Plattform-Admin-Übersicht). */
  List<Project> findAll();

  /** Löscht das Projekt; Boards/Karten/Mitgliedschaften kaskadieren über DB-FKs. */
  void deleteById(long id);

  /**
   * Setzt die Projekt-Startnummer ({@code next_card_number}) — die Untergrenze für die nächste
   * projektweite Kartennummer. Wird beim Anlegen der nächsten nummerierten Karte als Floor
   * herangezogen (siehe {@code CardRepository.nextCardNumber}).
   */
  void setNextCardNumber(long projectId, int value);
}
