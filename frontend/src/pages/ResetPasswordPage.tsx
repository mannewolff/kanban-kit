import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import { useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { authApi } from '../api/auth'
import { AuthCard } from '../components/AuthCard'
import { PasswordField } from '../components/PasswordField'
import { useT } from '../i18n/SpracheContext'

export function ResetPasswordPage() {
  const t = useT()
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await authApi.reset(token, password)
      setDone(true)
    } catch {
      setError(t.AUTH.LINK_UNGUELTIG)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title={t.AUTH.NEUES_PASSWORT_TITEL}>
      {done ? (
        <Stack spacing={2}>
          <Alert severity="success">{t.AUTH.PASSWORT_GESETZT}</Alert>
          <Link component={RouterLink} to="/login">{t.AUTH.ZUR_ANMELDUNG}</Link>
        </Stack>
      ) : (
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            {!token && <Alert severity="warning">{t.AUTH.KEIN_TOKEN}</Alert>}
            <PasswordField label={t.AUTH.NEUES_PASSWORT} value={password}
              onChange={(e) => setPassword(e.target.value)} required fullWidth autoComplete="new-password"
              helperText={t.AUTH.MINDESTENS_8_ZEICHEN} />
            <Button type="submit" variant="contained" disabled={busy || !token} fullWidth>
              {t.AUTH.PASSWORT_SETZEN}
            </Button>
          </Stack>
        </Box>
      )}
    </AuthCard>
  )
}
