import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AuthCard } from '../components/AuthCard'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(email, password)
      // autoRoute-Signal, damit bei genau einem Projekt/Board direkt durchgeroutet wird
      // (nach dem Login ist die Navigation ein Push, also nicht location.key === 'default').
      navigate('/', { replace: true, state: { autoRoute: true } })
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) {
        setError('Bitte bestätige zuerst deine E-Mail-Adresse.')
      } else {
        setError('Ungültige Anmeldedaten.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title="Anmelden">
      <Box component="form" onSubmit={handleSubmit}>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="E-Mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
            required fullWidth autoComplete="email" />
          <TextField label="Passwort" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            required fullWidth autoComplete="current-password" />
          <Button type="submit" variant="contained" disabled={busy} fullWidth>
            Anmelden
          </Button>
          <Stack direction="row" justifyContent="space-between">
            <Link component={RouterLink} to="/signup">Registrieren</Link>
            <Link component={RouterLink} to="/forgot">Passwort vergessen?</Link>
          </Stack>
        </Stack>
      </Box>
    </AuthCard>
  )
}
