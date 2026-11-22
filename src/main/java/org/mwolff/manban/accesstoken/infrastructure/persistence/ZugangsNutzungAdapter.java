package org.mwolff.manban.accesstoken.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.mwolff.manban.accesstoken.application.ZugangsNutzung;
import org.mwolff.manban.accesstoken.domain.AccessTokenArt;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter des {@link ZugangsNutzung}-Ports (Issue #2216, Plan #2206 E12): liest die wirksamen
 * gewöhnlichen Zugänge der Boards und lässt nur die gelten, deren Inhaber im gebundenen Projekt
 * echter OWNER oder ADMIN ist — die Rolle fragt {@link PermissionChecker}, damit sie nur an einer
 * Stelle bestimmt wird.
 */
@Component
class ZugangsNutzungAdapter implements ZugangsNutzung {

  private final KanbanAccessTokenJpaRepository jpa;
  private final PermissionChecker permissions;

  ZugangsNutzungAdapter(KanbanAccessTokenJpaRepository jpa, PermissionChecker permissions) {
    this.jpa = jpa;
    this.permissions = permissions;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, Instant> juengsteNutzungDesRechenzentrums(Collection<Long> boardIds) {
    Map<Long, Instant> jeBoard = new HashMap<>();
    if (boardIds.isEmpty()) {
      return jeBoard;
    }
    for (KanbanAccessTokenEntity t :
        jpa.findByBoardIdInAndArtAndRevokedFalse(boardIds, AccessTokenArt.STANDARD)) {
      Instant genutzt = t.getLastUsedAt();
      Long projekt = t.getProjectId();
      if (genutzt != null
          && projekt != null
          && permissions.istEchterOwnerOderAdmin(t.getUserId(), projekt)) {
        // Die Abfrage liest nur Zugänge der übergebenen Boards; das Board ist also gesetzt.
        jeBoard.merge(
            Objects.requireNonNull(t.getBoardId()), genutzt, (a, b) -> a.isAfter(b) ? a : b);
      }
    }
    return jeBoard;
  }
}
