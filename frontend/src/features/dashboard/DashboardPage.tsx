import { useEffect, useState } from 'react'
import { apiFetch } from '../../services/api'
import { useAuth } from '../auth/AuthContext'

export function DashboardPage() {
  const { logout } = useAuth()
  const [email, setEmail] = useState<string | null>(null)

  useEffect(() => {
    apiFetch('/user/me').then(async (res) => {
      if (res.ok) setEmail((await res.json()).email)
    })
  }, [])

  return (
    <main>
      <h1>Dashboard</h1>
      {email && <p>Signed in as {email}</p>}
      <button onClick={logout}>Log out</button>
    </main>
  )
}
