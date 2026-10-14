package org.mwolff.manban.nightrun.application;

import java.util.Optional;
import org.mwolff.manban.nightrun.domain.ReadyBewertung;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Was der Runner jetzt tut und warum ein Paket wartet (Plan #1724): der Meldeweg der Bewertung der
 * Ready-Karten (Issue #1726).
 *
 * <p>Die Methoden nehmen Primitive und keinen {@code KanbanPrincipal}: Application und Domäne von
 * {@code nightrun} kennen das Modul {@code accesstoken} nicht (ArchitectureTest).
 */
@Service
public class NightRunNowService {

  private final NightReadyAssessmentRepository bewertungen;
  private final NightRunRepository runs;
  private final PermissionChecker permissions;

  public NightRunNowService(
      NightReadyAssessmentRepository bewertungen,
      NightRunRepository runs,
      PermissionChecker permissions) {
    this.bewertungen = bewertungen;
    this.runs = runs;
    this.permissions = permissions;
  }

  /**
   * Speichert die gemeldete Bewertung als die des Projekts (Plan #1724 E7, Vertrag V2).
   *
   * <p>Melden darf, wer auch Läufe meldet ({@link PermissionChecker#requireOwner}). Die
   * Projektsperre aus {@link NightRunRepository#lockProject} geht dem Lesen voraus: Zwei
   * gleichzeitige Meldungen sehen sonst beide den alten Stand, und die ältere könnte die jüngere
   * verdrängen. Eine gleich alte ersetzt — sie ist die Wiederholung derselben Meldung.
   *
   * @throws NightReadyAssessmentOutdatedException wenn die gespeicherte Bewertung jünger ist
   */
  @Transactional
  public void meldeBewertung(long userId, long projectId, ReadyBewertung bewertung) {
    permissions.requireOwner(userId, projectId);
    runs.lockProject(projectId);
    Optional<ReadyBewertung> gespeichert = bewertungen.findByProjectId(projectId);
    if (gespeichert.isPresent() && gespeichert.get().assessedAt().isAfter(bewertung.assessedAt())) {
      throw new NightReadyAssessmentOutdatedException();
    }
    bewertungen.ersetze(projectId, bewertung);
  }
}
