import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../../api/boards'
import { ApiError } from '../../api/client'
import { projectsApi } from '../../api/projects'
import { pushApi } from '../../api/push'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { leseMobilBoard, schreibeMobilBoard } from '../../lib/mobilBoard'
import { abmelden, bestehendesAbo, einschalten, kannPush, vomStartbildschirm } from '../../lib/pushAbo'
import { MobilEinstellungenPage } from './MobilEinstellungenPage'

const authState = vi.hoisted(() => ({
  user: { userId: 7, platformRole: 'USER' } as { userId: number; platformRole: string } | null,
  vorschau: null as { kennung: string } | null,
}))
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => authState }))
vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../../api/boards', () => ({ boardsApi: { list: vi.fn() } }))
vi.mock('../../lib/mobilBoard', () => ({ leseMobilBoard: vi.fn(), schreibeMobilBoard: vi.fn() }))
vi.mock('../../api/push', () => ({ pushApi: { schluessel: vi.fn(), boards: vi.fn(), boardSchalten: vi.fn() } }))
vi.mock('../../lib/pushAbo', () => ({
  kannPush: vi.fn(),
  vomStartbildschirm: vi.fn(),
  bestehendesAbo: vi.fn(),
  einschalten: vi.fn(),
  abmelden: vi.fn(),
}))

const projects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const boards = boardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const gemerkt = vi.mocked(leseMobilBoard)
const merke = vi.mocked(schreibeMobilBoard)
const push = vi.mocked(pushApi)
const pushMoeglich = vi.mocked(kannPush)
const startbildschirm = vi.mocked(vomStartbildschirm)
const aboLesen = vi.mocked(bestehendesAbo)
const aboEin = vi.mocked(einschalten)
const aboAus = vi.mocked(abmelden)

const ENDPUNKT = 'https://push.example/abc'
const abo = { endpoint: ENDPUNKT } as PushSubscription
const pushBoard = (boardId: number, boardName: string, an = true) => ({
  boardId,
  boardName,
  projectId: 1,
  projectName: 'Alpha',
  an,
})

const board = (id: number, projectId: number, name: string) => ({ id, projectId, name, createdAt: '', columns: [] })

function seite(sprache: 'de' | 'en' = 'de') {
  return renderMitSprache(
    <MemoryRouter>
      <MobilEinstellungenPage />
    </MemoryRouter>,
    sprache,
  )
}

describe('MobilEinstellungenPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { userId: 7, platformRole: 'USER' }
    authState.vorschau = null
    gemerkt.mockReturnValue(null)
    pushMoeglich.mockReturnValue(true)
    startbildschirm.mockReturnValue(true)
    push.schluessel.mockResolvedValue({ publicKey: 'BAA' })
    push.boards.mockResolvedValue([pushBoard(10, 'Produkt'), pushBoard(11, 'Technik', false)])
    push.boardSchalten.mockResolvedValue(undefined)
    aboLesen.mockResolvedValue(null)
    aboEin.mockResolvedValue(abo)
    aboAus.mockResolvedValue(undefined)
    projects.list.mockResolvedValue([
      { id: 1, name: 'Alpha', role: 'MEMBER', createdAt: '' },
      { id: 2, name: 'Beta', role: 'VIEWER', createdAt: '' },
    ])
    boards.list.mockImplementation((projectId: number) =>
      Promise.resolve(projectId === 1 ? [board(10, 1, 'Produkt'), board(11, 1, 'Technik')] : [board(20, 2, 'Lesen')]),
    )
  })

  it('bietet nur Boards der Projekte an, in denen Karten angelegt werden dürfen', async () => {
    seite()
    const gruppe = await screen.findByRole('radiogroup', { name: 'Standard-Board' })
    expect(await within(gruppe).findByRole('radio', { name: 'Alpha · Produkt' })).toBeInTheDocument()
    expect(within(gruppe).getByRole('radio', { name: 'Alpha · Technik' })).toBeInTheDocument()
    expect(within(gruppe).queryByRole('radio', { name: 'Beta · Lesen' })).not.toBeInTheDocument()
    expect(boards.list).toHaveBeenCalledWith(1)
    expect(boards.list).not.toHaveBeenCalledWith(2)
  })

  it('bietet einem Plattform-Admin auch Boards an, in denen er nur liest', async () => {
    authState.user = { userId: 7, platformRole: 'ADMIN' }
    seite()
    expect(await screen.findByRole('radio', { name: 'Beta · Lesen' })).toBeInTheDocument()
  })

  it('merkt sich die Wahl auf diesem Gerät für das Konto', async () => {
    seite()
    await userEvent.click(await screen.findByRole('radio', { name: 'Alpha · Technik' }))
    expect(merke).toHaveBeenCalledWith(7, 11)
    expect(screen.getByRole('radio', { name: 'Alpha · Technik' })).toBeChecked()
    expect(screen.getByText('Gespeichert.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Zur Idee' })).toHaveAttribute('href', '/mobil')
  })

  it('zeigt die gemerkte Wahl', async () => {
    gemerkt.mockReturnValue(10)
    seite()
    expect(await screen.findByRole('radio', { name: 'Alpha · Produkt' })).toBeChecked()
    expect(screen.queryByText(/gibt es nicht mehr/)).not.toBeInTheDocument()
  })

  it('verlangt eine neue Wahl, wenn es das gemerkte Board nicht mehr gibt', async () => {
    gemerkt.mockReturnValue(99)
    seite()
    expect(await screen.findByText('Das gewählte Board gibt es nicht mehr. Bitte wähle ein neues.')).toBeInTheDocument()
    expect(screen.getAllByRole('radio').every((r) => !(r as HTMLInputElement).checked)).toBe(true)
    await userEvent.click(screen.getByRole('radio', { name: 'Alpha · Produkt' }))
    expect(merke).toHaveBeenCalledWith(7, 10)
    expect(screen.queryByText(/gibt es nicht mehr/)).not.toBeInTheDocument()
  })

  it('sagt es, wenn es kein Projekt mit Anlegerecht gibt', async () => {
    projects.list.mockResolvedValue([{ id: 2, name: 'Beta', role: 'VIEWER', createdAt: '' }])
    seite()
    expect(await screen.findByText('Du bist in keinem Projekt, in dem du Karten anlegen darfst.')).toBeInTheDocument()
  })

  it('meldet, wenn die Boards nicht geladen werden können', async () => {
    projects.list.mockRejectedValue(new TypeError('Failed to fetch'))
    seite()
    expect(await screen.findByText('Die Boards konnten nicht geladen werden.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('progressbar')).not.toBeInTheDocument())
  })

  it('merkt ohne Anmeldung nichts', async () => {
    authState.user = null
    seite()
    await userEvent.click(await screen.findByRole('radio', { name: 'Alpha · Produkt' }))
    expect(gemerkt).not.toHaveBeenCalled()
    expect(merke).not.toHaveBeenCalled()
    expect(screen.queryByText('Gespeichert.')).not.toBeInTheDocument()
  })

  it('übernimmt nach dem Verlassen der Seite weder Boards noch Fehler', async () => {
    let liefere: (p: unknown[]) => void = () => {}
    let verwerfe: (e: unknown) => void = () => {}
    projects.list
      .mockReturnValueOnce(new Promise<unknown[]>((r) => (liefere = r)))
      .mockReturnValueOnce(new Promise<unknown[]>((_, f) => (verwerfe = f)))
    seite().unmount()
    liefere([])
    seite().unmount()
    verwerfe(new TypeError('Failed to fetch'))
    await Promise.resolve()
    expect(projects.list).toHaveBeenCalledTimes(2)
  })

  it('spricht Englisch, wenn die Installation Englisch spricht', async () => {
    seite('en')
    expect(await screen.findByRole('radio', { name: 'Alpha · Produkt' })).toBeInTheDocument()
    expect(screen.getByRole('radiogroup', { name: 'Default board' })).toBeInTheDocument()
  })

  describe('Benachrichtigungen', () => {
    const hauptschalter = () => screen.findByRole('switch', { name: 'Benachrichtigungen auf diesem Gerät' })

    it('schaltet sie ein und zeigt danach die Boards mit ihren Schaltern', async () => {
      seite()
      const schalter = await hauptschalter()
      expect(schalter).not.toBeChecked()
      expect(screen.queryByRole('switch', { name: 'Alpha · Produkt' })).not.toBeInTheDocument()
      await userEvent.click(schalter)
      expect(aboEin).toHaveBeenCalledWith('BAA')
      expect(await screen.findByRole('switch', { name: 'Alpha · Produkt' })).toBeChecked()
      expect(screen.getByRole('switch', { name: 'Alpha · Technik' })).not.toBeChecked()
      expect(push.boards).toHaveBeenCalledWith(ENDPUNKT)
      expect(screen.getByRole('switch', { name: 'Benachrichtigungen auf diesem Gerät' })).toBeChecked()
    })

    it('zeigt ein bestehendes Abo als eingeschaltet', async () => {
      aboLesen.mockResolvedValue(abo)
      seite()
      expect(await screen.findByRole('switch', { name: 'Alpha · Produkt' })).toBeChecked()
      expect(await hauptschalter()).toBeChecked()
    })

    it('schaltet ein Board ab und wieder ein', async () => {
      aboLesen.mockResolvedValue(abo)
      seite()
      const produkt = await screen.findByRole('switch', { name: 'Alpha · Produkt' })
      await userEvent.click(produkt)
      expect(push.boardSchalten).toHaveBeenCalledWith(ENDPUNKT, 10, false)
      await waitFor(() => expect(produkt).not.toBeChecked())
      await userEvent.click(screen.getByRole('switch', { name: 'Alpha · Technik' }))
      expect(push.boardSchalten).toHaveBeenLastCalledWith(ENDPUNKT, 11, true)
      await waitFor(() => expect(screen.getByRole('switch', { name: 'Alpha · Technik' })).toBeChecked())
    })

    it('stellt einen Board-Schalter zurück, wenn das Schalten scheitert', async () => {
      aboLesen.mockResolvedValue(abo)
      push.boardSchalten.mockRejectedValue(new TypeError('Failed to fetch'))
      seite()
      const produkt = await screen.findByRole('switch', { name: 'Alpha · Produkt' })
      await userEvent.click(produkt)
      expect(await screen.findByText('Das hat nicht geklappt. Versuch es gleich noch einmal.')).toBeInTheDocument()
      expect(produkt).toBeChecked()
    })

    it('sagt es, wenn es kein schaltbares Board gibt', async () => {
      aboLesen.mockResolvedValue(abo)
      push.boards.mockResolvedValue([])
      seite()
      expect(await screen.findByText('Es gibt kein Board, zu dessen Läufen Nachrichten kommen können.')).toBeInTheDocument()
    })

    it('meldet, wenn die Boards nicht geladen werden können', async () => {
      aboLesen.mockResolvedValue(abo)
      push.boards.mockRejectedValue(new TypeError('Failed to fetch'))
      seite()
      expect(await screen.findByText('Die Benachrichtigungen konnten nicht geladen werden.')).toBeInTheDocument()
    })

    it('schaltet sie aus und beendet das Abo', async () => {
      aboLesen.mockResolvedValue(abo)
      seite()
      await screen.findByRole('switch', { name: 'Alpha · Produkt' })
      await userEvent.click(await hauptschalter())
      expect(aboAus).toHaveBeenCalled()
      await waitFor(() => expect(screen.queryByRole('switch', { name: 'Alpha · Produkt' })).not.toBeInTheDocument())
      expect(screen.getByRole('switch', { name: 'Benachrichtigungen auf diesem Gerät' })).not.toBeChecked()
    })

    it('bleibt eingeschaltet, wenn das Ausschalten scheitert', async () => {
      aboLesen.mockResolvedValue(abo)
      aboAus.mockRejectedValue(new TypeError('Failed to fetch'))
      seite()
      await screen.findByRole('switch', { name: 'Alpha · Produkt' })
      await userEvent.click(await hauptschalter())
      expect(await screen.findByText('Das hat nicht geklappt. Versuch es gleich noch einmal.')).toBeInTheDocument()
      expect(screen.getByRole('switch', { name: 'Benachrichtigungen auf diesem Gerät' })).toBeChecked()
    })

    it('sagt es, wenn die Erlaubnis fehlt', async () => {
      aboEin.mockResolvedValue(null)
      seite()
      await userEvent.click(await hauptschalter())
      expect(
        await screen.findByText(
          'Benachrichtigungen sind für diese Seite nicht erlaubt. Erlaube sie in den Einstellungen des Geräts.',
        ),
      ).toBeInTheDocument()
      expect(screen.getByRole('switch', { name: 'Benachrichtigungen auf diesem Gerät' })).not.toBeChecked()
    })

    it('sagt es, wenn das Einschalten scheitert', async () => {
      aboEin.mockRejectedValue(new TypeError('Failed to fetch'))
      seite()
      await userEvent.click(await hauptschalter())
      expect(await screen.findByText('Das hat nicht geklappt. Versuch es gleich noch einmal.')).toBeInTheDocument()
      expect(screen.getByRole('switch', { name: 'Benachrichtigungen auf diesem Gerät' })).not.toBeChecked()
    })

    it('zeigt ohne Push und nicht vom Startbildschirm den Weg über „Zum Home-Bildschirm“', async () => {
      pushMoeglich.mockReturnValue(false)
      startbildschirm.mockReturnValue(false)
      seite()
      expect(await screen.findByText(/Im Teilen-Menü „Zum Home-Bildschirm“ wählen/)).toBeInTheDocument()
      expect(screen.queryByRole('switch', { name: 'Benachrichtigungen auf diesem Gerät' })).not.toBeInTheDocument()
      expect(push.schluessel).not.toHaveBeenCalled()
    })

    it('sagt ohne Push vom Startbildschirm, dass das Gerät keine Benachrichtigungen kann', async () => {
      pushMoeglich.mockReturnValue(false)
      seite()
      expect(await screen.findByText('Dieses Gerät kann keine Benachrichtigungen.')).toBeInTheDocument()
      expect(screen.queryByText(/Zum Home-Bildschirm/)).not.toBeInTheDocument()
      expect(screen.queryByRole('switch', { name: 'Benachrichtigungen auf diesem Gerät' })).not.toBeInTheDocument()
    })

    it('sagt es, wenn der Server keine Benachrichtigungen eingerichtet hat', async () => {
      push.schluessel.mockRejectedValue(new ApiError(404, 'nicht eingerichtet'))
      seite()
      expect(await screen.findByText('Benachrichtigungen sind nicht eingerichtet.')).toBeInTheDocument()
      expect(screen.queryByRole('switch', { name: 'Benachrichtigungen auf diesem Gerät' })).not.toBeInTheDocument()
      expect(aboLesen).not.toHaveBeenCalled()
    })

    it('meldet, wenn der Schlüssel nicht geladen werden kann', async () => {
      push.schluessel.mockRejectedValue(new TypeError('Failed to fetch'))
      seite()
      expect(await screen.findByText('Die Benachrichtigungen konnten nicht geladen werden.')).toBeInTheDocument()
      expect(screen.queryByRole('switch', { name: 'Benachrichtigungen auf diesem Gerät' })).not.toBeInTheDocument()
    })

    it('sperrt die Schalter in der Vorschau', async () => {
      authState.vorschau = { kennung: 'vk-1' }
      aboLesen.mockResolvedValue(abo)
      seite()
      expect(await screen.findByRole('switch', { name: 'Alpha · Produkt' })).toBeDisabled()
      expect(await hauptschalter()).toBeDisabled()
      expect(screen.getByText('In der Vorschau lassen sich Benachrichtigungen nicht schalten.')).toBeInTheDocument()
    })

    it('übernimmt nach dem Verlassen der Seite nichts mehr', async () => {
      let liefere: (k: { publicKey: string }) => void = () => {}
      push.schluessel.mockReturnValueOnce(new Promise((r) => (liefere = r)))
      seite().unmount()
      liefere({ publicKey: 'BAA' })
      await Promise.resolve()
      expect(aboLesen).not.toHaveBeenCalled()
    })

    it('übernimmt nach dem Verlassen der Seite auch keinen Fehler', async () => {
      let verwerfe: (e: unknown) => void = () => {}
      push.schluessel.mockReturnValueOnce(new Promise((_, f) => (verwerfe = f)))
      seite().unmount()
      verwerfe(new TypeError('Failed to fetch'))
      await Promise.resolve()
      expect(push.schluessel).toHaveBeenCalledTimes(1)
    })

    it('meldet ein gescheitertes Board-Schalten auch, wenn die Benachrichtigungen inzwischen aus sind', async () => {
      aboLesen.mockResolvedValue(abo)
      let verwerfe: (e: unknown) => void = () => {}
      push.boardSchalten.mockReturnValueOnce(new Promise((_, f) => (verwerfe = f)))
      seite()
      await userEvent.click(await screen.findByRole('switch', { name: 'Alpha · Produkt' }))
      await userEvent.click(await hauptschalter())
      await waitFor(() => expect(screen.queryByRole('switch', { name: 'Alpha · Produkt' })).not.toBeInTheDocument())
      verwerfe(new TypeError('Failed to fetch'))
      expect(await screen.findByText('Das hat nicht geklappt. Versuch es gleich noch einmal.')).toBeInTheDocument()
      expect(screen.queryByRole('switch', { name: 'Alpha · Produkt' })).not.toBeInTheDocument()
    })

    it('spricht Englisch, wenn die Installation Englisch spricht', async () => {
      seite('en')
      expect(await screen.findByRole('switch', { name: 'Notifications on this device' })).toBeInTheDocument()
    })
  })
})
