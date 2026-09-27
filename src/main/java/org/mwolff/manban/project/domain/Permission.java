package org.mwolff.manban.project.domain;

/**
 * Projekt-Rechte als granulare CRUD-Matrix (je Ressource/Operation ein Recht). Die Enum-Namen
 * entsprechen den {@code key}-Werten der Tabelle {@code permission} (Seed in {@code V4}); die
 * Zuordnung zu Rollen liegt in {@code role_permission}.
 *
 * <p>Konvention: {@code <RESSOURCE>_<OPERATION>} — daraus lassen sich Ressource und Operation
 * ableiten (z. B. für die Matrix-Anzeige). {@code CARD_MOVE} ist die Operation „Karten
 * verschieben".
 *
 * <p>Die Reihenfolge der Werte ist die Anzeige-Reihenfolge der Matrix, und die Gruppierung nach
 * Ressource arbeitet auf <em>zusammenhängenden</em> Blöcken (siehe {@code RolesPage}). Ein neuer
 * Wert gehört deshalb zu seiner Ressource, nicht an das Ende der Liste.
 *
 * <p>Die Ressource {@code EPIC_*} heißt in der Oberfläche <b>Vorhaben</b>. Die Schlüssel bleiben
 * unverändert, weil sie die gespeicherten {@code key}-Werte aus {@code V4} sind; umbenannt wird
 * allein die Anzeige (siehe {@code RoleMatrixService} und die Rollen-Ansicht im Frontend).
 */
public enum Permission {
  BOARD_CREATE,
  BOARD_READ,
  BOARD_UPDATE,
  BOARD_DELETE,

  EPIC_CREATE,
  EPIC_READ,
  EPIC_UPDATE,
  EPIC_DELETE,

  TICKET_CREATE,
  TICKET_READ,
  TICKET_UPDATE,
  TICKET_DELETE,

  CARD_MOVE,
  /**
   * Karte in ein <b>anderes Projekt</b> verschieben (Issue #1165). Durchgesetzt wird das weiterhin
   * über die Rolle OWNER in <em>beiden</em> Projekten ({@code CardService#transfer}) — dieser
   * Schlüssel beschreibt die Regel, er ersetzt die Prüfung nicht.
   */
  CARD_MOVE_PROJECT,

  COMMENT_CREATE,
  COMMENT_READ,
  COMMENT_UPDATE,
  COMMENT_DELETE,

  ATTACHMENT_CREATE,
  ATTACHMENT_READ,
  ATTACHMENT_DELETE,

  MEMBER_INVITE,
  MEMBER_REMOVE,

  PROJECT_EDIT,
  PROJECT_OWNER_TRANSFER,

  /**
   * Auswertung der Läufe lesen (Issue #1165). Durchgesetzt wird das weiterhin über {@code
   * PermissionChecker#requireNightRunAccess}: der echte Projekt-OWNER immer, ein Plattform-Admin
   * ohne OWNER-Rolle nur bei Teilnahme am Plattform-Leitstand. Diese Zusatzbedingung hängt an
   * keiner Projekt-Rolle und ist deshalb kein eigener Schlüssel, sondern eine Fußnote der Matrix.
   */
  NIGHT_RUN_READ,

  /**
   * Protokoll eines Laufs hineingeben (Issue #1165). Eigener Schlüssel neben {@link
   * #NIGHT_RUN_READ}, weil der Bestand beides schon verschieden behandelt (lesen über {@code
   * requireNightRunAccess}, einliefern über {@code requireOwner}) und eine künftige Rolle lesen
   * dürfen soll, ohne einliefern zu dürfen.
   */
  NIGHT_RUN_SUBMIT
}
