package org.mwolff.manban.card.application;

/**
 * Sortierrichtung für das Ordnen einer Spalte nach Kartennummer (Issue #504). Die Richtung kommt
 * vom Aufrufer und wird nicht im Backend gemerkt: Das Frontend führt einen Auf-/Absteigend-Toggle
 * je Spalte und übergibt bei jedem Aufruf die gewünschte Richtung.
 */
public enum SortDirection {
  /** Kleinste Kartennummer zuerst. */
  ASC,
  /** Größte Kartennummer zuerst. */
  DESC
}
