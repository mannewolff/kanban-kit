package org.mwolff.manban.fremdboard.infrastructure;

import org.mwolff.manban.fremdboard.application.VerbindungRepository;
import org.mwolff.manban.project.application.BoardQuelle;
import org.springframework.stereotype.Component;

/**
 * Umsetzung von {@link BoardQuelle} über die gespeicherte Verbindung (Issue #2156, Plan #2152 A10):
 * Fremd ist ein Board genau dann, wenn sein Projekt eine Zeile in {@code fremdboard_verbindung}
 * hat.
 */
@Component
class VerbindungBoardQuelle implements BoardQuelle {

  private final VerbindungRepository verbindungen;

  VerbindungBoardQuelle(VerbindungRepository verbindungen) {
    this.verbindungen = verbindungen;
  }

  @Override
  public boolean istFremd(long projectId) {
    return verbindungen.existiert(projectId);
  }
}
