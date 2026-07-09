package org.mwolff.manban.project.domain;

/**
 * Projekt-Rechte. Die Enum-Namen entsprechen den {@code key}-Werten der Tabelle
 * {@code permission} (Seed in F2); die Zuordnung zu Rollen liegt in {@code role_permission}.
 */
public enum Permission {
    CARD_CREATE,
    CARD_MOVE,
    CARD_DELETE,
    COLUMN_EDIT,
    BOARD_CREATE,
    BOARD_DELETE,
    MEMBER_INVITE,
    MEMBER_REMOVE,
    PROJECT_EDIT,
    PROJECT_DELETE,
    COMMENT_CREATE,
    ATTACHMENT_UPLOAD
}
