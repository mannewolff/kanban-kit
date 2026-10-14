package org.mwolff.manban.nightrun.application;

import java.util.Optional;
import org.mwolff.manban.nightrun.domain.ReadyBewertung;

/**
 * Port für die Bewertung der Ready-Karten je Projekt (Issue #1726). Je Projekt liegt höchstens eine
 * Bewertung vor — die jüngste.
 */
public interface NightReadyAssessmentRepository {

  /** Die gespeicherte Bewertung des Projekts samt Einträgen und Gründen — oder leer. */
  Optional<ReadyBewertung> findByProjectId(long projectId);

  /**
   * Ersetzt die Bewertung des Projekts vollständig: Kopf, Einträge und Gründe. Die Prüfung, ob die
   * neue jünger ist, trifft der Aufrufer unter der Projektsperre.
   */
  void ersetze(long projectId, ReadyBewertung bewertung);
}
