import styles from './AuthLayout.module.css'

// An illustrative year of a running balance in rupees: salary steps up, spending dips.
const BALANCE = [
  52000, 84000, 77500, 71000, 64000, 58500, 54000, 88000, 81000, 73500, 66000,
  60000, 92000, 85000, 78500, 70000, 62000, 55500, 30500, 64000, 57000, 49500,
  42000, 36000, 68000,
]
const W = 1200
const H = 240
const PAD = 36
const STEP = W / (BALANCE.length - 1)
const MAX = Math.max(...BALANCE)
const MIN = Math.min(...BALANCE)

const x = (i: number) => i * STEP
const y = (v: number) => PAD + ((MAX - v) / (MAX - MIN)) * (H - 2 * PAD)

const rupees = (v: number) => `₹${v.toLocaleString('en-IN')}`

// Label the highest point, the lowest point and today.
const LABELS = [BALANCE.indexOf(MAX), BALANCE.indexOf(MIN), BALANCE.length - 1]

export function BalanceLine() {
  return (
    <div className={styles.chart} aria-hidden="true">
      <svg
        className={styles.line}
        viewBox={`0 0 ${W} ${H}`}
        preserveAspectRatio="none"
        focusable="false"
      >
        {BALANCE.slice(1).map((v, i) => (
          <line
            key={i}
            x1={x(i)}
            y1={y(BALANCE[i])}
            x2={x(i + 1)}
            y2={y(v)}
            className={v >= BALANCE[i] ? styles.credit : styles.debit}
          />
        ))}
      </svg>
      {LABELS.map((i) => (
        <span
          key={i}
          className={styles.figure}
          data-edge={i === BALANCE.length - 1 ? 'end' : undefined}
          data-low={BALANCE[i] === MIN || undefined}
          style={{
            left: `${(x(i) / W) * 100}%`,
            top: `${(y(BALANCE[i]) / H) * 100}%`,
          }}
        >
          {rupees(BALANCE[i])}
        </span>
      ))}
    </div>
  )
}
