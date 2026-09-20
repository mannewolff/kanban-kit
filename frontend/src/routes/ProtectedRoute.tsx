import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/**
 * Lässt nur angemeldete Nutzer durch; sonst Weiterleitung auf /login.
 *
 * Die aufgerufene Adresse reist als `state.from` mit (Issue #1082, Plan #1072 E9): AK 1 Satz 2 der
 * fachlichen Quelle #1064 will, dass man nach dem Anmelden dort landet, wo man hinwollte. Als
 * Router-State und nicht als Query-Parameter — ein State verlässt die Anwendung nicht und landet
 * in keinem Protokoll.
 */
export function ProtectedRoute() {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
        <CircularProgress />
      </Box>
    )
  }

  if (user) {
    return <Outlet />
  }
  return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
}
