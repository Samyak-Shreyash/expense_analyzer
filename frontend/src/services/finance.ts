import { apiFetch, apiUpload } from './api'

// Shapes returned by the backend (docs/api/API-CONTRACT.md). Amounts are rupees.

export type Page<T> = {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type StatementStatus =
  'UPLOADED' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export type Statement = {
  id: string
  fileName: string
  format: 'CSV' | 'XLS' | 'XLSX' | 'PDF'
  status: StatementStatus
  processingError: string | null
  createdAt: string
  completedAt: string | null
}

export type Transaction = {
  id: string
  date: string
  description: string
  normalizedDescription: string | null
  amount: number
  currency: string
  direction: 'DEBIT' | 'CREDIT'
  merchantId: string | null
  merchantName: string | null
  /** Effective category name (override, person, merchant, hint). */
  category: string | null
  categoryId: string | null
  /** True when this transaction has its own category, set by the user. */
  categoryOverridden: boolean
  personId: string | null
  personName: string | null
  statementId: string | null
}

type Totals = {
  inflow: number
  outflow: number
  /** Money out that is not counted as spending (transfers, repayments). */
  nonSpendingOutflow: number
  net: number
  transactionCount: number
}

export type Summary = Totals & {
  from: string
  to: string
  previous: Totals & { from: string; to: string }
  inflowChangePercent: number | null
  outflowChangePercent: number | null
}

type Spend = {
  spend: number
  sharePercent: number | null
  transactionCount: number
}

export type MerchantBreakdown = {
  totalSpend: number
  items: (Spend & { merchantId: string | null; name: string })[]
}
export type CategoryBreakdown = {
  totalSpend: number
  items: (Spend & { category: string })[]
}

export type NonSpending = {
  total: number
  items: {
    category: string
    spend: number
    sharePercent: number | null
    transactionCount: number
    people: {
      personId: string
      name: string
      spend: number
      transactionCount: number
    }[]
  }[]
}

export type CashFlow = {
  months: { month: string; inflow: number; outflow: number; net: number }[]
}
export type Trends = {
  months: { month: string; outflow: number; changePercent: number | null }[]
}

/** Thrown for non-2xx answers; `message` is safe to show. */
export class ApiError extends Error {
  readonly status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function getJson<T>(path: string): Promise<T> {
  const res = await apiFetch(path)
  if (!res.ok)
    throw new ApiError(
      res.status,
      'Could not load this data. Try again in a moment.',
    )
  return res.json()
}

const query = (params: Record<string, string | number | undefined>) => {
  const q = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '') q.set(k, String(v))
  }
  return q.toString()
}

// ---- statements ----

export const listStatements = () =>
  getJson<Page<Statement>>('/statements?size=100')

/** Messages for the backend's upload rejection reasons (ProblemDetail.reason). */
const UPLOAD_REASONS: Record<string, string> = {
  EMPTY_FILE: 'This file is empty.',
  FILE_TOO_LARGE: 'This file is larger than 20 MB.',
  UNSUPPORTED_EXTENSION: 'Choose a CSV, Excel (.xls or .xlsx) or PDF file.',
  PASSWORD_REQUIRED: 'This PDF is password-protected. Enter its password.',
  WRONG_PASSWORD: 'That password is incorrect. Try again.',
  MIME_TYPE_MISMATCH: "This file's type doesn't match its extension.",
  SIGNATURE_MISMATCH: "This file's contents don't match its extension.",
}

export type UploadOutcome =
  | { ok: true; statement: Statement }
  | { ok: false; message: string; existingId?: string; reason?: string }

export async function uploadStatement(
  file: File,
  onProgress: (fraction: number) => void,
  password?: string,
): Promise<UploadOutcome> {
  let res
  try {
    res = await apiUpload(
      '/statements',
      file,
      onProgress,
      password ? { password } : {},
    )
  } catch {
    return {
      ok: false,
      message:
        'The upload was interrupted. Check your connection and try again.',
    }
  }
  const body = (res.body ?? {}) as { reason?: string; statementId?: string }
  if (res.status === 201) return { ok: true, statement: res.body as Statement }
  if (res.status === 409) {
    return {
      ok: false,
      message: 'You have already uploaded this file.',
      existingId: body.statementId,
    }
  }
  if (res.status === 503) {
    return {
      ok: false,
      message: 'File storage is unavailable right now. Try again in a minute.',
    }
  }
  return {
    ok: false,
    reason: body.reason,
    message:
      (body.reason && UPLOAD_REASONS[body.reason]) ||
      'This file was not accepted.',
  }
}

export async function deleteStatement(id: string) {
  const res = await apiFetch(`/statements/${id}`, { method: 'DELETE' })
  if (!res.ok && res.status !== 404)
    throw new ApiError(res.status, 'Could not delete the statement.')
}

// ---- transactions ----

export type TransactionQuery = {
  q?: string
  from?: string
  to?: string
  direction?: string
  merchantId?: string
  statementId?: string
  category?: string
  page?: number
}

export const listTransactions = (filter: TransactionQuery) =>
  getJson<Page<Transaction>>(`/transactions?${query({ ...filter, size: 50 })}`)

// ---- analytics ----

const range = (from: string, to: string) => query({ from, to })

export const getSummary = (from: string, to: string) =>
  getJson<Summary>(`/analytics/summary?${range(from, to)}`)
export const getCategories = (from: string, to: string) =>
  getJson<CategoryBreakdown>(`/analytics/categories?${range(from, to)}`)
export const getMerchants = (from: string, to: string) =>
  getJson<MerchantBreakdown>(`/analytics/merchants?${range(from, to)}&limit=8`)
export const getCashFlow = (from: string, to: string) =>
  getJson<CashFlow>(`/analytics/cash-flow?${range(from, to)}`)
export const getTrends = (from: string, to: string) =>
  getJson<Trends>(`/analytics/trends?${range(from, to)}`)

export const getNonSpending = (from: string, to: string) =>
  getJson<NonSpending>(`/analytics/non-spending?${range(from, to)}`)

// ---- categories and people ----

export type Category = { id: string; name: string; countsAsSpending: boolean }
export type Person = { id: string; name: string; categoryId: string | null }

/** Write call: returns the parsed body (null when empty) or throws with a message safe to show. */
async function write<T>(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  body: unknown,
  messages: Record<number, string>,
): Promise<T> {
  const res = await apiFetch(path, {
    method,
    headers:
      body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok)
    throw new ApiError(
      res.status,
      messages[res.status] ?? 'Could not save that. Try again in a moment.',
    )
  return (res.status === 204 ? null : await res.json()) as T
}

const CATEGORY_ERRORS = {
  400: 'Use a name of 1 to 50 characters. "Uncategorized" is reserved.',
  404: 'That category no longer exists. Reload the page.',
  409: 'You already have a category with that name.',
}
const PERSON_ERRORS = {
  400: 'Use a name of 1 to 100 characters.',
  404: 'That person no longer exists. Reload the page.',
  409: 'You already have a person with that name.',
}

export const listCategories = () => getJson<Category[]>('/categories')
export const createCategory = (name: string, countsAsSpending: boolean) =>
  write<Category>(
    'POST',
    '/categories',
    { name, countsAsSpending },
    CATEGORY_ERRORS,
  )
export const updateCategory = (
  id: string,
  name: string,
  countsAsSpending: boolean,
) =>
  write<Category>(
    'PUT',
    `/categories/${id}`,
    { name, countsAsSpending },
    CATEGORY_ERRORS,
  )
export const deleteCategory = (id: string) =>
  write<null>('DELETE', `/categories/${id}`, undefined, CATEGORY_ERRORS)

export const listPeople = () => getJson<Person[]>('/people')
export const updatePerson = (
  id: string,
  name: string,
  categoryId: string | null,
) => write<Person>('PUT', `/people/${id}`, { name, categoryId }, PERSON_ERRORS)

/** categoryId null clears the assignment. */
export const setTransactionCategory = (id: string, categoryId: string | null) =>
  write<Transaction>('PUT', `/transactions/${id}/category`, { categoryId }, {})
export const setMerchantCategory = (id: string, categoryId: string | null) =>
  write<null>('PUT', `/merchants/${id}/category`, { categoryId }, {})
export const setTransactionMerchant = (id: string, name: string) =>
  write<Transaction>(
    'PUT',
    `/transactions/${id}/merchant`,
    { name },
    {
      400: 'Use a merchant name of 1 to 100 characters.',
      404: 'That transaction no longer exists. Reload the page.',
    },
  )
export const setCounterparty = (
  id: string,
  type: 'PERSON' | 'MERCHANT',
  name?: string,
) =>
  write<Transaction>(
    'PUT',
    `/transactions/${id}/counterparty`,
    { type, name },
    { 400: 'Enter a name that looks like a person, up to 100 characters.' },
  )
