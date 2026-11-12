import { rupees } from '../../lib/format'
import { type Transaction } from '../../services/finance'

export const LOAN = 'Loan'
export const LOAN_EMI = 'Loan EMI'

/** The list tag of a Loan EMI payment: whether it is linked to its loan; null for other rows. */
export function emiLabel(t: Transaction): string | null {
  if (t.direction !== 'DEBIT' || t.category !== LOAN_EMI) return null
  if (!t.loanId) return 'EMI · not linked'
  return t.loanInterest !== null
    ? `EMI · linked · ${rupees(t.loanInterest)} interest`
    : 'EMI · linked'
}
