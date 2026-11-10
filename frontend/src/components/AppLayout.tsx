import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router'
import { useAuth } from '../features/auth/AuthContext'
import { PROFILE_UPDATED } from '../features/profile/ProfilePage'
import { apiFetch } from '../services/api'
import styles from './AppLayout.module.css'

const LINKS = [
  { to: '/dashboard', label: 'Overview' },
  { to: '/statements', label: 'Statements' },
  { to: '/transactions', label: 'Transactions' },
  { to: '/categories', label: 'Categories' },
]

const ACCOUNT_LINKS = [
  { to: '/profile#details', label: 'Edit profile' },
  { to: '/profile#preferences', label: 'Preferences' },
  { to: '/profile#password', label: 'Change password' },
]

type Me = { email: string; fullName: string | null }

/** Signed-in page frame: header with navigation and account, then the page. */
export function AppLayout({ children }: { children: ReactNode }) {
  const { logout } = useAuth()
  const [me, setMe] = useState<Me | null>(null)
  const [open, setOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    apiFetch('/user/me').then(async (res) => {
      if (res.ok) setMe(await res.json())
    })
    const onUpdate = (e: Event) => setMe((e as CustomEvent<Me>).detail)
    window.addEventListener(PROFILE_UPDATED, onUpdate)
    return () => window.removeEventListener(PROFILE_UPDATED, onUpdate)
  }, [])

  useEffect(() => {
    if (!open) return
    const onPointer = (e: PointerEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false)
        buttonRef.current?.focus()
      }
    }
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const name = me?.fullName?.trim() || me?.email

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
          {name && (
            <div ref={menuRef} className={styles.menuWrap}>
              <button
                ref={buttonRef}
                type="button"
                className={styles.menuButton}
                aria-expanded={open}
                aria-controls="account-menu"
                onClick={() => setOpen((o) => !o)}
              >
                <span className={styles.name}>{name}</span>
                <svg
                  aria-hidden="true"
                  className={styles.chevron}
                  viewBox="0 0 16 16"
                  width="16"
                  height="16"
                >
                  <path
                    d="M4 6l4 4 4-4"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </button>
              {open && (
                <div id="account-menu" className={styles.menu}>
                  {me?.fullName && (
                    <p className={styles.menuEmail}>{me.email}</p>
                  )}
                  <ul>
                    {ACCOUNT_LINKS.map((l) => (
                      <li key={l.to}>
                        <Link
                          to={l.to}
                          className={styles.menuItem}
                          onClick={() => setOpen(false)}
                        >
                          {l.label}
                        </Link>
                      </li>
                    ))}
                  </ul>
                  <Link
                    to="/profile#delete-account"
                    className={`${styles.menuItem} ${styles.menuDanger}`}
                    onClick={() => setOpen(false)}
                  >
                    Delete account
                  </Link>
                </div>
              )}
            </div>
          )}
          <button type="button" className={styles.logout} onClick={logout}>
            Log out
          </button>
        </div>
      </header>
      <main className={styles.main}>{children}</main>
    </div>
  )
}
