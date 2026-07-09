export type ProjectRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER'

/** Wer Boards anlegen/löschen darf (BOARD_CREATE/BOARD_DELETE). */
export const canManageBoards = (role: string): boolean => role === 'OWNER' || role === 'ADMIN'

/** Wer das Projekt umbenennen/löschen darf (nur OWNER). */
export const canManageProject = (role: string): boolean => role === 'OWNER'

/** Wer Mitglieder verwalten darf (MEMBER_INVITE/MEMBER_REMOVE). */
export const canManageMembers = (role: string): boolean => role === 'OWNER' || role === 'ADMIN'

/** Wer Karten anlegen/verschieben darf (CARD_*). */
export const canEditCards = (role: string): boolean => role !== 'VIEWER'
