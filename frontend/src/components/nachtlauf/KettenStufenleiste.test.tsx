import { ThemeProvider } from '@mui/material/styles'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Label } from '../../api/labels'
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
]

const FACHLICH = '[Fachlich] Neue Ansicht'
const PLAN = '[Plan] Neue Ansicht'

function zeige({
  titel = FACHLICH,
  labelIds = [],
  boardLabels = VORRAT,
  disabled = false,
}: Partial<{ titel: string; labelIds: number[]; boardLabels: Label[]; disabled: boolean }> = {}) {
  const onChange = vi.fn()
  render(
    <ThemeProvider theme={theme}>
      <KettenStufenleiste
        titel={titel}
        labelIds={labelIds}
        boardLabels={boardLabels}
        disabled={disabled}
        onChange={onChange}
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
    expect(stationen.map((s) => s.dataset.testid)).toEqual([
      'station-fachplan',
      'station-plan',
      'station-pruefung',
      'station-pakete',
      'station-umsetzung',
      'station-push-vorbereitet',
    ])
    expect(station('fachplan')).toHaveTextContent('Fachplan')
    expect(station('fachplan')).toHaveTextContent('vor dem Lauf erbracht')
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
