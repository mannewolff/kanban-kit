import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { adminApi as defaultAdminApi, type AdminApi } from '../api/admin'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { PasswordField } from '../components/PasswordField'
import { useT } from '../i18n/SpracheContext'

interface Props {
  api?: AdminApi
}

/** Hebt den eingeloggten Nutzer per Env-Token zum ersten Plattform-Admin (nur auf frischer Instanz). */
export function BootstrapAdminPage({ api = defaultAdminApi }: Readonly<Props>) {
  const t = useT()
  const { refresh } = useAuth()
  const navigate = useNavigate()
  const [token, setToken] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!token.trim() || busy) return
    setBusy(true)
    setError(null)
    try {
      await api.bootstrap(token.trim())
      await refresh()
      navigate('/admin')
    } catch (e) {
      setError(
        e instanceof ApiError && e.status === 409
          ? t.AUTH.BOOTSTRAP_ADMIN_EXISTIERT
          : t.AUTH.BOOTSTRAP_TOKEN_UNGUELTIG,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <Box sx={{ maxWidth: 480 }}>
      <Typography variant="h5" gutterBottom>
        {t.AUTH.BOOTSTRAP_TITEL}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        {t.AUTH.BOOTSTRAP_HINWEIS}
      </Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Box component="form" onSubmit={submit}>
        <Stack direction="row" spacing={1}>
          <PasswordField
            size="small"
            label={t.AUTH.BOOTSTRAP_TOKEN}
            value={token}
            onChange={(e) => setToken(e.target.value)}
            slotProps={{ htmlInput: { 'aria-label': t.AUTH.BOOTSTRAP_TOKEN } }}
          />
          <Button type="submit" variant="contained" disabled={busy}>
            {t.AUTH.BOOTSTRAP_ADMIN_WERDEN}
          </Button>
        </Stack>
      </Box>
    </Box>
  )
}
