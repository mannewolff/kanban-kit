package org.mwolff.manban.comment.infrastructure.vorschau;

import java.util.Optional;
import org.mwolff.manban.comment.application.JuengsteVorschau;
import org.mwolff.manban.comment.domain.Vorschau;
import org.springframework.stereotype.Component;

/**
 * Standard-Umsetzung von {@link JuengsteVorschau}, solange die Auslieferung keine Vorschauen kennt
 * (Issue #2210): Es ist nie eine gemeldet, eine Freigabe-Rückfrage ist darum nie überholt. Das
 * Paket zur Auslieferung ersetzt diese Klasse.
 */
@Component
class KeineVorschau implements JuengsteVorschau {

  @Override
  public Optional<Vorschau> juengste(long projectId) {
    return Optional.empty();
  }
}
