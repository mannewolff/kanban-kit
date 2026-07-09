import Alert from '@mui/material/Alert'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { authApi } from '../api/auth'
import { AuthCard } from '../components/AuthCard'

type State = 'notice' | 'verifying' | 'success' | 'error'

/** Zeigt entweder den Bestätigungs-Hinweis oder löst ein Token aus der URL ein. */
export function VerifyNoticePage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [state, setState] = useState<State>(token ? 'verifying' : 'notice')

  useEffect(() => {
    if (!token) {
      return
    }
    authApi
      .verify(token)
      .then(() => setState('success'))
      .catch(() => setState('error'))
  }, [token])

  return (
    <AuthCard title="E-Mail-Bestätigung">
      <Stack spacing={2}>
        {state === 'notice' && (
          <Typography>
            Wir haben dir eine E-Mail zur Bestätigung geschickt. Bitte öffne den Link darin.
          </Typography>
        )}
        {state === 'verifying' && <Typography>E-Mail wird bestätigt …</Typography>}
        {state === 'success' && <Alert severity="success">E-Mail bestätigt. Du kannst dich jetzt anmelden.</Alert>}
        {state === 'error' && <Alert severity="error">Der Bestätigungslink ist ungültig oder abgelaufen.</Alert>}
        <Link component={RouterLink} to="/login">Zur Anmeldung</Link>
      </Stack>
    </AuthCard>
  )
}
