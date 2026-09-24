import { apiFetch } from './client'
import type { NightRunStage } from './nightRuns'

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

/**
 * Dieselbe Aufteilung je Gattung (Issue #1013, Plan #1007): `night` sind die Nachtläufe,
 * `interactive` die interaktiven Sitzungen. Die Angabe steht **neben** `usage` und nicht an dessen
 * Stelle — `usage` bleibt die Gesamtsumme und ist am Server genau die Addition der beiden Anteile
 * (#984 AK 5).
 */
export interface VerbrauchAufteilungGattung {
  night: VerbrauchAufteilung
  interactive: VerbrauchAufteilung
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
  /** Zahl der Einträge des Zeitraums — Läufe **und** Sitzungen (Issue #1013). */
  runCount: number
  /** Zahl der Nachtläufe des Zeitraums (Issue #1017). */
  nightRunCount: number
  /**
   * Zahl der interaktiven Sitzungen des Zeitraums. Beide stehen getrennt neben `runCount`, weil
   * die Gesamtzahl allein nicht sagt, woher sie kommt: „3 Läufe" über einem Zeitraum aus einem
   * Lauf und zwei Sitzungen wäre eine falsche Aussage (#984 AK 1).
   */
  interactiveRunCount: number
  durationMs: number
  cardCount: number
  usage: VerbrauchAufteilung
  usageByKind: VerbrauchAufteilungGattung
  /**
   * Beginn der Erfassung interaktiver Sitzungen als ISO-Zeitpunkt; `null`, solange das Projekt
   * keine gemeldet hat (Plan #1007 E18). Daran unterscheidet die Anzeige „nicht erfasst" von
   * „teilweise erfasst" — ein Sitzungs-Anteil von `null` vor diesem Zeitpunkt ist keine gemessene
   * Null, sondern eine Zeit ohne Erfassung.
   */
  interactiveUsageSince: string | null
}

/** Eine Nacht innerhalb eines Zeitraums (#926 AK 8). */
export interface VerbrauchNachtKurz {
  night: string
  runCount: number
  cardCount: number
  usage: VerbrauchAufteilung
  usageByKind: VerbrauchAufteilungGattung
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

/**
 * Eine Stufe der Kette in der Aufstellung (Issue #1114, #993 AK 8).
 *
 * <p>Der Stufenname ist der des Servers und wird aus `nightRuns.ts` übernommen statt hier ein
 * zweites Mal aufgeschrieben: Es ist derselbe Wertebereich, und eine zweite Kopie liefe beim
 * nächsten Schritt der Kette auseinander.
 *
 * <p><b>Es gibt keinen Posten „ohne Stufe"</b> nach dem Muster von „ohne Vorhaben" (Plan E6) —
 * er trüge bei einem Umsetzungs-Lauf den Verbrauch einer ganzen Nacht. Ein Lauf ohne Stufen
 * erscheint in dieser Aufstellung gar nicht.
 */
export interface VerbrauchStufe {
  stage: NightRunStage
  /** Zahl der Vorgänge, die diese Stufe durchlaufen haben. */
  itemCount: number
  durationMs: number | null
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
  /** Die Kosten je Stufe der Kette; leer, wenn im Zeitraum keine Kette lief (Issue #1114). */
  stages: VerbrauchStufe[]
}

/**
 * Die Summe über die ganze Laufzeit des Projekts (Issue #1014, #984 AK 4, Plan #1007 E19) — ohne
 * Zeitraum und deshalb ohne Abdeckungs-Einordnung.
 *
 * Die beiden Lückenangaben sagen Verschiedenes und ersetzen einander nicht:
 * `oldestRetainedRunStart` zeigt, was der Ringpuffer verdrängt hat, `interactiveUsageSince` trennt
 * „nie erfasst" von „erfasst, dann verdrängt".
 */
export interface VerbrauchGesamt {
  /** Zahl aller aufbewahrten Einträge — Läufe **und** Sitzungen. */
  runCount: number
  nightRunCount: number
  interactiveRunCount: number
  cardCount: number
  usage: VerbrauchAufteilung
  usageByKind: VerbrauchAufteilungGattung
  /** Beginn des ältesten aufbewahrten Eintrags als ISO-Zeitpunkt; `null` ohne Eintrag. */
  oldestRetainedRunStart: string | null
  /** Erfassungsbeginn der interaktiven Sitzungen; `null`, solange keine gemeldet wurde. */
  interactiveUsageSince: string | null
}

/** Die Zone des Lesers: „die letzte Nacht" ist seine Nacht, nicht die des Servers (Plan E4). */
export function leserZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

export const nightRunUsageApi = {
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
  /**
   * Die Lebenszeit-Summe (Issue #1014). Ohne Parameter: Eine Lebenszeit hat keinen ersten Tag,
   * keinen Vorzeitraum und keine Zone, nach der sie sich gruppieren ließe.
   */
  total: (projectId: number) =>
    apiFetch<VerbrauchGesamt>(`/api/projects/${projectId}/night-run-usage/total`),
}

export type NightRunUsageApi = typeof nightRunUsageApi
