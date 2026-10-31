import { useState } from 'react'
import type { FormEvent } from 'react'
import { flushSync } from 'react-dom'
import shared from '../../components/shared.module.css'
import styles from './CategoriesPage.module.css'

/** A name with a Rename button that opens an inline editor; focus returns to the button. */
export function EditableName({
  id,
  name,
  label,
  onSave,
}: {
  id: string
  name: string
  label: string
  onSave: (name: string) => Promise<void>
}) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(name)
  const buttonId = `rename-${id}`

  function close() {
    flushSync(() => setEditing(false))
    document.getElementById(buttonId)?.focus()
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    try {
      await onSave(draft.trim())
      close()
    } catch {
      // the page shows the message; keep the editor open to correct the name
    }
  }

  if (!editing) {
    return (
      <span className={styles.name}>
        {name}
        <button
          type="button"
          id={buttonId}
          className={shared.quiet}
          aria-label={`Rename ${label} ${name}`}
          onClick={() => {
            setDraft(name)
            setEditing(true)
          }}
        >
          Rename
        </button>
      </span>
    )
  }
  return (
    <form className={styles.editor} onSubmit={submit}>
      <input
        className={shared.input}
        aria-label={`New name for ${name}`}
        value={draft}
        autoFocus
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => e.key === 'Escape' && close()}
      />
      <button type="submit" className={shared.button}>
        Save
      </button>
      <button type="button" className={shared.quiet} onClick={close}>
        Cancel
      </button>
    </form>
  )
}
