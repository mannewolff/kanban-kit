import { render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { NightRunAnlauf, NightRunUsageView } from '../../api/nightRuns'
import { KartenAnlaeufe, zaehleWiederaufnahmen } from './KartenAnlaeufe'

/** Die Anläufe einer Karte im Karten-Detail (Issue #968). */

const verbrauch = (werte: Partial<NightRunUsageView>): NightRunUsageView => ({
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  ...werte,
})

const anlauf = (werte: Partial<NightRunAnlauf>): NightRunAnlauf => ({
  startedAt: '2026-09-01T22:00:00Z',
  mode: 'IMPLEMENTATION',
  state: 'GREEN',
  errorClass: null,
  durationMs: 60_000,
  commitHash: null,
  usage: null,
  ...werte,
})

const apiMit = (antwort: Promise<NightRunAnlauf[]>) => ({
  anlaeufeDerKarte: vi.fn().mockReturnValue(antwort),
})

const zeige = (anlaeufe: NightRunAnlauf[]) => {
  const api = apiMit(Promise.resolve(anlaeufe))
  render(<KartenAnlaeufe projectId={9} cardNumber={968} api={api} />)
  return api
}

describe('KartenAnlaeufe', () => {
  it('fragt die Anläufe der Karte im Projekt ab', async () => {
    const api = zeige([anlauf({})])

    expect(await screen.findByTestId('karten-anlaeufe')).toBeInTheDocument()
    expect(api.anlaeufeDerKarte).toHaveBeenCalledWith(9, 968)
  })

  it('zeigt die Dauer je Lauf-Art; eine nie gelaufene Art steht als „nicht gelaufen", nicht als 0', async () => {
    zeige([
      anlauf({ mode: 'IMPLEMENTATION', durationMs: 120_000 }),
      anlauf({ mode: 'IMPLEMENTATION', durationMs: 60_000, startedAt: '2026-09-02T22:00:00Z' }),
      anlauf({ mode: 'CHAIN', durationMs: 30_000, startedAt: '2026-09-03T22:00:00Z' }),
    ])

    const umsetzung = await screen.findByTestId('anlaeufe-dauer-IMPLEMENTATION')
    expect(umsetzung).toHaveTextContent('Umsetzungs-Lauf')
    expect(umsetzung).toHaveTextContent('3 Min')
    expect(screen.getByTestId('anlaeufe-dauer-CHAIN')).toHaveTextContent('30 s')

    const pruefung = within(screen.getByTestId('anlaeufe-dauer-REVIEW'))
    expect(pruefung.getByText('nicht gelaufen')).toBeInTheDocument()
    expect(screen.getByTestId('anlaeufe-dauer-REVIEW').textContent).not.toMatch(/0/)
  })

  it('schreibt eine gelaufene Art ohne gemessene Dauer als „nicht gemessen", auch in der Liste', async () => {
    zeige([anlauf({ mode: 'REVIEW', state: 'GREY', durationMs: null })])

    expect(await screen.findByTestId('anlaeufe-dauer-REVIEW')).toHaveTextContent('nicht gemessen')
    const zeile = within(screen.getByTestId('anlaeufe-liste')).getByRole('listitem')
    expect(zeile).toHaveTextContent('Dauer nicht gemessen')
  })

  it('schreibt nach dem Schließen nichts mehr, wenn die Ablehnung erst dann eintrifft', async () => {
    let ablehnen: (grund: unknown) => void = () => undefined
    const api = apiMit(
      new Promise<NightRunAnlauf[]>((_, reject) => {
        ablehnen = reject
      }),
    )
    const { unmount } = render(<KartenAnlaeufe projectId={9} cardNumber={968} api={api} />)
    unmount()

    ablehnen(new ApiError(500, 'Server Error'))
    await Promise.resolve()

    expect(screen.queryByText('Die Anläufe konnten nicht geladen werden.')).not.toBeInTheDocument()
  })

  it('schreibt nach dem Schließen nichts mehr, wenn die Antwort erst dann eintrifft', async () => {
    let liefern: (anlaeufe: NightRunAnlauf[]) => void = () => undefined
    const api = apiMit(
      new Promise<NightRunAnlauf[]>((resolve) => {
        liefern = resolve
      }),
    )
    const { unmount } = render(<KartenAnlaeufe projectId={9} cardNumber={968} api={api} />)
    unmount()

    liefern([anlauf({})])
    await Promise.resolve()

    expect(screen.queryByTestId('karten-anlaeufe')).not.toBeInTheDocument()
  })

  it('nennt NIGHTPLAN in keiner Beschriftung — der Modus wird nie eingeliefert', async () => {
    zeige([anlauf({})])

    const block = await screen.findByTestId('karten-anlaeufe')
    expect(block.textContent).not.toMatch(/NIGHTPLAN|Nachtplan/i)
  })

  it('nennt die Grundlage einer Summe, zu der nicht jeder Anlauf beitrug', async () => {
    zeige([
      anlauf({ usage: verbrauch({ costUsd: 1.5 }) }),
      anlauf({ usage: verbrauch({ costUsd: 0.5 }), startedAt: '2026-09-02T22:00:00Z' }),
      anlauf({ usage: null, startedAt: '2026-09-03T22:00:00Z' }),
    ])

    const kosten = await screen.findByTestId('anlaeufe-summe-kosten')
    expect(kosten.textContent?.replaceAll(' ', ' ')).toContain('2,00 $')
    expect(kosten).toHaveTextContent('aus 2 von 3 Anläufen')
  })

  it('schreibt eine Summe ohne jeden Wert als „nicht gemessen"', async () => {
    zeige([anlauf({ usage: verbrauch({ costUsd: 1.5 }) }), anlauf({ usage: null })])

    const eingabe = await screen.findByTestId('anlaeufe-summe-eingabe')
    expect(eingabe).toHaveTextContent('nicht gemessen')
    expect(eingabe).not.toHaveTextContent('aus 0 von')
  })

  it('nennt jede Menge mit ihrer Einheit', async () => {
    zeige([anlauf({ usage: verbrauch({ inputTokens: 1200, outputTokens: 30, cachedInputTokens: 900 }) })])

    expect(await screen.findByTestId('anlaeufe-summe-eingabe')).toHaveTextContent('1.200 Token')
    expect(screen.getByTestId('anlaeufe-summe-ausgabe')).toHaveTextContent('30 Token')
    expect(screen.getByTestId('anlaeufe-summe-zwischenspeicher')).toHaveTextContent('900 Token')
  })

  it('listet die Anläufe mit Ergebnis, Dauer und Kosten, jüngster zuerst', async () => {
    zeige([
      anlauf({ state: 'GREEN', startedAt: '2026-09-03T22:00:00Z', usage: verbrauch({ costUsd: 0.94 }) }),
      anlauf({ state: 'RED', errorClass: 'CHECKS_RED', startedAt: '2026-09-01T22:00:00Z' }),
    ])

    const zeilen = within(await screen.findByTestId('anlaeufe-liste')).getAllByRole('listitem')
    expect(zeilen).toHaveLength(2)
    expect(zeilen[0]).toHaveTextContent('Erfolg')
    expect(zeilen[0].textContent?.replaceAll(' ', ' ')).toContain('0,94 $')
    expect(zeilen[1]).toHaveTextContent('1 Min')
    expect(zeilen[1]).toHaveTextContent('nicht gemessen')
  })

  it('zeigt die Zahl der Wiederaufnahmen', async () => {
    zeige([
      anlauf({ state: 'GREEN', startedAt: '2026-09-02T22:00:00Z' }),
      anlauf({ state: 'RED', startedAt: '2026-09-01T22:00:00Z' }),
    ])

    expect(await screen.findByTestId('anlaeufe-wiederaufnahmen')).toHaveTextContent('Wiederaufnahmen: 1')
  })

  it('zeigt nichts und meldet nichts, wenn der Server 403 antwortet', async () => {
    const api = apiMit(Promise.reject(new ApiError(403, 'Forbidden')))
    const { container } = render(<KartenAnlaeufe projectId={9} cardNumber={968} api={api} />)

    await waitFor(() => expect(api.anlaeufeDerKarte).toHaveBeenCalled())
    await Promise.resolve()
    expect(container).toBeEmptyDOMElement()
  })

  it('zeigt nichts bei 404', async () => {
    const api = apiMit(Promise.reject(new ApiError(404, 'Not Found')))
    const { container } = render(<KartenAnlaeufe projectId={9} cardNumber={968} api={api} />)

    await waitFor(() => expect(api.anlaeufeDerKarte).toHaveBeenCalled())
    await Promise.resolve()
    expect(container).toBeEmptyDOMElement()
  })

  it('sagt es, wenn das Laden aus einem anderen Grund scheitert', async () => {
    const api = apiMit(Promise.reject(new ApiError(500, 'Server Error')))
    render(<KartenAnlaeufe projectId={9} cardNumber={968} api={api} />)

    expect(await screen.findByText('Die Anläufe konnten nicht geladen werden.')).toBeInTheDocument()
  })

  it('zeigt nichts, solange die Karte in keinem Lauf vorkam', async () => {
    const api = apiMit(Promise.resolve([]))
    const { container } = render(<KartenAnlaeufe projectId={9} cardNumber={968} api={api} />)

    await waitFor(() => expect(api.anlaeufeDerKarte).toHaveBeenCalled())
    await Promise.resolve()
    expect(container).toBeEmptyDOMElement()
  })
})

describe('zaehleWiederaufnahmen', () => {
  const am = (tag: number, state: NightRunAnlauf['state']) =>
    anlauf({ state, startedAt: `2026-09-0${tag}T22:00:00Z` })

  it('zählt ein RED, auf das ein GREEN folgt', () => {
    expect(zaehleWiederaufnahmen([am(2, 'GREEN'), am(1, 'RED')])).toBe(1)
  })

  it('zählt ein RED nicht, auf das nur ein GREY folgt', () => {
    expect(zaehleWiederaufnahmen([am(2, 'GREY'), am(1, 'RED')])).toBe(0)
  })

  it('zählt ein RED als letzten Anlauf nicht', () => {
    expect(zaehleWiederaufnahmen([am(2, 'RED'), am(1, 'GREEN')])).toBe(0)
  })

  it('zählt ein RED, das nach einem GREY doch noch aufgenommen wurde', () => {
    expect(zaehleWiederaufnahmen([am(3, 'YELLOW'), am(2, 'GREY'), am(1, 'RED')])).toBe(1)
  })

  it('zählt jedes RED mit späterem Anlauf einzeln', () => {
    expect(zaehleWiederaufnahmen([am(3, 'GREEN'), am(2, 'RED'), am(1, 'RED')])).toBe(2)
  })
})
