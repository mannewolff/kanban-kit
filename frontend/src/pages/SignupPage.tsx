import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { ApiError, apiErrorMessage } from '../api/client'
import { AuthCard } from '../components/AuthCard'
import { PasswordField } from '../components/PasswordField'
import { useT } from '../i18n/SpracheContext'

export function SignupPage() {
  const t = useT()
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
      if (e instanceof ApiError && e.status === 429) {
        // Zählbremse vor dem 409-Zweig. Die Abweisung ist herkunftsbezogen und verrät nichts
        // darüber, ob die E-Mail bereits registriert ist.
        setError(apiErrorMessage(e, t.AUTH.ZU_VIELE_VERSUCHE))
      } else if (e instanceof ApiError && e.status === 409) {
        setError(t.AUTH.EMAIL_BEREITS_REGISTRIERT)
      } else {
        setError(t.AUTH.REGISTRIERUNG_FEHLGESCHLAGEN)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title={t.AUTH.REGISTRIEREN}>
      <Box component="form" onSubmit={handleSubmit}>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label={t.AUTH.ANZEIGENAME} value={displayName} onChange={(e) => setDisplayName(e.target.value)}
            required fullWidth />
          <TextField label={t.AUTH.EMAIL} type="email" value={email} onChange={(e) => setEmail(e.target.value)}
            required fullWidth autoComplete="email" />
          <PasswordField label={t.AUTH.PASSWORT} value={password} onChange={(e) => setPassword(e.target.value)}
            required fullWidth autoComplete="new-password" helperText={t.AUTH.MINDESTENS_8_ZEICHEN} />
          <Button type="submit" variant="contained" disabled={busy} fullWidth>
            {t.AUTH.KONTO_ERSTELLEN}
          </Button>
          <Link component={RouterLink} to="/login">{t.AUTH.ZURUECK_ZUR_ANMELDUNG}</Link>
        </Stack>
      </Box>
    </AuthCard>
  )
}
