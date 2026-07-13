package org.mwolff.manban.project.domain;

/**
 * Projekt-Rechte als granulare CRUD-Matrix (je Ressource/Operation ein Recht). Die Enum-Namen
 * entsprechen den {@code key}-Werten der Tabelle {@code permission} (Seed in {@code V4}); die
 * Zuordnung zu Rollen liegt in {@code role_permission}.
 *
 * <p>Konvention: {@code <RESSOURCE>_<OPERATION>} — daraus lassen sich Ressource und Operation
 * ableiten (z. B. für die Matrix-Anzeige). {@code CARD_MOVE} ist die Operation „Karten
 * verschieben".
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
  PROJECT_OWNER_TRANSFER
}
