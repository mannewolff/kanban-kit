import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AttachmentsApi } from '../api/attachments'
import type { Card } from '../api/cards'
import type { CommentsApi } from '../api/comments'
import { CardDetailModal, parseDependencyInput } from './CardDetailModal'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 7, email: 'a@b.c', displayName: 'A', platformRole: 'USER', memberships: [] } }),
}))

const card: Card = {
  id: 100, boardId: 1, columnId: 10, number: 5, title: 'Aufgabe', description: '# Titel\n\n- a\n- b',
  positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [3, 4],
  type: 'CARD', parentId: null, shortcode: null,
}

function makeApis() {
  const commentsApi = {
    list: vi.fn().mockResolvedValue([
      { id: 1, cardId: 100, authorUserId: 7, authorName: 'A', body: 'Hallo', createdAt: '', updatedAt: '' },
    ]),
    create: vi.fn().mockResolvedValue(
      { id: 2, cardId: 100, authorUserId: 7, authorName: 'A', body: 'Neu', createdAt: '', updatedAt: '' },
    ),
    update: vi.fn(),
    remove: vi.fn().mockResolvedValue(undefined),
  } satisfies CommentsApi
  const attachmentsApi = {
    list: vi.fn().mockResolvedValue([]),
    upload: vi.fn(),
    remove: vi.fn(),
    fetchBlob: vi.fn(),
  } satisfies AttachmentsApi
  const cardsApi = { update: vi.fn().mockResolvedValue({ ...card }) }
  return { commentsApi, attachmentsApi, cardsApi }
}

describe('parseDependencyInput', () => {
  it('parst positive Nummern, entfernt Duplikate', () => {
    expect(parseDependencyInput('12, 34, 12')).toEqual({ deps: [12, 34], valid: true })
  })
  it('meldet ungültige Tokens', () => {
    expect(parseDependencyInput('12, x').valid).toBe(false)
    expect(parseDependencyInput('0').valid).toBe(false)
  })
})

describe('CardDetailModal', () => {
  beforeEach(() => {
    // jsdom kennt createObjectURL nicht.
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })

  it('rendert im Lesemodus Markdown, Abhängigkeiten und Kommentare', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)

    expect(screen.getByRole('heading', { name: 'Titel' })).toBeInTheDocument()
    expect(screen.getByLabelText('Abhängigkeiten')).toHaveTextContent('Abhängig von: #3, #4')
    await waitFor(() => expect(screen.getByText('Hallo')).toBeInTheDocument())
  })

  it('legt einen Kommentar an', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.change(screen.getByLabelText('Kommentar schreiben'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Senden' }))

    await waitFor(() => expect(apis.commentsApi.create).toHaveBeenCalledWith(100, 'Neu'))
    expect(await screen.findByText('Neu')).toBeInTheDocument()
  })

  it('speichert Titel, Beschreibung, Abhängigkeiten und Epic in einem Update', async () => {
    const apis = makeApis()
    const onChanged = vi.fn()
    render(<CardDetailModal card={card} canEdit onChanged={onChanged} onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Markdown-Beschreibung'), { target: { value: 'Neuer Text' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() =>
      expect(apis.cardsApi.update).toHaveBeenCalledWith(100, 'Aufgabe', 'Neuer Text', [3, 4], undefined, null),
    )
    expect(onChanged).toHaveBeenCalled()
  })

  it('zeigt dem Autor Bearbeiten, aber ohne Moderationsrecht kein Löschen', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)
    await waitFor(() => expect(screen.getByText('Hallo')).toBeInTheDocument())

    expect(screen.getByRole('button', { name: 'Kommentar bearbeiten' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Kommentar löschen' })).not.toBeInTheDocument()
  })

  it('zeigt Moderatoren den Löschen-Button', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit canModerateComments onClose={vi.fn()} {...apis} />)
    await waitFor(() => expect(screen.getByText('Hallo')).toBeInTheDocument())

    expect(screen.getByRole('button', { name: 'Kommentar löschen' })).toBeInTheDocument()
  })

  it('bearbeitet einen eigenen Kommentar inline', async () => {
    const apis = makeApis()
    apis.commentsApi.update = vi.fn().mockResolvedValue(
      { id: 1, cardId: 100, authorUserId: 7, authorName: 'A', body: 'Geändert', createdAt: '', updatedAt: '' },
    )
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)
    await waitFor(() => expect(screen.getByText('Hallo')).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'Kommentar bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Kommentar bearbeiten'), { target: { value: 'Geändert' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(apis.commentsApi.update).toHaveBeenCalledWith(1, 'Geändert'))
    expect(await screen.findByText('Geändert')).toBeInTheDocument()
  })

  it('öffnet eine Lightbox-Vorschau beim Klick auf einen PDF-Anhang', async () => {
    const apis = makeApis()
    apis.attachmentsApi.list = vi.fn().mockResolvedValue([
      { id: 5, cardId: 100, filename: 'doc.pdf', contentType: 'application/pdf', size: 2048, createdAt: '' },
    ])
    apis.attachmentsApi.fetchBlob = vi.fn().mockResolvedValue(new Blob(['x'], { type: 'application/pdf' }))
    render(<CardDetailModal card={card} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)

    fireEvent.click(await screen.findByRole('button', { name: 'doc.pdf' }))
    await waitFor(() => expect(screen.getByLabelText('Vorschau doc.pdf')).toBeInTheDocument())
  })
})
