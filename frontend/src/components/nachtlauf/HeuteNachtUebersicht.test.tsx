import { ThemeProvider } from '@mui/material/styles'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { HeuteNachtKarte } from '../../api/nightRuns'
import { theme } from '../../theme'
import { HeuteNachtUebersicht, LEER_SATZ } from './HeuteNachtUebersicht'

/**
 * Die Übersicht „Heute Nacht“ auf der Runner-Seite (Issue #1455, Plan #1447): die freigegebenen,
 * noch nicht übernommenen Karten des Projekts über alle Boards, je Zeile mit Nummer, Titel, Board,
 * kleinem Stufenstand und Ziel. Zustände sind ohne Farbe lesbar (E6).
 */

const karte = (felder: Partial<HeuteNachtKarte> = {}): HeuteNachtKarte => ({
  number: 1420,
  title: '[Fachlich] Export als CSV',
  boardName: 'Entwicklung',
  start: 'FACHPLAN',
  ziel: 'PAKETE',
  pruefer: null,
  ...felder,
})

function zeige(
  heuteNacht: (projectId: number) => Promise<HeuteNachtKarte[]>,
  onKarteOeffnen: (nummer: number) => void = vi.fn(),
) {
  const api = { heuteNacht: vi.fn(heuteNacht) }
  const view = render(
    <ThemeProvider theme={theme}>
      <HeuteNachtUebersicht projectId={5} api={api} onKarteOeffnen={onKarteOeffnen} />
    </ThemeProvider>,
  )
  return { ...view, api }
}

const NAMEN: Record<string, string> = {
  PLAN: 'Plan',
  REVIEW: 'Prüfung',
  PAKETE: 'Arbeitspakete',
  ABDECKUNG: 'Abdeckung',
  UMSETZUNG: 'Umsetzung',
  VORBEREITUNG: 'Veröffentlichung vorbereitet',
}

const zeile = (nummer: number) => screen.findByTestId(`heute-nacht-karte-${nummer}`)

/** Zustand und sichtbarer Text einer Station im kleinen Stufenstand einer Zeile. */
const station = (zeileEl: HTMLElement, nummer: number, schluessel: string) =>
  within(zeileEl).getByTestId(`heute-nacht-station-${nummer}-${schluessel}`)

describe('HeuteNachtUebersicht', () => {
  it('lädt die Karten des Projekts einmal', async () => {
    const { api } = zeige(() => Promise.resolve([karte()]))
    await zeile(1420)
    expect(api.heuteNacht).toHaveBeenCalledTimes(1)
    expect(api.heuteNacht).toHaveBeenCalledWith(5)
  })

  it('zeigt mehrere Karten aus zwei Boards mit Nummer, Titel und Board', async () => {
    zeige(() =>
      Promise.resolve([
        karte(),
        karte({ number: 1447, title: '[Plan] Stufenleiste', boardName: 'Betrieb', start: 'PLAN' }),
      ]),
    )
    const erste = await zeile(1420)
    expect(screen.getByRole('heading', { name: 'Heute Nacht' })).toBeInTheDocument()
    expect(within(erste).getByText('#1420')).toBeInTheDocument()
    expect(within(erste).getByText('[Fachlich] Export als CSV')).toBeInTheDocument()
    expect(within(erste).getByText('Board: Entwicklung')).toBeInTheDocument()
    const zweite = await zeile(1447)
    expect(within(zweite).getByText('#1447')).toBeInTheDocument()
    expect(within(zweite).getByText('[Plan] Stufenleiste')).toBeInTheDocument()
    expect(within(zweite).getByText('Board: Betrieb')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem', { name: /^#/ })).toHaveLength(2)
    expect(screen.queryByText(LEER_SATZ)).not.toBeInTheDocument()
  })

  it('Ziel „Arbeitspakete“ einer fachlichen Anforderung: Weg ab Plan, Abdeckung eingeschlossen', async () => {
    zeige(() => Promise.resolve([karte({ ziel: 'PAKETE' })]))
    const z = await zeile(1420)
    expect(within(z).getByText('Ziel: Arbeitspakete')).toBeInTheDocument()
    expect(within(z).getByText('Plan → Arbeitspakete')).toBeInTheDocument()
    const erwartet: Record<string, string> = {
      PLAN: 'vorgesehen',
      REVIEW: 'vorgesehen',
      PAKETE: 'Ziel',
      ABDECKUNG: 'vorgesehen',
      UMSETZUNG: 'nicht vorgesehen',
      VORBEREITUNG: 'nicht vorgesehen',
    }
    for (const [schluessel, text] of Object.entries(erwartet)) {
      const s = station(z, 1420, schluessel)
      expect(s.textContent).toBe(`${NAMEN[schluessel]}: ${text}`)
      // Nie allein Farbe (E6): Jede Station trägt ein Symbol neben dem Text.
      expect(within(s).getByTestId(/^heute-nacht-symbol-/)).toBeInTheDocument()
    }
    expect(within(station(z, 1420, 'PAKETE')).getByTestId('heute-nacht-symbol-ziel')).toBeInTheDocument()
    expect(within(station(z, 1420, 'PLAN')).getByTestId('heute-nacht-symbol-vorgesehen')).toBeInTheDocument()
    expect(
      within(station(z, 1420, 'UMSETZUNG')).getByTestId('heute-nacht-symbol-nicht-vorgesehen'),
    ).toBeInTheDocument()
  })

  it('Ziel „Umsetzung“ eines Plans: Plan und Prüfung vor dem Lauf erbracht, Weg ab Arbeitspakete', async () => {
    zeige(() => Promise.resolve([karte({ number: 1447, title: '[Plan] X', start: 'PLAN', ziel: 'UMSETZUNG' })]))
    const z = await zeile(1447)
    expect(within(z).getByText('Ziel: Umsetzung')).toBeInTheDocument()
    expect(within(z).getByText('Arbeitspakete → Umsetzung')).toBeInTheDocument()
    expect(station(z, 1447, 'PLAN').textContent).toBe('Plan: vor dem Lauf erbracht')
    expect(within(station(z, 1447, 'PLAN')).getByTestId('heute-nacht-symbol-erbracht')).toBeInTheDocument()
    expect(station(z, 1447, 'REVIEW').textContent).toBe('Prüfung: vor dem Lauf erbracht')
    expect(station(z, 1447, 'PAKETE').textContent).toBe('Arbeitspakete: vorgesehen')
    expect(station(z, 1447, 'ABDECKUNG').textContent).toBe('Abdeckung: vorgesehen')
    expect(station(z, 1447, 'UMSETZUNG').textContent).toBe('Umsetzung: Ziel')
    expect(station(z, 1447, 'VORBEREITUNG').textContent).toBe('Veröffentlichung vorbereitet: nicht vorgesehen')
  })

  it('Ziel „Veröffentlichung vorbereitet“ trägt die letzte Station als Ziel', async () => {
    zeige(() => Promise.resolve([karte({ ziel: 'VORBEREITUNG' })]))
    const z = await zeile(1420)
    expect(within(z).getByText('Ziel: Veröffentlichung vorbereitet')).toBeInTheDocument()
    expect(station(z, 1420, 'UMSETZUNG').textContent).toBe('Umsetzung: vorgesehen')
    expect(station(z, 1420, 'VORBEREITUNG').textContent).toBe('Veröffentlichung vorbereitet: Ziel')
  })

  it('Ziel „Plan“ lässt die Prüfung und alles danach aus', async () => {
    zeige(() => Promise.resolve([karte({ ziel: 'PLAN' })]))
    const z = await zeile(1420)
    expect(within(z).getByText('Plan → Plan')).toBeInTheDocument()
    expect(station(z, 1420, 'PLAN').textContent).toBe('Plan: Ziel')
    expect(station(z, 1420, 'REVIEW').textContent).toBe('Prüfung: nicht vorgesehen')
    expect(station(z, 1420, 'ABDECKUNG').textContent).toBe('Abdeckung: nicht vorgesehen')
  })

  it('nennt die Prüferzahl, wenn sie gewählt ist, und sonst nichts dazu', async () => {
    zeige(() =>
      Promise.resolve([karte({ pruefer: 2 }), karte({ number: 1421, pruefer: null }), karte({ number: 1422, pruefer: 1 })]),
    )
    expect(within(await zeile(1420)).getByText('Planprüfung: 2 Prüfer')).toBeInTheDocument()
    expect(within(await zeile(1422)).getByText('Planprüfung: 1 Prüfer')).toBeInTheDocument()
    expect(within(await zeile(1421)).queryByText(/Planprüfung/)).not.toBeInTheDocument()
  })

  it('sagt ausdrücklich, wenn keine Karte freigegeben ist', async () => {
    zeige(() => Promise.resolve([]))
    expect(await screen.findByText(LEER_SATZ)).toBeInTheDocument()
    expect(LEER_SATZ).toBe('Für die nächste Nacht ist keine Karte freigegeben.')
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('öffnet mit einem Klick auf die Karte die Kartenansicht', async () => {
    const oeffnen = vi.fn()
    zeige(() => Promise.resolve([karte(), karte({ number: 1447, title: '[Plan] Y' })]), oeffnen)
    await userEvent.click(within(await zeile(1447)).getByRole('button', { name: /#1447/ }))
    expect(oeffnen).toHaveBeenCalledTimes(1)
    expect(oeffnen).toHaveBeenCalledWith(1447)
  })

  it('zeigt bei fehlendem Leserecht (403) nichts', async () => {
    const { container, api } = zeige(() => Promise.reject(new ApiError(403, 'Kein Zugriff')))
    await act(async () => {
      await Promise.resolve()
    })
    expect(api.heuteNacht).toHaveBeenCalledTimes(1)
    expect(container).toBeEmptyDOMElement()
  })

  it('nennt einen anderen Ladefehler in der Übersicht', async () => {
    zeige(() => Promise.reject(new ApiError(500, 'Server kaputt', undefined, 'Server kaputt')))
    expect(
      await screen.findByText('Die Übersicht ließ sich nicht laden: Server kaputt'),
    ).toBeInTheDocument()
  })

  it('nennt einen Fehler ohne Server-Meldung allgemein', async () => {
    zeige(() => Promise.reject(new TypeError('Failed to fetch')))
    expect(
      await screen.findByText('Die Übersicht ließ sich nicht laden: unbekannter Fehler'),
    ).toBeInTheDocument()
  })

  it('ignoriert einen Fehler, der erst nach dem Schließen eintrifft', async () => {
    let ablehnen: (fehler: unknown) => void = () => {}
    const { unmount } = zeige(
      () =>
        new Promise<HeuteNachtKarte[]>((_, reject) => {
          ablehnen = reject
        }),
    )
    unmount()
    ablehnen(new ApiError(500, 'Server kaputt', undefined, 'Server kaputt'))
    await Promise.resolve()
    expect(screen.queryByText(/ließ sich nicht laden/)).not.toBeInTheDocument()
  })

  it('zeigt nichts, solange die Antwort aussteht, und ignoriert eine Antwort nach dem Schließen', async () => {
    let aufloesen: (karten: HeuteNachtKarte[]) => void = () => {}
    const { container, unmount } = zeige(
      () =>
        new Promise<HeuteNachtKarte[]>((resolve) => {
          aufloesen = resolve
        }),
    )
    expect(container).toBeEmptyDOMElement()
    unmount()
    aufloesen([karte()])
    await Promise.resolve()
    expect(screen.queryByTestId('heute-nacht-karte-1420')).not.toBeInTheDocument()
  })
})
