export type ProjectRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER'

/**
 * Ein Plattform-Admin ist Super-User (Vollzugriff auf alle Projekte, Backend erzwungen).
 * Die Rollen-Helfer geben deshalb bei {@code platformAdmin=true} immer {@code true}.
 */

/** Wer Boards anlegen/löschen darf (BOARD_CREATE/BOARD_DELETE). */
export const canManageBoards = (role: string, platformAdmin = false): boolean =>
  platformAdmin || role === 'OWNER' || role === 'ADMIN'

/** Wer das Projekt umbenennen/löschen darf (nur OWNER oder Plattform-Admin). */
export const canManageProject = (role: string, platformAdmin = false): boolean =>
  platformAdmin || role === 'OWNER'

/** Wer Mitglieder verwalten darf (MEMBER_INVITE/MEMBER_REMOVE). */
export const canManageMembers = (role: string, platformAdmin = false): boolean =>
  platformAdmin || role === 'OWNER' || role === 'ADMIN'

/** Wer Karten anlegen/verschieben darf (CARD_*). */
export const canEditCards = (role: string, platformAdmin = false): boolean =>
  platformAdmin || role !== 'VIEWER'

/** Ob der eingeloggte Nutzer plattformweit Admin ist. */
export const isPlatformAdmin = (user: { platformRole?: string } | null | undefined): boolean =>
  user?.platformRole === 'ADMIN'
