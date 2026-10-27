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
import { VorschauEintraege } from '../components/VorschauEintraege'
import { VorschauPersoenlichHinweis } from '../components/VorschauPersoenlichHinweis'
import { useT } from '../i18n/SpracheContext'

/**
 * Profil des angemeldeten Benutzers: Anzeigenamen ändern und sehen, wann welcher Admin die App als
 * dieses Konto angesehen hat (AK 8). In der „Ansicht als XY“ steht statt beidem nur der Hinweis,
 * dass das Profil von XY in der Vorschau nicht gezeigt wird (AK 5, Plan #1914, E5).
 */
export function ProfilePage() {
  const { vorschau } = useAuth()
  const t = useT()
  if (vorschau) {
    return (
      <Box>
        <Typography variant="h4" gutterBottom>
          {t.PROFIL.TITEL}
        </Typography>
        <VorschauPersoenlichHinweis />
      </Box>
    )
  }
  return <EigenesProfil />
}

function EigenesProfil() {
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
      <Box sx={{ maxWidth: 480, mt: 4 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          {t.VORSCHAU.PROTOKOLL_TITEL}
        </Typography>
        <VorschauEintraege laden={authApi.eigenesVorschauProtokoll} />
      </Box>
    </Box>
  )
}
