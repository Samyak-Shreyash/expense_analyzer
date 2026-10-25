import { useEffect, useState } from 'react'
import { apiFetch } from '../../services/api'
import { useAuth } from '../auth/AuthContext'
import styles from './DashboardPage.module.css'

export function DashboardPage() {
  const { logout } = useAuth()
  const [email, setEmail] = useState<string | null>(null)

  useEffect(() => {
    apiFetch('/user/me').then(async (res) => {
      if (res.ok) setEmail((await res.json()).email)
    })
  }, [])

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <p className={styles.wordmark}>Expense Analyzer</p>
        <div className={styles.account}>
          {email && <span className={styles.email}>{email}</span>}
          <button type="button" className={styles.logout} onClick={logout}>
            Log out
          </button>
        </div>
      </header>
      <main className={styles.main}>
        <h1>Your spending</h1>
        <p className={styles.empty}>
          No statements yet. Statement upload is coming in the next update; your
          spending will appear here once you add one.
        </p>
      </main>
    </div>
  )
}
