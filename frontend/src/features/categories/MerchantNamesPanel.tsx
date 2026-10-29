import { useEffect, useId, useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import shared from '../../components/shared.module.css'
import {
  listMerchantNames,
  setMerchantNameShared,
  setMerchantNameSharing,
  type MerchantName,
  type MerchantNames,
  type NotShareableReason,
} from '../../services/finance'
import styles from './CategoriesPage.module.css'

export const SHARING_WARNING =
  'Shared names must be real merchant names. Offensive names are reported and can lose you the ability to share.'

const REASONS: Record<NotShareableReason, string> = {
  TRANSFER: 'Transfer to a person',
  NOT_UPI_MERCHANT: 'Not a UPI merchant payment',
  NAME_NOT_ALLOWED: "Names with digits, links or @ can't be shared",
  NAME_OFFENSIVE: "This name can't be shared",
}

const message = (e: unknown) =>
  e instanceof Error ? e.message : 'Could not save that.'

/** The user's own merchant names, the account-level sharing switch and a per-name share opt-out. */
export function MerchantNamesPanel() {
  const [data, setData] = useState<MerchantNames | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const toggle = useRef<HTMLInputElement>(null)
  const explainId = useId()

  useEffect(() => {
    let live = true
    listMerchantNames()
      .then((d) => live && setData(d))
      .catch(() => live && setLoadError(true))
    return () => {
      live = false
    }
  }, [])

  async function run(task: () => Promise<void>) {
    setError(null)
    try {
      await task()
    } catch (e) {
      setError(message(e))
    }
  }

  function cancel() {
    flushSync(() => setConfirming(false))
    toggle.current?.focus()
  }

  async function setSharing(enabled: boolean) {
    flushSync(() => setConfirming(false))
    toggle.current?.focus()
    await run(async () => setData(await setMerchantNameSharing(enabled)))
  }

  const replace = (item: MerchantName) =>
    setData((d) => ({
      ...d!,
      items: d!.items.map((x) => (x.id === item.id ? item : x)),
    }))

  if (loadError)
    return (
      <p className={shared.error} role="alert">
        Could not load your merchant names. Reload the page to try again.
      </p>
    )
  if (!data) return <p className={shared.muted}>Loading…</p>

  const on = data.shareMerchantNames
  const revoked = data.sharingRevoked
  const toShare = data.items.filter((i) => i.shareable && !i.optedOut).length

  return (
    <>
      {revoked && (
        <p className={shared.error} role="status">
          Sharing has been turned off for your account because names you shared
          were reported as offensive.
        </p>
      )}
      <label className={styles.check}>
        <input
          ref={toggle}
          type="checkbox"
          role="switch"
          checked={on}
          disabled={revoked || confirming}
          aria-describedby={explainId}
          onChange={(e) =>
            e.target.checked ? setConfirming(true) : setSharing(false)
          }
        />
        Share my merchant names to improve suggestions for other users
      </label>
      <div id={explainId}>
        <p className={shared.muted}>
          Only the merchant name and the payee it matches are shared — never
          your transactions, amounts or identity. Suggestions are shown to
          others only after at least 2 users agree.
        </p>
        <p className={shared.muted}>{SHARING_WARNING}</p>
      </div>
      {confirming && (
        <div
          className={styles.actions}
          role="group"
          aria-label="Confirm sharing"
          onKeyDown={(e) => e.key === 'Escape' && cancel()}
        >
          <p>
            {toShare === 1
              ? '1 name will be shared.'
              : `${toShare} names will be shared.`}
          </p>
          <button
            type="button"
            className={shared.button}
            onClick={() => setSharing(true)}
          >
            Confirm
          </button>
          <button
            type="button"
            className={shared.quiet}
            autoFocus
            onClick={cancel}
          >
            Cancel
          </button>
        </div>
      )}
      {error && (
        <p className={shared.error} role="alert">
          {error}
        </p>
      )}
      {data.items.length === 0 ? (
        <p className={shared.muted}>
          You haven't named any merchants yet. Use “Add merchant” on a
          not-matched transaction.
        </p>
      ) : (
        <div className={shared.tableWrap}>
          <table className={shared.table}>
            <thead>
              <tr>
                <th>Name</th>
                <th>Payee</th>
                <th>Transactions</th>
                <th>Share</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((i) => (
                <tr key={i.id}>
                  <td>
                    <span className={styles.name}>
                      {i.name}
                      {i.reported && (
                        <span className={shared.error}>Reported</span>
                      )}
                    </span>
                  </td>
                  <td>{i.payee}</td>
                  <td>{i.transactionCount}</td>
                  <td>
                    <ShareCell
                      item={i}
                      on={on}
                      revoked={revoked}
                      onSave={(sharedNow) =>
                        run(async () =>
                          replace(await setMerchantNameShared(i.id, sharedNow)),
                        )
                      }
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

function ShareCell({
  item,
  on,
  revoked,
  onSave,
}: {
  item: MerchantName
  on: boolean
  revoked: boolean
  onSave: (shared: boolean) => void
}) {
  const hintId = useId()
  const hint = !item.shareable
    ? REASONS[item.notShareableReason ?? 'NOT_UPI_MERCHANT']
    : revoked
      ? 'Sharing is off for your account'
      : !on
        ? 'Turn on sharing above'
        : null
  return (
    <span className={styles.name}>
      <label className={styles.check}>
        <input
          type="checkbox"
          checked={on && item.shared}
          disabled={!on || revoked || !item.shareable}
          aria-describedby={hintId}
          onChange={(e) => onSave(e.target.checked)}
        />
        <span className={shared.visuallyHidden}>
          {item.reported && item.shared ? 'Unshare' : 'Share'} {item.name}
        </span>
      </label>
      <span id={hintId} className={shared.muted}>
        {hint}
        {(item.reported || item.notShareableReason === 'NAME_OFFENSIVE') && (
          <> {SHARING_WARNING}</>
        )}
      </span>
    </span>
  )
}
