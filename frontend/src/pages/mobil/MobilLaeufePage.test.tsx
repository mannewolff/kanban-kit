import { screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mobilApi, type MobilerLauf } from '../../api/mobil'
import { formatierer } from '../../i18n/format'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { MobilLaeufePage } from './MobilLaeufePage'

vi.mock('../../api/mobil', () => ({ mobilApi: { laeufe: vi.fn(), lauf: vi.fn() } }))

const laeufe = vi.mocked(mobilApi.laeufe)

const lauf = (teil: Partial<MobilerLauf> = {}): MobilerLauf => ({
  laufId: 4711,
  projectId: 7,
  projectName: 'Leitstand',
  boardId: 40,
  boardName: 'Produkt',
  zustand: 'GESCHEITERT',
  startedAt: '2026-10-28T22:00:00Z',
  durationMs: 3_600_000,
  ...teil,
})

function seite(sprache: 'de' | 'en' = 'de') {
  return renderMitSprache(
    <MemoryRouter>
      <MobilLaeufePage />
    </MemoryRouter>,
    sprache,
  )
}

describe('MobilLaeufePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('zeigt je Lauf Projekt, Board, Zustand, Beginn und Dauer und öffnet ihn per Tipp', async () => {
    laeufe.mockResolvedValue([lauf()])
    seite()
    const eintrag = await screen.findByRole('link', { name: /Leitstand · Produkt/ })
    expect(eintrag).toHaveAttribute('href', '/mobil/laeufe/4711')
    expect(within(eintrag).getByText('gescheitert')).toBeInTheDocument()
    const beginn = formatierer('de').datumUhrzeit('2026-10-28T22:00:00Z')
    expect(within(eintrag).getByText(`Beginn ${beginn} · Dauer ${formatierer('de').dauer(3600)}`)).toBeInTheDocument()
    expect(laeufe).toHaveBeenCalledTimes(1)
  })

  it('nennt „alle Boards“, wenn der Lauf keinem Board zugeordnet ist', async () => {
    laeufe.mockResolvedValue([lauf({ boardId: null, boardName: null, zustand: 'LAEUFT' })])
    seite()
    const eintrag = await screen.findByRole('link', { name: /Leitstand · alle Boards/ })
    expect(within(eintrag).getByText('läuft')).toBeInTheDocument()
  })

  it('Szenario: Benachrichtigungen für ein Board abgeschaltet', async () => {
    // Angenommen ein Mitglied hat Benachrichtigungen für das Board „Spielwiese“ ausgeschaltet;
    // die Liste fragt danach nicht: Ein hängender Lauf auf „Spielwiese“ steht trotzdem darin.
    laeufe.mockResolvedValue([lauf({ laufId: 12, boardId: 41, boardName: 'Spielwiese', zustand: 'HAENGT' })])
    seite()
    const eintrag = await screen.findByRole('link', { name: /Leitstand · Spielwiese/ })
    expect(within(eintrag).getByText('hängt')).toBeInTheDocument()
    expect(eintrag).toHaveAttribute('href', '/mobil/laeufe/12')
  })

  it('zeigt mehrere Läufe in der Reihenfolge des Servers', async () => {
    laeufe.mockResolvedValue([
      lauf({ laufId: 2, projectName: 'Zwei' }),
      lauf({ laufId: 1, projectName: 'Eins' }),
    ])
    seite()
    await screen.findByRole('link', { name: /Zwei/ })
    const links = within(screen.getByRole('list')).getAllByRole('link')
    expect(links.map((l) => l.getAttribute('href'))).toEqual(['/mobil/laeufe/2', '/mobil/laeufe/1'])
  })

  it('sagt ruhig Bescheid, wenn nichts in der Liste steht', async () => {
    laeufe.mockResolvedValue([])
    seite()
    expect(await screen.findByText('Gerade läuft nichts, und nichts hängt oder ist gescheitert.')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('meldet, wenn die Läufe nicht geladen werden konnten', async () => {
    laeufe.mockRejectedValue(new Error('offline'))
    seite()
    expect(await screen.findByText('Die Läufe konnten nicht geladen werden.')).toBeInTheDocument()
    expect(screen.queryByText(/Gerade läuft nichts/)).not.toBeInTheDocument()
  })

  it('zeigt eine Überschrift und bietet nichts zum Ändern an', async () => {
    laeufe.mockResolvedValue([lauf()])
    seite()
    expect(screen.getByRole('heading', { name: 'Läufe' })).toBeInTheDocument()
    await screen.findByRole('link', { name: /Leitstand/ })
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('schreibt in der Sprache der Installation', async () => {
    laeufe.mockResolvedValue([lauf({ boardId: null, boardName: null, zustand: 'HAENGT' })])
    seite('en')
    const eintrag = await screen.findByRole('link', { name: /Leitstand · all boards/ })
    expect(within(eintrag).getByText('stalled')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Runs' })).toBeInTheDocument()
  })
})
