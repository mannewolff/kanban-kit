import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AttachmentsApi } from '../api/attachments'
import type { Card } from '../api/cards'
import type { CommentsApi } from '../api/comments'
import { CardDetailModal, parseDependencyInput } from './CardDetailModal'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 7, email: 'a@b.c', displayName: 'A', platformRole: 'USER', memberships: [] } }),
}))
// Editiermodus gemockt: Bestandstests laufen mit editMode=true (Bearbeiten-Button sichtbar), der
// Editiermodus-aus-Test schaltet editMode.value=false.
const editMode = vi.hoisted(() => ({ value: true }))
vi.mock('../lib/EditModeContext', () => ({
  useEditMode: () => ({ editMode: editMode.value, setEditMode: vi.fn(), toggleEditMode: vi.fn() }),
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
    restore: vi.fn().mockResolvedValue({ ...card }),
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
    editMode.value = true
    // jsdom kennt createObjectURL nicht.
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })

  it('blendet bei ausgeschaltetem Editiermodus den Bearbeiten-Button aus (trotz canEdit)', async () => {
    editMode.value = false
    const apis = makeApis()
    render(<CardDetailModal card={card} canEdit columnName="In Progress" onClose={vi.fn()} {...apis} />)
    // Lesemodus lädt Kommentare asynchron nach — abwarten, dann den fehlenden Button prüfen.
    await waitFor(() => expect(screen.getByText('Hallo')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument()
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

  it('zeigt Kind-Karten eines Epics im Lesemodus', async () => {
    const apis = makeApis()
    const childCards: Card[] = [{ ...card, id: 300, number: 6, title: 'Kind' }]
    render(<CardDetailModal card={epicCard} canEdit childCards={childCards} onClose={vi.fn()} {...apis} />)

    expect(await screen.findByText('#6 · Kind')).toBeInTheDocument()
  })

  it('zeigt einen Leer-Hinweis ohne Kind-Karten', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={epicCard} canEdit childCards={[]} onClose={vi.fn()} {...apis} />)

    expect(await screen.findByText('Keine zugeordneten Karten.')).toBeInTheDocument()
  })

  it('zeigt im Edit-Modus eines Epics nur das Kürzel-Feld', async () => {
    const apis = makeApis()
    render(<CardDetailModal card={epicCard} canEdit onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))

    expect(screen.getByLabelText('Kürzel')).toHaveValue('AUT')
    expect(screen.queryByLabelText('Epic')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Fällig am')).not.toBeInTheDocument()
  })

  it('bietet im Edit-Modus einer Nicht-Epic-Karte die Epic-Zuordnung an', async () => {
    const apis = makeApis()
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1 }]
    render(<CardDetailModal card={card} canEdit epics={epics} onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByLabelText('Epic'), { target: { value: '9' } })

    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() =>
      expect(apis.cardsApi.update).toHaveBeenCalledWith(100, 'Aufgabe', expect.any(String), [3, 4], undefined, 9, null),
    )
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
    await waitFor(() => expect(screen.getByLabelText('Vorschau bild.png')).toBeInTheDocument())
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
    await waitFor(() => expect(screen.getByLabelText('Vorschau doc.pdf')).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: /schließen/i }))
    await waitFor(() => expect(screen.queryByLabelText('Vorschau doc.pdf')).not.toBeInTheDocument())
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview')
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
    await waitFor(() => expect(screen.getByText('Hallo')).toBeInTheDocument())

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
    const boardLabels = [{ id: 5, boardId: 1, name: 'Bug', color: '#f00' }]
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
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1 }]
    const linked: Card = { ...card, parentId: 9 }
    render(<CardDetailModal card={linked} canEdit epics={epics} onClose={vi.fn()} {...apis} />)

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))

    expect(screen.getByLabelText('Epic')).toHaveValue('9')
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
    await waitFor(() => expect(screen.getByText('Hallo')).toBeInTheDocument())

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
      { id: 5, boardId: 1, name: 'Bug', color: '#f00' },
      { id: 6, boardId: 1, name: 'Ux', color: '#0f0' },
    ]
    render(
      <CardDetailModal card={{ ...card, labels: [5] }} canEdit boardLabels={boardLabels} onClose={vi.fn()} {...apis} />,
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
    await waitFor(() => expect(screen.getByText('Zweiter')).toBeInTheDocument())

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

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Titel' })).toBeInTheDocument())
    expect(onClose).not.toHaveBeenCalled()
  })
})
