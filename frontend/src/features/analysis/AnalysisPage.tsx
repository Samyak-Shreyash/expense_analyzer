import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import shared from '../../components/shared.module.css'
import {
  dayLabel,
  monthLabel,
  percent,
  rangeFor,
  rupees,
} from '../../lib/format'
import {
  getCashFlow,
  getCategories,
  getSummary,
  listTransactions,
  type CashFlow,
  type CategoryBreakdown,
  type Summary,
  type Transaction,
} from '../../services/finance'
import styles from './AnalysisPage.module.css'

const COLORS = [
  '#4ade80',
  '#38bdf8',
  '#818cf8',
  '#c084fc',
  '#f87171',
  '#fbbf24',
]
const SLICES = 5

type Data = {
  summary: Summary
  categories: CategoryBreakdown
  cashFlow: CashFlow
  recent: Transaction[]
}

/** Dashboard-style view of this month, with a 12-month spending trend. */
export function AnalysisPage() {
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let live = true
    const month = rangeFor('month')
    const year = rangeFor('12m')
    Promise.all([
      getSummary(month.from, month.to),
      getCategories(month.from, month.to),
      getCashFlow(year.from, year.to),
      listTransactions({}),
    ])
      .then(([summary, categories, cashFlow, page]) => {
        if (!live) return
        setData({
          summary,
          categories,
          cashFlow,
          recent: page.items.slice(0, 5),
        })
        setError(false)
      })
      .catch(() => live && setError(true))
    return () => {
      live = false
    }
  }, [])

  if (error)
    return (
      <p className={shared.error} role="alert">
        Could not load your analysis. Try again in a moment.
      </p>
    )
  if (!data) return <p className={shared.muted}>Loading…</p>

  const { summary: s, categories, cashFlow, recent } = data
  return (
    <div className={styles.board}>
      <h1 className={styles.title}>Analysis</h1>
      <p className={styles.muted}>
        {dayLabel(s.from)} to {dayLabel(s.to)}
      </p>

      <div className={styles.grid}>
        <section className={`${styles.card} ${styles.stat}`}>
          <h2>Money in</h2>
          <p className={styles.in}>{rupees(s.inflow)}</p>
        </section>
        <section className={`${styles.card} ${styles.stat}`}>
          <h2>Spent</h2>
          <p className={styles.out}>{rupees(s.outflow)}</p>
        </section>
        <section className={`${styles.card} ${styles.stat}`}>
          <h2>Saved</h2>
          <p>{rupees(s.inflow - s.outflow)}</p>
        </section>

        <section className={`${styles.card} ${styles.trend}`}>
          <h2>Spending trend</h2>
          <p className={styles.muted}>Last 12 months</p>
          <AreaChart data={cashFlow} />
        </section>

        <section className={`${styles.card} ${styles.donutCard}`}>
          <h2>Spending by category</h2>
          <Donut data={categories} />
        </section>

        <section className={`${styles.card} ${styles.recent}`}>
          <h2>Recent transactions</h2>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Payee</th>
                <th>Category</th>
                <th>Date</th>
                <th className={styles.amount}>Amount</th>
              </tr>
            </thead>
            <tbody>
              {recent.map((t) => (
                <tr key={t.id}>
                  <td>{t.payeeName ?? t.merchantName ?? t.description}</td>
                  <td>{t.category ?? '—'}</td>
                  <td>{dayLabel(t.date)}</td>
                  <td className={styles.amount}>
                    <span
                      className={
                        t.direction === 'CREDIT'
                          ? styles.pillIn
                          : styles.pillOut
                      }
                    >
                      {rupees(t.amount)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Link to="/transactions">All transactions</Link>
        </section>

        <section className={`${styles.card} ${styles.insights}`}>
          <h2>Insights</h2>
          <ul>
            {insights(s, categories).map((text) => (
              <li key={text}>{text}</li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  )
}

function insights(s: Summary, c: CategoryBreakdown) {
  const out: string[] = []
  if (s.outflowChangePercent !== null)
    out.push(
      `You've spent ${percent(Math.abs(s.outflowChangePercent))} ${s.outflowChangePercent >= 0 ? 'more' : 'less'} than last period.`,
    )
  const top = c.items[0]
  if (top?.sharePercent != null)
    out.push(
      `${top.category} is ${percent(top.sharePercent)} of your spending.`,
    )
  if (out.length === 0) out.push('Nothing stands out yet this month.')
  return out
}

/** Monthly spending as a filled area; the list below carries the figures. */
function AreaChart({ data }: { data: CashFlow }) {
  const W = 600
  const H = 200
  const pad = 8
  const months = data.months
  const max = Math.max(1, ...months.map((m) => m.outflow))
  const step = months.length > 1 ? (W - 2 * pad) / (months.length - 1) : 0
  const pts = months.map(
    (m, i) =>
      `${pad + i * step},${H - pad - (m.outflow / max) * (H - 2 * pad)}`,
  )
  const line = pts.join(' ')
  return (
    <figure className={styles.figure}>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <defs>
          <linearGradient id="area" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#38bdf8" stopOpacity="0.6" />
            <stop offset="1" stopColor="#818cf8" stopOpacity="0.05" />
          </linearGradient>
        </defs>
        {pts.length > 1 && (
          <>
            <polygon
              points={`${pad},${H - pad} ${line} ${pad + (pts.length - 1) * step},${H - pad}`}
              fill="url(#area)"
            />
            <polyline points={line} className={styles.areaLine} />
          </>
        )}
      </svg>
      <div className={styles.axis} aria-hidden="true">
        {months.map((m) => (
          <span key={m.month}>{monthLabel(m.month).split(' ')[0]}</span>
        ))}
      </div>
      <ul className={shared.visuallyHidden}>
        {months.map((m) => (
          <li key={m.month}>
            {monthLabel(m.month)}: {rupees(m.outflow)}
          </li>
        ))}
      </ul>
    </figure>
  )
}

/** Top categories as a ring; the rest are folded into "Other". */
function Donut({ data }: { data: CategoryBreakdown }) {
  if (data.totalSpend <= 0)
    return <p className={styles.muted}>No spending this month.</p>
  const top = data.items.slice(0, SLICES)
  const rest = data.totalSpend - top.reduce((sum, i) => sum + i.spend, 0)
  const slices = [
    ...top.map((i) => ({ label: i.category, spend: i.spend })),
    ...(rest > 0 ? [{ label: 'Other', spend: rest }] : []),
  ]
  const R = 40
  const C = 2 * Math.PI * R
  let offset = 0
  return (
    <div className={styles.donut}>
      <svg viewBox="0 0 100 100" aria-hidden="true">
        {slices.map((sl, i) => {
          const len = (sl.spend / data.totalSpend) * C
          const el = (
            <circle
              key={sl.label}
              cx="50"
              cy="50"
              r={R}
              fill="none"
              stroke={COLORS[i % COLORS.length]}
              strokeWidth="14"
              strokeDasharray={`${len} ${C - len}`}
              strokeDashoffset={-offset}
              transform="rotate(-90 50 50)"
            />
          )
          offset += len
          return el
        })}
      </svg>
      <ul className={styles.legend}>
        {slices.map((sl, i) => (
          <li key={sl.label}>
            <span
              className={styles.dot}
              style={{ background: COLORS[i % COLORS.length] }}
            />
            {sl.label} <span className={styles.muted}>{rupees(sl.spend)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
