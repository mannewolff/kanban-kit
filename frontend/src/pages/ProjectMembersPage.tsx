import EditIcon from '@mui/icons-material/Edit'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Link from '@mui/material/Link'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { membersApi as defaultMembersApi, type Member, type MembersApi } from '../api/members'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { projectsApi } from '../api/projects'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { canManageMembers, type ProjectRole } from '../lib/roles'

const ROLES: ProjectRole[] = ['OWNER', 'ADMIN', 'MEMBER', 'VIEWER']

interface Props {
  api?: MembersApi
  loadRole?: (projectId: number) => Promise<string>
}

export function ProjectMembersPage({ api = defaultMembersApi, loadRole }: Props) {
  const { projectId } = useParams()
  const id = Number.parseInt(projectId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const { user } = useAuth()
  const [members, setMembers] = useState<Member[]>([])
  const [role, setRole] = useState<string>('VIEWER')
  const [projectName, setProjectName] = useState<string | null>(null)
  const [transferTarget, setTransferTarget] = useState<Member | null>(null)
  const [transferError, setTransferError] = useState<string | null>(null)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState<ProjectRole>('MEMBER')
  const [message, setMessage] = useState<{ kind: 'success' | 'error'; text: string } | null>(null)
  const [inviting, setInviting] = useState(false)
  const [editingName, setEditingName] = useState<{ userId: number; name: string } | null>(null)

  const reload = () => api.list(id).then(setMembers)

  useEffect(() => {
    if (!validId) {
      return
    }
    let active = true
    void api.list(id).then((ms) => {
      if (active) setMembers(ms)
    })
    if (loadRole) {
      void loadRole(id).then((r) => {
        if (active) setRole(r)
      })
    } else {
      // Rolle und Projektname aus demselben list()-Aufruf (kein zusätzlicher Request).
      void projectsApi.list().then((projects) => {
        if (!active) return
        const project = projects.find((p) => p.id === id)
        setRole(project?.role ?? 'VIEWER')
        setProjectName(project?.name ?? null)
      })
    }
    return () => {
      active = false
    }
  }, [id, validId, api, loadRole])

  const manage = canManageMembers(role)
  const isOwner = role === 'OWNER'
  const ownerCount = members.filter((m) => m.role === 'OWNER').length
  const isLastOwner = (m: Member) => m.role === 'OWNER' && ownerCount === 1

  const handleTransferOwner = async () => {
    if (!transferTarget) {
      return
    }
    setTransferError(null)
    try {
      await projectsApi.transferOwner(id, transferTarget.userId)
      setTransferTarget(null)
      // Der Aufrufer verliert die Owner-Rechte und wird Admin (Backend-Semantik).
      setRole('ADMIN')
      await reload()
    } catch {
      setTransferError('Eigentümer-Wechsel fehlgeschlagen.')
    }
  }

  const handleInvite = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!inviteEmail.trim()) {
      return
    }
    setMessage(null)
    setInviting(true)
    try {
      const result = await api.invite(id, inviteEmail.trim(), inviteRole)
      setInviteEmail('')
      if (result.status === 'added') {
        setMessage({ kind: 'success', text: 'Nutzer wurde hinzugefügt.' })
        await reload()
      } else {
        setMessage({ kind: 'success', text: 'Einladung verschickt.' })
      }
    } catch (error) {
      setMessage({
        kind: 'error',
        text:
          error instanceof ApiError && error.status === 422
            ? 'Nutzer ist noch nicht vom Admin freigegeben.'
            : 'Einladung fehlgeschlagen.',
      })
    } finally {
      setInviting(false)
    }
  }

  const handleChangeRole = async (member: Member, next: ProjectRole) => {
    setMessage(null)
    try {
      await api.changeRole(id, member.userId, next)
      await reload()
    } catch (error) {
      setMessage({
        kind: 'error',
        text: error instanceof ApiError && error.status === 409
          ? 'Der letzte Owner kann nicht herabgestuft werden.'
          : 'Rollenänderung fehlgeschlagen.',
      })
    }
  }

  const handleChangeDisplayName = async () => {
    if (!editingName || editingName.name.trim().length === 0) {
      return
    }
    setMessage(null)
    try {
      await api.changeDisplayName(id, editingName.userId, editingName.name)
      setEditingName(null)
      await reload()
    } catch (error) {
      setMessage({
        kind: 'error',
        text: error instanceof ApiError ? error.message : 'Namensänderung fehlgeschlagen.',
      })
    }
  }

  const handleRemove = async (member: Member) => {
    setMessage(null)
    try {
      await api.remove(id, member.userId)
      await reload()
    } catch (error) {
      setMessage({
        kind: 'error',
        text: error instanceof ApiError && error.status === 409
          ? 'Der letzte Owner kann nicht entfernt werden.'
          : 'Entfernen fehlgeschlagen.',
      })
    }
  }

  if (!validId) {
    return <Alert severity="error">Ungültige Projekt-ID.</Alert>
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Breadcrumbs
          items={[
            { label: 'Projekte', to: '/' },
            ...(projectName ? [{ label: projectName, to: `/projects/${id}` }] : []),
            { label: 'Mitglieder' },
          ]}
        />
        <Link component={RouterLink} to="/roles">Rollen &amp; Rechte</Link>
      </Stack>

      {message && <Alert severity={message.kind} sx={{ mb: 2 }}>{message.text}</Alert>}

      {manage && (
        <Box component="form" onSubmit={handleInvite} sx={{ mb: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <TextField size="small" type="email" label="E-Mail einladen" value={inviteEmail}
              onChange={(e) => setInviteEmail(e.target.value)} />
            <TextField size="small" select label="Rolle" value={inviteRole} sx={{ minWidth: 120 }}
              onChange={(e) => setInviteRole(e.target.value as ProjectRole)}
              inputProps={{ 'aria-label': 'Einladungsrolle' }}>
              {ROLES.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
            </TextField>
            <Button type="submit" variant="contained" disabled={inviting || !inviteEmail.trim()}>
              Einladen
            </Button>
          </Stack>
        </Box>
      )}

      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell>E-Mail</TableCell>
            <TableCell>Rolle</TableCell>
            {manage && <TableCell align="right">Aktionen</TableCell>}
          </TableRow>
        </TableHead>
        <TableBody>
          {members.map((member) => (
            <TableRow key={member.userId} data-testid={`member-${member.userId}`}>
              <TableCell>
                {manage && editingName?.userId === member.userId ? (
                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                    <TextField
                      size="small"
                      variant="standard"
                      value={editingName.name}
                      onChange={(e) => setEditingName({ userId: member.userId, name: e.target.value })}
                      inputProps={{ maxLength: 120, 'aria-label': `Anzeigename von ${member.email}` }}
                    />
                    <Button size="small" aria-label="Namen speichern" onClick={handleChangeDisplayName}>
                      Speichern
                    </Button>
                    <Button size="small" aria-label="Bearbeiten abbrechen" onClick={() => setEditingName(null)}>
                      Abbrechen
                    </Button>
                  </Box>
                ) : (
                  <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center' }}>
                    {member.displayName}
                    {manage && (
                      <Tooltip title="Anzeigename ändern (global, projektübergreifend)">
                        <IconButton
                          size="small"
                          aria-label={`Namen von ${member.displayName} bearbeiten`}
                          onClick={() => setEditingName({ userId: member.userId, name: member.displayName })}
                        >
                          <EditIcon fontSize="inherit" />
                        </IconButton>
                      </Tooltip>
                    )}
                  </Box>
                )}
              </TableCell>
              <TableCell>{member.email}</TableCell>
              <TableCell>
                {manage ? (
                  <TextField select size="small" value={member.role} variant="standard"
                    disabled={isLastOwner(member)} sx={{ minWidth: 110 }}
                    onChange={(e) => handleChangeRole(member, e.target.value as ProjectRole)}
                    inputProps={{ 'aria-label': `Rolle von ${member.displayName}` }}>
                    {ROLES.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
                  </TextField>
                ) : (
                  member.role
                )}
              </TableCell>
              {manage && (
                <TableCell align="right">
                  {isOwner && member.userId !== user?.userId && (
                    <Button size="small" sx={{ mr: 1 }}
                      aria-label={`${member.displayName} zum Eigentümer machen`}
                      onClick={() => setTransferTarget(member)}>
                      Zum Eigentümer machen
                    </Button>
                  )}
                  <Button size="small" color="error" disabled={isLastOwner(member)}
                    aria-label={`${member.displayName} entfernen`} onClick={() => handleRemove(member)}>
                    Entfernen
                  </Button>
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Dialog open={transferTarget !== null} onClose={() => setTransferTarget(null)}>
        <DialogTitle>Zum Eigentümer machen?</DialogTitle>
        <DialogContent>
          {transferError && <Alert severity="error" sx={{ mb: 2 }}>{transferError}</Alert>}
          <DialogContentText>
            „{transferTarget?.displayName}&ldquo; wird Eigentümer dieses Projekts. Du verlierst
            dabei deine Owner-Rechte und wirst zum Admin herabgestuft.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTransferTarget(null)}>Abbrechen</Button>
          <Button variant="contained" onClick={() => void handleTransferOwner()}>
            Übertragen
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
