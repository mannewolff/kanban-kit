import type { ComponentType } from 'react'
import type { SvgIconProps } from '@mui/material'
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings'
import FolderIcon from '@mui/icons-material/Folder'
import {
  BoardSymbol,
  IdeenSymbol,
  LeitstandSymbol,
  ListeSymbol,
  MitgliederSymbol,
  NachtlaeufeSymbol,
  RollenSymbol,
  VorhabenSymbol,
} from './navIcons'

type NavIcon = ComponentType<SvgIconProps>

export interface NavLink {
  kind: 'link'
  label: string
  path: string
  icon: NavIcon
}

/**
 * Ein Block der Navigationsschiene (Entwurf `.nav-block`): Etikett als Titel, darunter die
 * Einträge. Blöcke klappen nicht auf und zu — der Entwurf zeigt sie immer offen.
 */
export interface NavGroup {
  kind: 'group'
  /** Fester Schlüssel des Blocks; der Titel wechselt, sobald der Projektname geladen ist. */
  id: 'projekt' | 'uebersicht' | 'verwaltung'
  label: string
  children: NavLink[]
}

/** Aktueller Board-Kontext für die kontextbewusste Seitenleiste. */
export interface BoardContext {
  id: number
  name: string
  projectId: number
}

/**
 * Parameter für den Navigationsbaum. Zählungen ({@code null} = noch unbekannt) steuern die
 * Sichtbarkeit: nichts anzeigen, was man nicht wählen kann.
 */
export interface NavParams {
  board: BoardContext | null
  isAdmin?: boolean
  /** Anzahl sichtbarer Projekte; bei genau 1 wird „Projekte" ausgeblendet (außer System-Admin). */
  projectCount?: number | null
  /** Anzahl Boards im aktuellen Projekt; bei genau 1 wird „Boards" ausgeblendet (außer man darf Boards verwalten). */
  boardCount?: number | null
  /** Ob man im aktuellen Projekt Boards anlegen/löschen darf (dann bleibt „Boards" erreichbar). */
  canManageBoards?: boolean
  /**
   * Aktueller Projekt-Kontext auch ohne offenes Board (z. B. auf der Boards-/Ideen-Seite). Steuert
   * die Sichtbarkeit des projektweiten „Ideen"-Links. Bei offenem Board hat {@code board.projectId}
   * Vorrang; {@code null} = kein Projekt-Kontext (dann kein „Ideen"-Link).
   */
  projectId?: number | null
  /**
   * Ob der „Nachtlauf"-Bereich des aktuellen Projekts sichtbar ist. Der fertige Boolesche Wert
   * kommt von außen (Rolle und Plattform-Admin bleiben Sache der Shell); hier zählt nur, dass er
   * gesetzt ist und ein Projekt-Kontext besteht.
   */
  canViewNightRun?: boolean
  /** Name des aktuellen Projekts für den Titel des Projekt-Blocks; unbekannt = „Projekt". */
  projectName?: string | null
  /** Ob man die Mitglieder des aktuellen Projekts verwalten darf (dann erscheint „Mitglieder"). */
  canManageMembers?: boolean
}

/**
 * Baut die Blöcke der Navigationsschiene in der Gliederung des Leitstand-Entwurfs (#978,
 * `docs/entwurf-leitstand.html` HTML Z. 1114–1156):
 *
 * - **Projekt** — sobald ein Projekt-Kontext besteht: bei offenem Board Leitstand, Board, Liste und
 *   Vorhaben, dazu projektweit Ideen und (mit Recht) Nachtläufe.
 * - **Übersicht** — „Projekte" nur, wenn es etwas zu wählen gibt (≥ 2 Projekte) oder man
 *   System-Admin ist; „Boards" bei offenem Board nur, wenn es ≥ 2 Boards gibt oder man sie verwalten
 *   darf. Der Entwurf kennt diesen Block nicht; er hält die Wege zurück zur Auswahl.
 * - **Verwaltung** — Mitglieder (mit Recht), Rollen & Rechte, Admin (System-Admin).
 *
 * Leere Blöcke entfallen. Administration und Dokumentation stehen im Fuß der Schiene.
 */
export function buildNavItems(params: NavParams): NavGroup[] {
  const {
    board,
    isAdmin = false,
    projectCount = null,
    boardCount = null,
    canManageBoards = false,
    projectId = null,
    canViewNightRun = false,
    projectName = null,
    canManageMembers = false,
  } = params
  const bloecke: NavGroup[] = []

  // Projekt-Kontext: entweder aus dem offenen Board oder direkt aus einer Projekt-Route.
  const currentProjectId = board?.projectId ?? projectId

  if (currentProjectId !== null) {
    const projekt: NavLink[] = []
    if (board) {
      projekt.push(
        { kind: 'link', label: 'Leitstand', path: `/boards/${board.id}/dashboard`, icon: LeitstandSymbol },
        { kind: 'link', label: 'Board', path: `/boards/${board.id}`, icon: BoardSymbol },
        { kind: 'link', label: 'Liste', path: `/boards/${board.id}/list`, icon: ListeSymbol },
        { kind: 'link', label: 'Vorhaben', path: `/boards/${board.id}/vorhaben`, icon: VorhabenSymbol },
      )
    }
    // „Ideen" ist projektweit und auch ohne offenes Board sichtbar — dort liegt der Ideen-Pool.
    projekt.push({ kind: 'link', label: 'Ideen', path: `/projects/${currentProjectId}/ideas`, icon: IdeenSymbol })
    // „Nachtläufe" liegt hinter einem eigenen Recht (Owner bzw. Plattform-Admin); die Shell entscheidet.
    if (canViewNightRun) {
      projekt.push({
        kind: 'link',
        label: 'Nachtläufe',
        path: `/projects/${currentProjectId}/nachtlauf`,
        icon: NachtlaeufeSymbol,
      })
    }
    bloecke.push({ kind: 'group', id: 'projekt', label: projectName ? `Projekt ${projectName}` : 'Projekt', children: projekt })
  }

  const uebersicht: NavLink[] = []
  if (isAdmin || projectCount !== 1) {
    uebersicht.push({ kind: 'link', label: 'Projekte', path: '/', icon: FolderIcon })
  }
  if (board && (canManageBoards || boardCount !== 1)) {
    uebersicht.push({ kind: 'link', label: 'Boards', path: `/projects/${board.projectId}`, icon: BoardSymbol })
  }
  if (uebersicht.length > 0) {
    bloecke.push({ kind: 'group', id: 'uebersicht', label: 'Übersicht', children: uebersicht })
  }

  const verwaltung: NavLink[] = []
  if (currentProjectId !== null && canManageMembers) {
    verwaltung.push({
      kind: 'link',
      label: 'Mitglieder',
      path: `/projects/${currentProjectId}/members`,
      icon: MitgliederSymbol,
    })
  }
  verwaltung.push({ kind: 'link', label: 'Rollen & Rechte', path: '/roles', icon: RollenSymbol })
  if (isAdmin) {
    verwaltung.push({ kind: 'link', label: 'Admin', path: '/admin', icon: AdminPanelSettingsIcon })
  }
  bloecke.push({ kind: 'group', id: 'verwaltung', label: 'Verwaltung', children: verwaltung })

  return bloecke
}
