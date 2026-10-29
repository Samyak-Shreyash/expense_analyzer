// Mirrors the backend upload rules (StatementFileValidator, app.statement.upload.max-size-bytes).
export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024
const UPLOAD_EXTENSIONS = ['csv', 'xlsx']

const INR = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
})

export const rupees = (amount: number) => INR.format(amount)

/** Plain-language comparison with the previous period; null means the base was zero. */
export function changeLabel(percent: number | null) {
  if (percent === null) return 'Nothing to compare with in the previous period'
  const size = Math.abs(percent)
  return `${size}% ${percent >= 0 ? 'more' : 'less'} than the previous period`
}

/** Returns a user-facing reason the file can't be uploaded, or null if it can. */
export function validateStatementFile(file: File) {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!UPLOAD_EXTENSIONS.includes(ext)) {
    return 'Choose a CSV or Excel (.xlsx) file. PDF statements are not supported yet.'
  }
  if (file.size === 0) return 'This file is empty.'
  if (file.size > MAX_UPLOAD_BYTES) return 'This file is larger than 20 MB.'
  return null
}

export type RangePreset = 'month' | '3m' | '12m'
export type DateRange = { from: string; to: string }

const isoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** Whole calendar months up to today: this month, the last 3, or the last 12. */
export function rangeFor(preset: RangePreset, today = new Date()): DateRange {
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

/** "2026-01-02" -> "2 Jan 2026" */
export function dayLabel(date: string) {
  const [y, m, d] = date.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}
