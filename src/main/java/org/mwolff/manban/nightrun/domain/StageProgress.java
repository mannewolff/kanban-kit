package org.mwolff.manban.nightrun.domain;

/**
 * Eine Stufe im Weg einer Kette mit ihrem Zustand (Issue #1374).
 *
 * @param stufe die Stufe
 * @param zustand ihr Zustand
 */
public record StageProgress(ProgressStage stufe, StageState zustand) {}
