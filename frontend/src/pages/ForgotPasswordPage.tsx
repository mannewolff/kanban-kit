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

export function ForgotPasswordPage() {
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
        setError(apiErrorMessage(e, 'Zu viele Versuche. Bitte später erneut versuchen.'))
      } else {
        // Jeder andere Ausgang bleibt verschluckt: derselbe Hinweis, egal was passiert ist.
        setSent(true)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title="Passwort zurücksetzen">
      {sent ? (
        <Stack spacing={2}>
          <Alert severity="info">
            Falls ein Konto mit dieser E-Mail existiert, wurde ein Link zum Zurücksetzen gesendet.
          </Alert>
          <Link component={RouterLink} to="/login">Zur Anmeldung</Link>
        </Stack>
      ) : (
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField label="E-Mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              required fullWidth autoComplete="email" />
            <Button type="submit" variant="contained" disabled={busy} fullWidth>
              Link anfordern
            </Button>
            <Link component={RouterLink} to="/login">Zurück zur Anmeldung</Link>
          </Stack>
        </Box>
      )}
    </AuthCard>
  )
}
