package org.mwolff.manban.comment.application;

import java.util.Optional;
import org.mwolff.manban.comment.domain.Vorschau;

/**
 * Port: die jüngste gemeldete Vorschau eines Projekts, an der die Auswertung erkennt, ob eine
 * Freigabe-Rückfrage überholt ist (Issue #2210, Plan #2206 E11). Umgesetzt wird er im Modul {@code
 * deployment} aus den gemeldeten Vorschauen (Issue #2215); der Port prüft keine Rechte, die Prüfung
 * liegt beim Aufrufer.
 */
@FunctionalInterface
public interface JuengsteVorschau {

  /** Die jüngste Vorschau des Projekts; leer, wenn keine gemeldet ist. */
  Optional<Vorschau> juengste(long projectId);
}
