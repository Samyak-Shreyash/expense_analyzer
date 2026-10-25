import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router'
import { AuthLayout } from './components/AuthLayout'
import { AuthProvider } from './features/auth/AuthContext'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { RequireAuth } from './features/auth/RequireAuth'
import { DashboardPage } from './features/dashboard/DashboardPage'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/dashboard"
            element={
              <RequireAuth>
                <DashboardPage />
              </RequireAuth>
            }
          />
          <Route
            path="/forbidden"
            element={
              <AuthLayout
                title="You can't open this page"
                lead="Your account doesn't have access to it."
              >
                <p>
                  <Link to="/dashboard">Go to your dashboard</Link>
                </p>
              </AuthLayout>
            }
          />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
