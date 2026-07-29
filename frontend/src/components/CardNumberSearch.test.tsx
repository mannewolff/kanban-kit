import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cardsApi, type CardByNumber, type CardSearchHit } from '../api/cards'
import { CardNumberSearch } from './CardNumberSearch'

vi.mock('../api/cards', () => ({ cardsApi: { searchByNumber: vi.fn() } }))

// Toast-Weg: useSnackbar liefert im Test einen Spy (statt des No-op-Defaults ohne Provider).
const mNotify = vi.fn()
vi.mock('./SnackbarProvider', () => ({ useSnackbar: () => mNotify }))

// Das Detail-Modal ist eigenständig getestet; hier zählt nur, mit welcher Karte und mit welchem
// Kontext es geöffnet wird.
vi.mock('./CardDetailModal', () => ({
  CardDetailModal: ({
    card,
    canEdit,
    projectId,
    columnName,
    onClose,
  }: Readonly<{
    card: CardByNumber
    canEdit: boolean
    projectId?: number
    columnName?: string
    onClose: () => void
  }>) => (
    <div data-testid="card-detail">
      <span data-testid="detail-title">{card.title}</span>
      <span data-testid="detail-project">{String(projectId)}</span>
      <span data-testid="detail-column">{columnName ?? '—'}</span>
      <span data-testid="detail-can-edit">{String(canEdit)}</span>
      <button type="button" onClick={onClose}>
        Detail schließen
      </button>
    </div>
  ),
}))

const mockedCards = cardsApi as unknown as { searchByNumber: ReturnType<typeof vi.fn> }

function card(overrides: Partial<CardByNumber> = {}): CardByNumber {
  return {
    id: 7,
    number: 345,
    title: 'Fehlerbild klären',
    description: null,
    type: 'CARD',
    dependencies: [],
    assignees: [],
    labels: [],
    parentId: null,
    shortcode: null,
    dueDate: null,
    archived: false,
    ideaStored: false,
    boardId: 1,
    columnId: 2,
    ...overrides,
  }
}

function hit(overrides: Partial<CardSearchHit> = {}): CardSearchHit {
  return {
    card: card(),
    projectId: 5,
    projectName: 'Projekt A',
    boardId: 1,
    boardName: 'Entwicklung',
    boardArchived: false,
    columnId: 2,
    columnName: 'Backlog',
    ...overrides,
  }
}

/** Das Suchfeld — über sein Label, damit der Einklapp-Knopf nicht mit getroffen wird. */
function input(): HTMLElement {
  return screen.getByRole('textbox', { name: 'Kartennummer suchen' })
}

/** Eingeben und absenden. `submit` steht für die implizite Formular-Absendung mit Enter. */
function search(value: string): void {
  fireEvent.change(input(), { target: { value } })
  fireEvent.submit(screen.getByRole('search'))
}

describe('CardNumberSearch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedCards.searchByNumber.mockResolvedValue([])
  })

  it('fragt für „345" und „#345" dieselbe Nummer ab', async () => {
    render(<CardNumberSearch />)

    search('345')
    await screen.findByRole('search')
    search(' #345 ')
    await screen.findByRole('search')

    expect(mockedCards.searchByNumber).toHaveBeenCalledTimes(2)
    expect(mockedCards.searchByNumber).toHaveBeenNthCalledWith(1, 345)
    expect(mockedCards.searchByNumber).toHaveBeenNthCalledWith(2, 345)
  })

  it('öffnet bei genau einem Treffer direkt das Detail-Modal', async () => {
    mockedCards.searchByNumber.mockResolvedValue([hit()])
    render(<CardNumberSearch />)

    search('345')

    expect(await screen.findByTestId('detail-title')).toHaveTextContent('Fehlerbild klären')
    expect(screen.getByTestId('detail-project')).toHaveTextContent('5')
    expect(screen.getByTestId('detail-column')).toHaveTextContent('Backlog')
    // Aus der Suche heraus ist die Rolle im fremden Projekt unbekannt — nur lesen.
    expect(screen.getByTestId('detail-can-edit')).toHaveTextContent('false')
    // Nach dem Treffer ist das Feld wieder leer für die nächste Suche.
    expect(input()).toHaveValue('')
  })

  it('schließt das Detail-Modal wieder', async () => {
    mockedCards.searchByNumber.mockResolvedValue([hit()])
    render(<CardNumberSearch />)

    search('345')
    fireEvent.click(await screen.findByRole('button', { name: 'Detail schließen' }))

    expect(screen.queryByTestId('card-detail')).not.toBeInTheDocument()
  })

  it('zeigt bei mehreren Treffern eine Auswahl mit unterscheidbarem Ort', async () => {
    mockedCards.searchByNumber.mockResolvedValue([
      hit(),
      hit({
        card: card({ id: 9, title: 'Zweite Karte' }),
        projectId: 6,
        projectName: 'Projekt B',
        boardName: 'Altlasten',
        boardArchived: true,
        columnName: 'Done',
      }),
    ])
    render(<CardNumberSearch />)

    search('345')

    const options = await screen.findAllByRole('menuitem')
    expect(options).toHaveLength(2)
    expect(options[0]).toHaveTextContent('Projekt A / Entwicklung / Backlog')
    expect(options[1]).toHaveTextContent('Projekt B / Altlasten (archiviert) / Done')
    expect(screen.queryByTestId('card-detail')).not.toBeInTheDocument()

    fireEvent.click(options[1])

    expect(await screen.findByTestId('detail-title')).toHaveTextContent('Zweite Karte')
    expect(screen.getByTestId('detail-project')).toHaveTextContent('6')
    expect(screen.queryByRole('menuitem')).not.toBeInTheDocument()
  })

  it('benennt board-lose Pool-Ideen in der Auswahl als Ideen des Projekts', async () => {
    mockedCards.searchByNumber.mockResolvedValue([
      hit(),
      hit({
        card: card({ id: 9, title: 'Idee ohne Board', boardId: null, columnId: null }),
        boardId: null,
        boardName: null,
        columnId: null,
        columnName: null,
      }),
    ])
    render(<CardNumberSearch />)

    search('345')

    const options = await screen.findAllByRole('menuitem')
    expect(options[1]).toHaveTextContent('Projekt A / Ideen')

    fireEvent.click(options[1])

    // Ohne Spalte darf das Modal keinen Status-Chip-Namen bekommen.
    expect(await screen.findByTestId('detail-column')).toHaveTextContent('—')
  })

  it('lässt die Trefferauswahl ohne Wahl wieder schließen', async () => {
    mockedCards.searchByNumber.mockResolvedValue([hit(), hit({ card: card({ id: 9 }) })])
    render(<CardNumberSearch />)

    search('345')
    await screen.findAllByRole('menuitem')
    fireEvent.keyDown(screen.getByRole('menu', { name: 'Treffer auswählen' }), { key: 'Escape' })

    expect(await screen.findByRole('search')).toBeInTheDocument()
    expect(screen.queryByTestId('card-detail')).not.toBeInTheDocument()
  })

  it('meldet einen Fehlschlag der Suche, ohne ein Modal zu öffnen', async () => {
    mockedCards.searchByNumber.mockRejectedValue(new Error('offline'))
    render(<CardNumberSearch />)

    search('345')

    await waitFor(() =>
      expect(mNotify).toHaveBeenCalledWith('Die Suche ist fehlgeschlagen.', 'error'),
    )
    expect(screen.queryByTestId('card-detail')).not.toBeInTheDocument()
  })

  it('meldet einen leeren Treffer als „nicht gefunden", ohne Existenz zu behaupten', async () => {
    render(<CardNumberSearch />)

    search('#345')

    await waitFor(() =>
      expect(mNotify).toHaveBeenCalledWith('Keine Karte mit der Nummer 345 gefunden.', 'warning'),
    )
    expect(screen.queryByTestId('card-detail')).not.toBeInTheDocument()
  })

  it.each([
    ['abc', 'nicht-numerisch'],
    ['12a', 'teilweise numerisch'],
    ['1234567890', 'zu viele Stellen für eine Kartennummer'],
  ])('fragt bei „%s" (%s) nichts ab und erklärt die Eingabe', (value) => {
    render(<CardNumberSearch />)

    search(value)

    expect(mockedCards.searchByNumber).not.toHaveBeenCalled()
    expect(mNotify).toHaveBeenCalledWith(
      'Bitte eine Kartennummer eingeben, z. B. 345 oder #345.',
      'warning',
    )
  })

  it('fragt bei leerer Eingabe weder ab noch meldet es etwas', () => {
    render(<CardNumberSearch />)

    search('   ')

    expect(mockedCards.searchByNumber).not.toHaveBeenCalled()
    expect(mNotify).not.toHaveBeenCalled()
  })

  it('zeigt während der Anfrage einen Ladezustand, ohne das Feld zu sperren', async () => {
    let resolve: (hits: CardSearchHit[]) => void = () => {}
    mockedCards.searchByNumber.mockReturnValue(
      new Promise<CardSearchHit[]>((r) => {
        resolve = r
      }),
    )
    render(<CardNumberSearch />)

    search('345')

    expect(await screen.findByLabelText('Suche läuft')).toBeInTheDocument()
    expect(input()).toBeEnabled()

    await act(async () => {
      resolve([])
    })
    expect(screen.queryByLabelText('Suche läuft')).not.toBeInTheDocument()
  })

  it('startet während einer laufenden Anfrage keine zweite', async () => {
    mockedCards.searchByNumber.mockReturnValue(new Promise<CardSearchHit[]>(() => {}))
    render(<CardNumberSearch />)

    search('345')
    await screen.findByLabelText('Suche läuft')
    search('346')

    expect(mockedCards.searchByNumber).toHaveBeenCalledTimes(1)
  })

  it('klappt das Feld über den Suchknopf auf und fokussiert es', () => {
    render(<CardNumberSearch />)

    fireEvent.click(screen.getByRole('button', { name: 'Karte suchen' }))

    expect(input()).toHaveFocus()
  })

  it('fokussiert das Feld über das Tastenkürzel „/"', () => {
    render(<CardNumberSearch />)

    fireEvent.keyDown(document, { key: '/' })

    expect(input()).toHaveFocus()
  })

  it('leert und schließt das Feld mit Escape', () => {
    render(<CardNumberSearch />)

    fireEvent.click(screen.getByRole('button', { name: 'Karte suchen' }))
    fireEvent.change(input(), { target: { value: '345' } })
    fireEvent.keyDown(input(), { key: 'Escape' })

    expect(input()).toHaveValue('')
    expect(input()).not.toHaveFocus()
  })

  it('lässt andere Tasten im Feld unberührt', () => {
    render(<CardNumberSearch />)

    fireEvent.change(input(), { target: { value: '345' } })
    fireEvent.keyDown(input(), { key: 'a' })

    expect(input()).toHaveValue('345')
  })
})
