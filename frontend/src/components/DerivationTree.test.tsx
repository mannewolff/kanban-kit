import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DerivationTree } from './DerivationTree'
import { ApiError } from '../api/client'
import type { DerivationNode } from '../api/cards'

vi.mock('../api/cards', async (original) => {
  const actual = await original<typeof import('../api/cards')>()
  return { ...actual, cardsApi: { derivationTree: vi.fn() } }
})

const { cardsApi } = await import('../api/cards')
const derivationTree = cardsApi.derivationTree as unknown as ReturnType<typeof vi.fn>

function node(overrides: Partial<DerivationNode> & { number: number }): DerivationNode {
  return {
    title: `Karte ${overrides.number}`,
    type: 'CARD',
    derivedFrom: null,
    depth: 0,
    done: false,
    blocked: false,
    dependencies: [],
    externalDependencies: [],
    externalOrigin: false,
    broken: false,
    ...overrides,
  }
}

/** Dreistufige Kette 1 <- 2 <- 3, wie sie #609 liefert. */
const kette: DerivationNode[] = [
  node({ number: 1 }),
  node({ number: 2, depth: 1, derivedFrom: 1 }),
  node({ number: 3, depth: 2, derivedFrom: 2 }),
]

async function zeigeBaum(daten: DerivationNode[], onOpenCard = vi.fn()) {
  derivationTree.mockResolvedValue(daten)
  render(<DerivationTree boardId={7} onOpenCard={onOpenCard} />)
  await screen.findByRole('tree')
  return onOpenCard
}

/** Die Zeilen in Darstellungsreihenfolge — also ohne eingeklappte Kinder. */
function zeilen() {
  return screen.getAllByRole('treeitem')
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('DerivationTree', () => {
  it('stellt eine dreistufige Kette mit aria-level 1, 2, 3 dar', async () => {
    await zeigeBaum(kette)

    expect(zeilen().map((z) => z.getAttribute('aria-level'))).toEqual(['1', '2', '3'])
  })

  it('zeichnet Behälter und Zeilen aus; nur Zeilen mit Kindern tragen aria-expanded', async () => {
    await zeigeBaum(kette)

    expect(screen.getByRole('tree')).toBeInTheDocument()
    const alle = zeilen()
    expect(alle).toHaveLength(3)
    expect(alle[0]).toHaveAttribute('aria-expanded', 'true')
    expect(alle[1]).toHaveAttribute('aria-expanded', 'true')
    // Das Blatt hat keine Kinder und darf deshalb kein aria-expanded tragen.
    expect(alle[2]).not.toHaveAttribute('aria-expanded')
  })

  it('klappt mit Pfeil links zu und mit Pfeil rechts wieder auf', async () => {
    const user = userEvent.setup()
    await zeigeBaum(kette)

    zeilen()[0].focus()
    await user.keyboard('{ArrowLeft}')

    expect(zeilen()).toHaveLength(1)
    expect(zeilen()[0]).toHaveAttribute('aria-expanded', 'false')

    await user.keyboard('{ArrowRight}')

    expect(zeilen()).toHaveLength(3)
    expect(zeilen()[0]).toHaveAttribute('aria-expanded', 'true')
  })

  it('bewegt den Fokus mit Pfeil runter und hoch', async () => {
    const user = userEvent.setup()
    await zeigeBaum(kette)

    zeilen()[0].focus()
    await user.keyboard('{ArrowDown}')
    expect(zeilen()[1]).toHaveFocus()

    await user.keyboard('{ArrowUp}')
    expect(zeilen()[0]).toHaveFocus()
  })

  it('springt mit Home und End an Anfang und Ende', async () => {
    const user = userEvent.setup()
    await zeigeBaum(kette)

    zeilen()[0].focus()
    await user.keyboard('{End}')
    expect(zeilen()[2]).toHaveFocus()

    await user.keyboard('{Home}')
    expect(zeilen()[0]).toHaveFocus()
  })

  it('überspringt beim Weiterbewegen die Kinder einer zugeklappten Zeile', async () => {
    const user = userEvent.setup()
    // Zwei Wurzeln, damit es nach dem Zuklappen ueberhaupt eine naechste Zeile gibt.
    await zeigeBaum([...kette, node({ number: 9 })])

    zeilen()[0].focus()
    await user.keyboard('{ArrowLeft}{ArrowDown}')

    // Nicht die versteckte 2, sondern die naechste sichtbare Zeile.
    expect(zeilen()).toHaveLength(2)
    expect(zeilen()[1]).toHaveFocus()
    expect(zeilen()[1]).toHaveTextContent('#9')
  })

  it('ist ein einzelner Tab-Stopp', async () => {
    await zeigeBaum(kette)

    const tabbar = zeilen().filter((z) => z.getAttribute('tabindex') === '0')
    expect(tabbar).toHaveLength(1)
    expect(zeilen()[0]).toHaveAttribute('tabindex', '0')
  })

  it('springt mit Pfeil rechts von einer offenen Zeile zum ersten Kind', async () => {
    const user = userEvent.setup()
    await zeigeBaum(kette)

    zeilen()[0].focus()
    await user.keyboard('{ArrowRight}')

    expect(zeilen()[1]).toHaveFocus()
  })

  it('springt mit Pfeil links von einem Blatt zum Elternteil', async () => {
    const user = userEvent.setup()
    await zeigeBaum(kette)

    zeilen()[2].focus()
    await user.keyboard('{ArrowLeft}')

    expect(zeilen()[1]).toHaveFocus()
  })

  it('lässt Pfeiltasten an den Rändern des Baums wirkungslos', async () => {
    const user = userEvent.setup()
    // Eine einzelne Wurzel ohne Kinder: kein Elternteil, kein Kind, kein Nachbar.
    await zeigeBaum([node({ number: 1, externalOrigin: true, derivedFrom: 400 })])

    zeilen()[0].focus()
    await user.keyboard('{ArrowUp}{ArrowDown}{ArrowLeft}{ArrowRight}')

    expect(zeilen()).toHaveLength(1)
    expect(zeilen()[0]).toHaveFocus()
  })

  it('macht eine erledigte Zeile auch textlich als erledigt erkennbar', async () => {
    await zeigeBaum([node({ number: 1 }), node({ number: 2, depth: 1, done: true })])

    expect(zeilen()[1]).toHaveTextContent(/erledigt/i)
  })

  it('macht eine blockierte Zeile auch textlich erkennbar', async () => {
    await zeigeBaum([node({ number: 1 }), node({ number: 2, depth: 1, blocked: true })])

    expect(zeilen()[1]).toHaveTextContent(/blockiert/i)
  })

  it('weist eine unterbrochene Kette hin und lässt den Baum bedienbar', async () => {
    const user = userEvent.setup()
    await zeigeBaum([
      node({ number: 1, broken: true, derivedFrom: 2 }),
      node({ number: 2, depth: 1, broken: true, derivedFrom: 1 }),
    ])

    expect(zeilen()[0]).toHaveTextContent(/unterbrochen/i)

    zeilen()[0].focus()
    await user.keyboard('{ArrowDown}')
    expect(zeilen()[1]).toHaveFocus()
  })

  it('zeigt eine interne Abhängigkeit als nicht bedienbare Marke', async () => {
    await zeigeBaum([node({ number: 1 }), node({ number: 2, depth: 1, dependencies: [5] })])

    const marke = screen.getByText('⇠ #5')
    expect(marke).toBeInTheDocument()
    expect(marke).not.toHaveAttribute('tabindex')
    expect(screen.queryByRole('button', { name: /#5/ })).toBeNull()
    expect(screen.queryByRole('link', { name: /#5/ })).toBeNull()
  })

  it('markiert eine externe Abhängigkeit, ohne sie aufzulösen', async () => {
    await zeigeBaum([
      node({ number: 1 }),
      node({ number: 2, depth: 1, externalDependencies: [4242] }),
    ])

    const marke = screen.getByText(/4242/)
    expect(marke).toHaveTextContent(/extern/i)
    expect(marke).not.toHaveAttribute('tabindex')
    expect(screen.queryByRole('button', { name: /4242/ })).toBeNull()
  })

  it('markiert eine board-fremde Herkunft', async () => {
    await zeigeBaum([node({ number: 1, externalOrigin: true, derivedFrom: 400 })])

    expect(zeilen()[0]).toHaveTextContent(/400/)
    expect(zeilen()[0]).toHaveTextContent(/extern/i)
  })

  it('öffnet mit Enter die Karte der fokussierten Zeile', async () => {
    const user = userEvent.setup()
    const onOpenCard = await zeigeBaum(kette)

    zeilen()[1].focus()
    await user.keyboard('{Enter}')

    expect(onOpenCard).toHaveBeenCalledTimes(1)
    expect(onOpenCard).toHaveBeenCalledWith(2)
  })

  it('zeigt bei leerer Liste einen Hinweis statt eines leeren Baums', async () => {
    derivationTree.mockResolvedValue([])
    render(<DerivationTree boardId={7} onOpenCard={vi.fn()} />)

    expect(await screen.findByText(/keine herkunft/i)).toBeInTheDocument()
    expect(screen.queryByRole('tree')).toBeNull()
  })

  it('zeigt während des Ladens einen Platzhalter', () => {
    derivationTree.mockReturnValue(new Promise(() => {}))
    render(<DerivationTree boardId={7} onOpenCard={vi.fn()} />)

    expect(screen.getByRole('progressbar')).toBeInTheDocument()
  })

  it('zeigt bei fehlgeschlagenem Abruf eine Fehlermeldung', async () => {
    derivationTree.mockRejectedValue(new ApiError(403, 'Kein Zugriff auf dieses Board.'))
    render(<DerivationTree boardId={7} onOpenCard={vi.fn()} />)

    expect(await screen.findByText(/kein zugriff auf dieses board/i)).toBeInTheDocument()
    expect(screen.queryByRole('tree')).toBeNull()
  })

  it('zeigt auch bei einem Fehler ohne API-Kontext eine Meldung', async () => {
    derivationTree.mockRejectedValue(new Error('Netzwerk weg'))
    render(<DerivationTree boardId={7} onOpenCard={vi.fn()} />)

    expect(await screen.findByText(/herkunftsbaum/i)).toBeInTheDocument()
    expect(screen.queryByRole('tree')).toBeNull()
  })

  it('löst keinen Schreibaufruf aus', async () => {
    const user = userEvent.setup()
    await zeigeBaum(kette)

    zeilen()[0].focus()
    await user.keyboard('{ArrowLeft}{ArrowRight}{ArrowDown}{Enter}')

    // Gelesen wird ueber denselben Client — gezaehlt wird deshalb der Lesezugriff, nicht "nie".
    expect(derivationTree).toHaveBeenCalledTimes(1)
    expect(derivationTree).toHaveBeenCalledWith(7)
  })

  it('nennt Vorhaben im gerenderten Baum nicht „Epic"', async () => {
    await zeigeBaum([
      node({ number: 1, type: 'EPIC' }),
      node({ number: 2, depth: 1, derivedFrom: 1 }),
    ])

    expect(screen.queryByText(/epic/i)).toBeNull()
    expect(zeilen()[0]).toHaveTextContent(/vorhaben/i)
  })

  it('lässt eine unbehandelte Taste durch, ohne den Baum zu verändern', async () => {
    const user = userEvent.setup()
    const onOpenCard = await zeigeBaum(kette)

    zeilen()[0].focus()
    await user.keyboard('{Escape}')

    expect(zeilen()).toHaveLength(3)
    expect(zeilen()[0]).toHaveFocus()
    expect(onOpenCard).not.toHaveBeenCalled()
  })

  it('verwirft eine Antwort, die erst nach dem Verlassen der Ansicht eintrifft', async () => {
    // Ohne diesen Schutz schriebe eine langsame Antwort in eine Ansicht, die es nicht mehr gibt —
    // beim Boardwechsel waere das der Baum des vorigen Boards.
    let aufloesen: (daten: DerivationNode[]) => void = () => {}
    derivationTree.mockReturnValue(
      new Promise<DerivationNode[]>((r) => {
        aufloesen = r
      }),
    )
    const { unmount } = render(<DerivationTree boardId={7} onOpenCard={vi.fn()} />)
    unmount()

    aufloesen(kette)
    await Promise.resolve()

    expect(screen.queryByRole('tree')).toBeNull()
  })

  it('lädt neu, wenn sich das Board ändert', async () => {
    derivationTree.mockResolvedValue(kette)
    const { rerender } = render(<DerivationTree boardId={7} onOpenCard={vi.fn()} />)
    await screen.findByRole('tree')

    rerender(<DerivationTree boardId={8} onOpenCard={vi.fn()} />)

    await waitFor(() => expect(derivationTree).toHaveBeenCalledTimes(2))
    expect(derivationTree).toHaveBeenLastCalledWith(8)
  })
})
