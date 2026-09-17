import { describe, expect, it } from 'vitest'
import appQuelle from './App.tsx?raw'

/**
 * Hält `CLAUDE-design.md` gegen den Quelltext (#961). Die Designquelle macht Aussagen, die sich
 * nachprüfen lassen — welche Routen sie abdeckt und welche Vorlage sie bindet. Ohne diesen
 * Test liefen Tabelle und Zahl beim nächsten Paket still auseinander, und die Abnahme nach AK 3
 * prüfte gegen eine veraltete Liste.
 */
const DOKU: Record<string, string> = import.meta.glob('../../CLAUDE-design.md', {
  query: '?raw',
  import: 'default',
  eager: true,
})
const doku = Object.values(DOKU)[0] ?? ''

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

  it('macht den Leitstand-Entwurf zur verbindlichen Vorlage, abgenommen per Bildschirmfoto', () => {
    // Seit 2026-09-16 (#978): Die Vorlage gilt für Aussehen und Aufbau; ohne diese Sätze entschiede
    // ein Plan Gestaltungsfragen wieder gegen sie (Retro zu #925/#932).
    const vorlage = abschnitt('## 📌 Vorlage und Abnahme')
    expect(vorlage).toContain('docs/entwurf-leitstand.html')
    expect(vorlage).toMatch(/Die Vorlage ist verbindlich/)
    expect(vorlage).toMatch(/Bildschirmfoto bei 1440 × 900 neben der Vorlage/)
  })
})
