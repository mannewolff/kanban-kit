package org.mwolff.manban.codeanalyse.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.codeanalyse.domain.Messung;

/**
 * Port der gemeldeten Messungen der Codeanalyse (Issue #2274, Plan #2272 E2, E3, E12). Er speichert
 * je Projekt und veröffentlichtem Stand die erste Meldung roh; gerechnet wird erst beim Lesen. Die
 * Transaktion stellt der Dienst.
 */
public interface MessungStore {

  /**
   * Speichert die Messung, wenn das Projekt zu ihrem {@code publishedCommit} noch keine hat, und
   * gibt in jedem Fall die gespeicherte zurück — bei einer Wiederholung die erste, auch wenn sie
   * anders lautet (E3). Zwei gleichzeitige Meldungen desselben Stands bekommen dieselbe Antwort.
   */
  Messung speichereErste(long projectId, String tokenName, Messung messung, Instant eingang);

  /** Alle Messungen des Projekts mit {@code publishedAt} am oder nach {@code ab}, aufsteigend. */
  List<Messung> ladeAb(long projectId, Instant ab);

  /**
   * Das kleinste {@code publishedAt} aller Messungen des Projekts, zählend oder nicht; leer ohne.
   */
  Optional<Instant> erstesPublishedAt(long projectId);

  /** Ob für das Projekt mindestens eine Messung angekommen ist. */
  boolean hatMessungen(long projectId);
}
