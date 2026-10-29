import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { NavLink } from 'react-router'
import { useAuth } from '../features/auth/AuthContext'
import { apiFetch } from '../services/api'
import styles from './AppLayout.module.css'

const LINKS = [
  { to: '/dashboard', label: 'Overview' },
  { to: '/statements', label: 'Statements' },
  { to: '/transactions', label: 'Transactions' },
]

/** Signed-in page frame: header with navigation and account, then the page. */
export function AppLayout({ children }: { children: ReactNode }) {
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
        <nav aria-label="Main" className={styles.nav}>
          {LINKS.map((l) => (
            <NavLink key={l.to} to={l.to} className={styles.navLink}>
              {l.label}
            </NavLink>
          ))}
        </nav>
        <div className={styles.account}>
          {email && <span className={styles.email}>{email}</span>}
          <button type="button" className={styles.logout} onClick={logout}>
            Log out
          </button>
        </div>
      </header>
      <main className={styles.main}>{children}</main>
    </div>
  )
}
