import { useId, useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import shared from '../../components/shared.module.css'
import {
  ApiError,
  createCategory,
  setTransactionCategory,
  type Category,
  type Transaction,
} from '../../services/finance'
import styles from './TransactionsPage.module.css'

type Props = {
  t: Transaction
  categories: Category[]
  onCreated: (c: Category) => void
  onSaved: (t: Transaction) => void
  onFilter: (category: string) => void
}

/** Category of one transaction: click to pick from the list or type a new one. */
export function CategoryCell({
  t,
  categories,
  onCreated,
  onSaved,
  onFilter,
}: Props) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState('')
  const [countsAsSpending, setCountsAsSpending] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const trigger = useRef<HTMLButtonElement>(null)
  const listId = useId()

  const label = t.category ?? 'Uncategorized'
  const existing = categories.find(
    (c) => c.name.toLowerCase() === name.trim().toLowerCase(),
  )
  const isNew = name.trim() !== '' && !existing

  function open() {
    setName(t.categoryOverridden ? label : '')
    setError(null)
    setEditing(true)
  }

  function close() {
    flushSync(() => setEditing(false))
    trigger.current?.focus()
  }

  async function save(categoryId: string | null) {
    setSaving(true)
    setError(null)
    try {
      onSaved(await setTransactionCategory(t.id, categoryId))
      close()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save that.')
    } finally {
      setSaving(false)
    }
  }

  async function submit() {
    if (existing) return save(existing.id)
    try {
      const created = await createCategory(name.trim(), countsAsSpending)
      onCreated(created)
      return save(created.id)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save that.')
    }
  }

  if (!editing) {
    return (
      <button
        ref={trigger}
        type="button"
        className={shared.quiet}
        aria-label={`Change category, now ${label}`}
        onClick={open}
      >
        {t.category ?? <span className={shared.muted}>Uncategorised</span>}
      </button>
    )
  }

  return (
    <form
      className={styles.categoryEditor}
      onSubmit={(e) => {
        e.preventDefault()
        if (name.trim()) submit()
      }}
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          e.preventDefault()
          close()
        }
      }}
    >
      <label>
        <span className={shared.label}>Category for this transaction</span>
        <input
          className={shared.input}
          list={listId}
          value={name}
          maxLength={50}
          autoFocus
          autoComplete="off"
          placeholder="Pick or type a new one"
          onChange={(e) => setName(e.target.value)}
        />
      </label>
      <datalist id={listId}>
        {categories.map((c) => (
          <option key={c.id} value={c.name} />
        ))}
      </datalist>
      {isNew && (
        <label className={styles.inline}>
          <input
            type="checkbox"
            checked={countsAsSpending}
            onChange={(e) => setCountsAsSpending(e.target.checked)}
          />
          New category counts as spending
        </label>
      )}
      <div className={styles.editorActions}>
        <button
          type="submit"
          className={shared.button}
          disabled={saving || !name.trim()}
        >
          {isNew ? `Create "${name.trim()}" and use it` : 'Save'}
        </button>
        {t.categoryOverridden && (
          <button
            type="button"
            className={shared.quiet}
            disabled={saving}
            onClick={() => save(null)}
          >
            Use merchant/person default
          </button>
        )}
        <button type="button" className={shared.quiet} onClick={close}>
          Cancel
        </button>
        <button
          type="button"
          className={shared.quiet}
          onClick={() => onFilter(label)}
        >
          Show only {label}
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
