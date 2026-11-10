package org.mwolff.manban.project.application;

import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Lesender Port für die zuletzt vom Kit gemeldete Einstellung zur Prüfung vor Ready (Issue #2112,
 * Plan #2108 E18). Die Entscheidungskette entscheidet damit am Glied Issue-Review zwischen „nicht
 * vorgesehen“ und „nicht erfasst“.
 *
 * <p><strong>Sicherheitshinweis:</strong> Dieser Port prüft <strong>keine</strong> Rechte; das tut
 * der Aufrufer. Der Aufruferkreis ist deshalb in {@code
 * PortWhitelistArchitectureTest.KIT_REVIEW_VOR_READY_READER_NUR_ENTSCHEIDUNGSKETTE} maschinell
 * begrenzt.
 */
@FunctionalInterface
public interface KitReviewVorReadyReader {

  /** Die Meldung des Projekts; leer, wenn nie gemeldet wurde oder das Projekt unbekannt ist. */
  Optional<Meldung> meldung(long projectId);

  /**
   * Der zuletzt gemeldete Wert.
   *
   * @param wert ob das Projekt die Prüfung vor Ready verlangt
   * @param geaendertAm wann ein schon gemeldeter Wert zuletzt wechselte; {@code null}, solange er
   *     seit der ersten Meldung gleich blieb
   */
  record Meldung(boolean wert, @Nullable Instant geaendertAm) {}
}
