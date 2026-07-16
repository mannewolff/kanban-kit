import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

/** Selbstpflege des Profils: Anzeigenamen des angemeldeten Benutzers ändern. */
export function ProfilePage() {
  const { user, refresh } = useAuth()
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    setSaved(false)
    setBusy(true)
    try {
      await authApi.updateProfile(displayName)
      await refresh()
      setSaved(true)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Speichern fehlgeschlagen.')
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
          {error && <Alert severity="error">{error}</Alert>}
          {saved && <Alert severity="success">Anzeigename gespeichert.</Alert>}
          <TextField label="E-Mail" value={user?.email ?? ''} disabled fullWidth />
          <TextField
            label="Anzeigename"
            value={displayName}
            onChange={(e) => {
              setDisplayName(e.target.value)
              setSaved(false)
            }}
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
