package org.mwolff.manban.auth.domain;

/** Warum eine Vorschau „Ansicht als XY“ endete (Issue #1917, Plan #1914 E9, E12). */
public enum VorschauEndgrund {
  /** Der Admin hat „Beenden“ gewählt. */
  BEENDET,
  /** Die 30 Minuten sind um; {@code beendetAm} ist dann der Ablaufzeitpunkt. */
  ABLAUF,
  /** Der Admin hat sich abgemeldet. */
  ABMELDUNG,
  /** Der Admin ist nicht mehr Plattform-Admin. */
  ROLLE_ENTZOGEN,
  /** Das Konto des Admins ist gesperrt. */
  ADMIN_GESPERRT,
  /** Die Anmeldung, aus der die Vorschau startete, gilt nicht mehr (höhere Sitzungs-Generation). */
  SITZUNG_UNGUELTIG,
  /** Das Zielkonto ist inzwischen Plattform-Admin. */
  ZIEL_IST_ADMIN,
  /** Das Zielkonto gibt es nicht mehr. */
  ZIEL_FEHLT
}
