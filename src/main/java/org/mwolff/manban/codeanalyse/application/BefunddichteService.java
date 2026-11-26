package org.mwolff.manban.codeanalyse.application;

import java.time.Clock;
import java.time.Instant;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.codeanalyse.domain.BefunddichteErmittlung;
import org.mwolff.manban.codeanalyse.domain.Kalenderwochen;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Platte „Codeanalyse“ eines Boards (Issue #2276, Plan #2272 E7, E9): lädt die Messungen des
 * ganzen Projekts, zu dem das Board gehört, ab dem Beginn der zwölf {@link Kalenderwochen} und
 * lässt {@link BefunddichteErmittlung} bis zur Uhr rechnen. Recht wie der übrige Projekt-Leitstand:
 * die Mitgliedschaft im Projekt des Boards (Fachplan #2271 AK 13), Muster {@code
 * DeploymentKennzahlenService}.
 *
 * <p>„Hat Messungen“ und „seit“ gelten für das ganze Projekt, nicht nur für das Fenster (E9): Eine
 * Messung vor dem Fensterbeginn zählt in keiner Woche, aber die Platte zeigt dann weder „noch keine
 * Messung“ noch „seit“.
 */
@Service
public class BefunddichteService {

  private final MessungStore store;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final Clock clock;

  public BefunddichteService(
      MessungStore store, BoardService boardService, PermissionChecker permissions, Clock clock) {
    this.store = store;
    this.boardService = boardService;
    this.permissions = permissions;
    this.clock = clock;
  }

  /** Die Wochenwerte des Projekts zum Board; Nichtmitglied und unbekanntes Board → 404. */
  @Transactional(readOnly = true)
  public Wochenwerte wochenwerte(long userId, long boardId) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.requireMembership(userId, projectId);
    Instant jetzt = clock.instant();
    Instant fensterbeginn = Kalenderwochen.fensterbeginn(jetzt);
    Wochenwerte imFenster =
        BefunddichteErmittlung.ermittle(store.ladeAb(projectId, fensterbeginn), jetzt);
    Instant dataSince =
        store
            .erstesPublishedAt(projectId)
            .filter(erste -> erste.isAfter(fensterbeginn))
            .orElse(null);
    return new Wochenwerte(store.hatMessungen(projectId), dataSince, imFenster.wochen());
  }
}
