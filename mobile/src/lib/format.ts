const INR = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 2,
})

export const rupees = (amount: number) => INR.format(amount)

/** A yyyy-mm-dd date as e.g. "5 Jan 2026". */
export function dayLabel(date: string) {
  const [y, m, d] = date.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

/** "12% more than the previous period", or a note when there is nothing to compare. Same as the web. */
export function changeLabel(percent: number | null) {
  if (percent === null) return 'Nothing to compare with in the previous period'
  const size = Math.abs(percent)
  return `${size}% ${percent >= 0 ? 'more' : 'less'} than the previous period`
}

export type RangePreset = 'month' | '3m' | '12m'

const isoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** Whole calendar months up to today: this month, the last 3, or the last 12. Same as the web. */
export function rangeFor(preset: RangePreset, today = new Date()) {
  const months = { month: 1, '3m': 3, '12m': 12 }[preset]
  const start = new Date(
    today.getFullYear(),
    today.getMonth() - (months - 1),
    1,
  )
  return { from: isoDate(start), to: isoDate(today) }
}

/** "2026-01" -> "Jan 2026" */
export function monthLabel(month: string) {
  const [y, m] = month.split('-').map(Number)
  return new Date(y, m - 1, 1).toLocaleDateString('en-IN', {
    month: 'short',
    year: 'numeric',
  })
}
