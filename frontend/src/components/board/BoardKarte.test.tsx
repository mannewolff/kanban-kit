import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, test, vi } from 'vitest'
import type { Card } from '../../api/cards'
import type { KitNamenZustand } from '../../api/kitNamen'
import { de } from '../../i18n/de'
import { TabletQuerProvider } from '../../lib/tabletQuer'
import { KIT_NAMEN } from '../../test/kitNamen'
import { cssRegel } from '../../test/cssRegel'
import { setzeMedien } from '../../test/setup'
import { ErklaerungOffenProvider } from '../Erklaerung'
import { BoardKarte } from './BoardKarte'
import { renderMitSprache } from '../../i18n/renderMitSprache'

// Vorschau-Zustand der „Ansicht als XY“ (Plan #1914, E15); außer im eigenen Fall ohne Vorschau.
const auth = vi.hoisted(() => ({ vorschau: null as null | Record<string, string> }))
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ vorschau: auth.vorschau }) }))
beforeEach(() => {
  auth.vorschau = null
})

vi.mock('../../api/kitNamen', async (original) => ({
  ...(await original<typeof import('../../api/kitNamen')>()),
  useKitNamen: (): KitNamenZustand => ({ status: 'fertig', daten: KIT_NAMEN }),
}))

const card: Card = {
  id: 100, boardId: 1, columnId: 10, number: 1, title: 'Aufgabe', description: null, excerpt: null,
  positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [],
  type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
  derivedFrom: null, status: null, canSetStatus: false, herkunft: null,
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

  it('ist in der Ansicht als XY nicht ziehbar, bleibt aber anklickbar (Issue #1926)', () => {
    auth.vorschau = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }
    zeigen({ selectionMode: false, selected: false, canEdit: true })

    expect(screen.getByTestId('card-100')).toHaveAttribute('draggable', 'false')
    expect(screen.getByRole('button', { name: 'Aufgabe' })).toBeEnabled()
  })

  it('zeigt im Auswahlmodus kein ⋮-Menü, auch nicht an einer ziehbaren Karte', () => {
    zeigen({ selectionMode: true, selected: true, canEdit: true })

    expect(screen.queryByLabelText('Menü Aufgabe')).not.toBeInTheDocument()
  })
})

describe('BoardKarte — Umbruch langer Wörter im Titel (Issue #1942)', () => {
  it('bricht einen Titel ohne Leerzeichen innerhalb der Karte um', () => {
    zeigen({ selectionMode: false, selected: false, canEdit: true })

    expect(getComputedStyle(screen.getByRole('heading', { level: 4 })).overflowWrap).toBe('anywhere')
  })
})

describe('BoardKarte auf dem Tablet im Querformat (Plan #1707, E3/E4)', () => {
  const zeigenTablet = (
    over: Partial<{ selectionMode: boolean; selected: boolean; canEdit: boolean; fingerZustand: 'wartet' | 'zieht' }> = {},
  ) => {
    const onOpen = vi.fn()
    const onPointerDown = vi.fn()
    render(
      <TabletQuerProvider>
        <BoardKarte
          card={card} epic={undefined} done={false} spaltenname="Backlog" dichte="normal" bewegt={false}
          members={[]} boardLabels={[]} retentionDays={30} selectionMode={false} selected={false} canEdit
          fingerGriff={{ onPointerDown }}
          onDragStart={vi.fn()} onDragEnd={vi.fn()} onSelect={vi.fn()} onOpen={onOpen} onMenu={vi.fn()}
          {...over}
        />
      </TabletQuerProvider>,
    )
    return { onOpen, onPointerDown, karte: screen.getByTestId('card-100') }
  }

  it('ist nicht nativ ziehbar, trägt ihre Kennung und die Touch-Stile', () => {
    setzeMedien({ tabletQuer: true })
    const { karte } = zeigenTablet()

    expect(karte).toHaveAttribute('draggable', 'false')
    expect(karte).toHaveAttribute('data-karte-id', '100')
    // jsdom kennt `touch-action` im berechneten Stil nicht; die erzeugte Regel belegt es.
    expect(cssRegel(karte)).toContain('touch-action: pan-x pan-y')
    expect(cssRegel(karte)).toContain('-webkit-touch-callout: none')
    expect(cssRegel(karte)).toContain('user-select: none')
  })

  it('reicht das Aufsetzen des Fingers an den Griff und öffnet beim kurzen Antippen wie heute', () => {
    setzeMedien({ tabletQuer: true })
    const { karte, onOpen, onPointerDown } = zeigenTablet()

    fireEvent.pointerDown(karte)
    fireEvent.click(karte)

    expect(onPointerDown).toHaveBeenCalledTimes(1)
    expect(onOpen).toHaveBeenCalledTimes(1)
  })

  it('gibt einer nicht ziehbaren Karte keinen Griff', () => {
    setzeMedien({ tabletQuer: true })
    const { karte, onPointerDown } = zeigenTablet({ selectionMode: true, selected: false })

    fireEvent.pointerDown(karte)

    expect(onPointerDown).not.toHaveBeenCalled()
  })

  it('zeigt den Stand des Fingerzugs an', () => {
    setzeMedien({ tabletQuer: true })
    const { karte } = zeigenTablet({ fingerZustand: 'zieht' })

    expect(karte).toHaveAttribute('data-zieh-zustand', 'zieht')
  })

  it('bleibt am Desktop nativ ziehbar, ohne Kennung und ohne Griff', () => {
    const { karte, onPointerDown } = zeigenTablet()

    fireEvent.pointerDown(karte)

    expect(karte).toHaveAttribute('draggable', 'true')
    expect(karte).not.toHaveAttribute('data-karte-id')
    expect(cssRegel(karte)).not.toContain('touch-action')
    expect(onPointerDown).not.toHaveBeenCalled()
  })
})

describe('BoardKarte: Erklärungen der Labels auf dem Tablet im Querformat (Plan #1707, E7)', () => {
  it("Szenario: Erklärung eines Labels", async () => {
    // Angenommen ein Mensch bedient ein Board auf einem Tablet im Querformat per Finger
    setzeMedien({ tabletQuer: true })
    // Und eine Karte trägt ein Systemlabel mit einer Erklärung
    const onOpen = vi.fn()
    renderMitSprache(
      <TabletQuerProvider>
        <ErklaerungOffenProvider>
          <BoardKarte
            card={{ ...card, labels: [1, 2] }} epic={undefined} done={false} spaltenname="Backlog" dichte="normal"
            bewegt={false} selectionMode={false} selected={false} canEdit members={[]} retentionDays={30}
            boardLabels={[
              { id: 1, boardId: 1, name: 'kit:clarify', color: '#ff0000', countOnEpicTile: false },
              { id: 2, boardId: 1, name: 'run:running', color: '#00ff00', countOnEpicTile: false },
            ]}
            onDragStart={vi.fn()} onDragEnd={vi.fn()} onSelect={vi.fn()} onOpen={onOpen} onMenu={vi.fn()}
          />
        </ErklaerungOffenProvider>
      </TabletQuerProvider>,
    )

    // Wenn er das Label antippt
    fireEvent.click(screen.getByText('kit:clarify'))

    // Dann öffnet sich die Erklärung des Labels
    expect(await screen.findByRole('tooltip')).toHaveTextContent(de.LABEL_ERKLAERUNG.SACHEN.KLAEREN.bedeutung)
    // Und die Karte öffnet sich nicht
    expect(onOpen).not.toHaveBeenCalled()

    // Wenn er danach neben die Erklärung tippt
    await act(() => new Promise((fertig) => setTimeout(fertig, 0)))
    fireEvent.touchEnd(document.body)
    fireEvent.click(document.body)

    // Dann schließt sie sich
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())

    // Das Öffnen einer zweiten Erklärung schließt die erste
    fireEvent.click(screen.getByText('kit:clarify'))
    await screen.findByRole('tooltip')
    fireEvent.click(screen.getByText('run:running'))
    await waitFor(() => expect(screen.getAllByRole('tooltip')).toHaveLength(1))
    expect(screen.getByRole('tooltip')).toHaveTextContent(de.LABEL_ERKLAERUNG.SACHEN.LAUF_LAEUFT.bedeutung)
    expect(onOpen).not.toHaveBeenCalled()
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

describe('BoardKarte — Herkunft an der Nummer (Issue #1930)', () => {
  const zeigenMitHerkunft = (herkunft: Card['herkunft']) => {
    const onOpen = vi.fn()
    renderMitSprache(
      <BoardKarte
        card={{ ...card, herkunft }} epic={undefined} done={false} spaltenname="Backlog" dichte="normal" bewegt={false}
        members={[]} boardLabels={[]} retentionDays={30} selectionMode={false} selected={false} canEdit
        onDragStart={vi.fn()} onDragEnd={vi.fn()} onSelect={vi.fn()} onOpen={onOpen} onMenu={vi.fn()}
      />,
      'de',
    )
    return onOpen
  }

  it('trägt an der Nummer die Herkunft als Hinweis, und ein Klick darauf öffnet die Karte', async () => {
    const onOpen = zeigenMitHerkunft([{ number: 7, title: '[Plan] P', archived: false, luecke: null }])

    await userEvent.hover(screen.getByText('#1'))

    expect(await screen.findByRole('tooltip')).toHaveTextContent('#7 · [Plan] P')
    expect(screen.getByText('#1')).toHaveAttribute('tabindex', '0')
    await userEvent.click(screen.getByText('#1'))
    expect(onOpen).toHaveBeenCalledTimes(1)
  })

  it('zeigt „Ohne Herkunft“ an einer Karte ohne Vorfahren', async () => {
    zeigenMitHerkunft([])

    await userEvent.hover(screen.getByText('#1'))

    expect(await screen.findByRole('tooltip')).toHaveTextContent(de.BOARD.HERKUNFT_KEINE)
  })
})
