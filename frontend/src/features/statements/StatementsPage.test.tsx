import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Statement } from '../../services/finance'
import * as finance from '../../services/finance'
import { StatementsPage } from './StatementsPage'

vi.mock('../../services/finance', async (orig) => ({
  ...(await orig<typeof import('../../services/finance')>()),
  listStatements: vi.fn(),
  uploadStatement: vi.fn(),
  deleteStatement: vi.fn(),
}))

const statement = (over: Partial<Statement> = {}): Statement => ({
  id: 's1',
  fileName: 'jan.csv',
  format: 'CSV',
  status: 'COMPLETED',
  processingError: null,
  createdAt: '2026-01-31T10:00:00Z',
  completedAt: '2026-01-31T10:00:05Z',
  ...over,
})

const page = (items: Statement[]) => ({
  items,
  page: 0,
  size: 100,
  totalItems: items.length,
  totalPages: 1,
})

function renderPage() {
  return render(
    <MemoryRouter>
      <StatementsPage />
    </MemoryRouter>,
  )
}

function choose(file: File) {
  fireEvent.change(screen.getByLabelText(/choose a file/i), {
    target: { files: [file] },
  })
}

describe('StatementsPage', () => {
  beforeEach(() => {
    vi.mocked(finance.listStatements).mockResolvedValue(page([]))
  })

  it('rejects an unsupported file before uploading anything', async () => {
    renderPage()
    choose(new File(['x'], 'jan.docx'))

    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      'Choose a CSV, Excel (.xls or .xlsx) or PDF file.',
    )
    expect(finance.uploadStatement).not.toHaveBeenCalled()
  })

  it('uploads a valid file and shows it in the list with its status', async () => {
    const queued = statement({ status: 'QUEUED', completedAt: null })
    vi.mocked(finance.uploadStatement).mockResolvedValue({
      ok: true,
      statement: queued,
    })
    vi.mocked(finance.listStatements)
      .mockResolvedValueOnce(page([]))
      .mockResolvedValue(page([queued]))

    renderPage()
    choose(new File(['Date,Description,Amount\n'], 'jan.csv'))

    await waitFor(() => expect(finance.uploadStatement).toHaveBeenCalledOnce())
    expect(await screen.findByText('jan.csv')).toBeTruthy()
    expect(screen.getByText('Processing')).toBeTruthy()
  })

  it('asks for the password of a protected PDF and retries with it', async () => {
    vi.mocked(finance.uploadStatement)
      .mockResolvedValueOnce({
        ok: false,
        reason: 'PASSWORD_REQUIRED',
        message: 'This PDF is password-protected. Enter its password.',
      })
      .mockResolvedValue({ ok: true, statement: statement() })
    renderPage()
    const pdf = new File(['%PDF'], 'jan.pdf')
    choose(pdf)

    fireEvent.change(await screen.findByLabelText(/password for jan.pdf/i), {
      target: { value: '1234' },
    })
    fireEvent.click(screen.getByRole('button', { name: /unlock and upload/i }))

    await waitFor(() =>
      expect(finance.uploadStatement).toHaveBeenLastCalledWith(
        pdf,
        expect.any(Function),
        '1234',
      ),
    )
    await waitFor(() =>
      expect(screen.queryByLabelText(/password for/i)).toBeNull(),
    )
  })

  it('explains why the server rejected a file', async () => {
    vi.mocked(finance.uploadStatement).mockResolvedValue({
      ok: false,
      message: 'You have already uploaded this file.',
      existingId: 's1',
    })
    renderPage()
    choose(new File(['x'], 'jan.csv'))

    expect((await screen.findByRole('alert')).textContent).toContain(
      'You have already uploaded this file.',
    )
  })

  it('shows the reason a statement failed processing', async () => {
    vi.mocked(finance.listStatements).mockResolvedValue(
      page([
        statement({ status: 'FAILED', processingError: 'MISSING_HEADER' }),
      ]),
    )
    renderPage()

    expect(
      await screen.findByText(
        /No header row with Date, Description and Amount/,
      ),
    ).toBeTruthy()
  })

  it('moves focus into the delete confirmation and back out on Escape', async () => {
    vi.mocked(finance.listStatements).mockResolvedValue(page([statement()]))
    renderPage()

    const del = await screen.findByRole('button', { name: 'Delete' })
    fireEvent.click(del)
    const keep = screen.getByRole('button', { name: 'Keep' })
    expect(document.activeElement).toBe(keep)

    fireEvent.keyDown(keep, { key: 'Escape' })
    const reopened = screen.getByRole('button', { name: 'Delete' })
    expect(document.activeElement).toBe(reopened)
    expect(finance.deleteStatement).not.toHaveBeenCalled()
  })
})
