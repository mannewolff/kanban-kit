import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import type { Kachel as KachelDaten } from '../../lib/leitstand'
import { cssRegel } from '../../test/cssRegel'
import { KUPFER, KUPFER_SCHIMMER, LED_RING, MELDER, PLATTE_HOCH, RAND, TEXT_SCHWACH, theme } from '../../theme'
import {
  DeltaMarke,
  FilterTaste,
  Fuellschiene,
  Funke,
  Instrument,
  Kachel,
  KachelFuss,
  KachelWert,
  KlassenMarke,
  LeerSatz,
  Led,
  PaketDauer,
  Platte,
  Projektname,
  Taste,
  ZEILE_HOVER_SX,
} from './LeitstandBausteine'

// Verhaltenstests der Leitstand-Bausteine (Issue #1281): Die Bausteine waren über die Seiten-Tests
// abgedeckt, aber die Mutationsprüfung des Ausschnitts `bausteine-leitstand` fährt nur die Tests
// unter `leitstand/` — ohne eigene Tests blieb jeder Mutant dort ungeprüft.

const zeige = (knoten: ReactNode) => render(<ThemeProvider theme={theme}>{knoten}</ThemeProvider>)

describe('Led', () => {
  it('ruht ohne Angabe: eine Lampe, rund, in der Melderfarbe', () => {
    zeige(<Led melder="gruen" />)
    const led = screen.getByTestId('led-gruen')
    expect(led).toHaveAttribute('data-puls', 'aus')
    const regel = cssRegel(led)
    expect(regel).toContain('border-radius: 50%')
    expect(regel).toContain('flex: none')
    expect(regel).toContain(`box-shadow: ${LED_RING},0 0 8px -1px currentColor`)
  })

  it('gibt den Lampen des Blinkers Form und Leuchtschleier', () => {
    zeige(<Led melder="gruen" pulsiert />)
    const regel = cssRegel(screen.getAllByTestId('blinker-lampe')[0])
    expect(regel).toContain('border-radius: 50%')
    expect(regel).toContain('flex: none')
    expect(regel).toContain(`box-shadow: ${LED_RING},0 0 8px -1px currentColor`)
    expect(regel).toMatch(/animation: [\w-]+ 1s step-end infinite/)
  })

  it('schlägt im Blinken zwischen heller Phase und Melderfarbe um', () => {
    zeige(<Led melder="gruen" pulsiert />)
    const name = /animation: ([\w-]+) 1s/.exec(cssRegel(screen.getAllByTestId('blinker-lampe')[0]))![1]
    const takt = [...document.styleSheets]
      .flatMap((blatt) => [...blatt.cssRules])
      .find((r) => r.cssText.startsWith(`@keyframes ${name}`))
    expect(takt?.cssText).toContain('background-color: var(--blinker-hell)')
    expect(takt?.cssText).toContain('background-color: var(--blinker-an)')
  })
})

describe('Platte', () => {
  it('benennt den Abschnitt nach seinem Titel und zeigt LED und Inhalt', () => {
    zeige(
      <Platte titel="Läufe" led={<span data-testid="platte-led" />}>
        <p>Inhalt</p>
      </Platte>,
    )
    expect(screen.getByRole('region', { name: 'Läufe' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Läufe' })).toBeInTheDocument()
    expect(screen.getByTestId('platte-led')).toBeInTheDocument()
    expect(screen.getByText('Inhalt')).toBeInTheDocument()
  })

  it('zeigt Notiz und Werkzeug nur, wenn sie übergeben sind', () => {
    const { unmount } = zeige(
      <Platte titel="Läufe" notiz="letzte 7 Tage" werkzeug={<button type="button">Filter</button>}>
        <p>Inhalt</p>
      </Platte>,
    )
    expect(screen.getByText('letzte 7 Tage')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Filter' })).toBeInTheDocument()
    unmount()

    zeige(
      <Platte titel="Läufe">
        <p>Inhalt</p>
      </Platte>,
    )
    expect(screen.queryByText('letzte 7 Tage')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    // Der Kopf trägt nur die Überschrift: kein leerer Notiz- (span) oder Werkzeugplatz (div).
    const platte = screen.getByRole('region', { name: 'Läufe' })
    expect(platte.innerHTML).not.toContain('<span')
    expect(platte.innerHTML.match(/<div/g)).toHaveLength(1)
  })
})

describe('FilterTaste', () => {
  it('meldet ihren Zustand, trägt den vorgelesenen Namen und löst beim Klick aus', () => {
    const onClick = vi.fn()
    zeige(
      <FilterTaste gewaehlt ariaLabel="10 Läufe zeigen" onClick={onClick}>
        10
      </FilterTaste>,
    )
    const taste = screen.getByRole('button', { name: '10 Läufe zeigen' })
    expect(taste).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(taste)
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('ist ungewählt nicht gedrückt und heißt ohne Beschriftung nach ihrer Aufschrift', () => {
    zeige(
      <FilterTaste gewaehlt={false} onClick={() => undefined}>
        alle
      </FilterTaste>,
    )
    expect(screen.getByRole('button', { name: 'alle' })).toHaveAttribute('aria-pressed', 'false')
  })
})

describe('DeltaMarke', () => {
  it('färbt gut grün, schlecht zinnober und neutral gedämpft, mit passendem Rand', () => {
    zeige(
      <>
        <DeltaMarke art="gut">+3</DeltaMarke>
        <DeltaMarke art="schlecht">−2</DeltaMarke>
        <DeltaMarke art="neutral">±0</DeltaMarke>
      </>,
    )
    const gut = cssRegel(screen.getByTestId('delta-gut'))
    const schlecht = cssRegel(screen.getByTestId('delta-schlecht'))
    const neutral = cssRegel(screen.getByTestId('delta-neutral'))

    expect(gut).toContain(`color: ${MELDER.gruen}`)
    expect(gut).toContain(`border: 1px solid color-mix(in srgb, ${MELDER.gruen} 32%, ${RAND})`)
    expect(schlecht).toContain(`color: ${MELDER.zinnob}`)
    expect(schlecht).toContain(`border: 1px solid color-mix(in srgb, ${MELDER.zinnob} 32%, ${RAND})`)
    expect(neutral).not.toContain('color-mix')
    expect(neutral).toContain(`border: 1px solid ${RAND}`)
    expect(screen.getByTestId('delta-gut')).toHaveTextContent('+3')
  })
})

describe('Funke', () => {
  it('zeichnet Linie, Fläche und Endpunkt aus den Werten', () => {
    zeige(<Funke werte={[1, 3, 2]} melder="gruen" />)
    const svg = screen.getByTestId('funke')
    const inhalt = svg.innerHTML
    expect(inhalt).toContain('points="2,30 62,4 122,17"')
    expect(inhalt).toContain('d="M2 30 L62 4 L122 17 L122 34 L2 34 Z"')
    const verlaufId = /<linearGradient id="([^"]+)"/.exec(inhalt)![1]
    expect(inhalt).toContain(`fill="url(#${verlaufId})"`)
    expect(inhalt).toMatch(/<circle cx="122" cy="17"/)
    expect(cssRegel(svg)).toContain(`color: ${MELDER.gruen}`)
  })

  it('nimmt für kupfer die Leitfarbe', () => {
    zeige(<Funke werte={[1, 2]} melder="kupfer" />)
    expect(cssRegel(screen.getByTestId('funke'))).toContain(`color: ${KUPFER}`)
  })
})

describe('KachelWert, KachelFuss und Kachel', () => {
  it('zeigt ohne Wert den Leerstrich und als Grund „keine Datenbasis"', () => {
    zeige(<KachelWert wert={null} einheit="Karten" />)
    expect(screen.getByTestId('kachel-wert')).toHaveTextContent('—')
    expect(screen.getByText('keine Datenbasis')).toBeInTheDocument()
    expect(screen.queryByText('Karten')).not.toBeInTheDocument()
  })

  it('zeigt einen Wert mit seiner Einheit', () => {
    zeige(<KachelWert wert="12" einheit="Karten" />)
    expect(screen.getByTestId('kachel-wert')).toHaveTextContent('12')
    expect(screen.getByText('Karten')).toBeInTheDocument()
  })

  it('hält im Fuß ohne Delta den Platz links frei und zeigt die Basis', () => {
    const { container } = zeige(<KachelFuss basis="12 Wochen" />)
    expect(container.innerHTML).toMatch(/^<div[^>]*><span><\/span><span[^>]*>12 Wochen<\/span><\/div>$/)
  })

  it('zeigt im Fuß ein übergebenes Delta statt des Platzhalters', () => {
    const { container } = zeige(<KachelFuss delta={<b>+1</b>} basis="Vorwoche" />)
    expect(container.innerHTML).toMatch(/^<div[^>]*><b>\+1<\/b><span/)
  })

  const daten = (teil: Partial<KachelDaten>): KachelDaten => ({
    wert: '7',
    einheit: 'Karten',
    basis: 'letzte Woche',
    verlauf: null,
    delta: null,
    ...teil,
  })

  it('setzt eine Kachel aus Titel, Wert, Verlauf, Delta und Basis zusammen', () => {
    zeige(
      <Kachel titel="Durchsatz" melder="gruen" daten={daten({ verlauf: [1, 2], delta: { text: '+2', art: 'gut' } })} />,
    )
    const kachel = screen.getByRole('article', { name: 'Durchsatz' })
    expect(kachel).toHaveTextContent('Durchsatz')
    expect(screen.getByTestId('kachel-wert')).toHaveTextContent('7')
    expect(screen.getByTestId('funke')).toBeInTheDocument()
    expect(screen.getByTestId('delta-gut')).toHaveTextContent('+2')
    expect(screen.getByText('letzte Woche')).toBeInTheDocument()
  })

  it('lässt ohne Verlauf die Sparkline und ohne Delta die Marke weg', () => {
    zeige(<Kachel titel="Durchsatz" melder="gruen" daten={daten({})} />)
    expect(screen.queryByTestId('funke')).not.toBeInTheDocument()
    expect(screen.queryByTestId(/^delta-/)).not.toBeInTheDocument()
  })
})

describe('Fuellschiene', () => {
  it('füllt die Schiene zur angegebenen Breite', () => {
    zeige(<Fuellschiene breite={40} farbe="red" />)
    expect(cssRegel(screen.getByTestId('fuellung-40'))).toContain('width: 40%')
  })
})

describe('Instrument', () => {
  it('zeigt nicht Gemessenes als Strich mit vorgelesenem Grund, gedämpft', () => {
    zeige(<Instrument titel="Kosten" teile={null} testId="kosten" />)
    const wert = screen.getByTestId('kosten-wert')
    expect(wert).toHaveTextContent('—nicht gemessen')
    expect(screen.getByText('nicht gemessen')).toBeInTheDocument()
    expect(cssRegel(wert)).toContain(`color: ${TEXT_SCHWACH}`)
    expect(screen.queryByTestId('kosten-zusatz')).not.toBeInTheDocument()
  })

  it('nimmt einen eigenen Leertext', () => {
    zeige(<Instrument titel="Kosten" teile={null} leerText="kein Lauf" testId="kosten" />)
    expect(screen.getByText('kein Lauf')).toBeInTheDocument()
  })

  it('reiht mehrere Teile mit Leerzeichen und kleiner Einheit, in Textfarbe', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    zeige(
      <Instrument
        titel="Pakete"
        teile={[
          { wert: '5', einheit: 'grün' },
          { wert: '1', einheit: 'rot' },
        ]}
        testId="pakete"
      />,
    )
    const wert = screen.getByTestId('pakete-wert')
    expect(wert.textContent).toBe('5 grün 1 rot')
    // Jeder Teil trägt einen eigenen Schlüssel; gleiche Schlüssel meldete React als Fehler.
    expect(console.error).not.toHaveBeenCalled()
    vi.mocked(console.error).mockRestore()
    expect(cssRegel(wert)).toContain('color: var(--mb-palette-text-primary)')
    const rahmen = cssRegel(screen.getByTestId('pakete'))
    expect(rahmen).toContain(`border: 1px solid ${RAND}`)
    expect(rahmen).not.toContain('color-mix(in srgb, ' + KUPFER)
  })

  it('fasst ein heißes Instrument in Kupfer und zeigt den Zusatz', () => {
    zeige(
      <Instrument titel="Kosten" teile={[{ wert: '3,20', einheit: 'USD' }]} heiss zusatz="davon 1,10 Cache" testId="kosten" />,
    )
    expect(cssRegel(screen.getByTestId('kosten-wert'))).toContain(`color: ${KUPFER}`)
    expect(cssRegel(screen.getByTestId('kosten'))).toContain(`border: 1px solid color-mix(in srgb, ${KUPFER} 40%, ${RAND})`)
    expect(screen.getByTestId('kosten-zusatz')).toHaveTextContent('davon 1,10 Cache')
  })

  it('zeigt einen leeren Zusatz, weil er angegeben ist', () => {
    zeige(<Instrument titel="Kosten" teile={[{ wert: '1', einheit: 'USD' }]} zusatz="" testId="kosten" />)
    expect(screen.getByTestId('kosten-zusatz')).toBeEmptyDOMElement()
  })
})

describe('kleine Bausteine', () => {
  it('KlassenMarke steht in der Melderfarbe', () => {
    zeige(<KlassenMarke melder="zinnob">timeout</KlassenMarke>)
    expect(cssRegel(screen.getByText('timeout'))).toContain(`color: ${MELDER.zinnob}`)
  })

  it('LeerSatz trägt den Satz und die Kennung', () => {
    zeige(<LeerSatz testId="leer">Nichts läuft.</LeerSatz>)
    expect(screen.getByTestId('leer')).toHaveTextContent('Nichts läuft.')
  })

  it('Projektname zeigt den Namen', () => {
    zeige(<Projektname name="kanban-kit" />)
    expect(screen.getByText('kanban-kit')).toBeInTheDocument()
  })

  it('Taste trägt ihren Namen und löst beim Klick aus', () => {
    const onClick = vi.fn()
    zeige(
      <Taste ariaLabel="Lauf 12 öffnen" onClick={onClick}>
        öffnen
      </Taste>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Lauf 12 öffnen' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('ZEILE_HOVER_SX mischt die helle Platte mit dem Kupferschimmer', () => {
    expect(ZEILE_HOVER_SX).toBe(`color-mix(in srgb, ${PLATTE_HOCH} 75%, ${KUPFER_SCHIMMER})`)
  })
})

describe('PaketDauer', () => {
  it('zeigt Wert und Einheit und schreibt die Dauer aus', () => {
    zeige(<PaketDauer ms={125_000} testId="dauer" />)
    const dauer = screen.getByTestId('dauer')
    expect(dauer.textContent).toBe('02:05 min')
    expect(dauer).toHaveAttribute('aria-label', 'Dauer 2 min 5 s')
    expect(dauer).toHaveAttribute('title', 'Dauer 2 min 5 s')
  })

  it('zeigt ohne Messung nur den Strich, ohne Einheitenplatz', () => {
    zeige(<PaketDauer ms={null} testId="dauer" />)
    const dauer = screen.getByTestId('dauer')
    expect(dauer.textContent).toBe('—')
    expect(dauer.innerHTML).toBe('—')
  })
})
