package org.mwolff.manban.entscheidungskette.domain;

/** Ein Vermerk an einem Glied (Kriterien 2 und 13). */
public enum Hinweis {
  /** Die Meldung des Kits wurde nachträglich von Hand geändert; ihre Angaben gelten nicht. */
  VON_HAND_GEAENDERT,
  /** Das Glied liegt in einem anderen Projekt; es wird weder benannt noch geöffnet. */
  ANDERES_PROJEKT
}
