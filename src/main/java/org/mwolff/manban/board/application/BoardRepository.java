package org.mwolff.manban.board.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.board.domain.Board;

/** Ausgehender Port für die Persistenz von Boards. */
public interface BoardRepository {

  Board save(Board board);

  /** Nur aktive Boards; archivierte gelten hier als nicht vorhanden (→ 404 an allen Aufrufern). */
  Optional<Board> findById(long id);

  /** Findet ein Board unabhängig vom Archiv-Status (für Wiederherstellen/endgültiges Löschen). */
  Optional<Board> findByIdIncludingArchived(long id);

  /** Nur aktive Boards des Projekts. */
  List<Board> findByProjectId(long projectId);

  /**
   * Alle aktiven Boards aller Projekte, nach Anlage geordnet — für Verwaltungsaufgaben der ganzen
   * Installation (Standard-Labels, Issue #1485).
   */
  List<Board> findAllActive();

  /** Nur archivierte Boards des Projekts (für die Archiv-Ansicht). */
  List<Board> findArchivedByProjectId(long projectId);

  void deleteById(long id);
}
