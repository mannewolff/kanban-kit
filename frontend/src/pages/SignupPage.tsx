import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { AuthCard } from '../components/AuthCard'

export function SignupPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await authApi.register(email, password, displayName)
      navigate('/verify')
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setError('Diese E-Mail-Adresse ist bereits registriert.')
      } else {
        setError('Registrierung fehlgeschlagen. Bitte Eingaben prüfen (Passwort mind. 8 Zeichen).')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title="Registrieren">
      <Box component="form" onSubmit={handleSubmit}>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="Anzeigename" value={displayName} onChange={(e) => setDisplayName(e.target.value)}
            required fullWidth />
          <TextField label="E-Mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
            required fullWidth autoComplete="email" />
          <TextField label="Passwort" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            required fullWidth autoComplete="new-password" helperText="Mindestens 8 Zeichen" />
          <Button type="submit" variant="contained" disabled={busy} fullWidth>
            Konto erstellen
          </Button>
          <Link component={RouterLink} to="/login">Zurück zur Anmeldung</Link>
        </Stack>
      </Box>
    </AuthCard>
  )
}
