import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import shared from '../../components/shared.module.css'
import { dayLabel, validateStatementFile } from '../../lib/format'
import {
  deleteStatement,
  listStatements,
  uploadStatement,
  type Statement,
} from '../../services/finance'
import styles from './StatementsPage.module.css'

const POLL_MS = 2000
const IN_PROGRESS = new Set(['UPLOADED', 'QUEUED', 'PROCESSING'])

/** Backend processing codes (statements.processing_error) in plain language. */
const PROCESSING_ERRORS: Record<string, string> = {
  MISSING_HEADER:
    'No header row with Date, Description and Amount (or Debit and Credit) columns was found.',
  NO_VALID_ROWS: 'No row could be read. Check the date and amount columns.',
  UNREADABLE_FILE:
    'The file could not be read. Export it again from your bank.',
  TOO_MANY_ROWS:
    'The file has more than 50,000 rows. Split it and upload the parts.',
  TOO_MANY_COLUMNS: 'A row has more than 50 columns.',
  PARSE_TIMEOUT:
    'The file took too long to read. Split it and upload the parts.',
  FILE_MISSING: 'The stored file is missing. Upload it again.',
}

function statusText(s: Statement) {
  if (IN_PROGRESS.has(s.status)) return { label: 'Processing', note: null }
  if (s.status === 'FAILED') {
    return {
      label: 'Failed',
      note:
        (s.processingError && PROCESSING_ERRORS[s.processingError]) ??
        'Processing failed.',
    }
  }
  const skipped = s.processingError?.match(/^SKIPPED_ROWS:(\d+)$/)?.[1]
  return {
    label: 'Imported',
    note: skipped
      ? `${skipped} row${skipped === '1' ? " couldn't" : "s couldn't"} be read and ${skipped === '1' ? 'was' : 'were'} skipped.`
      : null,
  }
}

export function StatementsPage() {
  const [statements, setStatements] = useState<Statement[] | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [progress, setProgress] = useState<number | null>(null)
  const [dragging, setDragging] = useState(false)
  const [confirming, setConfirming] = useState<string | null>(null)
  // A password-protected PDF waiting for its password.
  const [locked, setLocked] = useState<File | null>(null)
  const [password, setPassword] = useState('')
  const input = useRef<HTMLInputElement>(null)
  // Bumped to refetch: after an upload or delete, and while polling.
  const [version, setVersion] = useState(0)
  const reload = () => setVersion((v) => v + 1)

  useEffect(() => {
    let live = true
    listStatements()
      .then((page) => {
        if (!live) return
        setStatements(page.items)
        setLoadError(false)
      })
      .catch(() => live && setLoadError(true))
    return () => {
      live = false
    }
  }, [version])

  // Poll while anything is still being processed.
  const pending = statements?.some((s) => IN_PROGRESS.has(s.status)) ?? false
  useEffect(() => {
    if (!pending) return
    const id = setTimeout(() => setVersion((v) => v + 1), POLL_MS)
    return () => clearTimeout(id)
  }, [pending, statements])

  async function upload(file: File, pdfPassword?: string) {
    const invalid = validateStatementFile(file)
    setUploadError(invalid)
    if (invalid) return
    setProgress(0)
    const outcome = await uploadStatement(file, setProgress, pdfPassword)
    setProgress(null)
    setPassword('')
    if (input.current) input.current.value = ''
    const needsPassword =
      !outcome.ok &&
      (outcome.reason === 'PASSWORD_REQUIRED' ||
        outcome.reason === 'WRONG_PASSWORD')
    setLocked(needsPassword ? file : null)
    if (outcome.ok) {
      reload()
    } else {
      setUploadError(outcome.message)
    }
  }

  async function remove(id: string) {
    setConfirming(null)
    await deleteStatement(id)
    reload()
  }

  return (
    <>
      <h1 className={shared.title}>Statements</h1>
      <p className={shared.lead}>
        Upload a CSV, Excel or PDF statement from your bank. Each transaction is
        read once, even if your statements overlap.
      </p>

      <div
        className={styles.drop}
        data-dragging={dragging || undefined}
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragging(false)
          const file = e.dataTransfer.files[0]
          if (file) upload(file)
        }}
      >
        <p className={styles.dropText}>Drop a statement here, or</p>
        <label className={shared.button + ' ' + styles.choose}>
          Choose a file
          <input
            ref={input}
            type="file"
            accept=".csv,.xls,.xlsx,.pdf"
            className={shared.visuallyHidden}
            disabled={progress !== null}
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (file) upload(file)
            }}
          />
        </label>
        <p className={styles.hint}>CSV, .xls, .xlsx or PDF, up to 20 MB</p>
        {progress !== null && (
          <div className={styles.progress}>
            <progress value={progress} max={1} aria-label="Upload progress" />
            <span>{Math.round(progress * 100)}%</span>
          </div>
        )}
      </div>

      {uploadError && (
        <p className={shared.error} role="alert">
          {uploadError}
        </p>
      )}

      {locked && (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            upload(locked, password)
          }}
        >
          <label>
            Password for {locked.name}{' '}
            <input
              type="password"
              autoComplete="off"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>{' '}
          <button
            type="submit"
            className={shared.button}
            disabled={progress !== null}
          >
            Unlock and upload
          </button>
        </form>
      )}

      <section className={shared.section} aria-labelledby="uploaded">
        <h2 id="uploaded">Uploaded statements</h2>
        {loadError && (
          <p className={shared.error}>
            Could not load your statements. Reload the page to try again.
          </p>
        )}
        {statements?.length === 0 && (
          <p className={shared.muted}>Nothing uploaded yet.</p>
        )}
        {statements && statements.length > 0 && (
          <div className={shared.tableWrap}>
            <table className={shared.table}>
              <thead>
                <tr>
                  <th>File</th>
                  <th>Uploaded</th>
                  <th>Status</th>
                  <th>
                    <span className={shared.visuallyHidden}>Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {statements.map((s) => {
                  const { label, note } = statusText(s)
                  return (
                    <tr key={s.id}>
                      <td className={styles.file}>{s.fileName}</td>
                      <td>{dayLabel(s.createdAt.slice(0, 10))}</td>
                      <td>
                        <span className={styles.status} data-status={label}>
                          {label}
                        </span>
                        {note && <p className={styles.note}>{note}</p>}
                      </td>
                      <td className={styles.actions}>
                        {s.status === 'COMPLETED' && (
                          <Link to={`/transactions?statementId=${s.id}`}>
                            View transactions
                          </Link>
                        )}
                        {confirming === s.id ? (
                          <>
                            <button
                              type="button"
                              className={shared.danger}
                              onClick={() => remove(s.id)}
                            >
                              Delete it and its transactions
                            </button>
                            <button
                              type="button"
                              className={shared.quiet}
                              onClick={() => setConfirming(null)}
                            >
                              Keep
                            </button>
                          </>
                        ) : (
                          <button
                            type="button"
                            className={shared.danger}
                            onClick={() => setConfirming(s.id)}
                          >
                            Delete
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  )
}
