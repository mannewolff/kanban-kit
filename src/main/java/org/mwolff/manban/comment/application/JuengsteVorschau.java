package org.mwolff.manban.comment.application;

import java.util.Optional;
import org.mwolff.manban.comment.domain.Vorschau;

/**
 * Port: die jüngste gemeldete Vorschau eines Projekts, an der die Auswertung erkennt, ob eine
 * Freigabe-Rückfrage überholt ist (Issue #2210, Plan #2206 E11). Bis die Auslieferung Vorschauen
 * kennt, meldet die Standard-Umsetzung keine; das Paket zur Auslieferung ersetzt sie.
 */
@FunctionalInterface
public interface JuengsteVorschau {

  /** Die jüngste Vorschau des Projekts; leer, wenn keine gemeldet ist. */
  Optional<Vorschau> juengste(long projectId);
}
