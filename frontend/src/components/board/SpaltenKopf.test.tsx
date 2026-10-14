import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { BoardColumn } from '../../api/boards'
import { TabletQuerProvider } from '../../lib/tabletQuer'
import { setzeMedien } from '../../test/setup'
import { SpaltenKopf } from './SpaltenKopf'

const spalte: BoardColumn = { id: 10, name: 'In Prüfung', position: 0, wipLimit: 4 }

function renderKopf(showStructureEdit: boolean) {
  return render(
    <TabletQuerProvider>
      <div data-testid="kopfzeile">
        <SpaltenKopf
          column={spalte}
          count={2}
          grenzeErreicht={false}
          hiddenCount={0}
          angezeigteIds={[]}
          selectedIds={new Set()}
          selectionMode={false}
          canEdit
          showStructureEdit={showStructureEdit}
          naechsteRichtung="ASC"
          sortiertGerade={false}
          colDrag={null}
          onSpaltenAuswahl={vi.fn()}
          onAusblendungAufheben={vi.fn()}
          onSortieren={vi.fn()}
          onBearbeiten={vi.fn()}
          onLoeschen={vi.fn()}
          onZugBeginn={vi.fn()}
          onAblage={vi.fn()}
          onZugEnde={vi.fn()}
        />
      </div>
    </TabletQuerProvider>,
  )
}

const knopf = (name: string) => screen.getByRole('button', { name })
const kopf = () => screen.getByTestId('column-header-10')
const name = () => screen.getByRole('heading', { name: 'In Prüfung' })

describe('SpaltenKopf auf dem Tablet im Querformat (Plan #1707, E6)', () => {
  it('stellt im Bearbeitungsmodus Skala und Symbolknöpfe in eine eigene zweite Zeile', () => {
    setzeMedien({ tabletQuer: true })
    renderKopf(true)

    const oben = screen.getByTestId('column-header-kennung-10')
    const unten = screen.getByTestId('column-header-werkzeuge-10')
    expect(oben).toContainElement(name())
    expect(oben).toHaveTextContent('2/4')
    expect(oben).not.toContainElement(knopf('Spalte In Prüfung bearbeiten'))
    expect(unten).toContainElement(screen.getByRole('meter'))
    expect(unten).toContainElement(knopf('Spalte In Prüfung bearbeiten'))
    expect(unten).toContainElement(knopf('Spalte In Prüfung löschen'))
    expect(unten).toContainElement(knopf('Spalte In Prüfung nach Nummer aufsteigend sortieren'))
    // Beide Zeilen gehören zum ziehbaren Kopf — die Spalte bleibt an ihm greifbar.
    expect(kopf()).toHaveAttribute('draggable', 'true')
    expect(kopf()).toContainElement(oben)
    expect(kopf()).toContainElement(unten)
  })

  it('bleibt außerhalb des Bearbeitungsmodus einzeilig', () => {
    setzeMedien({ tabletQuer: true })
    renderKopf(false)

    expect(kopf()).toContainElement(name())
    expect(kopf()).toContainElement(knopf('Spalte In Prüfung nach Nummer aufsteigend sortieren'))
    expect(screen.queryByTestId('column-header-werkzeuge-10')).not.toBeInTheDocument()
  })

  it('bleibt am Desktop im Bearbeitungsmodus einzeilig wie bisher, die Skala neben dem Kopf', () => {
    renderKopf(true)

    expect(kopf()).toContainElement(name())
    expect(kopf()).toContainElement(knopf('Spalte In Prüfung bearbeiten'))
    expect(kopf()).toContainElement(knopf('Spalte In Prüfung löschen'))
    expect(kopf()).not.toContainElement(screen.getByRole('meter'))
    expect(screen.getByTestId('kopfzeile')).toContainElement(screen.getByRole('meter'))
    expect(screen.queryByTestId('column-header-kennung-10')).not.toBeInTheDocument()
    expect(screen.queryByTestId('column-header-werkzeuge-10')).not.toBeInTheDocument()
  })
})
