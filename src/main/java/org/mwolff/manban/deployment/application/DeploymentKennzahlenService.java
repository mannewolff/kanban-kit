package org.mwolff.manban.deployment.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.common.Wochenfenster;
import org.mwolff.manban.deployment.domain.AuslieferungsErmittlung;
import org.mwolff.manban.deployment.domain.Auslieferungskennzahlen;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Platte „Auslieferung“ eines Boards (Issue #1835, Plan #1831 E1, E5, E8): lädt die Meldungen
 * des ganzen Projekts, zu dem das Board gehört, und lässt {@link AuslieferungsErmittlung} im
 * Fenster bis zur Uhr rechnen. Recht wie der übrige Projekt-Leitstand: die Mitgliedschaft im
 * Projekt des Boards (fachlich #1784 AK 16), Muster {@code BoardFlussService}.
 */
@Service
public class DeploymentKennzahlenService {

  private final DeploymentStore store;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final Clock clock;

  public DeploymentKennzahlenService(
      DeploymentStore store,
      BoardService boardService,
      PermissionChecker permissions,
      Clock clock) {
    this.store = store;
    this.boardService = boardService;
    this.permissions = permissions;
    this.clock = clock;
  }

  /** Die Kennzahlen des Projekts zum Board; Nichtmitglied und unbekanntes Board → 404. */
  @Transactional(readOnly = true)
  public Auslieferungskennzahlen kennzahlen(long userId, long boardId) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.requireMembership(userId, projectId);
    Instant jetzt = clock.instant();
    Auslieferungsdaten daten = store.ladeAuswertung(projectId, Wochenfenster.beginn(0, jetzt));
    return AuslieferungsErmittlung.ermittle(daten.versuche(), daten.commits(), jetzt);
  }
}
