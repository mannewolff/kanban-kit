import { describe, expect, it } from 'vitest'
import indexHtml from '../../index.html?raw'

/**
 * Guard fuer das Mal im Browser-Tab (#989). Die Auslieferungsdateien kommen ueber Vite statt ueber
 * `node:fs` (wie in `designGuard.test.ts`): So haengt der Test nicht am Arbeitsverzeichnis, aus dem
 * Vitest gestartet wurde.
 */
const AUSLIEFERUNG: Record<string, string> = import.meta.glob('../../public/*', {
  query: '?raw',
  import: 'default',
  eager: true,
})

/** Dateinamen unter `public/`, ohne den Pfad, mit dem Vite die Glob-Schluessel bildet. */
const dateien = Object.keys(AUSLIEFERUNG).map((schluessel) => schluessel.replace('../../public/', ''))

describe('Favicon und Touch-Icon', () => {
  it('verweist im Kopf auf das Kupfer-Mal als SVG', () => {
    expect(indexHtml).toContain('<link rel="icon" type="image/svg+xml" href="/favicon.svg" />')
  })

  it('liefert die verwiesenen Dateien aus', () => {
    expect(dateien).toContain('favicon.svg')
    expect(indexHtml).toContain('<link rel="apple-touch-icon" href="/apple-touch-icon.png" />')
    expect(dateien).toContain('apple-touch-icon.png')
  })

  it('traegt den Springer nirgends mehr', () => {
    expect(indexHtml).not.toContain('knight')
    expect(dateien).not.toContain('knight.svg')
  })

  it('zeigt im Favicon das Mal der Schiene: Kupferverlauf und drei Balken', () => {
    const svg = AUSLIEFERUNG['../../public/favicon.svg']
    // Die drei Halte des Verlaufs aus `MARKE_MAL_SX` (Kupfer hell → Kupfer → Kupfer tief). Feste
    // Werte des hellen Erscheinungsbildes: Ein Favicon liest keine CSS-Variablen.
    expect(svg).toContain('#C2743C')
    expect(svg).toContain('#A85F2C')
    expect(svg).toContain('#7B421C')
    // Drei Balken mit den Deckkraeften aus `MarkenSymbol` (navIcons.tsx).
    expect(svg?.match(/fill-opacity="\.(?:92|72|5)"/g) ?? []).toHaveLength(3)
  })
})
