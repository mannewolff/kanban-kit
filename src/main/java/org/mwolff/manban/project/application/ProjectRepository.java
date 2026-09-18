package org.mwolff.manban.project.application;

import java.time.Instant;
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

  /**
   * Setzt den Erfassungsbeginn der interaktiven Sitzungen ({@code project.interactive_usage_since})
   * — <b>nur, wenn er noch leer ist</b> (Issue #1012, Plan #1007 E18).
   *
   * <p>Die Bedingung steht im {@code UPDATE} selbst und nicht beim Aufrufer: Ein vorgelagertes
   * {@code SELECT} hätte ein Rennen — zwei gleichzeitig eingehende Sitzungen läsen beide einen
   * leeren Wert, und die spätere überschriebe die frühere. Ein unbekanntes Projekt ist ein No-Op.
   *
   * @param startedAt Startzeitpunkt der Sitzung, nicht die Uhr des Servers — der Wert markiert, ab
   *     wann erfasst wurde
   */
  void setInteractiveUsageSinceIfAbsent(long projectId, Instant startedAt);
}
