import { Suspense, lazy } from 'react'
import { Route, Routes } from 'react-router-dom'
import { Box, CircularProgress } from '@mui/material'
import { AppShell } from './components/AppShell'
import { ProtectedRoute } from './routes/ProtectedRoute'

// Route-Level Lazy Loading (CLAUDE-react.md Performance-Budget): jede Page wird als
// eigener Chunk geladen. AppShell/ProtectedRoute bleiben statisch (App-Shell).
const AcceptInvitationPage = lazy(() =>
  import('./pages/AcceptInvitationPage').then((m) => ({ default: m.AcceptInvitationPage })),
)
const AdminPage = lazy(() => import('./pages/AdminPage').then((m) => ({ default: m.AdminPage })))
const BoardListPage = lazy(() =>
  import('./pages/BoardListPage').then((m) => ({ default: m.BoardListPage })),
)
const BoardPage = lazy(() => import('./pages/BoardPage').then((m) => ({ default: m.BoardPage })))
const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })),
)
const BootstrapAdminPage = lazy(() =>
  import('./pages/BootstrapAdminPage').then((m) => ({ default: m.BootstrapAdminPage })),
)
const EpicsPage = lazy(() => import('./pages/EpicsPage').then((m) => ({ default: m.EpicsPage })))
const ForgotPasswordPage = lazy(() =>
  import('./pages/ForgotPasswordPage').then((m) => ({ default: m.ForgotPasswordPage })),
)
const LoginPage = lazy(() => import('./pages/LoginPage').then((m) => ({ default: m.LoginPage })))
const ProjectBoardsPage = lazy(() =>
  import('./pages/ProjectBoardsPage').then((m) => ({ default: m.ProjectBoardsPage })),
)
const ProjectMembersPage = lazy(() =>
  import('./pages/ProjectMembersPage').then((m) => ({ default: m.ProjectMembersPage })),
)
const ProjectsPage = lazy(() =>
  import('./pages/ProjectsPage').then((m) => ({ default: m.ProjectsPage })),
)
const RolesPage = lazy(() => import('./pages/RolesPage').then((m) => ({ default: m.RolesPage })))
const ResetPasswordPage = lazy(() =>
  import('./pages/ResetPasswordPage').then((m) => ({ default: m.ResetPasswordPage })),
)
const SignupPage = lazy(() => import('./pages/SignupPage').then((m) => ({ default: m.SignupPage })))
const VerifyNoticePage = lazy(() =>
  import('./pages/VerifyNoticePage').then((m) => ({ default: m.VerifyNoticePage })),
)

function PageFallback() {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '40vh' }}>
      <CircularProgress />
    </Box>
  )
}

export function App() {
  return (
    <Suspense fallback={<PageFallback />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/verify" element={<VerifyNoticePage />} />
        <Route path="/forgot" element={<ForgotPasswordPage />} />
        <Route path="/reset" element={<ResetPasswordPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route path="/" element={<ProjectsPage />} />
            <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
            <Route path="/projects/:projectId/members" element={<ProjectMembersPage />} />
            <Route path="/boards/:boardId" element={<BoardPage />} />
            <Route path="/boards/:boardId/list" element={<BoardListPage />} />
            <Route path="/boards/:boardId/epics" element={<EpicsPage />} />
            <Route path="/boards/:boardId/dashboard" element={<DashboardPage />} />
            <Route path="/admin" element={<AdminPage />} />
            <Route path="/admin/bootstrap" element={<BootstrapAdminPage />} />
            <Route path="/roles" element={<RolesPage />} />
            <Route path="/invitations/accept" element={<AcceptInvitationPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  )
}
