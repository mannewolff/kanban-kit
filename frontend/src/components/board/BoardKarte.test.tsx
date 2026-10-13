import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, test, vi } from 'vitest'
import type { Card } from '../../api/cards'
import type { KitNamenZustand } from '../../api/kitNamen'
import { de } from '../../i18n/de'
import { KIT_NAMEN } from '../../test/kitNamen'
import { BoardKarte } from './BoardKarte'
import { renderMitSprache } from '../../i18n/renderMitSprache'

vi.mock('../../api/kitNamen', async (original) => ({
  ...(await original<typeof import('../../api/kitNamen')>()),
  useKitNamen: (): KitNamenZustand => ({ status: 'fertig', daten: KIT_NAMEN }),
}))

const card: Card = {
  id: 100, boardId: 1, columnId: 10, number: 1, title: 'Aufgabe', description: null, excerpt: null,
  positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [],
  type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
  derivedFrom: null, status: null, canSetStatus: false,
}

const zeigen = (over: { selectionMode: boolean; selected: boolean; canEdit: boolean }) =>
  render(
    <BoardKarte
      card={card} epic={undefined} done={false} spaltenname="Backlog" dichte="normal" bewegt={false}
      members={[]} boardLabels={[]} retentionDays={30}
      onDragStart={vi.fn()} onDragEnd={vi.fn()} onSelect={vi.fn()} onOpen={vi.fn()} onMenu={vi.fn()}
      {...over}
    />,
  )

describe('BoardKarte — Ziehbarkeit (#1324)', () => {
  it.each([
    { fall: 'außerhalb des Auswahlmodus mit Bearbeitungsrecht', selectionMode: false, selected: false, canEdit: true, ziehbar: true },
    { fall: 'ausgewählt im Auswahlmodus mit Bearbeitungsrecht', selectionMode: true, selected: true, canEdit: true, ziehbar: true },
    { fall: 'nicht ausgewählt im Auswahlmodus', selectionMode: true, selected: false, canEdit: true, ziehbar: false },
    { fall: 'ausgewählt, aber ohne Bearbeitungsrecht', selectionMode: true, selected: true, canEdit: false, ziehbar: false },
    { fall: 'ohne Bearbeitungsrecht außerhalb des Auswahlmodus', selectionMode: false, selected: false, canEdit: false, ziehbar: false },
  ])('ist $fall ziehbar: $ziehbar', ({ selectionMode, selected, canEdit, ziehbar }) => {
    zeigen({ selectionMode, selected, canEdit })

    expect(screen.getByTestId('card-100')).toHaveAttribute('draggable', String(ziehbar))
  })

  it('zeigt im Auswahlmodus kein ⋮-Menü, auch nicht an einer ziehbaren Karte', () => {
    zeigen({ selectionMode: true, selected: true, canEdit: true })

    expect(screen.queryByLabelText('Menü Aufgabe')).not.toBeInTheDocument()
  })
})

describe('BoardKarte auf Englisch', () => {
  const zeigenEn = (over: Partial<Card>, weiter: { selectionMode?: boolean; spaltenname?: string } = {}) =>
    renderMitSprache(
      <BoardKarte
        card={{ ...card, ...over }} epic={undefined} done={false} spaltenname={weiter.spaltenname ?? 'Backlog'}
        dichte="normal" bewegt={false} selectionMode={weiter.selectionMode ?? false} selected={false} canEdit
        members={[{ userId: 7, email: 'a@b.c', displayName: 'Ada Lovelace', role: 'MEMBER' }]}
        boardLabels={[{ id: 3, boardId: 1, name: 'Fehler', color: '#ff0000', countOnEpicTile: false }]}
        retentionDays={30}
        onDragStart={vi.fn()} onDragEnd={vi.fn()} onSelect={vi.fn()} onOpen={vi.fn()} onMenu={vi.fn()}
      />,
      'en',
    )

  it('benennt Menü, Labels, Zuständige, Status und Frist auf Englisch', () => {
    zeigenEn({ labels: [3], assignees: [7], dueDate: '2026-10-13', status: 'READY' })

    expect(screen.getByLabelText('Menu Aufgabe')).toBeInTheDocument()
    expect(screen.getByLabelText('Labels Aufgabe')).toBeInTheDocument()
    expect(screen.getByLabelText('Assignees Aufgabe')).toBeInTheDocument()
    expect(screen.getByLabelText('Status Ready')).toBeInTheDocument()
    expect(screen.getByLabelText('Due Aufgabe')).toHaveTextContent('due Oct 13, 2026')
    // Daten bleiben unverändert (Kriterium 4).
    expect(screen.getByText('Fehler')).toBeInTheDocument()
  })

  it('benennt das Auswahlkästchen auf Englisch', () => {
    zeigenEn({}, { selectionMode: true })

    expect(screen.getByRole('checkbox', { name: 'Select card Aufgabe' })).toBeInTheDocument()
  })
})

describe('BoardKarte — Erklärung der Systemlabels (#1672)', () => {
  const LABELS = [
    { id: 1, boardId: 1, name: 'kit:clarify', color: '#6a1b9a', countOnEpicTile: false },
    { id: 2, boardId: 1, name: 'run:running', color: '#1565c0', countOnEpicTile: false },
    { id: 3, boardId: 1, name: 'Aktuell', color: '#ff0000', countOnEpicTile: false },
  ]

  const zeigenMit = (labelIds: number[], onOpen = vi.fn()) => {
    render(
      <BoardKarte
        card={{ ...card, labels: labelIds }} epic={undefined} done={false} spaltenname="Backlog" dichte="normal"
        bewegt={false} selectionMode={false} selected={false} canEdit
        members={[]} boardLabels={LABELS} retentionDays={30}
        onDragStart={vi.fn()} onDragEnd={vi.fn()} onSelect={vi.fn()} onOpen={onOpen} onMenu={vi.fn()}
      />,
    )
    return onOpen
  }

  // eslint-disable-next-line testing-library/no-node-access -- ein Chip hat keine Rolle; Tooltip und tabindex sitzen an seiner Wurzel, nicht am Text
  const label = (name: string) => screen.getByText(name).closest('.MuiChip-root') as HTMLElement

  test('Szenario: Erklärung eines geschützten Labels', async () => {
    // Angenommen eine Karte im Board trägt das Label `kit:clarify`
    zeigenMit([1])

    // Wenn der Nutzer mit der Maus über das Label fährt
    await userEvent.hover(label('kit:clarify'))

    // Dann erscheint eine Erklärung, was das Label bedeutet, wer es setzt, dass nur angemeldet
    // abgenommen werden kann und wann das geschieht, und was der Mensch tun soll
    const tooltip = await screen.findByRole('tooltip')
    const k = de.LABEL_ERKLAERUNG.SACHEN.KLAEREN
    expect(tooltip).toHaveTextContent(k.bedeutung)
    expect(tooltip).toHaveTextContent(k.setzt)
    expect(tooltip).toHaveTextContent(de.LABEL_ERKLAERUNG.SCHUTZ.NUR_MENSCH_NIMMT_AB)
    expect(tooltip).toHaveTextContent(k.verschwindet)
    expect(tooltip).toHaveTextContent(k.handlung)
  })

  test('Szenario: Erklärung eines Maschinen-Labels', async () => {
    // Angenommen eine Karte trägt während eines Nachtlaufs das Label `run:running`
    zeigenMit([2])

    // Wenn der Nutzer mit der Maus über das Label fährt
    await userEvent.hover(label('run:running'))

    // Dann sagt die Erklärung, wer das Label setzt und wann es wieder verschwindet,
    // und sie nennt keine Handlung für den Menschen
    const tooltip = await screen.findByRole('tooltip')
    const l = de.LABEL_ERKLAERUNG.SACHEN.LAUF_LAEUFT
    expect(within(tooltip).getAllByText(/.+/).map((p) => p.textContent)).toEqual([l.bedeutung, l.setzt, l.verschwindet])
  })

  test('Szenario: Selbst angelegtes Label', async () => {
    // Angenommen eine Karte trägt das selbst angelegte Label `Aktuell`
    zeigenMit([3])

    // Wenn der Nutzer mit der Maus über das Label fährt
    await userEvent.hover(label('Aktuell'))

    // Dann erscheint keine Erklärung, und das Label ist nicht fokussierbar
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    expect(label('Aktuell')).not.toHaveAttribute('tabindex')
  })

  it('öffnet die Karte beim Klick auf ein Systemlabel wie bisher', async () => {
    const onOpen = zeigenMit([1])

    await userEvent.click(label('kit:clarify'))

    expect(onOpen).toHaveBeenCalledTimes(1)
  })

  it('öffnet die Karte nicht, wenn das Label den Fokus bekommt', async () => {
    const onOpen = zeigenMit([1])

    act(() => label('kit:clarify').focus())

    expect(label('kit:clarify')).toHaveFocus()
    expect(onOpen).not.toHaveBeenCalled()
  })
})
