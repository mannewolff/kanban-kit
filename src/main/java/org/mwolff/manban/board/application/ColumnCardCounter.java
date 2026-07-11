package org.mwolff.manban.board.application;

/** Ausgehender Port: Anzahl der Karten in einer Spalte (für die Lösch-Sperre). */
@FunctionalInterface
public interface ColumnCardCounter {

  long countByColumnId(long columnId);
}
