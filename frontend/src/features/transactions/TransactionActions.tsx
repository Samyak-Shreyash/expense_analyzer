import { useState } from 'react'
import shared from '../../components/shared.module.css'
import {
  setCounterparty,
  setMerchantCategory,
  setTransactionCategory,
  updatePerson,
  type Category,
  type Transaction,
} from '../../services/finance'
import styles from './TransactionsPage.module.css'

const PICK = ''
const CLEAR = '__clear'

/**
 * Detail-row controls: this transaction's category, the default for its
 * merchant or person, and who the money went to.
 * `onSaved` receives the changed row; `reload` refetches because some changes
 * touch other rows (merchant default, counterparty).
 */
export function TransactionActions({
  t,
  categories,
  onSaved,
  reload,
}: {
  t: Transaction
  categories: Category[]
  onSaved: (t: Transaction) => void
  reload: () => void
}) {
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(
    null,
  )
  const [bulk, setBulk] = useState(PICK)
  const [isPerson, setIsPerson] = useState(t.personId !== null)
  const [name, setName] = useState(
    t.personName ?? t.normalizedDescription ?? t.description,
  )

  const holder = t.personId ? t.personName : t.merchantName

  async function save(task: () => Promise<void>, done: string) {
    setStatus(null)
    try {
      await task()
      setStatus({ ok: true, text: done })
    } catch (e) {
      setStatus({
        ok: false,
        text: e instanceof Error ? e.message : 'Could not save that.',
      })
    }
  }

  const options = categories.map((c) => (
    <option key={c.id} value={c.id}>
      {c.name}
      {c.countsAsSpending ? '' : ' (not spending)'}
    </option>
  ))

  return (
    <div className={styles.actions}>
      <label>
        <span className={shared.label}>Category for this transaction</span>
        <select
          className={shared.input}
          value={t.categoryOverridden ? (t.categoryId ?? '') : ''}
          onChange={(e) =>
            save(
              async () =>
                onSaved(
                  await setTransactionCategory(t.id, e.target.value || null),
                ),
              'Category saved.',
            )
          }
        >
          <option value="">Use merchant/person default</option>
          {options}
        </select>
      </label>

      {(t.personId || t.merchantId) && (
        <div className={styles.bulk}>
          <label>
            <span className={shared.label}>Set category for all {holder}</span>
            <select
              className={shared.input}
              value={bulk}
              onChange={(e) => setBulk(e.target.value)}
            >
              <option value={PICK}>Choose a category</option>
              <option value={CLEAR}>Clear the default</option>
              {options}
            </select>
          </label>
          <button
            type="button"
            className={shared.button}
            disabled={bulk === PICK}
            onClick={() =>
              save(async () => {
                const id = bulk === CLEAR ? null : bulk
                if (t.personId)
                  await updatePerson(t.personId, t.personName!, id)
                else await setMerchantCategory(t.merchantId!, id)
                setBulk(PICK)
                reload()
              }, `Default category saved for ${holder}.`)
            }
          >
            Apply to all
          </button>
        </div>
      )}

      <fieldset className={styles.counterparty}>
        <legend className={shared.label}>Who was paid</legend>
        <label className={styles.option}>
          <input
            type="radio"
            name={`counterparty-${t.id}`}
            checked={isPerson}
            onChange={() => setIsPerson(true)}
          />
          This is a person
        </label>
        <label className={styles.option}>
          <input
            type="radio"
            name={`counterparty-${t.id}`}
            checked={!isPerson}
            onChange={() => setIsPerson(false)}
          />
          This is a merchant
        </label>
        {isPerson && (
          <label>
            <span className={shared.label}>Person's name</span>
            <input
              className={shared.input}
              value={name}
              maxLength={100}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
        )}
        <button
          type="button"
          className={shared.button}
          disabled={isPerson && !name.trim()}
          onClick={() =>
            save(
              async () => {
                onSaved(
                  await setCounterparty(
                    t.id,
                    isPerson ? 'PERSON' : 'MERCHANT',
                    isPerson ? name.trim() : undefined,
                  ),
                )
                reload()
              },
              t.normalizedDescription === null
                ? 'Saved for this transaction.'
                : 'Saved. This applies to every transaction with the same cleaned-up description.',
            )
          }
        >
          Save
        </button>
      </fieldset>

      {/* Always mounted so screen readers announce the text when it is swapped in. */}
      <p className={shared.muted} role="status">
        {status?.ok ? status.text : ''}
      </p>
      {status && !status.ok && (
        <p className={shared.error} role="alert">
          {status.text}
        </p>
      )}
    </div>
  )
}
