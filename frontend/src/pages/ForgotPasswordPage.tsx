import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { authApi } from '../api/auth'
import { AuthCard } from '../components/AuthCard'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      await authApi.forgot(email)
    } finally {
      // Immer denselben Hinweis zeigen (keine Preisgabe, ob die E-Mail existiert).
      setSent(true)
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
