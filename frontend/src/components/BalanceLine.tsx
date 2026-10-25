import styles from './AuthLayout.module.css'

// A year of a running balance: payday-like steps up, gradual dips between.
const Y = [
  90, 40, 52, 64, 76, 88, 98, 50, 60, 72, 84, 96, 104, 56, 66, 78, 90, 100, 46,
  58, 70, 82, 94, 30, 42,
]
const POINTS = Y.map((y, i) => `${i * 50},${y}`).join(' ')

export function BalanceLine() {
  return (
    <svg
      className={styles.line}
      viewBox="0 0 1200 120"
      preserveAspectRatio="none"
      aria-hidden="true"
      focusable="false"
    >
      <polyline points={POINTS} />
    </svg>
  )
}
