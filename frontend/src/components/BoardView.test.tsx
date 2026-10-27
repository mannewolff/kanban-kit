import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState, type ComponentProps } from 'react'
import { afterEach, beforeEach, describe, expect, it, test, vi } from 'vitest'
import type { Board } from '../api/boards'
import { cardsApi } from '../api/cards'
import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'
import { ApiError } from '../api/client'
import { columnsApi } from '../api/columns'
import { boardsApi } from '../api/boards'
import { projectsApi } from '../api/projects'
import { BoardView } from './BoardView'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { SnackbarProvider } from './SnackbarProvider'
import { statusColors } from '../lib/statusColors'
import { cssRegel, cssRegelMit } from '../test/cssRegel'
import { NUT, PANEL_RADIUS } from '../theme'
import { TabletQuerProvider } from '../lib/tabletQuer'
import { setzeMedien } from '../test/setup'
import type { KitNamenZustand } from '../api/kitNamen'
import { KIT_NAMEN } from '../test/kitNamen'
import { de } from '../i18n/de'
import { en } from '../i18n/en'
import { HALTEZEIT_MS, RAND_SCROLL_MAX_PX } from '../lib/fingerZiehen'

vi.mock('../api/columns', () => ({
  columnsApi: {
    create: vi.fn(), update: vi.fn(), remove: vi.fn(), reorder: vi.fn(), sortByNumber: vi.fn(),
  },
}))
// Nur vom Transfer-Dialog zur Laufzeit genutzt; leere Listen genügen zum Öffnen, einzelne Tests
// überschreiben sie mit echten Projekten/Boards, um den Verschieben-Flow bis zum Ende zu treiben.
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('../api/boards', () => ({ boardsApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('../api/cards', () => ({ cardsApi: { bulkTransfer: vi.fn() } }))
// Editiermodus wird gemockt: Bestandstests laufen mit editMode=true (Bleistifte sichtbar); einzelne
// Tests schalten editMode.value=false, um das Ausblenden der Struktur-Affordances zu prüfen.
const editMode = vi.hoisted(() => ({ value: true }))
vi.mock('../lib/EditModeContext', () => ({
  useEditMode: () => ({ editMode: editMode.value, setEditMode: vi.fn(), toggleEditMode: vi.fn() }),
}))
// Kit-Namen nur für die Desktop-Gegenprobe geladen; alle übrigen Tests sehen das Original.
const kitNamen = vi.hoisted(() => ({ geladen: false }))
vi.mock('../api/kitNamen', async (original) => {
  const echt = await original<typeof import('../api/kitNamen')>()
  return {
    ...echt,
    useKitNamen: (): KitNamenZustand =>
      kitNamen.geladen ? { status: 'fertig', daten: KIT_NAMEN } : echt.useKitNamen(),
  }
})
// Vorschau-Zustand der „Ansicht als XY“ (Plan #1914, E15): außer im eigenen Block ohne Vorschau.
const auth = vi.hoisted(() => ({ vorschau: null as null | Record<string, string> }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ vorschau: auth.vorschau }) }))
beforeEach(() => {
  auth.vorschau = null
})
const mColumns = columnsApi as unknown as {
  create: ReturnType<typeof vi.fn>
  update: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
  reorder: ReturnType<typeof vi.fn>
  sortByNumber: ReturnType<typeof vi.fn>
}
const mProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mBoards = boardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mCards = cardsApi as unknown as { bulkTransfer: ReturnType<typeof vi.fn> }

const board: Board = {
  id: 1,
  projectId: 1,
  name: 'Board',
  createdAt: '2026-01-01T00:00:00Z',
  columns: [
    { id: 10, name: 'Backlog', position: 0, wipLimit: null },
    { id: 20, name: 'Done', position: 1, wipLimit: null },
  ],
}

const card: Card = {
  id: 100, boardId: 1, columnId: 10, number: 1, title: 'Aufgabe', description: null, excerpt: null,
  positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [],
  type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
  derivedFrom: null,
  status: null,
  canSetStatus: false,
  herkunft: null,
}

function mkApi(over: Record<string, unknown> = {}) {
  return {
    create: vi.fn(), createBatch: vi.fn(), move: vi.fn(), archive: vi.fn(), moveToIdeaStorage: vi.fn(),
    restore: vi.fn(), remove: vi.fn(), get: vi.fn().mockResolvedValue(card),
    bulkArchive: vi.fn(), bulkTransfer: vi.fn(), bulkDelete: vi.fn(),
    bulkLabels: vi.fn().mockResolvedValue([]), ...over,
  }
}

function dropOnColumn(columnId: number, cardId: number) {
  fireEvent.drop(screen.getByTestId(`column-${columnId}`), {
    dataTransfer: { getData: () => String(cardId) },
  })
}

/** Server-Ablehnung mit einer für den Nutzer formulierten Meldung in `detail` (RFC 9457). */
const serverfehler = (text: string) => new ApiError(409, 'Conflict', undefined, text)

/**
 * Prüft den Fehler-Toast: Der Text steht dort, und der Alert trägt die Severity `error`.
 *
 * `hidden: true`: Ein offener MUI-Dialog (Spalten-Dialog, Bestätigungsdialoge) stellt alles
 * außerhalb seines Portals auf `aria-hidden` — der Toast trägt seine Rolle, wird von der
 * Standardabfrage aber übergangen.
 */
async function erwarteFehlerToast(text: string) {
  expect(await screen.findByText(text)).toBeInTheDocument()
  expect(await screen.findByRole('alert', { hidden: true })).toHaveClass('MuiAlert-filledError')
}

describe('BoardView', () => {
  beforeEach(() => {
    editMode.value = true
    mProjects.list.mockResolvedValue([])
    mBoards.list.mockResolvedValue([])
    mCards.bulkTransfer.mockReset()
    mColumns.create.mockReset()
    mColumns.update.mockReset()
    mColumns.remove.mockReset()
    mColumns.reorder.mockReset()
    mColumns.sortByNumber.mockReset()
  })

  it('verschiebt die Karte optimistisch in die Zielspalte', async () => {
    const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    dropOnColumn(20, 100)

    await waitFor(() => expect(within(screen.getByTestId('column-20')).getByTestId('card-100')).toBeInTheDocument())
    expect(api.move).toHaveBeenCalledWith(100, 20, 0)
  })

  it('rollt bei einem API-Fehler auf den vorigen Stand zurück', async () => {
    const api = mkApi({ move: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    dropOnColumn(20, 100)

    await waitFor(() => expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument())
    expect(within(screen.getByTestId('column-20')).queryByTestId('card-100')).not.toBeInTheDocument()
  })

  it('legt über den Anlage-Dialog eine Karte mit Beschreibung an', async () => {
    const created: Card = { ...card, id: 200, number: 2, title: 'Neu' }
    const api = mkApi({ create: vi.fn().mockResolvedValue(created) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() =>
      expect(api.create).toHaveBeenCalledWith(1, 10, 'Neu', expect.stringContaining('## Kontext'), null, {
        dependencies: [],
        dueDate: null,
        assigneeIds: [],
        labelIds: [],
      }),
    )
    expect(within(screen.getByTestId('column-10')).getByTestId('card-200')).toBeInTheDocument()
  })

  it('blendet den Anlege-Button für Nicht-Editoren aus', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />)
    expect(screen.queryByRole('button', { name: 'Neu anlegen' })).not.toBeInTheDocument()
  })

  describe('Spezifikations-Import in der Werkzeugleiste (#1201)', () => {
    /** Markdown-Datei, wie der Nutzer sie auswählt — zwei H2-Abschnitte werden zwei Karten. */
    const specDatei = () =>
      new File(['## Anmeldung\nText A\n\n## Registrierung\nText B'], 'spec.md', {
        type: 'text/markdown',
      })

    /** Wählt eine Datei im versteckten Datei-Input des Knopfs aus; `null` = Auswahl abgebrochen. */
    function dateiSetzen(file: File | null) {
      const input = screen.getByLabelText('Markdown-Datei auswählen')
      Object.defineProperty(input, 'files', { value: file === null ? null : [file], configurable: true })
      fireEvent.change(input)
    }

    /** Wählt die Spezifikationsdatei aus und wartet auf den Anlegen-Knopf der Vorschau. */
    async function dateiWaehlen(file: File = specDatei()) {
      dateiSetzen(file)
      return screen.findByRole('button', { name: /Karten? anlegen/ })
    }

    it('zeigt den Knopf nur mit Bearbeitungsrecht', () => {
      const { unmount } = render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
      expect(screen.getByRole('button', { name: 'Spezifikation einlesen' })).toBeInTheDocument()
      unmount()

      render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />)
      expect(screen.queryByRole('button', { name: 'Spezifikation einlesen' })).not.toBeInTheDocument()
    })

    it('legt die Karten in der vorbelegten ersten Spalte an und zeigt sie sofort', async () => {
      const angelegt: Card[] = [
        { ...card, id: 300, number: 3, title: 'Anmeldung' },
        { ...card, id: 301, number: 4, title: 'Registrierung' },
      ]
      const api = mkApi({ createBatch: vi.fn().mockResolvedValue(angelegt) })
      const onCardsChanged = vi.fn()
      render(
        <BoardView board={board} initialCards={[card]} canEdit api={api} onCardsChanged={onCardsChanged} />,
        { wrapper: SnackbarProvider },
      )

      const anlegen = await dateiWaehlen()
      expect(screen.getByLabelText('Zielspalte')).toHaveValue('10')
      fireEvent.click(anlegen)

      await waitFor(() =>
        expect(api.createBatch).toHaveBeenCalledWith(1, 10, [
          { title: 'Anmeldung', description: 'Text A' },
          { title: 'Registrierung', description: 'Text B' },
        ]),
      )
      expect(await screen.findByText('2 Karten angelegt.')).toBeInTheDocument()
      expect(within(screen.getByTestId('column-10')).getByTestId('card-300')).toBeInTheDocument()
      expect(within(screen.getByTestId('column-10')).getByTestId('card-301')).toBeInTheDocument()
      expect(onCardsChanged).toHaveBeenCalled()
    })

    it('meldet eine einzelne Karte im Singular', async () => {
      const api = mkApi({ createBatch: vi.fn().mockResolvedValue([{ ...card, id: 302, number: 5 }]) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
        wrapper: SnackbarProvider,
      })

      const anlegen = await dateiWaehlen(
        new File(['## Nur eine'], 'spec.md', { type: 'text/markdown' }),
      )
      fireEvent.click(anlegen)

      expect(await screen.findByText('1 Karte angelegt.')).toBeInTheDocument()
    })

    it('legt in die gewählte Zielspalte an', async () => {
      const api = mkApi({ createBatch: vi.fn().mockResolvedValue([]) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
        wrapper: SnackbarProvider,
      })

      const anlegen = await dateiWaehlen()
      fireEvent.change(screen.getByLabelText('Zielspalte'), { target: { value: '20' } })
      fireEvent.click(anlegen)

      await waitFor(() => expect(api.createBatch).toHaveBeenCalledWith(1, 20, expect.any(Array)))
    })

    it('meldet eine unlesbare Datei, ohne die Vorschau zu öffnen', async () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })
      class FailingReader {
        onload: (() => void) | null = null
        onerror: (() => void) | null = null
        result: string | null = null
        readAsText(): void {
          this.onerror?.()
        }
      }
      vi.stubGlobal('FileReader', FailingReader)

      dateiSetzen(specDatei())

      expect(await screen.findByText('Die Datei konnte nicht gelesen werden.')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /Karten? anlegen/ })).not.toBeInTheDocument()
      vi.unstubAllGlobals()
    })

    it('tut nichts, wenn die Auswahl abgebrochen wird', () => {
      const api = mkApi({ createBatch: vi.fn() })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

      dateiSetzen(null)

      expect(screen.queryByRole('button', { name: /Karten? anlegen/ })).not.toBeInTheDocument()
      expect(api.createBatch).not.toHaveBeenCalled()
    })

    it('setzt den Datei-Input zurück, damit dieselbe Datei erneut gewählt werden kann', async () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      await dateiWaehlen()

      expect((screen.getByLabelText('Markdown-Datei auswählen') as HTMLInputElement).value).toBe('')
    })
  })

  it('legt über Typ=Epic ein Epic an statt einer Karte', async () => {
    const api = mkApi()
    const epicsApi = { create: vi.fn().mockResolvedValue({ id: 5 }), bulkAssign: vi.fn() }
    const onEpicsChanged = vi.fn()
    render(
      <BoardView board={board} initialCards={[card]} canEdit api={api}
        epicsApi={epicsApi} onEpicsChanged={onEpicsChanged} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
    fireEvent.change(screen.getByLabelText('Typ'), { target: { value: 'EPIC' } })
    fireEvent.change(screen.getByLabelText('Kürzel'), { target: { value: 'AUT' } })
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Auth' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(epicsApi.create).toHaveBeenCalledWith(1, 'Auth', expect.any(String), 'AUT'))
    expect(api.create).not.toHaveBeenCalled()
    expect(onEpicsChanged).toHaveBeenCalled()
  })

  it('zeigt ein Epic-Badge auf zugeordneten Karten', () => {
    const assigned: Card = { ...card, parentId: 9 }
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [1], rootNumbers: [1], requirementCardNumber: null }]
    render(<BoardView board={board} initialCards={[assigned]} canEdit epics={epics} api={mkApi()} />)
    expect(screen.getByText('AUT')).toBeInTheDocument()
  })

  it('zeigt ein Epic-Badge auch auf einer über die Herkunft zugeordneten Karte', () => {
    // parentId bleibt null: Die Zugehörigkeit steht allein in memberNumbers, wo der Server beide
    // Wege zusammenführt (Issue #684). Genau diese Karte trug auf dem Board bisher kein Kürzel.
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [card.number], rootNumbers: [], requirementCardNumber: null }]
    render(<BoardView board={board} initialCards={[card]} canEdit epics={epics} api={mkApi()} />)
    expect(screen.getByText('AUT')).toBeInTheDocument()
  })

  it('meldet über onEpicOpen das Vorhaben des angeklickten Kürzels, ohne die Karte zu öffnen', () => {
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [card.number], rootNumbers: [], requirementCardNumber: null }]
    const onEpicOpen = vi.fn()
    const onCardClick = vi.fn()
    render(
      <BoardView board={board} initialCards={[card]} canEdit epics={epics} api={mkApi()}
        onEpicOpen={onEpicOpen} onCardClick={onCardClick} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Vorhaben AUT öffnen' }))

    expect(onEpicOpen).toHaveBeenCalledWith(epics[0])
    // Der Badge stoppt das Bubbling — sonst öffneten sich Vorhaben und Karte zugleich.
    expect(onCardClick).not.toHaveBeenCalled()
  })

  it('archiviert und verschiebt über das ⋮-Menü', async () => {
    const api = mkApi({ archive: vi.fn().mockResolvedValue({}), move: vi.fn().mockResolvedValue({}) })
    const onCardsChanged = vi.fn()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} onCardsChanged={onCardsChanged} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Archivieren' }))
    await waitFor(() => expect(api.archive).toHaveBeenCalledWith(100))
    expect(onCardsChanged).toHaveBeenCalled()

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Nach rechts verschieben' }))
    await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 20, 0))
  })

  it('verschiebt eine Karte über das ⋮-Menü nach Bestätigung in den Papierkorb', async () => {
    const api = mkApi({ bulkDelete: vi.fn().mockResolvedValue(undefined) })
    const onCardsChanged = vi.fn()
    // Zweite Karte in derselben Spalte: sie darf vom optimistischen Filtern unberührt bleiben.
    const other: Card = { ...card, id: 101, number: 2, title: 'Andere' }
    render(
      <BoardView board={board} initialCards={[card, other]} canEdit api={api} onCardsChanged={onCardsChanged} />,
    )

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Löschen' }))

    // Erst die Bestätigung löst den Aufruf aus — das Menü allein löscht nichts.
    expect(api.bulkDelete).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'In den Papierkorb' }),
    )

    await waitFor(() => expect(api.bulkDelete).toHaveBeenCalledWith([100]))
    await waitFor(() => expect(screen.queryByTestId('card-100')).not.toBeInTheDocument())
    expect(screen.getByTestId('card-101')).toBeInTheDocument()
    expect(onCardsChanged).toHaveBeenCalled()
  })

  it('löscht nichts, wenn der Papierkorb-Dialog aus dem ⋮-Menü abgebrochen wird', () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Löschen' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Abbrechen' }))

    expect(api.bulkDelete).not.toHaveBeenCalled()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('rollt beim Fehler des Einzel-Papierkorbs zurück und meldet ihn', async () => {
    const api = mkApi({ bulkDelete: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Löschen' }))
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'In den Papierkorb' }),
    )

    expect(await screen.findByText('In den Papierkorb verschieben fehlgeschlagen.')).toBeInTheDocument()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('dupliziert eine Karte über das ⋮-Menü vorbefüllt, aber immer nach Backlog (erste Spalte)', async () => {
    // Quelle bewusst NICHT in der ersten Spalte (Backlog=10), sondern in Done=20 — die Kopie ist
    // ein neues Item und muss den kompletten Prozess durchlaufen, unabhängig davon, wo die
    // Quellkarte gerade steht.
    const source: Card = { ...card, columnId: 20, title: 'Original', description: 'Original-Text', parentId: 9 }
    const created: Card = { ...card, id: 300, number: 3, columnId: 10, title: 'Original' }
    // Die Listen-Antwort trägt die Beschreibung nicht mehr; der Volltext kommt aus dem Einzelabruf.
    const api = mkApi({
      create: vi.fn().mockResolvedValue(created),
      get: vi.fn().mockResolvedValue({ ...source, description: 'Volltext aus get' }),
    })
    render(<BoardView board={board} initialCards={[source]} canEdit api={api} />)

    fireEvent.click(screen.getByLabelText('Menü Original'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Duplizieren' }))

    expect(await screen.findByRole('heading', { name: 'Neue Karte in „Backlog“' })).toBeInTheDocument()
    expect(api.get).toHaveBeenCalledWith(100)
    expect(screen.getByLabelText('Titel')).toHaveValue('Original')
    expect(screen.getByLabelText('Markdown-Beschreibung')).toHaveValue('Volltext aus get')

    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() =>
      expect(api.create).toHaveBeenCalledWith(1, 10, 'Original', 'Volltext aus get', 9, {
        dependencies: [],
        dueDate: null,
        assigneeIds: [],
        labelIds: [],
      }),
    )
    // Quellkarte bleibt unverändert in ihrer Spalte (Done) erhalten.
    expect(within(screen.getByTestId('column-20')).getByTestId('card-100')).toBeInTheDocument()
  })

  it('legt beim Abbrechen des Duplizieren-Dialogs keine neue Karte an', async () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Duplizieren' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }))

    expect(api.create).not.toHaveBeenCalled()
  })

  it('öffnet den Duplizieren-Dialog nicht, wenn die Karte nicht geladen werden kann', async () => {
    const api = mkApi({ get: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Duplizieren' }))

    await screen.findByText('Karte konnte nicht geladen werden.')
    expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
    expect(api.create).not.toHaveBeenCalled()
  })

  it('zeigt den Archiv-Countdown auf Done-Karten', () => {
    const doneCard: Card = { ...card, columnId: 20, movedToDoneAt: new Date().toISOString() }
    render(<BoardView board={board} initialCards={[doneCard]} canEdit retentionDays={5} api={mkApi()} />)
    expect(screen.getByText(/wird in 5 Tagen archiviert/)).toBeInTheDocument()
  })

  it('zeigt keinen Archiv-Countdown, wenn die Aufbewahrung 0 ist (kein Auto-Archiv)', () => {
    const doneCard: Card = { ...card, columnId: 20, movedToDoneAt: new Date().toISOString() }
    render(<BoardView board={board} initialCards={[doneCard]} canEdit retentionDays={0} api={mkApi()} />)
    expect(screen.queryByText(/archiviert/)).not.toBeInTheDocument()
  })

  describe('Status auf der Kachel (#1303)', () => {
    const mitAnstehend: Board = {
      ...board,
      columns: [...board.columns, { id: 30, name: 'Anstehend', position: 2, wipLimit: null }],
    }
    const gestern = new Date(Date.now() - 86_400_000).toISOString()

    it('zeigt das Statusmal im Kartenfuß, wenn der Status nicht zur Spalte passt', () => {
      const paket: Card = { ...card, columnId: 30, status: 'DONE' }
      render(<BoardView board={mitAnstehend} initialCards={[paket]} canEdit api={mkApi()} />)

      const mal = within(screen.getByTestId('card-100')).getByLabelText('Status Done')
      expect(mal).toHaveTextContent('Done')
      expect(mal).toHaveAttribute('data-status', 'DONE')
    })

    it('zeigt kein Statusmal, wenn der Status zur Spalte passt', () => {
      const paket: Card = { ...card, columnId: 20, status: 'DONE' }
      render(<BoardView board={mitAnstehend} initialCards={[paket]} canEdit api={mkApi()} />)

      expect(within(screen.getByTestId('card-100')).queryByLabelText(/^Status /)).not.toBeInTheDocument()
    })

    it('zeigt kein Statusmal an einer Karte ohne eigenen Status', () => {
      const dokument: Card = { ...card, columnId: 30, status: null }
      render(<BoardView board={mitAnstehend} initialCards={[dokument]} canEdit api={mkApi()} />)

      expect(within(screen.getByTestId('card-100')).queryByLabelText(/^Status /)).not.toBeInTheDocument()
    })

    it('stellt das Statusmal vor die übrigen Schilder des Fußes', () => {
      const paket: Card = { ...card, columnId: 30, status: 'IN_REVIEW', dueDate: gestern }
      render(<BoardView board={mitAnstehend} initialCards={[paket]} canEdit api={mkApi()} />)

      const schilder = within(screen.getByTestId('card-100')).getAllByLabelText(/^(Status|Fällig) /)
      expect(schilder.map((e) => e.getAttribute('aria-label'))).toEqual(['Status In review', 'Fällig Aufgabe'])
    })

    it('zählt ein fertiges Paket in einer eigenen Spalte nicht als überfällig', () => {
      const fertig: Card = { ...card, columnId: 30, status: 'DONE', dueDate: gestern }
      render(<BoardView board={mitAnstehend} initialCards={[fertig]} canEdit api={mkApi()} />)

      expect(screen.getByRole('button', { name: 'Überfällig' })).toHaveTextContent(/^Überfällig$/)
      expect(screen.getByLabelText('Fällig Aufgabe')).not.toHaveAttribute('data-ueberfaellig')
    })

    it('zählt ein nicht fertiges Paket in der Done-Spalte als überfällig', () => {
      const offen: Card = { ...card, columnId: 20, status: 'READY', dueDate: gestern }
      render(<BoardView board={mitAnstehend} initialCards={[offen]} canEdit api={mkApi()} />)

      expect(screen.getByRole('button', { name: /^Überfällig/ })).toHaveTextContent('Überfällig1')
      expect(screen.getByLabelText('Fällig Aufgabe')).toHaveAttribute('data-ueberfaellig', 'ja')
    })

    it('zeigt den Archiv-Countdown an einem fertigen Paket außerhalb der Done-Spalte', () => {
      const fertig: Card = { ...card, columnId: 30, status: 'DONE', movedToDoneAt: new Date().toISOString() }
      render(<BoardView board={mitAnstehend} initialCards={[fertig]} canEdit retentionDays={5} api={mkApi()} />)

      expect(screen.getByText(/wird in 5 Tagen archiviert/)).toBeInTheDocument()
    })

    it('zeigt keinen Archiv-Countdown an einem offenen Paket in der Done-Spalte', () => {
      const offen: Card = { ...card, columnId: 20, status: 'IN_PROGRESS', movedToDoneAt: new Date().toISOString() }
      render(<BoardView board={mitAnstehend} initialCards={[offen]} canEdit retentionDays={5} api={mkApi()} />)

      expect(screen.queryByText(/archiviert/)).not.toBeInTheDocument()
    })
  })

  it('filtert das Board nach Epic', () => {
    // Die Zugehörigkeit steht in memberNumbers — seit Task #1103 rechnet auch die Filter-Achse darüber.
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [1], rootNumbers: [1], requirementCardNumber: null }]
    const inEpic: Card = { ...card, id: 100, parentId: 9 }
    const other: Card = { ...card, id: 200, number: 2, parentId: null }
    render(<BoardView board={board} initialCards={[inEpic, other]} canEdit epics={epics} api={mkApi()} />)

    expect(screen.getByTestId('card-100')).toBeInTheDocument()
    expect(screen.getByTestId('card-200')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
    expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
  })

  // Der Wähler der Werkzeugleiste trägt seine Benennung im Wert, nicht in einer Beschriftung darüber
  // (Entwurf `.waehler`, Z. 833–842; Issue #986). Der zugängliche Name bleibt, sonst hätte das Feld
  // ohne sichtbare Beschriftung gar keinen.
  it('benennt den Vorhaben-Filter im Wert statt über einer Beschriftung', () => {
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    render(<BoardView board={board} initialCards={[card]} canEdit epics={epics} api={mkApi()} />)

    expect(screen.queryByText('Vorhaben-Filter')).not.toBeInTheDocument()
    const filter = screen.getByLabelText('Vorhaben-Filter')
    expect(within(filter).getByRole('option', { name: 'Vorhaben: alle' })).toBeInTheDocument()
    expect(within(filter).getByRole('option', { name: 'Vorhaben: AUT – Auth' })).toBeInTheDocument()
  })

  it('legt eine neue Spalte an (mit canEdit)', async () => {
    mColumns.create.mockResolvedValue({ id: 30, name: 'Neu', position: 2, wipLimit: null })
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Spalte' }))
    fireEvent.change(screen.getByLabelText('Spaltenname'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(mColumns.create).toHaveBeenCalledWith(1, 'Neu', null))
    expect(await screen.findByText('Neu')).toBeInTheDocument()
  })

  it('bearbeitet Name und WIP-Limit einer Spalte', async () => {
    mColumns.update.mockResolvedValue({ id: 10, name: 'Todo', position: 0, wipLimit: 3 })
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog bearbeiten'))
    fireEvent.change(screen.getByLabelText('Spaltenname'), { target: { value: 'Todo' } })
    fireEvent.change(screen.getByLabelText('WIP-Limit'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(mColumns.update).toHaveBeenCalledWith(10, 'Todo', 3))
  })

  it('blendet Spalten-Bearbeitung ohne canEdit aus', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />)
    expect(screen.queryByRole('button', { name: 'Spalte' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Spalte Backlog bearbeiten')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Spalte Backlog löschen')).not.toBeInTheDocument()
  })

  it('löscht eine leere Spalte nach Bestätigung', async () => {
    mColumns.remove.mockResolvedValue(undefined)
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Done löschen'))
    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))

    await waitFor(() => expect(mColumns.remove).toHaveBeenCalledWith(20))
    await waitFor(() => expect(screen.queryByText('Done')).not.toBeInTheDocument())
  })

  it('zeigt einen Fehler, wenn die Spalte noch Karten enthält (409)', async () => {
    mColumns.remove.mockRejectedValue(new ApiError(409, 'nicht leer'))
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog löschen'))
    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))

    expect(
      await screen.findByText('Spalte enthält noch Karten und kann nicht gelöscht werden.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Backlog')).toBeInTheDocument()
  })

  it('zeigt eine generische Fehlermeldung bei einem anderen Löschfehler', async () => {
    mColumns.remove.mockRejectedValue(new Error('boom'))
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog löschen'))
    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))

    expect(await screen.findByText('Löschen fehlgeschlagen.')).toBeInTheDocument()
  })

  it('ordnet Spalten per Drag & Drop neu und persistiert die Reihenfolge', async () => {
    mColumns.reorder.mockResolvedValue([
      { id: 20, name: 'Done', position: 0, wipLimit: null },
      { id: 10, name: 'Backlog', position: 1, wipLimit: null },
    ])
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.dragStart(screen.getByTestId('column-header-20'))
    // Während des Ziehens über eine ANDERE Spalte hinweg (dragOver) und am Ende dragEnd —
    // vollständige Drag-Sequenz statt nur des isolierten drop.
    fireEvent.dragOver(screen.getByTestId('column-header-10'))
    fireEvent.drop(screen.getByTestId('column-header-10'))
    fireEvent.dragEnd(screen.getByTestId('column-header-10'))

    await waitFor(() => expect(mColumns.reorder).toHaveBeenCalledWith(1, [20, 10]))
  })

  it('ignoriert dragOver über der eigenen Spalte beim Spalten-Reorder', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    fireEvent.dragStart(screen.getByTestId('column-header-20'))
    // dragOver über der Spalte, von der aus gezogen wird: kein preventDefault/Fehler.
    fireEvent.dragOver(screen.getByTestId('column-header-20'))
    expect(screen.getByTestId('column-header-20')).toBeInTheDocument()
  })

  it('reordert nicht, wenn eine Spalte auf sich selbst fallengelassen wird', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    fireEvent.dragStart(screen.getByTestId('column-header-20'))
    fireEvent.drop(screen.getByTestId('column-header-20'))
    expect(mColumns.reorder).not.toHaveBeenCalled()
  })

  it('stellt die Spalten-Reihenfolge bei einem Fehler wieder her', async () => {
    mColumns.reorder.mockRejectedValue(new Error('kaputt'))
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.dragStart(screen.getByTestId('column-header-20'))
    fireEvent.drop(screen.getByTestId('column-header-10'))

    await waitFor(() => expect(mColumns.reorder).toHaveBeenCalled())
    // Nach dem Rollback steht Backlog wieder vor Done.
    const headers = screen.getAllByTestId(/^column-header-/)
    expect(headers[0]).toHaveAttribute('data-testid', 'column-header-10')
    expect(headers[1]).toHaveAttribute('data-testid', 'column-header-20')
    // Der Rollback allein bliebe stumm: die zurückspringende Reihenfolge sagt nicht, dass etwas
    // schiefging. Deshalb zusätzlich die Meldung (Issue #810).
    await erwarteFehlerToast('Spalten umsortieren fehlgeschlagen.')
  })

  it('macht Spalten ohne canEdit nicht draggable', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />)
    expect(screen.getByTestId('column-header-10')).not.toHaveAttribute('draggable', 'true')
  })

  it('zeigt den Verschieben-Menüeintrag nur mit canTransfer', () => {
    const { unmount } = render(
      <BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />,
    )
    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    expect(screen.queryByText('Verschieben…')).not.toBeInTheDocument()
    unmount()

    render(<BoardView board={board} initialCards={[card]} canEdit canTransfer api={mkApi()} />)
    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    expect(screen.getByText('Verschieben…')).toBeInTheDocument()
  })

  it('zeigt farbige Label-Chips auf der Karte', () => {
    const labelled: Card = { ...card, labels: [5] }
    const boardLabels = [{ id: 5, boardId: 1, name: 'Bug', color: '#f00', countOnEpicTile: false }]
    render(
      <BoardView board={board} initialCards={[labelled]} canEdit boardLabels={boardLabels} api={mkApi()} />,
    )

    expect(screen.getByLabelText('Labels Aufgabe')).toBeInTheDocument()
    expect(screen.getByText('Bug')).toBeInTheDocument()
  })

  it('zeigt Zuständigen-Avatare mit Initialen (zwei Wörter, ein Wort, leerer Name, Fallback)', () => {
    // Deckt alle initials()-Zweige ab: 'Max Mustermann' -> MM (zwei Wörter), 'Cher' -> C (ein Wort),
    // leerer Anzeigename -> '?' (kein Wort) und ein Assignee ohne Mitglied -> '#8' -> '#'.
    const assigned: Card = { ...card, assignees: [5, 6, 7, 8] }
    const members = [
      { userId: 5, email: 'a@x.de', displayName: 'Max Mustermann', role: 'MEMBER' as const },
      { userId: 6, email: 'b@x.de', displayName: 'Cher', role: 'MEMBER' as const },
      { userId: 7, email: 'c@x.de', displayName: '', role: 'MEMBER' as const },
    ]
    render(
      <BoardView board={board} initialCards={[assigned]} canEdit members={members} api={mkApi()} />,
    )

    const group = screen.getByLabelText('Zuständige Aufgabe')
    expect(group).toBeInTheDocument()
    expect(within(group).getByText('MM')).toBeInTheDocument()
    expect(within(group).getByText('C')).toBeInTheDocument()
    expect(within(group).getByText('?')).toBeInTheDocument()
    expect(within(group).getByText('#')).toBeInTheDocument()
  })

  // Seit #980 (Leitstand-Entwurf) trägt die Spalte ihren Status als Melder-LED im Kopf; Spalte und
  // Karte haben keine farbige Kante mehr. jsdom verwirft Kurzschreibweisen mit `var(…)`, geprüft
  // wird deshalb die erzeugte Regel samt Variablenname.
  it('trägt den Status der Spalte als LED im Spaltenkopf', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    expect(cssRegel(screen.getByTestId('status-10'))).toContain('background-color: var(--mb-palette-status-backlog-dot')
    expect(cssRegel(screen.getByTestId('status-20'))).toContain('background-color: var(--mb-palette-status-done-dot')
    expect(within(screen.getByTestId('column-header-10')).getByTestId('status-10')).toBeInTheDocument()
    expect(statusColors('Backlog').dot).toBe('var(--mb-palette-status-backlog-dot)')
  })

  it('legt Spalten als Nut und Karten als Platte an, ohne farbige Kanten', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    const spalte = cssRegel(screen.getByTestId('column-10'))
    expect(spalte).toContain('box-shadow: var(--mb-palette-warte-schattenNute')
    expect(spalte).not.toContain('border-top:')
    const karte = cssRegel(screen.getByTestId('card-100'))
    expect(karte).toContain('box-shadow: var(--mb-palette-panel-cardShadow')
    expect(karte).not.toContain('border-left:')
    expect(karte).not.toContain('border-top:')
  })

  // Der getönte Grund liegt seit #713 an der Anwendung (theme.ts, `body::before`) und nicht mehr
  // am Board. Trüge die Board-Fläche weiterhin einen eigenen Verlauf, stünde ein Verlauf auf einem
  // Verlauf; der eigene Radius rundete dann eine Fläche, die es als Fläche nicht mehr gibt.
  it('trägt keinen eigenen Verlauf und keinen eigenen Panel-Radius mehr', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    const surface = screen.getByTestId('board-surface')
    expect(getComputedStyle(surface).backgroundImage).not.toContain('gradient')
    // 14px ist PANEL_RADIUS — der gehört den Spalten, nicht der Fläche, auf der sie stehen.
    expect(surface).not.toHaveStyle({ borderRadius: `${PANEL_RADIUS}px` })
  })

  it('färbt den Label-Chip-Text nach Kontrast, auf hellem Label also nicht weiß', () => {
    const labelled: Card = { ...card, labels: [5, 6] }
    const boardLabels = [
      { id: 5, boardId: 1, name: 'Hell', color: '#FFF59D', countOnEpicTile: false },
      { id: 6, boardId: 1, name: 'Dunkel', color: '#1E5F68', countOnEpicTile: false },
    ]
    render(
      <BoardView board={board} initialCards={[labelled]} canEdit boardLabels={boardLabels} api={mkApi()} />,
    )

    // Der Chip-Rumpf traegt die Textfarbe, nicht das Label-Element darin: gesucht wird deshalb
    // ueber den Textinhalt des Rumpfes statt ueber einen Aufstieg im DOM.
    const chip = (name: string) =>
      screen.getByText((_content, element) =>
        element?.classList.contains('MuiChip-root') === true && element.textContent === name)

    // Erwartete Werte ausgeschrieben statt `getContrastText` gespiegelt: sonst prueft der Test
    // denselben Mechanismus, den er absichern soll, und bliebe auch bei falscher Wahl gruen.
    expect(chip('Hell')).toHaveStyle({ color: 'rgba(0, 0, 0, 0.87)' })
    expect(chip('Dunkel')).toHaveStyle({ color: 'rgb(255, 255, 255)' })
  })

  it('rendert weiter, wenn eine Labelfarbe keine CSS-Farbe ist', () => {
    // Die Labelfarbe ist serverseitig nur laengenbegrenzt, das Domain-Modell erlaubt ausdruecklich
    // auch Theme-Token. `getContrastText` wirft darauf, und es gibt keine ErrorBoundary — ohne
    // Fangnetz nimmt ein einziges solches Label den ganzen Board-Baum mit.
    const labelled: Card = { ...card, labels: [7, 8] }
    const boardLabels = [
      { id: 7, boardId: 1, name: 'Pfad', color: 'primary.main', countOnEpicTile: false },
      { id: 8, boardId: 1, name: 'Wort', color: 'red', countOnEpicTile: false },
    ]

    expect(() =>
      render(
        <BoardView board={board} initialCards={[labelled]} canEdit boardLabels={boardLabels} api={mkApi()} />,
      ),
    ).not.toThrow()
    expect(screen.getByText('Pfad')).toBeInTheDocument()
    expect(screen.getByText('Wort')).toBeInTheDocument()
  })

  it('zeigt für ein unbekanntes Label die Id als grauen Fallback-Chip', () => {
    // labelId 999 hat kein Board-Label -> Chip zeigt „#999" und die graue Fallback-Farbe.
    const labelled: Card = { ...card, labels: [999] }
    render(<BoardView board={board} initialCards={[labelled]} canEdit boardLabels={[]} api={mkApi()} />)

    expect(screen.getByLabelText('Labels Aufgabe')).toBeInTheDocument()
    expect(screen.getByText('#999')).toBeInTheDocument()
  })

  it('blendet im Auswahlmodus Checkboxen ein und selektiert per Klick', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    // Vor dem Auswahlmodus keine Checkbox.
    expect(screen.queryByLabelText('Karte Aufgabe auswählen')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    expect(screen.getByLabelText('Karte Aufgabe auswählen')).toBeInTheDocument()

    fireEvent.click(screen.getByTestId('card-100'))
    expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()

    // Erneuter Klick hebt die Auswahl auf -> Aktionsleiste verschwindet.
    fireEvent.click(screen.getByTestId('card-100'))
    expect(screen.queryByText('1 ausgewählt')).not.toBeInTheDocument()
  })

  it('öffnet im Auswahlmodus nicht das Detail beim Klick auf die Karte', () => {
    const onCardClick = vi.fn()
    render(
      <BoardView board={board} initialCards={[card]} canEdit api={mkApi()} onCardClick={onCardClick} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))

    expect(onCardClick).not.toHaveBeenCalled()
  })

  describe('Kartentitel als Knopf (Issue #1305)', () => {
    /** Der Titelknopf der Karte — sein Name ist der sichtbare Titel, kein `aria-label` (Plan #1292, E5). */
    const titelKnopf = () => within(screen.getByTestId('card-100')).getByRole('button', { name: 'Aufgabe' })

    /** Tabt vom Dokumentanfang, bis der Fokus auf `ziel` steht; scheitert, wenn der Weg es nie erreicht. */
    async function tabBis(user: ReturnType<typeof userEvent.setup>, ziel: HTMLElement) {
      for (let i = 0; i < 50 && !ziel.matches(':focus'); i++) await user.tab()
      expect(ziel).toHaveFocus()
    }

    it('erreicht den Titel per Tab vor dem ⋮ und öffnet das Detail mit Enter und Leertaste', async () => {
      const user = userEvent.setup()
      const onCardClick = vi.fn()
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} onCardClick={onCardClick} />)

      await tabBis(user, titelKnopf())
      await user.keyboard('{Enter}')
      expect(onCardClick).toHaveBeenCalledTimes(1)
      await user.keyboard(' ')
      expect(onCardClick).toHaveBeenCalledTimes(2)
      expect(onCardClick).toHaveBeenLastCalledWith(expect.objectContaining({ id: 100 }))

      // Die Haupthandlung zuerst: Der nächste Tab-Schritt führt auf das ⋮ derselben Karte (E7).
      await user.tab()
      expect(screen.getByLabelText('Menü Aufgabe')).toHaveFocus()
    })

    it('öffnet ohne Schreibrecht genauso über den Titelknopf', async () => {
      const user = userEvent.setup()
      const onCardClick = vi.fn()
      render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} onCardClick={onCardClick} />)

      await tabBis(user, titelKnopf())
      await user.keyboard('{Enter}')

      expect(onCardClick).toHaveBeenCalledTimes(1)
    })

    it('öffnet mit Enter auf dem ⋮ das Menü, nicht das Detail', async () => {
      const user = userEvent.setup()
      const onCardClick = vi.fn()
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} onCardClick={onCardClick} />)

      await tabBis(user, screen.getByLabelText('Menü Aufgabe'))
      await user.keyboard('{Enter}')

      expect(await screen.findByRole('menuitem', { name: 'Archivieren' })).toBeInTheDocument()
      expect(onCardClick).not.toHaveBeenCalled()
    })

    it('öffnet beim Mausklick auf den Titel das Detail genau einmal', async () => {
      const user = userEvent.setup()
      const onCardClick = vi.fn()
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} onCardClick={onCardClick} />)

      await user.click(titelKnopf())

      expect(onCardClick).toHaveBeenCalledTimes(1)
    })

    it('ist im Auswahlmodus keine Tab-Station und wählt per Mausklick aus', async () => {
      const user = userEvent.setup()
      const onCardClick = vi.fn()
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} onCardClick={onCardClick} />)
      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))

      expect(titelKnopf()).toHaveAttribute('tabindex', '-1')
      // Der Tastaturweg ist die Checkbox: Nach ihr kommt der Titel nicht als nächste Station.
      await tabBis(user, screen.getByLabelText('Karte Aufgabe auswählen'))
      await user.tab()
      expect(titelKnopf()).not.toHaveFocus()

      await user.click(titelKnopf())
      expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()
      expect(onCardClick).not.toHaveBeenCalled()
    })
  })

  it('archiviert die Auswahl nach Bestätigung über die Bulk-API und entfernt sie optimistisch', async () => {
    const api = mkApi({ bulkArchive: vi.fn().mockResolvedValue([]) })
    const onCardsChanged = vi.fn()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} onCardsChanged={onCardsChanged} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))

    // Bestätigungsdialog erscheint; erst dessen Bestätigung löst die API aus.
    expect(api.bulkArchive).not.toHaveBeenCalled()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

    await waitFor(() => expect(api.bulkArchive).toHaveBeenCalledWith([100]))
    await waitFor(() => expect(screen.queryByTestId('card-100')).not.toBeInTheDocument())
    // Erfolgspfad benachrichtigt das Elternteil (onCardsChanged?.()-Aufrufzweig).
    expect(onCardsChanged).toHaveBeenCalled()
  })

  it('archiviert nicht, wenn die Bestätigung abgebrochen wird', () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Abbrechen' }))

    expect(api.bulkArchive).not.toHaveBeenCalled()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('rollt beim Fehler des Bulk-Archivierens zurück und meldet ihn', async () => {
    const api = mkApi({ bulkArchive: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

    expect(await screen.findByText('Archivieren fehlgeschlagen.')).toBeInTheDocument()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('verschiebt die Auswahl nach Bestätigung in den Papierkorb', async () => {
    const api = mkApi({ bulkDelete: vi.fn().mockResolvedValue(undefined) })
    const onCardsChanged = vi.fn()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} onCardsChanged={onCardsChanged} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))

    expect(api.bulkDelete).not.toHaveBeenCalled()
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'In den Papierkorb' }),
    )

    await waitFor(() => expect(api.bulkDelete).toHaveBeenCalledWith([100]))
    await waitFor(() => expect(screen.queryByTestId('card-100')).not.toBeInTheDocument())
    // Erfolgspfad benachrichtigt das Elternteil (onCardsChanged?.()-Aufrufzweig).
    expect(onCardsChanged).toHaveBeenCalled()
  })

  it('rollt beim Fehler des Bulk-Papierkorbs zurück und meldet ihn', async () => {
    const api = mkApi({ bulkDelete: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'In den Papierkorb' }),
    )

    expect(await screen.findByText('In den Papierkorb verschieben fehlgeschlagen.')).toBeInTheDocument()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('öffnet mit Transfer-Recht den Verschieben-Dialog für die Auswahl', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    expect(screen.getByText('Karte verschieben')).toBeInTheDocument()
  })

  it('leert die Auswahl beim Abbrechen', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

    expect(screen.queryByLabelText('Karte Aufgabe auswählen')).not.toBeInTheDocument()
    expect(screen.queryByText('1 ausgewählt')).not.toBeInTheDocument()
  })

  describe('Alle Karten einer Spalte auswählen', () => {
    const zweiteImBacklog: Card = { ...card, id: 101, number: 2, title: 'Zweite', positionInColumn: 1 }
    const inDone: Card = { ...card, id: 200, number: 3, title: 'Fertig', columnId: 20, positionInColumn: 0 }
    const alleWaehlen = 'Alle Karten in Backlog auswählen'
    const auswahlAufheben = 'Auswahl in Backlog aufheben'

    const starteAuswahl = () => fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))

    it('zeigt das Kästchen im Spaltenkopf nur im Auswahlmodus', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

      expect(within(screen.getByTestId('column-header-10')).queryByRole('checkbox')).not.toBeInTheDocument()

      starteAuswahl()
      expect(within(screen.getByTestId('column-header-10')).getByRole('checkbox', { name: alleWaehlen }))
        .toBeInTheDocument()
    })

    it('wählt mit einem Klick alle angezeigten Karten der Spalte', () => {
      render(<BoardView board={board} initialCards={[card, zweiteImBacklog, inDone]} canEdit api={mkApi()} />)

      starteAuswahl()
      fireEvent.click(screen.getByRole('checkbox', { name: alleWaehlen }))

      // Nur die beiden Karten des Backlogs — die Karte in Done bleibt ungewählt.
      expect(screen.getByText('2 ausgewählt')).toBeInTheDocument()
      expect(screen.getByRole('checkbox', { name: auswahlAufheben })).toBeChecked()
    })

    it('hebt beim zweiten Klick nur die Auswahl dieser Spalte auf', () => {
      render(<BoardView board={board} initialCards={[card, zweiteImBacklog, inDone]} canEdit api={mkApi()} />)

      starteAuswahl()
      fireEvent.click(screen.getByTestId('card-200')) // Karte in Done einzeln gewählt
      fireEvent.click(screen.getByRole('checkbox', { name: alleWaehlen }))
      expect(screen.getByText('3 ausgewählt')).toBeInTheDocument()

      fireEvent.click(screen.getByRole('checkbox', { name: auswahlAufheben }))

      // Die Karte der anderen Spalte bleibt gewählt.
      expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()
    })

    it('zeigt bei teilweiser Auswahl „einige" und ergänzt beim Klick die fehlenden', () => {
      render(<BoardView board={board} initialCards={[card, zweiteImBacklog]} canEdit api={mkApi()} />)

      starteAuswahl()
      fireEvent.click(screen.getByTestId('card-100'))

      const kaestchen = screen.getByRole('checkbox', { name: alleWaehlen })
      expect(kaestchen).toHaveAttribute('data-indeterminate', 'true')

      fireEvent.click(kaestchen)
      expect(screen.getByText('2 ausgewählt')).toBeInTheDocument()
    })

    it('lässt eine verdeckte Karte der Spalte ungewählt', () => {
      const epic = {
        id: 9, number: 9, title: 'Auth', description: null, shortcode: 'AUT',
        done: 0, total: 1, memberNumbers: [2], rootNumbers: [2], requirementCardNumber: null,
      }
      const verdeckt: Card = { ...zweiteImBacklog, title: 'Verdeckt', parentId: 9 }
      render(
        <BoardView board={board} initialCards={[card, verdeckt]} canEdit epics={[epic]}
          hiddenEpics={new Set([9])} api={mkApi()} />,
      )

      starteAuswahl()
      fireEvent.click(screen.getByRole('checkbox', { name: alleWaehlen }))

      expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()
      // „Alle" meint die angezeigten Karten: mit der einen sichtbaren ist die Spalte voll gewählt.
      expect(screen.getByRole('checkbox', { name: auswahlAufheben })).toBeChecked()
    })

    it('sperrt das Kästchen einer leeren Spalte', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

      starteAuswahl()
      expect(screen.getByRole('checkbox', { name: 'Alle Karten in Done auswählen' })).toBeDisabled()
    })
  })

  describe('Labels über die Mehrfachauswahl', () => {
    const bug = { id: 7, boardId: 1, name: 'Bug', color: 'red', countOnEpicTile: false }
    const nacht = { id: 9, boardId: 1, name: 'Nacht', color: 'blue', countOnEpicTile: false }
    const zweite: Card = { ...card, id: 101, number: 2, title: 'Zweite', positionInColumn: 1 }

    /** Auswahlmodus starten, beide Karten anhaken und das Label-Menü öffnen. */
    function waehleBeideUndOeffneLabels() {
      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      fireEvent.click(screen.getByTestId('card-100'))
      fireEvent.click(screen.getByTestId('card-101'))
      fireEvent.click(screen.getByRole('button', { name: 'Labels' }))
    }

    it('fügt ein Label, das keine gewählte Karte trägt, der ganzen Auswahl hinzu', async () => {
      // Die dritte Karte ist nicht gewählt und steht nicht in der Antwort — sie bleibt unberührt.
      const dritte: Card = {
        ...card, id: 102, number: 3, title: 'Dritte', positionInColumn: 2, labels: [7],
      }
      const api = mkApi({
        bulkLabels: vi.fn().mockResolvedValue([
          { ...card, labels: [7, 9] },
          { ...zweite, labels: [9] },
        ]),
      })
      const onCardsChanged = vi.fn()
      render(
        <BoardView
          board={board}
          initialCards={[{ ...card, labels: [7] }, zweite, dritte]}
          canEdit
          boardLabels={[bug, nacht]}
          api={api}
          onCardsChanged={onCardsChanged}
        />,
      )

      waehleBeideUndOeffneLabels()
      // Nur die erste Karte trägt „Bug" -> „einige"; „Nacht" trägt keine.
      expect(screen.getByRole('menuitem', { name: 'Bug — einige gewählte Karten' })).toBeInTheDocument()
      fireEvent.click(screen.getByRole('menuitem', { name: 'Nacht — keine gewählte Karte' }))

      await waitFor(() => expect(api.bulkLabels).toHaveBeenCalledWith([100, 101], 9, 'ADD'))
      // Die Antwort ist übernommen: „Nacht" steht jetzt an allen, „Bug" weiter nur an einer.
      expect(
        await screen.findByRole('menuitem', { name: 'Nacht — alle gewählten Karten' }),
      ).toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: 'Bug — einige gewählte Karten' })).toBeInTheDocument()
      // Die ungewählte dritte Karte behält ihr Label, obwohl sie nicht in der Antwort stand.
      expect(within(screen.getByTestId('card-102')).getByText('Bug')).toBeInTheDocument()
      expect(onCardsChanged).toHaveBeenCalled()
    })

    it('nimmt ein Label ab, das alle gewählten Karten tragen', async () => {
      const api = mkApi({
        bulkLabels: vi.fn().mockResolvedValue([
          { ...card, labels: [] },
          { ...zweite, labels: [] },
        ]),
      })
      render(
        <BoardView
          board={board}
          initialCards={[{ ...card, labels: [7] }, { ...zweite, labels: [7] }]}
          canEdit
          boardLabels={[bug]}
          api={api}
        />,
      )

      waehleBeideUndOeffneLabels()
      fireEvent.click(screen.getByRole('menuitem', { name: 'Bug — alle gewählten Karten' }))

      await waitFor(() => expect(api.bulkLabels).toHaveBeenCalledWith([100, 101], 7, 'REMOVE'))
      expect(
        await screen.findByRole('menuitem', { name: 'Bug — keine gewählte Karte' }),
      ).toBeInTheDocument()
    })

    it('meldet einen Fehler und lässt die Auswahl bestehen', async () => {
      const api = mkApi({ bulkLabels: vi.fn().mockRejectedValue(new Error('fail')) })
      render(
        <BoardView
          board={board}
          initialCards={[card, zweite]}
          canEdit
          boardLabels={[nacht]}
          api={api}
        />,
        { wrapper: SnackbarProvider },
      )

      waehleBeideUndOeffneLabels()
      fireEvent.click(screen.getByRole('menuitem', { name: 'Nacht — keine gewählte Karte' }))

      await erwarteFehlerToast('Labels setzen fehlgeschlagen.')
      expect(screen.getByText('2 ausgewählt')).toBeInTheDocument()
    })

    it('schickt während der laufenden Anfrage keinen zweiten Batch', async () => {
      let antwort: (cards: Card[]) => void = () => {}
      const api = mkApi({
        bulkLabels: vi.fn(() => new Promise<Card[]>((resolve) => { antwort = resolve })),
      })
      render(
        <BoardView
          board={board}
          initialCards={[card, zweite]}
          canEdit
          boardLabels={[nacht]}
          api={api}
        />,
      )

      waehleBeideUndOeffneLabels()
      const eintrag = screen.getByRole('menuitem', { name: 'Nacht — keine gewählte Karte' })
      fireEvent.click(eintrag)
      fireEvent.click(eintrag)

      expect(api.bulkLabels).toHaveBeenCalledTimes(1)

      antwort([{ ...card, labels: [9] }, { ...zweite, labels: [9] }])
      expect(
        await screen.findByRole('menuitem', { name: 'Nacht — alle gewählten Karten' }),
      ).toBeInTheDocument()
    })

    it('sperrt „Labels" mit Hinweis, wenn das Board keine Labels hat', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit boardLabels={[]} api={mkApi()} />)

      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      fireEvent.click(screen.getByTestId('card-100'))

      expect(
        screen.getByRole('button', { name: 'Labels — Das Board hat keine Labels' }),
      ).toBeDisabled()
    })

    it('sperrt „Labels" mit Hinweis, wenn die Auswahl ein Vorhaben enthält', () => {
      // Die Board-Liste liefert heute keine Vorhaben aus (sie halten keine Spaltenposition); die
      // gesperrte Taste ist der Gurt zum Hosenträger des Servers, der einen solchen Batch ablehnt.
      const vorhaben: Card = { ...card, id: 102, number: 3, title: 'Vorhaben', type: 'EPIC' }
      render(
        <BoardView
          board={board}
          initialCards={[card, vorhaben]}
          canEdit
          boardLabels={[nacht]}
          api={mkApi()}
        />,
      )

      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      fireEvent.click(screen.getByTestId('card-102'))

      expect(screen.getByRole('button', { name: 'Labels — Vorhaben tragen keine Labels' })).toBeDisabled()
    })
  })

  it('zeigt das Fälligkeitsdatum-Badge, hervorgehoben bei überfälligen Karten', () => {
    const future = new Date(Date.now() + 86_400_000).toISOString()
    const past = new Date(Date.now() - 86_400_000).toISOString()
    const dueSoon: Card = { ...card, id: 100, title: 'Bald fällig', dueDate: future }
    const overdue: Card = { ...card, id: 200, number: 2, title: 'Überfällig', dueDate: past }
    render(<BoardView board={board} initialCards={[dueSoon, overdue]} canEdit api={mkApi()} />)

    expect(screen.getByLabelText('Fällig Bald fällig')).toBeInTheDocument()
    expect(screen.getByLabelText('Fällig Überfällig')).toBeInTheDocument()
    expect(screen.getAllByText(/^fällig /)).toHaveLength(2)
    expect(screen.getByLabelText('Fällig Überfällig')).toHaveAttribute('data-ueberfaellig', 'ja')
    expect(screen.getByLabelText('Fällig Bald fällig')).not.toHaveAttribute('data-ueberfaellig')
  })

  it('rollt eine fehlgeschlagene Kartenverschiebung zurück', async () => {
    const api = mkApi({ move: vi.fn().mockRejectedValue(new Error('fail')) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
      wrapper: SnackbarProvider,
    })

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Nach rechts verschieben' }))

    await waitFor(() => expect(api.move).toHaveBeenCalled())
    expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
    expect(within(screen.getByTestId('column-20')).queryByTestId('card-100')).not.toBeInTheDocument()
    // Die zurückspringende Karte allein erklärt nichts — die Meldung benennt den Fehlschlag (#810).
    await erwarteFehlerToast('Verschieben fehlgeschlagen.')
  })

  it('verschiebt eine einzelne Karte über das Menü auf ein anderes Board', async () => {
    const onCardsChanged = vi.fn()
    mProjects.list.mockResolvedValue([{ id: 2, name: 'Anderes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 2, name: 'Zielboard', createdAt: '',
        columns: [{ id: 900, name: 'Backlog', position: 0, wipLimit: null }] },
    ])
    mCards.bulkTransfer.mockResolvedValue([{ ...card, boardId: 99, columnId: 900 }])
    render(
      <BoardView board={board} initialCards={[card]} canEdit canTransfer api={mkApi()}
        onCardsChanged={onCardsChanged} />,
    )

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Verschieben…' }))

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '2' } })
    fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '99' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '900' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await waitFor(() => expect(mCards.bulkTransfer).toHaveBeenCalledWith([100], 99, 900))
    await waitFor(() => expect(screen.queryByTestId('card-100')).not.toBeInTheDocument())
    expect(onCardsChanged).toHaveBeenCalled()
  })

  it('verschiebt die Auswahl über den Bulk-Transfer-Dialog und entfernt sie aus der Ansicht', async () => {
    const onCardsChanged = vi.fn()
    mProjects.list.mockResolvedValue([{ id: 2, name: 'Anderes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 2, name: 'Zielboard', createdAt: '',
        columns: [{ id: 900, name: 'Backlog', position: 0, wipLimit: null }] },
    ])
    mCards.bulkTransfer.mockResolvedValue([{ ...card, boardId: 99, columnId: 900 }])
    render(
      <BoardView board={board} initialCards={[card]} canEdit canTransfer api={mkApi()}
        onCardsChanged={onCardsChanged} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '2' } })
    fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '99' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '900' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await waitFor(() => expect(mCards.bulkTransfer).toHaveBeenCalledWith([100], 99, 900))
    await waitFor(() => expect(screen.queryByTestId('card-100')).not.toBeInTheDocument())
    expect(screen.queryByText('1 ausgewählt')).not.toBeInTheDocument()
    expect(onCardsChanged).toHaveBeenCalled()
  })

  it('verschiebt die Auswahl in eine andere Spalte desselben Boards und zeigt sie dort', async () => {
    // #1043: Auf dem eigenen Board verlassen die Karten die Ansicht nicht — sie wechseln die
    // Spalte. Die Reihenfolge folgt der Sicht, also landet 301 vor 302 am Ende von Done.
    const onCardsChanged = vi.fn()
    mProjects.list.mockResolvedValue([{ id: 1, name: 'Eigenes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 1, projectId: 1, name: 'Board', createdAt: '', columns: [
        { id: 10, name: 'Backlog', position: 0, wipLimit: null },
        { id: 20, name: 'Done', position: 1, wipLimit: null },
      ] },
    ])
    const cards: Card[] = [
      { ...card, id: 301, columnId: 10, positionInColumn: 0, title: 'Eins' },
      { ...card, id: 302, columnId: 10, positionInColumn: 1, title: 'Zwei' },
    ]
    mCards.bulkTransfer.mockResolvedValue([
      { ...cards[0], columnId: 20 },
      { ...cards[1], columnId: 20 },
    ])
    render(
      <BoardView board={board} initialCards={cards} canEdit canTransfer api={mkApi()}
        onCardsChanged={onCardsChanged} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-301'))
    fireEvent.click(screen.getByTestId('card-302'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    // Das eigene Board steht vorausgewählt, die Quellspalte ist vorbelegt.
    await waitFor(() => expect(screen.getByLabelText('Zielboard')).toHaveValue('1'))
    expect(screen.getByLabelText('Zielspalte')).toHaveValue('10')
    fireEvent.change(screen.getByLabelText('Zielspalte'), { target: { value: '20' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await waitFor(() => expect(mCards.bulkTransfer).toHaveBeenCalledWith([301, 302], 1, 20))
    await waitFor(() =>
      expect(within(screen.getByTestId('column-20')).getByTestId('card-301')).toBeInTheDocument(),
    )
    expect(within(screen.getByTestId('column-20')).getByTestId('card-302')).toBeInTheDocument()
    expect(within(screen.getByTestId('column-10')).queryByTestId('card-301')).not.toBeInTheDocument()
    expect(screen.queryByText('2 ausgewählt')).not.toBeInTheDocument()
    expect(onCardsChanged).toHaveBeenCalled()
  })

  it('belegt im Verschieben-Dialog Projekt und Zielspalte der Einzelkarte vor', async () => {
    mProjects.list.mockResolvedValue([{ id: 1, name: 'Eigenes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 1, name: 'Ziel', createdAt: '', columns: [
        { id: 900, name: 'Backlog', position: 0, wipLimit: null },
        { id: 901, name: 'Fertig', position: 1, wipLimit: null },
      ] },
    ])
    // Karte liegt in der zweiten Spalte (Position 1) des Quellboards.
    const inDone: Card = { ...card, id: 105, columnId: 20, title: 'Fertige Aufgabe' }
    render(<BoardView board={board} initialCards={[inDone]} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Menü Fertige Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Verschieben…' }))

    await screen.findByRole('option', { name: 'Ziel' })
    expect(screen.getByLabelText('Zielprojekt')).toHaveValue('1')
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '99' } })

    expect(screen.getByLabelText('Zielspalte')).toHaveValue('901')
  })

  it('belegt die Zielspalte einer einspaltigen Bulk-Auswahl vor, bei mehrspaltiger nicht', async () => {
    mProjects.list.mockResolvedValue([{ id: 1, name: 'Eigenes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 1, name: 'Ziel', createdAt: '', columns: [
        { id: 900, name: 'Backlog', position: 0, wipLimit: null },
        { id: 901, name: 'Fertig', position: 1, wipLimit: null },
      ] },
    ])
    const cards: Card[] = [
      { ...card, id: 301, columnId: 10, positionInColumn: 0, title: 'Links oben' },
      { ...card, id: 302, columnId: 10, positionInColumn: 1, title: 'Links unten' },
      { ...card, id: 303, columnId: 20, positionInColumn: 0, title: 'Rechts' },
    ]
    render(<BoardView board={board} initialCards={cards} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-301'))
    fireEvent.click(screen.getByTestId('card-302'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await screen.findByRole('option', { name: 'Ziel' })
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '99' } })
    expect(screen.getByLabelText('Zielspalte')).toHaveValue('900')

    // Dritte Karte aus der anderen Spalte dazunehmen: die Quellspalte ist nicht mehr eindeutig.
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    fireEvent.click(screen.getByTestId('card-303'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await screen.findByRole('option', { name: 'Ziel' })
    fireEvent.change(screen.getByLabelText('Zielboard'), { target: { value: '99' } })
    expect(screen.getByLabelText('Zielspalte')).toHaveValue('')
  })

  it('übergibt die Auswahl in Sichtreihenfolge, nicht in Klick-Reihenfolge', async () => {
    const cards: Card[] = [
      { ...card, id: 101, positionInColumn: 0, title: 'Eins' },
      { ...card, id: 102, positionInColumn: 1, title: 'Zwei' },
      { ...card, id: 103, positionInColumn: 2, title: 'Drei' },
    ]
    mProjects.list.mockResolvedValue([{ id: 2, name: 'Anderes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 2, name: 'Zielboard', createdAt: '',
        columns: [{ id: 900, name: 'Backlog', position: 0, wipLimit: null }] },
    ])
    mCards.bulkTransfer.mockResolvedValue([])
    render(<BoardView board={board} initialCards={cards} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    // Von unten nach oben anhaken — genau der Fall, der ohne Sortierung verdreht im Ziel landet.
    fireEvent.click(screen.getByTestId('card-103'))
    fireEvent.click(screen.getByTestId('card-102'))
    fireEvent.click(screen.getByTestId('card-101'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '2' } })
    fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '99' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '900' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await waitFor(() => expect(mCards.bulkTransfer).toHaveBeenCalledWith([101, 102, 103], 99, 900))
  })

  it('sortiert eine spaltenübergreifende Auswahl zuerst nach Spaltenposition', async () => {
    const cards: Card[] = [
      { ...card, id: 201, columnId: 20, positionInColumn: 0, title: 'Rechts oben' },
      { ...card, id: 202, columnId: 10, positionInColumn: 5, title: 'Links unten' },
    ]
    mProjects.list.mockResolvedValue([{ id: 2, name: 'Anderes Projekt', role: 'OWNER', createdAt: '' }])
    mBoards.list.mockResolvedValue([
      { id: 99, projectId: 2, name: 'Zielboard', createdAt: '',
        columns: [{ id: 900, name: 'Backlog', position: 0, wipLimit: null }] },
    ])
    mCards.bulkTransfer.mockResolvedValue([])
    render(<BoardView board={board} initialCards={cards} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    // Die Karte der hinteren Spalte zuerst anhaken: die Spaltenposition muss die kleinere
    // Position-in-Spalte der anderen Karte schlagen.
    fireEvent.click(screen.getByTestId('card-201'))
    fireEvent.click(screen.getByTestId('card-202'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '2' } })
    fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '99' } })
    fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '900' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

    await waitFor(() => expect(mCards.bulkTransfer).toHaveBeenCalledWith([202, 201], 99, 900))
  })

  it('lässt Archivieren und Papierkorb bei der Klick-Reihenfolge — dort ist sie bedeutungslos', async () => {
    const cards: Card[] = [
      { ...card, id: 101, positionInColumn: 0, title: 'Eins' },
      { ...card, id: 102, positionInColumn: 1, title: 'Zwei' },
    ]
    const api = mkApi({ bulkArchive: vi.fn().mockResolvedValue([]) })
    render(<BoardView board={board} initialCards={cards} canEdit api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-102'))
    fireEvent.click(screen.getByTestId('card-101'))
    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

    await waitFor(() => expect(api.bulkArchive).toHaveBeenCalledWith([102, 101]))
  })

  it('öffnet den Anlage-Dialog für die erste Spalte über „Neu anlegen"', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
    expect(screen.getByRole('heading', { name: 'Neue Karte in „Backlog“' })).toBeInTheDocument()
  })

  it('wählt eine Karte per Klick auf die Checkbox selbst aus', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByLabelText('Karte Aufgabe auswählen'))
    expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()
  })

  it('setzt beim Ziehen einer Karte die dataTransfer-Nutzlast und reagiert auf dragOver der Zielspalte', async () => {
    const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    const setData = vi.fn()
    fireEvent.dragStart(screen.getByTestId('card-100'), { dataTransfer: { setData } })
    fireEvent.dragOver(screen.getByTestId('column-20'), { dataTransfer: {} })
    fireEvent.drop(screen.getByTestId('column-20'), { dataTransfer: { getData: () => '100' } })

    expect(setData).toHaveBeenCalledWith('text/plain', '100')
    await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 20, 0))
  })

  describe('Ziehen einer Karte (AK 7, AK 8, #956)', () => {
    const ziehen = async () => {
      fireEvent.dragStart(screen.getByTestId('card-100'), { dataTransfer: { setData: vi.fn() } })
      // Der Zustand kommt einen Takt nach dem Ziehbeginn: Der Browser nimmt das Ziehbild erst nach
      // dem Ereignis auf und zeigte sonst schon den Platzhalter statt der Karte.
      await waitFor(() => expect(screen.getByTestId('card-100')).toHaveAttribute('data-zieh-zustand', 'bewegt'))
    }

    it('kennzeichnet die gezogene Karte als bewegt und lässt sie als Platzhalter an ihrer Stelle', async () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

      await ziehen()

      const quelle = screen.getByTestId('card-100')
      expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBe(quelle)
      // Platzhalter derselben Höhe: dasselbe Element, Inhalt unsichtbar statt entfernt.
      expect(cssRegelMit(quelle, '>*')).toContain('visibility: hidden')
      expect(cssRegel(quelle)).toContain('border: 1px dashed')
    })

    it('zeigt die Ablagefläche in der Zielspalte, nicht in der Herkunftsspalte', async () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
      await ziehen()

      fireEvent.dragOver(screen.getByTestId('column-20'), { dataTransfer: {} })

      expect(screen.getByTestId('ablage-20')).toHaveAttribute('data-ablage', 'aktiv')
      expect(cssRegel(screen.getByTestId('ablage-20'))).toContain('outline: 2px dashed')
      fireEvent.dragOver(screen.getByTestId('column-10'), { dataTransfer: {} })
      expect(screen.getByTestId('ablage-10')).not.toHaveAttribute('data-ablage')
      expect(screen.getByTestId('ablage-20')).not.toHaveAttribute('data-ablage')
    })

    it('räumt Kennzeichnung und Ablagefläche nach dem Ablegen weg', async () => {
      const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)
      await ziehen()
      fireEvent.dragOver(screen.getByTestId('column-20'), { dataTransfer: {} })

      fireEvent.drop(screen.getByTestId('column-20'), { dataTransfer: { getData: () => '100' } })

      await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 20, 0))
      expect(screen.getByTestId('card-100')).not.toHaveAttribute('data-zieh-zustand')
      expect(screen.getByTestId('ablage-20')).not.toHaveAttribute('data-ablage')
    })

    it('räumt Kennzeichnung und Ablagefläche nach einem Abbruch weg', async () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
      await ziehen()
      fireEvent.dragOver(screen.getByTestId('column-20'), { dataTransfer: {} })

      fireEvent.dragEnd(screen.getByTestId('card-100'))

      expect(screen.getByTestId('card-100')).not.toHaveAttribute('data-zieh-zustand')
      expect(screen.getByTestId('ablage-20')).not.toHaveAttribute('data-ablage')
    })

    it('setzt keinen Ziehzustand, wenn das Ziehen endet, bevor er greift', async () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

      fireEvent.dragStart(screen.getByTestId('card-100'), { dataTransfer: { setData: vi.fn() } })
      fireEvent.dragEnd(screen.getByTestId('card-100'))

      await new Promise((fertig) => setTimeout(fertig, 5))
      expect(screen.getByTestId('card-100')).not.toHaveAttribute('data-zieh-zustand')
    })

    it('zeigt ohne Ziehvorgang keine Ablagefläche', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

      fireEvent.dragOver(screen.getByTestId('column-20'), { dataTransfer: {} })

      expect(screen.getByTestId('ablage-20')).not.toHaveAttribute('data-ablage')
    })
  })

  describe('Ausgewählte Karten gemeinsam ziehen (#1324)', () => {
    const backlog: Card[] = [
      { ...card, id: 101, number: 1, positionInColumn: 0, title: 'Eins', assignees: [7] },
      { ...card, id: 102, number: 2, positionInColumn: 1, title: 'Zwei', assignees: [7] },
      { ...card, id: 103, number: 3, positionInColumn: 2, title: 'Drei', assignees: [8] },
    ]
    const rechts: Card = { ...card, id: 201, number: 4, columnId: 20, positionInColumn: 0, title: 'Rechts' }

    const auswaehlen = (...ids: number[]) => {
      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      ids.forEach((id) => fireEvent.click(screen.getByTestId(`card-${id}`)))
    }
    const ziehenAuf = (cardId: number, columnId: number) => {
      fireEvent.dragStart(screen.getByTestId(`card-${cardId}`), { dataTransfer: { setData: vi.fn() } })
      fireEvent.dragOver(screen.getByTestId(`column-${columnId}`), { dataTransfer: {} })
      dropOnColumn(columnId, cardId)
    }
    const kartenIn = (columnId: number) =>
      within(screen.getByTestId(`column-${columnId}`)).queryAllByTestId(/^card-/).map((el) => el.dataset.testid)

    it('verschiebt drei ausgewählte Karten mit einem Sammelaufruf in Sichtreihenfolge', async () => {
      const onCardsChanged = vi.fn()
      const api = mkApi({ bulkTransfer: vi.fn().mockResolvedValue([]) })
      render(<BoardView board={board} initialCards={backlog} canEdit api={api} onCardsChanged={onCardsChanged} />)
      // Von unten nach oben anhaken: gezogen wird trotzdem in Sichtreihenfolge.
      auswaehlen(103, 101, 102)

      ziehenAuf(102, 20)

      await waitFor(() => expect(api.bulkTransfer).toHaveBeenCalledTimes(1))
      expect(api.bulkTransfer).toHaveBeenCalledWith([101, 102, 103], 1, 20)
      expect(api.move).not.toHaveBeenCalled()
      expect(kartenIn(20)).toEqual(['card-101', 'card-102', 'card-103'])
      expect(kartenIn(10)).toEqual([])
      expect(screen.getByRole('button', { name: 'Auswählen' })).toBeInTheDocument()
      expect(screen.queryByText('3 ausgewählt')).not.toBeInTheDocument()
      await waitFor(() => expect(onCardsChanged).toHaveBeenCalled())
    })

    it('lässt Karten, die schon in der Zielspalte liegen, an ihrem Platz', async () => {
      const api = mkApi({ bulkTransfer: vi.fn().mockResolvedValue([]) })
      render(<BoardView board={board} initialCards={[...backlog, rechts]} canEdit api={api} />)
      auswaehlen(101, 201, 103)

      ziehenAuf(201, 20)

      await waitFor(() => expect(api.bulkTransfer).toHaveBeenCalledWith([101, 103], 1, 20))
      expect(kartenIn(20)).toEqual(['card-201', 'card-101', 'card-103'])
    })

    it('tut nichts, wenn alle gezogenen Karten schon in der Zielspalte liegen', () => {
      const api = mkApi({ bulkTransfer: vi.fn().mockResolvedValue([]) })
      render(<BoardView board={board} initialCards={backlog} canEdit api={api} />)
      auswaehlen(101, 102)

      ziehenAuf(101, 10)

      expect(api.bulkTransfer).not.toHaveBeenCalled()
      expect(screen.getByText('2 ausgewählt')).toBeInTheDocument()
    })

    it('rollt bei einem Fehlschlag zurück und meldet ihn', async () => {
      const api = mkApi({ bulkTransfer: vi.fn().mockRejectedValue(serverfehler('Keine Berechtigung.')) })
      render(
        <SnackbarProvider>
          <BoardView board={board} initialCards={backlog} canEdit api={api} />
        </SnackbarProvider>,
      )
      auswaehlen(101, 102, 103)

      ziehenAuf(101, 20)

      await erwarteFehlerToast('Keine Berechtigung.')
      expect(kartenIn(10)).toEqual(['card-101', 'card-102', 'card-103'])
      expect(kartenIn(20)).toEqual([])
    })

    it('zieht nur die wirksame Auswahl — eine vom Filter verdeckte Karte bleibt', async () => {
      const api = mkApi({ bulkTransfer: vi.fn().mockResolvedValue([]) })
      render(<BoardView board={board} initialCards={backlog} canEdit currentUserId={7} api={api} />)
      auswaehlen(101, 102, 103)
      fireEvent.click(screen.getByRole('button', { name: 'Meine' }))

      ziehenAuf(101, 20)

      await waitFor(() => expect(api.bulkTransfer).toHaveBeenCalledWith([101, 102], 1, 20))
    })

    it('zieht im Auswahlmodus auch eine einzelne ausgewählte Karte über den Sammelaufruf', async () => {
      const api = mkApi({ bulkTransfer: vi.fn().mockResolvedValue([]) })
      render(<BoardView board={board} initialCards={backlog} canEdit api={api} />)
      auswaehlen(102)

      ziehenAuf(102, 20)

      await waitFor(() => expect(api.bulkTransfer).toHaveBeenCalledWith([102], 1, 20))
      expect(api.move).not.toHaveBeenCalled()
    })

    it('macht im Auswahlmodus nur ausgewählte Karten ziehbar', () => {
      render(<BoardView board={board} initialCards={backlog} canEdit api={mkApi()} />)
      auswaehlen(101)

      expect(screen.getByTestId('card-101')).toHaveAttribute('draggable', 'true')
      expect(screen.getByTestId('card-102')).toHaveAttribute('draggable', 'false')
    })

    it('verschiebt außerhalb des Auswahlmodus weiter nur die eine Karte per move', async () => {
      const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
      render(<BoardView board={board} initialCards={backlog} canEdit api={api} />)

      ziehenAuf(102, 20)

      await waitFor(() => expect(api.move).toHaveBeenCalledWith(102, 20, 0))
      expect(api.bulkTransfer).not.toHaveBeenCalled()
      expect(kartenIn(20)).toEqual(['card-102'])
    })

    it('kennzeichnet alle wandernden Karten als bewegt', async () => {
      render(<BoardView board={board} initialCards={backlog} canEdit api={mkApi()} />)
      auswaehlen(101, 103)

      fireEvent.dragStart(screen.getByTestId('card-101'), { dataTransfer: { setData: vi.fn() } })

      await waitFor(() => expect(screen.getByTestId('card-103')).toHaveAttribute('data-zieh-zustand', 'bewegt'))
      expect(screen.getByTestId('card-101')).toHaveAttribute('data-zieh-zustand', 'bewegt')
      expect(screen.getByTestId('card-102')).not.toHaveAttribute('data-zieh-zustand')
    })

    it('bietet bei einer Herkunftsspalte nur die anderen Spalten als Ablage an', async () => {
      render(<BoardView board={board} initialCards={backlog} canEdit api={mkApi()} />)
      auswaehlen(101, 102)
      fireEvent.dragStart(screen.getByTestId('card-101'), { dataTransfer: { setData: vi.fn() } })
      await waitFor(() => expect(screen.getByTestId('card-102')).toHaveAttribute('data-zieh-zustand', 'bewegt'))

      fireEvent.dragOver(screen.getByTestId('column-10'), { dataTransfer: {} })
      expect(screen.getByTestId('ablage-10')).not.toHaveAttribute('data-ablage')
      fireEvent.dragOver(screen.getByTestId('column-20'), { dataTransfer: {} })
      expect(screen.getByTestId('ablage-20')).toHaveAttribute('data-ablage', 'aktiv')
    })

    it('bietet bei mehreren Herkunftsspalten jede davon als Ablage an', async () => {
      render(<BoardView board={board} initialCards={[...backlog, rechts]} canEdit api={mkApi()} />)
      auswaehlen(101, 201)
      fireEvent.dragStart(screen.getByTestId('card-101'), { dataTransfer: { setData: vi.fn() } })
      await waitFor(() => expect(screen.getByTestId('card-201')).toHaveAttribute('data-zieh-zustand', 'bewegt'))

      fireEvent.dragOver(screen.getByTestId('column-10'), { dataTransfer: {} })
      expect(screen.getByTestId('ablage-10')).toHaveAttribute('data-ablage', 'aktiv')
      fireEvent.dragOver(screen.getByTestId('column-20'), { dataTransfer: {} })
      expect(screen.getByTestId('ablage-20')).toHaveAttribute('data-ablage', 'aktiv')
    })
  })

  describe('Werkzeugleiste (#980)', () => {
    const heute = Date.now()
    const eigene: Card = { ...card, id: 100, title: 'Meine Karte', assignees: [7] }
    const fremde: Card = { ...card, id: 101, number: 2, title: 'Fremde Karte', assignees: [8], positionInColumn: 1, dueDate: new Date(heute - 86_400_000).toISOString() }
    const fertig: Card = { ...card, id: 102, number: 3, title: 'Fertige Karte', columnId: 20, dueDate: new Date(heute - 86_400_000).toISOString() }

    it('filtert auf die Karten des angemeldeten Nutzers und zurück', () => {
      render(<BoardView board={board} initialCards={[eigene, fremde]} canEdit currentUserId={7} api={mkApi()} />)

      fireEvent.click(screen.getByRole('button', { name: 'Meine' }))

      expect(screen.getByRole('button', { name: 'Meine' })).toHaveAttribute('aria-pressed', 'true')
      expect(screen.getByText('Meine Karte')).toBeInTheDocument()
      expect(screen.queryByText('Fremde Karte')).not.toBeInTheDocument()
      fireEvent.click(screen.getByRole('button', { name: 'Alle Karten' }))
      expect(screen.getByText('Fremde Karte')).toBeInTheDocument()
    })

    it('öffnet eine Karte über den Klick-Handler der Seite', () => {
      const onCardClick = vi.fn()
      render(<BoardView board={board} initialCards={[eigene]} canEdit onCardClick={onCardClick} api={mkApi()} />)

      fireEvent.click(screen.getByText('Meine Karte'))

      expect(onCardClick).toHaveBeenCalledWith(expect.objectContaining({ id: 100 }))
    })

    it('öffnet ohne Klick-Handler beim Klick auf eine Karte nichts und wirft nicht', () => {
      render(<BoardView board={board} initialCards={[eigene]} canEdit api={mkApi()} />)

      fireEvent.click(screen.getByTestId('card-100'))

      expect(screen.getByTestId('card-100')).toBeInTheDocument()
    })

    it('bietet „Meine" ohne angemeldeten Nutzer nicht an', () => {
      render(<BoardView board={board} initialCards={[eigene]} canEdit api={mkApi()} />)

      expect(screen.queryByRole('button', { name: 'Meine' })).not.toBeInTheDocument()
    })

    it('zählt und filtert überfällige Karten, fertige zählen nicht', () => {
      render(<BoardView board={board} initialCards={[eigene, fremde, fertig]} canEdit currentUserId={7} api={mkApi()} />)

      const knopf = screen.getByRole('button', { name: /^Überfällig/ })
      expect(knopf).toHaveTextContent('Überfällig1')
      fireEvent.click(knopf)

      expect(screen.getByText('Fremde Karte')).toBeInTheDocument()
      expect(screen.queryByText('Meine Karte')).not.toBeInTheDocument()
      expect(screen.queryByText('Fertige Karte')).not.toBeInTheDocument()
    })

    it('zeigt ohne überfällige Karte keine Zahl am Filter', () => {
      render(<BoardView board={board} initialCards={[eigene]} canEdit api={mkApi()} />)

      expect(screen.getByRole('button', { name: 'Überfällig' })).toHaveTextContent(/^Überfällig$/)
    })

    it('blendet in der kompakten Dichte Zuständige und Labels aus', () => {
      const mitLabel: Card = { ...eigene, labels: [5] }
      const boardLabels = [{ id: 5, boardId: 1, name: 'Wichtig', color: '#C8393E', countOnEpicTile: false }]
      render(<BoardView board={board} initialCards={[mitLabel]} canEdit boardLabels={boardLabels} api={mkApi()} />)
      expect(screen.getByLabelText('Zuständige Meine Karte')).toBeInTheDocument()

      fireEvent.click(screen.getByRole('button', { name: 'kompakt' }))

      expect(screen.getByTestId('card-100')).toHaveAttribute('data-dichte', 'kompakt')
      expect(screen.queryByLabelText('Zuständige Meine Karte')).not.toBeInTheDocument()
      expect(screen.queryByText('Wichtig')).not.toBeInTheDocument()
    })

    it('zeigt die Werkzeugleiste auch ohne Bearbeitungsrecht, dann ohne Auswählen und Anlegen', () => {
      render(<BoardView board={board} initialCards={[eigene]} canEdit={false} api={mkApi()} />)

      expect(screen.getByRole('group', { name: 'Karten filtern' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Auswählen' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Neu anlegen' })).not.toBeInTheDocument()
    })
  })

  describe('Belastungsgrenze einer Spalte (AK 6, #956)', () => {
    const mitGrenze = (wipLimit: number | null): Board => ({
      ...board,
      columns: [
        { id: 10, name: 'Backlog', position: 0, wipLimit },
        { id: 20, name: 'Done', position: 1, wipLimit: null },
      ],
    })

    it('trägt bei erreichter Grenze den Grenz-Zustand, ohne dass man die Zahl lesen muss', () => {
      render(<BoardView board={mitGrenze(1)} initialCards={[card]} canEdit api={mkApi()} />)

      const balken = screen.getByRole('meter', { name: 'Auslastung Backlog' })
      expect(balken).toHaveAttribute('data-grenze', 'erreicht')
      expect(balken).toHaveAttribute('aria-valuenow', '1')
      expect(balken).toHaveAttribute('aria-valuemax', '1')
      expect(balken).toHaveAttribute('aria-valuetext', '1 von 1')
      // Der Text bleibt daneben stehen.
      expect(within(screen.getByTestId('column-header-10')).getByText('1/1')).toBeInTheDocument()
    })

    it('trägt unterhalb der Grenze keinen Grenz-Zustand', () => {
      render(<BoardView board={mitGrenze(3)} initialCards={[card]} canEdit api={mkApi()} />)

      expect(screen.getByRole('meter', { name: 'Auslastung Backlog' })).toHaveAttribute('data-grenze', 'offen')
    })

    it('meldet eine überschrittene Grenze ebenfalls als erreicht und kappt den Wert', () => {
      const zweite = { ...card, id: 101, number: 2, title: 'Zweite', positionInColumn: 1 }
      render(<BoardView board={mitGrenze(1)} initialCards={[card, zweite]} canEdit api={mkApi()} />)

      const balken = screen.getByRole('meter', { name: 'Auslastung Backlog' })
      expect(balken).toHaveAttribute('data-grenze', 'erreicht')
      expect(balken).toHaveAttribute('aria-valuenow', '1')
      expect(balken).toHaveAttribute('aria-valuetext', '2 von 1')
    })

    it('zeigt ohne Grenze keinen Balken', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

      expect(screen.queryByRole('meter')).not.toBeInTheDocument()
    })

    it('zeigt die Grenze als Segmentskala: belegte Plätze, bei erreichter Grenze das letzte als Grenzsegment', () => {
      const zweite = { ...card, id: 101, number: 2, title: 'Zweite', positionInColumn: 1 }
      const { unmount } = render(<BoardView board={mitGrenze(4)} initialCards={[card, zweite]} canEdit api={mkApi()} />)
      const segmente = () =>
        within(screen.getByRole('meter', { name: 'Auslastung Backlog' }))
          .getAllByTestId('segment')
          .map((segment) => segment.dataset.segment)
      expect(segmente()).toEqual(['belegt', 'belegt', 'frei', 'frei'])
      unmount()

      render(<BoardView board={mitGrenze(2)} initialCards={[card, zweite]} canEdit api={mkApi()} />)
      expect(segmente()).toEqual(['belegt', 'grenze'])
    })

    it('begrenzt die Skala bei großen Grenzen auf zwölf Segmente', () => {
      render(<BoardView board={mitGrenze(30)} initialCards={[card]} canEdit api={mkApi()} />)
      expect(within(screen.getByRole('meter', { name: 'Auslastung Backlog' })).getAllByTestId('segment')).toHaveLength(12)
    })

    it('legt den Balken neben den Spaltenkopf, statt den Kopf einzufärben', () => {
      // AK 2: Der Kopf ist im Struktur-Editiermodus anklickbar und ziehbar — eine zustandstragende
      // Fläche darf keine Bedienfunktion tragen.
      render(<BoardView board={mitGrenze(1)} initialCards={[card]} canEdit api={mkApi()} />)

      const kopf = screen.getByTestId('column-header-10')
      expect(within(kopf).queryByRole('meter')).not.toBeInTheDocument()
    })
  })

  it('weist bei schmalem Fenster darauf hin, dass sich weitere Spalten waagerecht rollen lassen', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    const hinweis = screen.getByText('Weitere Spalten: waagerecht rollen')
    // Nur unterhalb von 900 px sichtbar; darüber stehen die Spalten nebeneinander.
    const regeln = [...document.styleSheets]
      .flatMap((blatt) => [...blatt.cssRules])
      .map((regel) => regel.cssText)
    const klasse = [...hinweis.classList].find((c) => c.startsWith('css-'))
    expect(regeln.some((r) => r.includes('min-width:900px') && r.includes(`.${klasse}`) && r.includes('display: none'))).toBe(true)
  })

  it('bearbeitet eine Karte über „Bearbeiten“ im Menü', () => {
    const onEditCard = vi.fn()
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} onEditCard={onEditCard} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Bearbeiten' }))

    expect(onEditCard).toHaveBeenCalledWith(card)
  })

  it('bricht das Löschen einer Spalte über Abbrechen ab und schließt den Dialog per Escape', async () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog löschen'))
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    await waitFor(() => expect(screen.queryByText('Spalte löschen?')).not.toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('Spalte Backlog löschen'))
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() => expect(screen.queryByText('Spalte löschen?')).not.toBeInTheDocument())
  })

  it('bricht das Verschieben der Auswahl in den Papierkorb über Abbrechen ab', () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Abbrechen' }),
    )

    expect(api.bulkDelete).not.toHaveBeenCalled()
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('schließt die Bulk-Bestätigungsdialoge (Archivieren/Papierkorb) per Escape ohne Aktion', async () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))

    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() => expect(screen.queryByText('Karten archivieren?')).not.toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() =>
      expect(screen.queryByText('In den Papierkorb verschieben?')).not.toBeInTheDocument(),
    )

    expect(api.bulkArchive).not.toHaveBeenCalled()
    expect(api.bulkDelete).not.toHaveBeenCalled()
  })

  it('schließt den Einzelkarten- und den Bulk-Verschieben-Dialog per Escape', async () => {
    mProjects.list.mockResolvedValue([])
    render(<BoardView board={board} initialCards={[card]} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Verschieben…' }))
    fireEvent.keyDown(await screen.findByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() =>
      expect(screen.queryByText('Karte verschieben')).not.toBeInTheDocument(),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-100'))
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))
    fireEvent.keyDown(await screen.findByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() =>
      expect(screen.queryByText('Karte verschieben')).not.toBeInTheDocument(),
    )
  })

  it('lässt die Fehler-Toast-Meldung stehen (kein Auto-Hide für Fehler)', async () => {
    vi.useFakeTimers()
    try {
      const api = mkApi({ bulkArchive: vi.fn().mockRejectedValue(new Error('fail')) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      fireEvent.click(screen.getByTestId('card-100'))
      fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

      await vi.waitFor(() =>
        expect(screen.getByText('Archivieren fehlgeschlagen.')).toBeInTheDocument(),
      )

      // Fehler-Toasts blenden sich NICHT selbst aus (bleiben bis zum Wegklicken).
      vi.advanceTimersByTime(5000)

      expect(screen.getByText('Archivieren fehlgeschlagen.')).toBeInTheDocument()
    } finally {
      vi.useRealTimers()
    }
  })

  it('bricht das Spalten-Reorder ab, wenn die gezogene Spalte zwischenzeitlich verschwunden ist', () => {
    const { rerender } = render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    fireEvent.dragStart(screen.getByTestId('column-header-20'))

    // Spalte 20 verschwindet aus board.columns, bevor der Drop passiert (z. B. andere Session).
    const updatedBoard: Board = { ...board, columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }] }
    rerender(<BoardView board={updatedBoard} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.drop(screen.getByTestId('column-header-10'))
    expect(mColumns.reorder).not.toHaveBeenCalled()
  })

  it('ignoriert das Verschieben einer Karte auf ihre eigene Spalte', () => {
    const api = mkApi()
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)
    dropOnColumn(10, 100)
    expect(api.move).not.toHaveBeenCalled()
  })

  it('ändert den Epic-Filter auch, wenn localStorage nicht verfügbar ist', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => { throw new Error('storage disabled') },
      setItem: () => { throw new Error('storage disabled') },
      removeItem: () => { throw new Error('storage disabled') },
      clear: () => {}, key: () => null, length: 0,
    })
    try {
      const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [1], rootNumbers: [1], requirementCardNumber: null }]
      const inEpic: Card = { ...card, parentId: 9 }
      render(<BoardView board={board} initialCards={[inEpic]} canEdit epics={epics} api={mkApi()} />)
      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })
      // Filter greift trotz localStorage-Fehler (nur das Persistieren schlägt stumm fehl).
      expect(screen.getByTestId('card-100')).toBeInTheDocument()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('liest einen gespeicherten Epic-Filter beim Mount aus localStorage', () => {
    // Funktionaler localStorage-Stub mit vorbelegtem Wert -> der Lazy-Initializer nimmt beim
    // Mount den truthy-Zweig (Number(raw)) statt des null-Fallbacks.
    const store = new Map<string, string>([['manban.boardEpicFilter.1', '9']])
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => { store.set(k, v) },
      removeItem: (k: string) => { store.delete(k) },
      clear: () => store.clear(), key: () => null, length: 0,
    })
    try {
      const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [1], rootNumbers: [1], requirementCardNumber: null }]
      const inEpic: Card = { ...card, id: 100, parentId: 9 }
      const other: Card = { ...card, id: 200, number: 2, parentId: null }
      render(<BoardView board={board} initialCards={[inEpic, other]} canEdit epics={epics} api={mkApi()} />)

      // Filter greift sofort beim Mount: nur die Epic-Karte ist sichtbar.
      expect(screen.getByTestId('card-100')).toBeInTheDocument()
      expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('startet ohne aktiven Epic-Filter, wenn nichts gespeichert ist', () => {
    // Funktionaler, aber leerer localStorage: getItem liefert null -> der Initializer nimmt den
    // null-Zweig von `raw ? Number(raw) : null` (nicht den catch-Fallback).
    vi.stubGlobal('localStorage', {
      getItem: () => null,
      setItem: () => {}, removeItem: () => {},
      clear: () => {}, key: () => null, length: 0,
    })
    try {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
      expect(screen.getByTestId('card-100')).toBeInTheDocument()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('setzt den Epic-Filter auf „Alle" zurück und entfernt den gespeicherten Wert', () => {
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    const inEpic: Card = { ...card, id: 100, parentId: 9 }
    const other: Card = { ...card, id: 200, number: 2, parentId: null }
    render(<BoardView board={board} initialCards={[inEpic, other]} canEdit epics={epics} api={mkApi()} />)

    // Erst filtern (setItem/Number-Zweig) ...
    fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })
    expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
    // ... dann auf leeren Wert zurück: onChange nimmt den null-Zweig, changeEpicFilter den removeItem-Zweig.
    fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '' } })
    expect(screen.getByTestId('card-200')).toBeInTheDocument()
  })

  it('füllt beim Bearbeiten das WIP-Limit-Feld mit dem bestehenden Wert vor', () => {
    const boardWithWip: Board = {
      ...board,
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: 5 }, board.columns[1]],
    }
    render(<BoardView board={boardWithWip} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog bearbeiten'))
    // Bestehendes WIP-Limit (!= null) wird als String vorbefüllt.
    expect(screen.getByLabelText('WIP-Limit')).toHaveValue(5)
  })

  it('zeigt im WIP-Zähler den vollen Bestand der Spalte', () => {
    // Regressionsschutz: ohne gesetzten Vorhaben-Filter sind voller und gefilterter Bestand gleich.
    const boardWithWip: Board = {
      ...board,
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: 5 }, board.columns[1]],
    }
    const cards: Card[] = [
      { ...card, id: 100, number: 1, parentId: 9, positionInColumn: 0 },
      { ...card, id: 200, number: 2, parentId: 9, positionInColumn: 1 },
      { ...card, id: 300, number: 3, parentId: null, positionInColumn: 2 },
    ]
    render(<BoardView board={boardWithWip} initialCards={cards} canEdit api={mkApi()} />)

    expect(within(screen.getByTestId('column-header-10')).getByText('3/5')).toBeInTheDocument()
  })

  it('zeigt im WIP-Zähler den vollen Bestand auch bei gesetztem Vorhaben-Filter', () => {
    // Verhaltensänderung: der Zähler misst die WIP-Grenze, nicht den Filterstand. Sichtbar sind
    // nur die gefilterten Karten, gezählt wird der volle Bestand.
    const boardWithWip: Board = {
      ...board,
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: 5 }, board.columns[1]],
    }
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    const cards: Card[] = [
      { ...card, id: 100, number: 1, parentId: 9, positionInColumn: 0 },
      { ...card, id: 200, number: 2, parentId: 9, positionInColumn: 1 },
      { ...card, id: 300, number: 3, parentId: null, positionInColumn: 2 },
    ]
    render(<BoardView board={boardWithWip} initialCards={cards} canEdit epics={epics} api={mkApi()} />)

    fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })

    // Dargestellt wird gefiltert ...
    expect(screen.queryByTestId('card-300')).not.toBeInTheDocument()
    // ... gezählt wird der volle Bestand.
    expect(within(screen.getByTestId('column-header-10')).getByText('3/5')).toBeInTheDocument()
  })

  it('deaktiviert „Speichern" bei ungültigem WIP-Limit', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Spalte Backlog bearbeiten'))
    // 0 ist kein gültiges (positives) WIP-Limit -> parsedWip() === undefined -> Button disabled.
    fireEvent.change(screen.getByLabelText('WIP-Limit'), { target: { value: '0' } })
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
  })

  it('beendet den Auswahlmodus über denselben Umschalt-Button', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    expect(screen.getByLabelText('Karte Aufgabe auswählen')).toBeInTheDocument()
    // Erneuter Klick (jetzt „Auswahl beenden") nimmt den exitSelection-Zweig von toggleSelectionMode.
    fireEvent.click(screen.getByRole('button', { name: 'Auswahl beenden' }))
    expect(screen.queryByLabelText('Karte Aufgabe auswählen')).not.toBeInTheDocument()
  })

  it('bricht das Duplizieren ab, wenn zwischenzeitlich keine Spalte mehr existiert', () => {
    const api = mkApi()
    const { rerender } = render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)
    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

    // Alle Spalten verschwinden (z. B. andere Session), während das Menü offen ist.
    const emptyBoard: Board = { ...board, columns: [] }
    rerender(<BoardView board={emptyBoard} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByRole('menuitem', { name: 'Duplizieren' }))
    // Guard columns.length === 0 greift: kein Nachladen, kein Anlage-Dialog, kein create.
    expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
    expect(api.get).not.toHaveBeenCalled()
    expect(api.create).not.toHaveBeenCalled()
  })

  describe('Editiermodus aus (editMode=false)', () => {
    it('blendet die Struktur-Affordances aus, lässt aber den Karten-Alltag stehen', () => {
      editMode.value = false
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

      // Struktur-Bleistifte verschwinden ...
      expect(screen.queryByLabelText('Spalte Backlog bearbeiten')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Spalte Backlog löschen')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Spalte' })).not.toBeInTheDocument()

      // ... der tägliche Kanban-Alltag bleibt erhalten.
      expect(screen.getByRole('button', { name: 'Neu anlegen' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Menü Aufgabe' })).toBeInTheDocument()
    })

    it('zeigt im Karten-Menü kein „Bearbeiten“, aber weiterhin die Alltags-Aktionen', () => {
      editMode.value = false
      render(<BoardView board={board} initialCards={[card]} canEdit onEditCard={vi.fn()} api={mkApi()} />)

      fireEvent.click(screen.getByRole('button', { name: 'Menü Aufgabe' }))
      expect(screen.queryByRole('menuitem', { name: 'Bearbeiten' })).not.toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: 'Archivieren' })).toBeInTheDocument()
    })
  })

  describe('Tastenkürzel „+“', () => {
    const epics = [
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null },
    ]

    it('trägt in keinem Spaltenkopf mehr ein „+“', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
      expect(screen.queryByLabelText('Karte in Backlog anlegen')).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Karte in Done anlegen')).not.toBeInTheDocument()
    })

    it('öffnet den Anlage-Dialog für die erste Spalte', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

      fireEvent.keyDown(document.body, { key: '+' })

      expect(screen.getByRole('heading', { name: 'Neue Karte in „Backlog“' })).toBeInTheDocument()
    })

    it('legt die Karte in der ersten Spalte an', async () => {
      const created: Card = { ...card, id: 200, number: 2, title: 'Neu' }
      const api = mkApi({ create: vi.fn().mockResolvedValue(created) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

      fireEvent.keyDown(document.body, { key: '+' })
      fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Neu' } })
      fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

      await waitFor(() =>
        expect(api.create).toHaveBeenCalledWith(1, 10, 'Neu', expect.any(String), null, expect.any(Object)),
      )
      expect(within(screen.getByTestId('column-10')).getByTestId('card-200')).toBeInTheDocument()
    })

    it('greift nicht, solange der Fokus in einem Eingabefeld steht', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit epics={epics} api={mkApi()} />)

      fireEvent.keyDown(screen.getByLabelText('Vorhaben-Filter'), { key: '+' })

      expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
    })

    it('greift nicht, solange ein Dialog offen ist', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
      fireEvent.click(screen.getByLabelText('Spalte Backlog bearbeiten'))

      fireEvent.keyDown(screen.getByRole('dialog'), { key: '+' })

      expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
      expect(screen.getByRole('heading', { name: 'Spalte bearbeiten' })).toBeInTheDocument()
    })

    it('greift nicht ohne Bearbeitungsrecht', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />)

      fireEvent.keyDown(document.body, { key: '+' })

      expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
    })

    it('greift nicht, wenn das Board keine Spalten hat', () => {
      const emptyBoard: Board = { ...board, columns: [] }
      render(<BoardView board={emptyBoard} initialCards={[]} canEdit api={mkApi()} />)

      fireEvent.keyDown(document.body, { key: '+' })

      expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
    })

    it('meldet den Tastatur-Listener beim Verlassen der Ansicht wieder ab', () => {
      const add = vi.spyOn(document, 'addEventListener')
      const remove = vi.spyOn(document, 'removeEventListener')
      const { unmount } = render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
      const registered = add.mock.calls.filter(([type]) => type === 'keydown').map(([, fn]) => fn)

      unmount()

      const removed = remove.mock.calls.filter(([type]) => type === 'keydown').map(([, fn]) => fn)
      expect(registered).toHaveLength(1)
      expect(removed).toEqual(registered)
      add.mockRestore()
      remove.mockRestore()
    })
  })

  describe('Verschieben im ⋮-Menü', () => {
    // Drei Spalten, damit es eine echte Mitte mit beiden Nachbarn gibt.
    const wideBoard: Board = {
      ...board,
      columns: [
        { id: 10, name: 'Backlog', position: 0, wipLimit: null },
        { id: 20, name: 'Doing', position: 1, wipLimit: null },
        { id: 30, name: 'Done', position: 2, wipLimit: null },
      ],
    }
    const middleCard: Card = { ...card, columnId: 20 }
    const lastCard: Card = { ...card, columnId: 30 }
    const moveItems = () =>
      screen.getAllByRole('menuitem').filter((item) => item.textContent?.startsWith('Nach '))

    it('zeigt in einer mittleren Spalte genau zwei Verschieben-Einträge', () => {
      render(<BoardView board={wideBoard} initialCards={[middleCard]} canEdit api={mkApi()} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

      expect(moveItems().map((item) => item.textContent)).toEqual([
        'Nach links verschieben',
        'Nach rechts verschieben',
      ])
    })

    it('verschiebt nach rechts in die Spalte mit der nächsthöheren Position', async () => {
      const api = mkApi({ move: vi.fn().mockResolvedValue({}) })
      render(<BoardView board={wideBoard} initialCards={[middleCard]} canEdit api={api} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
      fireEvent.click(screen.getByRole('menuitem', { name: 'Nach rechts verschieben' }))

      await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 30, 0))
      expect(within(screen.getByTestId('column-30')).getByTestId('card-100')).toBeInTheDocument()
    })

    it('verschiebt nach links in die Spalte mit der nächstniedrigeren Position', async () => {
      const api = mkApi({ move: vi.fn().mockResolvedValue({}) })
      render(<BoardView board={wideBoard} initialCards={[middleCard]} canEdit api={api} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
      fireEvent.click(screen.getByRole('menuitem', { name: 'Nach links verschieben' }))

      await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 10, 0))
      expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
    })

    it('lässt in der ersten Spalte „nach links“ weg', () => {
      render(<BoardView board={wideBoard} initialCards={[card]} canEdit api={mkApi()} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

      expect(screen.getByRole('menuitem', { name: 'Nach rechts verschieben' })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Nach links verschieben' })).not.toBeInTheDocument()
    })

    it('lässt in der letzten Spalte „nach rechts“ weg', () => {
      render(<BoardView board={wideBoard} initialCards={[lastCard]} canEdit api={mkApi()} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

      expect(screen.getByRole('menuitem', { name: 'Nach links verschieben' })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Nach rechts verschieben' })).not.toBeInTheDocument()
    })

    it('zeigt bei einem Board mit nur einer Spalte keinen Verschieben-Eintrag', () => {
      const single: Board = { ...board, columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }] }
      render(<BoardView board={single} initialCards={[card]} canEdit api={mkApi()} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

      expect(moveItems()).toHaveLength(0)
    })

    it('lässt die übrigen Menüeinträge unangetastet — die Liste ist vollständig (Issue #1202)', () => {
      render(<BoardView board={wideBoard} initialCards={[middleCard]} canEdit canTransfer
        onEditCard={vi.fn()} api={mkApi()} />)

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

      // Vollständige Liste, nicht nur eine Auswahl: Nur so belegt der Test, dass der Pool-Eintrag
      // fehlt und nicht bloß nicht mitgeprüft wird (AK 3).
      expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual([
        'Bearbeiten',
        'Duplizieren',
        'Archivieren',
        'Verschieben…',
        'Nach links verschieben',
        'Nach rechts verschieben',
        'Löschen',
      ])
    })

    it('ist ohne Maus bedienbar: Tab zum ⋮, Enter, Pfeiltasten, Enter', async () => {
      const user = userEvent.setup()
      const api = mkApi({ move: vi.fn().mockResolvedValue({}) })
      render(<BoardView board={wideBoard} initialCards={[middleCard]} canEdit api={api} />)
      const focused = (el: HTMLElement) => el.matches(':focus')
      const menuButton = screen.getByLabelText('Menü Aufgabe')

      // Tabben, bis der ⋮-Button den Fokus hat — er muss in der Tab-Reihenfolge liegen.
      for (let i = 0; i < 40 && !focused(menuButton); i++) {
        await user.tab()
      }
      expect(menuButton).toHaveFocus()

      await user.keyboard('{Enter}')
      const target = await screen.findByRole('menuitem', { name: 'Nach rechts verschieben' })
      for (let i = 0; i < 10 && !focused(target); i++) {
        await user.keyboard('{ArrowDown}')
      }
      expect(target).toHaveFocus()
      await user.keyboard('{Enter}')

      await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 30, 0))
    })
  })

  describe('Sortier-Toggle im Spaltenkopf', () => {
    const ascLabel = (column: string) => `Spalte ${column} nach Nummer aufsteigend sortieren`
    const descLabel = (column: string) => `Spalte ${column} nach Nummer absteigend sortieren`

    it('wechselt bei jedem Klick die Richtung: ASC, DESC, ASC', async () => {
      mColumns.sortByNumber.mockResolvedValue(undefined)
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      await screen.findByLabelText(descLabel('Backlog'))
      fireEvent.click(screen.getByLabelText(descLabel('Backlog')))
      await screen.findByLabelText(ascLabel('Backlog'))
      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      await waitFor(() => expect(mColumns.sortByNumber).toHaveBeenCalledTimes(3))

      expect(mColumns.sortByNumber.mock.calls).toEqual([[10, 'ASC'], [10, 'DESC'], [10, 'ASC']])
    })

    it('führt die Richtung je Spalte unabhängig', async () => {
      mColumns.sortByNumber.mockResolvedValue(undefined)
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      await screen.findByLabelText(descLabel('Backlog'))

      // Die zweite Spalte startet trotzdem bei aufsteigend.
      expect(screen.getByLabelText(ascLabel('Done'))).toBeInTheDocument()
      fireEvent.click(screen.getByLabelText(ascLabel('Done')))
      await waitFor(() => expect(mColumns.sortByNumber).toHaveBeenCalledTimes(2))

      expect(mColumns.sortByNumber.mock.calls).toEqual([[10, 'ASC'], [20, 'ASC']])
      expect(screen.getByLabelText(descLabel('Backlog'))).toBeInTheDocument()
    })

    it('zeigt den Button auf allen Spalten, auch ohne Editiermodus', () => {
      editMode.value = false
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      expect(screen.getByLabelText(ascLabel('Backlog'))).toBeInTheDocument()
      expect(screen.getByLabelText(ascLabel('Done'))).toBeInTheDocument()
    })

    it('zeigt den Button nicht ohne Verschieberecht', () => {
      render(<BoardView board={board} initialCards={[card]} canEdit={false} api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      expect(screen.queryByLabelText(ascLabel('Backlog'))).not.toBeInTheDocument()
    })

    it('behält die Richtung bei, wenn das Sortieren fehlschlägt', async () => {
      mColumns.sortByNumber.mockRejectedValue(new Error('fail'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))

      expect(await screen.findByText('Sortieren fehlgeschlagen.')).toBeInTheDocument()
      expect(screen.getByLabelText(ascLabel('Backlog'))).toBeInTheDocument()
    })

    it('lädt die Karten nach erfolgreichem Sortieren neu', async () => {
      mColumns.sortByNumber.mockResolvedValue(undefined)
      const onCardsChanged = vi.fn()
      render(
        <BoardView
          board={board}
          initialCards={[card]}
          canEdit
          api={mkApi()}
          onCardsChanged={onCardsChanged} />,
        { wrapper: SnackbarProvider },
      )

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))

      await waitFor(() => expect(onCardsChanged).toHaveBeenCalledTimes(1))
    })

    it('lädt die Karten nicht neu, wenn das Sortieren fehlschlägt', async () => {
      mColumns.sortByNumber.mockRejectedValue(new Error('fail'))
      const onCardsChanged = vi.fn()
      render(
        <BoardView
          board={board}
          initialCards={[card]}
          canEdit
          api={mkApi()}
          onCardsChanged={onCardsChanged} />,
        { wrapper: SnackbarProvider },
      )

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))

      expect(await screen.findByText('Sortieren fehlgeschlagen.')).toBeInTheDocument()
      expect(onCardsChanged).not.toHaveBeenCalled()
    })

    it('meldet den Erfolg mit der tatsächlich sortierten Richtung', async () => {
      mColumns.sortByNumber.mockResolvedValue(undefined)
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      expect(await screen.findByText('Spalte Backlog aufsteigend sortiert')).toBeInTheDocument()

      fireEvent.click(screen.getByLabelText(descLabel('Backlog')))
      expect(await screen.findByText('Spalte Backlog absteigend sortiert')).toBeInTheDocument()
    })

    it('meldet keinen Erfolg, wenn das Sortieren fehlschlägt', async () => {
      mColumns.sortByNumber.mockRejectedValue(new Error('fail'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))

      expect(await screen.findByText('Sortieren fehlgeschlagen.')).toBeInTheDocument()
      expect(screen.queryByText('Spalte Backlog aufsteigend sortiert')).not.toBeInTheDocument()
    })

    it('sperrt den Button der laufenden Spalte gegen den zweiten Klick', async () => {
      let finish: () => void = () => {}
      mColumns.sortByNumber.mockReturnValue(
        new Promise<void>((resolve) => {
          finish = () => resolve()
        }),
      )
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      await waitFor(() => expect(screen.getByLabelText(ascLabel('Backlog'))).toBeDisabled())

      // Der zweite Klick während des Aufrufs läuft ins Leere — kein Doppel-Request.
      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))
      expect(mColumns.sortByNumber).toHaveBeenCalledTimes(1)
      // Andere Spalten bleiben bedienbar: gesperrt ist nur die laufende Spalte.
      expect(screen.getByLabelText(ascLabel('Done'))).toBeEnabled()

      finish()
      expect(await screen.findByLabelText(descLabel('Backlog'))).toBeEnabled()
    })

    it('gibt den Button nach einem Fehlschlag wieder frei', async () => {
      mColumns.sortByNumber.mockRejectedValue(new Error('fail'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))

      expect(await screen.findByText('Sortieren fehlgeschlagen.')).toBeInTheDocument()
      expect(screen.getByLabelText(ascLabel('Backlog'))).toBeEnabled()
    })
  })

  describe('Massenaktionen bei gesetztem Vorhaben-Filter', () => {
    // Massenaktionen dürfen nur treffen, was der Nutzer sieht: Wer auswählt und danach den
    // Vorhaben-Filter setzt, würde sonst Karten verschieben/archivieren/löschen, die nicht auf
    // dem Board stehen — und die Aktionsleiste nennte eine Zahl, die dazu nicht passt.
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [1, 3], rootNumbers: [1, 3], requirementCardNumber: null }]
    const epicA: Card = { ...card, id: 100, number: 1, title: 'Vorhaben A', parentId: 9, positionInColumn: 0 }
    const epicB: Card = { ...card, id: 101, number: 3, title: 'Vorhaben B', parentId: 9, positionInColumn: 1 }
    const frei: Card = { ...card, id: 200, number: 2, title: 'Ohne Vorhaben', parentId: null, positionInColumn: 2 }

    /** Auswahlmodus starten, die genannten Karten anhaken, danach auf Vorhaben 9 filtern. */
    const selectThenFilter = (ids: number[]) => {
      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      ids.forEach((id) => fireEvent.click(screen.getByTestId(`card-${id}`)))
      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })
    }

    const clearFilter = () =>
      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '' } })

    it('zählt in der Aktionsleiste nur die sichtbaren Karten', () => {
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={mkApi()} />)

      selectThenFilter([100, 200])

      expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()
    })

    it('übergibt dem Verschieben-Dialog nur die sichtbaren Karten', async () => {
      mProjects.list.mockResolvedValue([{ id: 2, name: 'Anderes Projekt', role: 'OWNER', createdAt: '' }])
      mBoards.list.mockResolvedValue([
        { id: 99, projectId: 2, name: 'Zielboard', createdAt: '',
          columns: [{ id: 900, name: 'Backlog', position: 0, wipLimit: null }] },
      ])
      mCards.bulkTransfer.mockResolvedValue([])
      render(
        <BoardView board={board} initialCards={[epicA, frei]} canEdit canTransfer epics={epics}
          api={mkApi()} />,
      )

      selectThenFilter([100, 200])
      fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

      fireEvent.change(await screen.findByLabelText('Zielprojekt'), { target: { value: '2' } })
      fireEvent.change(await screen.findByLabelText('Zielboard'), { target: { value: '99' } })
      fireEvent.change(await screen.findByLabelText('Zielspalte'), { target: { value: '900' } })
      fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))

      await waitFor(() => expect(mCards.bulkTransfer).toHaveBeenCalledWith([100], 99, 900))
    })

    it('archiviert nur die sichtbaren Karten und nimmt nur diese aus der Ansicht', async () => {
      const api = mkApi({ bulkArchive: vi.fn().mockResolvedValue([]) })
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={api} />)

      selectThenFilter([100, 200])
      fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

      await waitFor(() => expect(api.bulkArchive).toHaveBeenCalledWith([100]))
      // Die verborgene Karte hat das optimistische Entfernen nicht mitgemacht: Filter weg, Karte da.
      clearFilter()
      expect(screen.getByTestId('card-200')).toBeInTheDocument()
      expect(screen.queryByTestId('card-100')).not.toBeInTheDocument()
    })

    it('verschiebt nur die sichtbaren Karten in den Papierkorb', async () => {
      const api = mkApi({ bulkDelete: vi.fn().mockResolvedValue(undefined) })
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={api} />)

      selectThenFilter([100, 200])
      fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
      fireEvent.click(
        within(screen.getByRole('dialog')).getByRole('button', { name: 'In den Papierkorb' }),
      )

      await waitFor(() => expect(api.bulkDelete).toHaveBeenCalledWith([100]))
    })

    it('nennt in beiden Bestätigungsdialogen die Zahl der sichtbaren Karten', async () => {
      render(
        <BoardView board={board} initialCards={[epicA, epicB, frei]} canEdit epics={epics}
          api={mkApi()} />,
      )

      // Drei ausgewählt, zwei davon sichtbar — die Dialoge müssen von zweien sprechen.
      selectThenFilter([100, 101, 200])

      fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
      expect(screen.getByText(/2 Karten werden archiviert\./)).toBeInTheDocument()
      fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' })
      await waitFor(() => expect(screen.queryByText('Karten archivieren?')).not.toBeInTheDocument())

      fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
      expect(screen.getByText(/2 Karten werden in den Papierkorb verschoben\./)).toBeInTheDocument()
    })

    it('blendet die Aktionsleiste aus, wenn alle ausgewählten Karten verborgen sind', () => {
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={mkApi()} />)

      selectThenFilter([200])

      // Nicht „0 ausgewählt", sondern gar keine Leiste.
      expect(screen.queryByRole('region', { name: 'Massenaktionen' })).not.toBeInTheDocument()
    })

    it('macht die verborgene Auswahl nach Aufheben des Filters wieder wirksam', () => {
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={mkApi()} />)

      selectThenFilter([100, 200])
      expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()

      // Belegt, dass die wirksame Menge abgeleitet und die Auswahl nicht beschnitten wird.
      clearFilter()
      expect(screen.getByText('2 ausgewählt')).toBeInTheDocument()
    })

    it('beendet mit einer ausgeführten Massenaktion auch die verborgene Rest-Auswahl', async () => {
      const api = mkApi({ bulkArchive: vi.fn().mockResolvedValue([]) })
      render(<BoardView board={board} initialCards={[epicA, frei]} canEdit epics={epics} api={api} />)

      selectThenFilter([100, 200])
      fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))
      await waitFor(() => expect(api.bulkArchive).toHaveBeenCalledWith([100]))

      clearFilter()

      // Die Karte ist unversehrt zurück — aber exitSelection hat den Auswahlmodus samt der
      // verborgenen Rest-Auswahl beendet. Das Rückkehr-Versprechen gilt nur ohne Aktion dazwischen.
      expect(screen.getByTestId('card-200')).toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'Massenaktionen' })).not.toBeInTheDocument()
      expect(screen.queryByLabelText('Karte Ohne Vorhaben auswählen')).not.toBeInTheDocument()
    })
  })

  describe('ausgeblendete Vorhaben', () => {
    // Ausblenden ist reine Darstellung (Plan #620). Seit Plan #717 (A3) hält `BoardPage` den
    // Zustand und reicht ihn herein: Diese Tests setzen ihn deshalb als Prop, nicht über den
    // localStorage-Schlüssel — das Lesen und Fortschreiben belegt `BoardPage.test.tsx`.
    const mkEpic = (id: number, title: string, memberNumbers: number[]) => ({
      id, number: id, title, description: null, shortcode: title.slice(0, 3).toUpperCase(),
      done: 0, total: memberNumbers.length, memberNumbers, rootNumbers: memberNumbers,
      requirementCardNumber: null,
    })
    // Vorhaben A hält die Karten #1 (Backlog) und #3 (Done), Vorhaben B die Karte #2 (Backlog).
    const epics = [mkEpic(9, 'Auth', [1, 3]), mkEpic(8, 'Suche', [2])]
    const aBacklog: Card = { ...card, id: 100, number: 1, title: 'A im Backlog', parentId: 9, columnId: 10, positionInColumn: 0 }
    const bBacklog: Card = { ...card, id: 200, number: 2, title: 'B im Backlog', parentId: 8, columnId: 10, positionInColumn: 1 }
    const aDone: Card = { ...card, id: 101, number: 3, title: 'A in Done', parentId: 9, columnId: 20, positionInColumn: 0 }
    const frei: Card = { ...card, id: 300, number: 4, title: 'Ohne Vorhaben', parentId: null, columnId: 10, positionInColumn: 2 }

    /**
     * Hält die Ausblendung so, wie `BoardPage` es tut (Plan #717, A3): `BoardView` ist für diese
     * Achse ein kontrolliertes Bauteil, der Zustand liegt beim Aufrufer. Ohne diesen Halter bliebe
     * die Prop nach dem Einblenden stehen und die Karten kämen nicht zurück.
     */
    function BoardMitAusblendung({
      initial, cards: initialCards, ...rest
    }: { initial: number[]; cards: Card[] } & Partial<ComponentProps<typeof BoardView>>) {
      const [hidden, setHidden] = useState<ReadonlySet<number>>(() => new Set(initial))
      return (
        <BoardView
          board={board} initialCards={initialCards} canEdit epics={epics} api={mkApi()}
          hiddenEpics={hidden} onHiddenEpicsChange={setHidden} {...rest}
        />
      )
    }

    afterEach(() => vi.unstubAllGlobals())

    it('blendet die Karten eines ausgeblendeten Vorhabens aus allen Spalten aus', () => {
      render(<BoardMitAusblendung initial={[9]} cards={[aBacklog, aDone, frei]} />)

      // Beide Spalten verlieren ihre Karte — nicht nur das Backlog.
      expect(screen.queryByTestId('card-100')).not.toBeInTheDocument()
      expect(screen.queryByTestId('card-101')).not.toBeInTheDocument()
      expect(screen.getByTestId('card-300')).toBeInTheDocument()
      // Und beide sagen es an (Singular-Form der Marke).
      expect(within(screen.getByTestId('column-header-10')).getByRole('button', { name: '1 Karte ausgeblendet, einblenden' })).toBeInTheDocument()
      expect(within(screen.getByTestId('column-header-20')).getByRole('button', { name: '1 Karte ausgeblendet, einblenden' })).toBeInTheDocument()
    })

    it('lässt Karten ohne Vorhaben stehen, auch wenn alle Vorhaben ausgeblendet sind', () => {
      render(<BoardMitAusblendung initial={[9, 8]} cards={[aBacklog, bBacklog, frei]} />)

      expect(screen.queryByTestId('card-100')).not.toBeInTheDocument()
      expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
      expect(screen.getByTestId('card-300')).toBeInTheDocument()
    })

    it('meldet bei beiden Achsen gleichzeitig genau eine Erklärung je Spalte', () => {
      // Vorhaben B ausgeblendet, zusätzlich der Vorhaben-Filter auf A: Karte #2 fällt unter beide
      // Achsen und darf trotzdem nur einmal zählen (Vereinigung, Plan #620 E4).
      render(<BoardMitAusblendung initial={[8]} cards={[aBacklog, bBacklog, aDone, frei]} />)

      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })

      const backlogHeader = within(screen.getByTestId('column-header-10'))
      // Genau eine Marke — nicht je Achse eine.
      expect(backlogHeader.getAllByRole('button', { name: /ausgeblendet/ })).toHaveLength(1)
      // Und genau eine Zahl: #2 (beide Achsen) und #4 (nur Filter) = 2, keine Doppelzählung.
      expect(backlogHeader.getByRole('button', { name: '2 Karten ausgeblendet, einblenden' })).toBeInTheDocument()
      // Done zeigt weiter die Karte des gefilterten Vorhabens und damit keine Marke.
      expect(screen.getByTestId('card-101')).toBeInTheDocument()
      expect(within(screen.getByTestId('column-header-20')).queryByRole('button', { name: /ausgeblendet/ })).not.toBeInTheDocument()
    })

    it('zeigt die Zahl der Ausgeblendeten als eigene Marke neben dem WIP-Zähler', () => {
      const boardWithWip: Board = {
        ...board,
        columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: 5 }, board.columns[1]],
      }
      const dritte: Card = { ...card, id: 102, number: 5, title: 'A drittens', parentId: 9, columnId: 10, positionInColumn: 3 }
      render(
        <BoardMitAusblendung initial={[9]} cards={[aBacklog, aDone, dritte, frei]}
          board={boardWithWip} epics={[mkEpic(9, 'Auth', [1, 3, 5]), epics[1]]} />,
      )

      const backlogHeader = within(screen.getByTestId('column-header-10'))
      // Der WIP-Zähler bleibt am vollen Bestand, die Marke steht daneben.
      expect(backlogHeader.getByText('3/5')).toBeInTheDocument()
      expect(backlogHeader.getByRole('button', { name: '2 Karten ausgeblendet, einblenden' })).toBeInTheDocument()
      expect(backlogHeader.getByText('2 ausgeblendet')).toBeInTheDocument()
    })

    it('stellt über die Spaltenmarke die Karten wieder her und setzt beide Achsen zurück', () => {
      render(<BoardMitAusblendung initial={[8]} cards={[aBacklog, bBacklog, frei]} />)
      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })

      fireEvent.click(within(screen.getByTestId('column-header-10')).getByRole('button', { name: /ausgeblendet/ }))

      expect(screen.getByTestId('card-100')).toBeInTheDocument()
      expect(screen.getByTestId('card-200')).toBeInTheDocument()
      expect(screen.getByTestId('card-300')).toBeInTheDocument()
      // Auch die Filter-Achse steht wieder auf „Alle Vorhaben".
      expect(screen.getByLabelText('Vorhaben-Filter')).toHaveValue('')
      expect(screen.queryByRole('button', { name: /ausgeblendet/ })).not.toBeInTheDocument()
    })

    it('bietet ausgeblendete Vorhaben weder im Anlege-Dialog noch im Vorhaben-Filter an', () => {
      // Beide Bedienstellen zeigen seit Issue #785 nur einblendbare Vorhaben — vorher galt das nur
      // für den Anlege-Dialog (Plan #717, A4/A7): Ein ausgeblendetes Vorhaben zeigte über den
      // Filter ohnehin nie eine Karte, weil dessen Karten auf dem Board grundsätzlich verdeckt
      // bleiben; der Filter täuschte damit eine Erreichbarkeit vor, die es nie gab.
      render(<BoardMitAusblendung initial={[8]} cards={[aBacklog, bBacklog, frei]} />)

      expect(within(screen.getByLabelText('Vorhaben-Filter')).queryByRole('option', { name: /Suche/ })).not.toBeInTheDocument()

      fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
      const auswahl = within(screen.getByLabelText('Vorhaben'))
      expect(auswahl.getByRole('option', { name: /Auth/ })).toBeInTheDocument()
      expect(auswahl.queryByRole('option', { name: /Suche/ })).not.toBeInTheDocument()
    })

    it('setzt einen per localStorage gemerkten Filter zurück, wenn das Vorhaben inzwischen ausgeblendet ist', () => {
      // Der Filter merkt sich seine Auswahl unabhängig vom Ausblenden-Zustand: Zeigte er beim Laden
      // weiter auf ein inzwischen ausgeblendetes Vorhaben, bliebe das Board dauerhaft leer, ohne
      // dass der Dropdown das gewählte Vorhaben überhaupt noch als Option führte (Issue #785).
      // Funktionaler localStorage-Stub mit vorbelegtem Wert, analog zum Mount-Test oben im File.
      const store = new Map<string, string>([['manban.boardEpicFilter.1', '8']])
      vi.stubGlobal('localStorage', {
        getItem: (k: string) => store.get(k) ?? null,
        setItem: (k: string, v: string) => { store.set(k, v) },
        removeItem: (k: string) => { store.delete(k) },
        clear: () => store.clear(), key: () => null, length: 0,
      })

      render(<BoardMitAusblendung initial={[8]} cards={[aBacklog, bBacklog, frei]} />)

      expect(screen.getByLabelText('Vorhaben-Filter')).toHaveValue('')
      expect(store.has('manban.boardEpicFilter.1')).toBe(false)
      expect(screen.getByTestId('card-100')).toBeInTheDocument()
      expect(screen.getByTestId('card-300')).toBeInTheDocument()
    })

    it('setzt den Filter-Zustand trotz scheiterndem localStorage zurück, wenn das Vorhaben ausgeblendet ist', () => {
      // Deckt den catch-Zweig des Abgleichs ab: Das Zurücksetzen im State darf nicht an einem
      // scheiternden removeItem hängen — nur das Merken schlägt dann stumm fehl.
      vi.stubGlobal('localStorage', {
        getItem: () => '8',
        setItem: () => {},
        removeItem: () => { throw new Error('storage disabled') },
        clear: () => {}, key: () => null, length: 0,
      })

      render(<BoardMitAusblendung initial={[8]} cards={[aBacklog, bBacklog, frei]} />)

      expect(screen.getByLabelText('Vorhaben-Filter')).toHaveValue('')
      expect(screen.getByTestId('card-100')).toBeInTheDocument()
    })

    it('bleibt ohne die beiden Ausblendungs-Props bedienbar', () => {
      // Beide Props sind optional (Default: leere Menge, No-op). Ein Aufrufer, der die Achse nicht
      // führt, bekommt dann ein Board ohne Ausblendung — und die Spaltenmarke der Filter-Achse
      // funktioniert weiter, obwohl sie das Aufheben auch nach außen meldet.
      render(<BoardView board={board} initialCards={[aBacklog, bBacklog, frei]} canEdit epics={epics} api={mkApi()} />)
      expect(screen.getByTestId('card-100')).toBeInTheDocument()

      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })
      expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()

      fireEvent.click(within(screen.getByTestId('column-header-10')).getByRole('button', { name: /ausgeblendet/ }))
      expect(screen.getByTestId('card-200')).toBeInTheDocument()
    })

    it('blendet auch dann aus, wenn localStorage nicht verfügbar ist', () => {
      // Ohne funktionierendes localStorage fällt nur das Merken aus: Die Ausblendung greift, die
      // Marke erscheint, und das Einblenden wirkt trotz scheiterndem removeItem.
      vi.stubGlobal('localStorage', {
        getItem: () => { throw new Error('storage disabled') },
        setItem: () => { throw new Error('storage disabled') },
        removeItem: () => { throw new Error('storage disabled') },
        clear: () => {}, key: () => null, length: 0,
      })
      render(<BoardMitAusblendung initial={[8]} cards={[aBacklog, bBacklog, frei]} />)
      fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '9' } })

      expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
      const marke = within(screen.getByTestId('column-header-10')).getByRole('button', { name: '2 Karten ausgeblendet, einblenden' })

      fireEvent.click(marke)
      expect(screen.getByTestId('card-200')).toBeInTheDocument()
      expect(screen.getByTestId('card-300')).toBeInTheDocument()
    })

    it('lässt Verschieben und Anlegen auf dem vollen Bestand arbeiten', async () => {
      const created: Card = { ...card, id: 400, number: 6, title: 'Neu', parentId: null, columnId: 10 }
      const api = mkApi({ move: vi.fn().mockResolvedValue(undefined), create: vi.fn().mockResolvedValue(created) })
      render(<BoardMitAusblendung initial={[9]} cards={[aBacklog, bBacklog, aDone]} api={api} />)

      // Die sichtbare Karte #2 nach Done ziehen: Done enthält im vollen Bestand bereits die
      // ausgeblendete Karte #3, der Zielindex ist deshalb 1 — nicht 0 (gefilterter Bestand).
      dropOnColumn(20, 200)
      await waitFor(() => expect(api.move).toHaveBeenCalledWith(200, 20, 1))

      fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
      fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Neu' } })
      fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
      expect(await screen.findByTestId('card-400')).toBeInTheDocument()

      // Kein Aus- oder Einblenden hat eine Karte bewegt: nach dem Einblenden stehen alle wieder da.
      // findAll…: der Anlage-Dialog legt beim Schließen kurz `aria-hidden` über das Board.
      fireEvent.click((await screen.findAllByRole('button', { name: /ausgeblendet/ }))[0])
      expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
      expect(within(screen.getByTestId('column-20')).getByTestId('card-101')).toBeInTheDocument()
      expect(within(screen.getByTestId('column-20')).getByTestId('card-200')).toBeInTheDocument()
    })
  })

  /**
   * Jeder mutierende Handler zeigt die Meldung des Servers (`ApiError.detail`) statt eines eigenen
   * Ersatztexts — sonst rät der Nutzer, woran es lag. Der Fallback greift nur, wo gar keine
   * Server-Meldung vorliegt (Netzfehler). Issue #810.
   */
  describe('Fehlermeldungen der mutierenden Handler', () => {
    const ascLabel = (column: string) => `Spalte ${column} nach Nummer aufsteigend sortieren`

    it('saveColumn: meldet den Serverfehler beim Anlegen und lässt den Dialog mit der Eingabe offen', async () => {
      mColumns.create.mockRejectedValue(serverfehler('Eine Spalte mit diesem Namen gibt es schon.'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByRole('button', { name: 'Spalte' }))
      fireEvent.change(screen.getByLabelText('Spaltenname'), { target: { value: 'Neu' } })
      fireEvent.change(screen.getByLabelText('WIP-Limit'), { target: { value: '5' } })
      fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

      await erwarteFehlerToast('Eine Spalte mit diesem Namen gibt es schon.')
      // Der Dialog bleibt offen und behält Name und WIP-Limit — die Eingabe geht nicht verloren.
      expect(screen.getByLabelText('Spaltenname')).toHaveValue('Neu')
      expect(screen.getByLabelText('WIP-Limit')).toHaveValue(5)
      // Keine Spalte hinzugefügt: es bleiben die beiden des Boards.
      expect(screen.getAllByTestId(/^column-header-/)).toHaveLength(2)
    })

    it('saveColumn: meldet den Serverfehler beim Bearbeiten und ändert die Spalte nicht', async () => {
      mColumns.update.mockRejectedValue(serverfehler('Der Name ist schon vergeben.'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText('Spalte Backlog bearbeiten'))
      fireEvent.change(screen.getByLabelText('Spaltenname'), { target: { value: 'Todo' } })
      fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

      await erwarteFehlerToast('Der Name ist schon vergeben.')
      expect(screen.getByLabelText('Spaltenname')).toHaveValue('Todo')
      // Die Spalte im Board heißt unverändert Backlog.
      expect(within(screen.getByTestId('column-header-10')).getByText('Backlog')).toBeInTheDocument()
    })

    it('saveColumn: fällt ohne Server-Meldung auf den eigenen Text zurück', async () => {
      mColumns.create.mockRejectedValue(new TypeError('Failed to fetch'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByRole('button', { name: 'Spalte' }))
      fireEvent.change(screen.getByLabelText('Spaltenname'), { target: { value: 'Neu' } })
      fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

      await erwarteFehlerToast('Spalte speichern fehlgeschlagen.')
    })

    it('archiveCard: meldet den Serverfehler des Archivierens', async () => {
      const api = mkApi({ archive: vi.fn().mockRejectedValue(serverfehler('Die Karte ist gesperrt.')) })
      const onCardsChanged = vi.fn()
      render(
        <BoardView board={board} initialCards={[card]} canEdit api={api} onCardsChanged={onCardsChanged} />,
        { wrapper: SnackbarProvider },
      )

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
      fireEvent.click(screen.getByRole('menuitem', { name: 'Archivieren' }))

      await erwarteFehlerToast('Die Karte ist gesperrt.')
      // Ein Fehlschlag löst kein Nachladen aus — sonst verschwände die Karte scheinbar doch.
      expect(onCardsChanged).not.toHaveBeenCalled()
    })

    it('archiveCard: fällt ohne Server-Meldung auf den eigenen Text zurück', async () => {
      const api = mkApi({ archive: vi.fn().mockRejectedValue(new TypeError('Failed to fetch')) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
      fireEvent.click(screen.getByRole('menuitem', { name: 'Archivieren' }))

      await erwarteFehlerToast('Archivieren fehlgeschlagen.')
    })

    it('reorderColumn: meldet den Serverfehler und stellt die Reihenfolge wieder her', async () => {
      mColumns.reorder.mockRejectedValue(serverfehler('Die Spaltenreihenfolge ist veraltet.'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.dragStart(screen.getByTestId('column-header-20'))
      fireEvent.drop(screen.getByTestId('column-header-10'))

      await erwarteFehlerToast('Die Spaltenreihenfolge ist veraltet.')
      const headers = screen.getAllByTestId(/^column-header-/)
      expect(headers[0]).toHaveAttribute('data-testid', 'column-header-10')
      expect(headers[1]).toHaveAttribute('data-testid', 'column-header-20')
    })

    it('reorderColumn: fällt ohne Server-Meldung auf den eigenen Text zurück', async () => {
      mColumns.reorder.mockRejectedValue(new TypeError('Failed to fetch'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.dragStart(screen.getByTestId('column-header-20'))
      fireEvent.drop(screen.getByTestId('column-header-10'))

      await erwarteFehlerToast('Spalten umsortieren fehlgeschlagen.')
    })

    it('moveCard: meldet den Serverfehler und rollt die Verschiebung zurück', async () => {
      const api = mkApi({ move: vi.fn().mockRejectedValue(serverfehler('WIP-Limit der Zielspalte erreicht.')) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
        wrapper: SnackbarProvider,
      })

      dropOnColumn(20, 100)

      await erwarteFehlerToast('WIP-Limit der Zielspalte erreicht.')
      expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
      expect(within(screen.getByTestId('column-20')).queryByTestId('card-100')).not.toBeInTheDocument()
    })

    it('moveCard: fällt ohne Server-Meldung auf den eigenen Text zurück', async () => {
      const api = mkApi({ move: vi.fn().mockRejectedValue(new TypeError('Failed to fetch')) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
        wrapper: SnackbarProvider,
      })

      dropOnColumn(20, 100)

      await erwarteFehlerToast('Verschieben fehlgeschlagen.')
    })

    it('sortColumnByNumber: meldet den Serverfehler des Sortierens', async () => {
      mColumns.sortByNumber.mockRejectedValue(serverfehler('Sortieren ist auf diesem Board gesperrt.'))
      render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText(ascLabel('Backlog')))

      await erwarteFehlerToast('Sortieren ist auf diesem Board gesperrt.')
      // Die Richtung wechselt nicht: der nächste Klick versucht dieselbe erneut.
      expect(screen.getByLabelText(ascLabel('Backlog'))).toBeInTheDocument()
    })

    it('duplicateCard: meldet den Serverfehler des Ladens und öffnet keinen Dialog', async () => {
      const api = mkApi({ get: vi.fn().mockRejectedValue(serverfehler('Die Karte gibt es nicht mehr.')) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
      fireEvent.click(screen.getByRole('menuitem', { name: 'Duplizieren' }))

      await erwarteFehlerToast('Die Karte gibt es nicht mehr.')
      expect(screen.queryByRole('heading', { name: /Neue Karte/ })).not.toBeInTheDocument()
      expect(api.create).not.toHaveBeenCalled()
    })

    it('confirmBulkArchive: meldet den Serverfehler und rollt die Auswahl zurück', async () => {
      const api = mkApi({
        bulkArchive: vi.fn().mockRejectedValue(serverfehler('Eine der Karten ist gesperrt.')),
      })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      fireEvent.click(screen.getByTestId('card-100'))
      fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archivieren' }))

      await erwarteFehlerToast('Eine der Karten ist gesperrt.')
      expect(screen.getByTestId('card-100')).toBeInTheDocument()
    })

    it('confirmDelete: meldet den Serverfehler und rollt die Löschung zurück', async () => {
      const api = mkApi({
        bulkDelete: vi.fn().mockRejectedValue(serverfehler('Die Karte hängt an einem Vorhaben.')),
      })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByLabelText('Menü Aufgabe'))
      fireEvent.click(screen.getByRole('menuitem', { name: 'Löschen' }))
      fireEvent.click(
        within(screen.getByRole('dialog')).getByRole('button', { name: 'In den Papierkorb' }),
      )

      await erwarteFehlerToast('Die Karte hängt an einem Vorhaben.')
      expect(screen.getByTestId('card-100')).toBeInTheDocument()
    })

    /**
     * `createItem` fängt bewusst nicht selbst: Ein hier geschluckter Fehler ließe `onSubmit`
     * erfolgreich erscheinen und `NewCardModal` schlösse trotz Fehlschlag. Der Fehler propagiert
     * stattdessen an den Dialog, der ihn seit #808 selbst anzeigt und offen bleibt.
     */
    it('createItem: lässt den Anlege-Dialog bei einem Serverfehler offen (Karte)', async () => {
      const api = mkApi({ create: vi.fn().mockRejectedValue(serverfehler('Der Titel ist schon vergeben.')) })
      render(<BoardView board={board} initialCards={[card]} canEdit api={api} />, {
        wrapper: SnackbarProvider,
      })

      fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
      fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Neu' } })
      fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

      // Die Meldung steht im Dialog, nicht im Toast: BoardView reicht den Fehler durch.
      expect(await screen.findByText('Der Titel ist schon vergeben.')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Anlegen' })).toBeInTheDocument()
      expect(screen.getByLabelText('Titel')).toHaveValue('Neu')
      // Kein Item im Board: es bleibt die eine Bestandskarte.
      expect(screen.getAllByTestId(/^card-\d+$/)).toHaveLength(1)
    })

    it('createItem: lässt den Anlege-Dialog bei einem Serverfehler offen (Vorhaben)', async () => {
      const epicsApi = {
        create: vi.fn().mockRejectedValue(serverfehler('Das Kürzel ist schon vergeben.')),
        bulkAssign: vi.fn(),
      }
      const onEpicsChanged = vi.fn()
      render(
        <BoardView board={board} initialCards={[card]} canEdit api={mkApi()}
          epicsApi={epicsApi} onEpicsChanged={onEpicsChanged} />,
        { wrapper: SnackbarProvider },
      )

      fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
      fireEvent.change(screen.getByLabelText('Typ'), { target: { value: 'EPIC' } })
      fireEvent.change(screen.getByLabelText('Kürzel'), { target: { value: 'AUT' } })
      fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Auth' } })
      fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

      expect(await screen.findByText('Das Kürzel ist schon vergeben.')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Anlegen' })).toBeInTheDocument()
      expect(onEpicsChanged).not.toHaveBeenCalled()
    })
  })
})

describe('BoardView auf dem Tablet im Querformat (Plan #1707, E6)', () => {
  const namen = ['Backlog', 'Ready', 'In Arbeit', 'In Prüfung', 'Done', 'Archiv']
  const boardMit = (anzahl: number): Board => ({
    ...board,
    columns: namen.slice(0, anzahl).map((name, i) => ({ id: (i + 1) * 10, name, position: i, wipLimit: null })),
  })
  const renderBoard = (anzahl: number) =>
    render(
      <TabletQuerProvider>
        <BoardView board={boardMit(anzahl)} initialCards={[card]} canEdit api={mkApi()} />
      </TabletQuerProvider>,
    )
  /** Abstand zwischen zwei Spalten: `Stack` setzt ihn als linken Rand ab der zweiten Spalte. */
  const abstand = () => getComputedStyle(screen.getByTestId('column-20')).marginLeft
  /** Ob der Rollhinweis ohne Breakpoint ausgeblendet ist. */
  const hinweisRegel = () => cssRegel(screen.getByText('Weitere Spalten: waagerecht rollen'))

  beforeEach(() => {
    editMode.value = true
  })

  it('Szenario: Board auf dem kleinen Tablet', () => {
    setzeMedien({ tabletQuer: true })
    renderBoard(5)

    for (const id of [10, 20, 30, 40, 50]) {
      expect(cssRegel(screen.getByTestId(`column-${id}`))).toContain('min-width: 160px')
    }
    expect(abstand()).toBe('8px')
    expect(getComputedStyle(screen.getByTestId('board-surface')).overflow).toBe('auto')
  })

  it('lässt am Desktop Spaltenbreite und Abstand wie bisher (Gegenprobe)', () => {
    renderBoard(5)

    for (const id of [10, 20, 30, 40, 50]) {
      expect(cssRegel(screen.getByTestId(`column-${id}`))).toContain('min-width: 230px')
    }
    expect(abstand()).toBe('16px')
  })

  it('blendet den Rollhinweis bei fünf Spalten aus, weil alle nebeneinander stehen', () => {
    setzeMedien({ tabletQuer: true })
    renderBoard(5)

    expect(hinweisRegel()).toContain('display: none')
  })

  it('zeigt den Rollhinweis ab sechs Spalten, denn dann rollt die Fläche', () => {
    setzeMedien({ tabletQuer: true })
    renderBoard(6)

    expect(hinweisRegel()).toContain('display: block')
  })

  it('macht „+ Spalte“ zum schmalen Symbolknopf mit Beschriftung, der den Anlage-Dialog öffnet', () => {
    setzeMedien({ tabletQuer: true })
    renderBoard(5)

    const knopf = screen.getByRole('button', { name: 'Neue Spalte' })
    expect(knopf).toHaveClass('MuiIconButton-root')
    expect(knopf).not.toHaveTextContent('Spalte')
    fireEvent.click(knopf)
    expect(screen.getByRole('dialog', { name: 'Neue Spalte' })).toBeInTheDocument()
  })

  it('zeigt den Symbolknopf nur im Bearbeitungsmodus', () => {
    editMode.value = false
    setzeMedien({ tabletQuer: true })
    renderBoard(5)

    expect(screen.queryByRole('button', { name: 'Neue Spalte' })).not.toBeInTheDocument()
  })
})

describe('BoardView — Ziehen per Finger auf dem Tablet (Plan #1707, E3–E5, E15)', () => {
  // jsdom kennt weder `PointerEvent` noch `elementFromPoint` (E13): Die Attrappe trägt `pointerId`
  // und `pointerType`, `elementFromPoint` liefert, was der Test unter den Finger legt.
  class PointerEventAttrappe extends MouseEvent {
    readonly pointerId: number
    readonly pointerType: string
    constructor(type: string, init: PointerEventInit = {}) {
      super(type, init)
      this.pointerId = init.pointerId ?? 0
      this.pointerType = init.pointerType ?? ''
    }
  }

  const fuenfSpalten: Board = {
    ...board,
    columns: ['Backlog', 'Ready', 'In Arbeit', 'In Prüfung', 'Done'].map((name, i) => ({
      id: (i + 1) * 10, name, position: i, wipLimit: null,
    })),
  }
  const karte = (id: number, columnId: number, positionInColumn: number): Card =>
    ({ ...card, id, number: id, title: `Karte ${id}`, columnId, positionInColumn })
  const karten = [karte(201, 20, 0), karte(202, 20, 1), karte(203, 20, 2), karte(301, 30, 0), karte(302, 30, 1)]
  const finger = { pointerType: 'touch', pointerId: 1 }

  let unterFinger: Element | null = null

  const zeigen = (api: ReturnType<typeof mkApi>) =>
    render(
      <TabletQuerProvider>
        <SnackbarProvider>
          <BoardView board={fuenfSpalten} initialCards={karten} canEdit api={api} />
        </SnackbarProvider>
      </TabletQuerProvider>,
    )

  /** Legt die Karten einer Spalte untereinander: je 50 px hoch, ab y = 100 im Abstand von 60 px. */
  const stapeln = (...ids: number[]) =>
    ids.forEach((id, i) => {
      const top = 100 + i * 60
      screen.getByTestId(`card-${id}`).getBoundingClientRect = () =>
        ({ left: 0, top, width: 150, height: 50, right: 150, bottom: top + 50, x: 0, y: top, toJSON: () => ({}) }) as DOMRect
    })

  const greifen = (element: HTMLElement) => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date', 'requestAnimationFrame', 'cancelAnimationFrame'] })
    fireEvent.pointerDown(element, { ...finger, clientX: 50, clientY: 20 })
  }
  const warten = (ms: number) => act(() => { vi.advanceTimersByTime(ms) })
  const ueber = (spalteId: number, clientY: number) => {
    unterFinger = screen.getByTestId(`column-${spalteId}`)
    fireEvent.pointerMove(window, { ...finger, clientX: 400, clientY })
  }
  const loslassen = () => {
    fireEvent.pointerUp(window, finger)
    vi.useRealTimers()
  }
  const kartenIn = (columnId: number) =>
    within(screen.getByTestId(`column-${columnId}`)).queryAllByTestId(/^card-/).map((el) => el.dataset.testid)
  /** Die angehobene Kopie, die dem Finger folgt: dieselbe Test-ID, aber für Hilfsmittel verborgen. */
  const schwebendeKopie = (testId: string) =>
    screen.queryAllByTestId(testId).find((el) => el.getAttribute('aria-hidden') === 'true') ?? null
  /** Das Element auf dem Board, nicht seine schwebende Kopie, die dieselbe Test-ID trägt. */
  const aufDemBoard = (testId: string) => within(screen.getByTestId('board-surface')).getByTestId(testId)

  beforeEach(() => {
    editMode.value = true
    mColumns.reorder.mockReset()
    setzeMedien({ tabletQuer: true })
    vi.stubGlobal('PointerEvent', PointerEventAttrappe)
    unterFinger = null
    document.elementFromPoint = vi.fn(() => unterFinger)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('Szenario: Karte per Finger verschieben', async () => {
    const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
    zeigen(api)
    stapeln(301, 302)

    greifen(screen.getByTestId('card-201'))
    warten(HALTEZEIT_MS)

    expect(aufDemBoard('card-201')).toHaveAttribute('data-zieh-zustand', 'zieht')
    expect(schwebendeKopie('card-201')).toHaveTextContent('Karte 201')
    ueber(30, 170)
    // Die Einfügelinie steht an der Stelle, an der die Karte landen wird: zwischen 301 und 302.
    const ablage = screen.getByTestId('ablage-30')
    const reihenfolge = within(ablage).getAllByTestId(/^(card-\d+|einfuegelinie)$/).map((el) => el.dataset.testid)
    expect(reihenfolge).toEqual(['card-301', 'einfuegelinie', 'card-302'])
    expect(ablage).toHaveAttribute('data-ablage', 'aktiv')
    loslassen()

    expect(api.move).toHaveBeenCalledWith(201, 30, 1)
    expect(kartenIn(30)).toEqual(['card-301', 'card-201', 'card-302'])
    expect(kartenIn(20)).toEqual(['card-202', 'card-203'])
    expect(schwebendeKopie('card-201')).toBeNull()
    expect(screen.queryByTestId('einfuegelinie')).not.toBeInTheDocument()
    await waitFor(() => expect(api.move).toHaveBeenCalledTimes(1))
  })

  it('Szenario: Wischen scrollt statt zu verschieben', () => {
    const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
    zeigen(api)

    greifen(screen.getByTestId('card-202'))
    warten(100)
    ueber(20, 20 - 30)
    const touchmove = new Event('touchmove', { cancelable: true, bubbles: true })
    fireEvent(document, touchmove)
    warten(HALTEZEIT_MS)
    loslassen()

    expect(touchmove.defaultPrevented).toBe(false)
    expect(api.move).not.toHaveBeenCalled()
    expect(api.bulkTransfer).not.toHaveBeenCalled()
    expect(screen.getByTestId('card-202')).not.toHaveAttribute('data-zieh-zustand', 'zieht')
    expect(kartenIn(20)).toEqual(['card-201', 'card-202', 'card-203'])
  })

  it('ordnet innerhalb derselben Spalte um und ruft move mit der Stelle', () => {
    const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
    zeigen(api)
    stapeln(201, 202)

    greifen(screen.getByTestId('card-203'))
    warten(HALTEZEIT_MS)
    ueber(20, 110)
    loslassen()

    expect(api.move).toHaveBeenCalledWith(203, 20, 0)
    expect(kartenIn(20)).toEqual(['card-203', 'card-201', 'card-202'])
  })

  it('ruft nichts, wenn die Karte an ihrer eigenen Stelle abgelegt wird', () => {
    const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
    zeigen(api)
    stapeln(201, 203)

    greifen(screen.getByTestId('card-202'))
    warten(HALTEZEIT_MS)
    ueber(20, 140)
    loslassen()

    expect(api.move).not.toHaveBeenCalled()
    expect(kartenIn(20)).toEqual(['card-201', 'card-202', 'card-203'])
  })

  it('stellt bei einem Fehler die alte Reihenfolge her und meldet ihn', async () => {
    const api = mkApi({ move: vi.fn().mockRejectedValue(new Error('netz')) })
    zeigen(api)
    stapeln(201, 202)

    greifen(screen.getByTestId('card-203'))
    warten(HALTEZEIT_MS)
    ueber(20, 110)
    loslassen()

    await erwarteFehlerToast('Verschieben fehlgeschlagen.')
    expect(kartenIn(20)).toEqual(['card-201', 'card-202', 'card-203'])
  })

  it('ordnet Spalten im Bearbeitungsmodus per Finger um', async () => {
    mColumns.reorder.mockResolvedValue(fuenfSpalten.columns)
    zeigen(mkApi())

    greifen(screen.getByTestId('column-header-30'))
    warten(HALTEZEIT_MS)
    expect(aufDemBoard('column-header-30')).toHaveAttribute('data-zieh-zustand', 'zieht')
    ueber(10, 300)
    loslassen()

    await waitFor(() => expect(mColumns.reorder).toHaveBeenCalledWith(1, [30, 10, 20, 40, 50]))
  })

  it('greift Spalten außerhalb des Bearbeitungsmodus nicht', () => {
    editMode.value = false
    zeigen(mkApi())

    greifen(screen.getByTestId('column-header-30'))
    warten(HALTEZEIT_MS)
    ueber(10, 300)
    loslassen()

    expect(screen.getByTestId('column-header-30')).not.toHaveAttribute('data-zieh-zustand')
    expect(mColumns.reorder).not.toHaveBeenCalled()
  })

  it('zieht im Auswahlmodus die ganze Auswahl über moveCards ans Ende der Zielspalte', async () => {
    const api = mkApi({ bulkTransfer: vi.fn().mockResolvedValue([]) })
    zeigen(api)
    stapeln(301, 302)
    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-202'))
    fireEvent.click(screen.getByTestId('card-201'))

    greifen(screen.getByTestId('card-202'))
    warten(HALTEZEIT_MS)
    ueber(30, 110)
    // Die Einfügestelle gilt nur für die Einzelkarte (E15): keine Linie beim Sammelzug.
    expect(screen.queryByTestId('einfuegelinie')).not.toBeInTheDocument()
    expect(screen.getByTestId('card-201')).toHaveAttribute('data-zieh-zustand', 'bewegt')
    loslassen()

    expect(api.bulkTransfer).toHaveBeenCalledWith([201, 202], 1, 30)
    expect(api.move).not.toHaveBeenCalled()
    await waitFor(() => expect(kartenIn(30)).toEqual(['card-301', 'card-302', 'card-201', 'card-202']))
  })

  it('greift im Auswahlmodus eine nicht ausgewählte Karte nicht', () => {
    const api = mkApi({ bulkTransfer: vi.fn().mockResolvedValue([]) })
    zeigen(api)
    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    fireEvent.click(screen.getByTestId('card-201'))

    greifen(screen.getByTestId('card-202'))
    warten(HALTEZEIT_MS)
    ueber(30, 110)
    loslassen()

    expect(api.bulkTransfer).not.toHaveBeenCalled()
    expect(api.move).not.toHaveBeenCalled()
  })

  it('bricht bei pointercancel ohne Aufruf ab', () => {
    const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
    zeigen(api)

    greifen(screen.getByTestId('card-201'))
    warten(HALTEZEIT_MS)
    ueber(30, 110)
    fireEvent.pointerCancel(window, finger)
    vi.useRealTimers()

    expect(api.move).not.toHaveBeenCalled()
    expect(screen.getByTestId('card-201')).not.toHaveAttribute('data-zieh-zustand')
    expect(screen.queryByTestId('einfuegelinie')).not.toBeInTheDocument()
    expect(schwebendeKopie('card-201')).toBeNull()
    expect(kartenIn(20)).toEqual(['card-201', 'card-202', 'card-203'])
  })
})

describe('BoardView auf Englisch', () => {
  beforeEach(() => {
    editMode.value = true
    mColumns.create.mockReset()
    mColumns.update.mockReset()
    mColumns.remove.mockReset()
    mColumns.reorder.mockReset()
    mColumns.sortByNumber.mockReset()
  })

  const rechts: Card = { ...card, id: 101, number: 2, title: 'Rechts', columnId: 20 }
  const epics = [{ id: 9, number: 5, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]

  const zeigeEn = (props: Partial<ComponentProps<typeof BoardView>> = {}) =>
    renderMitSprache(
      <SnackbarProvider>
        <BoardView board={board} initialCards={[card, rechts]} canEdit currentUserId={7} api={mkApi()} {...props} />
      </SnackbarProvider>,
      'en',
    )

  it('zeigt die Werkzeugleiste auf Englisch', () => {
    zeigeEn({ epics })

    const filter = screen.getByRole('group', { name: 'Filter cards' })
    for (const name of ['All cards', 'Mine', 'Overdue']) {
      expect(within(filter).getByRole('button', { name })).toBeInTheDocument()
    }
    const dichte = screen.getByRole('group', { name: 'Density' })
    expect(within(dichte).getByRole('button', { name: 'normal' })).toBeInTheDocument()
    expect(within(dichte).getByRole('button', { name: 'compact' })).toBeInTheDocument()
    const vorhaben = screen.getByLabelText('Epic filter')
    expect(within(vorhaben).getByRole('option', { name: 'Epics: all' })).toBeInTheDocument()
    expect(within(vorhaben).getByRole('option', { name: 'Epic: AUT – Auth' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import specification' })).toBeInTheDocument()
    expect(screen.getByLabelText('Choose Markdown file')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create new' })).toBeInTheDocument()
    expect(screen.getByText('More columns: scroll horizontally')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Select' }))
    expect(screen.getByRole('button', { name: 'End selection' })).toBeInTheDocument()
  })

  it('benennt Spaltenkopf und Belastungsskala auf Englisch', () => {
    const mitGrenze: Board = { ...board, columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: 2 }, board.columns[1]] }
    zeigeEn({ board: mitGrenze })

    expect(screen.getByRole('button', { name: 'Edit column Backlog' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Delete column Backlog' })).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Load Backlog' })).toHaveAttribute('aria-valuetext', '1 of 2')
  })

  it('meldet das Sortieren in beiden Richtungen und seinen Fehlschlag auf Englisch', async () => {
    mColumns.sortByNumber.mockResolvedValue(undefined)
    zeigeEn()

    fireEvent.click(screen.getByRole('button', { name: 'Sort column Backlog by number ascending' }))
    expect(await screen.findByText('Column Backlog sorted ascending')).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Sort column Backlog by number descending' }))
    expect(await screen.findByText('Column Backlog sorted descending')).toBeInTheDocument()

    mColumns.sortByNumber.mockRejectedValue(new Error('netz'))
    fireEvent.click(await screen.findByRole('button', { name: 'Sort column Backlog by number ascending' }))
    expect(await screen.findByText('Sorting failed.')).toBeInTheDocument()
  })

  it('legt Spalten an und bearbeitet sie auf Englisch', async () => {
    mColumns.create.mockResolvedValue({ id: 30, name: 'Test', position: 2, wipLimit: null })
    zeigeEn()

    fireEvent.click(screen.getByRole('button', { name: 'Column' }))
    expect(screen.getByRole('heading', { name: 'New column' })).toBeInTheDocument()
    expect(screen.getByText('WIP limit (optional)', { selector: 'label' })).toBeInTheDocument()
    expect(screen.getByLabelText('WIP limit')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Column name'), { target: { value: 'Test' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Column created.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    mColumns.update.mockRejectedValueOnce(new Error('netz')).mockResolvedValueOnce({ id: 10, name: 'Neu', position: 0, wipLimit: null })
    fireEvent.click(screen.getByRole('button', { name: 'Edit column Backlog' }))
    expect(screen.getByRole('heading', { name: 'Edit column' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Saving the column failed.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Column saved.')).toBeInTheDocument()
  })

  it('löscht eine Spalte mit Rückfrage und meldet beide Fehlschläge auf Englisch', async () => {
    mColumns.remove
      .mockRejectedValueOnce(new ApiError(409, 'Conflict'))
      .mockRejectedValueOnce(new Error('netz'))
      .mockResolvedValueOnce(undefined)
    zeigeEn()

    fireEvent.click(screen.getByRole('button', { name: 'Delete column Backlog' }))
    expect(screen.getByRole('heading', { name: 'Delete column?' })).toBeInTheDocument()
    expect(screen.getByText('The column “Backlog” will be deleted.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    expect(await screen.findByText('The column still contains cards and cannot be deleted.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    expect(await screen.findByText('Deleting failed.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))
    expect(await screen.findByText('Column deleted.')).toBeInTheDocument()
  })

  it('meldet ein gescheitertes Umsortieren der Spalten auf Englisch', async () => {
    mColumns.reorder.mockRejectedValue(new Error('netz'))
    zeigeEn()

    fireEvent.dragStart(screen.getByTestId('column-header-20'))
    fireEvent.drop(screen.getByTestId('column-header-10'))

    expect(await screen.findByText('Reordering columns failed.')).toBeInTheDocument()
  })

  it('zeigt das Kartenmenü auf Englisch und meldet seine Fehlschläge', async () => {
    const api = mkApi({
      archive: vi.fn().mockRejectedValue(new Error('netz')),
      get: vi.fn().mockRejectedValue(new Error('netz')),
      move: vi.fn().mockRejectedValue(new Error('netz')),
      bulkDelete: vi.fn().mockRejectedValue(new Error('netz')),
    })
    zeigeEn({ api, canTransfer: true })

    fireEvent.click(screen.getByRole('button', { name: 'Menu Aufgabe' }))
    for (const name of ['Edit', 'Duplicate', 'Archive', 'Move…', 'Move right', 'Delete']) {
      expect(screen.getByRole('menuitem', { name })).toBeInTheDocument()
    }
    fireEvent.click(screen.getByRole('menuitem', { name: 'Archive' }))
    expect(await screen.findByText('Archiving failed.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Menu Aufgabe' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Duplicate' }))
    expect(await screen.findByText('The card could not be loaded.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Menu Rechts' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Move left' }))
    expect(await screen.findByText('Moving failed.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Menu Aufgabe' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Delete' }))
    expect(screen.getByRole('heading', { name: 'Move to trash?' })).toBeInTheDocument()
    expect(
      screen.getByText('The card will be moved to the trash. From there it can be restored or deleted permanently.'),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Move to trash' }))
    expect(await screen.findByText('Moving to the trash failed.')).toBeInTheDocument()
  })

  it('zeigt Auswahl, Massenaktionen und ihre Rückfragen auf Englisch', async () => {
    const api = mkApi({
      bulkArchive: vi.fn().mockRejectedValue(new Error('netz')),
      bulkLabels: vi.fn().mockRejectedValue(new Error('netz')),
    })
    const labels = [{ id: 3, boardId: 1, name: 'Fehler', color: '#ff0000', countOnEpicTile: false }]
    zeigeEn({ api, boardLabels: labels, initialCards: [card, { ...card, id: 102, number: 3, title: 'Zweite' }, rechts] })

    fireEvent.click(screen.getByRole('button', { name: 'Select' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select all cards in Backlog' }))
    expect(screen.getByRole('checkbox', { name: 'Clear selection in Backlog' })).toBeChecked()
    expect(screen.getByRole('region', { name: 'Bulk actions' })).toHaveTextContent('2 selected')

    fireEvent.click(screen.getByRole('button', { name: 'Archive' }))
    expect(screen.getByRole('heading', { name: 'Archive cards?' })).toBeInTheDocument()
    expect(
      screen.getByText('2 cards will be archived. They disappear from the board but are kept and can be restored one by one.'),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'Move to trash' }))
    expect(
      screen.getByText('2 cards will be moved to the trash. From there they can be restored or deleted permanently.'),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Fehler — no selected card' }))
    expect(await screen.findByText('Setting labels failed.')).toBeInTheDocument()
    fireEvent.keyDown(screen.getByRole('menu'), { key: 'Escape' })

    fireEvent.click(screen.getByRole('checkbox', { name: 'Select card Zweite' }))
    fireEvent.click(screen.getByRole('button', { name: 'Archive' }))
    expect(
      screen.getByText('The selected card will be archived. They disappear from the board but are kept and can be restored one by one.'),
    ).toBeInTheDocument()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archive' }))
    expect(await screen.findByText('Archiving failed.')).toBeInTheDocument()
  })

  it('nennt die Sperrgründe der Label-Taste auf Englisch', () => {
    const vorhaben: Card = { ...card, id: 103, number: 4, title: 'Vorhaben', type: 'EPIC' }
    const { unmount } = zeigeEn({ initialCards: [card] })
    fireEvent.click(screen.getByRole('button', { name: 'Select' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select card Aufgabe' }))
    expect(screen.getByRole('button', { name: 'Labels — The board has no labels' })).toBeDisabled()
    unmount()

    zeigeEn({ initialCards: [vorhaben] })
    fireEvent.click(screen.getByRole('button', { name: 'Select' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select card Vorhaben' }))
    expect(screen.getByRole('button', { name: 'Labels — Epics carry no labels' })).toBeDisabled()
  })

  it('meldet ausgeblendete Karten auf Englisch', () => {
    const imVorhaben: Card = { ...card, parentId: 9 }
    const zweite: Card = { ...card, id: 102, number: 3, title: 'Zweite', parentId: 9 }
    const viele = [{ ...epics[0], memberNumbers: [1, 3], rootNumbers: [1, 3] }]
    const { unmount } = zeigeEn({ epics: viele, hiddenEpics: new Set([9]), initialCards: [imVorhaben, zweite] })
    expect(screen.getByRole('button', { name: '2 cards hidden, show' })).toHaveTextContent('2 hidden')
    unmount()

    zeigeEn({ epics: [{ ...epics[0], memberNumbers: [1], rootNumbers: [1] }], hiddenEpics: new Set([9]), initialCards: [imVorhaben] })
    expect(screen.getByRole('button', { name: '1 card hidden, show' })).toHaveTextContent('1 hidden')
  })

  it('liest eine Spezifikation ein und meldet die angelegten Karten auf Englisch', async () => {
    const angelegt = (n: number) => Array.from({ length: n }, (_, i) => ({ ...card, id: 300 + i, number: 10 + i, title: `Neu ${i}` }))
    const api = mkApi({ createBatch: vi.fn().mockResolvedValueOnce(angelegt(2)).mockResolvedValueOnce(angelegt(1)) })
    zeigeEn({ api })

    const waehlen = (inhalt: string) => {
      const input = screen.getByLabelText('Choose Markdown file')
      Object.defineProperty(input, 'files', { value: [new File([inhalt], 'spec.md', { type: 'text/markdown' })], configurable: true })
      fireEvent.change(input)
    }

    waehlen('## Eins\nA\n\n## Zwei\nB')
    fireEvent.click(await screen.findByRole('button', { name: 'Create 2 cards' }))
    expect(await screen.findByText('2 cards created.')).toBeInTheDocument()

    waehlen('## Eins\nA')
    fireEvent.click(await screen.findByRole('button', { name: 'Create 1 card' }))
    expect(await screen.findByText('1 card created.')).toBeInTheDocument()
  })

  it('meldet eine unlesbare Spezifikationsdatei auf Englisch', async () => {
    zeigeEn()

    class FailingReader {
      onerror: (() => void) | null = null
      readAsText(): void {
        this.onerror?.()
      }
    }
    vi.stubGlobal('FileReader', FailingReader)
    const input = screen.getByLabelText('Choose Markdown file')
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'spec.md', { type: 'text/markdown' })], configurable: true })
    fireEvent.change(input)

    expect(await screen.findByText('The file could not be read.')).toBeInTheDocument()
    vi.unstubAllGlobals()
  })
})

describe('BoardView — Gegenprobe am Desktop (Plan #1707, E2)', () => {
  beforeEach(() => {
    kitNamen.geladen = true
  })

  afterEach(() => {
    kitNamen.geladen = false
  })

  it('Szenario: Desktop bleibt, wie er ist', async () => {
    // Angenommen ein Mensch arbeitet am Desktop mit der Maus in einem Browserfenster von 1024 px Breite
    const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
    const klaeren = { id: 7, boardId: 1, name: 'kit:clarify', color: '#ff0000', countOnEpicTile: false }
    render(
      <TabletQuerProvider>
        <BoardView board={board} initialCards={[{ ...card, labels: [7] }]} canEdit boardLabels={[klaeren]} api={api} />
      </TabletQuerProvider>,
    )

    // Wenn er mit der Maus über ein Label fährt
    await userEvent.hover(screen.getByText('kit:clarify'))
    // Dann erscheint die Erklärung beim Darüberfahren
    expect(await screen.findByRole('tooltip')).toHaveTextContent(de.LABEL_ERKLAERUNG.SACHEN.KLAEREN.bedeutung)
    await userEvent.unhover(screen.getByText('kit:clarify'))

    // Und die Spalten haben ihre bisherige Breite
    for (const id of [10, 20]) {
      expect(cssRegel(screen.getByTestId(`column-${id}`))).toContain('min-width: 230px')
    }

    // Wenn er eine Karte in eine andere Spalte zieht, folgt sie der Maus wie bisher (HTML5-Drag)
    const karte = screen.getByTestId('card-100')
    expect(karte).toHaveAttribute('draggable', 'true')
    const setData = vi.fn()
    fireEvent.dragStart(karte, { dataTransfer: { setData } })
    fireEvent.dragOver(screen.getByTestId('column-20'), { dataTransfer: {} })
    dropOnColumn(20, 100)

    expect(setData).toHaveBeenCalledWith('text/plain', '100')
    await waitFor(() => expect(api.move).toHaveBeenCalledWith(100, 20, 0))
  })
})

describe('BoardView — Knopf kit:nightrun im Kopf der Ready-Spalte (Issue #1708)', () => {
  beforeEach(() => {
    kitNamen.geladen = true
  })

  afterEach(() => {
    kitNamen.geladen = false
  })

  const readyBoard: Board = {
    ...board,
    columns: [
      { id: 10, name: 'Backlog', position: 0, wipLimit: null },
      { id: 30, name: 'Ready', position: 1, wipLimit: null },
      { id: 20, name: 'Done', position: 2, wipLimit: null },
    ],
  }
  const nightrun = { id: 9, boardId: 1, name: 'kit:nightrun', color: '#00ff00', countOnEpicTile: false }
  const paket = (id: number, title: string, over: Partial<Card> = {}): Card =>
    ({ ...card, id, number: id, title, columnId: 30, positionInColumn: id, ...over })
  const knopfName = de.BOARD.NACHTLAUF_MARKIEREN('kit:nightrun')

  it('erscheint nur an der Ready-Spalte und nur, wenn das Board das Label führt', () => {
    const { unmount } = render(
      <BoardView board={readyBoard} initialCards={[paket(1, 'Eins')]} canEdit boardLabels={[nightrun]} api={mkApi()} />,
    )
    expect(screen.getAllByRole('button', { name: knopfName })).toHaveLength(1)
    expect(within(screen.getByTestId('column-header-30')).getByRole('button', { name: knopfName })).toHaveTextContent('kit:nightrun')
    unmount()

    render(<BoardView board={readyBoard} initialCards={[paket(1, 'Eins')]} canEdit boardLabels={[]} api={mkApi()} />)
    expect(screen.queryByRole('button', { name: knopfName })).not.toBeInTheDocument()
  })

  it('entfällt ohne Bearbeitungsrecht', () => {
    render(<BoardView board={readyBoard} initialCards={[paket(1, 'Eins')]} canEdit={false} boardLabels={[nightrun]} api={mkApi()} />)
    expect(screen.queryByRole('button', { name: knopfName })).not.toBeInTheDocument()
  })

  it('setzt das Label an die sichtbaren Ready-Karten ohne Vorhaben, Menschenschritte und Karten mit dem Label', async () => {
    const eins = paket(1, '[Task] Eins')
    const zwei = paket(2, 'Zwei')
    const vorhaben = paket(3, 'Vorhaben', { type: 'EPIC' })
    const mensch = paket(4, '[Human] Schritt')
    const schon = paket(5, 'Schon', { labels: [9] })
    const meins = paket(6, 'Fremd', { assignees: [] })
    const anderswo = { ...paket(7, 'Backlog-Karte'), columnId: 10 }
    const api = mkApi({
      bulkLabels: vi.fn().mockResolvedValue([{ ...eins, labels: [9] }, { ...zwei, labels: [9] }]),
    })
    render(
      <BoardView
        board={readyBoard}
        initialCards={[eins, zwei, vorhaben, mensch, schon, meins, anderswo]}
        canEdit
        boardLabels={[nightrun]}
        api={api}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: knopfName }))

    await waitFor(() => expect(api.bulkLabels).toHaveBeenCalledWith([1, 2, 6], 9, 'ADD'))
    // Danach tragen die Karten das Label in der Anzeige.
    await waitFor(() => expect(within(screen.getByTestId('card-1')).getByText('kit:nightrun')).toBeInTheDocument())
    expect(within(screen.getByTestId('card-2')).getByText('kit:nightrun')).toBeInTheDocument()
  })

  it('lässt Karten aus, die der Filter der Werkzeugleiste verdeckt', async () => {
    const eigene = paket(1, 'Eigene', { assignees: [42] })
    const fremde = paket(2, 'Fremde')
    const api = mkApi()
    render(
      <BoardView board={readyBoard} initialCards={[eigene, fremde]} canEdit boardLabels={[nightrun]} currentUserId={42} api={api} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Meine' }))
    fireEvent.click(screen.getByRole('button', { name: knopfName }))

    await waitFor(() => expect(api.bulkLabels).toHaveBeenCalledWith([1], 9, 'ADD'))
  })

  it('ist gesperrt und nennt den Grund, wenn alle Karten das Label schon tragen', () => {
    render(
      <BoardView
        board={readyBoard}
        initialCards={[paket(1, 'Eins', { labels: [9] }), paket(2, '[Human] Schritt')]}
        canEdit
        boardLabels={[nightrun]}
        api={mkApi()}
      />,
    )

    const grund = de.BOARD.NACHTLAUF_ALLE_MARKIERT('kit:nightrun')
    const knopf = screen.getByRole('button', { name: grund })
    expect(knopf).toBeDisabled()
    // Der Tooltip hängt an der Hülle, weil der gesperrte Knopf keine Zeigerereignisse erhält.
    expect(screen.getByTitle(grund)).toContainElement(knopf)
  })

  it('ist gesperrt und nennt den Grund, wenn keine Karte das Label brauchen kann', () => {
    render(
      <BoardView board={readyBoard} initialCards={[paket(3, 'Vorhaben', { type: 'EPIC' })]} canEdit boardLabels={[nightrun]} api={mkApi()} />,
    )

    expect(screen.getByRole('button', { name: de.BOARD.NACHTLAUF_KEINE_KARTE('kit:nightrun') })).toBeDisabled()
  })

  it('meldet einen Fehler der Schnittstelle', async () => {
    const api = mkApi({ bulkLabels: vi.fn().mockRejectedValue(new Error('netz')) })
    render(
      <BoardView board={readyBoard} initialCards={[paket(1, 'Eins')]} canEdit boardLabels={[nightrun]} api={api} />,
      { wrapper: SnackbarProvider },
    )

    fireEvent.click(screen.getByRole('button', { name: knopfName }))

    await erwarteFehlerToast('Labels setzen fehlgeschlagen.')
  })

  it('nennt Knopf und Sperrgründe auf Englisch', () => {
    const { unmount } = renderMitSprache(
      <BoardView board={readyBoard} initialCards={[paket(1, 'One')]} canEdit boardLabels={[nightrun]} api={mkApi()} />,
      'en',
    )
    expect(screen.getByRole('button', { name: 'Set kit:nightrun on all cards in Ready' })).toBeEnabled()
    unmount()

    const { unmount: weg } = renderMitSprache(
      <BoardView board={readyBoard} initialCards={[paket(1, 'One', { labels: [9] })]} canEdit boardLabels={[nightrun]} api={mkApi()} />,
      'en',
    )
    expect(screen.getByRole('button', { name: 'All cards in Ready already carry kit:nightrun' })).toBeDisabled()
    weg()

    renderMitSprache(<BoardView board={readyBoard} initialCards={[]} canEdit boardLabels={[nightrun]} api={mkApi()} />, 'en')
    expect(screen.getByRole('button', { name: 'No card in Ready can take kit:nightrun' })).toBeDisabled()
  })

  it('schickt bei einem Doppelklick nur einen Aufruf los', async () => {
    let antwort: (cards: Card[]) => void = () => {}
    const eins = paket(1, 'Eins')
    const api = mkApi({ bulkLabels: vi.fn(() => new Promise<Card[]>((resolve) => { antwort = resolve })) })
    render(<BoardView board={readyBoard} initialCards={[eins]} canEdit boardLabels={[nightrun]} api={api} />)

    const knopf = screen.getByRole('button', { name: knopfName })
    fireEvent.click(knopf)
    fireEvent.click(knopf)

    expect(api.bulkLabels).toHaveBeenCalledTimes(1)
    expect(knopf).toBeDisabled()
    await act(async () => antwort([{ ...eins, labels: [9] }]))
  })
})

describe('Vorhaben über die Mehrfachauswahl zuordnen (Issue #1869)', () => {
  beforeEach(() => {
    editMode.value = true
  })

  const mkEpic = (id: number, title: string, shortcode: string, memberNumbers: number[] = [], rootNumbers = memberNumbers): Epic => ({
    id, number: id, title, description: null, shortcode, done: 0, total: memberNumbers.length,
    memberNumbers, rootNumbers, requirementCardNumber: null,
  })
  const karte = (n: number, over: Partial<Card> = {}): Card => ({
    ...card, id: 1000 + n, number: n, title: `Karte ${n}`, positionInColumn: n, ...over,
  })
  const zahlen = (anzahl: number) => Array.from({ length: anzahl }, (_, i) => i + 1)
  const ergebnis = (changedCardIds: number[], overwritten = 0, skipped = 0) => ({ changedCardIds, overwritten, skipped })
  const abrechnung = mkEpic(9, 'Abrechnung', 'ABR')
  const altlasten = (members: number[] = [], roots = members) => mkEpic(8, 'Altlasten', 'ALT', members, roots)
  const nice = (members: number[] = []) => mkEpic(5, 'Nice to have', 'NTH', members)
  const vorhabenApi = (bulkAssign = vi.fn().mockResolvedValue(ergebnis([]))) => ({ create: vi.fn(), bulkAssign })
  const GRUND_VORHABEN = 'ein Vorhaben lässt sich keinem Vorhaben zuordnen'
  const GRUND_KEINE = 'das Board hat kein wählbares Vorhaben'

  /**
   * Hält Vorhaben-Liste und Ausblendung so, wie `BoardPage` es tut: `onEpicsChanged` lädt die
   * Liste neu — hier mit `nachLaden`, der Antwort des Servers nach der Zuordnung.
   */
  const KEINE_AUSGEBLENDET: ReadonlySet<number> = new Set()

  function BoardMitVorhaben({
    start, nachLaden = start, hidden = KEINE_AUSGEBLENDET, onEpicsChanged, ...rest
  }: { start: Epic[]; nachLaden?: Epic[]; hidden?: ReadonlySet<number> } & Omit<ComponentProps<typeof BoardView>, 'board' | 'canEdit' | 'epics' | 'hiddenEpics'>) {
    const [epics, setEpics] = useState(start)
    return (
      <BoardView
        board={board} canEdit epics={epics} hiddenEpics={hidden}
        onEpicsChanged={() => { setEpics(nachLaden); onEpicsChanged?.() }} {...rest}
      />
    )
  }

  const zeige = (props: ComponentProps<typeof BoardMitVorhaben>) =>
    render(<BoardMitVorhaben {...props} />, { wrapper: SnackbarProvider })

  const waehle = (...nummern: number[]) => {
    fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
    for (const n of nummern) fireEvent.click(screen.getByTestId(`card-${1000 + n}`))
  }
  const oeffneZuordnen = (punkt: string) => {
    fireEvent.click(screen.getByRole('button', { name: 'Vorhaben zuordnen' }))
    fireEvent.click(screen.getByRole('menuitem', { name: punkt }))
  }
  const rueckfrage = () => screen.getByRole('dialog', { name: 'Vorhaben zuordnen?' })
  const bestaetige = () => fireEvent.click(within(rueckfrage()).getByRole('button', { name: 'Zuordnen' }))

  test('Szenario: Zwanzig lose Karten in ein ausgeblendetes Sammel-Vorhaben', async () => {
    const nummern = zahlen(20)
    const karten = nummern.map((n) => karte(n))
    const ids = karten.map((c) => c.id)
    const epicsApi = vorhabenApi(vi.fn().mockResolvedValue(ergebnis(ids)))
    zeige({
      initialCards: karten, start: [abrechnung, nice()], nachLaden: [abrechnung, nice(nummern)], hidden: new Set([5]),
      api: mkApi(), epicsApi,
    })

    waehle(...nummern)
    oeffneZuordnen('Nice to have (ausgeblendet)')
    expect(rueckfrage()).toHaveTextContent('20 Karten werden dem Vorhaben „Nice to have“ zugeordnet.')
    expect(rueckfrage()).not.toHaveTextContent('anderen Vorhaben')
    bestaetige()

    await waitFor(() => expect(epicsApi.bulkAssign).toHaveBeenCalledWith(ids, 5))
    expect(
      await screen.findByText(
        '20 Karten dem ausgeblendeten Vorhaben „Nice to have“ zugeordnet. Sie sind auf diesem Board jetzt verdeckt — „Ausgeblendete zeigen“ blendet sie wieder ein.',
      ),
    ).toBeInTheDocument()
    // Die neu geladene Vorhaben-Liste führt sie als Mitglieder — damit sind sie verdeckt.
    expect(screen.queryAllByTestId(/^card-\d+$/)).toHaveLength(0)
    expect(
      within(screen.getByTestId('column-header-10')).getByRole('button', { name: '20 Karten ausgeblendet, einblenden' }),
    ).toBeInTheDocument()
  })

  test('Szenario: Eine Auswahl enthält Karten mit bestehender Zuordnung', async () => {
    const nummern = zahlen(7)
    const karten = nummern.map((n) => karte(n, n <= 3 ? { parentId: 8 } : {}))
    const epicsApi = vorhabenApi(vi.fn().mockResolvedValue(ergebnis(karten.map((c) => c.id), 3)))
    zeige({
      initialCards: karten,
      start: [altlasten([1, 2, 3]), abrechnung],
      nachLaden: [altlasten(), mkEpic(9, 'Abrechnung', 'ABR', nummern)],
      api: mkApi(), epicsApi,
    })

    waehle(...nummern)
    oeffneZuordnen('Abrechnung')
    expect(rueckfrage()).toHaveTextContent('7 Karten werden dem Vorhaben „Abrechnung“ zugeordnet.')
    expect(rueckfrage()).toHaveTextContent(
      'Davon gehören 3 Karten bereits zu einem anderen Vorhaben; diese Zuordnung wird ersetzt.',
    )
    bestaetige()

    // Genau so viele überschrieben wie angekündigt — kein Zusatz.
    expect(await screen.findByText('7 Karten dem Vorhaben „Abrechnung“ zugeordnet.')).toBeInTheDocument()
    for (const n of nummern) {
      expect(within(screen.getByTestId(`card-${1000 + n}`)).getByText('ABR')).toBeInTheDocument()
      expect(within(screen.getByTestId(`card-${1000 + n}`)).queryByText('ALT')).not.toBeInTheDocument()
    }
  })

  test('Szenario: Eine Karte mit geerbtem Vorhaben behält ihre Anzeige', async () => {
    // Karte #1 gehört über ihre Herkunft (Wurzel #50) zu „Altlasten", unmittelbar zu keinem.
    const geerbt = karte(1)
    const onEpicsChanged = vi.fn()
    const epicsApi = vorhabenApi(vi.fn().mockResolvedValue(ergebnis([geerbt.id])))
    zeige({
      initialCards: [geerbt],
      start: [altlasten([1], [50]), abrechnung],
      nachLaden: [altlasten([1], [50]), mkEpic(9, 'Abrechnung', 'ABR', [1], [1])],
      api: mkApi(), epicsApi, onEpicsChanged,
    })
    expect(within(screen.getByTestId('card-1001')).getByText('ALT')).toBeInTheDocument()

    waehle(1)
    oeffneZuordnen('Abrechnung')
    expect(rueckfrage()).toHaveTextContent('Die ausgewählte Karte wird dem Vorhaben „Abrechnung“ zugeordnet.')
    expect(rueckfrage()).not.toHaveTextContent('anderen Vorhaben')
    bestaetige()

    expect(await screen.findByText('1 Karte dem Vorhaben „Abrechnung“ zugeordnet.')).toBeInTheDocument()
    expect(onEpicsChanged).toHaveBeenCalled()
    // Die geerbte Zugehörigkeit hat Vorrang in der Anzeige.
    expect(within(screen.getByTestId('card-1001')).getByText('ALT')).toBeInTheDocument()
    expect(within(screen.getByTestId('card-1001')).queryByText('ABR')).not.toBeInTheDocument()
    // Keine zusätzliche Rückfrage, keine Warnung: nur die eine Erfolgsmeldung.
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getAllByRole('alert')).toHaveLength(1)
  })

  test('Szenario: Die Auswahl enthält ein Vorhaben', async () => {
    const karten = [...zahlen(4).map((n) => karte(n)), karte(5, { type: 'EPIC', title: 'Ein Vorhaben' })]
    const epicsApi = vorhabenApi()
    zeige({ initialCards: karten, start: [abrechnung], api: mkApi(), epicsApi })

    waehle(1, 2, 3, 4, 5)

    const taste = screen.getByRole('button', { name: `Vorhaben zuordnen — ${GRUND_VORHABEN}` })
    expect(taste).toBeDisabled()
    fireEvent.click(taste)
    fireEvent.mouseOver(screen.getByLabelText(GRUND_VORHABEN))
    expect(await screen.findByRole('tooltip')).toHaveTextContent(GRUND_VORHABEN)
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(epicsApi.bulkAssign).not.toHaveBeenCalled()
    for (const n of zahlen(4)) expect(screen.getByTestId(`card-${1000 + n}`)).toBeInTheDocument()
  })

  test('Szenario: Das Board hat kein wählbares Vorhaben', async () => {
    const epicsApi = vorhabenApi()
    zeige({ initialCards: [karte(1), karte(2)], start: [], api: mkApi(), epicsApi })

    waehle(1, 2)

    const taste = screen.getByRole('button', { name: `Vorhaben zuordnen — ${GRUND_KEINE}` })
    expect(taste).toBeDisabled()
    fireEvent.click(taste)
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    fireEvent.mouseOver(screen.getByLabelText(GRUND_KEINE))
    expect(await screen.findByRole('tooltip')).toHaveTextContent(GRUND_KEINE)
    expect(epicsApi.bulkAssign).not.toHaveBeenCalled()
  })

  it('nennt beide Sperrgründe zugleich, mit „; " verbunden', () => {
    zeige({ initialCards: [karte(1), karte(2, { type: 'EPIC' })], start: [], api: mkApi(), epicsApi: vorhabenApi() })

    waehle(1, 2)

    expect(
      screen.getByRole('button', { name: `Vorhaben zuordnen — ${GRUND_VORHABEN}; ${GRUND_KEINE}` }),
    ).toBeDisabled()
  })

  it('bietet alle Vorhaben der vollen Liste an, ausgeblendete gekennzeichnet, in der Reihenfolge des Servers', () => {
    zeige({ initialCards: [karte(1)], start: [nice(), abrechnung, altlasten()], hidden: new Set([5]), api: mkApi(), epicsApi: vorhabenApi() })

    waehle(1)
    fireEvent.click(screen.getByRole('button', { name: 'Vorhaben zuordnen' }))

    expect(screen.getAllByRole('menuitem').map((m) => m.textContent)).toEqual([
      'Nice to have (ausgeblendet)', 'Abrechnung', 'Altlasten',
    ])
  })

  it('zählt als überschrieben nur Karten mit einer anderen unmittelbaren Zuordnung', () => {
    // #1 schon im Ziel, #2 unmittelbar in „Altlasten", #3 ohne Vorhaben, #4 nur geerbt.
    const karten = [karte(1, { parentId: 9 }), karte(2, { parentId: 8 }), karte(3), karte(4)]
    zeige({ initialCards: karten, start: [altlasten([2, 4], [2]), abrechnung], api: mkApi(), epicsApi: vorhabenApi() })

    waehle(1, 2, 3, 4)
    oeffneZuordnen('Abrechnung')

    expect(rueckfrage()).toHaveTextContent('4 Karten werden dem Vorhaben „Abrechnung“ zugeordnet.')
    expect(rueckfrage()).toHaveTextContent(
      'Davon gehört 1 Karte bereits zu einem anderen Vorhaben; diese Zuordnung wird ersetzt.',
    )
  })

  it('schließt die Rückfrage beim Abbrechen, ohne zuzuordnen', async () => {
    const epicsApi = vorhabenApi()
    zeige({ initialCards: [karte(1)], start: [abrechnung], api: mkApi(), epicsApi })

    waehle(1)
    oeffneZuordnen('Abrechnung')
    fireEvent.click(within(rueckfrage()).getByRole('button', { name: 'Abbrechen' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(epicsApi.bulkAssign).not.toHaveBeenCalled()
    expect(screen.getByText('1 ausgewählt')).toBeInTheDocument()
  })

  it('schließt die Rückfrage mit Escape, ohne zuzuordnen', async () => {
    const epicsApi = vorhabenApi()
    zeige({ initialCards: [karte(1)], start: [abrechnung], api: mkApi(), epicsApi })

    waehle(1)
    oeffneZuordnen('Abrechnung')
    fireEvent.keyDown(rueckfrage(), { key: 'Escape' })

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(epicsApi.bulkAssign).not.toHaveBeenCalled()
  })

  describe('Höchstzahl je Zug', () => {
    const MELDUNG_201 = 'Höchstens 200 Karten in einem Zug — gewählt sind 201'

    it('lehnt beim Wählen des Vorhabens mehr als 200 Karten ab, ohne Rückfrage und ohne Aufruf', async () => {
      const karten = zahlen(201).map((n) => karte(n))
      const epicsApi = vorhabenApi()
      zeige({ initialCards: karten, start: [abrechnung], api: mkApi(), epicsApi })

      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      fireEvent.click(screen.getByRole('checkbox', { name: 'Alle Karten in Backlog auswählen' }))
      oeffneZuordnen('Abrechnung')

      await erwarteFehlerToast(MELDUNG_201)
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(epicsApi.bulkAssign).not.toHaveBeenCalled()
      expect(screen.getByText('201 ausgewählt')).toBeInTheDocument()
    })

    it('prüft beim Bestätigen erneut, gegen die wirksame Auswahl dieses Moments', async () => {
      // 200 überfällige Karten und eine ohne Frist: Unter „Überfällig" sind 200 wirksam, die
      // Rückfrage öffnet; zurück auf „Alle Karten" sind es beim Bestätigen 201.
      const gestern = new Date(Date.now() - 86_400_000).toISOString()
      const karten = zahlen(201).map((n) => karte(n, n <= 200 ? { dueDate: gestern } : {}))
      const epicsApi = vorhabenApi()
      zeige({ initialCards: karten, start: [abrechnung], api: mkApi(), epicsApi })

      fireEvent.click(screen.getByRole('button', { name: 'Auswählen' }))
      fireEvent.click(screen.getByRole('checkbox', { name: 'Alle Karten in Backlog auswählen' }))
      fireEvent.click(screen.getByRole('button', { name: /^Überfällig/ }))
      oeffneZuordnen('Abrechnung')
      expect(rueckfrage()).toHaveTextContent('200 Karten werden dem Vorhaben „Abrechnung“ zugeordnet.')
      fireEvent.click(screen.getByRole('button', { name: 'Alle Karten', hidden: true }))
      bestaetige()

      await erwarteFehlerToast(MELDUNG_201)
      expect(epicsApi.bulkAssign).not.toHaveBeenCalled()
    })
  })

  it('sendet beim Bestätigen nur die Karten, die der Filter in diesem Moment zeigt', async () => {
    const gestern = new Date(Date.now() - 86_400_000).toISOString()
    const karten = [karte(1, { dueDate: gestern }), karte(2), karte(3, { dueDate: gestern })]
    const epicsApi = vorhabenApi(vi.fn().mockResolvedValue(ergebnis([1001, 1003])))
    zeige({ initialCards: karten, start: [abrechnung], api: mkApi(), epicsApi })

    waehle(1, 2, 3)
    oeffneZuordnen('Abrechnung')
    expect(rueckfrage()).toHaveTextContent('3 Karten werden dem Vorhaben „Abrechnung“ zugeordnet.')
    fireEvent.click(screen.getByRole('button', { name: /^Überfällig/, hidden: true }))
    bestaetige()

    await waitFor(() => expect(epicsApi.bulkAssign).toHaveBeenCalledWith([1001, 1003], 9))
  })

  it('lässt bei einem Fehler Karten, Vorhaben und Auswahl unverändert und nennt den Grund', async () => {
    const onEpicsChanged = vi.fn()
    const onCardsChanged = vi.fn()
    const epicsApi = vorhabenApi(vi.fn().mockRejectedValue(serverfehler('Keine Berechtigung.')))
    zeige({
      initialCards: [karte(1, { parentId: 8 }), karte(2)], start: [altlasten([1]), abrechnung],
      api: mkApi(), epicsApi, onEpicsChanged, onCardsChanged,
    })

    waehle(1, 2)
    oeffneZuordnen('Abrechnung')
    bestaetige()

    await erwarteFehlerToast('Keine Berechtigung.')
    expect(onEpicsChanged).not.toHaveBeenCalled()
    expect(onCardsChanged).not.toHaveBeenCalled()
    expect(screen.getByText('2 ausgewählt')).toBeInTheDocument()
    // Die Zuordnung ist unverändert: dieselbe Rückfrage nennt weiter eine überschriebene Karte.
    oeffneZuordnen('Abrechnung')
    expect(rueckfrage()).toHaveTextContent('Davon gehört 1 Karte bereits zu einem anderen Vorhaben')
  })

  it('meldet einen Fehler ohne Text vom Server mit dem eigenen Wortlaut', async () => {
    const epicsApi = vorhabenApi(vi.fn().mockRejectedValue(new Error('Netz weg')))
    zeige({ initialCards: [karte(1)], start: [abrechnung], api: mkApi(), epicsApi })

    waehle(1)
    oeffneZuordnen('Abrechnung')
    bestaetige()

    await erwarteFehlerToast('Vorhaben zuordnen fehlgeschlagen.')
  })

  it('setzt nach dem Erfolg die Zuordnung der geänderten Karten, meldet beide Änderungen und beendet die Auswahl', async () => {
    const onEpicsChanged = vi.fn()
    const onCardsChanged = vi.fn()
    // Karte #3 ist nicht in der Antwort (schon zugeordnet oder verschwunden) und behält ihren Stand.
    const epicsApi = vorhabenApi(vi.fn().mockResolvedValue(ergebnis([1001, 1002])))
    zeige({
      initialCards: [karte(1), karte(2), karte(3)], start: [altlasten(), abrechnung],
      api: mkApi(), epicsApi, onEpicsChanged, onCardsChanged,
    })

    waehle(1, 2, 3)
    oeffneZuordnen('Abrechnung')
    bestaetige()

    expect(await screen.findByText('2 Karten dem Vorhaben „Abrechnung“ zugeordnet.')).toBeInTheDocument()
    expect(onEpicsChanged).toHaveBeenCalledTimes(1)
    expect(onCardsChanged).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Auswählen' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Massenaktionen' })).not.toBeInTheDocument()

    // `parentId` ist gesetzt: Die nächste Rückfrage nach „Altlasten" zählt #1 und #2 als überschrieben.
    waehle(1, 2, 3)
    oeffneZuordnen('Altlasten')
    expect(rueckfrage()).toHaveTextContent('Davon gehören 2 Karten bereits zu einem anderen Vorhaben')
  })

  it('nennt mehr überschriebene als angekündigt und die übergangenen Karten', async () => {
    // Angekündigt war eine überschriebene Zuordnung; bei der Ausführung waren es drei.
    const epicsApi = vorhabenApi(vi.fn().mockResolvedValue(ergebnis([1001, 1002, 1003], 3, 2)))
    zeige({
      initialCards: [karte(1, { parentId: 8 }), karte(2), karte(3), karte(4), karte(5)],
      start: [altlasten([1]), abrechnung], api: mkApi(), epicsApi,
    })

    waehle(1, 2, 3, 4, 5)
    oeffneZuordnen('Abrechnung')
    expect(rueckfrage()).toHaveTextContent('Davon gehört 1 Karte bereits zu einem anderen Vorhaben')
    bestaetige()

    expect(
      await screen.findByText(
        '3 Karten dem Vorhaben „Abrechnung“ zugeordnet. Davon hatten 3 bisher ein anderes Vorhaben. 2 ausgewählte Karten gab es nicht mehr auf diesem Board; sie blieben außen vor.',
      ),
    ).toBeInTheDocument()
  })

  it('nennt im Singular eine mehr überschriebene und eine übergangene Karte', async () => {
    const epicsApi = vorhabenApi(vi.fn().mockResolvedValue(ergebnis([1001], 1, 1)))
    zeige({ initialCards: [karte(1), karte(2)], start: [abrechnung], api: mkApi(), epicsApi })

    waehle(1, 2)
    oeffneZuordnen('Abrechnung')
    bestaetige()

    expect(
      await screen.findByText(
        '1 Karte dem Vorhaben „Abrechnung“ zugeordnet. Davon hatte 1 bisher ein anderes Vorhaben. 1 ausgewählte Karte gab es nicht mehr auf diesem Board; sie blieb außen vor.',
      ),
    ).toBeInTheDocument()
  })

  it('meldet Erfolg auch, wenn bei der Ausführung keine Karte geändert wurde (E10)', async () => {
    const epicsApi = vorhabenApi(vi.fn().mockResolvedValue(ergebnis([], 0, 2)))
    zeige({ initialCards: [karte(1), karte(2)], start: [nice()], hidden: new Set([5]), api: mkApi(), epicsApi })

    waehle(1, 2)
    oeffneZuordnen('Nice to have (ausgeblendet)')
    bestaetige()

    // Nichts wurde verdeckt — der Weg zurück entfällt.
    expect(
      await screen.findByText(
        '0 Karten dem ausgeblendeten Vorhaben „Nice to have“ zugeordnet. 2 ausgewählte Karten gab es nicht mehr auf diesem Board; sie blieben außen vor.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveClass('MuiAlert-filledSuccess')
  })

  it('führt Rückfrage und Meldung auf Englisch', async () => {
    const epicsApi = vorhabenApi(vi.fn().mockResolvedValue(ergebnis([1001, 1002], 2, 1)))
    renderMitSprache(
      <SnackbarProvider>
        <BoardMitVorhaben
          initialCards={[karte(1, { parentId: 8 }), karte(2)]} start={[altlasten([1]), abrechnung]}
          api={mkApi()} epicsApi={epicsApi}
        />
      </SnackbarProvider>,
      'en',
    )

    fireEvent.click(screen.getByRole('button', { name: 'Select' }))
    fireEvent.click(screen.getByTestId('card-1001'))
    fireEvent.click(screen.getByTestId('card-1002'))
    fireEvent.click(screen.getByRole('button', { name: 'Assign epic' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Abrechnung' }))
    const dialog = screen.getByRole('dialog', { name: 'Assign epic?' })
    expect(dialog).toHaveTextContent('2 cards will be assigned to the epic “Abrechnung”.')
    expect(dialog).toHaveTextContent('1 of them already belongs to another epic; that assignment will be replaced.')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Assign' }))

    expect(
      await screen.findByText(
        '2 cards assigned to the epic “Abrechnung”. 2 of them previously had another epic. 1 selected card no longer existed on this board; it was left out.',
      ),
    ).toBeInTheDocument()
  })

  describe('Wortlaute in beiden Sprachen', () => {
    it.each([
      ['de', de],
      ['en', en],
    ])('%s: jede Form ist ausgeschrieben', (_sprache, w) => {
      for (const anzahl of [0, 1, 2]) {
        for (const text of [
          w.BOARD.ZUORDNEN_TEXT(anzahl, 'X'),
          w.BOARD.ZUORDNEN_UEBERSCHREIBT(anzahl),
          w.BOARD.ZUORDNEN_ERFOLG(anzahl, 'X'),
          w.BOARD.ZUORDNEN_ERFOLG_AUSGEBLENDET(anzahl, 'X'),
          w.BOARD.ZUORDNEN_MEHR_UEBERSCHRIEBEN(anzahl),
          w.BOARD.ZUORDNEN_UEBERGANGEN(anzahl),
        ]) {
          expect(text).not.toMatch(/undefined|NaN/)
        }
      }
      expect(w.BOARD.ZUORDNEN_HOECHSTENS(200, 240)).toMatch(/200.*240/)
    })

    it('nennt im Englischen Einzahl und Mehrzahl', () => {
      expect(en.BOARD.ZUORDNEN_TEXT(1, 'X')).toBe('The selected card will be assigned to the epic “X”.')
      expect(en.BOARD.ZUORDNEN_ERFOLG(1, 'X')).toBe('1 card assigned to the epic “X”.')
      expect(en.BOARD.ZUORDNEN_ERFOLG_AUSGEBLENDET(1, 'X')).toBe(
        '1 card assigned to the hidden epic “X”. It is now concealed on this board — “Show hidden” shows it again.',
      )
      expect(en.BOARD.ZUORDNEN_ERFOLG_AUSGEBLENDET(2, 'X')).toBe(
        '2 cards assigned to the hidden epic “X”. They are now concealed on this board — “Show hidden” shows them again.',
      )
      expect(en.BOARD.ZUORDNEN_ERFOLG_AUSGEBLENDET(0, 'X')).toBe('0 cards assigned to the hidden epic “X”.')
      expect(en.BOARD.ZUORDNEN_UEBERSCHREIBT(2)).toBe('2 of them already belong to another epic; that assignment will be replaced.')
      expect(en.BOARD.ZUORDNEN_MEHR_UEBERSCHRIEBEN(1)).toBe('1 of them previously had another epic.')
      expect(en.BOARD.ZUORDNEN_UEBERGANGEN(2)).toBe('2 selected cards no longer existed on this board; they were left out.')
      expect(en.BOARD.ZUORDNEN_HOECHSTENS(200, 240)).toBe('At most 200 cards in one go — 240 selected')
    })

    it('nennt im Deutschen die Einzahl des ausgeblendeten Ziels', () => {
      expect(de.BOARD.ZUORDNEN_ERFOLG_AUSGEBLENDET(1, 'X')).toBe(
        '1 Karte dem ausgeblendeten Vorhaben „X“ zugeordnet. Sie ist auf diesem Board jetzt verdeckt — „Ausgeblendete zeigen“ blendet sie wieder ein.',
      )
    })
  })
})

describe('BoardView — Spaltenköpfe bleiben beim Rollen stehen (Issue #1901)', () => {
  const rahmen = (left: number, top: number, width: number, height: number): DOMRect =>
    ({ left, top, width, height, right: left + width, bottom: top + height, x: left, y: top, toJSON: () => ({}) }) as DOMRect
  const flaeche = () => screen.getByTestId('board-surface')

  /** Ein `dragover` mit Koordinaten: jsdom kennt kein `DragEvent`, `fireEvent.dragOver` verlöre sie. */
  const ziehenUeber = (x: number, y: number) =>
    fireEvent(flaeche(), new MouseEvent('dragover', { bubbles: true, cancelable: true, clientX: x, clientY: y }))

  const karteZiehen = async () => {
    fireEvent.dragStart(screen.getByTestId('card-100'), { dataTransfer: { setData: vi.fn() } })
    await waitFor(() => expect(screen.getByTestId('card-100')).toHaveAttribute('data-zieh-zustand', 'bewegt'))
  }

  /** Die Fläche liegt unter der Werkzeugleiste, 1200 × 588 px groß, wie bei 1440 × 768. */
  const flaecheAusmessen = () => {
    flaeche().getBoundingClientRect = () => rahmen(0, 180, 1200, 588)
  }

  it('rollt senkrecht und waagerecht in der Fläche, ohne die feste Abzugskonstante', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    const regel = cssRegel(flaeche())
    expect(regel).toContain('overflow: auto')
    expect(regel).not.toContain('100vh')
  })

  it('lässt alle Spalten mindestens so hoch wie die Fläche und gleich hoch untereinander', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    const zeile = within(flaeche()).getByTestId('board-zeile')
    expect(within(zeile).getByTestId('column-10')).toBeInTheDocument()
    const regel = cssRegel(zeile)
    expect(regel).toContain('min-height: 100%')
    expect(regel).toContain('align-items: stretch')
  })

  it('reicht von ihrer Oberkante bis zum unteren Fensterrand, neu gemessen bei Größenänderung des Fensters', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    flaeche().getBoundingClientRect = () => rahmen(0, 180, 1200, 100)
    fireEvent(window, new Event('resize'))
    expect(flaeche()).toHaveStyle({ height: `${window.innerHeight - 180}px` })
  })

  it('misst die Oberkante im Dokument, auch wenn die Seite schon gerollt ist', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    const vorher = window.scrollY
    Object.defineProperty(window, 'scrollY', { configurable: true, value: 50 })
    try {
      flaeche().getBoundingClientRect = () => rahmen(0, 130, 1200, 100)
      fireEvent(window, new Event('resize'))
      expect(flaeche()).toHaveStyle({ height: `${window.innerHeight - 180}px` })
    } finally {
      Object.defineProperty(window, 'scrollY', { configurable: true, value: vorher })
    }
  })

  it('wird nie niedriger als 320 px', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    flaeche().getBoundingClientRect = () => rahmen(0, window.innerHeight - 100, 1200, 100)
    fireEvent(window, new Event('resize'))
    expect(flaeche()).toHaveStyle({ height: '320px' })
  })

  it('misst neu, wenn sich die Größe der Fläche ändert', () => {
    const beobachtet: Element[] = []
    let melden: () => void = () => undefined
    const trennen = vi.fn()
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(rueckruf: () => void) {
          melden = rueckruf
        }
        observe(ziel: Element) {
          beobachtet.push(ziel)
        }
        disconnect = trennen
      },
    )
    try {
      const { unmount } = render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

      expect(beobachtet).toEqual([flaeche()])
      flaeche().getBoundingClientRect = () => rahmen(0, 240, 1200, 100)
      act(() => melden())
      expect(flaeche()).toHaveStyle({ height: `${window.innerHeight - 240}px` })
      unmount()
      expect(trennen).toHaveBeenCalled()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('hört nach dem Aushängen nicht mehr auf Größenänderungen des Fensters', () => {
    const entfernen = vi.spyOn(window, 'removeEventListener')
    const { unmount } = render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    unmount()
    expect(entfernen).toHaveBeenCalledWith('resize', expect.any(Function))
  })

  it('rollt beim Ziehen einer Karte im unteren Randband nach unten, im oberen nach oben', async () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    flaecheAusmessen()
    await karteZiehen()

    flaeche().scrollTop = 100
    ziehenUeber(600, 768)
    expect(flaeche().scrollTop).toBe(100 + RAND_SCROLL_MAX_PX)
    ziehenUeber(600, 180)
    expect(flaeche().scrollTop).toBe(100)
    expect(flaeche().scrollLeft).toBe(0)
  })

  it('rollt beim Ziehen einer Karte im rechten Randband nach rechts, im linken nach links', async () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    flaecheAusmessen()
    await karteZiehen()

    flaeche().scrollLeft = 100
    ziehenUeber(1200, 450)
    expect(flaeche().scrollLeft).toBe(100 + RAND_SCROLL_MAX_PX)
    ziehenUeber(0, 450)
    expect(flaeche().scrollLeft).toBe(100)
    expect(flaeche().scrollTop).toBe(0)
  })

  it('rollt in der Mitte der Fläche nicht', async () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    flaecheAusmessen()
    await karteZiehen()

    ziehenUeber(600, 450)
    expect(flaeche().scrollTop).toBe(0)
    expect(flaeche().scrollLeft).toBe(0)
  })

  it('rollt ohne gezogene Karte auch am Rand nicht', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)
    flaecheAusmessen()

    ziehenUeber(1200, 768)
    expect(flaeche().scrollTop).toBe(0)
    expect(flaeche().scrollLeft).toBe(0)
  })

  it('hält den Kopfbereich jeder Spalte oben in der Fläche, im Nut-Ton und über den Karten', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    for (const id of [10, 20]) {
      const kopf = screen.getByTestId(`kopfbereich-${id}`)
      expect(within(kopf).getByTestId(`column-header-${id}`)).toBeInTheDocument()
      const regel = cssRegel(kopf)
      expect(regel).toContain('position: sticky')
      expect(regel).toContain('top: 0')
      expect(regel).toContain('z-index: 1')
      expect(regel).toContain(`background: ${NUT}`)
    }
  })

  it('lässt den Hinweis „waagerecht rollen“ stehen', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    expect(screen.getByText(de.BOARD.WAAGERECHT_ROLLEN)).toBeInTheDocument()
  })
})

describe('BoardView — Ansicht als XY: Veränderungen sind gesperrt (Issue #1926)', () => {
  const vorschau = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }
  const nightrun = { id: 9, boardId: 1, name: 'kit:nightrun', color: '#00ff00', countOnEpicTile: false }

  beforeEach(() => {
    editMode.value = true
    auth.vorschau = vorschau
    mCards.bulkTransfer.mockReset()
    mColumns.remove.mockReset()
    mColumns.reorder.mockReset()
    mColumns.sortByNumber.mockReset()
  })

  afterEach(() => {
    kitNamen.geladen = false
  })

  /** Das sichtbare, gesperrte Bedienelement mit dem Namen „Vorschau“ und dem Text `text`. */
  const gesperrtMitText = (text: string) =>
    screen.getAllByRole('button', { name: 'Vorschau' }).find((k) => k.textContent === text) ?? null

  function erwarteGesperrt(element: HTMLElement | null) {
    expect(element).toBeInTheDocument()
    expect(element).toHaveAccessibleName('Vorschau')
    if (element?.tagName === 'LABEL') {
      expect(element).toHaveAttribute('aria-disabled', 'true')
    } else {
      expect(element).toBeDisabled()
    }
  }

  it('Szenario: Veränderungen sind in der Vorschau gesperrt — Ziehen löst keinen Verschiebe-Aufruf aus', () => {
    const api = mkApi({ move: vi.fn().mockResolvedValue(undefined) })
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    const karte = screen.getByTestId('card-100')
    expect(karte).toHaveAttribute('draggable', 'false')
    fireEvent.dragStart(karte, { dataTransfer: { setData: vi.fn() } })
    dropOnColumn(20, 100)

    expect(api.move).not.toHaveBeenCalled()
    expect(mCards.bulkTransfer).not.toHaveBeenCalled()
    expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
  })

  it('sperrt Anlegen, Spezifikation einlesen und neue Spalte mit dem Hinweis „Vorschau“', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    erwarteGesperrt(gesperrtMitText('Neu anlegen'))
    erwarteGesperrt(gesperrtMitText('Spezifikation einlesen'))
    expect(screen.getByLabelText('Markdown-Datei auswählen')).toBeDisabled()
    erwarteGesperrt(gesperrtMitText('Spalte'))
    // Auswählen ändert nichts und bleibt bedienbar.
    expect(screen.getByRole('button', { name: 'Auswählen' })).toBeEnabled()
  })

  it('öffnet mit „+“ keinen Anlage-Dialog', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    fireEvent.keyDown(document.body, { key: '+' })

    expect(screen.queryByRole('heading', { name: 'Neue Karte in „Backlog“' })).not.toBeInTheDocument()
  })

  it('sperrt Sortieren, Bearbeiten und Löschen im Spaltenkopf und das Ziehen der Spalte', () => {
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    const kopf = screen.getByTestId('column-header-20')
    expect(kopf).toHaveAttribute('draggable', 'false')
    const knoepfe = within(kopf).getAllByRole('button', { name: 'Vorschau' })
    expect(knoepfe).toHaveLength(3)
    knoepfe.forEach((knopf) => expect(knopf).toBeDisabled())
    expect(within(kopf).queryByRole('button', { name: 'Spalte Done löschen' })).not.toBeInTheDocument()

    fireEvent.dragStart(kopf)
    fireEvent.drop(screen.getByTestId('column-header-10'), { dataTransfer: { getData: () => '' } })
    expect(mColumns.reorder).not.toHaveBeenCalled()
  })

  it('zeigt im ⋮-Menü jeden verändernden Eintrag gesperrt', () => {
    const wideBoard: Board = {
      ...board,
      columns: [
        { id: 10, name: 'Backlog', position: 0, wipLimit: null },
        { id: 20, name: 'Doing', position: 1, wipLimit: null },
        { id: 30, name: 'Done', position: 2, wipLimit: null },
      ],
    }
    render(<BoardView board={wideBoard} initialCards={[{ ...card, columnId: 20 }]} canEdit canTransfer api={mkApi()} />)

    fireEvent.click(screen.getByLabelText('Menü Aufgabe'))

    const eintraege = screen.getAllByRole('menuitem')
    expect(eintraege.map((e) => e.textContent)).toEqual([
      'Bearbeiten', 'Duplizieren', 'Archivieren', 'Verschieben…', 'Nach links verschieben', 'Nach rechts verschieben', 'Löschen',
    ])
    eintraege.forEach((eintrag) => {
      expect(eintrag).toHaveAccessibleName('Vorschau')
      expect(eintrag).toHaveAttribute('aria-disabled', 'true')
    })
  })

  it('sperrt den Knopf kit:nightrun mit dem Hinweis „Vorschau“', () => {
    kitNamen.geladen = true
    const readyBoard: Board = { ...board, columns: [{ id: 30, name: 'Ready', position: 0, wipLimit: null }] }
    render(
      <BoardView board={readyBoard} initialCards={[{ ...card, columnId: 30 }]} canEdit boardLabels={[nightrun]} api={mkApi()} />,
    )

    erwarteGesperrt(gesperrtMitText('kit:nightrun'))
  })

  it('lässt außerhalb der Vorschau alles bedienbar', () => {
    auth.vorschau = null
    render(<BoardView board={board} initialCards={[card]} canEdit api={mkApi()} />)

    expect(screen.getByRole('button', { name: 'Neu anlegen' })).toBeEnabled()
    expect(screen.getByLabelText('Markdown-Datei auswählen')).toBeEnabled()
    expect(screen.getByTestId('card-100')).toHaveAttribute('draggable', 'true')
    expect(screen.getByTestId('column-header-20')).toHaveAttribute('draggable', 'true')
    expect(screen.queryByRole('button', { name: 'Vorschau' })).not.toBeInTheDocument()
  })
})
