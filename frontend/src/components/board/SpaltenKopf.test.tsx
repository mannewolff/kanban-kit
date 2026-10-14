import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { BoardColumn } from '../../api/boards'
import { TabletQuerProvider } from '../../lib/tabletQuer'
import { cssRegel } from '../../test/cssRegel'
import { setzeMedien } from '../../test/setup'
import { SpaltenKopf } from './SpaltenKopf'

const spalte: BoardColumn = { id: 10, name: 'In Prüfung', position: 0, wipLimit: 4 }

const handler = {
  onZugBeginn: vi.fn(),
  onAblage: vi.fn(),
  onZugEnde: vi.fn(),
  onPointerDown: vi.fn(),
}

function renderKopf(showStructureEdit: boolean, fingerZustand?: 'wartet' | 'zieht') {
  Object.values(handler).forEach((f) => f.mockReset())
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
          onZugBeginn={handler.onZugBeginn}
          onAblage={handler.onAblage}
          onZugEnde={handler.onZugEnde}
          fingerGriff={{ onPointerDown: handler.onPointerDown }}
          fingerZustand={fingerZustand}
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
    // Beide Zeilen gehören zum Kopf, den der Finger greift — die Spalte bleibt an ihm greifbar.
    expect(kopf()).toHaveAttribute('draggable', 'false')
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

describe('SpaltenKopf — Spalten per Finger umordnen (Plan #1707, E3/E4)', () => {
  it('greift im Bearbeitungsmodus per Finger und ist nicht nativ ziehbar', () => {
    setzeMedien({ tabletQuer: true })
    renderKopf(true)

    fireEvent.pointerDown(kopf())

    expect(kopf()).toHaveAttribute('draggable', 'false')
    expect(handler.onPointerDown).toHaveBeenCalledTimes(1)
    // jsdom kennt `touch-action` im berechneten Stil nicht; die erzeugte Regel belegt es.
    expect(cssRegel(kopf())).toContain('touch-action: pan-x pan-y')
    expect(cssRegel(kopf())).toContain('-webkit-touch-callout: none')
    expect(cssRegel(kopf())).toContain('user-select: none')
  })

  it('löst beim kurzen Antippen nichts aus', () => {
    setzeMedien({ tabletQuer: true })
    renderKopf(true)

    fireEvent.pointerDown(kopf())
    fireEvent.pointerUp(kopf())
    fireEvent.click(kopf())

    expect(handler.onZugBeginn).not.toHaveBeenCalled()
    expect(handler.onAblage).not.toHaveBeenCalled()
    expect(handler.onZugEnde).not.toHaveBeenCalled()
  })

  it('zeigt den Stand des Fingerzugs am Kopf', () => {
    setzeMedien({ tabletQuer: true })
    renderKopf(true, 'zieht')

    expect(kopf()).toHaveAttribute('data-zieh-zustand', 'zieht')
  })

  it('greift außerhalb des Bearbeitungsmodus nicht', () => {
    setzeMedien({ tabletQuer: true })
    renderKopf(false)

    fireEvent.pointerDown(kopf())

    expect(handler.onPointerDown).not.toHaveBeenCalled()
    expect(kopf()).toHaveAttribute('draggable', 'false')
  })

  it.each([true, false])('bleibt am Desktop nativ ziehbar wie heute (Bearbeitungsmodus: %s), ohne Griff', (bearbeiten) => {
    renderKopf(bearbeiten)

    fireEvent.pointerDown(kopf())

    expect(kopf()).toHaveAttribute('draggable', String(bearbeiten))
    expect(cssRegel(kopf())).not.toContain('touch-action')
    expect(handler.onPointerDown).not.toHaveBeenCalled()
  })
})
