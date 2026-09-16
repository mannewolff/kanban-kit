import { apiFetch } from './client'

/**
 * Abruf der Verbrauchs-Auswertung (Issue #940, Endpunkte aus Issue #939, Plan #933).
 *
 * Die Felder ohne Wert kommen als `null` und nicht als fehlender Schlüssel — derselbe Grund wie bei
 * `NightRunItemView` (Issue #734). **„Nicht gemessen" bleibt `null` und wird nie zu 0** (Plan E5):
 * Ein Abruf, der ein fehlendes Feld auf 0 setzte, machte aus „wir wissen es nicht" ein „es hat
 * nichts gekostet".
 */

/** Die Arten eines Auswertungszeitraums (Plan E18). */
export type VerbrauchZeitraumArt = 'DAY' | 'WEEK' | 'MONTH'

/** Abdeckung eines Zeitraums durch die aufbewahrten Läufe (Plan E8). */
export type VerbrauchAbdeckung = 'COMPLETE' | 'PARTIAL' | 'BEFORE_RETENTION'

/** Die vier Verbrauchsangaben und der Anteil aus dem Zwischenspeicher in Prozent. */
export interface VerbrauchAngaben {
  costUsd: number | null
  inputTokens: number | null
  outputTokens: number | null
  cachedInputTokens: number | null
  cachedInputSharePercent: number | null
}

/** Gesamtsumme, kartenbezogener Anteil und nicht zuordenbarer Rest (#926 AK 2). */
export interface VerbrauchAufteilung {
  total: VerbrauchAngaben
  cardShare: VerbrauchAngaben
  remainder: VerbrauchAngaben
}

/** Eine Kartenzeile einer Nacht. */
export interface VerbrauchKarte {
  cardNumber: number
  attemptCount: number
  durationMs: number | null
  usage: VerbrauchAngaben
}

/** Eine Nacht (#926 AK 1–4). */
export interface VerbrauchNacht {
  /** Datum, an dem die Nacht beginnt (`JJJJ-MM-TT`). */
  night: string
  runCount: number
  durationMs: number
  cardCount: number
  usage: VerbrauchAufteilung
  aborted: boolean
  cards: VerbrauchKarte[]
}

/** Die Kennzahlen eines Zeitraums samt seiner Grenzen. */
export interface VerbrauchKennzahlen {
  type: VerbrauchZeitraumArt
  /** Beginntag der ersten Nacht (`JJJJ-MM-TT`). */
  firstDay: string
  /** Beginntag der letzten Nacht (`JJJJ-MM-TT`). */
  lastDay: string
  from: string
  to: string
  coverage: VerbrauchAbdeckung
  noRuns: boolean
  runCount: number
  durationMs: number
  cardCount: number
  usage: VerbrauchAufteilung
}

/** Eine Nacht innerhalb eines Zeitraums (#926 AK 8). */
export interface VerbrauchNachtKurz {
  night: string
  runCount: number
  cardCount: number
  usage: VerbrauchAufteilung
  aborted: boolean
}

/** Ein Vorhaben der Aufstellung; beim Posten „ohne Vorhaben" sind Kennung, Kürzel und Titel `null`. */
export interface VerbrauchVorhaben {
  epicId: number | null
  shortcode: string | null
  title: string | null
  cardCount: number
  usage: VerbrauchAngaben
}

/** Ein Zeitraum samt Vorzeitraum, Nächten und Vorhaben-Aufstellung. */
export interface VerbrauchZeitraum {
  current: VerbrauchKennzahlen
  previous: VerbrauchKennzahlen
  nights: VerbrauchNachtKurz[]
  epics: VerbrauchVorhaben[]
  withoutEpic: VerbrauchVorhaben
  /** Eine Karte gehört zu mehreren Vorhaben — die Vorhaben-Summen überschneiden sich (Plan E11). */
  epicsOverlap: boolean
}

/** Die Zone des Lesers: „die letzte Nacht" ist seine Nacht, nicht die des Servers (Plan E4). */
export function leserZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

export const nightRunUsageApi = {
  night: (projectId: number, date: string, zone: string = leserZone()) =>
    apiFetch<VerbrauchNacht>(
      `/api/projects/${projectId}/night-run-usage/night?${new URLSearchParams({ date, zone })}`,
    ),
  period: (
    projectId: number,
    type: VerbrauchZeitraumArt,
    stepsBack: number,
    zone: string = leserZone(),
  ) =>
    apiFetch<VerbrauchZeitraum>(
      `/api/projects/${projectId}/night-run-usage?${new URLSearchParams({
        type,
        stepsBack: String(stepsBack),
        zone,
      })}`,
    ),
}

export type NightRunUsageApi = typeof nightRunUsageApi
