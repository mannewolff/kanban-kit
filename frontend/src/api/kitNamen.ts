import { useEffect, useState } from 'react'
import { apiFetch } from './client'

/**
 * Das Namen-Register des claude-workflow-kits vom Server (Issue #1594, Plan #1582 A4, E2, E9, E10).
 *
 * Das Frontend führt keine zweite Kopie der Paarliste in TypeScript: Sachen, Titel-Präfixe und
 * Namensraum kommen allein aus `GET /api/kit-namen` (Issue #1588). Es gibt keine eingebaute
 * Ersatzliste (E10) — wer die Namen braucht, zeigt während des Ladens und beim Fehler einen Hinweis.
 */

/** Eine Sache des Kits mit allen ihren Label-Namen, der deutsche zuerst. */
export interface KitSache {
  sache: string
  namen: string[]
}

/** Eine Kartenart mit ihren Titel-Präfixen, das deutsche zuerst. */
export interface KitArt {
  art: string
  namen: string[]
}

/** Antwort von `GET /api/kit-namen`. */
export interface KitNamen {
  sachen: KitSache[]
  titelPraefixe: KitArt[]
  namensraum: string[]
}

/** Zustand von {@link useKitNamen}. */
export type KitNamenZustand =
  | { status: 'laden' }
  | { status: 'fertig'; daten: KitNamen }
  | { status: 'fehler'; fehler: unknown }

let geladen: Promise<KitNamen> | null = null

/**
 * Lädt das Register einmal je Sitzung; weitere Aufrufe teilen sich dieselbe Anfrage. Scheitert
 * sie, versucht der nächste Aufruf es erneut.
 */
export function ladeKitNamen(): Promise<KitNamen> {
  geladen ??= apiFetch<KitNamen>('/api/kit-namen').catch((fehler: unknown) => {
    geladen = null
    throw fehler
  })
  return geladen
}

/** Liefert das Register: erst `laden`, dann `fertig` mit den Daten oder `fehler`. */
export function useKitNamen(): KitNamenZustand {
  const [zustand, setZustand] = useState<KitNamenZustand>({ status: 'laden' })
  useEffect(() => {
    let aktiv = true
    ladeKitNamen().then(
      (daten) => {
        if (aktiv) setZustand({ status: 'fertig', daten })
      },
      (fehler: unknown) => {
        if (aktiv) setZustand({ status: 'fehler', fehler })
      },
    )
    return () => {
      aktiv = false
    }
  }, [])
  return zustand
}

/** Alle Namen der Sache, der deutsche zuerst; leer für eine unbekannte Sache. */
export function namenDerSache(daten: KitNamen, sache: string): string[] {
  return daten.sachen.find((s) => s.sache === sache)?.namen ?? []
}

/** Sache zum Namen: nach Trimmen und mit exakter Schreibung wie im Backend, sonst `undefined`. */
export function sacheVon(daten: KitNamen, name: string): string | undefined {
  const gesucht = name.trim()
  return daten.sachen.find((s) => s.namen.includes(gesucht))?.sache
}

/**
 * Der Name, unter dem ein Label der Sache auf diesem Board zu setzen ist (E9): der Name, den das
 * Board trägt; trägt es beide oder keinen, der deutsche. `undefined` für eine unbekannte Sache.
 */
export function boardName(
  daten: KitNamen,
  sache: string,
  boardLabels: readonly string[],
): string | undefined {
  const namen = namenDerSache(daten, sache)
  const getragen = namen.filter((n) => boardLabels.includes(n))
  return getragen.length === 1 ? getragen[0] : namen[0]
}

function maskiert(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * Kartenart aus dem Titel-Präfix nach denselben Regeln wie im Backend: führender Leerraum ist
 * erlaubt, die Schreibung beliebig. Ohne bekanntes Präfix `undefined`.
 */
export function artAusTitel(daten: KitNamen, titel: string): string | undefined {
  return daten.titelPraefixe.find((a) =>
    new RegExp(`^\\s*(?:${a.namen.map(maskiert).join('|')})`, 'i').test(titel),
  )?.art
}
