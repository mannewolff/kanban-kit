package org.mwolff.manban.project.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.project.domain.ProjectMembership;

/** Ausgehender Port für die Persistenz von Projekt-Mitgliedschaften. */
public interface ProjectMembershipRepository {

  ProjectMembership save(ProjectMembership membership);

  List<ProjectMembership> findByUserId(long userId);

  List<ProjectMembership> findByProjectId(long projectId);

  Optional<ProjectMembership> findByProjectIdAndUserId(long projectId, long userId);

  void deleteById(long membershipId);

  /**
   * Sperrt <strong>alle</strong> Mitgliedschaften des Projekts bis zum Ende der laufenden
   * Transaktion und liefert die Benutzer-IDs der aktuellen OWNER.
   *
   * <p><strong>Warum das Sperren zum Lesen gehört (Issue #498):</strong> Die Invariante „jedes
   * Projekt hat mindestens einen OWNER" ist eine Bedingung über eine <em>Zeilenmenge</em>, geprüft
   * vor dem Schreiben. Ohne Sperre sähen zwei gleichzeitige Degradierungen der letzten beiden Owner
   * beide noch zwei Owner, bestünden beide die Prüfung — und danach gäbe es keinen mehr.
   *
   * <p><strong>Warum alle Mitgliedschaften und nicht nur die OWNER-Zeilen:</strong> {@code
   * transferOwnership} befördert eine Zeile, die zum Sperrzeitpunkt noch <em>kein</em> OWNER ist.
   * Wären nur die OWNER-Zeilen gesperrt, könnte ein gleichzeitiges {@code removeMember} genau diese
   * frisch beförderte Zeile löschen und das Projekt ohne Owner zurücklassen. Die Mitgliedschaften
   * des Projekts sind die Menge, aus der jeder rollenändernde Pfad seine Zielzeile wählt — deshalb
   * ist sie der Anker. Ein <em>neu eingefügtes</em> Mitglied bleibt ungesperrt: Ein Insert kann die
   * Invariante nicht verletzen.
   *
   * <p>Gesperrt wird in aufsteigender ID-Reihenfolge, damit zwei Aufrufer nicht verklemmen. Der
   * zweite Aufrufer wertet die Rollenbedingung nach dem Commit des ersten auf der neuen
   * Zeilenversion aus und sieht den inzwischen degradierten Owner nicht mehr.
   *
   * @param projectId Projekt, dessen Owner-Invariante serialisiert wird
   * @return Benutzer-IDs aller OWNER des Projekts, aufsteigend
   */
  List<Long> lockOwnerUserIds(long projectId);
}
