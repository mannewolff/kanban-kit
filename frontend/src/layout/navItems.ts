import type { ComponentType } from 'react'
import type { SvgIconProps } from '@mui/material'
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings'
import FolderIcon from '@mui/icons-material/Folder'
import { canManageBoards, canManageMembers } from '../lib/roles'
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
        { kind: 'link', label: 'Leitstand', path: `/boards/${board.id}/leitstand`, icon: LeitstandSymbol },
        { kind: 'link', label: 'Board', path: `/boards/${board.id}`, icon: BoardSymbol },
        { kind: 'link', label: 'Liste', path: `/boards/${board.id}/list`, icon: ListeSymbol },
        { kind: 'link', label: 'Vorhaben', path: `/boards/${board.id}/vorhaben`, icon: VorhabenSymbol },
      )
    }
    // „Ideen" ist projektweit und auch ohne offenes Board sichtbar — dort liegt der Ideen-Pool.
    projekt.push({ kind: 'link', label: 'Ideen', path: `/projects/${currentProjectId}/ideas`, icon: IdeenSymbol })
    // „Läufe" liegt hinter einem eigenen Recht (Owner bzw. Plattform-Admin); die Shell entscheidet.
    if (canViewNightRun) {
      projekt.push({
        kind: 'link',
        label: 'Läufe',
        path: `/projects/${currentProjectId}/nachtlauf`,
        icon: NachtlaeufeSymbol,
      })
    }
    bloecke.push({ kind: 'group', id: 'projekt', label: projectName ? `Projekt ${projectName}` : 'Projekt', children: projekt })
  }

  const uebersicht: NavLink[] = []
  if (isAdmin || projectCount !== 1) {
    uebersicht.push({ kind: 'link', label: 'Projekte', path: '/projects', icon: FolderIcon })
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
    // Die Startseite eines Plattform-Admins (Issue #1082, AK 2): jederzeit erreichbar, nicht nur
    // beim Anmelden. Wer nicht Plattform-Admin ist, sieht den Eintrag nicht (AK 3).
    verwaltung.push({
      kind: 'link',
      label: 'Plattform-Leitstand',
      path: '/plattform-leitstand',
      icon: LeitstandSymbol,
    })
    verwaltung.push({ kind: 'link', label: 'Admin', path: '/admin', icon: AdminPanelSettingsIcon })
  }
  bloecke.push({ kind: 'group', id: 'verwaltung', label: 'Verwaltung', children: verwaltung })

  return bloecke
}

/**
 * Projekt, so weit die Schiene es braucht. Strukturell kompatibel mit {@code Project} der API —
 * bewusst als eigener Typ, damit dieses Modul rein bleibt und nichts aus {@code api/} zieht.
 */
export interface NavProjekt {
  id: number
  name: string
  role: string
  /**
   * Ob das Projekt am Plattform-Leitstand teilnimmt (Issue #1076). Seit Issue #1079 haengt daran,
   * ob ein Plattform-Admin ohne eigene OWNER-Rolle die Nachtlauf-Auswertung ueberhaupt lesen darf;
   * ohne die Angabe fuehrte der Eintrag "Nachtlaeufe" auf einen Fehlertext.
   */
  dashboardParticipation?: boolean
}

/** Rohdaten der Shell für {@link navKontext}: Route, geladenes Board, Projektliste, Plattform-Rolle. */
export interface NavKontextParams {
  /** Das zuletzt geladene Board der Shell; {@code null} = keines geladen. */
  board: BoardContext | null
  /** Projekt-ID aus der Route {@code /projects/:id/*}, sonst {@code null}. */
  routeProjectId: number | null
  /** Board-ID aus der Route {@code /boards/:id/*}, sonst {@code null}. */
  boardId: number | null
  /** Geladene Projektliste; {@code null} = noch unbekannt. */
  projects: NavProjekt[] | null
  /** Ob der angemeldete Nutzer Plattform-Admin ist. */
  admin: boolean
}

/** Abgeleiteter Zustand der Schiene und des Kopfs — alles, was aus Route und Projektliste folgt. */
export interface NavKontext {
  /** Board, das die Schiene zeigt; auf einer fremden Projektseite {@code null}. */
  kontextBoard: BoardContext | null
  /** Anzahl sichtbarer Projekte; {@code null} = noch unbekannt. */
  projectCount: number | null
  /** Ob man im Projekt des Boards Boards anlegen/löschen darf. */
  canManageCurrentBoards: boolean
  /** Ob der Nachtlauf-Bereich des Pfad-Projekts sichtbar ist (Owner oder Plattform-Admin). */
  canViewNightRun: boolean
  /** Ob man die Mitglieder des Pfad-Projekts verwalten darf. */
  canManageCurrentMembers: boolean
  /** Name des Pfad-Projekts für den Titel des Projekt-Blocks; {@code null} = unbekannt. */
  projectName: string | null
  /** Name des Projekts des Board-Kontexts — nur für den Board-Verlauf. */
  currentProjectName: string | null
  /** Pfad im Kopf (Entwurf `.pfad`): Projekt, bei offenem Board dahinter das Board. */
  pfad: Array<{ label: string; to: string }>
}

/**
 * Leitet aus Route, geladenem Board und Projektliste ab, was Schiene und Kopf zeigen. Rein und
 * ohne React, damit die Regeln ohne Rendering prüfbar sind — wie {@link buildNavItems}, das die
 * Ergebnisse weiterverarbeitet.
 *
 * Board-Kontext: Auf einer Projektseite zählt nur ein Board desselben Projekts. Der Abgleich
 * gehört in den Render und nicht allein in den Ladeeffekt der Shell — sonst stünde nach einem
 * Projektwechsel für einen Wimpernschlag das Board des vorigen Projekts unter dem Namen des neuen.
 *
 * Bezugsprojekt: Der Nachtlauf-Bereich und der Pfad sind projektweit, nicht board-gebunden. Ohne
 * offenes Board zählt deshalb das Projekt der Route — sonst verschwände der Nachtlauf-Eintrag
 * genau nach dem Klick auf ihn. {@code canManageProject} ist die Semantik von {@code requireOwner}:
 * Owner *oder* Plattform-Admin (Plan #718, A6).
 */
/**
 * Ob der Eintrag „Nachtlaeufe" sichtbar ist.
 *
 * Der echte Projekt-OWNER sieht ihn immer. Ein Plattform-Admin **ohne** diese Rolle nur, wenn das
 * Projekt am Plattform-Leitstand teilnimmt — seit Issue #1079 laesst ihn der Server sonst nicht
 * mehr lesen, und ein Eintrag, der auf einen Fehlertext fuehrt, ist schlechter als keiner.
 *
 * Ist die Teilnahme unbekannt (Projektliste noch nicht geladen, aeltere Antwort ohne das Feld),
 * gilt sie als nicht gegeben: Ein zu frueh gezeigter Eintrag fuehrt ins Leere, ein zu spaet
 * gezeigter erscheint mit der naechsten Antwort.
 */
function darfNachtlaufSehen(rolle: string, admin: boolean, teilnahme: boolean | undefined): boolean {
  return rolle === 'OWNER' || (admin && teilnahme === true)
}

export function navKontext(params: NavKontextParams): NavKontext {
  const { board, routeProjectId, boardId, projects, admin } = params

  const kontextBoard = routeProjectId !== null && board?.projectId !== routeProjectId ? null : board
  const currentProject = kontextBoard ? projects?.find((p) => p.id === kontextBoard.projectId) : undefined
  const pfadProjekt = projects?.find((p) => p.id === (kontextBoard?.projectId ?? routeProjectId))
  // Ohne bekanntes Projekt gilt die schwächste Rolle. Mutationstest: `'VIEWER'` → `''` überlebt hier
  // und in `canManageCurrentBoards` als äquivalenter Mutant — die Rollenhelfer vergleichen gegen
  // `'OWNER'`/`'ADMIN'`, jede andere Zeichenkette wirkt gleich.
  const pfadRolle = pfadProjekt?.role ?? 'VIEWER'

  // Pfad im Kopf (Entwurf `.pfad`, Z. 301–303): Projekt / Board. Auf einer Projektseite nennt er
  // nur das Projekt — er sagt, wo man ist, die Schiene, wohin man kann (#990). Der Abgleich mit
  // `boardId` hält ihn zugleich vom noch geladenen Vorgänger-Board frei.
  const pfad: Array<{ label: string; to: string }> = []
  if (pfadProjekt) {
    pfad.push({ label: pfadProjekt.name, to: `/projects/${pfadProjekt.id}` })
  }
  if (kontextBoard?.id === boardId && kontextBoard) {
    pfad.push({ label: kontextBoard.name, to: `/boards/${kontextBoard.id}` })
  }

  return {
    kontextBoard,
    projectCount: projects?.length ?? null,
    canManageCurrentBoards: canManageBoards(currentProject?.role ?? 'VIEWER', admin),
    canViewNightRun: darfNachtlaufSehen(pfadRolle, admin, pfadProjekt?.dashboardParticipation),
    canManageCurrentMembers: canManageMembers(pfadRolle),
    projectName: pfadProjekt?.name ?? null,
    currentProjectName: currentProject?.name ?? null,
    pfad,
  }
}
