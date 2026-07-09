import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/** Lässt nur angemeldete Nutzer durch; sonst Weiterleitung auf /login. */
export function ProtectedRoute() {
  const { user, loading } = useAuth()

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
        <CircularProgress />
      </Box>
    )
  }

  return user ? <Outlet /> : <Navigate to="/login" replace />
}
