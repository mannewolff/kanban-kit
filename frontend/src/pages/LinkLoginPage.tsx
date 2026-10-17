import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useRef, useState } from 'react'
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AuthCard } from '../components/AuthCard'
import { useT } from '../i18n/SpracheContext'
import { codeFehlermeldung } from '../lib/codeFehlermeldung'

/**
 * Ziel des Links aus der Anmeldemail (Plan #1801, A9). Das Öffnen per `GET` meldet nie an: Die Seite
 * löst das Token beim Laden per `POST` ein, damit Vorschaudienste der Mailprogramme nichts verbrauchen.
 */
export function LinkLoginPage() {
  const t = useT()
  const { loginWithLink } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [error, setError] = useState<string | null>(null)
  // Das Token gilt nur einmal: Ohne diese Sperre löste der doppelte Effekt im StrictMode es zweimal
  // ein, und der zweite Aufruf meldete „Code ungültig“.
  const eingeloest = useRef(false)

  useEffect(() => {
    if (token == null || token === '' || eingeloest.current) return
    eingeloest.current = true
    loginWithLink(token).then(
      () => navigate('/', { replace: true, state: { autoRoute: true } }),
      (e: unknown) => setError(codeFehlermeldung(e, t)),
    )
  }, [token, loginWithLink, navigate, t])

  const meldung = token == null || token === '' ? t.AUTH.LINK_OHNE_TOKEN : error

  return (
    <AuthCard title={t.AUTH.ANMELDEN}>
      {meldung == null ? (
        <Stack spacing={2} sx={{ alignItems: 'center' }}>
          <CircularProgress aria-label={t.AUTH.LINK_LAEUFT} />
          <Typography>{t.AUTH.LINK_LAEUFT}</Typography>
        </Stack>
      ) : (
        <Stack spacing={2}>
          <Alert severity="error">{meldung}</Alert>
          <Link component={RouterLink} to="/login">{t.AUTH.ZURUECK_ZUR_ANMELDUNG}</Link>
        </Stack>
      )}
    </AuthCard>
  )
}
