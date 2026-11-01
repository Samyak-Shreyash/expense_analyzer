import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import shared from '../../components/shared.module.css'
import { getProfile, updateProfile, type Profile } from '../../services/finance'
import styles from './ProfilePage.module.css'

/** Shows the signed-in user's details and lets them edit name and preferences. */
export function ProfilePage() {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [fullName, setFullName] = useState('')
  const [theme, setTheme] = useState('light')
  const [currency, setCurrency] = useState('inr')
  const [notifications, setNotifications] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const show = (p: Profile) => {
    setProfile(p)
    setFullName(p.fullName ?? '')
    setTheme(p.preferences.theme ?? 'light')
    setCurrency(p.preferences.currency ?? 'inr')
    setNotifications(p.preferences.notifications ?? true)
  }

  useEffect(() => {
    getProfile().then(show, () => setLoadError(true))
  }, [])

  async function save(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    setSaved(false)
    try {
      show(await updateProfile(fullName, { theme, currency, notifications }))
      setSaved(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save that.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <h1 className={shared.title}>Profile</h1>
      {loadError && (
        <p className={shared.error} role="alert">
          Could not load your profile. Try again in a moment.
        </p>
      )}
      {!profile && !loadError && <p className={shared.muted}>Loading...</p>}
      {profile && (
        <form className={styles.form} onSubmit={save}>
          <p className={shared.muted}>
            {profile.email} ({profile.role.toLowerCase()})
          </p>
          <label>
            Full name
            <input
              className={styles.input}
              value={fullName}
              maxLength={255}
              onChange={(e) => setFullName(e.target.value)}
            />
          </label>
          <label>
            Theme
            <select
              className={styles.input}
              value={theme}
              onChange={(e) => setTheme(e.target.value)}
            >
              <option value="light">Light</option>
              <option value="dark">Dark</option>
            </select>
          </label>
          <label>
            Currency
            <select
              className={styles.input}
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
            >
              <option value="inr">INR</option>
              <option value="usd">USD</option>
              <option value="eur">EUR</option>
            </select>
          </label>
          <label>
            <input
              type="checkbox"
              checked={notifications}
              onChange={(e) => setNotifications(e.target.checked)}
            />{' '}
            Notifications
          </label>
          {error && (
            <p className={shared.error} role="alert">
              {error}
            </p>
          )}
          {saved && <p role="status">Profile saved.</p>}
          <button className={shared.button} disabled={saving}>
            {saving ? 'Saving...' : 'Save'}
          </button>
        </form>
      )}
    </>
  )
}
