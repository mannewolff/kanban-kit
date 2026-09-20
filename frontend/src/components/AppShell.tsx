import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import ButtonBase from '@mui/material/ButtonBase'
import Drawer from '@mui/material/Drawer'
import IconButton from '@mui/material/IconButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Tooltip from '@mui/material/Tooltip'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme, type Theme } from '@mui/material/styles'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import LogoutIcon from '@mui/icons-material/Logout'
import MenuBookIcon from '@mui/icons-material/MenuBook'
import MenuIcon from '@mui/icons-material/Menu'
import PersonOutlineIcon from '@mui/icons-material/PersonOutline'
import SettingsIcon from '@mui/icons-material/Settings'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import { Fragment, type MouseEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link as RouterLink, Outlet, useLocation, useMatch, useNavigate } from 'react-router-dom'
import { boardsApi } from '../api/boards'
import { apiErrorMessage } from '../api/client'
import { projectsApi, type Project } from '../api/projects'
import { APP_NAME } from '../appMeta'
import { useAuth } from '../auth/AuthContext'
import { MarkenSymbol } from '../layout/navIcons'
import { buildNavItems, navKontext, type BoardContext, type NavGroup, type NavLink } from '../layout/navItems'
import { isPlatformAdmin } from '../lib/roles'
import { lastBoardOfProject, useBoardHistory, type BoardHistoryEntry } from '../lib/useBoardHistory'
import { useEditMode } from '../lib/EditModeContext'
import { useKeyboardShortcut } from '../lib/useKeyboardShortcut'
import { useRefetchOnFocus } from '../lib/useRefetchOnFocus'
import {
  ANZEIGE,
  CARD_RADIUS,
  ETIKETT,
  GRUND_TIEF,
  HEADER_BG,
  KUPFER,
  MARKE_MAL_SX,
  NUT,
  NUTZER_MAL_SX,
  PLATTE,
  PLATTE_HOCH,
  RAND,
  SCHATTEN_NUTE,
  SCHATTEN_TASTE,
  SCHRIFT_ANZEIGE,
  TEXT_SCHWACH,
} from '../theme'
import { BoardSwitcher } from './BoardSwitcher'
import { CardNumberSearch } from './CardNumberSearch'
import { EditModeBanner, EDIT_MODE_BANNER_HEIGHT } from './EditModeBanner'
import { useSnackbar } from './SnackbarProvider'

/** Breite der Schiene (Entwurf `.warte`, Z. 199). */
const DRAWER_WIDTH = 224
/** Eingeklappt: Innenabstand 14 + Eintrag (10 + 16 + 10) + 14 — nur die Symbole. */
const DRAWER_COLLAPSED_WIDTH = 64
/** Höhe des Kopfs: 14 px Innenabstand oben und unten um das 30-px-Nutzer-Mal, dazu die Haarlinie. */
const KOPF_HEIGHT = 59
const STORAGE_KEY = 'sidebar-collapsed'

/**
 * Sprungmarke zum Inhalt (AK 15): erstes Tastaturziel der Seite, außerhalb des sichtbaren Bereichs,
 * bis sie den Fokus hat. Nicht `display: none` — dann wäre sie gar nicht fokussierbar.
 */
const SPRUNGMARKE_SX = {
  position: 'absolute',
  left: 16,
  top: -64,
  zIndex: (t: Theme) => t.zIndex.tooltip + 1,
  px: 2,
  py: 1,
  borderRadius: 1,
  bgcolor: 'background.paper',
  color: 'text.primary',
  fontWeight: 700,
  '&:focus': { top: 8 },
} as const

/**
 * Fester, vom kontextuellen Navigationsbaum abgesetzter Eintrag am unteren Rand der Seitenleiste.
 * Immer sichtbar; führt auf die Administrations-/Einstellungsseite. Bewusst nicht in
 * {@link buildNavItems}, da er positionell (unten) und semantisch getrennt und stets präsent ist.
 */
const ADMINISTRATION_LINK: NavLink = {
  kind: 'link',
  label: 'Administration',
  path: '/administration',
  icon: SettingsIcon,
}

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

function writeCollapsed(value: boolean): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(value))
  } catch {
    // localStorage nicht verfügbar — kein Hard-Fail
  }
}

/**
 * Eintrag der Schiene (Entwurf `.nav-eintrag`, Z. 245–268): Symbol, Beschriftung; der aktive
 * Eintrag ist eine erhabene Taste mit kupfernem Symbol. Eingeklappt bleibt nur das Symbol.
 */
const NAV_EINTRAG_SX = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'flex-start',
  gap: '10px',
  px: '10px',
  py: '7px',
  borderRadius: `${CARD_RADIUS}px`,
  color: 'text.secondary',
  fontSize: 13,
  fontWeight: 500,
  lineHeight: 1.5,
  textDecoration: 'none',
  border: '1px solid transparent',
  transition: 'background .14s ease, color .14s ease',
  '& .nav-icon': { width: 16, height: 16, flex: 'none', color: TEXT_SCHWACH },
  '&:hover': { bgcolor: `color-mix(in srgb, ${PLATTE} 70%, transparent)`, color: 'text.primary' },
  '&[aria-current="page"]': {
    background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})`,
    borderColor: RAND,
    boxShadow: SCHATTEN_TASTE,
    color: 'text.primary',
  },
  '&[aria-current="page"] .nav-icon': { color: KUPFER },
} as const

interface SchieneProps {
  /** Die Blöcke der Navigation, wie {@link buildNavItems} sie liefert. */
  navItems: NavGroup[]
  /** Nur Symbole statt Symbol und Beschriftung (breiter Zweig, eingeklappt). */
  eingeklappt: boolean
  /** Der gespeicherte Einklapp-Wunsch — im schmalen Zweig ohne Wirkung, er steuert nur die Taste. */
  collapsed: boolean
  /** Schmaler Zweig: die Schiene liegt als Schublade über dem Inhalt, die Einklapp-Taste entfällt. */
  schmal: boolean
  onZielWaehlen: (event: MouseEvent<HTMLAnchorElement>, pfad: string) => void
  onToggleCollapsed: () => void
}

/**
 * Die Navigationsschiene (Entwurf `.warte`, Z. 199–272): Marke, die Blöcke aus
 * {@link buildNavItems} und der Fuß mit Administration, Dokumentation und Einklapp-Taste.
 * Eigene Komponente, weil die Schiene für sich steht — die Shell reicht ihr nur Zustand an.
 */
function Schiene({ navItems, eingeklappt, collapsed, schmal, onZielWaehlen, onToggleCollapsed }: SchieneProps) {
  const location = useLocation()

  // Aktiv ist der Eintrag mit dem längsten passenden Pfad: Auf `/boards/1/list` passt „Board"
  // (`/boards/1`) als Präfix ebenso wie „Liste" — gemeint ist nur die Liste.
  const aktiverPfad = [...navItems.flatMap((block) => block.children), ADMINISTRATION_LINK]
    .map((link) => link.path)
    .filter((pfad) => location.pathname === pfad || (pfad !== '/' && location.pathname.startsWith(`${pfad}/`)))
    .sort((a, b) => b.length - a.length)[0]

  const renderLink = (link: NavLink) => {
    const Icon = link.icon
    const aktiv = link.path === aktiverPfad
    const eintrag = (
      <ButtonBase
        key={link.path}
        component="a"
        href={link.path}
        onClick={(event: MouseEvent<HTMLAnchorElement>) => onZielWaehlen(event, link.path)}
        aria-current={aktiv ? 'page' : undefined}
        aria-label={eingeklappt ? link.label : undefined}
        sx={{ ...NAV_EINTRAG_SX, ...(eingeklappt && { justifyContent: 'center', px: 0 }) }}
      >
        <Icon className="nav-icon" />
        {!eingeklappt && link.label}
      </ButtonBase>
    )
    return eingeklappt ? (
      <Tooltip key={link.path} title={link.label} placement="right">
        {eintrag}
      </Tooltip>
    ) : (
      eintrag
    )
  }

  // Doku ist statisch unter /docs/ ausgeliefert (#314), keine SPA-Route -> echter Anker im neuen Tab.
  const docsLink = (
    <ButtonBase
      component="a"
      href="/docs/"
      target="_blank"
      rel="noopener noreferrer"
      aria-label={eingeklappt ? 'Dokumentation' : undefined}
      sx={{ ...NAV_EINTRAG_SX, ...(eingeklappt && { justifyContent: 'center', px: 0 }) }}
    >
      <MenuBookIcon className="nav-icon" />
      {!eingeklappt && 'Dokumentation'}
    </ButtonBase>
  )

  return (
    <Box
      component="nav"
      aria-label="Hauptnavigation"
      sx={{
        minHeight: '100%',
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        gap: '22px',
        pt: '18px',
        pb: '24px',
        px: '14px',
        overflowX: 'hidden',
        overflowY: 'auto',
      }}
    >
      {/* Marke (Entwurf `.marke`, Z. 211–236). */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px', px: eingeklappt ? 0 : '4px', justifyContent: eingeklappt ? 'center' : 'flex-start' }}>
        <Box
          component="span"
          aria-hidden
          sx={{ ...MARKE_MAL_SX, width: 30, height: 30, borderRadius: '9px', display: 'grid', placeItems: 'center', flex: 'none' }}
        >
          <MarkenSymbol />
        </Box>
        {!eingeklappt && (
          <Box component="span" sx={{ lineHeight: 1.15, minWidth: 0 }}>
            <Box
              component="span"
              sx={{ display: 'block', fontFamily: SCHRIFT_ANZEIGE, fontStretch: '118%', fontWeight: 700, fontSize: 14, letterSpacing: '-.01em' }}
            >
              {APP_NAME}
            </Box>
            <Box component="span" sx={{ display: 'block', fontSize: 10.5, color: TEXT_SCHWACH, letterSpacing: '.04em' }}>
              v{__APP_VERSION__}
            </Box>
          </Box>
        )}
      </Box>

      {navItems.map((block) => (
        <Box key={block.id} role="group" aria-label={block.label} sx={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
          {eingeklappt ? (
            <Box aria-hidden sx={{ height: '1px', bgcolor: RAND, mx: '6px', mb: '4px' }} />
          ) : (
            <Box component="span" sx={{ ...ETIKETT, px: '8px', pb: '7px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {block.label}
            </Box>
          )}
          {block.children.map((link) => (
            <Fragment key={link.path}>{renderLink(link)}</Fragment>
          ))}
        </Box>
      ))}

      {/* Fuß der Schiene (Entwurf `.schiene-fuss`, Z. 272): Administration, Dokumentation, Einklappen. */}
      <Box sx={{ mt: 'auto', display: 'flex', flexDirection: 'column', gap: '3px' }}>
        {renderLink(ADMINISTRATION_LINK)}
        {eingeklappt ? (
          <Tooltip title="Dokumentation" placement="right">
            {docsLink}
          </Tooltip>
        ) : (
          docsLink
        )}
        {!schmal && (
          <Box sx={{ display: 'flex', justifyContent: collapsed ? 'center' : 'flex-end', pt: '6px' }}>
            <Tooltip title={collapsed ? 'Menü ausklappen' : 'Menü einklappen'} placement="right">
              <IconButton
                onClick={onToggleCollapsed}
                size="small"
                aria-label={collapsed ? 'Menü ausklappen' : 'Menü einklappen'}
                sx={{ color: TEXT_SCHWACH }}
              >
                {collapsed ? <ChevronRightIcon fontSize="small" /> : <ChevronLeftIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </Box>
        )}
      </Box>
    </Box>
  )
}

/** Rahmen für angemeldete Bereiche: Schiene links, Kopf und Bühne rechts (Entwurf Z. 196–389). */
export function AppShell() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const [collapsed, setCollapsed] = useState<boolean>(readCollapsed)
  // Mindestbreite 768 px (Plan #932 E5): Unterhalb des Breakpoints `md` (900 px) wird die Navigation
  // zur temporären Schublade hinter einer Schaltfläche, damit 768 vollständig im schmalen Zweig liegt.
  // `noSsr`, damit der erste Render schon den richtigen Zweig trägt statt kurz den breiten.
  const muiTheme = useTheme()
  const schmal = useMediaQuery(muiTheme.breakpoints.down('md'), { noSsr: true })
  const [navOffen, setNavOffen] = useState(false)
  const [kontoAnker, setKontoAnker] = useState<HTMLElement | null>(null)
  // Eingeklappt gibt es nur im breiten Zweig; die Schublade zeigt immer die volle Navigation.
  const eingeklappt = collapsed && !schmal
  const inhaltRef = useRef<HTMLElement>(null)
  const [board, setBoard] = useState<BoardContext | null>(null)
  const [projects, setProjects] = useState<Project[] | null>(null)
  const [boardCount, setBoardCount] = useState<number | null>(null)

  // Projektliste für die Sichtbarkeit von „Projekte" (Anzahl) und die Board-Verwaltungsrolle.
  useEffect(() => {
    projectsApi.list().then(setProjects).catch(() => setProjects(null))
  }, [])

  // Board-Kontext für die Seitenleiste: auf einer Board-Route den Namen nachladen.
  // Ein einzelnes Muster mit Splat matcht sowohl /boards/:id als auch /boards/:id/vorhaben.
  // (Kein `useMatch(a) ?? useMatch(b)` — der `??`-Short-Circuit würde den zweiten Hook
  // bedingt aufrufen und die Rules of Hooks verletzen.)
  const boardMatch = useMatch('/boards/:boardId/*')
  const boardId = boardMatch?.params.boardId ? Number(boardMatch.params.boardId) : null

  // Projekt-Kontext auch ohne offenes Board (Boards-/Ideen-/Mitglieder-Seite), damit der
  // projektweite „Ideen"-Link auch dort sichtbar/aktiv ist. Das Splat matcht /projects/:id ebenso
  // wie /projects/:id/ideas. Auf Board-Routen ist dieser Match null — dort liefert board.projectId.
  const projectMatch = useMatch('/projects/:projectId/*')
  const routeProjectId = projectMatch?.params.projectId ? Number(projectMatch.params.projectId) : null

  // Der Verlauf steht schon hier, weil der Board-Kontext einer Projektseite aus ihm kommt (#990).
  const { history, recordVisit, remove } = useBoardHistory()

  // Spiegel von Board-Kontext und Verlauf für den Effekt darunter: Der soll auf den Routenwechsel
  // anspringen, nicht auf jede neue Verlaufsreferenz — und sich nicht selbst neu anstoßen, wenn er
  // den Kontext setzt. Schon mit dem ersten Render belegt (Muster wie in `useBoardHistory`), damit
  // der erste Lauf den gespeicherten Verlauf sieht und nicht einen leeren.
  const letzterKontext = useRef({ board, history })
  useEffect(() => {
    letzterKontext.current = { board, history }
  }, [board, history])

  /*
   * Board-Kontext der Schiene in drei Fällen:
   *
   * - **Board-Route** — das Board der Route laden.
   * - **Projektseite** — wer von einem Board auf eine Seite desselben Projekts geht, ist weiter in
   *   diesem Projekt: Der Kontext bleibt unangetastet stehen. Ohne ihn (Lesezeichen, Neuladen,
   *   anderes Projekt) entscheidet der Verlauf — das zuletzt besuchte Board dieses Projekts —, sonst
   *   das erste Board des Projekts; hat das Projekt keines, bleibt es bei keinem Board (#990).
   * - **sonst** — kein Board-Kontext.
   */
  useEffect(() => {
    let cancelled = false
    if (boardId != null) {
      boardsApi
        .get(boardId)
        .then((b) => {
          if (cancelled) return
          setBoard({ id: b.id, name: b.name, projectId: b.projectId })
          // Anzahl Boards im Projekt für die Sichtbarkeit des „Boards"-Eintrags.
          boardsApi
            .list(b.projectId)
            .then((bs) => {
              if (!cancelled) setBoardCount(bs.length)
            })
            .catch(() => {
              if (!cancelled) setBoardCount(null)
            })
        })
        .catch(() => {
          if (!cancelled) {
            setBoard(null)
            setBoardCount(null)
          }
        })
      return () => {
        cancelled = true
      }
    }
    if (routeProjectId == null) {
      setBoard(null)
      setBoardCount(null)
      return
    }
    if (letzterKontext.current.board?.projectId === routeProjectId) {
      return
    }
    // Die Boardliste liefert beides: das Ziel der Board-Einträge und ihre Anzahl. Der Verlauf
    // entscheidet nur, welches der vorhandenen Boards gemeint ist — ein inzwischen gelöschtes
    // steht nicht darin und fällt so von selbst auf das erste Board zurück.
    boardsApi
      .list(routeProjectId)
      .then((bs) => {
        if (cancelled) return
        const zuletzt = lastBoardOfProject(letzterKontext.current.history, routeProjectId)
        const gewaehlt = bs.find((b) => b.id === zuletzt?.id) ?? bs[0]
        setBoard(gewaehlt ? { id: gewaehlt.id, name: gewaehlt.name, projectId: gewaehlt.projectId } : null)
        setBoardCount(bs.length)
      })
      .catch(() => {
        if (!cancelled) {
          setBoard(null)
          setBoardCount(null)
        }
      })
    return () => {
      cancelled = true
    }
  }, [boardId, routeProjectId])

  // Beim Zurückkehren in den Tab Projekt- und Board-Kontext neu laden, damit die Seitenleiste
  // nicht auf einem in einer anderen Session veränderten Stand (z. B. entferntes Board) verharrt.
  const refetchOnFocus = useCallback(() => {
    projectsApi.list().then(setProjects).catch(() => setProjects(null))
    if (boardId == null) {
      return
    }
    boardsApi
      .get(boardId)
      .then((b) => {
        setBoard({ id: b.id, name: b.name, projectId: b.projectId })
        boardsApi
          .list(b.projectId)
          .then((bs) => setBoardCount(bs.length))
          .catch(() => setBoardCount(null))
      })
      .catch(() => {
        setBoard(null)
        setBoardCount(null)
      })
  }, [boardId])
  useRefetchOnFocus(refetchOnFocus)

  // An abgeleitete Primitive binden, nicht an Objektidentitäten (sonst rechnet useMemo bei jeder
  // neuen user-Referenz neu und die openGroups-Effect-Schleife läuft endlos).
  const admin = isPlatformAdmin(user)
  // Was Schiene und Kopf aus Route, geladenem Board und Projektliste ableiten, rechnet `navKontext`
  // rein und ohne React (geprüft in `layout/navItems.test.ts`).
  const {
    kontextBoard,
    projectCount,
    canManageCurrentBoards,
    canViewNightRun,
    canManageCurrentMembers,
    projectName,
    currentProjectName,
    pfad,
  } = useMemo(
    () => navKontext({ board, routeProjectId, boardId, projects, admin }),
    [board, routeProjectId, boardId, projects, admin],
  )
  const navItems = useMemo(
    () =>
      buildNavItems({
        board: kontextBoard,
        isAdmin: admin,
        projectCount,
        boardCount,
        canManageBoards: canManageCurrentBoards,
        projectId: routeProjectId,
        canViewNightRun,
        projectName,
        canManageMembers: canManageCurrentMembers,
      }),
    [
      kontextBoard,
      admin,
      projectCount,
      boardCount,
      canManageCurrentBoards,
      routeProjectId,
      canViewNightRun,
      projectName,
      canManageCurrentMembers,
    ],
  )

  // ---- Board-Wechsel (#587): Verlauf fortschreiben und das Overlay bedienen ----
  const notify = useSnackbar()
  const [switcherOpen, setSwitcherOpen] = useState(false)

  // Ein Verlaufseintrag entsteht nur aus kohärentem Kontext: Route, geladenes Board und zugeordnetes
  // Projekt müssen dasselbe Board meinen. Beim Wechsel A→B hält `board` noch A, während `boardId`
  // schon B ist — ohne den Abgleich landete A unter der ID von B. Der Projektname kommt aus der
  // Projektliste, weil der `BoardContext` ihn nicht trägt.
  const visit = useMemo<BoardHistoryEntry | null>(
    () =>
      kontextBoard !== null && kontextBoard.id === boardId && currentProjectName !== null
        ? {
            id: kontextBoard.id,
            name: kontextBoard.name,
            projectId: kontextBoard.projectId,
            projectName: currentProjectName,
          }
        : null,
    [kontextBoard, boardId, currentProjectName],
  )
  useEffect(() => {
    if (visit !== null) {
      recordVisit(visit)
    }
  }, [visit, recordVisit])

  // Das Kürzel ist nur scharf, wenn es etwas zu wechseln gibt — bei leerem Verlauf bliebe das
  // Overlay ohnehin unsichtbar (#584), und ein wirkungsloses Kürzel wäre nur verwirrend.
  useKeyboardShortcut('b', history.length > 0, () => setSwitcherOpen(true))

  // `navigate` bleibt im Erfolgszweig: Scheitert das Abmelden, ist der Nutzer weiter angemeldet und
  // bleibt auf der Seite, statt vor eine Login-Maske gestellt zu werden.
  const handleLogout = async () => {
    try {
      await logout()
      navigate('/login')
    } catch (e) {
      notify(apiErrorMessage(e, 'Abmelden fehlgeschlagen.'), 'error')
    }
  }

  const toggleCollapsed = () => {
    setCollapsed((prev) => {
      const next = !prev
      writeCollapsed(next)
      return next
    })
  }

  /**
   * Navigiert in der Anwendung und schließt die Schublade des schmalen Zweigs — dort läge sie sonst
   * über dem Ziel. Ein Klick mit Zusatztaste oder mittlerer Maustaste bleibt beim Browser (neuer Tab).
   */
  const zielWaehlen = (event: MouseEvent<HTMLAnchorElement>, pfad: string) => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
      return
    }
    event.preventDefault()
    navigate(pfad)
    setNavOffen(false)
  }

  const drawerWidth = eingeklappt ? DRAWER_COLLAPSED_WIDTH : DRAWER_WIDTH
  // Dialoge versetzen sich um die Breite, die der Drawer tatsächlich einnimmt. Die Schublade des
  // schmalen Zweigs liegt über dem Inhalt und nimmt keine ein — sonst hingen CardDetailModal und
  // NewCardModal bei 768 px rechts versetzt.
  const inhaltLinks = schmal ? 0 : drawerWidth

  const { editMode } = useEditMode()
  // Im Editiermodus liegt der Hinweisstreifen über dem Header; Header und Inhalt weichen um die
  // Bannerhöhe nach unten, damit nichts verdeckt wird.
  const bannerOffset = editMode ? EDIT_MODE_BANNER_HEIGHT : 0

  // Maße des Kontextbereichs (unter der Kopfleiste, rechts der Sidebar) als CSS-Variablen an :root,
  // damit portalbasierte Overlays (z. B. der Kartendetail-Dialog) sich darin positionieren können.
  // Reaktiv zur Drawer-Breite und zum Editiermodus-Banner; Default 0 gilt außerhalb der Shell.
  useEffect(() => {
    const root = document.documentElement
    root.style.setProperty('--app-content-left', `${inhaltLinks}px`)
    root.style.setProperty('--app-content-top', `${KOPF_HEIGHT + bannerOffset}px`)
    return () => {
      root.style.removeProperty('--app-content-left')
      root.style.removeProperty('--app-content-top')
    }
  }, [inhaltLinks, bannerOffset])
  // Kürzel des Nutzers wie im Entwurf: die Anfangsbuchstaben der ersten beiden Namensteile.
  const kuerzel =
    user?.displayName
      ?.trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((teil) => teil.charAt(0).toUpperCase())
      .join('') || '?'

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Box
        component="a"
        href="#inhalt"
        sx={SPRUNGMARKE_SX}
        onClick={(e) => {
          // Der Browser scrollte nur zum Anker; der Fokus bliebe oben, und die nächste Tab-Taste
          // begänne wieder beim Kopf.
          e.preventDefault()
          inhaltRef.current?.focus()
        }}
      >
        Zum Inhalt springen
      </Box>
      <EditModeBanner />

      {/* Schiene (Entwurf `.schiene`, Z. 204–212): eingelassene Nut mit Verlauf und Innenschatten. */}
      <Drawer
        variant={schmal ? 'temporary' : 'permanent'}
        open={schmal ? navOffen : true}
        onClose={() => setNavOffen(false)}
        sx={{
          width: schmal ? undefined : drawerWidth,
          flexShrink: 0,
          transition: (t) =>
            t.transitions.create('width', {
              easing: t.transitions.easing.sharp,
              duration: t.transitions.duration.enteringScreen,
            }),
          [`& .MuiDrawer-paper`]: {
            width: drawerWidth,
            boxSizing: 'border-box',
            overflowX: 'hidden',
            top: `${bannerOffset}px`,
            height: `calc(100% - ${bannerOffset}px)`,
            background: `linear-gradient(180deg, ${GRUND_TIEF}, ${NUT})`,
            borderRight: `1px solid ${RAND}`,
            boxShadow: SCHATTEN_NUTE,
            transition: (t) =>
              t.transitions.create('width', {
                easing: t.transitions.easing.sharp,
                duration: t.transitions.duration.enteringScreen,
              }),
          },
        }}
      >
        <Schiene
          navItems={navItems}
          eingeklappt={eingeklappt}
          collapsed={collapsed}
          schmal={schmal}
          onZielWaehlen={zielWaehlen}
          onToggleCollapsed={toggleCollapsed}
        />
      </Drawer>

      <Box sx={{ flexGrow: 1, minWidth: 0, display: 'flex', flexDirection: 'column', pt: `${bannerOffset}px` }}>
        {/* Kopf (Entwurf `.kopf`, Z. 284–296): klebt oben, leicht getönt, mit Weichzeichner. Keine
            Ansichtswahl — die Ansichten stehen in der Schiene (Entscheidung Manne, #978). */}
        <Box
          component="header"
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
            px: { xs: '16px', md: '26px' },
            minHeight: KOPF_HEIGHT,
            boxSizing: 'border-box',
            borderBottom: `1px solid ${RAND}`,
            bgcolor: HEADER_BG,
            backdropFilter: 'blur(10px)',
            position: 'sticky',
            top: `${bannerOffset}px`,
            zIndex: (t) => t.zIndex.appBar,
            color: 'text.primary',
          }}
        >
          {schmal && (
            <IconButton
              edge="start"
              sx={{ color: 'text.primary' }}
              aria-label="Navigation öffnen"
              aria-expanded={navOffen}
              onClick={() => setNavOffen(true)}
            >
              <MenuIcon />
            </IconButton>
          )}
          <Box
            component="nav"
            aria-label="Pfad"
            sx={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 13, color: 'text.secondary', minWidth: 0 }}
          >
            {pfad.map((teil, i) => (
              <Fragment key={`${teil.label}-${i}`}>
                {i > 0 && (
                  <Box component="span" aria-hidden sx={{ color: TEXT_SCHWACH }}>
                    /
                  </Box>
                )}
                <Box
                  component={RouterLink}
                  to={teil.to}
                  sx={{
                    ...ANZEIGE,
                    fontStretch: '110%',
                    fontWeight: 600,
                    color: 'text.primary',
                    textDecoration: 'none',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    '&:hover': { textDecoration: 'underline' },
                  }}
                >
                  {teil.label}
                </Box>
              </Fragment>
            ))}
          </Box>
          {user && (
            <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: '10px' }}>
              <CardNumberSearch />
              {/* Ein unsichtbares Kürzel gibt es für die Hälfte der Nutzer nicht: Die Taste ist der
                  sichtbare Zugang und trägt zugleich die Beschriftung, über die `b` bekannt wird.
                  Der Umschlag mit `span` gibt dem Tooltip auch an der deaktivierten Taste einen
                  Ereignisempfänger. */}
              <Tooltip title="Board wechseln (Taste b)">
                <span>
                  <Button
                    variant="outlined"
                    startIcon={<SwapHorizIcon />}
                    aria-label="Board wechseln"
                    disabled={history.length === 0}
                    onClick={() => setSwitcherOpen(true)}
                    sx={{ color: 'text.primary' }}
                  >
                    Board
                  </Button>
                </span>
              </Tooltip>
              {/* Nutzer-Mal (Entwurf `.nutzer`): öffnet Profil und Abmelden, statt beides als eigene
                  Tasten in den Kopf zu stellen. */}
              <Tooltip title={user.displayName}>
                <ButtonBase
                  onClick={(e) => setKontoAnker(e.currentTarget)}
                  aria-label={`Konto von ${user.displayName}`}
                  aria-haspopup="menu"
                  aria-expanded={kontoAnker !== null}
                  sx={{
                    ...NUTZER_MAL_SX,
                    width: 30,
                    height: 30,
                    borderRadius: '50%',
                    fontSize: 11,
                    fontWeight: 600,
                    flex: 'none',
                  }}
                >
                  {kuerzel}
                </ButtonBase>
              </Tooltip>
              <Menu
                anchorEl={kontoAnker}
                open={kontoAnker !== null}
                onClose={() => setKontoAnker(null)}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                transformOrigin={{ vertical: 'top', horizontal: 'right' }}
                slotProps={{ list: { 'aria-label': `Konto von ${user.displayName}` } }}
              >
                <MenuItem
                  onClick={() => {
                    setKontoAnker(null)
                    navigate('/profil')
                  }}
                >
                  <ListItemIcon>
                    <PersonOutlineIcon fontSize="small" />
                  </ListItemIcon>
                  <ListItemText>Profil bearbeiten</ListItemText>
                </MenuItem>
                <MenuItem
                  onClick={() => {
                    setKontoAnker(null)
                    void handleLogout()
                  }}
                >
                  <ListItemIcon>
                    <LogoutIcon fontSize="small" />
                  </ListItemIcon>
                  <ListItemText>Abmelden</ListItemText>
                </MenuItem>
              </Menu>
            </Box>
          )}
        </Box>

        {/* Bühne (Entwurf `.buehne`, Z. 386–389). Ohne eigenen Grund: Der Grund der Anwendung
            (theme.ts, `body::before`) scheint durch. Ziel der Sprungmarke: `tabIndex={-1}` macht
            `main` per Skript fokussierbar, ohne es in die Tab-Reihenfolge zu legen. */}
        <Box
          component="main"
          id="inhalt"
          ref={inhaltRef}
          tabIndex={-1}
          sx={{ flexGrow: 1, pt: '22px', px: { xs: '16px', md: '26px' }, pb: '44px', minWidth: 0, '&:focus': { outline: 'none' } }}
        >
          <Outlet />
        </Box>
      </Box>

      {/* Auf einer Board-Route bestimmt die aktuelle Board-ID die Vorauswahl, sonst gibt es keine
          — das weiß allein die Shell (`boardMatch`). */}
      <BoardSwitcher
        open={switcherOpen}
        entries={history}
        currentBoardId={boardId}
        onClose={() => setSwitcherOpen(false)}
        onRemoveEntry={remove}
        onNotify={notify}
      />
    </Box>
  )
}
