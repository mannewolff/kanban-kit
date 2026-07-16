import EditIcon from '@mui/icons-material/Edit'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useState } from 'react'
import { adminApi as defaultAdminApi, type AdminApi, type AdminUser } from '../api/admin'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

interface Props {
  api?: AdminApi
}

export function AdminPage({ api = defaultAdminApi }: Readonly<Props>) {
  const { user: currentUser } = useAuth()
  const [users, setUsers] = useState<AdminUser[]>([])
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<{ id: number; name: string } | null>(null)

  const reload = useCallback(() => {
    api
      .listUsers()
      .then(setUsers)
      .catch((e) => setError(e instanceof ApiError && e.status === 403 ? 'Kein Admin-Zugriff.' : 'Laden fehlgeschlagen.'))
  }, [api])

  useEffect(() => {
    reload()
  }, [reload])

  const toggleRole = async (u: AdminUser) => {
    setError(null)
    const next = u.platformRole === 'ADMIN' ? 'USER' : 'ADMIN'
    try {
      await api.setRole(u.id, next)
      reload()
    } catch (e) {
      setError(
        e instanceof ApiError && e.status === 409
          ? 'Der letzte Admin kann nicht degradiert werden.'
          : 'Rollenänderung fehlgeschlagen.',
      )
    }
  }

  const approve = async (u: AdminUser) => {
    setError(null)
    try {
      await api.approve(u.id)
      reload()
    } catch {
      setError('Freigabe fehlgeschlagen.')
    }
  }

  const saveDisplayName = async () => {
    if (!editing || editing.name.trim().length === 0) {
      return
    }
    setError(null)
    try {
      await api.setDisplayName(editing.id, editing.name)
      setEditing(null)
      reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Namensänderung fehlgeschlagen.')
    }
  }

  const toggleDisabled = async (u: AdminUser) => {
    setError(null)
    try {
      await (u.disabled ? api.enable(u.id) : api.disable(u.id))
      reload()
    } catch {
      setError(u.disabled ? 'Entsperren fehlgeschlagen.' : 'Sperren fehlgeschlagen.')
    }
  }

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        Admin — Nutzerverwaltung
      </Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell>E-Mail</TableCell>
            <TableCell>Verifiziert</TableCell>
            <TableCell>Freigabe</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Rolle</TableCell>
            <TableCell align="right">Aktion</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {users.map((u) => (
            <TableRow key={u.id} data-testid={`admin-user-${u.id}`}>
              <TableCell>
                {editing?.id === u.id ? (
                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                    <TextField
                      size="small"
                      value={editing.name}
                      onChange={(e) => setEditing({ id: u.id, name: e.target.value })}
                      slotProps={{ htmlInput: { maxLength: 120, 'aria-label': `Anzeigename von ${u.email}` } }}
                    />
                    <Button size="small" aria-label="Namen speichern" onClick={saveDisplayName}>
                      Speichern
                    </Button>
                    <Button size="small" aria-label="Bearbeiten abbrechen" onClick={() => setEditing(null)}>
                      Abbrechen
                    </Button>
                  </Box>
                ) : (
                  <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center' }}>
                    {u.displayName}
                    <IconButton
                      size="small"
                      aria-label={`Namen von ${u.displayName} bearbeiten`}
                      onClick={() => setEditing({ id: u.id, name: u.displayName })}
                    >
                      <EditIcon fontSize="inherit" />
                    </IconButton>
                  </Box>
                )}
              </TableCell>
              <TableCell>{u.email}</TableCell>
              <TableCell>{u.emailVerified ? 'ja' : 'nein'}</TableCell>
              <TableCell>
                <Chip
                  label={u.approvedAt ? 'Freigegeben' : 'Wartet auf Freigabe'}
                  size="small"
                  color={u.approvedAt ? 'success' : 'warning'}
                />
              </TableCell>
              <TableCell>
                <Chip
                  label={u.disabled ? 'Gesperrt' : 'Aktiv'}
                  size="small"
                  color={u.disabled ? 'error' : 'success'}
                />
              </TableCell>
              <TableCell>
                <Chip label={u.platformRole} size="small" color={u.platformRole === 'ADMIN' ? 'primary' : 'default'} />
              </TableCell>
              <TableCell align="right">
                {!u.approvedAt && (
                  <Button
                    size="small"
                    aria-label={`${u.displayName} freigeben`}
                    onClick={() => approve(u)}
                    sx={{ mr: 1 }}
                  >
                    Freigeben
                  </Button>
                )}
                <Button size="small" aria-label={`Rolle von ${u.displayName} umschalten`} onClick={() => toggleRole(u)}>
                  {u.platformRole === 'ADMIN' ? 'Zu USER' : 'Zu ADMIN'}
                </Button>
                {u.id !== currentUser?.userId && (
                  <Button
                    size="small"
                    color={u.disabled ? 'success' : 'error'}
                    aria-label={u.disabled ? `${u.displayName} entsperren` : `${u.displayName} sperren`}
                    onClick={() => toggleDisabled(u)}
                    sx={{ ml: 1 }}
                  >
                    {u.disabled ? 'Entsperren' : 'Sperren'}
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  )
}
