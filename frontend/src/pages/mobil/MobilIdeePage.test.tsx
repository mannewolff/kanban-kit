import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi, type Board } from '../../api/boards'
import { cardsApi } from '../../api/cards'
import { ApiError } from '../../api/client'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { leseMobilBoard, schreibeMobilBoard } from '../../lib/mobilBoard'
import { MobilIdeePage } from './MobilIdeePage'

const authState = vi.hoisted(() => ({ user: { userId: 7 } as { userId: number } | null }))
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => authState }))
vi.mock('../../api/boards', () => ({ boardsApi: { get: vi.fn() } }))
vi.mock('../../api/cards', () => ({ cardsApi: { create: vi.fn() } }))
vi.mock('../../lib/mobilBoard', () => ({ leseMobilBoard: vi.fn(), schreibeMobilBoard: vi.fn() }))

const boards = boardsApi as unknown as { get: ReturnType<typeof vi.fn> }
const cards = cardsApi as unknown as { create: ReturnType<typeof vi.fn> }
const gemerkt = vi.mocked(leseMobilBoard)

const produkt: Board = {
  id: 42,
  projectId: 3,
  name: 'Produkt',
  createdAt: '2026-10-01T00:00:00Z',
  columns: [
    { id: 501, name: 'Ready', position: 1, wipLimit: null },
    { id: 500, name: 'Backlog', position: 0, wipLimit: null },
  ],
}

function seite(sprache: 'de' | 'en' = 'de') {
  return renderMitSprache(
    <MemoryRouter initialEntries={['/mobil']}>
      <Routes>
        <Route path="/mobil" element={<MobilIdeePage />} />
        <Route path="/mobil/einstellungen" element={<p>Einstellungen geöffnet</p>} />
      </Routes>
    </MemoryRouter>,
    sprache,
  )
}

describe('MobilIdeePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { userId: 7 }
    gemerkt.mockReturnValue(42)
    boards.get.mockResolvedValue(produkt)
  })

  afterEach(() => {
    vi.mocked(schreibeMobilBoard).mockReset()
  })

  it('Szenario: Idee in der mobilen Oberfläche einsprechen', async () => {
    // Angenommen ein Mitglied hat „Produkt“ als Standard-Board gewählt
    cards.create.mockResolvedValue({ id: 1, number: 9 })
    seite()
    expect(await screen.findByText('Geht ins Backlog von „Produkt“.')).toBeInTheDocument()
    // Wenn es einen Satz einspricht und auf den Knopf tippt
    const feld = screen.getByRole('textbox', { name: 'Deine Idee' })
    expect(feld).toHaveFocus()
    await userEvent.type(feld, ' Kaffeemaschine im Büro reparieren ')
    await userEvent.click(screen.getByRole('button', { name: 'Festhalten' }))
    // Dann bestätigt die Oberfläche das Anlegen, das Feld ist leer
    expect(await screen.findByText('Idee festgehalten.')).toBeInTheDocument()
    expect(feld).toHaveValue('')
    // Und im Backlog von „Produkt“ liegt eine Karte [Idea] mit dem Satz
    expect(cards.create).toHaveBeenCalledWith(42, 500, '[Idea] Kaffeemaschine im Büro reparieren')
  })

  it('Szenario: Anlegen ohne Netz', async () => {
    // Angenommen das Handy hat gerade kein Netz
    cards.create.mockRejectedValue(new TypeError('Failed to fetch'))
    seite()
    const feld = await screen.findByRole('textbox', { name: 'Deine Idee' })
    await screen.findByText('Geht ins Backlog von „Produkt“.')
    // Wenn das Mitglied eine Idee abschickt
    await userEvent.type(feld, 'Neue Idee')
    await userEvent.click(screen.getByRole('button', { name: 'Festhalten' }))
    // Dann meldet die Oberfläche, dass die Idee nicht angelegt wurde
    expect(await screen.findByText(/Die Idee wurde nicht angelegt/)).toBeInTheDocument()
    // Und der Text steht weiter im Feld
    expect(feld).toHaveValue('Neue Idee')
    expect(screen.queryByText('Idee festgehalten.')).not.toBeInTheDocument()
  })

  it('holt das Board beim Abschicken nach, wenn es beim Öffnen nicht geladen werden konnte', async () => {
    boards.get.mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValueOnce(produkt)
    cards.create.mockResolvedValue({ id: 1, number: 9 })
    seite()
    const feld = await screen.findByRole('textbox', { name: 'Deine Idee' })
    await waitFor(() => expect(boards.get).toHaveBeenCalledTimes(1))
    expect(screen.queryByText(/Geht ins Backlog/)).not.toBeInTheDocument()
    await userEvent.type(feld, 'Idee')
    await userEvent.click(screen.getByRole('button', { name: 'Festhalten' }))
    expect(await screen.findByText('Idee festgehalten.')).toBeInTheDocument()
    expect(cards.create).toHaveBeenCalledWith(42, 500, '[Idea] Idee')
  })

  it('meldet einen Fehler, wenn das Board keine Spalte hat', async () => {
    boards.get.mockResolvedValue({ ...produkt, columns: [] })
    seite()
    const feld = await screen.findByRole('textbox', { name: 'Deine Idee' })
    await screen.findByText('Geht ins Backlog von „Produkt“.')
    await userEvent.type(feld, 'Idee')
    await userEvent.click(screen.getByRole('button', { name: 'Festhalten' }))
    expect(await screen.findByText(/Die Idee wurde nicht angelegt/)).toBeInTheDocument()
    expect(cards.create).not.toHaveBeenCalled()
    expect(feld).toHaveValue('Idee')
  })

  it('sperrt den Knopf bei leerer oder nur aus Leerzeichen bestehender Eingabe', async () => {
    seite()
    const feld = await screen.findByRole('textbox', { name: 'Deine Idee' })
    const knopf = screen.getByRole('button', { name: 'Festhalten' })
    expect(knopf).toBeDisabled()
    await userEvent.type(feld, '   ')
    expect(knopf).toBeDisabled()
    await userEvent.type(feld, 'x')
    expect(knopf).toBeEnabled()
  })

  it('sperrt den Knopf über 293 Zeichen und sagt das', async () => {
    seite()
    const feld = await screen.findByRole('textbox', { name: 'Deine Idee' })
    const knopf = screen.getByRole('button', { name: 'Festhalten' })
    await userEvent.click(feld)
    await userEvent.paste('x'.repeat(293))
    expect(knopf).toBeEnabled()
    expect(screen.queryByText(/Zu lang/)).not.toBeInTheDocument()
    await userEvent.paste('y')
    expect(knopf).toBeDisabled()
    expect(screen.getByText('Zu lang: 294 Zeichen, höchstens 293 gehen auf eine Karte.')).toBeInTheDocument()
  })

  it('führt ohne gewähltes Board zu den Einstellungen', async () => {
    gemerkt.mockReturnValue(null)
    seite()
    expect(await screen.findByText('Einstellungen geöffnet')).toBeInTheDocument()
    expect(boards.get).not.toHaveBeenCalled()
  })

  it.each([404, 403])('führt bei einem Board, das es nicht mehr gibt (%i), zu den Einstellungen', async (status) => {
    boards.get.mockRejectedValue(new ApiError(status, 'weg'))
    seite()
    expect(await screen.findByText('Einstellungen geöffnet')).toBeInTheDocument()
  })

  it('bietet nichts zum Verschieben, Bearbeiten, Archivieren oder Löschen', async () => {
    seite()
    await screen.findByText('Geht ins Backlog von „Produkt“.')
    expect(screen.getAllByRole('button').map((b) => b.textContent)).toEqual(['Festhalten'])
  })

  it('führt zu den Einstellungen, wenn das Board beim Abschicken verschwunden ist', async () => {
    boards.get.mockRejectedValueOnce(new TypeError('Failed to fetch')).mockRejectedValueOnce(new ApiError(404, 'weg'))
    seite()
    const feld = await screen.findByRole('textbox', { name: 'Deine Idee' })
    await waitFor(() => expect(boards.get).toHaveBeenCalledTimes(1))
    await userEvent.type(feld, 'Idee')
    await userEvent.click(screen.getByRole('button', { name: 'Festhalten' }))
    expect(await screen.findByText('Einstellungen geöffnet')).toBeInTheDocument()
    expect(cards.create).not.toHaveBeenCalled()
  })

  it('führt ohne Anmeldung zu den Einstellungen', async () => {
    authState.user = null
    seite()
    expect(await screen.findByText('Einstellungen geöffnet')).toBeInTheDocument()
    expect(gemerkt).not.toHaveBeenCalled()
  })

  it('übernimmt kein Board mehr, das erst nach dem Verlassen der Seite ankommt', async () => {
    let liefere: (b: Board) => void = () => {}
    let verwerfe: (e: unknown) => void = () => {}
    boards.get
      .mockReturnValueOnce(new Promise<Board>((r) => (liefere = r)))
      .mockReturnValueOnce(new Promise<Board>((_, f) => (verwerfe = f)))
    seite().unmount()
    liefere(produkt)
    seite().unmount()
    verwerfe(new ApiError(404, 'weg'))
    await Promise.resolve()
    expect(boards.get).toHaveBeenCalledTimes(2)
  })

  it('spricht Englisch, wenn die Installation Englisch spricht', async () => {
    seite('en')
    expect(await screen.findByText('Goes to the backlog of “Produkt”.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('textbox', { name: 'Your idea' }))
    await userEvent.paste('x'.repeat(294))
    expect(screen.getByText('Too long: 294 characters, at most 293 fit on a card.')).toBeInTheDocument()
  })
})
