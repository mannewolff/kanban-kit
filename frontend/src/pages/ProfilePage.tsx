import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useSnackbar } from '../components/SnackbarProvider'

/** Selbstpflege des Profils: Anzeigenamen des angemeldeten Benutzers ändern. */
export function ProfilePage() {
  const { user, refresh } = useAuth()
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [busy, setBusy] = useState(false)
  const notify = useSnackbar()

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      await authApi.updateProfile(displayName)
      await refresh()
      notify('Anzeigename gespeichert.', 'success')
    } catch (e) {
      notify(e instanceof ApiError ? e.message : 'Speichern fehlgeschlagen.', 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Profil
      </Typography>
      <Box component="form" onSubmit={handleSubmit} sx={{ maxWidth: 480 }}>
        <Stack spacing={2}>
          <TextField label="E-Mail" value={user?.email ?? ''} disabled fullWidth />
          <TextField
            label="Anzeigename"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
            fullWidth
            slotProps={{ htmlInput: { maxLength: 120, 'aria-label': 'Anzeigename' } }}
          />
          <Button type="submit" variant="contained" disabled={busy || displayName.trim().length === 0}>
            Speichern
          </Button>
        </Stack>
      </Box>
    </Box>
  )
}
