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
    choose(new File(['%PDF'], 'jan.pdf'))

    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      'Choose a CSV or Excel (.xls or .xlsx) file. PDF statements are not supported yet.',
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
})
