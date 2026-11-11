import {
  type Category,
  listTransactions,
  reportSuggestion,
  setCounterparty,
  setMerchantCategory,
  setPersonCategory,
  setReversal,
  setTransactionCategory,
  setTransactionMerchant,
  setDuplicate,
  setLoan,
  setLoanClosed,
  setTransfer,
  type Transaction,
} from '@expense-analyzer/api-client'
import { type ReactNode, useEffect, useState } from 'react'
import { Alert, Text, View } from 'react-native'
import { Button, Chip, ErrorText, Field, useStyles } from '../../components/ui'
import { space } from '../../components/theme'
import { dayLabel, rupees } from '../../lib/format'
import { Picker } from '../categories/controls'

const CLEAR = '__clear'
/** How long after a failed payment its refund is looked for (never before it). Same as the web. */
const REFUND_DAYS = 60
/** With no exact match, how far above its refund a payment is looked for. Same as the web. */
const REFUND_CHARGE_PERCENT = 10

const shift = (date: string, days: number) => {
  const d = new Date(`${date}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

/** Payments (or refunds) that could be the other half of `t`, closest amount first. */
async function reversalCandidates(t: Transaction) {
  const opposite = t.direction === 'DEBIT' ? 'CREDIT' : 'DEBIT'
  // A refund is never dated before its payment.
  const after = t.direction === 'DEBIT' ? { from: t.date } : { to: t.date }
  const search = async (minAmount: number, maxAmount: number) =>
    (
      await listTransactions({
        direction: opposite,
        minAmount,
        maxAmount,
        ...(t.direction === 'DEBIT'
          ? { from: t.date, to: shift(t.date, REFUND_DAYS) }
          : { from: shift(t.date, -REFUND_DAYS), to: t.date }),
      })
    ).items.filter((x) => x.id !== t.id)
  let found = await search(t.amount, t.amount)
  if (found.length === 0) {
    // No exact match: the refund may have come back less charges.
    const paise = Math.round(t.amount * 100)
    const scale = 1 + REFUND_CHARGE_PERCENT / 100
    found =
      t.direction === 'DEBIT'
        ? await search(Math.ceil(paise / scale) / 100, t.amount)
        : await search(t.amount, Math.floor(paise * scale) / 100)
  }
  // Rows marked as failed, at any valid date and any smaller refund.
  const marked = (
    await listTransactions({
      direction: opposite,
      reversalPending: 'true',
      ...after,
      ...(t.direction === 'DEBIT'
        ? { maxAmount: t.amount }
        : { minAmount: t.amount }),
    })
  ).items.filter((x) => x.id !== t.id && !found.some((f) => f.id === x.id))
  return [...marked, ...found].sort(
    (a, b) => Math.abs(a.amount - t.amount) - Math.abs(b.amount - t.amount),
  )
}

/**
 * The expanded transaction: its category, the default for its merchant or person, who was paid,
 * transfer and failed-payment pairing — the same actions as the web detail row.
 * `onSaved` receives the changed row; `reload` refetches when a change touches other rows.
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
  const styles = useStyles()
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(
    null,
  )
  const [isPerson, setIsPerson] = useState(t.personId !== null)
  const [name, setName] = useState(
    t.personName ?? t.payeeName ?? t.normalizedDescription ?? t.description,
  )
  const [merchant, setMerchant] = useState(
    t.merchantName ?? t.personName ?? t.payeeName ?? '',
  )
  const [candidates, setCandidates] = useState<Transaction[] | null>(null)
  const [partner, setPartner] = useState<string | null>(null)
  const [reported, setReported] = useState(false)

  const holder = t.personId ? t.personName : t.merchantName
  const suggested = reported ? null : t.suggestedMerchantName
  const options = categories.map((c) => ({
    value: c.id,
    label: c.countsAsSpending ? c.name : `${c.name} (not spending)`,
  }))

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

  function report(name: string) {
    Alert.alert(`Report ${name}?`, 'Report this name as offensive or wrong?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Report',
        style: 'destructive',
        onPress: () =>
          save(async () => {
            await reportSuggestion(t.id)
            setReported(true)
            reload()
          }, 'Reported. Thank you.'),
      },
    ])
  }

  const unmark = (
    <Button
      title="Not a failed payment and refund"
      variant="secondary"
      onPress={() =>
        save(async () => {
          onSaved(await setReversal(t.id, null))
          setCandidates(null)
          reload()
        }, 'No longer marked as failed and refunded.')
      }
    />
  )

  return (
    <View style={{ gap: space.md }}>
      <Text style={styles.muted}>{t.description}</Text>

      {suggested && !t.merchantId && !t.personId && (
        <Section title={`Suggested merchant: ${suggested}`}>
          <View style={styles.chipRow}>
            <Button
              title={`Use ${suggested}`}
              accessibilityLabel={`Use ${suggested} for ${t.description}`}
              onPress={() =>
                save(async () => {
                  onSaved(await setTransactionMerchant(t.id, suggested, false))
                  reload()
                }, `Named ${suggested}.`)
              }
            />
            <Button
              title="Report"
              variant="secondary"
              accessibilityLabel={`Report suggested name ${suggested}`}
              onPress={() => report(suggested)}
            />
          </View>
        </Section>
      )}

      <Section title="Category for this transaction">
        <Picker
          label="Category for this transaction"
          value={t.categoryOverridden ? (t.categoryId ?? '') : ''}
          placeholder="Use merchant/person default"
          options={[
            { value: '', label: 'Use merchant/person default' },
            ...options,
          ]}
          onChange={(v) =>
            save(
              async () =>
                onSaved(await setTransactionCategory(t.id, v || null)),
              'Category saved.',
            )
          }
        />
      </Section>

      {(t.personId || t.merchantId) && (
        <Section title={`Category for all ${holder}`}>
          <Picker
            label={`Set category for all ${holder}`}
            value={null}
            placeholder="Choose a category"
            options={[{ value: CLEAR, label: 'Clear the default' }, ...options]}
            onChange={(v) =>
              save(async () => {
                const id = v === CLEAR ? null : v
                // Money in and money out keep separate defaults.
                const credit = t.direction === 'CREDIT'
                if (t.personId) await setPersonCategory(t.personId, id, credit)
                else await setMerchantCategory(t.merchantId!, id, credit)
                reload()
              }, `Default category saved for ${holder}.`)
            }
          />
        </Section>
      )}

      <Section title="Who was paid">
        <View style={styles.chipRow}>
          <Chip
            label="A person"
            selected={isPerson}
            onPress={() => setIsPerson(true)}
          />
          <Chip
            label="A merchant"
            selected={!isPerson}
            onPress={() => setIsPerson(false)}
          />
        </View>
        {isPerson ? (
          <Field
            label="Person's name"
            value={name}
            maxLength={100}
            onChangeText={setName}
          />
        ) : (
          <Field
            label="Merchant's name"
            value={merchant}
            maxLength={100}
            onChangeText={setMerchant}
          />
        )}
        <Button
          title="Save who was paid"
          disabled={!(isPerson ? name : merchant).trim()}
          onPress={() =>
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
        />
      </Section>

      <Section title="Between your own accounts">
        <Button
          title={
            t.transfer
              ? 'Not a transfer between my accounts'
              : 'Mark as a transfer between my accounts'
          }
          variant="secondary"
          onPress={() =>
            save(
              async () => {
                onSaved(await setTransfer(t.id, !t.transfer))
                reload()
              },
              t.transfer
                ? 'No longer counted as a transfer.'
                : 'Marked as a transfer between your accounts.',
            )
          }
        />
      </Section>

      <Section title="Duplicate">
        <Button
          title={t.duplicate ? 'Not a duplicate' : 'Mark as duplicate'}
          variant="secondary"
          onPress={() =>
            save(
              async () => {
                onSaved(await setDuplicate(t.id, !t.duplicate))
                reload()
              },
              t.duplicate
                ? 'No longer marked as a duplicate.'
                : 'Marked as a duplicate. It no longer counts in totals.',
            )
          }
        />
      </Section>

      <LoanSection t={t} save={save} onSaved={onSaved} reload={reload} />

      <Section title="Failed payment and refund">
        {t.reversalPairId ? (
          unmark
        ) : candidates === null ? (
          <>
            <Button
              title={
                t.reversalPending
                  ? t.direction === 'DEBIT'
                    ? 'Find the refund'
                    : 'Find the failed payment'
                  : t.direction === 'DEBIT'
                    ? 'Mark as failed and refunded'
                    : 'Mark as the refund of a failed payment'
              }
              variant="secondary"
              onPress={() =>
                save(async () => {
                  // Marked first, so it is offered when its other half is paired later.
                  if (!t.reversalPending)
                    onSaved(await setReversal(t.id, null, true))
                  const found = await reversalCandidates(t)
                  setCandidates(found)
                  setPartner(found[0]?.id ?? null)
                }, '')
              }
            />
            {t.reversalPending && unmark}
          </>
        ) : candidates.length === 0 ? (
          <>
            <Text style={styles.muted}>
              No {t.direction === 'DEBIT' ? 'money in' : 'money out'} of{' '}
              {rupees(t.amount)}, or close to it after charges, within{' '}
              {REFUND_DAYS} days {t.direction === 'DEBIT' ? 'after' : 'before'}{' '}
              this one. It stays marked, so it is offered when you open the{' '}
              {t.direction === 'DEBIT' ? 'refund' : 'failed payment'} and pair
              it.
            </Text>
            {unmark}
          </>
        ) : (
          <>
            <Picker
              label={
                t.direction === 'DEBIT' ? 'The refund' : 'The failed payment'
              }
              value={partner}
              placeholder="Choose one"
              options={candidates.map((c) => ({
                value: c.id,
                label: `${dayLabel(c.date)} · ${c.normalizedDescription ?? c.description}${
                  c.amount !== t.amount
                    ? ` · ${rupees(Math.abs(c.amount - t.amount))} charges`
                    : ''
                }`,
              }))}
              onChange={setPartner}
            />
            <Button
              title="Pair them"
              disabled={!partner}
              onPress={() =>
                save(async () => {
                  onSaved(await setReversal(t.id, partner))
                  reload()
                }, 'Marked as failed and refunded. Only any charges count in your totals.')
              }
            />
          </>
        )}
      </Section>

      {status?.ok && status.text ? (
        <Text style={styles.muted} accessibilityLiveRegion="polite">
          {status.text}
        </Text>
      ) : null}
      {status && !status.ok && <ErrorText>{status.text}</ErrorText>}
    </View>
  )
}

const LOAN = 'Loan'
const LOAN_EMI = 'Loan EMI'

/**
 * Loan controls, same as the web. On money out categorised Loan EMI: link it to a loan (its lender's other EMIs
 * follow) or unlink it. On money in categorised Loan: what its linked EMIs repaid, and closing it.
 */
function LoanSection({
  t,
  save,
  onSaved,
  reload,
}: {
  t: Transaction
  save: (task: () => Promise<void>, done: string) => Promise<void>
  onSaved: (t: Transaction) => void
  reload: () => void
}) {
  const styles = useStyles()
  const emi = t.direction === 'DEBIT' && t.category === LOAN_EMI
  const loan = t.direction === 'CREDIT' && t.category === LOAN
  const [loans, setLoans] = useState<Transaction[] | null>(null)
  const [picked, setPicked] = useState<string | null>(null)
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
      <Section title="Loan">
        {t.loanId ? (
          <>
            <Text style={styles.muted}>
              Repays a loan
              {t.loanInterest !== null ? ` · ${rupees(t.loanInterest)} of it is interest` : ''}
            </Text>
            <Button
              title="Unlink from loan"
              variant="secondary"
              onPress={() =>
                save(async () => {
                  onSaved(await setLoan(t.id, null))
                  reload()
                }, 'No longer linked to a loan.')
              }
            />
          </>
        ) : loans === null ? (
          <Button
            title="Link to a loan"
            variant="secondary"
            onPress={() =>
              save(async () => {
                const open = (
                  await listTransactions({ category: LOAN, direction: 'CREDIT' })
                ).items.filter((l) => !l.loanClosed && l.date <= t.date)
                setLoans(open)
                setPicked(open[0]?.id ?? null)
              }, '')
            }
          />
        ) : loans.length === 0 ? (
          <Text style={styles.muted}>
            No open loan before this payment. Categorise the money you borrowed as {LOAN} first.
          </Text>
        ) : (
          <>
            <Picker
              label="The loan"
              value={picked}
              placeholder="Choose one"
              options={loans.map((l) => ({
                value: l.id,
                label: `${rupees(l.amount)} · ${dayLabel(l.date)} · ${l.normalizedDescription ?? l.description}`,
              }))}
              onChange={setPicked}
            />
            <Button
              title="Link"
              disabled={!picked}
              onPress={() =>
                save(async () => {
                  onSaved(await setLoan(t.id, picked))
                  reload()
                }, 'Linked. This lender’s other and future EMIs link to this loan too.')
              }
            />
          </>
        )}
      </Section>
    )
  }

  if (!loan) return null
  const repaid = (repayments ?? []).reduce((sum, r) => sum + r.amount, 0)
  const interest = (repayments ?? []).reduce((sum, r) => sum + (r.loanInterest ?? 0), 0)
  return (
    <Section title={t.loanClosed ? 'Loan (closed)' : 'Loan'}>
      {repayments !== null && (
        <Text style={styles.muted}>
          {repayments.length} EMI{repayments.length === 1 ? '' : 's'} · repaid {rupees(repaid)} of{' '}
          {rupees(t.amount)}
          {interest > 0
            ? ` · ${rupees(interest)} interest`
            : ` · ${rupees(t.amount - repaid)} outstanding`}
        </Text>
      )}
      <Button
        title={t.loanClosed ? 'Reopen loan' : 'Close loan'}
        variant="secondary"
        onPress={() =>
          save(
            async () => {
              onSaved(await setLoanClosed(t.id, !t.loanClosed))
              reload()
            },
            t.loanClosed
              ? 'Reopened. Its lender’s EMIs link to it again.'
              : 'Closed. Later payments to its lender are no longer treated as EMIs.',
          )
        }
      />
    </Section>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  const styles = useStyles()
  return (
    <View style={{ gap: space.sm }}>
      <Text style={styles.label} accessibilityRole="header">
        {title}
      </Text>
      {children}
    </View>
  )
}
