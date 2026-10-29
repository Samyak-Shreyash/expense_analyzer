import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router'
import { AppLayout } from './components/AppLayout'
import { AuthLayout } from './components/AuthLayout'
import { AuthProvider } from './features/auth/AuthContext'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { RequireAuth } from './features/auth/RequireAuth'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { StatementsPage } from './features/statements/StatementsPage'
import { TransactionsPage } from './features/transactions/TransactionsPage'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          {[
            { path: '/dashboard', page: <DashboardPage /> },
            { path: '/statements', page: <StatementsPage /> },
            { path: '/transactions', page: <TransactionsPage /> },
          ].map(({ path, page }) => (
            <Route
              key={path}
              path={path}
              element={
                <RequireAuth>
                  <AppLayout>{page}</AppLayout>
                </RequireAuth>
              }
            />
          ))}
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
