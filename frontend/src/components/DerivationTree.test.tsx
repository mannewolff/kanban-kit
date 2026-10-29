import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DerivationTree, hakenArt } from './DerivationTree'
import type { DerivationNode } from '../api/cards'
import type { KitNamenZustand } from '../api/kitNamen'
import { KIT_NAMEN } from '../test/kitNamen'
import { MELDER } from '../theme'
import { renderMitSprache } from '../i18n/renderMitSprache'

/** Das Namen-Register des Kits (Issue #1596): ohne Angabe geladen, ein Test kann es umstellen. */
const kitNamen = vi.hoisted(() => ({ zustand: null as KitNamenZustand | null }))
vi.mock('../api/kitNamen', async (original) => ({
  ...(await original<typeof import('../api/kitNamen')>()),
  useKitNamen: (): KitNamenZustand => kitNamen.zustand ?? { status: 'fertig', daten: KIT_NAMEN },
}))

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
    inReview: false,
    blocked: false,
    dependencies: [],
    externalDependencies: [],
    externalOrigin: false,
    broken: false,
    labels: [],
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
  kitNamen.zustand = null
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

  it('macht eine erledigte Zeile am Haken erkennbar, der zugänglich „erledigt" heißt (Issue #985)', async () => {
    await zeigeBaum([node({ number: 1 }), node({ number: 2, depth: 1, done: true })])

    const haken = screen.getByRole('img', { name: 'erledigt' })
    expect(zeilen()[1]).toContainElement(haken)
    expect(haken).toHaveTextContent('✓')
  })

  it('zeigt eine Zeile in review mit gelbem Haken „in review", eine erledigte mit grünem (Issue #1608)', async () => {
    await zeigeBaum([
      node({ number: 1, done: true }),
      node({ number: 2, depth: 1, inReview: true }),
      node({ number: 3, depth: 1 }),
    ])

    const erledigt = screen.getByRole('img', { name: 'erledigt' })
    const inReview = screen.getByRole('img', { name: 'in review' })
    expect(zeilen()[0]).toContainElement(erledigt)
    expect(zeilen()[1]).toContainElement(inReview)
    expect(inReview).toHaveTextContent('✓')
    expect(getComputedStyle(inReview).color).toBe(MELDER.bernst)
    expect(getComputedStyle(erledigt).color).toBe(MELDER.gruen)
    expect(screen.getAllByTestId('hakenspalte')[2]).toHaveTextContent('')
  })

  it('zeigt an einer erledigten Zeile den grünen Haken, auch wenn sie in review markiert wäre', async () => {
    await zeigeBaum([node({ number: 1, done: true, inReview: true })])

    const haken = screen.getByRole('img', { name: 'erledigt' })
    expect(getComputedStyle(haken).color).toBe(MELDER.gruen)
    expect(screen.queryByRole('img', { name: 'in review' })).toBeNull()
  })

  it('braucht für eine Karte in review keine Kit-Namen', async () => {
    // Wie erledigt: Den Zustand traegt der Status, nicht der Titel.
    kitNamen.zustand = { status: 'laden' }
    await zeigeBaum([node({ number: 1, inReview: true })])

    expect(screen.getByRole('img', { name: 'in review' })).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: 'Kit-Namen werden geladen' })).toBeNull()
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
    const onOpenCard = await zeigeBaum(mitAbhaengigkeit)

    await fokusAuf(marke(2))
    await user.keyboard('{Enter}')

    expect(zeilen()[1]).toHaveFocus()
    // Enter auf einem <button> loest zusaetzlich zum Keydown ein synthetisches click aus — ohne
    // stopPropagation im onClick der Sprungmarke oeffnete das zugleich die eigene Karte der Zeile.
    expect(onOpenCard).not.toHaveBeenCalled()
  })

  it('springt auch mit der Leertaste', async () => {
    const user = userEvent.setup()
    const onOpenCard = await zeigeBaum(mitAbhaengigkeit)

    await fokusAuf(marke(2))
    await user.keyboard('[Space]')

    expect(zeilen()[1]).toHaveFocus()
    expect(onOpenCard).not.toHaveBeenCalled()
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
    const onOpenCard = await zeigeBaum([
      node({ number: 1 }),
      node({ number: 2, depth: 1, dependencies: [77] }),
    ])

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
    expect(onOpenCard).not.toHaveBeenCalled()
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

  it('öffnet mit einem Klick auf die Kartennummer die Karte der Zeile (Issue #739)', async () => {
    const user = userEvent.setup()
    const onOpenCard = await zeigeBaum(kette)

    await user.click(screen.getByText('#2'))

    expect(onOpenCard).toHaveBeenCalledTimes(1)
    expect(onOpenCard).toHaveBeenCalledWith(2)
  })

  it('öffnet mit einem Klick auf den Titel die Karte der Zeile (Issue #739)', async () => {
    const user = userEvent.setup()
    const onOpenCard = await zeigeBaum(kette)

    await user.click(screen.getByText('Karte 2'))

    expect(onOpenCard).toHaveBeenCalledTimes(1)
    expect(onOpenCard).toHaveBeenCalledWith(2)
  })

  it('öffnet mit einem Klick auf die Sprungmarke nicht zugleich die eigene Karte der Zeile (Issue #739)', async () => {
    const user = userEvent.setup()
    const onOpenCard = await zeigeBaum(mitAbhaengigkeit)

    await user.click(marke(2))

    expect(zeilen()[1]).toHaveFocus()
    expect(onOpenCard).not.toHaveBeenCalled()
  })

  it('zeigt den Zeiger-Cursor als Hinweis auf die Klickbarkeit (Issue #739)', async () => {
    await zeigeBaum(kette)

    expect(zeilen()[0]).toHaveStyle({ cursor: 'pointer' })
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

  // --- Gezaehlte Label-Marken je Zeile (Issue #661) -------------------------

  it('zeigt die gezählten Labels einer Zeile als Marken', async () => {
    await zeigeBaum([node({ number: 1, labels: [{ name: 'bereit', color: '#00ff00' }] })])

    expect(screen.getByText('bereit')).toBeInTheDocument()
  })

  /**
   * Die Unterscheidung darf nicht allein an der Farbe haengen: Der Name steht als Text im DOM,
   * die Farbe ist Zusatz.
   */
  it('nennt den Label-Namen als Text, nicht nur als Fläche', async () => {
    await zeigeBaum([
      node({
        number: 1,
        labels: [
          { name: 'bereit', color: '#00ff00' },
          { name: 'blockiert von aussen', color: '#ff0000' },
        ],
      }),
    ])

    expect(screen.getByText('bereit')).toBeInTheDocument()
    expect(screen.getByText('blockiert von aussen')).toBeInTheDocument()
  })

  it('zeigt an einer Zeile ohne gezählte Labels keine Marke', async () => {
    await zeigeBaum([node({ number: 1, labels: [] }), node({ number: 2, labels: [{ name: 'bereit', color: '#0f0' }] })])

    const zeilen = screen.getAllByRole('treeitem')
    expect(zeilen[0]).not.toHaveTextContent('bereit')
    expect(zeilen[1]).toHaveTextContent('bereit')
  })

  /**
   * Der Baum ist ein Roving Tabindex mit genau einem Tab-Stopp. Eine Label-Marke ist reine
   * Anzeige — bekaeme sie Rolle oder `tabIndex`, staende ein zweites, konkurrierendes
   * Fokusmodell daneben.
   */
  it('macht die Label-Marke nicht bedienbar', async () => {
    await zeigeBaum([node({ number: 1, labels: [{ name: 'bereit', color: '#0f0' }] })])

    const marke = screen.getByText('bereit')
    expect(marke).not.toHaveAttribute('tabindex')
    expect(marke).not.toHaveAttribute('role')
  })

  it('zeigt die Label-Chips nach der Kartennummer und vor dem Titel (Issue #740)', async () => {
    await zeigeBaum([
      node({
        number: 1,
        title: 'Ein langer Titel, der sonst ans Zeilenende ablenkt',
        labels: [
          { name: 'eins', color: '#0f0' },
          { name: 'zwei', color: '#00f' },
        ],
      }),
    ])

    const nummer = screen.getByText('#1')
    const erstesLabel = screen.getByText('eins')
    const zweitesLabel = screen.getByText('zwei')
    const titel = screen.getByText('Ein langer Titel, der sonst ans Zeilenende ablenkt')

    expect(nummer.compareDocumentPosition(erstesLabel) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(erstesLabel.compareDocumentPosition(zweitesLabel) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(zweitesLabel.compareDocumentPosition(titel) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  // --- Hakenspalte ganz links (Issue #985) ---------------------------------
  //
  // Die Regel liegt als reine Funktion neben der Komponente: Ob ein Haken steht, haengt am Baum
  // und nicht am Rendern. Die Faelle darunter pruefen deshalb `hakenArt` direkt; die
  // Render-Tests danach belegen nur noch, dass der Haken an der richtigen Stelle landet.

  /** Zeilen aus Titeln bauen — die Regel liest den Titel, nicht die Nummer. */
  const baum = (...titel: (readonly [number, string, Partial<DerivationNode>?])[]) =>
    titel.map(([depth, title, rest], i) => node({ number: i + 1, depth, title, ...rest }))

  it('setzt an einer erledigten Karte den Haken „erledigt"', () => {
    const rows = baum([0, '[Plan] Herkunftsbaum'], [1, 'Hakenspalte bauen', { done: true }])

    expect(hakenArt(rows, 1, KIT_NAMEN)).toBe('erledigt')
  })

  it('setzt an einer Karte in review den Haken „inReview" (Issue #1608)', () => {
    const rows = baum([0, '[Plan] Herkunftsbaum'], [1, 'Hakenspalte bauen', { inReview: true }])

    expect(hakenArt(rows, 1, KIT_NAMEN)).toBe('inReview')
  })

  it('gibt Done Vorrang vor in review', () => {
    const rows = baum([0, '[Plan] Herkunftsbaum'], [1, 'Hakenspalte bauen', { done: true, inReview: true }])

    expect(hakenArt(rows, 1, KIT_NAMEN)).toBe('erledigt')
  })

  it('gibt in review Vorrang vor abgearbeitet', () => {
    const rows = baum([0, '[Plan] Herkunftsbaum', { inReview: true }], [1, 'Hakenspalte bauen'])

    expect(hakenArt(rows, 0, KIT_NAMEN)).toBe('inReview')
  })

  it('setzt an einer offenen Karte keinen Haken', () => {
    const rows = baum([0, '[Plan] Herkunftsbaum'], [1, 'Hakenspalte bauen'])

    expect(hakenArt(rows, 1, KIT_NAMEN)).toBeNull()
  })

  it('setzt an einem [Fachlich] mit [Plan] darunter den Haken „abgearbeitet"', () => {
    const rows = baum([0, '[Fachlich] Stand links ablesbar'], [1, '[Plan] Herkunftsbaum'])

    expect(hakenArt(rows, 0, KIT_NAMEN)).toBe('abgearbeitet')
  })

  it('setzt an einem [Fachlich] ohne Kind keinen Haken', () => {
    const rows = baum([0, '[Fachlich] Stand links ablesbar'])

    expect(hakenArt(rows, 0, KIT_NAMEN)).toBeNull()
  })

  it('setzt an einem [Fachlich] mit nur Nicht-Plan-Kind keinen Haken', () => {
    // Eine Karte direkt unter dem Fachplan ist noch kein abgearbeiteter Fachplan — erst ein Plan
    // darunter belegt, dass der fachliche Weg in einen technischen ueberfuehrt wurde.
    const rows = baum([0, '[Fachlich] Stand links ablesbar'], [1, 'Irgendeine Karte'])

    expect(hakenArt(rows, 0, KIT_NAMEN)).toBeNull()
  })

  /** Englische Titel-Präfixe wirken wie ihr deutsches Gegenstück (Plan #1582, E2). */
  it('setzt an einem [Requirement] mit [Plan] darunter den Haken „abgearbeitet"', () => {
    const rows = baum([0, '[Requirement] Status readable on the left'], [1, '[Plan] Derivation tree'])

    expect(hakenArt(rows, 0, KIT_NAMEN)).toBe('abgearbeitet')
  })

  it('setzt an einem [Requirement] ohne Kind keinen Haken', () => {
    const rows = baum([0, '[Requirement] Status readable on the left'])

    expect(hakenArt(rows, 0, KIT_NAMEN)).toBeNull()
  })

  it('setzt an einem [Plan] mit Arbeitspaket darunter den Haken „abgearbeitet"', () => {
    const rows = baum([0, '[Plan] Herkunftsbaum'], [1, '[Task] Hakenspalte bauen'])

    expect(hakenArt(rows, 0, KIT_NAMEN)).toBe('abgearbeitet')
  })

  it('setzt an einem [Plan] ohne Arbeitspaket keinen Haken', () => {
    const rows = baum([0, '[Fachlich] Stand links ablesbar'], [1, '[Plan] Herkunftsbaum'])

    expect(hakenArt(rows, 1, KIT_NAMEN)).toBeNull()
  })

  it('zählt nur direkte Kinder, keine tieferen Nachfahren', () => {
    // Plan und Arbeitspakete haengen direkt an ihrem Vorgaenger; ein Plan zwei Ebenen tiefer
    // gehoert zu einer anderen Zeile und darf den Fachplan nicht als abgearbeitet ausweisen.
    const rows = baum(
      [0, '[Fachlich] Stand links ablesbar'],
      [1, 'Irgendeine Karte'],
      [2, '[Plan] Herkunftsbaum'],
    )

    expect(hakenArt(rows, 0, KIT_NAMEN)).toBeNull()
  })

  it('zählt ein Kind auch dann, wenn davor ein Enkel derselben Zeile steht', () => {
    // In Praeorder liegen die Nachfahren eines ersten Kindes zwischen erstem und zweitem Kind —
    // die Suche darf dort nicht abbrechen, sonst bliebe das zweite Kind ungezaehlt.
    const rows = baum(
      [0, '[Fachlich] Stand links ablesbar'],
      [1, 'Irgendeine Karte'],
      [2, 'Enkel'],
      [1, '[Plan] Herkunftsbaum'],
    )

    expect(hakenArt(rows, 0, KIT_NAMEN)).toBe('abgearbeitet')
  })

  it('zeigt den Haken eines abgearbeiteten Plans zugänglich als „abgearbeitet"', async () => {
    await zeigeBaum(baum([0, '[Plan] Herkunftsbaum'], [1, '[Task] Hakenspalte bauen']))

    const haken = screen.getByRole('img', { name: 'abgearbeitet' })
    expect(zeilen()[0]).toContainElement(haken)
  })

  it('zeigt die graue Marke „erledigt" rechts nicht mehr', async () => {
    await zeigeBaum([node({ number: 1, done: true })])

    // Nur noch der Haken traegt die Aussage — als zugaenglicher Name, nicht als sichtbarer Text.
    expect(screen.queryByText('erledigt')).toBeNull()
    expect(screen.getByRole('img', { name: 'erledigt' })).toBeInTheDocument()
  })

  it('stellt den Haken vor die Kartennummer', async () => {
    await zeigeBaum([node({ number: 1, done: true })])

    const haken = screen.getByRole('img', { name: 'erledigt' })
    const nummer = screen.getByText('#1')
    expect(haken.compareDocumentPosition(nummer) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('hält die Hakenspalte über alle Tiefen hinweg bündig und gleich breit', async () => {
    await zeigeBaum([node({ number: 1, done: true }), node({ number: 2, depth: 1 })])

    // Die Einrueckung beginnt erst hinter der Spalte: Beide Zeilen haben denselben linken
    // Innenabstand, und beide fuehren als erstes Kind eine Spalte gleicher Breite — die der
    // zweiten Zeile leer.
    const [ersteZeile, zweiteZeile] = zeilen()
    expect(zweiteZeile).toHaveStyle({ paddingLeft: getComputedStyle(ersteZeile).paddingLeft })
    const spalten = screen.getAllByTestId('hakenspalte')
    expect(spalten).toHaveLength(2)
    expect(spalten[0]).toHaveTextContent('✓')
    expect(spalten[1]).toHaveTextContent('')
    expect(spalten[1]).toHaveStyle({ width: getComputedStyle(spalten[0]).width })
    expect(getComputedStyle(spalten[1]).width).not.toBe('')
  })

  // --- Kit-Namen laden oder fehlen (Plan #1582, E10) -------------------------

  it('zeigt während des Ladens der Kit-Namen einen Ladehinweis in der Hakenspalte', async () => {
    kitNamen.zustand = { status: 'laden' }
    await zeigeBaum(baum([0, '[Requirement] Anforderung'], [1, '[Plan] Weg']))

    const hinweise = screen.getAllByRole('img', { name: 'Kit-Namen werden geladen' })
    expect(hinweise).toHaveLength(2)
    expect(zeilen()[0]).toContainElement(hinweise[0])
    expect(screen.queryByRole('img', { name: 'abgearbeitet' })).toBeNull()
  })

  it('zeigt „Kit-Namen nicht ladbar" in der Hakenspalte, wenn das Laden scheitert', async () => {
    kitNamen.zustand = { status: 'fehler', fehler: new Error('weg') }
    await zeigeBaum(baum([0, '[Fachlich] Anforderung'], [1, '[Plan] Weg']))

    const hinweise = screen.getAllByRole('img', { name: 'Kit-Namen nicht ladbar' })
    expect(hinweise).toHaveLength(2)
    expect(zeilen()[1]).toContainElement(hinweise[1])
    expect(screen.queryByRole('img', { name: 'abgearbeitet' })).toBeNull()
  })

  it('braucht für eine erledigte Karte keine Kit-Namen', async () => {
    // Erledigt liest die Spalte, nicht den Titel — dort fehlt kein Präfix.
    kitNamen.zustand = { status: 'fehler', fehler: new Error('weg') }
    await zeigeBaum([node({ number: 1, done: true })])

    expect(screen.getByRole('img', { name: 'erledigt' })).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: 'Kit-Namen nicht ladbar' })).toBeNull()
  })

  it('rückt die Zeileninhalte weiterhin nach der Tiefe ein', async () => {
    await zeigeBaum(kette)

    const abstand = (text: string) => getComputedStyle(screen.getByText(text)).marginLeft
    expect(abstand('#1')).not.toBe(abstand('#2'))
    expect(abstand('#2')).not.toBe(abstand('#3'))
  })
})

describe('DerivationTree: lange Titel (Plan #1980, E9)', () => {
  /** 90 Zeichen, nur Buchstaben und Ziffern — taugt auch als regulärer Ausdruck. */
  const langerTitel = 'Herkunftsbaum0'.repeat(6) + 'abcdef'
  const kurzerTitel = langerTitel.slice(0, 60) + '…'

  it('Szenario: Langer Titel', async () => {
    expect(langerTitel).toHaveLength(90)
    await zeigeBaum([node({ number: 1, title: langerTitel })])

    // Dann stehen dort die ersten 60 Zeichen, gefolgt von „…"
    expect(screen.getByText(kurzerTitel)).toBeInTheDocument()
    // Und beim Darüberfahren erscheint der volle Titel
    expect(screen.getByTitle(langerTitel)).toBeInTheDocument()
    expect(zeilen()[0]).toContainElement(screen.getByTitle(langerTitel))
  })

  it('verbirgt den gekürzten Text vor Screenreadern und liest den vollen Titel vor', async () => {
    await zeigeBaum([node({ number: 1, title: langerTitel })])

    expect(screen.getByText(kurzerTitel)).toHaveAttribute('aria-hidden', 'true')
    const voll = screen.getByText(langerTitel)
    expect(voll).not.toHaveAttribute('aria-hidden')
    expect(voll).toHaveStyle({ position: 'absolute', overflow: 'hidden' })
    expect(zeilen()[0]).toHaveAccessibleName(new RegExp(`${langerTitel}$`))
  })

  it('lässt einen Titel mit 60 Zeichen ungekürzt und ohne Doppel', async () => {
    const titel = langerTitel.slice(0, 60)
    await zeigeBaum([node({ number: 1, title: titel })])

    expect(screen.getByText(titel)).not.toHaveAttribute('aria-hidden')
    expect(screen.queryByText(/…/)).toBeNull()
    expect(screen.queryByTitle(titel)).toBeNull()
  })

  it('öffnet auch mit einem Klick auf den gekürzten Titel die Karte', async () => {
    const user = userEvent.setup()
    const onOpenCard = await zeigeBaum([node({ number: 7, title: langerTitel })])

    await user.click(screen.getByText(kurzerTitel))

    expect(onOpenCard).toHaveBeenCalledWith(7)
  })
})

describe('DerivationTree: aktuelle Karte (Plan #1980, E8)', () => {
  function zeigeMitAktueller(aktuelleNummer: number | undefined, onOpenCard = vi.fn()) {
    render(<DerivationTree rows={kette} onOpenCard={onOpenCard} aktuelleNummer={aktuelleNummer} />)
    return onOpenCard
  }

  it('kennzeichnet die Zeile der geöffneten Karte mit aria-current', () => {
    zeigeMitAktueller(2)

    const [erste, zweite, dritte] = zeilen()
    expect(zweite).toHaveAttribute('aria-current', 'true')
    expect(erste).not.toHaveAttribute('aria-current')
    expect(dritte).not.toHaveAttribute('aria-current')
  })

  it('hebt die Zeile der geöffneten Karte sichtbar hervor', () => {
    zeigeMitAktueller(2)

    const [erste, zweite] = zeilen()
    expect(getComputedStyle(zweite).backgroundColor).not.toBe(getComputedStyle(erste).backgroundColor)
    // Nicht nur Farbe (CLAUDE-design.md): Der Titel der aktuellen Zeile steht zusätzlich fett.
    expect(screen.getByText('Karte 2')).toHaveStyle({ fontWeight: 600 })
    expect(screen.getByText('Karte 1')).not.toHaveStyle({ fontWeight: 600 })
  })

  it('öffnet mit einem Klick auf die aktuelle Zeile nichts', async () => {
    const user = userEvent.setup()
    const onOpenCard = zeigeMitAktueller(2)

    await user.click(screen.getByText('Karte 2'))

    expect(onOpenCard).not.toHaveBeenCalled()
  })

  it('öffnet mit Enter auf der aktuellen Zeile nichts', async () => {
    const user = userEvent.setup()
    const onOpenCard = zeigeMitAktueller(2)

    await fokusAuf(zeilen()[1])
    await user.keyboard('{Enter}')

    expect(onOpenCard).not.toHaveBeenCalled()
  })

  it('öffnet die anderen Zeilen weiterhin per Klick und Enter', async () => {
    const user = userEvent.setup()
    const onOpenCard = zeigeMitAktueller(2)

    await user.click(screen.getByText('Karte 1'))
    await fokusAuf(zeilen()[2])
    await user.keyboard('{Enter}')

    expect(onOpenCard).toHaveBeenNthCalledWith(1, 1)
    expect(onOpenCard).toHaveBeenNthCalledWith(2, 3)
  })

  it('lässt auf der aktuellen Zeile die Pfeiltasten wirken', async () => {
    const user = userEvent.setup()
    zeigeMitAktueller(2)

    await fokusAuf(zeilen()[1])
    await user.keyboard('{ArrowDown}')

    expect(zeilen()[2]).toHaveFocus()
  })

  it('zeigt die aktuelle Zeile nicht als klickbar', () => {
    zeigeMitAktueller(2)

    expect(zeilen()[1]).toHaveStyle({ cursor: 'default' })
    expect(zeilen()[0]).toHaveStyle({ cursor: 'pointer' })
  })

  it('trägt ohne aktuelleNummer an keiner Zeile aria-current', () => {
    zeigeMitAktueller(undefined)

    for (const zeile of zeilen()) {
      expect(zeile).not.toHaveAttribute('aria-current')
    }
    expect(screen.getByText('Karte 2')).not.toHaveStyle({ fontWeight: 600 })
  })

  it('trägt bei einer Nummer außerhalb des Baums an keiner Zeile aria-current', () => {
    zeigeMitAktueller(99)

    for (const zeile of zeilen()) {
      expect(zeile).not.toHaveAttribute('aria-current')
    }
  })
})

describe('DerivationTree auf Englisch', () => {
  beforeEach(() => {
    kitNamen.zustand = null
  })

  it('meldet den leeren Baum auf Englisch', () => {
    renderMitSprache(<DerivationTree rows={[]} onOpenCard={vi.fn()} />, 'en')
    expect(screen.getByText('No cards are assigned to this epic yet.')).toBeInTheDocument()
  })

  it('beschriftet Baum, Marken und Haken auf Englisch', () => {
    renderMitSprache(
      <DerivationTree
        rows={[
          node({ number: 1, type: 'EPIC', done: true }),
          node({ number: 2, depth: 1, inReview: true, blocked: true, broken: true }),
          node({ number: 3, depth: 1, externalOrigin: true, derivedFrom: 40, dependencies: [2], externalDependencies: [50] }),
          node({ number: 4, title: '[Plan] Plan', depth: 1 }),
          node({ number: 5, depth: 2 }),
        ]}
        onOpenCard={vi.fn()}
      />,
      'en',
    )
    expect(screen.getByRole('tree', { name: 'Origin' })).toBeInTheDocument()
    expect(screen.getByText('Epic')).toBeInTheDocument()
    expect(screen.getByText('blocked')).toBeInTheDocument()
    expect(screen.getByText('Origin chain broken')).toBeInTheDocument()
    expect(screen.getByText('External origin: #40')).toBeInTheDocument()
    expect(screen.getByText('⇠ external #50')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Jump to card #2' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'done' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'in review' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'processed' })).toBeInTheDocument()
  })

  it('nennt Lade- und Fehlerzustand des Namen-Registers auf Englisch', () => {
    kitNamen.zustand = { status: 'laden' }
    const { unmount } = renderMitSprache(<DerivationTree rows={[node({ number: 1 })]} onOpenCard={vi.fn()} />, 'en')
    expect(screen.getByRole('img', { name: 'Loading kit names' })).toBeInTheDocument()
    unmount()

    kitNamen.zustand = { status: 'fehler', fehler: new Error('weg') }
    renderMitSprache(<DerivationTree rows={[node({ number: 1 })]} onOpenCard={vi.fn()} />, 'en')
    expect(screen.getByRole('img', { name: 'Kit names unavailable' })).toBeInTheDocument()
  })
})
