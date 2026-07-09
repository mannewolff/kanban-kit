import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Typography from '@mui/material/Typography'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { membersApi as defaultMembersApi, type Member, type MembersApi } from '../api/members'

interface Props {
  api?: MembersApi
}

type State =
  | { status: 'loading' }
  | { status: 'ok'; member: Member }
  | { status: 'error' }

export function AcceptInvitationPage({ api = defaultMembersApi }: Props) {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [state, setState] = useState<State>({ status: 'loading' })

  useEffect(() => {
    if (!token) {
      setState({ status: 'error' })
      return
    }
    api
      .accept(token)
      .then((member) => setState({ status: 'ok', member }))
      .catch(() => setState({ status: 'error' }))
  }, [api, token])

  return (
    <Box sx={{ maxWidth: 480 }}>
      <Typography variant="h5" sx={{ mb: 2 }}>
        Einladung annehmen
      </Typography>

      {state.status === 'loading' && <CircularProgress />}

      {state.status === 'error' && (
        <Alert severity="error">Die Einladung ist ungültig oder abgelaufen.</Alert>
      )}

      {state.status === 'ok' && (
        <Box>
          <Alert severity="success" sx={{ mb: 2 }}>
            Du bist dem Projekt als {state.member.role} beigetreten.
          </Alert>
          <Button component={RouterLink} to="/" variant="contained">
            Zu den Projekten
          </Button>
        </Box>
      )}
    </Box>
  )
}
