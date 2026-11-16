package org.mwolff.manban.fremdboard.domain;

/**
 * Status einer Karte des fremden Boards (Plan #2152 A3) — immer einer der fünf Prozesszustände des
 * Kits. Die Konstanten heißen wie die von {@code card.domain.CardStatus}, damit der Spiegel sie
 * über den Namen überträgt, ohne {@code card.domain} zu kennen.
 */
public enum FremdStatus {
  BACKLOG,
  READY,
  IN_PROGRESS,
  IN_REVIEW,
  DONE
}
