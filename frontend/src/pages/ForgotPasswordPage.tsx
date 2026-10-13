import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { authApi } from '../api/auth'
import { ApiError, apiErrorMessage } from '../api/client'
import { AuthCard } from '../components/AuthCard'
import { useT } from '../i18n/SpracheContext'

export function ForgotPasswordPage() {
  const t = useT()
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      await authApi.forgot(email)
      setSent(true)
    } catch (e) {
      if (e instanceof ApiError && e.status === 429) {
        // Einzige Ausnahme vom neutralen Hinweis: Die Zählbremse weist herkunftsbezogen ab und
        // verrät nichts über die Existenz eines Kontos. Ohne diese Meldung sähe der Nutzer einen
        // Erfolg, der keiner war — er wartete auf eine Mail, die nie kommt.
        setError(apiErrorMessage(e, t.AUTH.ZU_VIELE_VERSUCHE))
      } else {
        // Jeder andere Ausgang bleibt verschluckt: derselbe Hinweis, egal was passiert ist.
        setSent(true)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title={t.AUTH.VERGESSEN_TITEL}>
      {sent ? (
        <Stack spacing={2}>
          <Alert severity="info">
            {t.AUTH.VERGESSEN_GESENDET}
          </Alert>
          <Link component={RouterLink} to="/login">{t.AUTH.ZUR_ANMELDUNG}</Link>
        </Stack>
      ) : (
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField label={t.AUTH.EMAIL} type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              required fullWidth autoComplete="email" />
            <Button type="submit" variant="contained" disabled={busy} fullWidth>
              {t.AUTH.LINK_ANFORDERN}
            </Button>
            <Link component={RouterLink} to="/login">{t.AUTH.ZURUECK_ZUR_ANMELDUNG}</Link>
          </Stack>
        </Box>
      )}
    </AuthCard>
  )
}
