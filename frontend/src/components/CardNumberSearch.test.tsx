import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cardsApi, type CardByNumber, type CardSearchHit } from '../api/cards'
import { epicsApi, type Epic } from '../api/epics'
import { labelsApi, type Label } from '../api/labels'
import { membersApi, type Member } from '../api/members'
import { projectsApi } from '../api/projects'
import { CardNumberSearch } from './CardNumberSearch'

vi.mock('../api/cards', () => ({ cardsApi: { searchByNumber: vi.fn() } }))
vi.mock('../api/members', () => ({ membersApi: { list: vi.fn() } }))
vi.mock('../api/epics', () => ({ epicsApi: { list: vi.fn() } }))
vi.mock('../api/labels', () => ({ labelsApi: { list: vi.fn() } }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))

// Die Rolle kommt aus `useProjectRole` und damit aus dem Auth-Context — hier gemockt, damit jeder
// Test seine Projektrolle setzen kann (der Hook selbst ist eigenständig getestet).
const authUser = vi.hoisted(() => ({ value: null as unknown }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: authUser.value }) }))

// Toast-Weg: useSnackbar liefert im Test einen Spy (statt des No-op-Defaults ohne Provider).
const mNotify = vi.fn()
vi.mock('./SnackbarProvider', () => ({ useSnackbar: () => mNotify }))

// Das Detail-Modal ist eigenständig getestet; hier zählt nur, mit welcher Karte und mit welchem
// Kontext es geöffnet wird.
vi.mock('./CardDetailModal', () => ({
  CardDetailModal: ({
    card,
    canEdit,
    canModerateComments,
    canEditEpic,
    canEditLabels,
    projectId,
    columnName,
    members,
    epics,
    boardLabels,
    onChanged,
    onClose,
  }: Readonly<{
    card: CardByNumber
    canEdit: boolean
    canModerateComments?: boolean
    canEditEpic?: boolean
    canEditLabels?: boolean
    projectId?: number
    columnName?: string
    members?: Member[]
    epics?: Epic[]
    boardLabels?: Label[]
    onChanged?: () => void
    onClose: () => void
  }>) => (
    <div data-testid="card-detail">
      <span data-testid="detail-title">{card.title}</span>
      <span data-testid="detail-card-id">{String(card.id)}</span>
      <span data-testid="detail-project">{String(projectId)}</span>
      <span data-testid="detail-column">{columnName ?? '—'}</span>
      <span data-testid="detail-can-edit">{String(canEdit)}</span>
      <span data-testid="detail-can-moderate">{String(canModerateComments)}</span>
      <span data-testid="detail-can-edit-epic">{String(canEditEpic)}</span>
      <span data-testid="detail-can-edit-labels">{String(canEditLabels)}</span>
      <span data-testid="detail-members">{(members ?? []).map((m) => m.displayName).join(', ')}</span>
      <span data-testid="detail-epics">{(epics ?? []).map((e) => e.title).join(', ')}</span>
      <span data-testid="detail-labels">{(boardLabels ?? []).map((l) => l.name).join(', ')}</span>
      <button type="button" onClick={onChanged}>
        Gespeichert
      </button>
      <button type="button" onClick={onClose}>
        Detail schließen
      </button>
    </div>
  ),
}))

const mockedCards = cardsApi as unknown as { searchByNumber: ReturnType<typeof vi.fn> }
const mockedMembers = membersApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedEpics = epicsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedLabels = labelsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }

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

/** Board-lose Pool-Idee: weder Board noch Spalte, aber ein Projekt. */
function ideaHit(): CardSearchHit {
  return hit({
    card: card({ id: 9, title: 'Idee ohne Board', boardId: null, columnId: null }),
    boardId: null,
    boardName: null,
    columnId: null,
    columnName: null,
  })
}

const member = (userId: number, displayName: string): Member => ({
  userId,
  email: `${userId}@b.c`,
  displayName,
  role: 'MEMBER',
})

const epic: Epic = {
  id: 9,
  number: 2,
  title: 'Auth',
  description: null,
  shortcode: 'AUT',
  done: 0,
  total: 1,
}

const label: Label = { id: 5, boardId: 1, name: 'Bug', color: '#f00' }

/** Setzt den eingeloggten Nutzer mit einer Projektrolle für Projekt 5. */
function loginAs(role: string, platformRole = 'USER'): void {
  authUser.value = {
    userId: 1,
    email: 'a@b.c',
    displayName: 'Manne',
    platformRole,
    memberships: [{ projectId: 5, role }],
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

/** Wartet, bis der Bearbeitungskontext des geöffneten Treffers feststeht. */
async function detailReady(): Promise<HTMLElement> {
  const flag = await screen.findByTestId('detail-can-edit')
  await waitFor(() => expect(screen.getByTestId('detail-members')).not.toBeEmptyDOMElement())
  return flag
}

describe('CardNumberSearch', () => {
  beforeEach(() => {
    // `reset` statt `clear`: Die Tests unten reihen `…Once`-Antworten ein, die sonst in den
    // nächsten Test durchrutschen würden.
    vi.resetAllMocks()
    loginAs('OWNER')
    mockedCards.searchByNumber.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([member(1, 'Manne')])
    mockedEpics.list.mockResolvedValue([epic])
    mockedLabels.list.mockResolvedValue([label])
    mockedProjects.list.mockResolvedValue([])
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
    // Nach dem Treffer ist das Feld wieder leer für die nächste Suche.
    expect(input()).toHaveValue('')
  })

  it('öffnet einen Treffer im eigenen Projekt bearbeitbar', async () => {
    mockedCards.searchByNumber.mockResolvedValue([hit()])
    render(<CardNumberSearch />)

    search('345')

    expect(await detailReady()).toHaveTextContent('true')
    expect(mockedMembers.list).toHaveBeenCalledWith(5)
    expect(mockedEpics.list).toHaveBeenCalledWith(1)
    expect(mockedLabels.list).toHaveBeenCalledWith(1)
  })

  it('reicht Epics, Mitglieder und Board-Labels des Treffers durch', async () => {
    mockedCards.searchByNumber.mockResolvedValue([hit()])
    render(<CardNumberSearch />)

    search('345')
    await detailReady()

    expect(screen.getByTestId('detail-members')).toHaveTextContent('Manne')
    expect(screen.getByTestId('detail-epics')).toHaveTextContent('Auth')
    expect(screen.getByTestId('detail-labels')).toHaveTextContent('Bug')
    expect(screen.getByTestId('detail-can-edit-epic')).toHaveTextContent('true')
    expect(screen.getByTestId('detail-can-edit-labels')).toHaveTextContent('true')
  })

  it('öffnet einen Treffer als VIEWER nur lesend', async () => {
    loginAs('VIEWER')
    mockedCards.searchByNumber.mockResolvedValue([hit()])
    render(<CardNumberSearch />)

    search('345')

    expect(await detailReady()).toHaveTextContent('false')
    expect(screen.getByTestId('detail-can-moderate')).toHaveTextContent('false')
  })

  it.each([
    ['MEMBER', 'false'],
    ['ADMIN', 'true'],
    ['OWNER', 'true'],
  ])('gibt als %s Bearbeitung frei, Moderation aber nur ab ADMIN', async (role, moderate) => {
    loginAs(role)
    mockedCards.searchByNumber.mockResolvedValue([hit()])
    render(<CardNumberSearch />)

    search('345')

    expect(await detailReady()).toHaveTextContent('true')
    expect(screen.getByTestId('detail-can-moderate')).toHaveTextContent(moderate)
  })

  it('hält eine Karte auf archiviertem Board bearbeitbar, ohne Epics und Labels', async () => {
    mockedCards.searchByNumber.mockResolvedValue([
      hit({ boardName: 'Altlasten', boardArchived: true }),
    ])
    render(<CardNumberSearch />)

    search('345')

    expect(await detailReady()).toHaveTextContent('true')
    // Mitglieder hängen am Projekt, nicht am Board — sie bleiben gefüllt.
    expect(screen.getByTestId('detail-members')).toHaveTextContent('Manne')
    expect(mockedEpics.list).not.toHaveBeenCalled()
    expect(mockedLabels.list).not.toHaveBeenCalled()
    expect(screen.getByTestId('detail-epics')).toBeEmptyDOMElement()
    expect(screen.getByTestId('detail-labels')).toBeEmptyDOMElement()
    // Ohne Optionsvorrat bleiben Epic-Auswahl und Label-Sektion lesend.
    expect(screen.getByTestId('detail-can-edit-epic')).toHaveTextContent('false')
    expect(screen.getByTestId('detail-can-edit-labels')).toHaveTextContent('false')
  })

  it('hält eine board-lose Pool-Idee bearbeitbar, ohne Epics und Labels', async () => {
    mockedCards.searchByNumber.mockResolvedValue([ideaHit()])
    render(<CardNumberSearch />)

    search('345')

    expect(await detailReady()).toHaveTextContent('true')
    expect(screen.getByTestId('detail-members')).toHaveTextContent('Manne')
    expect(mockedEpics.list).not.toHaveBeenCalled()
    expect(mockedLabels.list).not.toHaveBeenCalled()
    expect(screen.getByTestId('detail-can-edit-epic')).toHaveTextContent('false')
    expect(screen.getByTestId('detail-can-edit-labels')).toHaveTextContent('false')
  })

  it('bleibt lesend, solange die Rolle noch geladen wird', async () => {
    // Ohne Mitgliedschaft im Auth-Context lädt useProjectRole die Projektliste nach.
    authUser.value = {
      userId: 1,
      email: 'a@b.c',
      displayName: 'Manne',
      platformRole: 'USER',
      memberships: [],
    }
    let resolveProjects: (projects: { id: number; name: string; role: string; createdAt: string }[]) => void =
      () => {}
    mockedProjects.list.mockReturnValue(
      new Promise<{ id: number; name: string; role: string; createdAt: string }[]>((r) => {
        resolveProjects = r
      }),
    )
    mockedCards.searchByNumber.mockResolvedValue([hit()])
    render(<CardNumberSearch />)

    search('345')
    await detailReady()

    expect(screen.getByTestId('detail-can-edit')).toHaveTextContent('false')

    await act(async () => {
      resolveProjects([{ id: 5, name: 'Projekt A', role: 'OWNER', createdAt: '' }])
    })
    expect(screen.getByTestId('detail-can-edit')).toHaveTextContent('true')
  })

  it('bleibt lesend, solange die Bearbeitungsdaten noch laden', async () => {
    let resolveMembers: (members: Member[]) => void = () => {}
    mockedMembers.list.mockReturnValue(
      new Promise<Member[]>((r) => {
        resolveMembers = r
      }),
    )
    mockedCards.searchByNumber.mockResolvedValue([hit()])
    render(<CardNumberSearch />)

    search('345')

    expect(await screen.findByTestId('detail-can-edit')).toHaveTextContent('false')

    await act(async () => {
      resolveMembers([member(1, 'Manne')])
    })
    expect(screen.getByTestId('detail-can-edit')).toHaveTextContent('true')
  })

  it('verwirft bei einem Ladefehler die übrigen Daten nicht und bleibt lesend', async () => {
    mockedEpics.list.mockRejectedValue(new Error('offline'))
    mockedCards.searchByNumber.mockResolvedValue([hit()])
    render(<CardNumberSearch />)

    search('345')
    await detailReady()

    expect(screen.getByTestId('detail-can-edit')).toHaveTextContent('false')
    expect(screen.getByTestId('detail-members')).toHaveTextContent('Manne')
    expect(screen.getByTestId('detail-labels')).toHaveTextContent('Bug')
    expect(mNotify).toHaveBeenCalledWith(
      'Die Bearbeitungsdaten konnten nicht vollständig geladen werden — die Karte bleibt lesend.',
      'error',
    )
  })

  it('meldet mehrere Ladefehler desselben Treffers nur einmal', async () => {
    mockedEpics.list.mockRejectedValue(new Error('offline'))
    mockedLabels.list.mockRejectedValue(new Error('offline'))
    mockedCards.searchByNumber.mockResolvedValue([hit()])
    render(<CardNumberSearch />)

    search('345')
    await detailReady()

    expect(mNotify).toHaveBeenCalledTimes(1)
  })

  it('leert die Bearbeitungsdaten beim Trefferwechsel sofort', async () => {
    mockedMembers.list
      .mockResolvedValueOnce([member(1, 'Erste Person')])
      .mockReturnValueOnce(new Promise<Member[]>(() => {}))
    mockedCards.searchByNumber
      .mockResolvedValueOnce([hit()])
      .mockResolvedValueOnce([hit({ card: card({ id: 9, title: 'Zweite Karte' }) })])
    render(<CardNumberSearch />)

    search('345')
    await detailReady()
    search('346')

    await waitFor(() => expect(screen.getByTestId('detail-card-id')).toHaveTextContent('9'))
    // Die Daten des vorigen Treffers gelten hier nicht mehr — und bis die neuen da sind, lesend.
    expect(screen.getByTestId('detail-members')).toBeEmptyDOMElement()
    expect(screen.getByTestId('detail-can-edit')).toHaveTextContent('false')
  })

  it('ignoriert eine verspätete Antwort des vorigen Treffers', async () => {
    let resolveFirst: (members: Member[]) => void = () => {}
    mockedMembers.list
      .mockReturnValueOnce(
        new Promise<Member[]>((r) => {
          resolveFirst = r
        }),
      )
      .mockResolvedValueOnce([member(2, 'Zweite Person')])
    mockedCards.searchByNumber
      .mockResolvedValueOnce([hit()])
      .mockResolvedValueOnce([hit({ card: card({ id: 9, title: 'Zweite Karte' }) })])
    render(<CardNumberSearch />)

    search('345')
    await screen.findByTestId('detail-title')
    search('346')

    await waitFor(() => expect(screen.getByTestId('detail-members')).toHaveTextContent('Zweite Person'))

    await act(async () => {
      resolveFirst([member(1, 'Erste Person')])
    })
    expect(screen.getByTestId('detail-members')).toHaveTextContent('Zweite Person')
    expect(screen.getByTestId('detail-members')).not.toHaveTextContent('Erste Person')
  })

  it('löst nach dem Speichern über die Karten-ID auf, nicht über die Nummer', async () => {
    const first = hit({ card: card({ id: 7, title: 'Karte A' }) })
    const second = hit({
      card: card({ id: 9, title: 'Karte B' }),
      projectId: 6,
      projectName: 'Projekt B',
    })
    mockedCards.searchByNumber.mockResolvedValue([first, second])
    render(<CardNumberSearch />)

    search('345')
    const options = await screen.findAllByRole('menuitem')
    fireEvent.click(options[1])
    await screen.findByTestId('detail-title')

    mockedCards.searchByNumber.mockResolvedValue([
      first,
      { ...second, card: { ...second.card, title: 'Karte B (neu)' } },
    ])
    fireEvent.click(screen.getByRole('button', { name: 'Gespeichert' }))

    await waitFor(() => expect(screen.getByTestId('detail-title')).toHaveTextContent('Karte B (neu)'))
    expect(screen.getByTestId('detail-card-id')).toHaveTextContent('9')
  })

  it('hält den zuletzt bekannten Stand offen, wenn das Neuladen fehlschlägt', async () => {
    mockedCards.searchByNumber.mockResolvedValueOnce([hit()])
    render(<CardNumberSearch />)

    search('345')
    await screen.findByTestId('detail-title')

    mockedCards.searchByNumber.mockRejectedValueOnce(new Error('offline'))
    fireEvent.click(screen.getByRole('button', { name: 'Gespeichert' }))

    await waitFor(() =>
      expect(mNotify).toHaveBeenCalledWith('Der aktuelle Stand konnte nicht geladen werden.', 'error'),
    )
    expect(screen.getByTestId('detail-title')).toHaveTextContent('Fehlerbild klären')
  })

  it('schließt den Dialog mit Hinweis, wenn die Karte nach dem Speichern verschwunden ist', async () => {
    mockedCards.searchByNumber.mockResolvedValueOnce([hit()])
    render(<CardNumberSearch />)

    search('345')
    await screen.findByTestId('detail-title')

    mockedCards.searchByNumber.mockResolvedValueOnce([])
    fireEvent.click(screen.getByRole('button', { name: 'Gespeichert' }))

    await waitFor(() => expect(screen.queryByTestId('card-detail')).not.toBeInTheDocument())
    expect(mNotify).toHaveBeenCalledWith('Die Karte ist nicht mehr auffindbar.', 'warning')
  })

  it('lädt ohne geöffneten Treffer keine Bearbeitungsdaten', async () => {
    mockedCards.searchByNumber.mockResolvedValue([hit(), hit({ card: card({ id: 9 }) })])
    render(<CardNumberSearch />)

    search('345')
    await screen.findAllByRole('menuitem')

    expect(mockedMembers.list).not.toHaveBeenCalled()
    expect(mockedEpics.list).not.toHaveBeenCalled()
    expect(mockedLabels.list).not.toHaveBeenCalled()
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
    mockedCards.searchByNumber.mockResolvedValue([hit(), ideaHit()])
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
