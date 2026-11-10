package org.mwolff.manban.entscheidungskette.domain;

/** Ein Abschnitt der Kette, von oben nach unten (Kriterium 3). */
public enum Dokumentart {
  IDEE,
  FACHPLAN,
  PLAN,
  PAKET;

  /** Das Dokument einer Karte: alles außer Fachplan und Plan ist ein Paket. */
  static Dokumentart von(KettenEingabe.Art art) {
    return switch (art) {
      case FACHLICH -> FACHPLAN;
      case PLAN -> Dokumentart.PLAN;
      default -> Dokumentart.PAKET;
    };
  }
}
