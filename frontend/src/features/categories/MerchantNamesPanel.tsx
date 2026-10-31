import { useEffect, useId, useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import shared from '../../components/shared.module.css'
import {
  listMerchantNames,
  renameMerchantName,
  setMerchantCategory,
  setMerchantNameShared,
  setMerchantNameSharing,
  type Category,
  type MerchantName,
  type MerchantNames,
  type NotShareableReason,
} from '../../services/finance'
import styles from './CategoriesPage.module.css'
import { DirectionDefaults } from './DirectionDefaults'
import { EditableName } from './EditableName'

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

/** The user's own merchant names, the account-level sharing switch, a per-name share opt-out and default category. */
export function MerchantNamesPanel({ categories }: { categories: Category[] }) {
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
  // Payees of one merchant sit together; each merchant is listed under its first name.
  const merchants = new Map<string, string>()
  data.items.forEach((i) => {
    if (!merchants.has(i.merchantId)) merchants.set(i.merchantId, i.name)
  })
  const order = [...merchants.keys()]
  const grouped = [...data.items].sort(
    (a, b) => order.indexOf(a.merchantId) - order.indexOf(b.merchantId),
  )
  const groupSize = (merchantId: string) =>
    data.items.filter((x) => x.merchantId === merchantId).length

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
                <th>Move to</th>
                <th>Transactions</th>
                <th>Default categories</th>
                <th>Share</th>
              </tr>
            </thead>
            <tbody>
              {grouped.map((i) => (
                <tr key={i.id}>
                  <td>
                    <span className={styles.name}>
                      <EditableName
                        id={i.id}
                        name={i.name}
                        label="merchant"
                        onSave={async (name) => {
                          setError(null)
                          try {
                            setData(await renameMerchantName(i.id, name))
                          } catch (e) {
                            setError(message(e))
                            throw e
                          }
                        }}
                      />
                      {i.reported && (
                        <span className={shared.error}>Reported</span>
                      )}
                    </span>
                  </td>
                  <td>
                    {i.payee}
                    {groupSize(i.merchantId) > 1 && (
                      <span className={shared.muted}>
                        {' '}
                        · one of {groupSize(i.merchantId)} payees of this
                        merchant
                      </span>
                    )}
                  </td>
                  <td>
                    <select
                      className={shared.input}
                      aria-label={`Move ${i.payee} to merchant`}
                      value=""
                      disabled={merchants.size < 2}
                      onChange={(e) =>
                        run(async () =>
                          setData(
                            await renameMerchantName(i.id, e.target.value),
                          ),
                        )
                      }
                    >
                      <option value="">Move to…</option>
                      {[...merchants]
                        .filter(([id]) => id !== i.merchantId)
                        .map(([id, name]) => (
                          <option key={id} value={name}>
                            {name}
                          </option>
                        ))}
                    </select>
                  </td>
                  <td>{i.transactionCount}</td>
                  <td>
                    <DirectionDefaults
                      name={i.name}
                      out={i.categoryId}
                      into={i.creditCategoryId}
                      categories={categories}
                      onChange={(credit, categoryId) =>
                        run(async () => {
                          await setMerchantCategory(
                            i.merchantId,
                            categoryId,
                            credit,
                          )
                          const field = credit
                            ? 'creditCategoryId'
                            : 'categoryId'
                          // Names sharing a merchant share its categories.
                          setData((d) => ({
                            ...d!,
                            items: d!.items.map((x) =>
                              x.merchantId === i.merchantId
                                ? { ...x, [field]: categoryId }
                                : x,
                            ),
                          }))
                        })
                      }
                    />
                  </td>
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
