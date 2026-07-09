import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { authApi } from '../api/auth'
import { AuthCard } from '../components/AuthCard'

export function ResetPasswordPage() {
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
      setError('Der Link ist ungültig oder abgelaufen.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title="Neues Passwort setzen">
      {done ? (
        <Stack spacing={2}>
          <Alert severity="success">Passwort gesetzt. Du kannst dich jetzt anmelden.</Alert>
          <Link component={RouterLink} to="/login">Zur Anmeldung</Link>
        </Stack>
      ) : (
        <Box component="form" onSubmit={handleSubmit}>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            {!token && <Alert severity="warning">Kein Token in der URL gefunden.</Alert>}
            <TextField label="Neues Passwort" type="password" value={password}
              onChange={(e) => setPassword(e.target.value)} required fullWidth autoComplete="new-password"
              helperText="Mindestens 8 Zeichen" />
            <Button type="submit" variant="contained" disabled={busy || !token} fullWidth>
              Passwort setzen
            </Button>
          </Stack>
        </Box>
      )}
    </AuthCard>
  )
}
