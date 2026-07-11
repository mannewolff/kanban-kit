import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { membersApi as defaultMembersApi, type Member, type MembersApi } from '../api/members'
import { projectsApi } from '../api/projects'
import { ApiError } from '../api/client'
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
  const [members, setMembers] = useState<Member[]>([])
  const [role, setRole] = useState<string>('VIEWER')
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState<ProjectRole>('MEMBER')
  const [message, setMessage] = useState<{ kind: 'success' | 'error'; text: string } | null>(null)

  const reload = () => api.list(id).then(setMembers)

  useEffect(() => {
    if (!validId) {
      return
    }
    void reload()
    const roleLoader = loadRole
      ? loadRole(id)
      : projectsApi.list().then((projects) => projects.find((p) => p.id === id)?.role ?? 'VIEWER')
    void roleLoader.then(setRole)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, validId])

  const manage = canManageMembers(role)
  const ownerCount = members.filter((m) => m.role === 'OWNER').length
  const isLastOwner = (m: Member) => m.role === 'OWNER' && ownerCount === 1

  const handleInvite = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!inviteEmail.trim()) {
      return
    }
    setMessage(null)
    try {
      await api.invite(id, inviteEmail.trim(), inviteRole)
      setInviteEmail('')
      setMessage({ kind: 'success', text: 'Einladung verschickt.' })
    } catch {
      setMessage({ kind: 'error', text: 'Einladung fehlgeschlagen.' })
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
      <Link component={RouterLink} to={`/projects/${id}`}>← Boards</Link>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mt: 1, mb: 2 }}>
        <Typography variant="h5">Mitglieder</Typography>
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
            <Button type="submit" variant="contained">
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
              <TableCell>{member.displayName}</TableCell>
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
    </Box>
  )
}
