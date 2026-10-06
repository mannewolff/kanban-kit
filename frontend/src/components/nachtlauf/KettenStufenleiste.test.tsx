import { ThemeProvider } from '@mui/material/styles'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { Label } from '../../api/labels'
import type { KettenStand, NightChainStationView } from '../../api/nightRuns'
import { theme } from '../../theme'
import { KettenStufenleiste, traegtStufenleiste } from './KettenStufenleiste'

/**
 * Die Stufenleiste an `[Fachlich]`- und `[Plan]`-Karten (Issue #1449, Plan #1447): Ziel und
 * Prüferzahl wählen, ohne Labels von Hand zu setzen. Geprüft werden die Regeln aus E6, E8, E10 und
 * E14 des Plans.
 */

const label = (id: number, name: string): Label => ({
  id,
  boardId: 1,
  name,
  color: '#888888',
  countOnEpicTile: false,
})

/** Der volle Vorrat aus E10, dazu ein fremdes Label, das jeder Wechsel stehen lassen muss. */
const VORRAT: Label[] = [
  label(1, 'ziel:plan'),
  label(2, 'ziel:pakete'),
  label(3, 'ziel:umsetzung'),
  label(4, 'ziel:push-vorbereitet'),
  label(5, 'planreview:1'),
  label(6, 'planreview:2'),
  label(7, 'kit:durchziehen'),
  label(8, 'Bug'),
  label(9, 'kit:night'),
  label(10, 'review:fertig'),
  label(11, 'kit:klaeren'),
  label(12, 'lauf:wartet'),
]

const FACHLICH = '[Fachlich] Neue Ansicht'
const PLAN = '[Plan] Neue Ansicht'

function zeige({
  titel = FACHLICH,
  labelIds = [],
  boardLabels = VORRAT,
  disabled = false,
  beschreibung = '',
  kommentare = [],
  // Ohne Angabe lädt der Kettenstand nie: Die Leiste zeigt dann den Stand aus Gruppe A.
  kettenstand = vi.fn(() => new Promise<KettenStand>(() => {})),
}: Partial<{
  titel: string
  labelIds: number[]
  boardLabels: Label[]
  disabled: boolean
  beschreibung: string
  kommentare: { body: string }[]
  kettenstand: (cardId: number) => Promise<KettenStand>
}> = {}) {
  const onChange = vi.fn()
  render(
    <ThemeProvider theme={theme}>
      <KettenStufenleiste
        titel={titel}
        labelIds={labelIds}
        boardLabels={boardLabels}
        disabled={disabled}
        beschreibung={beschreibung}
        kommentare={kommentare}
        onChange={onChange}
        cardId={812}
        api={{ kettenstand }}
      />
    </ThemeProvider>,
  )
  return onChange
}

const zielGruppe = () => within(screen.getByRole('group', { name: 'Ziel der Kette' }))
const ziel = (name: string) => zielGruppe().getByRole('button', { name: new RegExp(`^${name}`) })
const station = (name: string) => screen.getByTestId(`station-${name}`)

describe('traegtStufenleiste', () => {
  it('erkennt die beiden Präfixe und nur sie', () => {
    expect(traegtStufenleiste('[Fachlich] x')).toBe(true)
    expect(traegtStufenleiste('[Plan] x')).toBe(true)
    expect(traegtStufenleiste('[Task] x')).toBe(false)
    expect(traegtStufenleiste('Plan [Fachlich]')).toBe(false)
  })
})

describe('KettenStufenleiste', () => {
  it('zeigt sechs Stationen in fester Reihenfolge, jede mit Zustandstext', () => {
    zeige()

    const stationen = screen.getAllByTestId(/^station-/)
    // Dieselben sechs Stationen wie während des Laufs und in „Heute Nacht“ (Fachplan #1420, Issue #1472).
    expect(stationen.map((s) => s.dataset.testid)).toEqual([
      'station-plan',
      'station-pruefung',
      'station-pakete',
      'station-abdeckung',
      'station-umsetzung',
      'station-push-vorbereitet',
    ])
    expect(station('abdeckung')).toHaveTextContent('Abdeckung')
    expect(station('plan')).toHaveTextContent('vorgesehen')
    expect(station('pruefung')).toHaveTextContent('vorgesehen')
    expect(station('pakete')).toHaveTextContent('Ziel')
    expect(station('umsetzung')).toHaveTextContent('nicht vorgesehen')
    expect(station('push-vorbereitet')).toHaveTextContent('Veröffentlichung vorbereitet')
    expect(station('push-vorbereitet')).toHaveTextContent('nicht vorgesehen')
    // Jede Station trägt ein Symbol neben dem Text (E6).
    for (const s of stationen) {
      expect(within(s).getByTestId('stationssymbol')).not.toBeEmptyDOMElement()
    }
  })

  it('rechnet die Abdeckung beim Ziel „Arbeitspakete“ zum vorgesehenen Weg (Issue #1472)', () => {
    zeige()

    expect(station('pakete')).toHaveTextContent('Ziel')
    expect(station('abdeckung')).toHaveTextContent('vorgesehen')
    expect(station('abdeckung')).not.toHaveTextContent('nicht vorgesehen')
  })

  it('rechnet die Abdeckung beim Ziel „Plan“ als nicht vorgesehen (Issue #1472)', () => {
    zeige({ labelIds: [1] })

    expect(station('plan')).toHaveTextContent('Ziel')
    expect(station('abdeckung')).toHaveTextContent('nicht vorgesehen')
  })

  it('bietet genau vier Ziele als Umschaltknöpfe an, ohne Ziel ist „Arbeitspakete“ markiert', () => {
    zeige()

    const knoepfe = zielGruppe().getAllByRole('button')
    expect(knoepfe).toHaveLength(4)
    expect(ziel('Plan')).toHaveAttribute('aria-pressed', 'false')
    expect(ziel('Arbeitspakete')).toHaveAttribute('aria-pressed', 'true')
    expect(ziel('Umsetzung')).toHaveAttribute('aria-pressed', 'false')
    expect(ziel('Veröffentlichung vorbereitet')).toHaveAttribute('aria-pressed', 'false')
    for (const k of knoepfe) {
      expect(k).not.toHaveAttribute('aria-disabled', 'true')
    }
  })

  it('markiert das gesetzte Ziel und rechnet die Stationen dahinter als nicht vorgesehen', () => {
    zeige({ labelIds: [1] })

    expect(ziel('Plan')).toHaveAttribute('aria-pressed', 'true')
    expect(ziel('Arbeitspakete')).toHaveAttribute('aria-pressed', 'false')
    expect(station('plan')).toHaveTextContent('Ziel')
    expect(station('pruefung')).toHaveTextContent('nicht vorgesehen')
    expect(station('pakete')).toHaveTextContent('nicht vorgesehen')
  })

  it('setzt beim Wählen das neue Ziel und nimmt das alte ab, fremde Labels bleiben', async () => {
    const onChange = zeige({ labelIds: [8, 2] })

    await userEvent.click(ziel('Veröffentlichung vorbereitet'))

    expect(onChange).toHaveBeenCalledWith([8, 4])
  })

  it('ändert nichts, wenn das schon markierte Ziel gewählt wird', async () => {
    const onChange = zeige({ labelIds: [3] })

    await userEvent.click(ziel('Umsetzung'))

    expect(onChange).not.toHaveBeenCalled()
  })

  it('wählt auch die Vorgabe „Arbeitspakete“ nicht noch einmal aus', async () => {
    const onChange = zeige()

    await userEvent.click(ziel('Arbeitspakete'))

    expect(onChange).not.toHaveBeenCalled()
  })

  it('wertet kit:durchziehen als Ziel „Umsetzung“ und lässt nur die zwei weiter reichenden Ziele zu', async () => {
    const onChange = zeige({ labelIds: [7] })

    expect(ziel('Umsetzung')).toHaveAttribute('aria-pressed', 'true')
    expect(ziel('Arbeitspakete')).toHaveAttribute('aria-pressed', 'false')
    expect(ziel('Veröffentlichung vorbereitet')).not.toHaveAttribute('aria-disabled', 'true')
    for (const name of ['Plan', 'Arbeitspakete']) {
      expect(ziel(name)).toHaveAttribute('aria-disabled', 'true')
    }

    await userEvent.click(ziel('Plan'))
    expect(onChange).not.toHaveBeenCalled()

    await userEvent.hover(ziel('Arbeitspakete'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('kit:durchziehen')

    await userEvent.click(ziel('Veröffentlichung vorbereitet'))
    // kit:durchziehen bleibt stehen (Kit A3), das Ziel kommt dazu.
    expect(onChange).toHaveBeenCalledWith([7, 4])
  })

  it('lässt bei kit:durchziehen ein weiter reichendes Ziel gelten', () => {
    zeige({ labelIds: [7, 4] })

    expect(ziel('Veröffentlichung vorbereitet')).toHaveAttribute('aria-pressed', 'true')
    expect(ziel('Umsetzung')).toHaveAttribute('aria-pressed', 'false')
  })

  it('graut an einer [Plan]-Karte Plan und Prüfung aus und zeigt keinen Prüferschalter', async () => {
    const onChange = zeige({ titel: PLAN })

    expect(ziel('Plan')).toHaveAttribute('aria-disabled', 'true')
    expect(station('plan')).toHaveTextContent('vor dem Lauf erbracht')
    expect(station('pruefung')).toHaveTextContent('vor dem Lauf erbracht')
    expect(station('pruefung')).toHaveAttribute('aria-disabled', 'true')
    expect(screen.queryByRole('group', { name: 'Planprüfung' })).not.toBeInTheDocument()

    await userEvent.click(ziel('Plan'))
    expect(onChange).not.toHaveBeenCalled()

    await userEvent.hover(ziel('Plan'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('schon ein Plan')
  })

  it('wertet ein ziel:plan an einer [Plan]-Karte nicht als Ziel', () => {
    zeige({ titel: PLAN, labelIds: [1] })

    expect(ziel('Plan')).toHaveAttribute('aria-pressed', 'false')
    expect(ziel('Arbeitspakete')).toHaveAttribute('aria-pressed', 'true')
  })

  it('zeigt den Prüferschalter an [Fachlich] und tauscht planreview:1 gegen planreview:2', async () => {
    const onChange = zeige({ labelIds: [5, 8] })

    const pruefer = within(screen.getByRole('group', { name: 'Planprüfung' }))
    expect(pruefer.getByRole('button', { name: '1 Prüfer' })).toHaveAttribute('aria-pressed', 'true')
    expect(pruefer.getByRole('button', { name: '2 Prüfer' })).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(pruefer.getByRole('button', { name: '2 Prüfer' }))

    expect(onChange).toHaveBeenCalledWith([8, 6])
  })

  it('lässt den Prüferschalter ohne Wahl auf der Vorgabe des Projekts stehen', async () => {
    const onChange = zeige()

    const pruefer = within(screen.getByRole('group', { name: 'Planprüfung' }))
    expect(pruefer.getByRole('button', { name: '1 Prüfer' })).toHaveAttribute('aria-pressed', 'false')
    expect(pruefer.getByRole('button', { name: '2 Prüfer' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByText(/Vorgabe des Projekts/)).toBeInTheDocument()

    await userEvent.click(pruefer.getByRole('button', { name: '1 Prüfer' }))
    expect(onChange).toHaveBeenCalledWith([5])
  })

  it('ändert nichts, wenn die schon gewählte Prüferzahl noch einmal gewählt wird', async () => {
    const onChange = zeige({ labelIds: [6] })

    await userEvent.click(screen.getByRole('button', { name: '2 Prüfer' }))

    expect(onChange).not.toHaveBeenCalled()
  })

  it('graut eine Station aus, deren Label am Board fehlt, und nennt den Namen', async () => {
    const onChange = zeige({ boardLabels: VORRAT.filter((l) => l.name !== 'ziel:umsetzung') })

    expect(ziel('Umsetzung')).toHaveAttribute('aria-disabled', 'true')
    expect(station('umsetzung')).toHaveTextContent('nicht wählbar')

    await userEvent.click(ziel('Umsetzung'))
    expect(onChange).not.toHaveBeenCalled()

    await userEvent.hover(ziel('Umsetzung'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('ziel:umsetzung')
  })

  it('graut einen Prüferknopf aus, dessen Label am Board fehlt', async () => {
    const onChange = zeige({ boardLabels: VORRAT.filter((l) => l.name !== 'planreview:2') })

    const knopf = screen.getByRole('button', { name: '2 Prüfer' })
    expect(knopf).toHaveAttribute('aria-disabled', 'true')
    await userEvent.click(knopf)
    expect(onChange).not.toHaveBeenCalled()

    await userEvent.hover(knopf)
    expect(await screen.findByRole('tooltip')).toHaveTextContent('planreview:2')
  })

  it('wechselt den Fokus per Pfeiltaste, überspringt Gesperrtes und wählt per Leertaste', async () => {
    const onChange = zeige({ labelIds: [7] })

    act(() => ziel('Umsetzung').focus())
    await userEvent.keyboard('{ArrowRight}')
    expect(ziel('Veröffentlichung vorbereitet')).toHaveFocus()
    // Am Ende springt der Fokus zurück an den Anfang — Plan und Arbeitspakete sind gesperrt.
    await userEvent.keyboard('{ArrowDown}')
    expect(ziel('Umsetzung')).toHaveFocus()
    await userEvent.keyboard('{ArrowLeft}')
    expect(ziel('Veröffentlichung vorbereitet')).toHaveFocus()
    await userEvent.keyboard('{ArrowUp}')
    expect(ziel('Umsetzung')).toHaveFocus()

    await userEvent.keyboard('{ArrowRight}')
    await userEvent.keyboard(' ')
    expect(onChange).toHaveBeenCalledWith([7, 4])
  })

  it('lässt andere Tasten unbeachtet', async () => {
    zeige()

    act(() => ziel('Plan').focus())
    await userEvent.keyboard('a')
    expect(ziel('Plan')).toHaveFocus()
  })

  it('bedient auch den Prüferschalter per Pfeiltaste', async () => {
    zeige()

    act(() => screen.getByRole('button', { name: '1 Prüfer' }).focus())
    await userEvent.keyboard('{ArrowRight}')
    expect(screen.getByRole('button', { name: '2 Prüfer' })).toHaveFocus()
  })

  it('ist ohne Recht reine Anzeige ohne bedienbare Knöpfe', () => {
    zeige({ disabled: true, labelIds: [4, 6] })

    expect(screen.queryAllByRole('button')).toHaveLength(0)
    expect(station('push-vorbereitet')).toHaveTextContent('Ziel')
    expect(station('pakete')).toHaveTextContent('vorgesehen')
    expect(screen.getByText(/2 Prüfer/)).toBeInTheDocument()
  })

  it('zeigt ohne Recht und ohne Prüferwahl die Vorgabe des Projekts', () => {
    zeige({ disabled: true })

    expect(screen.queryAllByRole('button')).toHaveLength(0)
    expect(screen.getByText(/Vorgabe des Projekts/)).toBeInTheDocument()
  })

  it('zeigt ohne Recht an einer [Plan]-Karte keine Prüferangabe', () => {
    zeige({ disabled: true, titel: PLAN })

    expect(screen.queryByText(/Prüfer/)).not.toBeInTheDocument()
  })
})

/**
 * Start und Rücknahme der Kette (Issue #1450, Plan #1447 E3, E7, E10, E15): „Kette starten“ setzt
 * `kit:night`, „Start zurücknehmen“ nimmt nur dieses Label ab; ab „Umsetzung“ verlangt der Start
 * eine Bestätigung, Sperrhinweise nennen, was vor dem Start fehlt.
 */
describe('KettenStufenleiste — Start', () => {
  const GO_SATZ = 'Mit diesem Ziel gibst du das GO für alle Arbeitspakete dieser Karte.'
  const startKnopf = () => screen.getByRole('button', { name: 'Kette starten' })
  /** Eine fachliche Anforderung mit abgeschlossener Prüfung: startbereit. */
  const BEREIT = [10]
  const laufstand = (rumpf: string) => ({ body: `## Laufstand\n\n${rumpf}` })
  const MIT_LAUF_ID = laufstand(
    'Umsetzung laeuft seit 2026-10-05T18:20:40.036Z\n\nProtokoll: .claude/protokolle/2026-10-05-175710/1450-umsetzung.log',
  )

  it('setzt kit:night mit einem Klick, solange das Ziel vor „Umsetzung“ liegt', async () => {
    const onChange = zeige({ labelIds: [...BEREIT, 2] })

    expect(screen.queryByText(GO_SATZ)).not.toBeInTheDocument()
    await userEvent.click(startKnopf())

    expect(onChange).toHaveBeenCalledWith([10, 2, 9])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('zeigt ab „Umsetzung“ den GO-Satz schon vor dem Start', () => {
    zeige({ labelIds: [...BEREIT, 3] })

    expect(screen.getByText(GO_SATZ)).toBeInTheDocument()
  })

  it('zeigt den GO-Satz auch beim Ziel „Veröffentlichung vorbereitet“ und bei kit:durchziehen', () => {
    zeige({ labelIds: [...BEREIT, 7] })

    expect(screen.getByText(GO_SATZ)).toBeInTheDocument()
  })

  it('fragt ab „Umsetzung“ nach, der Fokus liegt zuerst auf „Abbrechen“, und Abbrechen setzt kein Label', async () => {
    const onChange = zeige({ labelIds: [...BEREIT, 4] })

    await userEvent.click(startKnopf())

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText('Damit gibst du das GO für alle Arbeitspakete dieser Karte. Kette starten?')).toBeInTheDocument()
    expect(dialog.getByRole('button', { name: 'Abbrechen' })).toHaveFocus()

    await userEvent.click(dialog.getByRole('button', { name: 'Abbrechen' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(onChange).not.toHaveBeenCalled()
  })

  it('setzt kit:night erst nach der Bestätigung im Dialog', async () => {
    const onChange = zeige({ labelIds: [...BEREIT, 3] })

    await userEvent.click(startKnopf())
    const dialog = within(await screen.findByRole('dialog'))
    await userEvent.click(dialog.getByRole('button', { name: 'Kette starten' }))

    expect(onChange).toHaveBeenCalledWith([10, 3, 9])
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('bricht den Dialog auch per Escape ab, ohne ein Label zu setzen', async () => {
    const onChange = zeige({ labelIds: [...BEREIT, 3] })

    await userEvent.click(startKnopf())
    await screen.findByRole('dialog')
    await userEvent.keyboard('{Escape}')

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(onChange).not.toHaveBeenCalled()
  })

  it('nimmt beim Zurücknehmen nur kit:night ab, Ziel und Prüferzahl bleiben', async () => {
    const onChange = zeige({ labelIds: [...BEREIT, 3, 6, 9, 8] })

    expect(screen.queryByRole('button', { name: 'Kette starten' })).not.toBeInTheDocument()
    expect(screen.getByText(/wartet auf die Übernahme durch einen Runner/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Start zurücknehmen' }))

    expect(onChange).toHaveBeenCalledWith([10, 3, 6, 8])
  })

  it('lässt das Ziel nach dem Start erst nach der Rücknahme wieder ändern', async () => {
    const onChange = zeige({ labelIds: [...BEREIT, 2, 9] })

    expect(screen.queryByRole('group', { name: 'Ziel der Kette' })).not.toBeInTheDocument()
    expect(station('pakete')).toHaveTextContent('Ziel')
    expect(screen.queryByRole('group', { name: 'Planprüfung' })).not.toBeInTheDocument()
    expect(onChange).not.toHaveBeenCalled()
  })

  it('sperrt den Start an [Fachlich] ohne review:fertig und nennt den Grund', async () => {
    const onChange = zeige()

    expect(startKnopf()).toHaveAttribute('aria-disabled', 'true')
    expect(startKnopf()).toHaveAccessibleDescription(/fachliche Prüfung/)
    await userEvent.click(startKnopf())
    expect(onChange).not.toHaveBeenCalled()
  })

  it('sperrt den Start an [Plan] ohne Zeile „Plan-Review:“ im Body', async () => {
    const onChange = zeige({ titel: PLAN, beschreibung: 'Ein Plan.\nPlan-Review steht noch aus.' })

    expect(startKnopf()).toHaveAttribute('aria-disabled', 'true')
    expect(startKnopf()).toHaveAccessibleDescription(/Planprüfung/)
    await userEvent.click(startKnopf())
    expect(onChange).not.toHaveBeenCalled()
  })

  it('lässt einen [Plan] mit Zeile „Plan-Review:“ ohne review:fertig starten', async () => {
    const onChange = zeige({ titel: PLAN, beschreibung: 'Ein Plan.\nPlan-Review: fable (2026-10-05)' })

    expect(startKnopf()).not.toHaveAttribute('aria-disabled')
    await userEvent.click(startKnopf())
    expect(onChange).toHaveBeenCalledWith([9])
  })

  it.each([
    ['kit:klaeren', 11],
    ['lauf:wartet', 12],
  ])('sperrt den Start, solange %s an der Karte hängt', async (name, id) => {
    const onChange = zeige({ labelIds: [...BEREIT, id] })

    expect(startKnopf()).toHaveAttribute('aria-disabled', 'true')
    expect(startKnopf()).toHaveAccessibleDescription(/offene Frage/)
    expect(screen.getByText(new RegExp(name))).toBeInTheDocument()
    await userEvent.click(startKnopf())
    expect(onChange).not.toHaveBeenCalled()
  })

  it('nennt mehrere Sperrgründe zugleich', () => {
    zeige({ labelIds: [11] })

    expect(startKnopf()).toHaveAccessibleDescription(/fachliche Prüfung.*offene Frage/)
  })

  it('sperrt den Start, wenn das Board kein kit:night führt', async () => {
    const onChange = zeige({ labelIds: BEREIT, boardLabels: VORRAT.filter((l) => l.name !== 'kit:night') })

    expect(startKnopf()).toHaveAttribute('aria-disabled', 'true')
    expect(startKnopf()).toHaveAccessibleDescription(/kit:night/)
    await userEvent.click(startKnopf())
    expect(onChange).not.toHaveBeenCalled()
  })

  it('ist nach der Übernahme durch einen Runner nur Anzeige', () => {
    zeige({ labelIds: [...BEREIT, 3, 6], kommentare: [{ body: 'Erst ein Wort.' }, MIT_LAUF_ID] })

    expect(screen.queryAllByRole('button')).toHaveLength(0)
    expect(screen.getByText(/Ein Runner hat die Kette übernommen/)).toBeInTheDocument()
    expect(station('umsetzung')).toHaveTextContent('Ziel')
    expect(screen.getByText(/2 Prüfer/)).toBeInTheDocument()
    expect(screen.queryByText(GO_SATZ)).not.toBeInTheDocument()
  })

  it('wertet einen Laufstand ohne Lauf-ID nicht als Übernahme', () => {
    zeige({ labelIds: BEREIT, kommentare: [laufstand('Umsetzung laeuft seit 2026-10-05T18:20:40.036Z')] })

    expect(startKnopf()).not.toHaveAttribute('aria-disabled')
  })

  it('wertet einen Kommentar, der den Anker nur im Text trägt, nicht als Laufstand', () => {
    zeige({ labelIds: BEREIT, kommentare: [{ body: 'Siehe ## Laufstand vom Lauf 2026-10-05-175710' }] })

    expect(startKnopf()).not.toHaveAttribute('aria-disabled')
  })

  it('bietet bei gesetztem kit:night trotz altem Laufstand die Rücknahme an', () => {
    zeige({ labelIds: [...BEREIT, 9], kommentare: [MIT_LAUF_ID] })

    expect(screen.getByRole('button', { name: 'Start zurücknehmen' })).toBeInTheDocument()
  })

  it('zeigt ohne Recht nur den Stand, ohne Start- und Rücknahmeknopf', () => {
    zeige({ disabled: true, labelIds: [...BEREIT, 9] })

    expect(screen.queryAllByRole('button')).toHaveLength(0)
    expect(screen.getByText(/wartet auf die Übernahme durch einen Runner/)).toBeInTheDocument()
  })
})

describe('KettenStufenleiste — Kettenstand (Issue #1453)', () => {
  const st = (
    station: NightChainStationView['station'],
    zustand: NightChainStationView['zustand'],
    text: string,
    grund: string | null = null,
  ): NightChainStationView => ({ station, zustand, text, grund })

  const VOR_DEM_START: KettenStand = {
    ziel: null,
    pruefer: null,
    zielErreicht: false,
    grenze: null,
    stationen: [],
    uebernommen: false,
    planReviewVorhanden: false,
    lauf: null,
  }

  /** Ein übernommener Stand: Plan erledigt, die übrigen Stationen nach Wahl. */
  const uebernommen = (stationen: NightChainStationView[], teil: Partial<KettenStand> = {}): KettenStand => ({
    ...VOR_DEM_START,
    ziel: 'UMSETZUNG',
    uebernommen: true,
    lauf: '2026-10-05T01:00:00Z',
    stationen: [st('PLAN', 'ERLEDIGT', 'erledigt'), ...stationen],
    ...teil,
  })

  const geladen = (stand: KettenStand) => vi.fn(() => Promise.resolve(stand))
  const kStation = (s: string) => screen.findByTestId(`kette-station-${s}`)

  it('lädt den Kettenstand der Karte einmal beim Öffnen', async () => {
    const kettenstand = geladen(VOR_DEM_START)
    zeige({ kettenstand })
    await waitFor(() => expect(kettenstand).toHaveBeenCalledWith(812))
    expect(kettenstand).toHaveBeenCalledTimes(1)
  })

  it('zeigt eine laufende Prüfung mit Prüferzahl, Symbol und als aktuelle Station', async () => {
    zeige({
      kettenstand: geladen(
        uebernommen([st('REVIEW', 'LAEUFT', 'läuft (2 Prüfer)'), st('PAKETE', 'STEHT_AUS', 'steht aus')], {
          pruefer: 2,
        }),
      ),
    })

    const review = await kStation('REVIEW')
    expect(review).toHaveTextContent('Prüfung')
    expect(review).toHaveTextContent('läuft (2 Prüfer)')
    expect(review).toHaveAttribute('aria-current', 'step')
    expect(within(review).getByTestId('stationssymbol-laeuft')).toBeInTheDocument()
    expect(await kStation('PAKETE')).toHaveTextContent('steht aus')
    expect(await kStation('PAKETE')).not.toHaveAttribute('aria-current')
    expect(screen.getByText('2 Prüfer')).toBeInTheDocument()
  })

  it('zeigt eine wartende Station mit ihrem Grund wörtlich', async () => {
    zeige({ kettenstand: geladen(uebernommen([st('REVIEW', 'WARTET', 'wartet', 'wartet: Frage an den Menschen')])) })

    const review = await kStation('REVIEW')
    expect(review).toHaveTextContent('wartet')
    expect(within(review).getByText('wartet: Frage an den Menschen')).toBeInTheDocument()
    expect(within(review).getByTestId('stationssymbol-wartet')).toBeInTheDocument()
  })

  it('zeigt eine abgebrochene Kette mit Grund', async () => {
    zeige({
      kettenstand: geladen(
        uebernommen([st('REVIEW', 'ABGEBROCHEN', 'abgebrochen', 'abgebrochen: Zeitgrenze erreicht')]),
      ),
    })

    const review = await kStation('REVIEW')
    expect(review).toHaveTextContent('abgebrochen')
    expect(within(review).getByText('abgebrochen: Zeitgrenze erreicht')).toBeInTheDocument()
    expect(within(review).getByTestId('stationssymbol-abgebrochen')).toBeInTheDocument()
  })

  it('zeigt an der erreichten Zielstation „Ziel erreicht“ und markiert sie als Ziel', async () => {
    zeige({
      kettenstand: geladen(
        uebernommen(
          [st('PAKETE', 'ERLEDIGT', 'erledigt'), st('UMSETZUNG', 'ERLEDIGT', 'Ziel erreicht'), st('VORBEREITUNG', 'NICHT_VORGESEHEN', 'nicht vorgesehen')],
          { zielErreicht: true },
        ),
      ),
    })

    const umsetzung = await kStation('UMSETZUNG')
    expect(umsetzung).toHaveTextContent('Ziel erreicht')
    expect(within(umsetzung).getByText('Ziel')).toBeInTheDocument()
    expect(within(umsetzung).getByTestId('stationssymbol-ziel-erreicht')).toBeInTheDocument()
    expect(await kStation('VORBEREITUNG')).toHaveTextContent('nicht vorgesehen')
  })

  it('zeigt die Projektgrenze mit dem gemeldeten Grund', async () => {
    const grund = 'wartet: Übergang abdeckung→umsetzung im Projekt nicht freigegeben — weiter mit kit:night'
    zeige({
      kettenstand: geladen(
        uebernommen([st('ABDECKUNG', 'ERLEDIGT', 'erledigt'), st('UMSETZUNG', 'WARTET', 'Projektgrenze', grund)], {
          grenze: { stufe: 'ABDECKUNG', grund },
        }),
      ),
    })

    const umsetzung = await kStation('UMSETZUNG')
    expect(umsetzung).toHaveTextContent(`Projektgrenze: ${grund}`)
    expect(within(umsetzung).getByTestId('stationssymbol-projektgrenze')).toBeInTheDocument()
  })

  it('zeigt an einem Plan Plan und Prüfung als vor dem Lauf erbracht, ohne Prüferangabe', async () => {
    zeige({
      titel: PLAN,
      kettenstand: geladen({
        ...uebernommen([]),
        stationen: [
          st('PLAN', 'VOR_DEM_LAUF_ERBRACHT', 'vor dem Lauf erbracht'),
          st('REVIEW', 'VOR_DEM_LAUF_ERBRACHT', 'vor dem Lauf erbracht'),
        ],
      }),
    })

    expect(await kStation('REVIEW')).toHaveTextContent('vor dem Lauf erbracht')
    expect(within(await kStation('PLAN')).getByTestId('stationssymbol-erbracht')).toBeInTheDocument()
    expect(screen.queryByText('Planprüfung:')).not.toBeInTheDocument()
  })

  it('ist bei übernommener Karte laut Endpunkt reine Anzeige — auch ohne Laufstand in den Kommentaren', async () => {
    zeige({ labelIds: [10, 3], kettenstand: geladen(uebernommen([st('REVIEW', 'LAEUFT', 'läuft')])) })

    expect(await kStation('REVIEW')).toBeInTheDocument()
    expect(screen.queryAllByRole('button')).toHaveLength(0)
    expect(screen.getByText(/Ein Runner hat die Kette übernommen/)).toBeInTheDocument()
    expect(screen.getByText('Vorgabe des Projekts')).toBeInTheDocument()
  })

  it('folgt nach dem Laden dem Endpunkt, nicht den Kommentaren: nicht übernommen heißt startbar', async () => {
    zeige({
      labelIds: [10, 2],
      kommentare: [{ body: '## Laufstand\n\nProtokoll: .claude/protokolle/2026-10-05-175710/x.log' }],
      kettenstand: geladen(VOR_DEM_START),
    })

    expect(await screen.findByRole('button', { name: 'Kette starten' })).not.toHaveAttribute('aria-disabled')
  })

  it('sperrt den Prüferschalter an [Fachlich], wenn der Plan schon Plan-Review trägt', async () => {
    const onChange = zeige({ labelIds: [10], kettenstand: geladen({ ...VOR_DEM_START, planReviewVorhanden: true }) })

    const hinweis = await screen.findByText(/Der Plan dieser Anforderung trägt schon „Plan-Review:“/)
    expect(hinweis).toBeInTheDocument()
    const knopf = within(screen.getByRole('group', { name: 'Planprüfung' })).getByRole('button', {
      name: /^2 Prüfer/,
    })
    expect(knopf).toHaveAttribute('aria-disabled', 'true')
    await userEvent.click(knopf)
    expect(onChange).not.toHaveBeenCalled()
  })

  it('lässt den Prüferschalter ohne Plan-Review frei', async () => {
    zeige({ labelIds: [10], kettenstand: geladen(VOR_DEM_START) })

    await act(() => Promise.resolve())
    const knopf = within(screen.getByRole('group', { name: 'Planprüfung' })).getByRole('button', {
      name: /^2 Prüfer/,
    })
    expect(knopf).not.toHaveAttribute('aria-disabled')
    expect(screen.queryByText(/trägt schon „Plan-Review:“/)).not.toBeInTheDocument()
  })

  it('fällt bei einem Ladefehler auf die Anzeige vor dem Lauf zurück und nennt den Fehler im Text', async () => {
    zeige({
      labelIds: [10, 2],
      kettenstand: vi.fn(() => Promise.reject(new ApiError(500, 'Fehler', undefined, 'Datenbank nicht erreichbar'))),
    })

    expect(await screen.findByText(/Der Stand der Kette ließ sich nicht laden: Datenbank nicht erreichbar/)).toBeInTheDocument()
    expect(station('abdeckung')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Kette starten' })).toBeInTheDocument()
  })

  it('nennt bei einem Ladefehler ohne Meldung des Servers einen allgemeinen Grund', async () => {
    zeige({ kettenstand: vi.fn(() => Promise.reject(new Error('offline'))) })

    expect(await screen.findByText(/ließ sich nicht laden: unbekannter Fehler/)).toBeInTheDocument()
  })

  it('übernimmt eine Antwort nach dem Schließen nicht mehr', async () => {
    let loese: (s: KettenStand) => void = () => {}
    const { unmount } = render(
      <ThemeProvider theme={theme}>
        <KettenStufenleiste
          titel={FACHLICH}
          labelIds={[]}
          boardLabels={VORRAT}
          disabled={false}
          beschreibung=""
          kommentare={[]}
          onChange={vi.fn()}
          cardId={812}
          api={{ kettenstand: () => new Promise<KettenStand>((r) => (loese = r)) }}
        />
      </ThemeProvider>,
    )
    unmount()
    await act(async () => loese(uebernommen([])))
    expect(screen.queryByTestId('ketten-stufenleiste')).not.toBeInTheDocument()
  })
})
