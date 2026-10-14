import { act, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { Wartegruende } from '../../api/nightRuns'
import { de } from '../../i18n/de'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { tagZeit } from '../../lib/leitstand'
import { Wartegrund } from './Wartegrund'

/**
 * Der Abschnitt „Wartegrund“ im Kartendialog (Issue #1730, Plan #1724 E9 bis E12): je Grund ein
 * Satz, „startbereit“, die Vermerke über das Alter der Bewertung und „Pakete davor“. Ob die Karte
 * in Ready steht, entscheidet der Server — bei 403 und 404 erscheint nichts.
 */

const t = de.WARTEGRUND

const leer: Wartegruende = { runReasons: [], cardReasons: [], packagesAhead: null, notes: [], startable: false }

function zeige(wartegrund: (cardId: number) => Promise<Wartegruende>) {
  const api = { wartegrund: vi.fn(wartegrund) }
  const view = render(<Wartegrund cardId={77} api={api} />)
  return { ...view, api }
}

const abschnitt = () => screen.findByTestId('wartegrund')

describe('Wartegrund', () => {
  it('Szenario: Eine Kette belegt die Umsetzung', async () => {
    const { api } = zeige(() =>
      Promise.resolve({
        ...leer,
        runReasons: [
          { code: 'KEINE_UMSETZUNGSNACHT', since: null, stage: null, runMode: null, cardNumber: null },
          { code: 'BELEGT', since: null, stage: 'VORBEREITUNG', runMode: 'CHAIN', cardNumber: 1665 },
        ],
        packagesAhead: 0,
      }),
    )

    const a = await abschnitt()
    expect(api.wartegrund).toHaveBeenCalledWith(77)
    expect(within(a).getByText('Wartegrund')).toBeInTheDocument()
    expect(within(a).getByText('keine Umsetzungsnacht aktiv')).toBeInTheDocument()
    expect(
      within(a).getByText('Umsetzung belegt durch einen anderen Lauf: Kette #1665, Stufe Vorbereitung'),
    ).toBeInTheDocument()
    expect(within(a).queryByText(t.STARTBEREIT)).not.toBeInTheDocument()
  })

  it('nennt eine belegende Umsetzungsnacht mit Art und Karte, ohne Stufe', async () => {
    zeige(() =>
      Promise.resolve({
        ...leer,
        runReasons: [{ code: 'BELEGT', since: null, stage: 'UMSETZUNG', runMode: 'IMPLEMENTATION', cardNumber: 1700 }],
      }),
    )

    expect(
      within(await abschnitt()).getByText('Umsetzung belegt durch einen anderen Lauf: Umsetzungsnacht #1700'),
    ).toBeInTheDocument()
  })

  it('Szenario: Mehrere Gründe zugleich', async () => {
    zeige(() =>
      Promise.resolve({
        ...leer,
        cardReasons: [
          { code: 'DEPENDENCY', kind: null, cardNumbers: [1698, 1699], paths: [], text: 'Abhaengigkeit' },
          { code: 'NO_LABEL', kind: null, cardNumbers: [], paths: [], text: "kein Label 'kit:nightrun'" },
        ],
      }),
    )

    const a = await abschnitt()
    expect(within(a).getByText('wartet auf eine andere Karte (#1698, #1699)')).toBeInTheDocument()
    expect(within(a).getByText('nicht für die Nacht freigegeben (kit:nightrun fehlt)')).toBeInTheDocument()
    expect(within(a).getAllByRole('listitem')).toHaveLength(2)
  })

  it('Szenario: Startbereit', async () => {
    zeige(() => Promise.resolve({ ...leer, packagesAhead: 0, startable: true }))

    const a = await abschnitt()
    expect(within(a).getByText('startbereit – kein Hinderungsgrund festgestellt')).toBeInTheDocument()
    expect(within(a).getByText('Pakete davor: 0')).toBeInTheDocument()
  })

  it('Szenario: Der Runner verstummt', async () => {
    const since = '2026-10-13T23:05:00Z'
    zeige(() =>
      Promise.resolve({
        ...leer,
        runReasons: [
          { code: 'VERSTUMMT', since, stage: 'UMSETZUNG', runMode: 'IMPLEMENTATION', cardNumber: null },
          { code: 'VERSTUMMT', since, stage: null, runMode: 'IMPLEMENTATION', cardNumber: null },
        ],
      }),
    )

    const a = await abschnitt()
    // Derselbe Schlüssel wie im Bereich „Runner jetzt“: RUNNER_JETZT.VERSTUMMT.
    expect(within(a).getByText(de.RUNNER_JETZT.VERSTUMMT(tagZeit(since), 'Umsetzung'))).toBeInTheDocument()
    expect(within(a).getByText(de.RUNNER_JETZT.VERSTUMMT(tagZeit(since), 'nicht gemeldet'))).toBeInTheDocument()
  })

  it('Szenario: Ein Mitglied ohne Owner-Rolle sieht nichts Neues', async () => {
    const { api, container } = zeige(() => Promise.reject(new ApiError(403, 'Forbidden')))

    await act(async () => {
      await Promise.resolve()
    })
    expect(api.wartegrund).toHaveBeenCalledTimes(1)
    expect(container).toBeEmptyDOMElement()
  })

  it('zeigt bei 404 keinen Abschnitt — die Karte steht nicht in Ready', async () => {
    const { container } = zeige(() => Promise.reject(new ApiError(404, 'Not Found')))

    await act(async () => {
      await Promise.resolve()
    })
    expect(container).toBeEmptyDOMElement()
  })

  it('zeigt einen anderen Ladefehler im Abschnitt', async () => {
    zeige(() => Promise.reject(new ApiError(500, 'x', undefined, 'kaputt')))

    expect(within(await abschnitt()).getByText(t.LADEFEHLER('kaputt'))).toBeInTheDocument()
  })

  it('nennt beide Vermerke über das Alter der Bewertung', async () => {
    const am = '2026-10-13T22:40:00Z'
    zeige(() =>
      Promise.resolve({
        ...leer,
        notes: [
          { code: 'NICHT_BEWERTET', assessedAt: am },
          { code: 'NICHT_BEWERTET', assessedAt: null },
          { code: 'SEIT_BEWERTUNG_GEAENDERT', assessedAt: am },
        ],
      }),
    )

    const a = await abschnitt()
    expect(within(a).getByText(t.NICHT_BEWERTET(tagZeit(am)))).toBeInTheDocument()
    expect(within(a).getByText('noch nicht vom Runner bewertet (noch keine Bewertung)')).toBeInTheDocument()
    expect(within(a).getByText('seit der Bewertung geändert')).toBeInTheDocument()
  })

  it('nennt „Pakete davor“', async () => {
    zeige(() => Promise.resolve({ ...leer, packagesAhead: 2, startable: true }))

    expect(within(await abschnitt()).getByText('Pakete davor: 2')).toBeInTheDocument()
  })

  it('zeigt einen unbekannten Code mit dem Wortlaut des Runners', async () => {
    zeige(() =>
      Promise.resolve({
        ...leer,
        cardReasons: [{ code: 'GANZ_NEU', kind: null, cardNumbers: [], paths: [], text: 'ein Grund, den das Board nicht kennt' }],
      }),
    )

    expect(within(await abschnitt()).getByText('ein Grund, den das Board nicht kennt')).toBeInTheDocument()
  })

  it('übersetzt die übrigen Gründe des Runners', async () => {
    const grund = (code: string, kind: string | null = null, paths: string[] = []) => ({
      code,
      kind,
      cardNumbers: [],
      paths,
      text: code,
    })
    zeige(() =>
      Promise.resolve({
        ...leer,
        cardReasons: [
          grund('NOT_A_WORK_PACKAGE', 'PLAN'),
          grund('NOT_A_WORK_PACKAGE', 'REQUIREMENT'),
          grund('NOT_A_WORK_PACKAGE', 'IDEA'),
          grund('NOT_A_WORK_PACKAGE', 'HUMAN'),
          grund('NOT_A_WORK_PACKAGE', 'NEU'),
          grund('NOT_A_WORK_PACKAGE'),
          grund('CLARIFY'),
          grund('PROTECTED_LABEL'),
          grund('PROTECTED_FILE', null, ['a.md', '.claude/settings.json']),
          grund('REVIEW_MISSING'),
        ],
      }),
    )

    const a = await abschnitt()
    expect(within(a).getByText('kein Arbeitspaket, sondern ein Plan')).toBeInTheDocument()
    expect(within(a).getByText('kein Arbeitspaket, sondern eine fachliche Anforderung')).toBeInTheDocument()
    expect(within(a).getByText('kein Arbeitspaket, sondern eine Idee')).toBeInTheDocument()
    expect(within(a).getByText('kein Arbeitspaket, sondern ein Schritt für einen Menschen')).toBeInTheDocument()
    expect(
      within(a).getAllByText('kein Arbeitspaket, sondern ein Dokument oder ein Schritt für einen Menschen'),
    ).toHaveLength(2)
    expect(within(a).getByText('wartet auf Klärung (kit:klaeren)')).toBeInTheDocument()
    expect(within(a).getByText('berührt eine geschützte Datei (kit:geschuetzt)')).toBeInTheDocument()
    expect(within(a).getByText('berührt eine geschützte Datei (a.md, .claude/settings.json)')).toBeInTheDocument()
    expect(within(a).getByText('die nötige Prüfung (Review) fehlt')).toBeInTheDocument()
  })

  it('verwirft eine Antwort, die nach dem Schließen kommt', async () => {
    let ablehnen: (grund: unknown) => void = () => {}
    let liefern: (gruende: Wartegruende) => void = () => {}
    const fehler = zeige(() => new Promise<Wartegruende>((_, nein) => (ablehnen = nein)))
    const erfolg = zeige(() => new Promise<Wartegruende>((ja) => (liefern = ja)))
    fehler.unmount()
    erfolg.unmount()

    await act(async () => {
      ablehnen(new ApiError(500, 'x', undefined, 'kaputt'))
      liefern({ ...leer, startable: true })
      await Promise.resolve()
    })
    expect(screen.queryByTestId('wartegrund')).not.toBeInTheDocument()
  })

  it('spricht Englisch', async () => {
    const since = '2026-10-13T23:05:00Z'
    const api = {
      wartegrund: vi.fn(() =>
        Promise.resolve<Wartegruende>({
          runReasons: [
            { code: 'KEINE_UMSETZUNGSNACHT', since: null, stage: null, runMode: null, cardNumber: null },
            { code: 'BELEGT', since: null, stage: 'VORBEREITUNG', runMode: 'CHAIN', cardNumber: 1665 },
            { code: 'BELEGT', since: null, stage: 'UMSETZUNG', runMode: 'IMPLEMENTATION', cardNumber: 1700 },
            { code: 'VERSTUMMT', since, stage: null, runMode: 'IMPLEMENTATION', cardNumber: null },
          ],
          cardReasons: [
            { code: 'NOT_A_WORK_PACKAGE', kind: 'PLAN', cardNumbers: [], paths: [], text: 'x' },
            { code: 'NOT_A_WORK_PACKAGE', kind: null, cardNumbers: [], paths: [], text: 'x' },
            { code: 'NO_LABEL', kind: null, cardNumbers: [], paths: [], text: 'x' },
            { code: 'DEPENDENCY', kind: null, cardNumbers: [1698], paths: [], text: 'x' },
            { code: 'CLARIFY', kind: null, cardNumbers: [], paths: [], text: 'x' },
            { code: 'PROTECTED_LABEL', kind: null, cardNumbers: [], paths: [], text: 'x' },
            { code: 'PROTECTED_FILE', kind: null, cardNumbers: [], paths: ['a.md'], text: 'x' },
            { code: 'REVIEW_MISSING', kind: null, cardNumbers: [], paths: [], text: 'x' },
          ],
          packagesAhead: 1,
          notes: [
            { code: 'NICHT_BEWERTET', assessedAt: since },
            { code: 'NICHT_BEWERTET', assessedAt: null },
            { code: 'SEIT_BEWERTUNG_GEAENDERT', assessedAt: since },
          ],
          startable: true,
        }),
      ),
    }
    renderMitSprache(<Wartegrund cardId={77} api={api} />, 'en')

    const a = await abschnitt()
    expect(within(a).getByText('Why it waits')).toBeInTheDocument()
    expect(within(a).getByText('no implementation night active')).toBeInTheDocument()
    expect(within(a).getByText('Implementation occupied by another run: chain #1665, stage Preparation')).toBeInTheDocument()
    expect(within(a).getByText('Implementation occupied by another run: Implementation night #1700')).toBeInTheDocument()
    expect(within(a).getByText(/^Runner silent since .*\(last stage: not reported\)/)).toBeInTheDocument()
    expect(within(a).getByText('not a work package but a plan')).toBeInTheDocument()
    expect(within(a).getByText('not a work package but a document or a step for a human')).toBeInTheDocument()
    expect(within(a).getByText('not released for the night (kit:nightrun missing)')).toBeInTheDocument()
    expect(within(a).getByText('waiting for another card (#1698)')).toBeInTheDocument()
    expect(within(a).getByText('waiting for clarification (kit:clarify)')).toBeInTheDocument()
    expect(within(a).getByText('touches a protected file (kit:protected)')).toBeInTheDocument()
    expect(within(a).getByText('touches a protected file (a.md)')).toBeInTheDocument()
    expect(within(a).getByText('the required review is missing')).toBeInTheDocument()
    expect(within(a).getByText('Packages ahead: 1')).toBeInTheDocument()
    expect(within(a).getByText(/^not yet assessed by the runner \(last assessment /)).toBeInTheDocument()
    expect(within(a).getByText('not yet assessed by the runner (no assessment yet)')).toBeInTheDocument()
    expect(within(a).getByText('changed since the assessment')).toBeInTheDocument()
    expect(within(a).getByText('ready to start – no obstacle found')).toBeInTheDocument()
  })

  it('meldet einen Ladefehler auf Englisch', async () => {
    const api = { wartegrund: vi.fn(() => Promise.reject(new ApiError(500, 'x', undefined, 'broken'))) }
    renderMitSprache(<Wartegrund cardId={77} api={api} />, 'en')

    expect(await screen.findByText('The waiting reason could not be loaded: broken')).toBeInTheDocument()
  })
})
