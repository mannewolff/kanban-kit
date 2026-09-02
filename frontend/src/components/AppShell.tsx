import AppBar from '@mui/material/AppBar'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import ButtonBase from '@mui/material/ButtonBase'
import Collapse from '@mui/material/Collapse'
import Divider from '@mui/material/Divider'
import Drawer from '@mui/material/Drawer'
import IconButton from '@mui/material/IconButton'
import List from '@mui/material/List'
import ListItem from '@mui/material/ListItem'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Toolbar from '@mui/material/Toolbar'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import LogoutIcon from '@mui/icons-material/Logout'
import MenuBookIcon from '@mui/icons-material/MenuBook'
import SettingsIcon from '@mui/icons-material/Settings'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Outlet, useLocation, useMatch, useNavigate } from 'react-router-dom'
import { boardsApi } from '../api/boards'
import { projectsApi, type Project } from '../api/projects'
import { APP_NAME } from '../appMeta'
import { useAuth } from '../auth/AuthContext'
import { buildNavItems, type BoardContext, type NavGroup, type NavLink, type NavNode } from '../layout/navItems'
import { canManageBoards, isPlatformAdmin } from '../lib/roles'
import { useBoardHistory, type BoardHistoryEntry } from '../lib/useBoardHistory'
import { useEditMode } from '../lib/EditModeContext'
import { useKeyboardShortcut } from '../lib/useKeyboardShortcut'
import { useRefetchOnFocus } from '../lib/useRefetchOnFocus'
import { BoardSwitcher } from './BoardSwitcher'
import { CardNumberSearch } from './CardNumberSearch'
import { EditModeBanner, EDIT_MODE_BANNER_HEIGHT } from './EditModeBanner'
import { useSnackbar } from './SnackbarProvider'

const DRAWER_WIDTH = 240
const DRAWER_COLLAPSED_WIDTH = 56
/** Höhe der fixen Kopfleiste (MUI-Standard-Toolbar, Desktop). */
const APPBAR_HEIGHT = 64
const STORAGE_KEY = 'sidebar-collapsed'

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

function isGroup(node: NavNode): node is NavGroup {
  return node.kind === 'group'
}

/** Rahmen für angemeldete Bereiche: fixe Kopfleiste + einklappbare, kontextbewusste Seitenleiste. */
export function AppShell() {
  const location = useLocation()
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const [collapsed, setCollapsed] = useState<boolean>(readCollapsed)
  const [board, setBoard] = useState<BoardContext | null>(null)
  const [projects, setProjects] = useState<Project[] | null>(null)
  const [boardCount, setBoardCount] = useState<number | null>(null)
  const [flyout, setFlyout] = useState<{ label: string; anchor: HTMLElement } | null>(null)

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

  useEffect(() => {
    if (boardId == null) {
      setBoard(null)
      setBoardCount(null)
      return
    }
    let cancelled = false
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
  }, [boardId])

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
  const projectCount = projects?.length ?? null
  const currentProject = board ? projects?.find((p) => p.id === board.projectId) : undefined
  const canManageCurrentBoards = canManageBoards(currentProject?.role ?? 'VIEWER', admin)
  const navItems = useMemo(
    () =>
      buildNavItems({
        board,
        isAdmin: admin,
        projectCount,
        boardCount,
        canManageBoards: canManageCurrentBoards,
        projectId: routeProjectId,
      }),
    [board, admin, projectCount, boardCount, canManageCurrentBoards, routeProjectId],
  )

  // ---- Board-Wechsel (#587): Verlauf fortschreiben und das Overlay bedienen ----
  const notify = useSnackbar()
  const { history, recordVisit, remove } = useBoardHistory()
  const [switcherOpen, setSwitcherOpen] = useState(false)

  // Ein Verlaufseintrag entsteht nur aus kohärentem Kontext: Route, geladenes Board und zugeordnetes
  // Projekt müssen dasselbe Board meinen. Beim Wechsel A→B hält `board` noch A, während `boardId`
  // schon B ist — ohne den Abgleich landete A unter der ID von B. Der Projektname kommt aus der
  // Projektliste, weil der `BoardContext` ihn nicht trägt.
  const currentProjectName = currentProject?.name ?? null
  const visit = useMemo<BoardHistoryEntry | null>(
    () =>
      board !== null && board.id === boardId && currentProjectName !== null
        ? { id: board.id, name: board.name, projectName: currentProjectName }
        : null,
    [board, boardId, currentProjectName],
  )
  useEffect(() => {
    if (visit !== null) {
      recordVisit(visit)
    }
  }, [visit, recordVisit])

  // Das Kürzel ist nur scharf, wenn es etwas zu wechseln gibt — bei leerem Verlauf bliebe das
  // Overlay ohnehin unsichtbar (#584), und ein wirkungsloses Kürzel wäre nur verwirrend.
  useKeyboardShortcut('b', history.length > 0, () => setSwitcherOpen(true))

  const [openGroups, setOpenGroups] = useState<Set<string>>(new Set())
  // Gruppe der aktiven Route automatisch aufklappen.
  useEffect(() => {
    setOpenGroups((prev) => {
      const next = new Set(prev)
      for (const node of navItems) {
        if (isGroup(node) && node.children.some((c) => location.pathname.startsWith(c.path))) {
          next.add(node.label)
        }
      }
      return next
    })
  }, [navItems, location.pathname])

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  const toggleCollapsed = () => {
    setCollapsed((prev) => {
      const next = !prev
      writeCollapsed(next)
      return next
    })
  }

  const toggleGroup = (label: string) => {
    setOpenGroups((prev) => {
      const next = new Set(prev)
      if (next.has(label)) next.delete(label)
      else next.add(label)
      return next
    })
  }

  const renderLink = (link: NavLink, indented: boolean) => {
    const Icon = link.icon
    const selected = location.pathname === link.path || location.pathname.startsWith(`${link.path}/`)
    if (collapsed) {
      return (
        <Tooltip key={link.path} title={link.label} placement="right">
          <ListItem disablePadding>
            <ListItemButton
              selected={selected}
              onClick={() => navigate(link.path)}
              aria-label={link.label}
              sx={{ justifyContent: 'center', px: 1 }}
            >
              <ListItemIcon sx={{ minWidth: 0 }}>
                <Icon color={selected ? 'primary' : 'inherit'} />
              </ListItemIcon>
            </ListItemButton>
          </ListItem>
        </Tooltip>
      )
    }
    return (
      <ListItem key={link.path} disablePadding>
        <ListItemButton
          selected={selected}
          onClick={() => navigate(link.path)}
          sx={indented ? { pl: 4 } : undefined}
        >
          <ListItemIcon>
            <Icon color={selected ? 'primary' : 'inherit'} />
          </ListItemIcon>
          <ListItemText primary={link.label} />
        </ListItemButton>
      </ListItem>
    )
  }

  // Doku ist statisch unter /docs/ ausgeliefert (#314), keine SPA-Route -> echter Anker im neuen
  // Tab, nicht navigate(). Steht im abgesetzten Administrations-Bereich (unten).
  const renderDocsLink = () => {
    if (collapsed) {
      return (
        <Tooltip title="Dokumentation" placement="right">
          <ListItem disablePadding>
            <ListItemButton
              component="a"
              href="/docs/"
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Dokumentation"
              sx={{ justifyContent: 'center', px: 1 }}
            >
              <ListItemIcon sx={{ minWidth: 0 }}>
                <MenuBookIcon />
              </ListItemIcon>
            </ListItemButton>
          </ListItem>
        </Tooltip>
      )
    }
    return (
      <ListItem disablePadding>
        <ListItemButton component="a" href="/docs/" target="_blank" rel="noopener noreferrer">
          <ListItemIcon>
            <MenuBookIcon />
          </ListItemIcon>
          <ListItemText primary="Dokumentation" />
        </ListItemButton>
      </ListItem>
    )
  }

  const renderGroup = (group: NavGroup) => {
    const GroupIcon = group.icon
    const expanded = openGroups.has(group.label)
    const hasActiveChild = group.children.some((c) => location.pathname.startsWith(c.path))

    if (collapsed) {
      const flyoutOpen = flyout?.label === group.label
      return (
        <Box key={group.label}>
          <Tooltip title={group.label} placement="right">
            <ListItem disablePadding>
              <ListItemButton
                aria-label={group.label}
                aria-haspopup="menu"
                aria-expanded={flyoutOpen}
                onClick={(e) => setFlyout({ label: group.label, anchor: e.currentTarget })}
                sx={{ justifyContent: 'center', px: 1 }}
              >
                <ListItemIcon sx={{ minWidth: 0 }}>
                  <GroupIcon color={hasActiveChild ? 'primary' : 'inherit'} />
                </ListItemIcon>
              </ListItemButton>
            </ListItem>
          </Tooltip>
          <Menu
            anchorEl={flyout?.anchor ?? null}
            open={flyoutOpen}
            onClose={() => setFlyout(null)}
            anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
            transformOrigin={{ vertical: 'top', horizontal: 'left' }}
          >
            {group.children.map((child) => {
              const ChildIcon = child.icon
              return (
                <MenuItem
                  key={child.path}
                  selected={location.pathname.startsWith(child.path)}
                  onClick={() => {
                    navigate(child.path)
                    setFlyout(null)
                  }}
                >
                  <ListItemIcon>
                    <ChildIcon fontSize="small" />
                  </ListItemIcon>
                  <ListItemText>{child.label}</ListItemText>
                </MenuItem>
              )
            })}
          </Menu>
        </Box>
      )
    }

    return (
      <Box key={group.label}>
        <ListItem disablePadding>
          <ListItemButton onClick={() => toggleGroup(group.label)} aria-expanded={expanded}>
            <ListItemIcon>
              <GroupIcon color={hasActiveChild ? 'primary' : 'inherit'} />
            </ListItemIcon>
            <ListItemText
              primary={group.label}
              slotProps={{
                primary: {
                  sx: {
                    textTransform: 'uppercase',
                    letterSpacing: '.08em',
                    fontSize: 12,
                    fontWeight: 700,
                    color: hasActiveChild ? 'primary.main' : 'text.secondary',
                  },
                },
              }}
            />
            {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
          </ListItemButton>
        </ListItem>
        <Collapse in={expanded} timeout="auto" unmountOnExit>
          <List component="div" disablePadding>
            {group.children.map((child) => renderLink(child, true))}
          </List>
        </Collapse>
      </Box>
    )
  }

  const drawerWidth = collapsed ? DRAWER_COLLAPSED_WIDTH : DRAWER_WIDTH

  const { editMode } = useEditMode()
  // Im Editiermodus liegt der Hinweisstreifen über dem Header; Header und Inhalt weichen um die
  // Bannerhöhe nach unten, damit nichts verdeckt wird.
  const bannerOffset = editMode ? EDIT_MODE_BANNER_HEIGHT : 0

  // Maße des Kontextbereichs (unter der Kopfleiste, rechts der Sidebar) als CSS-Variablen an :root,
  // damit portalbasierte Overlays (z. B. der Kartendetail-Dialog) sich darin positionieren können.
  // Reaktiv zur Drawer-Breite und zum Editiermodus-Banner; Default 0 gilt außerhalb der Shell.
  useEffect(() => {
    const root = document.documentElement
    root.style.setProperty('--app-content-left', `${drawerWidth}px`)
    root.style.setProperty('--app-content-top', `${APPBAR_HEIGHT + bannerOffset}px`)
    return () => {
      root.style.removeProperty('--app-content-left')
      root.style.removeProperty('--app-content-top')
    }
  }, [drawerWidth, bannerOffset])
  const initial = user?.displayName?.trim().charAt(0).toUpperCase() ?? '?'

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <EditModeBanner />
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1, top: `${bannerOffset}px` }}>
        <Toolbar>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexGrow: 1 }}>
            {/* Teal-Einfaerbung als Marken-Echo zur Leiste am Drawer (#653): `knight.svg` traegt
                keinen eigenen Fill und waere auf der jetzt weissen Leiste schwarz. Als Maske
                gerendert nimmt es die Palettenfarbe an. Rein dekorativ, daher aria-hidden. */}
            <Box
              component="span"
              aria-hidden
              sx={{
                width: 22,
                height: 22,
                flexShrink: 0,
                // Dieselbe dunkle Tinte wie die Schrift daneben. Der Marken-Teal `primary.main`
                // stand auf der weissen Leiste gut, auf dem hellen Teal der Leiste waere es Teal
                // auf Teal.
                bgcolor: 'text.primary',
                maskImage: 'url(/knight.svg)',
                maskSize: 'contain',
                maskRepeat: 'no-repeat',
                maskPosition: 'center',
                WebkitMaskImage: 'url(/knight.svg)',
                WebkitMaskSize: 'contain',
                WebkitMaskRepeat: 'no-repeat',
                WebkitMaskPosition: 'center',
              }}
            />
            <Typography variant="h6" noWrap component="div" sx={{ fontWeight: 700, color: 'text.primary' }}>
              {APP_NAME}
            </Typography>
            {/* Kein `opacity`: Die Abschwaechung druecke den Kontrast unter die AA-Schwelle. Auch
                die sekundaere Textfarbe entfaellt -- auf dem hellen Teal der Leiste traegt die
                Versionsangabe dieselbe dunkle Tinte wie der Name. */}
            <Typography variant="body1" noWrap sx={{ color: 'text.primary' }}>
              v{__APP_VERSION__}
            </Typography>
          </Box>
          {user && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              {/* Ein unsichtbares Kürzel gibt es für die Hälfte der Nutzer nicht: Der Knopf ist der
                  sichtbare Zugang und trägt zugleich die Beschriftung, über die `b` bekannt wird.
                  Der Umschlag mit `span` ist nötig, damit der Tooltip auch am deaktivierten Knopf
                  einen Ereignisempfänger hat. */}
              <Tooltip title="Board wechseln (Taste b)">
                <span>
                  <IconButton
                    sx={{ color: 'text.primary' }}
                    aria-label="Board wechseln"
                    disabled={history.length === 0}
                    onClick={() => setSwitcherOpen(true)}
                  >
                    <SwapHorizIcon />
                  </IconButton>
                </span>
              </Tooltip>
              <CardNumberSearch />
              <Tooltip title="Profil bearbeiten">
                <ButtonBase
                  onClick={() => navigate('/profil')}
                  aria-label={`Profil von ${user.displayName} bearbeiten`}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1,
                    borderRadius: (t) => `${t.shape.borderRadius}px`,
                    p: 0.5,
                    color: 'text.primary',
                  }}
                >
                  <Avatar
                    sx={{ bgcolor: 'primary.dark', width: 32, height: 32, fontSize: '0.875rem' }}
                  >
                    {initial}
                  </Avatar>
                  <Typography variant="body2" sx={{ display: { xs: 'none', sm: 'inline' } }}>
                    {user.displayName}
                  </Typography>
                </ButtonBase>
              </Tooltip>
              <Button sx={{ color: 'text.primary' }} startIcon={<LogoutIcon />} onClick={handleLogout} aria-label="Abmelden">
                Abmelden
              </Button>
            </Box>
          )}
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth,
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
            borderLeft: (t) => `8px solid ${t.palette.primary.main}`,
            transition: (t) =>
              t.transitions.create('width', {
                easing: t.transitions.easing.sharp,
                duration: t.transitions.duration.enteringScreen,
              }),
          },
        }}
      >
        <Toolbar sx={{ mt: `${bannerOffset}px` }} />
        <Box sx={{ overflow: 'auto', display: 'flex', flexDirection: 'column', height: '100%' }}>
          <Box sx={{ flexGrow: 1 }}>
            <List>{navItems.map((node) => (isGroup(node) ? renderGroup(node) : renderLink(node, false)))}</List>
          </Box>
          <Box>
            <Divider />
            <List disablePadding>
              {renderLink(ADMINISTRATION_LINK, false)}
              {renderDocsLink()}
            </List>
            <Divider />
            <Box sx={{ display: 'flex', justifyContent: collapsed ? 'center' : 'flex-end', p: 0.5 }}>
              <Tooltip title={collapsed ? 'Menü ausklappen' : 'Menü einklappen'} placement="right">
                <IconButton
                  onClick={toggleCollapsed}
                  size="small"
                  aria-label={collapsed ? 'Menü ausklappen' : 'Menü einklappen'}
                >
                  {collapsed ? <ChevronRightIcon /> : <ChevronLeftIcon />}
                </IconButton>
              </Tooltip>
            </Box>
          </Box>
        </Box>
      </Drawer>

      {/* Ohne eigenen Grund: der getönte Grund der Anwendung (theme.ts, `body::before`) scheint
          durch. Ein `bgcolor` deckte ihn innerhalb der Shell wieder mit Weiß zu. */}
      <Box component="main" sx={{ flexGrow: 1, p: 3, minWidth: 0 }}>
        <Toolbar sx={{ mt: `${bannerOffset}px` }} />
        <Outlet />
      </Box>

      {/* Auf einer Board-Route bestimmt die aktuelle Board-ID die Vorauswahl, sonst gibt es keine
          — das weiß allein die Shell (`boardMatch`). Ein als 403/404 gemeldetes Ziel fliegt aus
          dem Verlauf; der Fehler-Catch des Board-Abrufs oben bleibt davon unberührt. */}
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
