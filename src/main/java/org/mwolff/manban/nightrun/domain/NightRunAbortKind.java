package org.mwolff.manban.nightrun.domain;

/**
 * Wie ein abgebrochener Lauf zu seinem Abschluss kam (Issue #1499, Plan #1498, fachliche Quelle
 * #1493).
 *
 * <p>Der Abbruchgrund allein sagt das nicht: Einen Grund trägt auch der Lauf, den das Kit
 * nachträglich als verstummt abschließt. Wer ihn auf der Kommandozeile gesehen hat, unterscheidet
 * sich aber — und daran hängt, ob ein Lauf ohne angefasstes Paket „nicht angelaufen" ist oder eine
 * Störung (AK 6 der fachlichen Quelle).
 */
public enum NightRunAbortKind {

  /**
   * Der Lauf hat seinen Abbruch selbst gemeldet — Kit-Abschluss {@code abgebrochen} oder {@code
   * harterStopp}. Wer ihn gestartet hat, sah den Grund auf der Kommandozeile.
   */
  REPORTED,

  /**
   * Der Lauf ist verstummt, und der Wächter des Kits hat ihn nachträglich abgeschlossen —
   * Kit-Abschluss {@code verstummt}. Den Grund hat niemand gesehen, als er entstand.
   */
  SILENCED
}
