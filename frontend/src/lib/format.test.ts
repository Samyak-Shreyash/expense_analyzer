import { describe, expect, it } from 'vitest'
import {
  MAX_UPLOAD_BYTES,
  changeLabel,
  rangeFor,
  rupees,
  validateStatementFile,
} from './format'

describe('rupees', () => {
  it('formats with Indian digit grouping and the rupee sign', () => {
    expect(rupees(123456.5)).toBe('₹1,23,456.50')
  })

  it('formats zero and negatives', () => {
    expect(rupees(0)).toBe('₹0.00')
    expect(rupees(-1000)).toBe('-₹1,000.00')
  })
})

describe('changeLabel', () => {
  it('describes increases and decreases against the previous period', () => {
    expect(changeLabel(12.5)).toBe('12.5% more than the previous period')
    expect(changeLabel(-3)).toBe('3% less than the previous period')
  })

  it('says nothing useful is comparable when the base was zero', () => {
    expect(changeLabel(null)).toBe(
      'Nothing to compare with in the previous period',
    )
  })
})

describe('validateStatementFile', () => {
  const file = (name: string, size: number) =>
    new File([new Uint8Array(size)], name, { type: '' })

  it('accepts CSV and XLSX under the size limit', () => {
    expect(validateStatementFile(file('jan.csv', 10))).toBeNull()
    expect(validateStatementFile(file('JAN.XLSX', 10))).toBeNull()
  })

  it('accepts XLS and PDF, rejects other formats', () => {
    expect(validateStatementFile(file('jan.docx', 10))).toBe(
      'Choose a CSV, Excel (.xls or .xlsx) or PDF file.',
    )
    expect(validateStatementFile(file('jan.xls', 10))).toBeNull()
    expect(validateStatementFile(file('jan.pdf', 10))).toBeNull()
  })

  it('rejects empty and oversized files', () => {
    expect(validateStatementFile(file('jan.csv', 0))).toBe(
      'This file is empty.',
    )
    expect(validateStatementFile(file('jan.csv', MAX_UPLOAD_BYTES + 1))).toBe(
      'This file is larger than 20 MB.',
    )
  })
})

describe('rangeFor', () => {
  const today = new Date(2026, 9, 29) // 29 Oct 2026, local time

  it('covers the current month to date', () => {
    expect(rangeFor('month', today)).toEqual({
      from: '2026-10-01',
      to: '2026-10-29',
    })
  })

  it('covers whole months back from the current one', () => {
    expect(rangeFor('3m', today)).toEqual({
      from: '2026-08-01',
      to: '2026-10-29',
    })
    expect(rangeFor('12m', today)).toEqual({
      from: '2025-11-01',
      to: '2026-10-29',
    })
  })
})
