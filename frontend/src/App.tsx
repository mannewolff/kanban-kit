import { Outlet, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { BoardPage } from './pages/BoardPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { LoginPage } from './pages/LoginPage'
import { ProjectBoardsPage } from './pages/ProjectBoardsPage'
import { ProjectsPage } from './pages/ProjectsPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { SignupPage } from './pages/SignupPage'
import { VerifyNoticePage } from './pages/VerifyNoticePage'
import { ProtectedRoute } from './routes/ProtectedRoute'

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/verify" element={<VerifyNoticePage />} />
      <Route path="/forgot" element={<ForgotPasswordPage />} />
      <Route path="/reset" element={<ResetPasswordPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell><Outlet /></AppShell>}>
          <Route path="/" element={<ProjectsPage />} />
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
