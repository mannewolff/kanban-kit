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
import { useT } from '../i18n/SpracheContext'

/** Selbstpflege des Profils: Anzeigenamen des angemeldeten Benutzers ändern. */
export function ProfilePage() {
  const { user, refresh } = useAuth()
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [busy, setBusy] = useState(false)
  const notify = useSnackbar()
  const t = useT()

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      await authApi.updateProfile(displayName)
      await refresh()
      notify(t.PROFIL.GESPEICHERT, 'success')
    } catch (e) {
      notify(e instanceof ApiError ? e.message : t.ALLGEMEIN.SPEICHERN_FEHLGESCHLAGEN, 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        {t.PROFIL.TITEL}
      </Typography>
      <Box component="form" onSubmit={handleSubmit} sx={{ maxWidth: 480 }}>
        <Stack spacing={2}>
          <TextField label={t.AUTH.EMAIL} value={user?.email ?? ''} disabled fullWidth />
          <TextField
            label={t.AUTH.ANZEIGENAME}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
            fullWidth
            slotProps={{ htmlInput: { maxLength: 120, 'aria-label': t.AUTH.ANZEIGENAME } }}
          />
          <Button type="submit" variant="contained" disabled={busy || displayName.trim().length === 0}>
            {t.ALLGEMEIN.SPEICHERN}
          </Button>
        </Stack>
      </Box>
    </Box>
  )
}
