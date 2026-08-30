import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DerivationTree } from './DerivationTree'
import type { DerivationNode } from '../api/cards'

vi.mock('../api/cards', async (original) => {
  const actual = await original<typeof import('../api/cards')>()
  return { ...actual, cardsApi: { epicTree: vi.fn() } }
})

// jsdom implementiert scrollIntoView nicht. Der Stub ist zugleich der Spy fuer Issue #612 — mehr
// als "wurde gerufen" ist an dieser Funktion maschinell nicht pruefbar.
const scrollIntoView = vi.fn()
Element.prototype.scrollIntoView = scrollIntoView

const { cardsApi } = await import('../api/cards')
const epicTree = cardsApi.epicTree as unknown as ReturnType<typeof vi.fn>

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

/** Wurzel 1 mit zwei Kindern; #3 haengt von #2 ab und traegt deshalb die Marke. */
const mitAbhaengigkeit: DerivationNode[] = [
  node({ number: 1 }),
  node({ number: 2, depth: 1, derivedFrom: 1 }),
  node({ number: 3, depth: 1, derivedFrom: 1, dependencies: [2] }),
]

const marke = (nummer: number) => screen.getByRole('button', { name: `Zur Karte #${nummer} springen` })

/**
 * Seit Issue #644 bekommt die Komponente ihre Zeilen als Eigenschaft; das Laden liegt im
 * aufrufenden Dialog. Die Signatur bleibt `async`, damit die Testfaelle darunter unveraendert
 * bleiben — geaendert hat sich nur der Arrange-Teil.
 */
async function zeigeBaum(daten: DerivationNode[], onOpenCard = vi.fn()) {
  render(<DerivationTree rows={daten} onOpenCard={onOpenCard} />)
  await screen.findByRole('tree')
  return onOpenCard
}

/**
 * Fokus setzen und dabei den React-Zustand mitziehen. Ein rohes `el.focus()` loest ueber `onFocus`
 * einen State-Update aus, der ausserhalb von `act(...)` eine Warnung schreibt — und die faellt dem
 * Test auf die Fuesse, der `console.error` prueft.
 */
async function fokusAuf(el: HTMLElement) {
  await act(async () => {
    el.focus()
  })
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

    await fokusAuf(zeilen()[0])
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

    await fokusAuf(zeilen()[0])
    await user.keyboard('{ArrowDown}')
    expect(zeilen()[1]).toHaveFocus()

    await user.keyboard('{ArrowUp}')
    expect(zeilen()[0]).toHaveFocus()
  })

  it('springt mit Home und End an Anfang und Ende', async () => {
    const user = userEvent.setup()
    await zeigeBaum(kette)

    await fokusAuf(zeilen()[0])
    await user.keyboard('{End}')
    expect(zeilen()[2]).toHaveFocus()

    await user.keyboard('{Home}')
    expect(zeilen()[0]).toHaveFocus()
  })

  it('überspringt beim Weiterbewegen die Kinder einer zugeklappten Zeile', async () => {
    const user = userEvent.setup()
    // Zwei Wurzeln, damit es nach dem Zuklappen ueberhaupt eine naechste Zeile gibt.
    await zeigeBaum([...kette, node({ number: 9 })])

    await fokusAuf(zeilen()[0])
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

    await fokusAuf(zeilen()[0])
    await user.keyboard('{ArrowRight}')

    expect(zeilen()[1]).toHaveFocus()
  })

  it('springt mit Pfeil links von einem Blatt zum Elternteil', async () => {
    const user = userEvent.setup()
    await zeigeBaum(kette)

    await fokusAuf(zeilen()[2])
    await user.keyboard('{ArrowLeft}')

    expect(zeilen()[1]).toHaveFocus()
  })

  it('lässt Pfeiltasten an den Rändern des Baums wirkungslos', async () => {
    const user = userEvent.setup()
    // Eine einzelne Wurzel ohne Kinder: kein Elternteil, kein Kind, kein Nachbar.
    await zeigeBaum([node({ number: 1, externalOrigin: true, derivedFrom: 400 })])

    await fokusAuf(zeilen()[0])
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

    await fokusAuf(zeilen()[0])
    await user.keyboard('{ArrowDown}')
    expect(zeilen()[1]).toHaveFocus()
  })

  it('zeigt eine interne Abhängigkeit als Marke ⇠ #N', async () => {
    await zeigeBaum([node({ number: 1 }), node({ number: 2, depth: 1, dependencies: [5] })])

    expect(screen.getByText('⇠ #5')).toBeInTheDocument()
  })

  it('springt beim Auslösen der Marke auf die Zielzeile', async () => {
    const user = userEvent.setup()
    await zeigeBaum(mitAbhaengigkeit)

    await user.click(marke(2))

    expect(zeilen()[1]).toHaveFocus()
    expect(zeilen()[1]).toHaveAttribute('data-jump-target', 'true')
  })

  it('springt per Tastatur mit Enter genauso wie per Maus', async () => {
    const user = userEvent.setup()
    await zeigeBaum(mitAbhaengigkeit)

    await fokusAuf(marke(2))
    await user.keyboard('{Enter}')

    expect(zeilen()[1]).toHaveFocus()
  })

  it('springt auch mit der Leertaste', async () => {
    const user = userEvent.setup()
    await zeigeBaum(mitAbhaengigkeit)

    await fokusAuf(marke(2))
    await user.keyboard('[Space]')

    expect(zeilen()[1]).toHaveFocus()
  })

  it('öffnet mit Enter auf der Zeile weiterhin die Karte, statt zu springen', async () => {
    const user = userEvent.setup()
    const onOpenCard = await zeigeBaum(mitAbhaengigkeit)

    await fokusAuf(zeilen()[2])
    await user.keyboard('{Enter}')

    expect(onOpenCard).toHaveBeenCalledTimes(1)
    expect(onOpenCard).toHaveBeenCalledWith(3)
    expect(zeilen()[2]).toHaveFocus()
  })

  it('erreicht die Marke der fokussierten Zeile per Tab', async () => {
    const user = userEvent.setup()
    await zeigeBaum(mitAbhaengigkeit)

    await fokusAuf(zeilen()[2])
    await user.tab()

    expect(marke(2)).toHaveFocus()
  })

  it('klappt den Pfad zu einem eingeklappten Ziel auf und fokussiert es', async () => {
    const user = userEvent.setup()
    await zeigeBaum([
      node({ number: 1 }),
      node({ number: 2, depth: 1, derivedFrom: 1 }),
      node({ number: 9, dependencies: [2] }),
    ])

    await fokusAuf(zeilen()[0])
    await user.keyboard('{ArrowLeft}')
    expect(zeilen()).toHaveLength(2)

    await user.click(marke(2))

    expect(zeilen()).toHaveLength(3)
    expect(zeilen()[1]).toHaveFocus()
  })

  it('rollt die Zielzeile in den sichtbaren Bereich', async () => {
    const user = userEvent.setup()
    await zeigeBaum(mitAbhaengigkeit)

    await user.click(marke(2))

    expect(scrollIntoView).toHaveBeenCalled()
  })

  it('macht die Zielzeile zur aktiven Zeile — Pfeiltasten laufen von dort weiter', async () => {
    const user = userEvent.setup()
    await zeigeBaum(mitAbhaengigkeit)

    await user.click(marke(2))
    await user.keyboard('{ArrowDown}')

    expect(zeilen()[2]).toHaveFocus()
  })

  it('löscht die Hervorhebung beim nächsten Fokuswechsel', async () => {
    const user = userEvent.setup()
    await zeigeBaum(mitAbhaengigkeit)

    await user.click(marke(2))
    expect(zeilen()[1]).toHaveAttribute('data-jump-target', 'true')

    await user.keyboard('{ArrowDown}')

    expect(screen.queryByTestId('nicht-vorhanden')).toBeNull()
    expect(zeilen()[1]).not.toHaveAttribute('data-jump-target')
    expect(zeilen()[2]).not.toHaveAttribute('data-jump-target')
  })

  it('macht bei zwei Abhängigkeiten beide Marken einzeln auslösbar', async () => {
    const user = userEvent.setup()
    await zeigeBaum([
      node({ number: 1 }),
      node({ number: 2, depth: 1, derivedFrom: 1 }),
      node({ number: 3, depth: 1, derivedFrom: 1 }),
      node({ number: 4, depth: 1, derivedFrom: 1, dependencies: [2, 3] }),
    ])

    await user.click(marke(3))
    expect(zeilen()[2]).toHaveFocus()

    await user.click(marke(2))
    expect(zeilen()[1]).toHaveFocus()
  })

  it('tut nichts, wenn die Marke auf eine Nummer außerhalb des Baums zeigt', async () => {
    const user = userEvent.setup()
    const fehler = vi.spyOn(console, 'error').mockImplementation(() => {})
    // Der Normalfall aus #609: die Zielkarte liegt auf dem Board, aber ohne Herkunftsbezug — und
    // steht deshalb nicht im Baum.
    await zeigeBaum([node({ number: 1 }), node({ number: 2, depth: 1, dependencies: [77] })])

    // Ueber die Tastatur geprueft: Ein Klick fokussiert den Button schon von sich aus, auch im
    // echten Browser — er koennte "der Fokus bleibt, wo er war" also gar nicht belegen.
    await fokusAuf(zeilen()[1])
    await fokusAuf(marke(77))
    await user.keyboard('{Enter}')

    expect(marke(77)).toHaveFocus()
    expect(zeilen()).toHaveLength(2)
    // Kein Sprung: keine Zeile ist als Sprungziel hervorgehoben.
    expect(zeilen().filter((z) => z.hasAttribute('data-jump-target'))).toHaveLength(0)
    expect(fehler).not.toHaveBeenCalled()
    fehler.mockRestore()
  })

  it('markiert eine externe Abhängigkeit, ohne sie aufzulösen', async () => {
    await zeigeBaum([
      node({ number: 1 }),
      node({ number: 2, depth: 1, externalDependencies: [4242] }),
    ])

    const externeMarke = screen.getByText(/4242/)
    expect(externeMarke).toHaveTextContent(/extern/i)
    expect(externeMarke).not.toHaveAttribute('tabindex')
    // Externe Kanten werden markiert, aber nicht aufgeloest (Plan #606, E4) — also kein Sprungziel.
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

    await fokusAuf(zeilen()[1])
    await user.keyboard('{Enter}')

    expect(onOpenCard).toHaveBeenCalledTimes(1)
    expect(onOpenCard).toHaveBeenCalledWith(2)
  })

  it('zeigt bei leerer Liste einen vorhabenbezogenen Hinweis statt eines leeren Baums', () => {
    // Vorhabenbezogen, nicht boardbezogen: Die Ansicht haengt im Dialog EINES Vorhabens (#644),
    // ein Satz ueber das Board waere hier die falsche Aussage.
    render(<DerivationTree rows={[]} onOpenCard={vi.fn()} />)

    expect(screen.getByText(/diesem vorhaben sind noch keine karten zugeordnet/i)).toBeInTheDocument()
    expect(screen.queryByRole('tree')).toBeNull()
  })

  it('rendert die übergebenen Zeilen ohne jeden Netzwerkaufruf', async () => {
    // Seit #644 ist die Komponente reine Darstellung. Gezaehlt wird deshalb "nie" und nicht mehr
    // "genau ein Lesezugriff": Auch das Bedienen loest keinen Abruf aus.
    const user = userEvent.setup()
    await zeigeBaum(kette)

    await fokusAuf(zeilen()[0])
    await user.keyboard('{ArrowLeft}{ArrowRight}{ArrowDown}{Enter}')

    expect(zeilen()).not.toHaveLength(0)
    expect(epicTree).not.toHaveBeenCalled()
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

    await fokusAuf(zeilen()[0])
    await user.keyboard('{Escape}')

    expect(zeilen()).toHaveLength(3)
    expect(zeilen()[0]).toHaveFocus()
    expect(onOpenCard).not.toHaveBeenCalled()
  })

})
