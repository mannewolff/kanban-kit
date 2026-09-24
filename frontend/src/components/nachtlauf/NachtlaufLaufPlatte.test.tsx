import { ThemeProvider } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { kontrast } from '../../lib/kontrast'
import { TEXT_MATT, theme } from '../../theme'
import { LaufMarke, NachtlaufLaufPlatte } from './NachtlaufLaufPlatte'

/**
 * Der Kopf einer Laufplatte (#1041). Bis dahin war der Aufklapp-Pfeil auf der Nachtlauf-Seite
 * nicht zu sehen, und klickbar war nur er selbst.
 *
 * <p><b>Warum die Größe des Pfeils geprüft wird und nicht nur seine Farbe.</b> Der Pfeil bekam
 * seine 12 px über die Systemeigenschaften `width`/`height` eines `Box component="svg"` — als
 * Zeichenkette `"12"`. MUI hat sie damit als Systemeigenschaft verbraucht (sie landeten **nicht**
 * als SVG-Attribut) und zu `width:12` ohne Einheit gemacht; jeder CSS-Parser verwirft die
 * Angabe. Der Pfeil stand damit ohne gültige Größe im Kopf. Die Farbe war nie das Problem — sie
 * löste sich auf, und der Test hält beides fest, damit keine der beiden Hälften zurückfällt.
 *
 * <p>jsdom löst `var()` nicht auf; Farben werden deshalb gegen den **Verweis** verglichen und die
 * Kontraste aus `theme.colorSchemes` gerechnet (Muster aus `NachtlaufFuss.test.tsx`).
 */

const hell = theme.colorSchemes.light!.palette
const dunkel = theme.colorSchemes.dark!.palette

/** Die Kopfzeile trägt den Verlauf von „Platte hoch" nach „Platte" — beide Enden sind ihre Fläche. */
const kopfflaechen = [
  ['hell', 'Platte hoch', hell.warte.platteHoch, hell.text.secondary],
  ['hell', 'Platte', hell.warte.platte, hell.text.secondary],
  ['dunkel', 'Platte hoch', dunkel.warte.platteHoch, dunkel.text.secondary],
  ['dunkel', 'Platte', dunkel.warte.platte, dunkel.text.secondary],
] as const

/**
 * Die Platte in einer Hülle, die `offen` selbst hält — die Komponente ist gesteuert, und „klappt
 * auf und wieder zu" ist ohne diesen Zustand nicht prüfbar. Der Zähler zeigt, wie oft umgeschaltet
 * wurde: Ein Klick auf den Pfeil darf nicht zusätzlich den Kopf auslösen.
 */
function zeige(offenAnfangs = false, abbruchGrund?: string) {
  const umschalten = vi.fn()

  function Huelle() {
    const [offen, setOffen] = useState(offenAnfangs)
    return (
      <NachtlaufLaufPlatte
        titel="Run #86 · 14. September, 22:05"
        zyklus="Schicht vom 14.09.2026 auf den 15.09.2026"
        meta="02:00 · 41 min · 7 bearbeitet"
        melder="gruen"
        abbruchGrund={abbruchGrund}
        offen={offen}
        onUmschalten={() => {
          umschalten()
          setOffen((vorher) => !vorher)
        }}
        testId="lauf-1"
      >
        <div>Vorgänge des Laufs</div>
      </NachtlaufLaufPlatte>
    )
  }

  render(
    <ThemeProvider theme={theme}>
      <Huelle />
    </ThemeProvider>,
  )
  return { umschalten }
}

const vorzeile = () => screen.getByTestId('nachtlauf-vorzeile')
const pfeil = () => screen.getByRole('button', { name: /Run #86 · 14\. September, 22:05 (auf|zu)klappen/ })
const kopf = () => screen.getByTestId('lauf-kopf')
const inhalt = () => screen.queryByText('Vorgänge des Laufs')
const grundzeile = () => screen.queryByTestId('nachtlauf-abbruchgrund')

/**
 * Ein Abbruchgrund, wie ihn der Runner meldet (Issue #1145): mehrzeilig, mit Pfaden, und länger als
 * die 120 Zeichen, auf die `kurzGrund` die Kopfmarke kürzt. Genau daran hängt AK 4 — hier steht er
 * **vollständig**.
 */
const LANGER_GRUND = [
  'Harter Stopp (dirty-tree) — der Working Tree trug nach der Runde unkommittete Reste, der Lauf bricht ab',
  'frontend/src/pages/NightRunPage.tsx',
  'frontend/src/components/nachtlauf/NachtlaufLaufPlatte.tsx',
  'src/main/java/org/mwolff/manban/card/NightRunService.java',
].join('\n')

afterEach(() => {
  vi.restoreAllMocks()
})

describe('NachtlaufLaufPlatte — der Pfeil ist zu sehen', () => {
  it('malt den Pfeil im Token „Text matt"', () => {
    zeige()

    expect(getComputedStyle(pfeil()).color).toBe(TEXT_MATT)
  })

  it.each(kopfflaechen)('hält %s auf %s mindestens 3:1', (_, __, flaeche, farbe) => {
    expect(kontrast(flaeche, farbe)).toBeGreaterThanOrEqual(3)
  })

  it('malt auch eine Marke im Token „Text matt"', () => {
    // Die Vorlage führt `.zustand` in `var(--text-matt)`; der Baustein nimmt seine Farben damit
    // vollständig aus den benannten Tokens und nicht mehr über den Palettenpfad.
    render(
      <ThemeProvider theme={theme}>
        <LaufMarke testId="lauf-stand">Ergebnisstand</LaufMarke>
      </ThemeProvider>,
    )

    expect(getComputedStyle(screen.getByTestId('lauf-stand')).color).toBe(TEXT_MATT)
  })

  it('gibt dem Pfeil eine Größe mit Einheit', () => {
    // Ohne Einheit verwirft der Browser die Angabe, und der Pfeil steht ohne Größe im Kopf —
    // genau der Befund aus #1041.
    zeige()

    const svg = screen.getByTestId('nachtlauf-pfeil')
    expect(getComputedStyle(svg).width).toBe('12px')
    expect(getComputedStyle(svg).height).toBe('12px')
  })
})

describe('NachtlaufLaufPlatte — Zyklus in der Vorzeile, Nummer im Titel (#1127)', () => {
  it('nennt in der Vorzeile allein den Zyklus — die Art steht seit #1128 als Marke', () => {
    zeige()

    expect(vorzeile().textContent).toBe('Schicht vom 14.09.2026 auf den 15.09.2026')
  })

  it('führt den Titel als Überschrift', () => {
    zeige()

    expect(screen.getByTestId('nachtlauf-ueberschrift')).toHaveTextContent('Run #86 · 14. September, 22:05')
  })
})

describe('NachtlaufLaufPlatte — die ganze Kopfzeile schaltet', () => {
  it('klappt bei einem Klick auf den Titel auf und beim nächsten wieder zu', async () => {
    const nutzer = userEvent.setup()
    const { umschalten } = zeige()
    expect(inhalt()).not.toBeInTheDocument()

    await nutzer.click(screen.getByTestId('nachtlauf-ueberschrift'))
    expect(inhalt()).toBeInTheDocument()

    await nutzer.click(screen.getByTestId('nachtlauf-ueberschrift'))
    expect(inhalt()).not.toBeInTheDocument()
    expect(umschalten).toHaveBeenCalledTimes(2)
  })

  it('schaltet auch über die Metazeile und die freie Fläche des Kopfes', async () => {
    const nutzer = userEvent.setup()
    const { umschalten } = zeige()

    await nutzer.click(screen.getByTestId('nachtlauf-meta'))
    await nutzer.click(kopf())

    expect(umschalten).toHaveBeenCalledTimes(2)
  })

  it('schaltet bei einem Klick auf den Pfeil genau einmal', async () => {
    // Der Pfeil liegt im Kopf: Ohne gestoppte Weitergabe schaltete sein Klick zweimal — also gar
    // nicht.
    const nutzer = userEvent.setup()
    const { umschalten } = zeige()

    await nutzer.click(pfeil())

    expect(umschalten).toHaveBeenCalledTimes(1)
    expect(inhalt()).toBeInTheDocument()
  })

  it('zeigt am Kopf den Zeiger einer Bedienfläche', () => {
    zeige()

    expect(getComputedStyle(kopf()).cursor).toBe('pointer')
  })

  it('schaltet nicht, wenn im Kopf Text ausgewählt ist', async () => {
    // Herkunft und Token-Namen in den Marken werden kopiert; der Klick, der die Auswahl beendet,
    // darf den Lauf nicht zuklappen.
    const nutzer = userEvent.setup()
    const { umschalten } = zeige()
    vi.spyOn(window, 'getSelection').mockReturnValue({
      toString: () => 'Ergebnisstand',
    } as unknown as Selection)

    await nutzer.click(screen.getByTestId('nachtlauf-ueberschrift'))

    expect(umschalten).not.toHaveBeenCalled()
    expect(inhalt()).not.toBeInTheDocument()
  })

  it('schaltet, wo der Browser gar keine Auswahl führt', async () => {
    // `getSelection()` darf `null` liefern (etwa in einem Dokument ohne Auswahlbereich). „Keine
    // Auswahl" ist kein Grund, die Kopfzeile stumm zu stellen.
    const nutzer = userEvent.setup()
    const { umschalten } = zeige()
    vi.spyOn(window, 'getSelection').mockReturnValue(null)

    await nutzer.click(screen.getByTestId('nachtlauf-ueberschrift'))

    expect(umschalten).toHaveBeenCalledTimes(1)
    expect(inhalt()).toBeInTheDocument()
  })
})

describe('NachtlaufLaufPlatte — der Abbruchgrund vollständig (#1145)', () => {
  it('zeigt den vollständigen Grund in der aufgeklappten Platte', () => {
    zeige(true, LANGER_GRUND)

    expect(grundzeile()).toHaveTextContent(
      'Harter Stopp (dirty-tree) — der Working Tree trug nach der Runde unkommittete Reste, der Lauf bricht ab',
    )
    // Jeder Pfad steht da — der Grund wird hier nicht gekürzt (AK 4).
    expect(grundzeile()!.textContent).toBe(LANGER_GRUND)
  })

  it('lässt den Grund umbrechen statt ihn wie eine Marke einzeilig zu führen', () => {
    // Der Fund aus WICHTIG 5 der Plan-Prüfung: In einem `nowrap`-Element ragte ein Grund bis 4000
    // Zeichen aus der Platte heraus. AK 4 verlangt ihn vollständig **und** lesbar.
    zeige(true, LANGER_GRUND)

    expect(getComputedStyle(grundzeile()!).whiteSpace).toBe('pre-wrap')
  })

  it('hält die Marke weiterhin einzeilig', () => {
    // `LaufMarke` bleibt unverändert: Die Kopfmarke trägt den **gekürzten** Grund und darf dort
    // nicht umbrechen.
    render(
      <ThemeProvider theme={theme}>
        <LaufMarke testId="lauf-zustand">Harter Stopp (dirty-tree)</LaufMarke>
      </ThemeProvider>,
    )

    expect(getComputedStyle(screen.getByTestId('lauf-zustand')).whiteSpace).toBe('nowrap')
  })

  it('zeigt die Zeile nicht am zugeklappten Lauf', () => {
    zeige(false, LANGER_GRUND)

    expect(grundzeile()).not.toBeInTheDocument()
  })

  it('zeigt ohne Abbruchgrund gar keine Zeile', () => {
    zeige(true)

    expect(grundzeile()).not.toBeInTheDocument()
  })

  it('malt den Grund im Token „Text matt"', () => {
    zeige(true, LANGER_GRUND)

    expect(getComputedStyle(grundzeile()!).color).toBe(TEXT_MATT)
  })
})

describe('NachtlaufLaufPlatte — Tastatur und Screenreader', () => {
  it('bedient den Pfeil mit der Eingabetaste', async () => {
    const nutzer = userEvent.setup()
    const { umschalten } = zeige()

    await nutzer.tab()
    expect(pfeil()).toHaveFocus()
    await nutzer.keyboard('{Enter}')

    expect(umschalten).toHaveBeenCalledTimes(1)
    expect(inhalt()).toBeInTheDocument()
  })

  it('bedient den Pfeil mit der Leertaste', async () => {
    const nutzer = userEvent.setup()
    const { umschalten } = zeige()

    await nutzer.tab()
    await nutzer.keyboard('[Space]')

    expect(umschalten).toHaveBeenCalledTimes(1)
    expect(inhalt()).toBeInTheDocument()
  })

  it('führt den Zustand am Pfeil, nicht an der Kopfzeile', async () => {
    const nutzer = userEvent.setup()
    zeige()
    expect(pfeil()).toHaveAttribute('aria-expanded', 'false')
    expect(kopf()).not.toHaveAttribute('aria-expanded')

    await nutzer.click(screen.getByTestId('nachtlauf-ueberschrift'))

    expect(pfeil()).toHaveAttribute('aria-expanded', 'true')
    expect(pfeil()).toHaveAttribute('aria-controls')
  })

  it('lässt die Kopfzeile kein zweites Bedienelement werden', () => {
    // Ein `button` mit Titel, Metazeile und Marken darin ergäbe einen überlangen zugänglichen
    // Namen; der Pfeil bleibt der eine Knopf (Entscheidung zu #1041).
    zeige()

    expect(screen.getAllByRole('button')).toHaveLength(1)
  })
})
