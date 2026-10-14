import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { KitNamenZustand } from '../api/kitNamen'
import type { Label } from '../api/labels'
import { de } from '../i18n/de'
import { TabletQuerProvider } from '../lib/tabletQuer'
import { KIT_NAMEN } from '../test/kitNamen'
import { setzeMedien } from '../test/setup'
import { BulkActionBar, type LabelOption } from './BulkActionBar'
import { renderMitSprache } from '../i18n/renderMitSprache'

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

/** Rendert die Leiste und gibt die Rückrufe zurück, damit Tests sie prüfen können. */
function zeigeLeiste(over: Partial<Parameters<typeof BulkActionBar>[0]> = {}) {
  const eigenschaften = {
    count: 3,
    canMove: true,
    labelOptions: [] as LabelOption[],
    labelsDisabledReason: null,
    onToggleLabel: vi.fn(),
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
    for (const name of ['Labels', 'Move', 'Archive', 'Move to trash', 'Cancel']) {
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
})
