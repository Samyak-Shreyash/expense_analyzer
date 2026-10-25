import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import * as api from '../../services/api'

type Status = 'loading' | 'authenticated' | 'anonymous'

type Auth = {
  status: Status
  login: (email: string, password: string) => Promise<boolean>
  logout: () => Promise<void>
}

const AuthContext = createContext<Auth | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading')
  const navigate = useNavigate()

  useEffect(() => {
    api.setAuthHandlers({
      onAuthLost: () => {
        setStatus('anonymous')
        navigate('/login', { replace: true })
      },
      onForbidden: () => navigate('/forbidden'),
    })
    // Restore the session after a reload from the refresh cookie.
    api.refresh().then((ok) => setStatus(ok ? 'authenticated' : 'anonymous'))
  }, [navigate])

  const auth: Auth = {
    status,
    login: async (email, password) => {
      const ok = await api.login(email, password)
      if (ok) setStatus('authenticated')
      return ok
    },
    logout: async () => {
      await api.logout()
      setStatus('anonymous')
      navigate('/login', { replace: true })
    },
  }

  return <AuthContext value={auth}>{children}</AuthContext>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside AuthProvider')
  return auth
}
