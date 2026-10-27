import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { KitNamenZustand } from '../api/kitNamen'
import type { Epic } from '../api/epics'
import type { Label } from '../api/labels'
import { de } from '../i18n/de'
import { TabletQuerProvider } from '../lib/tabletQuer'
import { KIT_NAMEN } from '../test/kitNamen'
import { setzeMedien } from '../test/setup'
import { BulkActionBar, type LabelOption } from './BulkActionBar'
import { renderMitSprache } from '../i18n/renderMitSprache'

// Vorschau-Zustand der „Ansicht als XY“ (Plan #1914, E15); außer im eigenen Block ohne Vorschau.
const auth = vi.hoisted(() => ({ vorschau: null as null | Record<string, string> }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ vorschau: auth.vorschau }) }))
const vorschau = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }
beforeEach(() => {
  auth.vorschau = null
})

vi.mock('../api/kitNamen', async (original) => ({
  ...(await original<typeof import('../api/kitNamen')>()),
  useKitNamen: (): KitNamenZustand => ({ status: 'fertig', daten: KIT_NAMEN }),
}))

const label = (id: number, name: string): Label => ({
  id,
  boardId: 1,
  name,
  color: '#ff0000',
  countOnEpicTile: false,
})

const epic = (id: number, title: string): Epic => ({
  id,
  number: id,
  title,
  description: null,
  shortcode: null,
  done: 0,
  total: 0,
  memberNumbers: [],
  rootNumbers: [],
  requirementCardNumber: null,
})

/** Rendert die Leiste und gibt die Rückrufe zurück, damit Tests sie prüfen können. */
function zeigeLeiste(over: Partial<Parameters<typeof BulkActionBar>[0]> = {}) {
  const eigenschaften = {
    count: 3,
    canMove: true,
    labelOptions: [] as LabelOption[],
    labelsDisabledReason: null,
    onToggleLabel: vi.fn(),
    epicOptions: [] as { epic: Epic; hidden: boolean }[],
    epicDisabledReason: null,
    onChooseEpic: vi.fn(),
    onArchive: vi.fn(),
    onMove: vi.fn(),
    onDelete: vi.fn(),
    onCancel: vi.fn(),
    ...over,
  }
  render(
    <TabletQuerProvider>
      <BulkActionBar {...eigenschaften} />
    </TabletQuerProvider>,
  )
  return eigenschaften
}

describe('BulkActionBar', () => {
  it('zeigt die Anzahl und ruft die passenden Callbacks', () => {
    const rueckrufe = zeigeLeiste()

    expect(screen.getByText('3 ausgewählt')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    expect(rueckrufe.onArchive).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))
    expect(rueckrufe.onMove).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
    expect(rueckrufe.onDelete).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(rueckrufe.onCancel).toHaveBeenCalledTimes(1)
  })

  it('blendet Verschieben aus, wenn der Nutzer nicht verschieben darf', () => {
    zeigeLeiste({ count: 1, canMove: false })

    expect(screen.queryByRole('button', { name: 'Verschieben' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Archivieren' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'In den Papierkorb' })).toBeInTheDocument()
  })

  it('nennt im Label-Menü je Label den Zustand alle/einige/keine im zugänglichen Namen', () => {
    zeigeLeiste({
      labelOptions: [
        { label: label(1, 'Bug'), zustand: 'alle' },
        { label: label(2, 'Nacht'), zustand: 'einige' },
        { label: label(3, 'Ux'), zustand: 'keine' },
      ],
    })

    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))

    expect(screen.getByRole('menuitem', { name: 'Bug — alle gewählten Karten' })).toBeInTheDocument()
    expect(
      screen.getByRole('menuitem', { name: 'Nacht — einige gewählte Karten' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Ux — keine gewählte Karte' })).toBeInTheDocument()
  })

  it('fügt ohne vollen Haken hinzu, nimmt beim vollen Haken ab und lässt das Menü offen', () => {
    const rueckrufe = zeigeLeiste({
      labelOptions: [
        { label: label(1, 'Bug'), zustand: 'alle' },
        { label: label(2, 'Nacht'), zustand: 'einige' },
        { label: label(3, 'Ux'), zustand: 'keine' },
      ],
    })

    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Nacht — einige gewählte Karten' }))
    expect(rueckrufe.onToggleLabel).toHaveBeenLastCalledWith(2, 'ADD')

    // Das Menü bleibt offen — der zweite Klick geht ohne erneutes Öffnen durch.
    fireEvent.click(screen.getByRole('menuitem', { name: 'Ux — keine gewählte Karte' }))
    expect(rueckrufe.onToggleLabel).toHaveBeenLastCalledWith(3, 'ADD')

    fireEvent.click(screen.getByRole('menuitem', { name: 'Bug — alle gewählten Karten' }))
    expect(rueckrufe.onToggleLabel).toHaveBeenLastCalledWith(1, 'REMOVE')
    expect(rueckrufe.onToggleLabel).toHaveBeenCalledTimes(3)
  })

  it('schließt das Label-Menü auf Escape', () => {
    zeigeLeiste({ labelOptions: [{ label: label(1, 'Bug'), zustand: 'keine' }] })

    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))
    expect(screen.getByRole('menuitem', { name: 'Bug — keine gewählte Karte' })).toBeInTheDocument()

    fireEvent.keyDown(screen.getByRole('menu'), { key: 'Escape' })
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  describe('Erklärung der Systemlabels im Menü (#1673)', () => {
    const KLAEREN = de.LABEL_ERKLAERUNG.SACHEN.KLAEREN
    const optionen: LabelOption[] = [
      { label: label(1, 'Bug'), zustand: 'keine' },
      { label: label(8, 'kit:clarify'), zustand: 'keine' },
    ]

    it('zeigt die Erklärung, wenn die Pfeiltaste den Menüpunkt kit:clarify fokussiert', async () => {
      zeigeLeiste({ labelOptions: optionen })
      await userEvent.click(screen.getByRole('button', { name: 'Labels' }))
      const klaeren = screen.getByRole('menuitem', { name: 'kit:clarify — keine gewählte Karte' })

      await userEvent.keyboard('{ArrowDown}')

      expect(klaeren).toHaveFocus()
      const tooltip = await screen.findByRole('tooltip')
      expect(tooltip).toHaveTextContent(KLAEREN.bedeutung)
      expect(klaeren).toHaveAttribute('aria-describedby', tooltip.id)
    })

    it('zeigt an einem selbst angelegten Label keine Erklärung', async () => {
      zeigeLeiste({ labelOptions: optionen })
      // Per Tastatur geöffnet: Der erste Punkt bekommt den Fokus wie nach einer Pfeiltaste.
      screen.getByRole('button', { name: 'Labels' }).focus()
      await userEvent.keyboard('{Enter}')
      const bug = screen.getByRole('menuitem', { name: 'Bug — keine gewählte Karte' })
      expect(bug).toHaveFocus()

      await userEvent.hover(bug)

      expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
      expect(bug).not.toHaveAttribute('aria-describedby')
    })

    it('lässt die Menüpunkte bei der Tastenführung des Menüs: nur einer ist Tab-Halt', async () => {
      zeigeLeiste({ labelOptions: [optionen[1], optionen[0]] })
      await userEvent.click(screen.getByRole('button', { name: 'Labels' }))

      // Der erste Punkt bekommt beim Öffnen den Fokus, auch wenn er ein Systemlabel ist.
      expect(screen.getByRole('menuitem', { name: 'kit:clarify — keine gewählte Karte' })).toHaveFocus()
      expect(screen.getByRole('menuitem', { name: 'Bug — keine gewählte Karte' })).toHaveAttribute('tabindex', '-1')
    })

    it('wählt einen Systemlabel-Punkt per Klick und per Enter wie bisher', async () => {
      const rueckrufe = zeigeLeiste({ labelOptions: optionen })
      await userEvent.click(screen.getByRole('button', { name: 'Labels' }))

      await userEvent.click(screen.getByRole('menuitem', { name: 'kit:clarify — keine gewählte Karte' }))
      expect(rueckrufe.onToggleLabel).toHaveBeenLastCalledWith(8, 'ADD')

      await userEvent.keyboard('{Enter}')
      expect(rueckrufe.onToggleLabel).toHaveBeenCalledTimes(2)
      expect(screen.getByRole('menu')).toBeInTheDocument()
    })

    it('öffnet die Erklärung rechts vom Menüpunkt', async () => {
      zeigeLeiste({ labelOptions: optionen })
      await userEvent.click(screen.getByRole('button', { name: 'Labels' }))

      await userEvent.hover(screen.getByRole('menuitem', { name: 'kit:clarify — keine gewählte Karte' }))

      expect(await screen.findByRole('tooltip')).toHaveAttribute('data-popper-placement', expect.stringMatching(/^right/))
    })
  })

  it('sperrt Labels mit dem Grund im Namen und im Tooltip', async () => {
    zeigeLeiste({ labelsDisabledReason: 'Vorhaben tragen keine Labels' })

    expect(screen.getByRole('button', { name: 'Labels — Vorhaben tragen keine Labels' })).toBeDisabled()

    // Der gesperrte Button meldet keine Mausereignisse; der Tooltip hängt am span-Wrapper, den MUI
    // mit dem Titel als zugänglichem Namen versieht.
    fireEvent.mouseOver(screen.getByLabelText('Vorhaben tragen keine Labels'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Vorhaben tragen keine Labels')
  })
})

describe('BulkActionBar: Vorhaben zuordnen (Plan #1865)', () => {
  const SPERRGRUND = 'das Board hat kein wählbares Vorhaben'
  const optionen = [
    { epic: epic(5, 'Nice to have'), hidden: true },
    { epic: epic(3, 'Suche'), hidden: false },
  ]

  it('steht direkt nach Labels und vor Verschieben', () => {
    zeigeLeiste()

    const namen = screen.getAllByRole('button').map((taste) => taste.textContent)
    const labels = namen.indexOf('Labels')
    expect(namen[labels + 1]).toBe('Vorhaben zuordnen')
    expect(namen[labels + 2]).toBe('Verschieben')
  })

  it('öffnet ein Menü mit allen Vorhaben in der übergebenen Reihenfolge, ausgeblendete gekennzeichnet', () => {
    zeigeLeiste({ epicOptions: optionen })

    fireEvent.click(screen.getByRole('button', { name: 'Vorhaben zuordnen' }))

    const punkte = screen.getAllByRole('menuitem')
    expect(punkte.map((punkt) => punkt.textContent)).toEqual(['Nice to have (ausgeblendet)', 'Suche'])
    expect(punkte[0]).toHaveAccessibleName('Nice to have (ausgeblendet)')
    expect(punkte[1]).toHaveAccessibleName('Suche')
  })

  it('schließt das Menü bei der Wahl und meldet das Vorhaben', async () => {
    const rueckrufe = zeigeLeiste({ epicOptions: optionen })

    fireEvent.click(screen.getByRole('button', { name: 'Vorhaben zuordnen' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Suche' }))

    expect(rueckrufe.onChooseEpic).toHaveBeenCalledTimes(1)
    expect(rueckrufe.onChooseEpic).toHaveBeenCalledWith(3)
    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument())
  })

  it('schließt das Vorhaben-Menü auf Escape, ohne ein Vorhaben zu melden', async () => {
    const rueckrufe = zeigeLeiste({ epicOptions: optionen })

    fireEvent.click(screen.getByRole('button', { name: 'Vorhaben zuordnen' }))
    fireEvent.keyDown(screen.getByRole('menu'), { key: 'Escape' })

    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument())
    expect(rueckrufe.onChooseEpic).not.toHaveBeenCalled()
  })

  it('sperrt die Taste mit dem Grund im Namen und im Tooltip, ohne Menü', async () => {
    zeigeLeiste({ epicOptions: optionen, epicDisabledReason: SPERRGRUND })

    const taste = screen.getByRole('button', { name: `Vorhaben zuordnen — ${SPERRGRUND}` })
    expect(taste).toBeDisabled()
    fireEvent.click(taste)
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()

    fireEvent.mouseOver(screen.getByLabelText(SPERRGRUND))
    expect(await screen.findByRole('tooltip')).toHaveTextContent(SPERRGRUND)
  })

  it('öffnet die Erklärung der gesperrten Taste auf dem Tablet per Antippen', () => {
    setzeMedien({ tabletQuer: true })
    zeigeLeiste({ epicDisabledReason: SPERRGRUND })

    fireEvent.click(screen.getByLabelText(SPERRGRUND))

    expect(screen.getByRole('tooltip')).toHaveTextContent(SPERRGRUND)
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
})

describe('BulkActionBar auf dem Tablet im Querformat (Plan #1707, E7)', () => {
  const KLAEREN = de.LABEL_ERKLAERUNG.SACHEN.KLAEREN
  const optionen: LabelOption[] = [
    { label: label(1, 'Bug'), zustand: 'keine' },
    { label: label(8, 'kit:clarify'), zustand: 'keine' },
  ]

  it('öffnet am gesperrten Labels-Knopf die Erklärung per Antippen', async () => {
    setzeMedien({ tabletQuer: true })
    zeigeLeiste({ labelsDisabledReason: 'Vorhaben tragen keine Labels' })

    fireEvent.click(screen.getByLabelText('Vorhaben tragen keine Labels'))

    // Sofort, ohne die Verzögerung des Darüberfahrens: Das Antippen öffnet die Erklärung selbst.
    expect(screen.getByRole('tooltip')).toHaveTextContent('Vorhaben tragen keine Labels')
  })

  it('setzt an Label-Einträge mit Erklärung ein Info-Symbol; Antippen des Eintrags setzt das Label', async () => {
    setzeMedien({ tabletQuer: true })
    const rueckrufe = zeigeLeiste({ labelOptions: optionen })
    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))

    fireEvent.click(screen.getByRole('menuitem', { name: 'kit:clarify — keine gewählte Karte' }))
    expect(rueckrufe.onToggleLabel).toHaveBeenCalledWith(8, 'ADD')
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()

    expect(screen.queryByRole('button', { name: de.ERKLAERUNG_INFO.ZEIGEN('Bug') })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: de.ERKLAERUNG_INFO.ZEIGEN('kit:clarify') }))

    expect(await screen.findByRole('tooltip')).toHaveTextContent(KLAEREN.bedeutung)
    expect(rueckrufe.onToggleLabel).toHaveBeenCalledTimes(1)
  })

  it('zeigt am Desktop kein Info-Symbol und die Erklärungen beim Darüberfahren wie heute (AK 7)', async () => {
    zeigeLeiste({ labelOptions: optionen })
    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))

    expect(screen.queryByRole('button', { name: de.ERKLAERUNG_INFO.ZEIGEN('kit:clarify') })).not.toBeInTheDocument()
    await userEvent.hover(screen.getByRole('menuitem', { name: 'kit:clarify — keine gewählte Karte' }))
    expect(await screen.findByRole('tooltip')).toHaveTextContent(KLAEREN.bedeutung)
  })
})

describe('BulkActionBar auf Englisch', () => {
  const zeigeEn = (over: Partial<Parameters<typeof BulkActionBar>[0]> = {}) =>
    renderMitSprache(
      <BulkActionBar
        count={3}
        canMove
        labelOptions={[
          { label: label(1, 'Fehler'), zustand: 'alle' },
          { label: label(2, 'Idee'), zustand: 'einige' },
          { label: label(3, 'Doku'), zustand: 'keine' },
        ]}
        labelsDisabledReason={null}
        onToggleLabel={vi.fn()}
        epicOptions={[{ epic: epic(5, 'Nice to have'), hidden: true }]}
        epicDisabledReason={null}
        onChooseEpic={vi.fn()}
        onArchive={vi.fn()}
        onMove={vi.fn()}
        onDelete={vi.fn()}
        onCancel={vi.fn()}
        {...over}
      />,
      'en',
    )

  it('zeigt Anzahl, Region und Knöpfe auf Englisch', () => {
    zeigeEn()

    expect(screen.getByRole('region', { name: 'Bulk actions' })).toBeInTheDocument()
    expect(screen.getByText('3 selected')).toBeInTheDocument()
    for (const name of ['Labels', 'Assign epic', 'Move', 'Archive', 'Move to trash', 'Cancel']) {
      expect(screen.getByRole('button', { name })).toBeInTheDocument()
    }
  })

  it('nennt den Zustand der Labels im Menü auf Englisch', () => {
    zeigeEn()
    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))

    expect(screen.getByRole('menuitem', { name: 'Fehler — all selected cards' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Idee — some selected cards' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Doku — no selected card' })).toBeInTheDocument()
  })

  it('nennt den Sperrgrund im Namen der Label-Taste auf Englisch', () => {
    zeigeEn({ labelsDisabledReason: 'The board has no labels' })

    expect(screen.getByRole('button', { name: 'Labels — The board has no labels' })).toBeDisabled()
  })

  it('kennzeichnet ausgeblendete Vorhaben und den Sperrgrund auf Englisch', () => {
    zeigeEn()
    fireEvent.click(screen.getByRole('button', { name: 'Assign epic' }))
    expect(screen.getByRole('menuitem', { name: 'Nice to have (hidden)' })).toBeInTheDocument()
  })

  it('nennt den Sperrgrund im Namen der Vorhaben-Taste auf Englisch', () => {
    zeigeEn({ epicDisabledReason: 'the board has no selectable epic' })

    expect(screen.getByRole('button', { name: 'Assign epic — the board has no selectable epic' })).toBeDisabled()
  })
})

describe('BulkActionBar — Ansicht als XY (Issue #1926)', () => {
  it('zeigt jede Sammelaktion gesperrt mit dem Hinweis „Vorschau“, Abbrechen bleibt bedienbar', () => {
    auth.vorschau = vorschau
    const rueckrufe = zeigeLeiste({ labelsDisabledReason: 'Kein Label auf dem Board' })

    const gesperrt = screen.getAllByRole('button', { name: 'Vorschau' })
    expect(gesperrt.map((k) => k.textContent)).toEqual([
      'Labels', 'Vorhaben zuordnen', 'Verschieben', 'Archivieren', 'In den Papierkorb',
    ])
    gesperrt.forEach((k) => {
      expect(k).toBeDisabled()
      fireEvent.click(k)
    })
    expect(rueckrufe.onMove).not.toHaveBeenCalled()
    expect(rueckrufe.onArchive).not.toHaveBeenCalled()
    expect(rueckrufe.onDelete).not.toHaveBeenCalled()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeEnabled()
  })

  it('nennt außerhalb der Vorschau weiter den Sperrgrund statt „Vorschau“', () => {
    zeigeLeiste({ labelsDisabledReason: 'Kein Label auf dem Board' })

    expect(screen.queryByRole('button', { name: 'Vorschau' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Labels/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Archivieren' })).toBeEnabled()
  })
})
