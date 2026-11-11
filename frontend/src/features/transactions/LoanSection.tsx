import { useEffect, useState } from 'react'
import shared from '../../components/shared.module.css'
import { dayLabel, rupees } from '../../lib/format'
import {
  listTransactions,
  setLoan,
  setLoanClosed,
  type Transaction,
} from '../../services/finance'
import styles from './TransactionsPage.module.css'

export const LOAN = 'Loan'
export const LOAN_EMI = 'Loan EMI'

const PICK = ''

/**
 * Loan controls. On money out categorised Loan EMI: link it to a loan (its
 * lender's other EMIs follow) or unlink it. On money in categorised Loan: what
 * its linked EMIs repaid, and closing it. Nothing for other rows.
 */
export function LoanSection({
  t,
  save,
  reload,
}: {
  t: Transaction
  save: (task: () => Promise<void>, done: string) => void
  reload: () => void
}) {
  const emi = t.direction === 'DEBIT' && t.category === LOAN_EMI
  const loan = t.direction === 'CREDIT' && t.category === LOAN
  const [loans, setLoans] = useState<Transaction[] | null>(null)
  const [picked, setPicked] = useState(PICK)
  const [repayments, setRepayments] = useState<Transaction[] | null>(null)

  useEffect(() => {
    if (!loan) return
    let live = true
    listTransactions({ loanId: t.id }).then(
      (page) => live && setRepayments(page.items),
      () => live && setRepayments([]),
    )
    return () => {
      live = false
    }
  }, [loan, t.id])

  if (emi) {
    return (
      <div className={styles.bulk}>
        <span className={shared.label}>Loan</span>
        {t.loanId ? (
          <>
            <span className={shared.muted}>
              Repays a loan
              {t.loanInterest !== null &&
                ` · ${rupees(t.loanInterest)} of it is interest`}
            </span>
            <button
              type="button"
              className={shared.button}
              onClick={() =>
                save(async () => {
                  await setLoan(t.id, null)
                  reload()
                }, 'No longer linked to a loan.')
              }
            >
              Unlink from loan
            </button>
          </>
        ) : loans === null ? (
          <button
            type="button"
            className={shared.button}
            onClick={() =>
              save(async () => {
                const open = (
                  await listTransactions({
                    category: LOAN,
                    direction: 'CREDIT',
                  })
                ).items.filter((l) => !l.loanClosed && l.date <= t.date)
                setLoans(open)
                setPicked(open[0]?.id ?? PICK)
              }, '')
            }
          >
            Link to a loan
          </button>
        ) : loans.length === 0 ? (
          <span className={shared.muted}>
            No open loan before this payment. Categorise the money you borrowed
            as {LOAN} first.
          </span>
        ) : (
          <>
            <label>
              <span className={shared.label}>The loan</span>
              <select
                className={shared.input}
                value={picked}
                onChange={(e) => setPicked(e.target.value)}
              >
                {loans.map((l) => (
                  <option key={l.id} value={l.id}>
                    {rupees(l.amount)} · {dayLabel(l.date)} ·{' '}
                    {l.normalizedDescription ?? l.description}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              className={shared.button}
              disabled={picked === PICK}
              onClick={() =>
                save(async () => {
                  await setLoan(t.id, picked)
                  reload()
                }, 'Linked. This lender’s other and future EMIs link to this loan too.')
              }
            >
              Link
            </button>
          </>
        )}
      </div>
    )
  }

  if (!loan) return null
  const repaid = (repayments ?? []).reduce((sum, r) => sum + r.amount, 0)
  const interest = (repayments ?? []).reduce(
    (sum, r) => sum + (r.loanInterest ?? 0),
    0,
  )
  return (
    <div className={styles.bulk}>
      <span className={shared.label}>
        Loan{t.loanClosed ? ' (closed)' : ''}
      </span>
      {repayments !== null && (
        <span className={shared.muted}>
          {repayments.length} EMI{repayments.length === 1 ? '' : 's'} · repaid{' '}
          {rupees(repaid)} of {rupees(t.amount)}
          {interest > 0
            ? ` · ${rupees(interest)} interest`
            : ` · ${rupees(t.amount - repaid)} outstanding`}
        </span>
      )}
      <button
        type="button"
        className={shared.button}
        onClick={() =>
          save(
            async () => {
              await setLoanClosed(t.id, !t.loanClosed)
              reload()
            },
            t.loanClosed
              ? 'Reopened. Its lender’s EMIs link to it again.'
              : 'Closed. Later payments to its lender are no longer treated as EMIs.',
          )
        }
      >
        {t.loanClosed ? 'Reopen loan' : 'Close loan'}
      </button>
    </div>
  )
}
