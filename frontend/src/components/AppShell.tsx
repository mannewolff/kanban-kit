import AppBar from '@mui/material/AppBar'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
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
import { useEffect, useMemo, useState } from 'react'
import { Outlet, useLocation, useMatch, useNavigate } from 'react-router-dom'
import { boardsApi } from '../api/boards'
import { projectsApi, type Project } from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { buildNavItems, type BoardContext, type NavGroup, type NavLink, type NavNode } from '../layout/navItems'
import { canManageBoards, isPlatformAdmin } from '../lib/roles'

const DRAWER_WIDTH = 240
const DRAWER_COLLAPSED_WIDTH = 56
const STORAGE_KEY = 'sidebar-collapsed'

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
  // Ein einzelnes Muster mit Splat matcht sowohl /boards/:id als auch /boards/:id/epics.
  // (Kein `useMatch(a) ?? useMatch(b)` — der `??`-Short-Circuit würde den zweiten Hook
  // bedingt aufrufen und die Rules of Hooks verletzen.)
  const boardMatch = useMatch('/boards/:boardId/*')
  const boardId = boardMatch?.params.boardId ? Number(boardMatch.params.boardId) : null

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

  // An abgeleitete Primitive binden, nicht an Objektidentitäten (sonst rechnet useMemo bei jeder
  // neuen user-Referenz neu und die openGroups-Effect-Schleife läuft endlos).
  const admin = isPlatformAdmin(user)
  const projectCount = projects?.length ?? null
  const currentProject = board ? projects?.find((p) => p.id === board.projectId) : undefined
  const canManageCurrentBoards = canManageBoards(currentProject?.role ?? 'VIEWER', admin)
  const navItems = useMemo(
    () => buildNavItems({ board, isAdmin: admin, projectCount, boardCount, canManageBoards: canManageCurrentBoards }),
    [board, admin, projectCount, boardCount, canManageCurrentBoards],
  )

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
              primaryTypographyProps={{
                sx: {
                  textTransform: 'uppercase',
                  letterSpacing: '.08em',
                  fontSize: 12,
                  fontWeight: 700,
                  color: hasActiveChild ? 'primary.main' : 'text.secondary',
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
  const initial = user?.displayName?.trim().charAt(0).toUpperCase() ?? '?'

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexGrow: 1 }}>
            <Box component="img" src="/knight.svg" alt="" sx={{ width: 22, height: 22, filter: 'brightness(0) invert(1)' }} />
            <Typography variant="h6" noWrap component="div" sx={{ fontWeight: 700 }}>
              kanban-kit
            </Typography>
          </Box>
          {user && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Avatar
                sx={{ bgcolor: 'primary.dark', width: 32, height: 32, fontSize: '0.875rem' }}
                aria-label={`Angemeldet als ${user.displayName}`}
              >
                {initial}
              </Avatar>
              <Typography variant="body2" sx={{ display: { xs: 'none', sm: 'inline' } }}>
                {user.displayName}
              </Typography>
              <Button color="inherit" startIcon={<LogoutIcon />} onClick={handleLogout} aria-label="Abmelden">
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
            borderLeft: (t) => `4px solid ${t.palette.primary.main}`,
            transition: (t) =>
              t.transitions.create('width', {
                easing: t.transitions.easing.sharp,
                duration: t.transitions.duration.enteringScreen,
              }),
          },
        }}
      >
        <Toolbar />
        <Box sx={{ overflow: 'auto', display: 'flex', flexDirection: 'column', height: '100%' }}>
          <Box sx={{ flexGrow: 1 }}>
            <List>{navItems.map((node) => (isGroup(node) ? renderGroup(node) : renderLink(node, false)))}</List>
          </Box>
          <Box>
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

      <Box component="main" sx={{ flexGrow: 1, p: 3, bgcolor: 'background.default', minWidth: 0 }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  )
}
