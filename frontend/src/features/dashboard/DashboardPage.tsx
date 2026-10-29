import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import shared from '../../components/shared.module.css'
import {
  changeLabel,
  dayLabel,
  rangeFor,
  rupees,
  type RangePreset,
} from '../../lib/format'
import {
  getCashFlow,
  getCategories,
  getMerchants,
  getSummary,
  getTrends,
  type CashFlow,
  type CategoryBreakdown,
  type MerchantBreakdown,
  type Summary,
  type Trends,
} from '../../services/finance'
import { LedgerChart, SpendBars, TrendLine } from './charts'
import styles from './DashboardPage.module.css'

const PRESETS: { value: RangePreset; label: string }[] = [
  { value: 'month', label: 'This month' },
  { value: '3m', label: 'Last 3 months' },
  { value: '12m', label: 'Last 12 months' },
]

type Data = {
  summary: Summary
  categories: CategoryBreakdown
  merchants: MerchantBreakdown
  cashFlow: CashFlow
  trends: Trends
}

/** The period comes from the URL: ?preset=3m, or ?from=&to= for a custom range. */
function usePeriod() {
  const [params, setParams] = useSearchParams()
  const from = params.get('from')
  const to = params.get('to')
  const preset =
    (params.get('preset') as RangePreset | null) ?? (from && to ? null : '3m')
  const range = preset ? rangeFor(preset) : { from: from!, to: to! }
  return { preset, range, setParams }
}

export function DashboardPage() {
  const { preset, range, setParams } = usePeriod()
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState(false)
  const [custom, setCustom] = useState(range)

  const { from, to } = range
  useEffect(() => {
    let live = true
    Promise.all([
      getSummary(from, to),
      getCategories(from, to),
      getMerchants(from, to),
      getCashFlow(from, to),
      getTrends(from, to),
    ])
      .then(([summary, categories, merchants, cashFlow, trends]) => {
        if (!live) return
        setData({ summary, categories, merchants, cashFlow, trends })
        setError(false)
      })
      .catch(() => live && setError(true))
    return () => {
      live = false
    }
  }, [from, to])

  const s = data?.summary
  const toTransactions = (extra: string) =>
    `/transactions?${extra}from=${range.from}&to=${range.to}`

  return (
    <>
      <h1 className={shared.title}>Overview</h1>
      <p className={shared.lead}>
        {dayLabel(range.from)} to {dayLabel(range.to)}
      </p>

      <fieldset className={styles.period}>
        <legend className={shared.label}>Period</legend>
        {PRESETS.map((p) => (
          <label key={p.value} className={styles.option}>
            <input
              type="radio"
              name="period"
              checked={preset === p.value}
              onChange={() => setParams({ preset: p.value })}
            />
            {p.label}
          </label>
        ))}
        <label className={styles.option}>
          <input
            type="radio"
            name="period"
            checked={preset === null}
            onChange={() => setParams({ from: range.from, to: range.to })}
          />
          Choose dates
        </label>
        {preset === null && (
          <form
            className={styles.custom}
            onSubmit={(e) => {
              e.preventDefault()
              if (custom.from && custom.to && custom.from <= custom.to)
                setParams(custom)
            }}
          >
            <label>
              <span className={shared.label}>From</span>
              <input
                className={shared.input}
                type="date"
                value={custom.from}
                onChange={(e) => setCustom({ ...custom, from: e.target.value })}
              />
            </label>
            <label>
              <span className={shared.label}>To</span>
              <input
                className={shared.input}
                type="date"
                value={custom.to}
                min={custom.from}
                onChange={(e) => setCustom({ ...custom, to: e.target.value })}
              />
            </label>
            <button className={shared.button} type="submit">
              Show these dates
            </button>
          </form>
        )}
      </fieldset>

      {error && (
        <p className={shared.error} role="alert">
          Could not load your overview. Try again in a moment.
        </p>
      )}

      {s && s.transactionCount === 0 && !error && (
        <p className={styles.empty}>
          No transactions in this period.{' '}
          <Link to="/statements">Upload a statement</Link> or choose a longer
          period.
        </p>
      )}

      {data && s && s.transactionCount > 0 && !error && (
        <>
          <dl className={styles.totals}>
            <div>
              <dt>Money in</dt>
              <dd className={styles.figureIn}>{rupees(s.inflow)}</dd>
              <dd className={styles.compare}>
                {changeLabel(s.inflowChangePercent)}
              </dd>
            </div>
            <div>
              <dt>Money out</dt>
              <dd className={styles.figureOut}>{rupees(s.outflow)}</dd>
              <dd className={styles.compare}>
                {changeLabel(s.outflowChangePercent)}
              </dd>
            </div>
            <div>
              <dt>Net</dt>
              <dd className={styles.figureNet}>{rupees(s.net)}</dd>
              <dd className={styles.compare}>
                {rupees(s.previous.net)} from {dayLabel(s.previous.from)} to{' '}
                {dayLabel(s.previous.to)}
              </dd>
            </div>
          </dl>

          <section className={shared.section} aria-labelledby="cash-flow">
            <h2 id="cash-flow">Cash flow by month</h2>
            <LedgerChart data={data.cashFlow} />
          </section>

          <section className={shared.section} aria-labelledby="trend">
            <h2 id="trend">Spending trend</h2>
            <TrendLine data={data.trends} />
          </section>

          <div className={styles.split}>
            <section className={shared.section} aria-labelledby="categories">
              <h2 id="categories">Where it went</h2>
              <SpendBars
                empty="No spending in this period."
                bars={data.categories.items.map((c) => ({
                  key: c.category,
                  label: c.category,
                  spend: c.spend,
                  share: c.sharePercent,
                  href: toTransactions(
                    `category=${encodeURIComponent(c.category)}&`,
                  ),
                }))}
              />
            </section>
            <section className={shared.section} aria-labelledby="merchants">
              <h2 id="merchants">Top merchants</h2>
              <SpendBars
                empty="No spending in this period."
                bars={data.merchants.items.map((m) => ({
                  key: m.merchantId ?? 'unmatched',
                  label: m.name,
                  spend: m.spend,
                  share: m.sharePercent,
                  href: m.merchantId
                    ? toTransactions(`merchantId=${m.merchantId}&`)
                    : undefined,
                }))}
              />
            </section>
          </div>
        </>
      )}
    </>
  )
}
