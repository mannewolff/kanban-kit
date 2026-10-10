package org.mwolff.manban.kanbancompat.application;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;

/**
 * Die Board-Bindung des Zugangs, gemeinsam für {@link KanbanCompatService} und {@link
 * KanbanArchivService} (Plan #1574, E19). Liegt hier und nicht an {@link KanbanPrincipal}: Das
 * Modul {@code accesstoken} kennt {@link TokenNotBoundException} nicht, und die Kante dorthin wäre
 * ein Zyklus.
 */
final class KanbanBindung {

  private KanbanBindung() {}

  /** Das gebundene Board des Zugangs; ohne Bindung {@link TokenNotBoundException} (409). */
  static long requireBound(@Nullable KanbanPrincipal principal) {
    if (principal == null || !principal.isBound()) {
      throw new TokenNotBoundException();
    }
    // isBound() garantiert die Bindung; requireNonNull macht das fuer NullAway explizit.
    return Objects.requireNonNull(principal.boardId());
  }
}
