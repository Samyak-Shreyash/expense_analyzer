import { useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { flushSync } from 'react-dom'
import shared from '../../components/shared.module.css'
import { ApiError } from '../../services/finance'
import styles from './TransactionsPage.module.css'

/**
 * Shows `children` (the name, usually a link) with a Rename button that appears on hover or keyboard focus and
 * opens an inline editor; focus returns to the button.
 */
export function RenameOnHover({
  name,
  label,
  onSave,
  children,
}: {
  name: string
  /** What is renamed, for the button's accessible name: "merchant" or "person". */
  label: string
  onSave: (name: string) => Promise<void>
  children: ReactNode
}) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(name)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const trigger = useRef<HTMLButtonElement>(null)

  function close() {
    flushSync(() => setEditing(false))
    trigger.current?.focus()
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!draft.trim()) return
    setSaving(true)
    setError(null)
    try {
      await onSave(draft.trim())
      close()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not rename.')
    } finally {
      setSaving(false)
    }
  }

  if (!editing) {
    return (
      <span className={styles.renamable}>
        {children}
        <button
          ref={trigger}
          type="button"
          className={`${shared.quiet} ${styles.renameButton}`}
          aria-label={`Rename ${label} ${name}`}
          onClick={() => {
            setDraft(name)
            setError(null)
            setEditing(true)
          }}
        >
          Rename
        </button>
      </span>
    )
  }
  return (
    <form className={styles.categoryEditor} onSubmit={submit}>
      <input
        className={shared.input}
        aria-label={`New name for ${name}`}
        value={draft}
        maxLength={100}
        autoFocus
        autoComplete="off"
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            e.preventDefault()
            close()
          }
        }}
      />
      <div className={styles.editorActions}>
        <button
          type="submit"
          className={shared.button}
          disabled={saving || !draft.trim()}
        >
          Save
        </button>
        <button type="button" className={shared.quiet} onClick={close}>
          Cancel
        </button>
      </div>
      {error && (
        <p className={shared.error} role="alert">
          {error}
        </p>
      )}
    </form>
  )
}
