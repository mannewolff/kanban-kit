import type { ComponentType } from 'react'
import type { SvgIconProps } from '@mui/material'
import AccountTreeIcon from '@mui/icons-material/AccountTree'
import FolderIcon from '@mui/icons-material/Folder'
import ViewColumnIcon from '@mui/icons-material/ViewColumn'

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
}

/**
 * Baut die Navigationsbäume: „Projekte" ist immer da; ist ein Board offen,
 * kommt eine nach dem Board benannte Gruppe hinzu (Kind „Board"; „Epics" ergänzt E2).
 */
export function buildNavItems(board: BoardContext | null): NavNode[] {
  const items: NavNode[] = [{ kind: 'link', label: 'Projekte', path: '/', icon: FolderIcon }]
  if (board) {
    items.push({
      kind: 'group',
      label: board.name,
      icon: ViewColumnIcon,
      children: [
        { kind: 'link', label: 'Board', path: `/boards/${board.id}`, icon: ViewColumnIcon },
        { kind: 'link', label: 'Epics', path: `/boards/${board.id}/epics`, icon: AccountTreeIcon },
      ],
    })
  }
  return items
}
