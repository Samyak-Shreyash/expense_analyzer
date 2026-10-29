import { Link } from 'react-router'
import shared from '../../components/shared.module.css'
import { monthLabel, rupees } from '../../lib/format'
import type { CashFlow, Trends } from '../../services/finance'
import styles from './DashboardPage.module.css'

const COL = 64
const BAR = 26
const HALF = 110
const LABEL = 28

/**
 * Cash flow as a ledger: each month's money in rises above the line, money out drops below it.
 * Both halves share one scale so heights compare honestly.
 */
export function LedgerChart({ data }: { data: CashFlow }) {
  const max = Math.max(1, ...data.months.flatMap((m) => [m.inflow, m.outflow]))
  const h = (v: number) => (v / max) * (HALF - 8)
  const width = Math.max(data.months.length * COL, COL)

  return (
    <figure className={styles.figure}>
      <div className={styles.scroll}>
        <svg
          className={styles.ledger}
          viewBox={`0 0 ${width} ${HALF * 2 + LABEL}`}
          style={{ minWidth: `${Math.min(width, 40 * 16)}px` }}
          role="img"
          aria-label="Money in and out by month. The same figures are in the table below."
        >
          <line
            x1={0}
            x2={width}
            y1={HALF}
            y2={HALF}
            className={styles.baseline}
          />
          {data.months.map((m, i) => {
            const x = i * COL + (COL - BAR) / 2
            return (
              <g key={m.month}>
                <rect
                  x={x}
                  y={HALF - h(m.inflow)}
                  width={BAR}
                  height={h(m.inflow)}
                  className={styles.in}
                />
                <rect
                  x={x}
                  y={HALF}
                  width={BAR}
                  height={h(m.outflow)}
                  className={styles.out}
                />
                <text
                  x={i * COL + COL / 2}
                  y={HALF * 2 + LABEL - 8}
                  className={styles.month}
                >
                  {monthLabel(m.month).split(' ')[0]}
                </text>
              </g>
            )
          })}
        </svg>
      </div>
      <figcaption className={styles.legend}>
        <span className={styles.keyIn}>Money in</span>
        <span className={styles.keyOut}>Money out</span>
      </figcaption>
      <table className={shared.visuallyHidden}>
        <caption>Cash flow by month</caption>
        <thead>
          <tr>
            <th>Month</th>
            <th>Money in</th>
            <th>Money out</th>
            <th>Net</th>
          </tr>
        </thead>
        <tbody>
          {data.months.map((m) => (
            <tr key={m.month}>
              <td>{monthLabel(m.month)}</td>
              <td>{rupees(m.inflow)}</td>
              <td>{rupees(m.outflow)}</td>
              <td>{rupees(m.net)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  )
}

/** Monthly spending as a line, with each month's change listed for screen readers. */
export function TrendLine({ data }: { data: Trends }) {
  const W = 600
  const H = 140
  const pad = 12
  const max = Math.max(1, ...data.months.map((m) => m.outflow))
  const step =
    data.months.length > 1 ? (W - 2 * pad) / (data.months.length - 1) : 0
  const points = data.months.map((m, i) => [
    pad + i * step,
    H - pad - (m.outflow / max) * (H - 2 * pad),
  ])
  const last = data.months.at(-1)

  return (
    <figure className={styles.figure}>
      <svg
        className={styles.trend}
        viewBox={`0 0 ${W} ${H}`}
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <polyline
          points={points.map((p) => p.join(',')).join(' ')}
          className={styles.trendLine}
        />
        {points.map(([x, y], i) => (
          <circle key={i} cx={x} cy={y} r={4} className={styles.trendPoint} />
        ))}
      </svg>
      {last && (
        <figcaption className={shared.muted}>
          {monthLabel(last.month)}: {rupees(last.outflow)} spent
          {last.changePercent !== null &&
            `, ${Math.abs(last.changePercent)}% ${last.changePercent >= 0 ? 'more' : 'less'} than the month before`}
          .
        </figcaption>
      )}
      <ul className={shared.visuallyHidden}>
        {data.months.map((m) => (
          <li key={m.month}>
            {monthLabel(m.month)}: {rupees(m.outflow)}
          </li>
        ))}
      </ul>
    </figure>
  )
}

type Bar = {
  key: string
  label: string
  spend: number
  share: number | null
  href?: string
}

/** Horizontal share bars; the text carries the numbers, the bar only shows proportion. */
export function SpendBars({ bars, empty }: { bars: Bar[]; empty: string }) {
  if (bars.length === 0) return <p className={shared.muted}>{empty}</p>
  const max = Math.max(...bars.map((b) => b.spend))
  return (
    <ul className={styles.bars}>
      {bars.map((b) => (
        <li key={b.key}>
          <div className={styles.barText}>
            {b.href ? (
              <Link to={b.href}>{b.label}</Link>
            ) : (
              <span>{b.label}</span>
            )}
            <span className={styles.barAmount}>
              {rupees(b.spend)}
              {b.share !== null && (
                <span className={shared.muted}> ({b.share}%)</span>
              )}
            </span>
          </div>
          <div className={styles.track} aria-hidden="true">
            <div
              className={styles.fill}
              style={{ width: `${(b.spend / max) * 100}%` }}
            />
          </div>
        </li>
      ))}
    </ul>
  )
}
