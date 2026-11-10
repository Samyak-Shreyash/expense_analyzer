const INR = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 2,
})

export const rupees = (amount: number) => INR.format(amount)

/** First and last day of the month containing `date`, as yyyy-mm-dd. */
export function monthRange(date = new Date()) {
  const y = date.getFullYear()
  const m = date.getMonth()
  const iso = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  return { from: iso(new Date(y, m, 1)), to: iso(new Date(y, m + 1, 0)) }
}
