import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AttachmentsApi } from '../api/attachments'
import type { Board } from '../api/boards'
import { ApiError } from '../api/client'
import type { Card, CardByNumber } from '../api/cards'
import type { CommentsApi } from '../api/comments'
import type { CardLocation } from '../lib/cardLocation'
import { CardDetailModal, commentFieldProps, parseDependencyInput, parseHerkunftInput } from './CardDetailModal'
import { SnackbarProvider } from './SnackbarProvider'
import { MAX_TEXT_LENGTH } from '../lib/textLimits'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 7, email: 'a@b.c', displayName: 'A', platformRole: 'USER', memberships: [] } }),
}))

const card: Card = {
  id: 100, boardId: 1, columnId: 10, number: 5, title: 'Aufgabe', description: '# Titel\n\n- a\n- b',
  positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [3, 4],
  type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [], derivedFrom: null,
}

/** Karte, auf die der Abhängigkeits-Verweis „#3“ zeigt — bewusst auf einem anderen Board. */
const linkedCard: CardByNumber = {
  id: 300, boardId: 2, columnId: 20, number: 3, title: 'Vorbedingung', description: 'Text der Vorbedingung',
  archived: false, ideaStored: false, dependencies: [], type: 'CARD', parentId: null, shortcode: null,
  assignees: [], dueDate: null, labels: [], derivedFrom: null,
}

const linkedBoard: Board = {
  id: 2, projectId: 9, name: 'Anderes Board', createdAt: '',
  columns: [{ id: 20, name: 'Done', position: 0, wipLimit: null }],
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
    restore: vi.fn().mockResolvedValue({ ...card }),
    moveToIdeaStorage: vi.fn().mockResolvedValue({ ...card }),
    byNumber: vi.fn().mockResolvedValue({ ...linkedCard }),
    assignDerivedFrom: vi.fn().mockResolvedValue({ ...card }),
    epicTree: vi.fn().mockResolvedValue([]),
    openEpic: vi.fn().mockResolvedValue({ ...card, id: 400, number: 9, title: 'Neues Vorhaben', type: 'EPIC' }),
  }
  const boardsApi = { get: vi.fn().mockResolvedValue(linkedBoard) }
  return { commentsApi, attachmentsApi, cardsApi, boardsApi }
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

describe('parseHerkunftInput', () => {
  it('nimmt eine positive Nummer und deutet leer als „keine Herkunft"', () => {
    expect(parseHerkunftInput('42')).toEqual({ herkunft: 42, valid: true })
    expect(parseHerkunftInput('  ')).toEqual({ herkunft: null, valid: true })
  })
  it('lehnt Nicht-Zahlen und die Null ab', () => {
    expect(parseHerkunftInput('abc').valid).toBe(false)
    expect(parseHerkunftInput('0').valid).toBe(false)
  })
})

describe('CardDetailModal', () => {
  beforeEach(() => {
    // jsdom kennt createObjectURL nicht.
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })

  it('zeigt den Bearbeiten-Button bei canEdit unabhängig vom Editiermodus (#324)', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)
    // Item-/Epic-Bearbeiten ist Kanban-Alltag und nicht mehr ans Editiermodus-Gate gekoppelt.
    expect(await screen.findByRole('button', { name: 'Bearbeiten' })).toBeInTheDocument()
  })

  it('rendert im Lesemodus Markdown, Abhängigkeiten und Kommentare', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)

    expect(screen.getByRole('heading', { name: 'Titel' })).toBeInTheDocument()
    expect(screen.getByLabelText('Abhängigkeiten')).toHaveTextContent('Abhängig von: #3, #4')
    expect(await screen.findByText('Hallo')).toBeInTheDocument()
  })

  it('legt einen Kommentar an', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.change(screen.getByLabelText('Kommentar schreiben'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Senden' }))

    await waitFor(() => expect(apis.commentsApi.create).toHaveBeenCalledWith(100, 'Neu'))
    expect(await screen.findByText('Neu')).toBeInTheDocument()
  })

  it('legt eine aktive Karte über den Detail-Button in den Ideen-Pool', async () => {
    const apis = makeApis()
    const onChanged = vi.fn()
    const onClose = vi.fn()
    render(<CardDetailModal card={card} canEdit onChanged={onChanged} onClose={onClose} {...apis} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByRole('button', { name: 'In den Ideen-Pool' }))

    await waitFor(() => expect(apis.cardsApi.moveToIdeaStorage).toHaveBeenCalledWith(100))
    expect(onChanged).toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
    // Erfolgs-Toast benennt den Zielort.
    expect(await screen.findByText('In den Ideen-Pool verschoben — unter Ideen zu finden.')).toBeInTheDocument()
  })

  it('zeigt bei Fehler einen Toast und lässt die Karte im Detail sichtbar', async () => {
    const apis = makeApis()
    apis.cardsApi.moveToIdeaStorage = vi.fn().mockRejectedValue(new Error('fail'))
    const onClose = vi.fn()
    render(<CardDetailModal card={card} canEdit onClose={onClose} {...apis} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByRole('button', { name: 'In den Ideen-Pool' }))

    expect(await screen.findByText('In den Ideen-Pool verschieben fehlgeschlagen.')).toBeInTheDocument()
    // Bei einem Fehler bleibt der Dialog offen — die Karte verschwindet nicht.
    expect(onClose).not.toHaveBeenCalled()
  })

  it('zeigt „In den Ideen-Pool“ nicht für eine bereits gespeicherte Idee', () => {
    const apis = makeApis()
    const idea: Card = { ...card, ideaStored: true }
    render(<CardDetailModal card={idea} canEdit onClose={vi.fn()} {...apis} />)

    expect(screen.queryByRole('button', { name: 'In den Ideen-Pool' })).not.toBeInTheDocument()
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

  it('zeigt einen Fehler-Toast, wenn das Speichern der Karte scheitert', async () => {
    const apis = makeApis()
    apis.cardsApi.update = vi.fn().mockRejectedValue(new Error('boom'))
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Markdown-Beschreibung'), { target: { value: 'X' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByText('Speichern fehlgeschlagen.')).toBeInTheDocument()
  })

  it('zeigt dem Autor Bearbeiten, aber ohne Moderationsrecht kein Löschen', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)
    expect(await screen.findByText('Hallo')).toBeInTheDocument()

    expect(screen.getByRole('button', { name: 'Kommentar bearbeiten' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Kommentar löschen' })).not.toBeInTheDocument()
  })

  it('zeigt Moderatoren den Löschen-Button', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit canModerateComments onClose={vi.fn()} {...apis} />)
    expect(await screen.findByText('Hallo')).toBeInTheDocument()

    expect(screen.getByRole('button', { name: 'Kommentar löschen' })).toBeInTheDocument()
  })

  it('bearbeitet einen eigenen Kommentar inline', async () => {
    const apis = makeApis()
    apis.commentsApi.update = vi.fn().mockResolvedValue(
      { id: 1, cardId: 100, authorUserId: 7, authorName: 'A', body: 'Geändert', createdAt: '', updatedAt: '' },
    )
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)
    expect(await screen.findByText('Hallo')).toBeInTheDocument()

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
    expect(await screen.findByLabelText('Vorschau doc.pdf')).toBeInTheDocument()
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

  it('zeigt ein künftiges Datum im Lesemodus ohne Überfällig-Hervorhebung', () => {
    const apis = makeApis()
    render(
      <CardDetailModal
        card={{ ...card, dueDate: '2099-12-31T00:00:00Z' }}
        canEdit={false}
        columnName="In Progress"
        onClose={vi.fn()}
        {...apis}
      />,
    )

    const due = screen.getByLabelText('Fälligkeitsdatum')
    expect(due).not.toHaveTextContent('überfällig')
    expect(due).toHaveTextContent('Fällig am')
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
    const boardLabels = [{ id: 5, boardId: 1, name: 'Bug', color: '#f00', countOnEpicTile: false }]
    render(
      <CardDetailModal
        card={{ ...card, labels: [5], derivedFrom: null }}
        canEdit={false}
        boardLabels={boardLabels}
        onClose={vi.fn()}
        {...apis}
      />,
    )

    expect(screen.getByText('Bug')).toBeInTheDocument()
  })

  it('zeigt für ein unbekanntes Label eine graue #ID-Chip', () => {
    const apis = makeApis()
    // Label 99 fehlt in boardLabels → find() undefined → Fallback `#99` und grey.500.
    render(
      <CardDetailModal
        card={{ ...card, labels: [99], derivedFrom: null }}
        canEdit={false}
        boardLabels={[{ id: 5, boardId: 1, name: 'Bug', color: '#f00', countOnEpicTile: false }]}
        onClose={vi.fn()}
        {...apis}
      />,
    )

    expect(screen.getByText('#99')).toBeInTheDocument()
  })

  it('setzt Labels über die Mehrfachauswahl', async () => {
    const apis = makeApis()
    const boardLabels = [
      { id: 5, boardId: 1, name: 'Bug', color: '#f00', countOnEpicTile: false },
      { id: 6, boardId: 1, name: 'Ux', color: '#0f0', countOnEpicTile: false },
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

  it('lässt Alt-Einträge ohne Herkunft unmarkiert', async () => {
    // Vor V23 gespeicherte Einträge tragen keine Herkunft — sie sehen aus wie bisher.
    const apis = makeApis()
    apis.cardsApi.getActivity = vi.fn().mockResolvedValue([
      { id: 1, actorUserId: 5, type: 'MOVED', detail: 'Verschoben', createdAt: '2026-01-01T10:00:00Z', origin: null, tokenName: null, agent: null },
    ])
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    expect(await screen.findByText(/Verschoben/)).toBeInTheDocument()
    expect(screen.queryByTestId('activity-token')).not.toBeInTheDocument()
    expect(screen.queryByTestId('activity-agent')).not.toBeInTheDocument()
  })

  it('lässt Session-Einträge unmarkiert — der Mensch ist der Default', async () => {
    const apis = makeApis()
    apis.cardsApi.getActivity = vi.fn().mockResolvedValue([
      { id: 1, actorUserId: 5, type: 'CREATED', detail: 'Karte angelegt', createdAt: '2026-01-01T10:00:00Z', origin: 'SESSION', tokenName: null, agent: null },
    ])
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    expect(await screen.findByText(/Karte angelegt/)).toBeInTheDocument()
    expect(screen.queryByTestId('activity-token')).not.toBeInTheDocument()
    expect(screen.queryByTestId('activity-agent')).not.toBeInTheDocument()
  })

  it('kennzeichnet Token-Einträge mit dem Token-Namen als Tatsache', async () => {
    const apis = makeApis()
    apis.cardsApi.getActivity = vi.fn().mockResolvedValue([
      { id: 1, actorUserId: 5, type: 'CREATED', detail: 'Idee angelegt', createdAt: '2026-01-01T10:00:00Z', origin: 'TOKEN', tokenName: 'Nachtlauf', agent: null },
    ])
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    const token = await screen.findByTestId('activity-token')
    expect(token).toHaveTextContent('Nachtlauf')
    expect(screen.queryByTestId('activity-agent')).not.toBeInTheDocument()
  })

  it('zeigt die Modell-Angabe eines Token-Eintrags als unverifizierte Angabe', async () => {
    const apis = makeApis()
    apis.cardsApi.getActivity = vi.fn().mockResolvedValue([
      { id: 1, actorUserId: 5, type: 'CREATED', detail: 'Idee angelegt', createdAt: '2026-01-01T10:00:00Z', origin: 'TOKEN', tokenName: 'Nachtlauf', agent: 'claude-opus-5' },
    ])
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    expect(await screen.findByTestId('activity-token')).toHaveTextContent('Nachtlauf')
    const agent = screen.getByTestId('activity-agent')
    expect(agent).toHaveTextContent('claude-opus-5')
    // Selbstauskunft, keine Tatsache: für Screenreader explizit als unverifizierte Angabe benannt.
    expect(agent).toHaveAttribute(
      'aria-label',
      'Modell-Angabe des Clients, nicht verifiziert: claude-opus-5',
    )
  })

  it('zeigt einen Token-Eintrag ohne Namen mit neutraler Beschriftung', async () => {
    // Defensiv: origin=TOKEN, aber kein Name überliefert.
    const apis = makeApis()
    apis.cardsApi.getActivity = vi.fn().mockResolvedValue([
      { id: 1, actorUserId: 5, type: 'CREATED', detail: 'Idee angelegt', createdAt: '2026-01-01T10:00:00Z', origin: 'TOKEN', tokenName: null, agent: null },
    ])
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    expect(await screen.findByTestId('activity-token')).toHaveTextContent('Token')
  })

  it('bietet bei archivierter Karte Wiederherstellen und ruft restore', async () => {
    const apis = makeApis()
    const onChanged = vi.fn()
    const onClose = vi.fn()
    render(
      <CardDetailModal
        card={{ ...card, archived: true }}
        canEdit
        onChanged={onChanged}
        onClose={onClose}
        {...apis}
      />,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Wiederherstellen' }))

    await waitFor(() => expect(apis.cardsApi.restore).toHaveBeenCalledWith(100))
    expect(onChanged).toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
  })

  it('zeigt keinen Wiederherstellen-Button für aktive Karten', () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    expect(screen.queryByRole('button', { name: 'Wiederherstellen' })).not.toBeInTheDocument()
  })

  const epicCard: Card = { ...card, id: 200, type: 'EPIC', shortcode: 'AUT' }

  /**
   * Die flache Kartenliste ist entfallen (Issue #660): Sie zeigte die per `parentId` zugeordneten
   * Karten -- und genau die sind im Herkunftsbaum die Wurzeln. Zwei Darstellungen desselben
   * Bestands untereinander, von denen die obere nichts beitraegt.
   */
  it('zeigt im Vorhaben keine flache Kartenliste mehr, sondern nur den Herkunftsbaum', async () => {
    const apis = makeApis()
    apis.cardsApi.epicTree.mockResolvedValue([baumZeile(7)])
    render(<CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />)

    expect(await screen.findByRole('tree')).toBeInTheDocument()
    expect(screen.queryByText(/^Karten \(/)).toBeNull()
    expect(screen.queryByText('Keine zugeordneten Karten.')).toBeNull()
  })

  // --- Herkunftsbaum im Vorhaben-Dialog (Issue #644) ------------------------

  /** Eine Baumzeile, wie sie GET /api/boards/{id}/epics/{epicId}/tree liefert. */
  function baumZeile(nummer: number, depth = 0) {
    return {
      number: nummer, title: `Karte ${nummer}`, type: 'CARD' as const, derivedFrom: null, depth,
      done: false, blocked: false, dependencies: [], externalDependencies: [],
      externalOrigin: false, broken: false, labels: [],
    }
  }

  it('zeigt im Detail-Dialog eines Vorhabens den Baum', async () => {
    const apis = makeApis()
    apis.cardsApi.epicTree.mockResolvedValue([baumZeile(7), baumZeile(8, 1)])
    render(<CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />)

    expect(await screen.findByRole('tree')).toBeInTheDocument()
    expect(screen.getAllByRole('treeitem')).toHaveLength(2)
    expect(apis.cardsApi.epicTree).toHaveBeenCalledWith(1, 200)
  })

  it('zeigt bei einer gewöhnlichen Karte keinen Baum und ruft den Endpunkt nicht auf', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    await screen.findByText('Aufgabe')
    expect(screen.queryByRole('tree')).toBeNull()
    expect(apis.cardsApi.epicTree).not.toHaveBeenCalled()
  })

  it('zeigt für ein Vorhaben ohne zugeordnete Karten den vorhabenbezogenen Hinweis', async () => {
    const apis = makeApis()
    apis.cardsApi.epicTree.mockResolvedValue([])
    render(<CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />)

    expect(
      await screen.findByText(/diesem vorhaben sind noch keine karten zugeordnet/i),
    ).toBeInTheDocument()
    expect(screen.queryByRole('tree')).toBeNull()
  })

  it('zeigt während des Ladens einen Platzhalter', () => {
    const apis = makeApis()
    apis.cardsApi.epicTree.mockReturnValue(new Promise(() => {}))
    render(<CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />)

    expect(screen.getByRole('progressbar')).toBeInTheDocument()
  })

  /**
   * Scheitert der Abruf, bleibt der Rest des Dialogs bedienbar: Der Baum ist ein Bereich neben
   * Beschreibung, Anhaengen und Kommentaren, nicht an ihrer Stelle.
   */
  it('zeigt bei fehlgeschlagenem Baum-Abruf eine Meldung und lässt den Dialog bedienbar', async () => {
    const apis = makeApis()
    apis.cardsApi.epicTree.mockRejectedValue(new ApiError(403, 'Kein Zugriff auf dieses Board.'))
    render(<CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />)

    expect(await screen.findByText(/kein zugriff auf dieses board/i)).toBeInTheDocument()
    expect(screen.queryByRole('tree')).toBeNull()
    // Die uebrigen Bereiche stehen weiterhin da.
    expect(screen.getByText('Kommentare')).toBeInTheDocument()
  })

  it('meldet auch einen Fehler ohne API-Kontext', async () => {
    const apis = makeApis()
    apis.cardsApi.epicTree.mockRejectedValue(new Error('Netzwerk weg'))
    render(<CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />)

    expect(await screen.findByText(/herkunftsbaum konnte nicht geladen werden/i)).toBeInTheDocument()
  })

  /**
   * Enter auf einer Baumzeile laedt die Karte in denselben Dialog — ueber denselben Verweis-Stack
   * wie die `#N`-Spruenge (Entscheidung 2026-08-30). Damit behaelt die Enter-Bedienung aus #611
   * ein Ziel.
   */
  it('lädt per Enter auf einer Baumzeile die Karte in denselben Dialog', async () => {
    const apis = makeApis()
    apis.cardsApi.epicTree.mockResolvedValue([baumZeile(3)])
    render(
      <CardDetailModal card={epicCard} canEdit projectId={9} onClose={vi.fn()} {...apis} />,
    )
    const zeile = (await screen.findAllByRole('treeitem'))[0]

    fireEvent.keyDown(zeile, { key: 'Enter' })

    // `byNumber` liefert die Vorbedingung — sie ersetzt die Vorhaben-Ansicht im selben Dialog.
    expect(await screen.findByText('Vorbedingung')).toBeInTheDocument()
    expect(apis.cardsApi.byNumber).toHaveBeenCalledWith(9, 3)
  })

  /**
   * Ohne diesen Schutz schriebe eine langsame Antwort in einen Dialog, den es nicht mehr gibt.
   * Der Test ist aus `DerivationTree.test.tsx` hierher gewandert: Dort lag frueher das Laden.
   */
  it('verwirft eine Baum-Antwort, die erst nach dem Schliessen eintrifft', async () => {
    const apis = makeApis()
    let aufloesen: (zeilen: unknown[]) => void = () => {}
    apis.cardsApi.epicTree.mockReturnValue(
      new Promise<unknown[]>((r) => {
        aufloesen = r
      }),
    )
    const { unmount } = render(
      <CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />,
    )
    unmount()

    aufloesen([baumZeile(7)])
    await Promise.resolve()

    expect(screen.queryByRole('tree')).toBeNull()
  })

  // --- Vorgang eröffnen (Issue #647) ----------------------------------------

  const vorgangKnopf = () => screen.getByRole('button', { name: 'Vorgang eröffnen' })

  async function oeffneVorgangsDialog(apis: ReturnType<typeof makeApis>, karte = card) {
    const onChanged = vi.fn()
    render(
      <CardDetailModal
        card={karte}
        canEdit
        projectId={9}
        onClose={vi.fn()}
        onChanged={onChanged}
        {...apis}
      />,
      { wrapper: SnackbarProvider },
    )
    await screen.findByText(karte.title)
    fireEvent.click(vorgangKnopf())
    return onChanged
  }

  it('zeigt an einer gewöhnlichen Karte ohne Vorhaben den Knopf „Vorgang eröffnen"', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />, {
      wrapper: SnackbarProvider,
    })

    await screen.findByText('Aufgabe')
    expect(vorgangKnopf()).toBeInTheDocument()
  })

  it('zeigt den Knopf an einem Vorhaben nicht', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />, {
      wrapper: SnackbarProvider,
    })

    await screen.findByText('Aufgabe')
    expect(screen.queryByRole('button', { name: 'Vorgang eröffnen' })).toBeNull()
  })

  it('zeigt den Knopf an einer bereits zugeordneten Karte nicht', async () => {
    // Stillschweigendes Umhaengen entzoege einer bestehenden Gruppierung eine Karte (#640).
    const apis = makeApis()
    render(
      <CardDetailModal card={{ ...card, parentId: 200 }} canEdit onClose={vi.fn()} {...apis} />,
      { wrapper: SnackbarProvider },
    )

    await screen.findByText('Aufgabe')
    expect(screen.queryByRole('button', { name: 'Vorgang eröffnen' })).toBeNull()
  })

  it('schlägt den Kartentitel als Namen vor', async () => {
    const apis = makeApis()
    await oeffneVorgangsDialog(apis)

    expect(screen.getByLabelText('Name des Vorhabens')).toHaveValue('Aufgabe')
  })

  it('eröffnet auch ohne Kürzel', async () => {
    const apis = makeApis()
    await oeffneVorgangsDialog(apis)

    fireEvent.click(screen.getByRole('button', { name: 'Eröffnen' }))

    await waitFor(() => expect(apis.cardsApi.openEpic).toHaveBeenCalledWith(100, 'Aufgabe', null))
  })

  it('macht den Erfolg sichtbar und führt zum neuen Vorhaben', async () => {
    const apis = makeApis()
    const onChanged = await oeffneVorgangsDialog(apis)

    fireEvent.click(screen.getByRole('button', { name: 'Eröffnen' }))

    expect(await screen.findByText('Vorgang eröffnet: Neues Vorhaben')).toBeInTheDocument()
    // Der Aufrufer laedt nach: Die Karte traegt jetzt eine Vorhaben-Zuordnung.
    await waitFor(() => expect(onChanged).toHaveBeenCalled())
    // Der Weg zum neuen Vorhaben laeuft ueber denselben Verweis-Stack wie die `#N`-Spruenge.
    await waitFor(() => expect(apis.cardsApi.byNumber).toHaveBeenCalledWith(9, 9))
  })

  /**
   * Die Ablehnungen aus #640 tragen einen Feldbezug; eine eigene Ersatzmeldung liesse den Nutzer
   * raten, woran es lag.
   */
  it('zeigt bei einem Fehler die Meldung des Servers und lässt den Dialog bedienbar', async () => {
    const apis = makeApis()
    apis.cardsApi.openEpic.mockRejectedValue(
      new ApiError(400, 'Die Karte ist bereits einem Vorhaben zugeordnet: 5'),
    )
    await oeffneVorgangsDialog(apis)

    fireEvent.click(screen.getByRole('button', { name: 'Eröffnen' }))

    expect(
      await screen.findByText('Die Karte ist bereits einem Vorhaben zugeordnet: 5'),
    ).toBeInTheDocument()
    // Der Dialog bleibt offen und bedienbar.
    expect(screen.getByRole('button', { name: 'Eröffnen' })).toBeInTheDocument()
  })

  it('lässt den vorgeschlagenen Namen überschreiben', async () => {
    const apis = makeApis()
    await oeffneVorgangsDialog(apis)

    fireEvent.change(screen.getByLabelText('Name des Vorhabens'), {
      target: { value: 'Eigener Name' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Eröffnen' }))

    await waitFor(() =>
      expect(apis.cardsApi.openEpic).toHaveBeenCalledWith(100, 'Eigener Name', null),
    )
  })

  it('bricht ohne Aufruf ab', async () => {
    const apis = makeApis()
    await oeffneVorgangsDialog(apis)

    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

    expect(apis.cardsApi.openEpic).not.toHaveBeenCalled()
  })

  it('schließt den Dialog auch per Escape, ohne zu eröffnen', async () => {
    const apis = makeApis()
    await oeffneVorgangsDialog(apis)

    fireEvent.keyDown(screen.getByRole('dialog', { name: 'Vorgang eröffnen' }), { key: 'Escape' })

    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Vorgang eröffnen' })).toBeNull(),
    )
    expect(apis.cardsApi.openEpic).not.toHaveBeenCalled()
  })

  it('gibt ein eingegebenes Kürzel mit', async () => {
    const apis = makeApis()
    await oeffneVorgangsDialog(apis)

    fireEvent.change(screen.getByLabelText('Kürzel (optional)'), { target: { value: 'VOR' } })
    fireEvent.click(screen.getByRole('button', { name: 'Eröffnen' }))

    await waitFor(() => expect(apis.cardsApi.openEpic).toHaveBeenCalledWith(100, 'Aufgabe', 'VOR'))
  })

  /**
   * Ohne `projectId` gibt es keine Aufloesung von Kartennummern — dann bleibt es bei der Meldung,
   * statt ins Leere zu springen. Dieselbe Semantik wie bei den `#N`-Verweisen.
   */
  it('meldet den Erfolg auch ohne Projekt-ID, führt dann aber nicht weiter', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />, {
      wrapper: SnackbarProvider,
    })
    await screen.findByText('Aufgabe')
    fireEvent.click(vorgangKnopf())
    fireEvent.click(screen.getByRole('button', { name: 'Eröffnen' }))

    expect(await screen.findByText('Vorgang eröffnet: Neues Vorhaben')).toBeInTheDocument()
    expect(apis.cardsApi.byNumber).not.toHaveBeenCalled()
  })

  it('meldet auch einen Fehler ohne API-Kontext', async () => {
    const apis = makeApis()
    apis.cardsApi.openEpic.mockRejectedValue(new Error('Netzwerk weg'))
    await oeffneVorgangsDialog(apis)

    fireEvent.click(screen.getByRole('button', { name: 'Eröffnen' }))

    expect(await screen.findByText('Vorgang eröffnen fehlgeschlagen.')).toBeInTheDocument()
  })

  /**
   * Der Knopf muss per Tastatur ausloesbar sein. Ein Test, der nur klickt, belegt das nicht:
   * jsx-a11y prueft nur DOM-Elemente in Kleinschreibung, keine MUI-Komponenten.
   */
  it('löst den Knopf auch per Tastatur aus', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />, {
      wrapper: SnackbarProvider,
    })
    await screen.findByText('Aufgabe')

    vorgangKnopf().focus()
    expect(vorgangKnopf()).toHaveFocus()
    await userEvent.keyboard('{Enter}')

    expect(screen.getByRole('dialog', { name: 'Vorgang eröffnen' })).toBeInTheDocument()
  })

  it('zeigt im Edit-Modus eines Epics nur das Kürzel-Feld', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))

    expect(screen.getByLabelText('Kürzel')).toHaveValue('AUT')
    expect(screen.queryByLabelText('Vorhaben')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Fällig am')).not.toBeInTheDocument()
  })

  it('bietet im Edit-Modus einer Nicht-Epic-Karte die Epic-Zuordnung an', async () => {
    const apis = makeApis()
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    render(<CardDetailModal card={card} canEdit epics={epics} onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Vorhaben'), { target: { value: '9' } })

    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() =>
      expect(apis.cardsApi.update).toHaveBeenCalledWith(100, 'Aufgabe', expect.any(String), [3, 4], undefined, 9, null),
    )
  })

  it('setzt die Epic-Zuordnung über die leere Auswahl wieder auf null', async () => {
    const apis = makeApis()
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    render(<CardDetailModal card={{ ...card, parentId: 9 }} canEdit epics={epics} onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    // Auswahl auf „—" (leerer Wert) → onParentIdChange(null), deckt den `=== '' ? null`-Zweig ab.
    fireEvent.change(screen.getByLabelText('Vorhaben'), { target: { value: '' } })

    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() =>
      expect(apis.cardsApi.update).toHaveBeenCalledWith(100, 'Aufgabe', expect.any(String), [3, 4], undefined, null, null),
    )
  })

  it('lässt eine gesetzte Epic-Zuordnung ohne ladbare Epic-Liste unangetastet', async () => {
    const apis = makeApis()
    render(
      <CardDetailModal
        card={{ ...card, parentId: 9 }}
        canEdit
        canEditEpic={false}
        epics={[]}
        onClose={vi.fn()}
        {...apis}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))

    // Lesend statt Auswahl: Ein leerer Optionsvorrat böte nur „(kein Epic)" an — ein Klick darauf
    // löschte die Zuordnung, ohne sie je gezeigt zu haben (#586).
    const field = screen.getByLabelText('Vorhaben')
    expect(field).toHaveValue('#9')
    expect(field).toHaveAttribute('readonly')

    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() =>
      expect(apis.cardsApi.update).toHaveBeenCalledWith(100, 'Aufgabe', expect.any(String), [3, 4], undefined, 9, null),
    )
  })

  it('zeigt die Label-Sektion ohne ladbare Label-Liste nur lesend', async () => {
    const apis = makeApis()
    render(
      <CardDetailModal
        card={{ ...card, labels: [6], derivedFrom: null }}
        canEdit
        canEditLabels={false}
        boardLabels={[]}
        onClose={vi.fn()}
        {...apis}
      />,
    )

    // Nummern-Fallback statt Name: Das Label ist gesetzt und bleibt sichtbar, auch ohne Vorrat.
    expect(await screen.findByText('#6')).toBeInTheDocument()
    expect(screen.queryByLabelText('Labels')).not.toBeInTheDocument()
    expect(apis.cardsApi.setLabels).not.toHaveBeenCalled()
  })

  it('deaktiviert den Speichern-Button bei leerem Titel im Edit-Modus', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: '   ' } })

    // Der Button ist die Gatung (kein redundanter Guard in save()): leerer Titel → disabled.
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
  })

  it('zeigt „Keine Beschreibung.“ bei leerem Beschreibungstext', () => {
    const apis = makeApis()
    render(<CardDetailModal card={{ ...card, description: null }} canEdit={false} onClose={vi.fn()} {...apis} />)

    expect(screen.getByText('Keine Beschreibung.')).toBeInTheDocument()
  })

  it('zeigt einen nicht-vorschaubaren Anhang als Download-Link', async () => {
    const apis = makeApis()
    apis.attachmentsApi.list = vi.fn().mockResolvedValue([
      { id: 6, cardId: 100, filename: 'notiz.txt', contentType: 'text/plain', size: 512, createdAt: '' },
    ])
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    const link = await screen.findByRole('link', { name: 'notiz.txt' })
    expect(link).toHaveAttribute('href', '/api/attachments/6')
  })

  it('lädt für Bild-Anhänge automatisch eine Vorschau und öffnet sie per Klick', async () => {
    const apis = makeApis()
    apis.attachmentsApi.list = vi.fn().mockResolvedValue([
      { id: 7, cardId: 100, filename: 'bild.png', contentType: 'image/png', size: 1024, createdAt: '' },
    ])
    apis.attachmentsApi.fetchBlob = vi.fn().mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    const thumb = await screen.findByAltText('bild.png')
    expect(thumb).toHaveAttribute('src', 'blob:preview')

    fireEvent.click(thumb)
    expect(await screen.findByLabelText('Vorschau bild.png')).toBeInTheDocument()
  })

  it('lädt eine Datei hoch und zeigt sie in der Liste', async () => {
    const apis = makeApis()
    const created = { id: 8, cardId: 100, filename: 'neu.pdf', contentType: 'application/pdf', size: 100, createdAt: '' }
    apis.attachmentsApi.upload = vi.fn().mockResolvedValue(created)
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    const file = new File(['x'], 'neu.pdf', { type: 'application/pdf' })
    fireEvent.change(screen.getByLabelText('Datei anhängen'), { target: { files: [file] } })

    expect(await screen.findByRole('button', { name: 'neu.pdf' })).toBeInTheDocument()
    expect(apis.attachmentsApi.upload).toHaveBeenCalledWith(100, file)
  })

  it('lädt eine Bilddatei hoch und deren Vorschau gleich mit', async () => {
    const apis = makeApis()
    const created = { id: 9, cardId: 100, filename: 'foto.png', contentType: 'image/png', size: 100, createdAt: '' }
    apis.attachmentsApi.upload = vi.fn().mockResolvedValue(created)
    apis.attachmentsApi.fetchBlob = vi.fn().mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    const file = new File(['x'], 'foto.png', { type: 'image/png' })
    fireEvent.change(screen.getByLabelText('Datei anhängen'), { target: { files: [file] } })

    expect(await screen.findByAltText('foto.png')).toBeInTheDocument()
  })

  it('zeigt einen Fehler, wenn der Upload fehlschlägt', async () => {
    const apis = makeApis()
    apis.attachmentsApi.upload = vi.fn().mockRejectedValue(new Error('boom'))
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    const file = new File(['x'], 'neu.pdf', { type: 'application/pdf' })
    fireEvent.change(screen.getByLabelText('Datei anhängen'), { target: { files: [file] } })

    expect(await screen.findByText(/Upload fehlgeschlagen/)).toBeInTheDocument()
  })

  it('löscht einen Anhang', async () => {
    const apis = makeApis()
    apis.attachmentsApi.list = vi.fn().mockResolvedValue([
      { id: 6, cardId: 100, filename: 'notiz.txt', contentType: 'text/plain', size: 512, createdAt: '' },
    ])
    apis.attachmentsApi.remove = vi.fn().mockResolvedValue(undefined)
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(await screen.findByLabelText('Anhang notiz.txt löschen'))

    await waitFor(() => expect(apis.attachmentsApi.remove).toHaveBeenCalledWith(6))
    expect(screen.queryByText('notiz.txt')).not.toBeInTheDocument()
  })

  it('zeigt einen Fehler, wenn die Anhangs-Vorschau nicht geladen werden kann', async () => {
    const apis = makeApis()
    apis.attachmentsApi.list = vi.fn().mockResolvedValue([
      { id: 5, cardId: 100, filename: 'doc.pdf', contentType: 'application/pdf', size: 2048, createdAt: '' },
    ])
    apis.attachmentsApi.fetchBlob = vi.fn().mockRejectedValue(new Error('boom'))
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(await screen.findByRole('button', { name: 'doc.pdf' }))
    expect(await screen.findByText('Vorschau konnte nicht geladen werden.')).toBeInTheDocument()
  })

  it('schließt die Anhangs-Vorschau wieder', async () => {
    const apis = makeApis()
    apis.attachmentsApi.list = vi.fn().mockResolvedValue([
      { id: 5, cardId: 100, filename: 'doc.pdf', contentType: 'application/pdf', size: 2048, createdAt: '' },
    ])
    apis.attachmentsApi.fetchBlob = vi.fn().mockResolvedValue(new Blob(['x'], { type: 'application/pdf' }))
    render(<CardDetailModal card={card} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)

    fireEvent.click(await screen.findByRole('button', { name: 'doc.pdf' }))
    expect(await screen.findByLabelText('Vorschau doc.pdf')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /schließen/i }))
    await waitFor(() => expect(screen.queryByLabelText('Vorschau doc.pdf')).not.toBeInTheDocument())
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview')
  })

  it('zeigt frisch gespeicherte Abhängigkeiten sofort im Lesemodus (#537)', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Abhängig von'), { target: { value: '12' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    // Ohne Schließen/Neuöffnen: der Lesemodus zeigt den gespeicherten Stand, nicht die alte Prop.
    expect(await screen.findByLabelText('Abhängigkeiten')).toHaveTextContent('Abhängig von: #12')

    // Und erneutes Bearbeiten startet mit dem gespeicherten Stand im Eingabefeld.
    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    expect(screen.getByLabelText('Abhängig von')).toHaveValue('12')
  })

  it('sendet beim Task-Toggle die zuletzt gespeicherten Abhängigkeiten mit (#537)', async () => {
    // Ein Checkbox-Klick nach dem Abhängigkeits-Save darf den frischen Stand nicht mit der
    // veralteten Prop überschreiben.
    const apis = makeApis()
    render(<CardDetailModal card={taskCard} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Abhängig von'), { target: { value: '12' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(apis.cardsApi.update).toHaveBeenCalledTimes(1))

    fireEvent.click(await screen.findByLabelText('Aufgabe 2'))

    await waitFor(() => expect(apis.cardsApi.update).toHaveBeenCalledTimes(2))
    expect(apis.cardsApi.update).toHaveBeenLastCalledWith(
      100,
      taskCard.title,
      expect.any(String),
      [12],
      undefined,
      null,
      null,
    )
  })

  it('zeigt einen Fehler bei ungültiger Abhängigkeits-Eingabe und löscht ihn beim erneuten Tippen', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Abhängig von'), { target: { value: '12, x' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(
      await screen.findByText('Nur positive Nummern, kommagetrennt (z. B. 12, 34).'),
    ).toBeInTheDocument()
    expect(apis.cardsApi.update).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Abhängig von'), { target: { value: '12' } })
    expect(
      screen.queryByText('Nur positive Nummern, kommagetrennt (z. B. 12, 34).'),
    ).not.toBeInTheDocument()
  })

  it('rollt die Checkbox zurück, wenn das Persistieren fehlschlägt', async () => {
    const apis = makeApis()
    apis.cardsApi.update = vi.fn().mockRejectedValue(new Error('boom'))
    render(<CardDetailModal card={taskCard} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(await screen.findByLabelText('Aufgabe 2'))

    await waitFor(() => expect(apis.cardsApi.update).toHaveBeenCalled())
    await waitFor(() => expect(screen.getByLabelText('Aufgabe 2')).not.toBeChecked())
  })

  it('löscht einen Kommentar', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit canModerateComments onClose={vi.fn()} {...apis} />)
    expect(await screen.findByText('Hallo')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Kommentar löschen' }))

    await waitFor(() => expect(apis.commentsApi.remove).toHaveBeenCalledWith(1))
    expect(screen.queryByText('Hallo')).not.toBeInTheDocument()
  })

  it('zeigt „System“, wenn der Akteur einer Aktivität nicht (mehr) Mitglied ist', async () => {
    const apis = makeApis()
    apis.cardsApi.getActivity = vi.fn().mockResolvedValue([
      { id: 1, actorUserId: 99, type: 'MOVED', detail: 'Verschoben nach Done', createdAt: '2026-01-01T10:00:00Z' },
    ])
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    expect(await screen.findByText(/System/)).toBeInTheDocument()
  })

  it('ruft onChanged nach dem Setzen von Labels auf', async () => {
    const apis = makeApis()
    const onChanged = vi.fn()
    const boardLabels = [{ id: 5, boardId: 1, name: 'Bug', color: '#f00', countOnEpicTile: false }]
    render(
      <CardDetailModal card={card} canEdit boardLabels={boardLabels} onChanged={onChanged} onClose={vi.fn()} {...apis} />,
    )

    fireEvent.mouseDown(await screen.findByLabelText('Labels'))
    fireEvent.click(await screen.findByText('Bug'))

    await waitFor(() => expect(apis.cardsApi.setLabels).toHaveBeenCalled())
    expect(onChanged).toHaveBeenCalled()
  })

  it('übernimmt vorhandene Beschreibung und Fälligkeitsdatum beim Öffnen des Edit-Modus', async () => {
    const apis = makeApis()
    const filled: Card = { ...card, description: 'Vorhandener Text', dueDate: '2026-08-01T00:00:00Z' }
    render(<CardDetailModal card={filled} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))

    expect(screen.getByLabelText('Markdown-Beschreibung')).toHaveValue('Vorhandener Text')
    expect(screen.getByLabelText('Fällig am')).toHaveValue('2026-08-01')
  })

  it('bietet im Edit-Modus die bereits gesetzte Epic-Zuordnung an', async () => {
    const apis = makeApis()
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    const linked: Card = { ...card, parentId: 9 }
    render(<CardDetailModal card={linked} canEdit epics={epics} onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))

    expect(screen.getByLabelText('Vorhaben')).toHaveValue('9')
  })

  it('speichert eine Epic-Karte mit geändertem Titel und Kürzel', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Neuer Titel' } })
    fireEvent.change(screen.getByLabelText('Kürzel'), { target: { value: 'NEU' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() =>
      expect(apis.cardsApi.update).toHaveBeenCalledWith(
        200, 'Neuer Titel', expect.any(String), [3, 4], 'NEU', undefined, undefined,
      ),
    )
  })

  it('persistiert einen Checkbox-Klick auf einer Epic-Karte mit Epic-typischen Update-Feldern', async () => {
    const apis = makeApis()
    const epicTaskCard: Card = { ...epicCard, description: '[ ] eins\n[ ] zwei' }
    render(<CardDetailModal card={epicTaskCard} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(await screen.findByLabelText('Aufgabe 2'))

    await waitFor(() =>
      expect(apis.cardsApi.update).toHaveBeenCalledWith(
        200, 'Aufgabe', '[ ] eins\n[x] zwei', [3, 4], 'AUT', undefined, undefined,
      ),
    )
  })

  it('legt keinen leeren Kommentar an', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Senden' }))

    expect(apis.commentsApi.create).not.toHaveBeenCalled()
  })

  it('speichert keinen leeren Kommentar beim inline-Bearbeiten und bricht per Abbrechen ab', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)
    expect(await screen.findByText('Hallo')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Kommentar bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Kommentar bearbeiten'), { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(apis.commentsApi.update).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(screen.queryByRole('textbox', { name: 'Kommentar bearbeiten' })).not.toBeInTheDocument()
    expect(screen.getByText('Hallo')).toBeInTheDocument()
  })

  it('schließt das Modal per Escape im Lesemodus', async () => {
    const onClose = vi.fn()
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={onClose} {...apis} />)

    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' })
    expect(onClose).toHaveBeenCalled()
  })

  it('bricht das Bearbeiten der Karte über Abbrechen ab', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Verworfen' } })
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

    expect(screen.getByRole('heading', { name: 'Titel' })).toBeInTheDocument()
    expect(apis.cardsApi.update).not.toHaveBeenCalled()
  })

  it('vergleicht bereits zugewiesene Zuständige beim Öffnen der Auswahl', async () => {
    const apis = makeApis()
    const members = [
      { userId: 5, email: 'm@x.de', displayName: 'Max', role: 'MEMBER' as const },
      { userId: 6, email: 'e@x.de', displayName: 'Eva', role: 'MEMBER' as const },
    ]
    render(
      <CardDetailModal
        card={{ ...card, assignees: [5] }}
        canEdit
        members={members}
        onClose={vi.fn()}
        {...apis}
      />,
    )

    fireEvent.mouseDown(await screen.findByLabelText('Zuständige'))
    expect(await screen.findByText('Eva')).toBeInTheDocument()
  })

  it('vergleicht bereits gesetzte Labels beim Öffnen der Auswahl', async () => {
    const apis = makeApis()
    const boardLabels = [
      { id: 5, boardId: 1, name: 'Bug', color: '#f00', countOnEpicTile: false },
      { id: 6, boardId: 1, name: 'Ux', color: '#0f0', countOnEpicTile: false },
    ]
    render(
      <CardDetailModal card={{ ...card, labels: [5], derivedFrom: null }} canEdit boardLabels={boardLabels} onClose={vi.fn()} {...apis} />,
    )

    fireEvent.mouseDown(await screen.findByLabelText('Labels'))
    expect(await screen.findByText('Ux')).toBeInTheDocument()
  })

  it('zeigt eine Nummer statt eines Namens für einen unbekannten Zuständigen', async () => {
    const apis = makeApis()
    render(
      <CardDetailModal card={{ ...card, assignees: [42] }} canEdit={false} onClose={vi.fn()} {...apis} />,
    )

    expect(await screen.findByText('#42')).toBeInTheDocument()
  })

  it('übernimmt eine leere Beschreibung beim Öffnen des Edit-Modus', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={{ ...card, description: null }} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    expect(screen.getByLabelText('Markdown-Beschreibung')).toHaveValue('')
  })

  it('speichert eine Epic-Karte mit geleertem Kürzel als null', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Kürzel'), { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() =>
      expect(apis.cardsApi.update).toHaveBeenCalledWith(
        200, 'Aufgabe', expect.any(String), [3, 4], null, undefined, undefined,
      ),
    )
  })

  it('persistiert einen Checkbox-Klick auf einer Epic-Karte ohne Kürzel als null', async () => {
    const apis = makeApis()
    const epicTaskCardNoShortcode: Card = { ...epicCard, shortcode: null, description: '[ ] eins\n[ ] zwei' }
    render(<CardDetailModal card={epicTaskCardNoShortcode} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(await screen.findByLabelText('Aufgabe 2'))

    await waitFor(() =>
      expect(apis.cardsApi.update).toHaveBeenCalledWith(
        200, 'Aufgabe', '[ ] eins\n[x] zwei', [3, 4], null, undefined, undefined,
      ),
    )
  })

  it('bearbeitet einen Kommentar inline, ohne die übrigen Kommentare zu verändern', async () => {
    const apis = makeApis()
    apis.commentsApi.list = vi.fn().mockResolvedValue([
      { id: 1, cardId: 100, authorUserId: 7, authorName: 'A', body: 'Hallo', createdAt: '', updatedAt: '' },
      { id: 2, cardId: 100, authorUserId: 7, authorName: 'A', body: 'Zweiter', createdAt: '', updatedAt: '' },
    ])
    apis.commentsApi.update = vi.fn().mockResolvedValue(
      { id: 1, cardId: 100, authorUserId: 7, authorName: 'A', body: 'Geändert', createdAt: '', updatedAt: '' },
    )
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)
    expect(await screen.findByText('Zweiter')).toBeInTheDocument()

    fireEvent.click(screen.getAllByRole('button', { name: 'Kommentar bearbeiten' })[0])
    fireEvent.change(screen.getByRole('textbox', { name: 'Kommentar bearbeiten' }), { target: { value: 'Geändert' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(apis.commentsApi.update).toHaveBeenCalled())
    expect(await screen.findByText('Geändert')).toBeInTheDocument()
    expect(screen.getByText('Zweiter')).toBeInTheDocument()
  })

  it('schließt den Edit-Modus per Escape, ohne das ganze Modal zu schließen', async () => {
    const onClose = vi.fn()
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={onClose} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' })

    expect(await screen.findByRole('heading', { name: 'Titel' })).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  // Abhängigkeits-Verweise (#488): #N führt projektweit zur verknüpften Karte.
  it('stellt Abhängigkeits-Verweise ohne projectId als reinen Text dar', () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    expect(screen.getByLabelText('Abhängigkeiten')).toHaveTextContent('Abhängig von: #3, #4')
    expect(screen.queryByRole('button', { name: 'Karte #3 öffnen' })).not.toBeInTheDocument()
  })

  it('öffnet beim Klick auf #3 die verknüpfte Karte samt Status ihres eigenen Boards', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit projectId={9} columnName="In Progress" onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Karte #3 öffnen' }))

    expect(await screen.findByText('Vorbedingung')).toBeInTheDocument()
    expect(apis.cardsApi.byNumber).toHaveBeenCalledWith(9, 3)
    // Spaltenname stammt aus dem nachgeladenen fremden Board, nicht aus dem Kontext der Ausgangskarte.
    expect(apis.boardsApi.get).toHaveBeenCalledWith(2)
    expect(await screen.findByText('Done')).toBeInTheDocument()
    // Der Board-Kontext der verknüpften Karte fehlt — sie wird deshalb nur gelesen.
    expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument()
  })

  it('führt von der verknüpften Karte per Zurück zur Ausgangskarte', async () => {
    const apis = makeApis()
    const onClose = vi.fn()
    render(<CardDetailModal card={card} canEdit projectId={9} onClose={onClose} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Karte #3 öffnen' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Zurück zur vorherigen Karte' }))

    expect(await screen.findByText('Aufgabe')).toBeInTheDocument()
    expect(screen.queryByText('Vorbedingung')).not.toBeInTheDocument()
    // Zurück navigiert nur im Modal, es schließt es nicht.
    expect(onClose).not.toHaveBeenCalled()
  })

  it('meldet einen nicht auflösbaren Verweis und bleibt bei der Ausgangskarte', async () => {
    const apis = makeApis()
    apis.cardsApi.byNumber = vi.fn().mockRejectedValue(new Error('404'))
    render(<CardDetailModal card={card} canEdit projectId={9} onClose={vi.fn()} {...apis} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByRole('button', { name: 'Karte #4 öffnen' }))

    expect(await screen.findByText('Karte #4 nicht gefunden — gelöscht oder kein Zugriff.')).toBeInTheDocument()
    expect(screen.getByText('Aufgabe')).toBeInTheDocument()
  })

  it('öffnet einen Verweis per Tastatur', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit projectId={9} onClose={vi.fn()} {...apis} />)

    const link = screen.getByRole('button', { name: 'Karte #3 öffnen' })
    link.focus()
    expect(link).toHaveFocus()
    await userEvent.keyboard('{Enter}')

    expect(await screen.findByText('Vorbedingung')).toBeInTheDocument()
  })

  it('zeigt eine board-lose Pool-Idee ohne Status-Chip und ohne Board-Abruf', async () => {
    const apis = makeApis()
    apis.cardsApi.byNumber = vi.fn().mockResolvedValue({ ...linkedCard, boardId: null, columnId: null })
    render(<CardDetailModal card={card} canEdit projectId={9} onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Karte #3 öffnen' }))

    expect(await screen.findByText('Vorbedingung')).toBeInTheDocument()
    expect(apis.boardsApi.get).not.toHaveBeenCalled()
    expect(screen.queryByText('Done')).not.toBeInTheDocument()
  })

  it('zeigt die verknüpfte Karte auch, wenn ihr Board nicht geladen werden kann', async () => {
    const apis = makeApis()
    apis.boardsApi.get = vi.fn().mockRejectedValue(new Error('403'))
    render(<CardDetailModal card={card} canEdit projectId={9} onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Karte #3 öffnen' }))

    expect(await screen.findByText('Vorbedingung')).toBeInTheDocument()
    expect(screen.queryByText('Done')).not.toBeInTheDocument()
  })

  const onBoard: CardLocation = {
    projectId: 9,
    projectName: 'IT-Bildungshaus',
    board: { id: 1, name: 'Entwicklung', columnName: 'In Progress' },
  }

  it('zeigt den Ortspfad aus Projekt, Board und Spalte', () => {
    const apis = makeApis()
    render(
      <MemoryRouter>
        <CardDetailModal card={card} canEdit location={onBoard} onClose={vi.fn()} {...apis} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'IT-Bildungshaus' })).toHaveAttribute('href', '/projects/9')
    expect(screen.getByRole('link', { name: 'Entwicklung' })).toHaveAttribute('href', '/boards/1')
    // Der Spaltenname steht im Pfad und (bei gesetztem columnName) zusätzlich im Status-Chip.
    expect(screen.getAllByText('In Progress').length).toBeGreaterThan(0)
  })

  it('zeigt für eine board-lose Pool-Idee den verkürzten Pfad zu den Ideen', () => {
    const apis = makeApis()
    render(
      <MemoryRouter>
        <CardDetailModal
          card={{ ...card, ideaStored: true }}
          canEdit
          location={{ projectId: 9, projectName: 'IT-Bildungshaus', board: null }}
          onClose={vi.fn()}
          {...apis}
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Ideen' })).toHaveAttribute('href', '/projects/9/ideas')
    expect(screen.queryByRole('link', { name: 'Entwicklung' })).not.toBeInTheDocument()
  })

  it('zeigt ohne Ortsangabe keinen Pfad', () => {
    const apis = makeApis()
    render(
      <MemoryRouter>
        <CardDetailModal card={card} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('link', { name: 'IT-Bildungshaus' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })

  it('überträgt den Ortspfad nicht auf eine über #N geöffnete Karte eines anderen Boards', async () => {
    const apis = makeApis()
    render(
      <MemoryRouter>
        <CardDetailModal card={card} canEdit projectId={9} location={onBoard} onClose={vi.fn()} {...apis} />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Karte #3 öffnen' }))

    expect(await screen.findByText('Vorbedingung')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Entwicklung' })).not.toBeInTheDocument()
  })

  it('sperrt Senden und meldet die Ueberschreitung bei einem zu langen neuen Kommentar', () => {
    // Der Text bleibt vollstaendig stehen; abgeschickt wird nichts (Issue #572).
    const apis = makeApis()
    const tooLong = 'a'.repeat(MAX_TEXT_LENGTH + 10_000)
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.change(screen.getByLabelText('Kommentar schreiben'), { target: { value: tooLong } })

    expect(screen.getByLabelText('Kommentar schreiben')).toHaveValue(tooLong)
    expect(screen.getByText('60.000 / 50.000 Zeichen')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Senden' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Senden' }))
    expect(apis.commentsApi.create).not.toHaveBeenCalled()
  })

  it('sperrt Speichern beim Bearbeiten eines Kommentars ueber der Grenze', async () => {
    const apis = makeApis()
    const tooLong = 'a'.repeat(MAX_TEXT_LENGTH + 10_000)
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)
    expect(await screen.findByText('Hallo')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Kommentar bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Kommentar bearbeiten'), { target: { value: tooLong } })

    expect(screen.getByLabelText('Kommentar bearbeiten')).toHaveValue(tooLong)
    expect(screen.getByText('60.000 / 50.000 Zeichen')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(apis.commentsApi.update).not.toHaveBeenCalled()
  })
})

/** Kommentar-Darstellung (Issue #575): Markdown statt einzeiliger Textwand. */
describe('CardDetailModal — Kommentar-Body als Markdown', () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })

  /** Apis mit genau einem Kommentar des angegebenen Bodys. */
  function apisWithComment(body: string) {
    const apis = makeApis()
    apis.commentsApi.list = vi.fn().mockResolvedValue([
      { id: 1, cardId: 100, authorUserId: 7, authorName: 'A', body, createdAt: '', updatedAt: '' },
    ])
    return apis
  }

  /** Rendert das Modal und liefert den Container des einzigen Kommentar-Bodys. */
  async function renderComment(body: string) {
    const apis = apisWithComment(body)
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)
    return { apis, body: await screen.findByTestId('comment-body') }
  }

  it('rendert Ueberschrift, Aufzaehlung und Codeblock als eigene Elemente', async () => {
    const { body } = await renderComment('## Befund\n\n- eins\n- zwei\n\n```\nnpm test\n```')

    expect(within(body).getByRole('heading', { name: 'Befund' })).toBeInTheDocument()
    expect(within(body).getAllByRole('listitem').map((li) => li.textContent)).toEqual(['eins', 'zwei'])
    expect(within(body).getByText('npm test', { selector: 'pre code' })).toBeInTheDocument()
  })

  it('haelt zwei durch eine Leerzeile getrennte Absaetze getrennt', async () => {
    // Der gemeldete Fehler: <Typography> kollabierte alles zu einer Textwand. Die beiden exakten
    // Treffer je eigenem <p> gäbe es nicht mehr, wenn die Absätze zu einem Text zusammenfielen.
    const { body } = await renderComment('Erster Absatz\n\nZweiter Absatz')

    expect(within(body).getByText('Erster Absatz', { selector: 'p' })).toBeInTheDocument()
    expect(within(body).getByText('Zweiter Absatz', { selector: 'p' })).toBeInTheDocument()
  })

  it('zeigt eingebettetes HTML als Text statt als Markup', async () => {
    const { body } = await renderComment('<b>fett</b> und <img src="x" onerror="alert(1)">')

    expect(within(body).queryByText('fett', { selector: 'b' })).toBeNull()
    expect(within(body).queryByRole('img')).toBeNull()
    expect(body).toHaveTextContent('<b>fett</b> und <img src="x" onerror="alert(1)">')
  })

  it('entfernt href/src bei nicht erlaubten Schemata und laesst https durch', async () => {
    const { body } = await renderComment(
      '[boese](javascript:alert(1)) [daten](data:text/html,x) [gut](https://example.org)\n\n' +
        '![gefaehrlich](javascript:alert(1)) ![harmlos](https://example.org/b.png)',
    )

    expect(within(body).getByText('boese')).not.toHaveAttribute('href')
    expect(within(body).getByText('daten')).not.toHaveAttribute('href')
    expect(within(body).getByText('gut')).toHaveAttribute('href', 'https://example.org')
    expect(within(body).getByAltText('gefaehrlich')).not.toHaveAttribute('src')
    expect(within(body).getByAltText('harmlos')).toHaveAttribute('src', 'https://example.org/b.png')
  })

  it('rendert Task-Listen als gesperrte Checkboxen ohne Schreibpfad', async () => {
    // Kommentare haben keinen Persistenzpfad für Toggles — die Beschreibung schon (TaskMarkdown).
    const { apis, body } = await renderComment('- [ ] offen\n- [x] erledigt')

    const boxes = within(body).getAllByRole('checkbox')
    expect(boxes).toHaveLength(2)
    expect(boxes[0]).toBeDisabled()
    expect(boxes[1]).toBeDisabled()
    expect(boxes[0]).not.toBeChecked()
    expect(boxes[1]).toBeChecked()

    fireEvent.click(boxes[0])
    expect(apis.cardsApi.update).not.toHaveBeenCalled()
    expect(apis.commentsApi.update).not.toHaveBeenCalled()
  })

  it('normalisiert Task-Marker wie die Beschreibung', async () => {
    // Gleiche Schreibweise, gleiches Ergebnis in Beschreibung und Kommentar (normalizeTaskLists).
    const { body } = await renderComment('[] roh\n\n- [ X ] erledigt')

    const boxes = within(body).getAllByRole('checkbox')
    expect(boxes).toHaveLength(2)
    expect(boxes[1]).toBeChecked()
  })

  it('bricht lange Tokens um und scrollt Codebloecke und Tabellen im eigenen Bereich', async () => {
    const { body } = await renderComment(
      `${'x'.repeat(300)}\n\n\`\`\`\n${'y'.repeat(300)}\n\`\`\`\n\n| a | b |\n| - | - |\n| 1 | 2 |`,
    )

    // `pre` trägt keinen eigenen Textknoten (der Text steckt im `code`), daher der Element-Matcher.
    const pre = within(body).getByText(
      (_, el) => el?.tagName === 'PRE' && el.textContent === `${'y'.repeat(300)}\n`,
    )

    expect(getComputedStyle(body).overflowWrap).toBe('anywhere')
    expect(getComputedStyle(pre).overflowX).toBe('auto')
    expect(getComputedStyle(within(body).getByRole('table')).overflowX).toBe('auto')
  })

  it('verfasst und bearbeitet Kommentare in mehrzeiligen Feldern', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit onClose={vi.fn()} {...apis} />)
    expect(await screen.findByTestId('comment-body')).toBeInTheDocument()

    expect(screen.getByLabelText('Kommentar schreiben').tagName).toBe('TEXTAREA')
    fireEvent.click(screen.getByRole('button', { name: 'Kommentar bearbeiten' }))
    expect(screen.getByLabelText('Kommentar bearbeiten').tagName).toBe('TEXTAREA')
  })

  it('konfiguriert beide Kommentar-Felder gleich', () => {
    // Beide Felder spreaden dieselbe Konstante — sonst sehen Verfassen und Bearbeiten anders aus.
    expect(commentFieldProps).toEqual({ multiline: true, minRows: 3 })
  })
})

describe('CardDetailModal — Herkunft (#608)', () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })

  const mitHerkunft: Card = { ...card, derivedFrom: 42 }
  // Eigene Fixture mit echter Aufgabenliste: Die Standard-Beschreibung traegt eine
  // gewoehnliche Liste, dort rendert MarkdownInput keine Checkbox.
  const mitAufgabe: Card = { ...mitHerkunft, description: '- [ ] Schritt eins' }

  it('zeigt eine gesetzte Herkunft im Feld', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={mitHerkunft} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }))
    expect(screen.getByRole('textbox', { name: 'Herkunft' })).toHaveValue('42')
  })

  it('schickt die eingetragene Nummer an den eigenen Endpunkt', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Herkunft' }), '42')
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(apis.cardsApi.assignDerivedFrom).toHaveBeenCalledWith(100, 42))
  })

  it('loescht die Herkunft, wenn das Feld geleert wird', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={mitHerkunft} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }))
    await userEvent.clear(screen.getByRole('textbox', { name: 'Herkunft' }))
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(apis.cardsApi.assignDerivedFrom).toHaveBeenCalledWith(100, null))
  })

  /**
   * Der zweite, leicht uebersehene Schreibaufrufer: `toggleTask` persistiert ausserhalb des
   * Editiermodus sofort. Wuerde `cardsApi.update` die Herkunft tragen, loeschte jeder
   * Checkbox-Klick sie kommentarlos (Issue #607, Entscheidung D1a).
   */
  it('laesst die Herkunft beim Checkbox-Klick unberuehrt', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={mitAufgabe} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)

    await userEvent.click((await screen.findAllByRole('checkbox'))[0])

    await waitFor(() => expect(apis.cardsApi.update).toHaveBeenCalled())
    // Kein Argument des Update-Aufrufs traegt die Herkunft — der Vertrag kennt sie gar nicht.
    expect(apis.cardsApi.assignDerivedFrom).not.toHaveBeenCalled()
    const args = apis.cardsApi.update.mock.calls[0]
    expect(args).not.toContain(42)
  })

  it('faengt eine unparsebare Eingabe lokal ab, ohne zu schreiben', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Herkunft' }), 'abc')
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(apis.cardsApi.assignDerivedFrom).not.toHaveBeenCalled()
    expect(apis.cardsApi.update).not.toHaveBeenCalled()
    expect(screen.getByRole('textbox', { name: 'Herkunft' })).toBeInvalid()
  })

  it('zeigt die Server-Meldung am Feld und laesst die Maske offen', async () => {
    const apis = makeApis()
    apis.cardsApi.assignDerivedFrom = vi.fn().mockRejectedValue(
      new ApiError(400, 'Ablehnung', { derivedFrom: 'Unbekannte Kartennummer als Herkunft: 99999' }),
    )
    render(<CardDetailModal card={card} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Herkunft' }), '99999')
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByText('Unbekannte Kartennummer als Herkunft: 99999')).toBeInTheDocument()
    // Die Maske bleibt im Editiermodus, die Eingabe steht noch da.
    expect(screen.getByRole('textbox', { name: 'Herkunft' })).toHaveValue('99999')

    // Weitertippen raeumt die Meldung weg — sonst bliebe sie neben einer laengst geaenderten Eingabe stehen.
    await userEvent.type(screen.getByRole('textbox', { name: 'Herkunft' }), '1')
    expect(screen.queryByText('Unbekannte Kartennummer als Herkunft: 99999')).toBeNull()
  })

  it('zeigt die Herkunft ohne Schreibrecht schreibgeschuetzt an', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={mitHerkunft} canEdit={false} columnName="In Progress" onClose={vi.fn()} {...apis} />)

    expect(await screen.findByLabelText('Herkunft')).toHaveTextContent('#42')
    expect(screen.queryByRole('textbox', { name: 'Herkunft' })).toBeNull()
  })
})
