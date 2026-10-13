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
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { membersApi as defaultMembersApi, type Member, type MembersApi } from '../api/members'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { projectsApi } from '../api/projects'
import { ApiError, apiErrorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useEditMode } from '../lib/EditModeContext'
import { canManageMembers, type ProjectRole } from '../lib/roles'
import { useSnackbar } from '../components/SnackbarProvider'
import { useT } from '../i18n/SpracheContext'

const ROLES: ProjectRole[] = ['OWNER', 'ADMIN', 'MEMBER', 'VIEWER']

interface Props {
  api?: MembersApi
  loadRole?: (projectId: number) => Promise<string>
}

export function ProjectMembersPage({ api = defaultMembersApi, loadRole }: Readonly<Props>) {
  const { projectId } = useParams()
  const id = Number.parseInt(projectId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const { user } = useAuth()
  const notify = useSnackbar()
  const t = useT()
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
  const { editMode } = useEditMode()
  const isOwner = role === 'OWNER'
  const ownerCount = members.filter((m) => m.role === 'OWNER').length
  const isLastOwner = (m: Member) => m.role === 'OWNER' && ownerCount === 1

  // Kein Nullable-Guard nötig: der Übertragen-Button existiert nur, solange der Dialog offen ist
  // (open={transferTarget !== null}, siehe unten) — das Ziel wird ihm direkt übergeben.
  const handleTransferOwner = async (target: Member) => {
    setTransferError(null)
    try {
      await projectsApi.transferOwner(id, target.userId)
    } catch (e) {
      setTransferError(apiErrorMessage(e, t.MITGLIEDER.TRANSFER_FEHLER))
      return
    }
    setTransferTarget(null)
    // Der Aufrufer verliert die Owner-Rechte und wird Admin (Backend-Semantik).
    setRole('ADMIN')
    try {
      await reload()
    } catch (e) {
      // Der Wechsel ist durch — der Dialog ist schon zu, daher ueber den Snackbar statt
      // transferError (das nur im Dialog sichtbar ist).
      notify(apiErrorMessage(e, t.MITGLIEDER.TRANSFER_LISTE_FEHLER), 'error')
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
        setMessage({ kind: 'success', text: t.MITGLIEDER.HINZUGEFUEGT })
        await reload()
      } else {
        setMessage({ kind: 'success', text: t.MITGLIEDER.EINLADUNG_VERSCHICKT })
      }
    } catch (error) {
      setMessage({
        kind: 'error',
        text:
          error instanceof ApiError && error.status === 422
            ? t.MITGLIEDER.NICHT_FREIGEGEBEN
            : t.MITGLIEDER.EINLADUNG_FEHLER,
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
          ? t.MITGLIEDER.LETZTER_OWNER_HERABSTUFEN
          : t.MITGLIEDER.ROLLE_FEHLER,
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
        text: error instanceof ApiError ? error.message : t.MITGLIEDER.NAME_FEHLER,
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
          ? t.MITGLIEDER.LETZTER_OWNER_ENTFERNEN
          : t.MITGLIEDER.ENTFERNEN_FEHLER,
      })
    }
  }

  if (!validId) {
    return <Alert severity="error">{t.ALLGEMEIN.UNGUELTIGE_PROJEKT_ID}</Alert>
  }

  const nameCell = (member: Member) =>
    manage && editingName?.userId === member.userId ? (
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
        <TextField
          size="small"
          variant="standard"
          value={editingName.name}
          onChange={(e) => setEditingName({ userId: member.userId, name: e.target.value })}
          slotProps={{ htmlInput: { maxLength: 120, 'aria-label': t.MITGLIEDER.ANZEIGENAME_VON(member.email) } }}
        />
        <Button size="small" aria-label={t.MITGLIEDER.NAMEN_SPEICHERN} onClick={handleChangeDisplayName}>
          {t.ALLGEMEIN.SPEICHERN}
        </Button>
        <Button size="small" aria-label={t.MITGLIEDER.BEARBEITEN_ABBRECHEN} onClick={() => setEditingName(null)}>
          {t.ALLGEMEIN.ABBRECHEN}
        </Button>
      </Box>
    ) : (
      <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center' }}>
        {member.displayName}
        {manage && editMode && (
          <Tooltip title={t.MITGLIEDER.NAME_AENDERN_HINWEIS}>
            <IconButton
              size="small"
              aria-label={t.MITGLIEDER.NAMEN_BEARBEITEN(member.displayName)}
              onClick={() => setEditingName({ userId: member.userId, name: member.displayName })}
            >
              <EditIcon fontSize="inherit" />
            </IconButton>
          </Tooltip>
        )}
      </Box>
    )

  const roleCell = (member: Member) =>
    manage ? (
      <TextField
        select
        size="small"
        value={member.role}
        variant="standard"
        disabled={isLastOwner(member)}
        sx={{ minWidth: 110 }}
        onChange={(e) => handleChangeRole(member, e.target.value as ProjectRole)}
        slotProps={{ htmlInput: { 'aria-label': t.MITGLIEDER.ROLLE_VON(member.displayName) } }}
      >
        {ROLES.map((r) => (
          <MenuItem key={r} value={r}>
            {r}
          </MenuItem>
        ))}
      </TextField>
    ) : (
      member.role
    )

  const actionsCell = (member: Member) => (
    <>
      {isOwner && member.userId !== user?.userId && (
        <Button
          size="small"
          sx={{ mr: 1 }}
          aria-label={t.MITGLIEDER.ZUM_EIGENTUEMER_ARIA(member.displayName)}
          onClick={() => setTransferTarget(member)}
        >
          {t.MITGLIEDER.ZUM_EIGENTUEMER}
        </Button>
      )}
      <Button
        size="small"
        color="error"
        disabled={isLastOwner(member)}
        aria-label={t.MITGLIEDER.ENTFERNEN_ARIA(member.displayName)}
        onClick={() => handleRemove(member)}
      >
        {t.ALLGEMEIN.ENTFERNEN}
      </Button>
    </>
  )

  const columns: DataTableColumn<Member>[] = [
    { key: 'name', header: t.MITGLIEDER.SPALTE_NAME, render: nameCell, defaultWidth: 200, minWidth: 120, hideable: true, resizable: true },
    { key: 'email', header: t.MITGLIEDER.SPALTE_EMAIL, render: (m) => m.email, defaultWidth: 220, minWidth: 120, hideable: true, resizable: true },
    { key: 'role', header: t.ALLGEMEIN.ROLLE, render: roleCell, defaultWidth: 140, minWidth: 100, hideable: true, resizable: true },
    ...(manage
      ? [{ key: 'actions', header: t.MITGLIEDER.SPALTE_AKTIONEN, render: actionsCell, align: 'right' as const, defaultWidth: 260 }]
      : []),
  ]

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Breadcrumbs
          items={[
            { label: t.RAHMEN.NAV_PROJEKTE, to: '/projects' },
            ...(projectName ? [{ label: projectName, to: `/projects/${id}` }] : []),
            { label: t.RAHMEN.NAV_MITGLIEDER },
          ]}
        />
        <Link component={RouterLink} to="/roles">{t.RAHMEN.NAV_ROLLEN}</Link>
      </Stack>

      {message && <Alert severity={message.kind} sx={{ mb: 2 }}>{message.text}</Alert>}

      {manage && (
        <Box component="form" aria-label={t.MITGLIEDER.EINLADEN_FORMULAR} onSubmit={handleInvite} sx={{ mb: 3 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <TextField size="small" type="email" label={t.MITGLIEDER.EMAIL_EINLADEN} value={inviteEmail}
              onChange={(e) => setInviteEmail(e.target.value)} />
            <TextField size="small" select label={t.ALLGEMEIN.ROLLE} value={inviteRole} sx={{ minWidth: 120 }}
              onChange={(e) => setInviteRole(e.target.value as ProjectRole)}
              slotProps={{ htmlInput: { 'aria-label': t.MITGLIEDER.EINLADUNGSROLLE } }}>
              {ROLES.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
            </TextField>
            <Button type="submit" variant="contained" disabled={inviting || !inviteEmail.trim()}>
              {t.MITGLIEDER.EINLADEN}
            </Button>
          </Stack>
        </Box>
      )}

      <DataTable
        columns={columns}
        rows={members}
        getRowKey={(m) => m.userId}
        getRowTestId={(m) => `member-${m.userId}`}
        storageKey="project-members"
      />

      {transferTarget && (
        <Dialog open onClose={() => setTransferTarget(null)}>
          <DialogTitle>{t.MITGLIEDER.TRANSFER_TITEL}</DialogTitle>
          <DialogContent>
            {transferError && <Alert severity="error" sx={{ mb: 2 }}>{transferError}</Alert>}
            <DialogContentText>
              {t.MITGLIEDER.TRANSFER_TEXT(transferTarget.displayName)}
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setTransferTarget(null)}>{t.ALLGEMEIN.ABBRECHEN}</Button>
            <Button variant="contained" onClick={() => void handleTransferOwner(transferTarget)}>
              {t.MITGLIEDER.UEBERTRAGEN}
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  )
}
