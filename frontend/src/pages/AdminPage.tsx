import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useState } from 'react'
import { adminApi as defaultAdminApi, type AdminApi, type AdminUser } from '../api/admin'
import { ApiError } from '../api/client'

interface Props {
  api?: AdminApi
}

export function AdminPage({ api = defaultAdminApi }: Props) {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [error, setError] = useState<string | null>(null)

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
            <TableCell>Rolle</TableCell>
            <TableCell align="right">Aktion</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {users.map((u) => (
            <TableRow key={u.id} data-testid={`admin-user-${u.id}`}>
              <TableCell>{u.displayName}</TableCell>
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
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  )
}
