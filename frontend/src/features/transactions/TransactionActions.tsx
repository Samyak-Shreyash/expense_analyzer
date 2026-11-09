import { useState } from 'react'
import shared from '../../components/shared.module.css'
import { dayLabel, rupees } from '../../lib/format'
import {
  listTransactions,
  setCounterparty,
  setMerchantCategory,
  setPersonCategory,
  setTransactionCategory,
  setTransactionMerchant,
  setReversal,
  setTransfer,
  type Category,
  type Transaction,
} from '../../services/finance'
import styles from './TransactionsPage.module.css'

const PICK = ''
const CLEAR = '__clear'
/** How far from a failed payment its refund is looked for, either way. */
const REFUND_DAYS = 45
/** How far a payment can be above its refund: the rest is charges. Same limit as the server. */
const REFUND_CHARGE_PERCENT = 10

const shift = (date: string, days: number) => {
  const d = new Date(`${date}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

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
  const [candidates, setCandidates] = useState<Transaction[] | null>(null)
  const [partner, setPartner] = useState(PICK)
  const [isPerson, setIsPerson] = useState(t.personId !== null)
  const [name, setName] = useState(
    t.personName ?? t.payeeName ?? t.normalizedDescription ?? t.description,
  )

  const [merchant, setMerchant] = useState(
    t.merchantName ?? t.personName ?? t.payeeName ?? '',
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
        {!isPerson && (
          <label>
            <span className={shared.label}>Merchant's name</span>
            <input
              className={shared.input}
              value={merchant}
              maxLength={100}
              onChange={(e) => setMerchant(e.target.value)}
            />
          </label>
        )}
        <button
          type="button"
          className={shared.button}
          disabled={!(isPerson ? name : merchant).trim()}
          onClick={() =>
            save(
              async () => {
                onSaved(
                  isPerson
                    ? await setCounterparty(t.id, 'PERSON', name.trim())
                    : // A named merchant is remembered for the payee, even with no merchant rule for it.
                      await setTransactionMerchant(
                        t.id,
                        merchant.trim(),
                        false,
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
                // Money in and money out keep separate defaults.
                const credit = t.direction === 'CREDIT'
                if (t.personId) await setPersonCategory(t.personId, id, credit)
                else await setMerchantCategory(t.merchantId!, id, credit)
                setBulk(PICK)
                reload()
              }, `Default category saved for ${holder}.`)
            }
          >
            Apply to all
          </button>
        </div>
      )}

      <div className={styles.bulk}>
        <span className={shared.label}>Between your own accounts</span>
        <button
          type="button"
          className={shared.button}
          onClick={() =>
            save(
              async () => {
                await setTransfer(t.id, !t.transfer)
                reload()
              },
              t.transfer
                ? 'No longer counted as a transfer.'
                : 'Marked as a transfer between your accounts.',
            )
          }
        >
          {t.transfer
            ? 'Not a transfer between my accounts'
            : 'Mark as a transfer between my accounts'}
        </button>
      </div>

      <div className={styles.bulk}>
        <span className={shared.label}>Failed payment and refund</span>
        {t.reversalPairId ? (
          <button
            type="button"
            className={shared.button}
            onClick={() =>
              save(async () => {
                await setReversal(t.id, null)
                reload()
              }, 'No longer marked as failed and refunded.')
            }
          >
            Not a failed payment and refund
          </button>
        ) : candidates === null ? (
          <button
            type="button"
            className={shared.button}
            onClick={() =>
              save(async () => {
                const search = async (minAmount: number, maxAmount: number) =>
                  (
                    await listTransactions({
                      direction: t.direction === 'DEBIT' ? 'CREDIT' : 'DEBIT',
                      minAmount,
                      maxAmount,
                      from: shift(t.date, -REFUND_DAYS),
                      to: shift(t.date, REFUND_DAYS),
                    })
                  ).items.filter((x) => x.id !== t.id)
                let found = await search(t.amount, t.amount)
                if (found.length === 0) {
                  // No exact match: the refund may have come back less charges.
                  const paise = Math.round(t.amount * 100)
                  const scale = 1 + REFUND_CHARGE_PERCENT / 100
                  found = (
                    t.direction === 'DEBIT'
                      ? await search(Math.ceil(paise / scale) / 100, t.amount)
                      : await search(t.amount, Math.floor(paise * scale) / 100)
                  ).sort(
                    (a, b) =>
                      Math.abs(a.amount - t.amount) -
                      Math.abs(b.amount - t.amount),
                  )
                }
                setCandidates(found)
                setPartner(found[0]?.id ?? PICK)
              }, '')
            }
          >
            {t.direction === 'DEBIT'
              ? 'Mark as failed and refunded'
              : 'Mark as the refund of a failed payment'}
          </button>
        ) : candidates.length === 0 ? (
          <span className={shared.muted}>
            No {t.direction === 'DEBIT' ? 'money in' : 'money out'} of{' '}
            {rupees(t.amount)}, or close to it after charges, within{' '}
            {REFUND_DAYS} days of this one.
          </span>
        ) : (
          <>
            <label>
              <span className={shared.label}>
                {t.direction === 'DEBIT' ? 'The refund' : 'The failed payment'}
              </span>
              <select
                className={shared.input}
                value={partner}
                onChange={(e) => setPartner(e.target.value)}
              >
                {candidates.map((c) => (
                  <option key={c.id} value={c.id}>
                    {dayLabel(c.date)} ·{' '}
                    {c.normalizedDescription ?? c.description}
                    {c.amount !== t.amount &&
                      ` · ${rupees(Math.abs(c.amount - t.amount))} charges`}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              className={shared.button}
              disabled={partner === PICK}
              onClick={() =>
                save(async () => {
                  await setReversal(t.id, partner)
                  reload()
                }, 'Marked as failed and refunded. Only any charges count in your totals.')
              }
            >
              Pair them
            </button>
          </>
        )}
      </div>

      {/* Always mounted so screen readers announce the text when it is swapped in. */}
      <p
        className={shared.muted}
        role="status"
        style={status?.ok ? undefined : { margin: 0 }}
      >
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
