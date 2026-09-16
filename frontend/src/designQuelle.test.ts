import { describe, expect, it } from 'vitest'
import appQuelle from './App.tsx?raw'

/**
 * Hält `CLAUDE-design.md` gegen den Quelltext (#961). Die Designquelle macht Aussagen, die sich
 * nachzählen lassen — welche Routen sie abdeckt und wie viele Abweichungen offen sind. Ohne diesen
 * Test liefen Tabelle und Zahl beim nächsten Paket still auseinander, und die Abnahme nach AK 3
 * prüfte gegen eine veraltete Liste.
 */
const DOKU: Record<string, string> = import.meta.glob('../../CLAUDE-design.md', {
  query: '?raw',
  import: 'default',
  eager: true,
})
const doku = Object.values(DOKU)[0] ?? ''

const QUELLEN: Record<string, string> = import.meta.glob('./**/*.tsx', {
  query: '?raw',
  import: 'default',
  eager: true,
})

/** Der Abschnitt einer Überschrift bis zum nächsten Trenner. */
const abschnitt = (ueberschrift: string): string => {
  const beginn = doku.indexOf(ueberschrift)
  return beginn === -1 ? '' : doku.slice(beginn, doku.indexOf('\n---', beginn))
}

describe('CLAUDE-design.md gegen den Quelltext', () => {
  it('liest die Designquelle überhaupt ein', () => {
    expect(doku).toContain('# CLAUDE-design.md')
  })

  it('führt in „Ansicht → Regel" genau die darstellenden Routen aus App.tsx', () => {
    // Darstellend ist jede Route mit einer Seite als Element; eine Weiterleitung stellt nichts dar.
    const routen = [...appQuelle.matchAll(/<Route path="([^"]+)" element=\{<(\w+)/g)]
      .filter(([, , element]) => !element.endsWith('Redirect'))
      .map(([, pfad]) => pfad)
    const zeilen = [...abschnitt('## 🗺️ Ansicht → Regel').matchAll(/^\| `([^`]+)` \|/gm)].map(([, pfad]) => pfad)

    expect(routen.length).toBeGreaterThan(15)
    expect(new Set(zeilen).size).toBe(zeilen.length)
    expect([...zeilen].sort()).toEqual([...routen].sort())
  })

  it('beschreibt das dunkle Erscheinungsbild samt Media-Query und ohne Schalter', () => {
    const erscheinungsbilder = abschnitt('## 🌗 Erscheinungsbilder')
    expect(erscheinungsbilder).toContain("colorSchemeSelector: 'media'")
    expect(erscheinungsbilder).toContain('prefers-color-scheme: dark')
    expect(erscheinungsbilder).toMatch(/kein Schalter/)
  })

  it('nennt die Zahl der Gewichtsabweichungen so, wie das Kommando sie findet', () => {
    // Nachbildung von `grep -rn "fontWeight: 5\|fontWeight: 6" frontend/src --include=*.tsx | grep -v test`:
    // Die Zeile samt Pfad fällt heraus, sobald sie „test" enthält.
    const treffer = Object.entries(QUELLEN).flatMap(([pfad, quelle]) =>
      quelle
        .split('\n')
        .map((zeile, i) => `frontend/src/${pfad.replace('./', '')}:${i + 1}:${zeile}`)
        .filter((zeile) => /fontWeight: [56]/.test(zeile) && !zeile.includes('test')),
    )
    const nachtlauf = treffer.filter((zeile) => zeile.startsWith('frontend/src/components/nachtlauf/'))
    const offen = abschnitt('## ⚠️ Offene Abweichungen')

    expect(offen).toContain(`findet **${treffer.length}** Stellen`)
    expect(offen).toContain(`Davon liegen ${nachtlauf.length} unter`)
    expect(offen).toContain(`die übrigen ${treffer.length - nachtlauf.length} verletzen`)
  })
})
