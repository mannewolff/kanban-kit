import type { ComponentType } from 'react'
import type { SvgIconProps } from '@mui/material'
import AccountTreeIcon from '@mui/icons-material/AccountTree'
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings'
import FolderIcon from '@mui/icons-material/Folder'
import InsightsIcon from '@mui/icons-material/Insights'
import ViewColumnIcon from '@mui/icons-material/ViewColumn'
import ViewListIcon from '@mui/icons-material/ViewList'

type NavIcon = ComponentType<SvgIconProps>

export interface NavLink {
  kind: 'link'
  label: string
  path: string
  icon: NavIcon
}

export interface NavGroup {
  kind: 'group'
  label: string
  icon: NavIcon
  children: NavLink[]
}

export type NavNode = NavLink | NavGroup

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
}

/**
 * Baut den Navigationsbaum kontextbewusst. „Projekte" erscheint nur, wenn es etwas zu wählen gibt
 * (≥ 2 Projekte) oder man System-Admin ist (Anlege-Zugang). Ist ein Board offen, kommt eine nach
 * dem Board benannte Gruppe hinzu; darin verlinkt „Boards" zurück zur Boardauswahl — aber nur, wenn
 * das Projekt ≥ 2 Boards hat oder man Boards verwalten darf.
 */
export function buildNavItems(params: NavParams): NavNode[] {
  const { board, isAdmin = false, projectCount = null, boardCount = null, canManageBoards = false } = params
  const items: NavNode[] = []

  if (isAdmin || projectCount !== 1) {
    items.push({ kind: 'link', label: 'Projekte', path: '/', icon: FolderIcon })
  }

  if (board) {
    const children: NavLink[] = []
    if (canManageBoards || boardCount !== 1) {
      children.push({ kind: 'link', label: 'Boards', path: `/projects/${board.projectId}`, icon: FolderIcon })
    }
    children.push(
      { kind: 'link', label: 'Board', path: `/boards/${board.id}`, icon: ViewColumnIcon },
      { kind: 'link', label: 'Liste', path: `/boards/${board.id}/list`, icon: ViewListIcon },
      { kind: 'link', label: 'Epics', path: `/boards/${board.id}/epics`, icon: AccountTreeIcon },
      { kind: 'link', label: 'Dashboard', path: `/boards/${board.id}/dashboard`, icon: InsightsIcon },
    )
    items.push({ kind: 'group', label: board.name, icon: ViewColumnIcon, children })
  }

  if (isAdmin) {
    items.push({ kind: 'link', label: 'Admin', path: '/admin', icon: AdminPanelSettingsIcon })
  }
  return items
}
