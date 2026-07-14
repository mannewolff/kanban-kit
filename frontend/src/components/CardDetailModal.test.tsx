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
  type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
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
  const cardsApi = {
    update: vi.fn().mockResolvedValue({ ...card }),
    setAssignees: vi.fn().mockResolvedValue({ ...card }),
    setLabels: vi.fn().mockResolvedValue({ ...card }),
    getActivity: vi.fn().mockResolvedValue([]),
  }
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
      expect(apis.cardsApi.update).toHaveBeenCalledWith(100, 'Aufgabe', 'Neuer Text', [3, 4], undefined, null, null),
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

  const taskCard: Card = { ...card, description: '[ ] eins\n[ ] zwei' }

  it('rendert auch nackte [ ] als Checkbox', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={taskCard} canEdit onClose={vi.fn()} {...apis} />)

    expect(await screen.findByLabelText('Aufgabe 1')).toBeInTheDocument()
    expect(screen.getByLabelText('Aufgabe 2')).toBeInTheDocument()
  })

  it('persistiert den Klick auf die n-te Checkbox mit geflipptem Marker', async () => {
    const apis = makeApis()
    const onChanged = vi.fn()
    render(<CardDetailModal card={taskCard} canEdit onChanged={onChanged} onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByLabelText('Aufgabe 2'))

    await waitFor(() =>
      expect(apis.cardsApi.update).toHaveBeenCalledWith(
        100, 'Aufgabe', '[ ] eins\n[x] zwei', [3, 4], undefined, null, null,
      ),
    )
    expect(onChanged).toHaveBeenCalled()
  })

  it('lässt Checkboxen ohne Bearbeiten-Recht deaktiviert', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={taskCard} canEdit={false} onClose={vi.fn()} {...apis} />)

    const box = await screen.findByLabelText('Aufgabe 1')
    expect(box).toBeDisabled()
    fireEvent.click(box)
    expect(apis.cardsApi.update).not.toHaveBeenCalled()
  })

  it('zeigt Zuständige als Chips im Lesemodus (ohne Bearbeiten-Recht)', async () => {
    const apis = makeApis()
    const members = [
      { userId: 5, email: 'm@x.de', displayName: 'Max', role: 'MEMBER' as const },
    ]
    render(
      <CardDetailModal
        card={{ ...card, assignees: [5] }}
        canEdit={false}
        members={members}
        onClose={vi.fn()}
        {...apis}
      />,
    )

    expect(await screen.findByText('Max')).toBeInTheDocument()
  })

  it('setzt Zuständige über die Mehrfachauswahl', async () => {
    const apis = makeApis()
    const onChanged = vi.fn()
    const members = [
      { userId: 5, email: 'm@x.de', displayName: 'Max', role: 'MEMBER' as const },
      { userId: 6, email: 'e@x.de', displayName: 'Eva', role: 'MEMBER' as const },
    ]
    render(
      <CardDetailModal
        card={card}
        canEdit
        members={members}
        onClose={vi.fn()}
        onChanged={onChanged}
        {...apis}
      />,
    )

    const input = await screen.findByLabelText('Zuständige')
    fireEvent.mouseDown(input)
    fireEvent.click(await screen.findByText('Eva'))

    await waitFor(() => expect(apis.cardsApi.setAssignees).toHaveBeenCalledWith(100, [6]))
    expect(onChanged).toHaveBeenCalled()
  })

  it('zeigt ein überfälliges Datum hervorgehoben im Lesemodus', () => {
    const apis = makeApis()
    render(
      <CardDetailModal
        card={{ ...card, dueDate: '2020-01-01T00:00:00Z' }}
        canEdit={false}
        columnName="In Progress"
        onClose={vi.fn()}
        {...apis}
      />,
    )

    const due = screen.getByLabelText('Fälligkeitsdatum')
    expect(due).toHaveTextContent('überfällig')
  })

  it('speichert ein gesetztes Fälligkeitsdatum als ISO-Zeitstempel', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Fällig am'), { target: { value: '2026-08-01' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() =>
      expect(apis.cardsApi.update).toHaveBeenCalledWith(
        100, 'Aufgabe', expect.any(String), [3, 4], undefined, null, '2026-08-01T00:00:00Z',
      ),
    )
  })

  it('zeigt Labels als farbige Chips im Lesemodus', () => {
    const apis = makeApis()
    const boardLabels = [{ id: 5, boardId: 1, name: 'Bug', color: '#f00' }]
    render(
      <CardDetailModal
        card={{ ...card, labels: [5] }}
        canEdit={false}
        boardLabels={boardLabels}
        onClose={vi.fn()}
        {...apis}
      />,
    )

    expect(screen.getByText('Bug')).toBeInTheDocument()
  })

  it('setzt Labels über die Mehrfachauswahl', async () => {
    const apis = makeApis()
    const boardLabels = [
      { id: 5, boardId: 1, name: 'Bug', color: '#f00' },
      { id: 6, boardId: 1, name: 'Ux', color: '#0f0' },
    ]
    render(
      <CardDetailModal card={card} canEdit boardLabels={boardLabels} onClose={vi.fn()} {...apis} />,
    )

    fireEvent.mouseDown(await screen.findByLabelText('Labels'))
    fireEvent.click(await screen.findByText('Ux'))

    await waitFor(() => expect(apis.cardsApi.setLabels).toHaveBeenCalledWith(100, [6]))
  })

  it('zeigt den Aktivitätsverlauf mit Akteur und Detail', async () => {
    const apis = makeApis()
    apis.cardsApi.getActivity = vi.fn().mockResolvedValue([
      { id: 1, actorUserId: 5, type: 'MOVED', detail: 'Verschoben nach Done', createdAt: '2026-01-01T10:00:00Z' },
    ])
    const members = [{ userId: 5, email: 'm@x.de', displayName: 'Max', role: 'MEMBER' as const }]
    render(<CardDetailModal card={card} canEdit members={members} onClose={vi.fn()} {...apis} />)

    expect(await screen.findByText(/Verschoben nach Done/)).toBeInTheDocument()
    expect(screen.getByText(/Max/)).toBeInTheDocument()
  })
})
