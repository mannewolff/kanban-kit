import { ThemeProvider } from '@mui/material/styles'
import { GRUND_UNBEKANNT, serverBefund } from '../test/befund'
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Card, CardByNumber } from '../api/cards'
import type {
  NightRunBudgetView,
  NightRunErrorClassCounts,
  NightRunItemStageView,
  NightRunItemView,
  NightRunResult,
  NightRunUsageView,
  NightRunView,
} from '../api/nightRuns'
import { SnackbarProvider } from '../components/SnackbarProvider'
import echterLauf from '../lib/__fixtures__/night-run-2026-09-07-085229.json'
import echterNachtplanRegulaer from '../lib/__fixtures__/night-run-2026-09-09-125621.json'
import echterNachtplanHarterStopp from '../lib/__fixtures__/night-run-2026-09-09-141506.json'
import echterPrueflauf from '../lib/__fixtures__/night-run-2026-09-11-103116.json'
import echteKette from '../lib/__fixtures__/night-run-2026-09-14-131200.json'
import { formatDuration } from '../lib/formatDuration'
import { parseNightRunErgebnisstand } from '../lib/nightRunErgebnisstand'
import { buildHandoffText, KURZ_GRUND_MAX, type NightRunHandoffItem } from '../lib/nightRunHandoff'
import { NACHTLAUF_FARBEN, NACHTLAUF_SCHRIFTEN } from '../nachtlaufDesign'
import { cssRegel } from '../test/cssRegel'
import { MELDER, theme } from '../theme'
import { NightRunPage } from './NightRunPage'
import appQuelle from '../App.tsx?raw'

/**
 * Die Seite wird gegen einen **`fetch`-Stub** getestet, nicht gegen gemockte API-Module. Nur so
 * ist das Kriterium aus Issue #725 überhaupt prüfbar, dass beim Hineingeben eines Ergebnisstands
 * **kein** Request seinen Inhalt trägt: Ein gemocktes `nightRunsApi` erzeugte gar keine Requests,
 * und der Test wäre grün, ohne etwas zu belegen.
 *
 * Eingelesen wird seit Issue #774 ausschließlich der **Ergebnisstand** (`night-run-<datum>.json`);
 * das Textprotokoll ist kein Weg mehr. Die selbstgebauten Stände sind anonymisiert (Titel nach dem
 * Schema `Paket N`, keine Pfade) — daneben steht der echte Lauf vom 2026-09-07 aus
 * `lib/__fixtures__/`, damit die Seite mindestens einmal gegen echte Daten läuft.
 */

// Das Karten-Detail ist separat getestet (CardDetailModal.test.tsx) — hier ein Stub, der die
// geöffnete Karte sichtbar macht und den Schließen-Pfad auslöst.
//
// Der Stub gibt zusätzlich aus, **welches Theme** er sieht (#914): Nicht-Ziel 2 des Fachplans
// verbietet, die Gestaltung der Nachtlauf-Auswertung auf die übrige Anwendung zu übertragen, und
// der Dialog gehört zu ihr, obwohl er von dieser Seite aus geöffnet wird. Ob er im Theme-Teilbaum
// steht, entscheidet allein seine Stellung im React-Baum — das Portal, in dem er im DOM landet,
// sagt darüber nichts.
vi.mock('../components/CardDetailModal', async () => {
  const { useTheme } = await import('@mui/material/styles')
  return {
    CardDetailModal: ({
      card,
      onClose,
    }: {
      card: { number: number | null }
      onClose: () => void
    }) => {
      const gesehenesTheme = useTheme()
      return (
        <div data-testid="karten-detail" data-schrift={String(gesehenesTheme.typography.fontFamily)}>
          Karte {card.number}
          <button type="button" onClick={onClose}>
            detail-schliessen
          </button>
        </div>
      )
    },
  }
})

/** Startzeitpunkt eines Laufs — der Schlüssel der Auswertung (Plan #718, A4). */
const startedAt = (minute: number) => `2026-09-01T22:${String(minute).padStart(2, '0')}:00.000Z`


/** Die Dauer einer Session im Ergebnisstand; die Laufdauer ist ihre Summe. */
const SIEBEN_MIN = 7 * 60_000

/**
 * Ein Ergebnisstand der Fassung 1, wie `night.mjs` ihn schreibt. Jedes Feld ist einzeln
 * überschreibbar — dieselbe Bauform wie in `lib/nightRunErgebnisstand.test.ts`.
 */
const stand = (felder: Record<string, unknown> = {}): string =>
  JSON.stringify({
    schemaFassung: 1,
    erzeugtVon: '1.47.0',
    start: startedAt(0),
    art: 'implementierung',
    modell: 'claude-opus-5',
    max: 5,
    label: null,
    einheiten: [],
    abschluss: 'regulaer',
    ...felder,
  })

/** Eine Einheit des Ergebnisstands — ein Arbeitspaket der Runde. */
const einheit = (felder: Record<string, unknown>): Record<string, unknown> => ({
  id: '700',
  titel: 'Paket A',
  dauerMs: SIEBEN_MIN,
  ...felder,
})

/**
 * Markierungstext in einem Feld, das der Parser nicht deutet. Der Ergebnisstand führt in
 * `pruefung.laufen[].grund` die Pfade der geänderten Dateien — sie dürfen in keinem Request
 * auftauchen, weder in einer URL noch in einem Body.
 */
const GEHEIM = 'GEHEIM-NICHT-SENDEN'

/** Ein grüner Nachweis, samt der Felder, die die Auswertung nicht braucht. */
const GEPRUEFT = {
  zustand: 'geprueft',
  laufen: [{ cmd: 'npm test', grund: GEHEIM, ergebnis: 'gruen' }],
  ausgelassen: [],
}

/** Ein roter Nachweis — der Fall, für den es die Auswertung gibt. */
const NACHWEIS_ROT = {
  zustand: 'rot',
  rotesKommando: 'npm test',
  rotesErgebnis: 'rot',
  laufen: [{ cmd: 'npm test', grund: GEHEIM, ergebnis: 'rot' }],
  ausgelassen: [],
}

const EIN_LAUF = stand({
  einheiten: [einheit({ ausgang: 'erfolg', commit: 'a1b2c3d', pruefung: GEPRUEFT })],
})

/**
 * Ein Lauf, der Kosten meldet (Issue #948) — am Lauf die Summe über alle Sitzungen, am Vorgang
 * sein eigener Betrag. Die Zahlen sind die des echten Ketten-Laufs vom 2026-09-14.
 */
const MIT_KOSTEN = stand({
  kostenSumme: 25.983293,
  einheiten: [einheit({ ausgang: 'erfolg', commit: 'a1b2c3d', pruefung: GEPRUEFT, kostenUsd: 11.5228115 })],
})

/** Erfolgreiche Session, aber roter Nachweis → gelb. */
const GELB = stand({
  einheiten: [einheit({ ausgang: 'erfolg', commit: 'a1b2c3d', pruefung: NACHWEIS_ROT })],
})

/** Der Grund eines zurückgestellten Pakets, wie `night.mjs` ihn schreibt. */
const GRUND_ZURUECKGESTELLT = 'Abhaengigkeit #999 liegt nicht in Done.'

/** Alle vier Zustände in einem Lauf. */
const VIER_ZUSTAENDE = stand({
  einheiten: [
    einheit({ ausgang: 'erfolg', commit: 'a1b2c3d', pruefung: GEPRUEFT }),
    einheit({ id: '701', titel: 'Paket B', ausgang: 'erfolg', commit: 'b2c3d4e', pruefung: NACHWEIS_ROT }),
    einheit({ id: '702', titel: 'Paket C', ausgang: 'fehlschlag', pruefung: { zustand: 'ungeprueft' } }),
    // Ein zurückgestelltes Paket lief nie, also trägt es auch keine Dauer.
    einheit({ id: '703', titel: 'Paket D', ausgang: 'zurueckgestellt', grund: GRUND_ZURUECKGESTELLT, dauerMs: undefined }),
  ],
})

/** Der echte Lauf vom 2026-09-07 (Issue #773) — unverändert, wie der Runner ihn schrieb. */
const ECHTER_STAND = JSON.stringify(echterLauf)
const ECHTER_START = '2026-09-07T08:52:29.532Z'

/**
 * Der echte, hart gestoppte Nachtplan-Lauf vom 2026-09-09 (Issue #805/#806) — unverändert, aus
 * `claude-workflow-kit`. 33 übersprungene Pakete (kein Label `kit:nightplan`), zwei abgebrochene
 * (#479, #549).
 */
const ECHTER_NACHTPLAN_STAND = JSON.stringify(echterNachtplanHarterStopp)
const ECHTER_NACHTPLAN_START = '2026-09-09T14:15:06.165Z'

/**
 * Der echte, regulär beendete Erzeugungs-Lauf vom 2026-09-09 (Issue #872). Drei bearbeitete
 * Vorgänge, darunter der fortgesetzte #535 mit **gemessener** Dauer null und ohne jede
 * Sitzungs-Kennzahl — der Fall, an dem sich die Null von der fehlenden Angabe trennt.
 */
const ECHTE_ERZEUGUNG_STAND = JSON.stringify(echterNachtplanRegulaer)
const ECHTE_ERZEUGUNG_START = '2026-09-09T12:56:21.983Z'

/**
 * Der echte Prüf-Lauf vom 2026-09-11 (Issue #872). Sein einziger bearbeiteter Vorgang #782
 * meldet 18,1 Minuten Arbeitszeit bei 14,7 Minuten Dauer, weil mehrere Prüfer gleichzeitig
 * liefen — der Beleg dafür, dass die Arbeitszeit die Dauer übersteigen kann.
 */
const ECHTER_PRUEFLAUF_STAND = JSON.stringify(echterPrueflauf)
const ECHTER_PRUEFLAUF_START = '2026-09-11T10:31:16.379Z'

/**
 * Der echte Ketten-Lauf vom 2026-09-14 (Issue #854) — unverändert, wie der Runner ihn schrieb.
 * Drei Vorgänge: zwei fertige Ketten (#791, #814) und ein Zeitbudget-Abbruch mit erzeugtem Plan
 * (#842). Anders als der Nachtplan-Lauf geht er an den Server.
 */
const ECHTE_KETTE_STAND = JSON.stringify(echteKette)
const ECHTE_KETTE_START = '2026-09-14T13:12:00.574Z'

/** Eine Runner-Zeile, die kein Muster deutete — mit Markdown-Zeichen im Text. */
const UNGEDEUTET = 'Voellig unbekannte Runner-Zeile mit *Sternchen* und `Backticks`'

function karte(
  partial: Partial<CardByNumber> & { id: number; number: number; title: string },
): CardByNumber {
  return {
    description: null,
    type: 'CARD',
    dependencies: [],
    assignees: [],
    labels: [],
    parentId: null,
    shortcode: null,
    dueDate: null,
    archived: false,
    ideaStored: false,
    derivedFrom: null,
    boardId: 1,
    columnId: 2,
    ...partial,
  }
}

/**
 * Ein Vorhaben, wie `GET /api/cards/{id}` es liefert — die volle `Card`, nicht die `CardByNumber`
 * des Nummer-Lookups. `id` und `number` sind in den Tests bewusst verschieden: Der Abruf läuft über
 * die `parentId` (eine Karten-ID), angezeigt wird die projektweite Nummer.
 */
function vorhaben(partial: Partial<Card> & { id: number; number: number; title: string }): Card {
  return {
    boardId: 1,
    columnId: 2,
    description: null,
    excerpt: null,
    positionInColumn: 0,
    archived: false,
    ideaStored: false,
    movedToDoneAt: null,
    dependencies: [],
    type: 'EPIC',
    parentId: null,
    shortcode: null,
    assignees: [],
    dueDate: null,
    labels: [],
    derivedFrom: null,
    ...partial,
  }
}

/** Der Lauf eines Ergebnisstands; wirft, wenn er gar nicht deutbar ist — dann taugt das Fixture nicht. */
function gedeutet(ergebnisstand: string) {
  const ergebnis = parseNightRunErgebnisstand(ergebnisstand)
  if (!ergebnis.ok) throw new Error(`Fixture nicht deutbar: ${ergebnis.grund}`)
  return ergebnis.run
}

/**
 * Engt einen Modus auf das ein, was der Server kennt — `wieAufbewahrt`/`aufbewahrt` bilden nur
 * seine Antwort nach, und ein Nachtplan-Lauf kommt dort nie an (Plan #803, Entscheidung 8). Ein
 * Wurf statt eines Casts: Ein Test, der versehentlich einen Nachtplan-Ergebnisstand hier hineingibt,
 * soll das laut sagen, nicht still eine falsche Server-Antwort simulieren.
 */
function alsServerModus(mode: NightRunView['mode'] | 'NIGHTPLAN'): NightRunView['mode'] {
  if (mode === 'NIGHTPLAN') throw new Error('Der Server liefert nie NIGHTPLAN — falsches Fixture?')
  return mode
}

/**
 * Der Lauf eines Ergebnisstands so, wie der Server ihn nach dem Einliefern zurückgibt. Die Seite
 * lädt nach erfolgreichem Senden die Liste neu — ohne diese Nachbildung zeigte jeder Sendetest
 * danach eine leere Seite. Der Helfer bildet nur die Feldabbildung des Servers nach; **welchen**
 * Zustand ein Arbeitspaket trägt, prüfen die Tests weiterhin am sichtbaren Text.
 */
function wieAufbewahrt(ergebnisstand: string): NightRunView[] {
  const run = gedeutet(ergebnisstand)
  const lauf: NightRunView = {
      id: 1,
      startedAt: run.startedAt,
      mode: alsServerModus(run.mode),
      durationMs: run.durationMs,
      processedCount: run.processedCount,
      skippedCount: run.skippedCount,
      unparsedCount: run.unparsedCount,
      // Der Ergebnisstand kennt keine ungedeuteten Zeilen (#773), also schickt der Server auch
      // keinen Auszug zurück. Ein aufbewahrter Lauf aus der Zeit der Protokolldeutung schon —
      // dafür steht {@link aufbewahrt} mit ausdrücklicher Vorgabe.
      unparsedSample: null,
      createdAt: '2026-09-02T06:00:00.000Z',
      origin: 'UPLOAD',
      tokenName: null,
      complete: true,
      updatedAt: null,
      usage: null,
      noWorkReason: null,
      abortReason: null,
      // Der Upload-Weg fuehrt keine Budgets (E14); der Server legt sie zu einem eingelesenen Lauf
      // fest als „nicht gemeldet" ab.
      budget: null,
      items: run.items.map((item, position) => wieAufbewahrtesItem({ id: position + 1, ...item })),
      outcome: { abortReason: null, verdict: 'SUCCEEDED', decisiveItem: null, noWorkReason: null },
  }
  // Der Befund kommt aus dem Szenario, nicht aus einer Vorgabe (Issue #1081).
  return [{ ...lauf, outcome: serverBefund(lauf) }]
}

/** Ein Arbeitspaket in der Kurzform der Tests: Was nichts zur Sache tut, bleibt weg. */
type ItemVorgabe = Partial<NightRunItemView> &
  Pick<NightRunItemView, 'id' | 'cardNumber' | 'title' | 'state'>

/**
 * Ein Arbeitspaket so, wie der Server es schickt: Was der Lauf nicht wusste, steht als `null` im
 * JSON — nicht als fehlender Schlüssel (Issue #734). Die Helfer nehmen die Angaben in der
 * Frontend-Schreibweise (`undefined`) entgegen und übersetzen sie hier einmal, damit kein Test
 * versehentlich eine Antwortform nachbildet, die es nicht gibt.
 */
function wieAufbewahrtesItem(item: ItemVorgabe): NightRunItemView {
  return {
    id: item.id,
    cardNumber: item.cardNumber,
    title: item.title,
    state: item.state,
    errorClass: item.errorClass ?? null,
    durationMs: item.durationMs ?? null,
    commitHash: item.commitHash ?? null,
    excerpt: item.excerpt ?? null,
    usage: item.usage ?? null,
    // Leer statt `null`: „dieser Vorgang hatte keine Stufen" ist eine Aussage (Issue #1113).
    stages: item.stages ?? [],
  }
}

/** Ein Verbrauch in der Antwortform: Was der Test nicht nennt, hat der Lauf nicht gemessen. */
const verbraucht = (felder: Partial<NightRunUsageView>): NightRunUsageView => ({
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  modelDurationMs: null,
  turns: null,
  ...felder,
})

/** Die Einlieferungs-Antwort zu einem Ergebnisstand, dessen Lauf neu ist. */
const alleNeu = (ergebnisstand: string): NightRunResult[] => [
  { startedAt: gedeutet(ergebnisstand).startedAt, created: true },
]

function aufbewahrt(
  partial: Omit<Partial<NightRunView>, 'items'> & {
    id: number
    startedAt: string
    items?: ItemVorgabe[]
  },
): NightRunView {
  const { items, ...rest } = partial
  return {
    mode: 'IMPLEMENTATION',
    durationMs: 10 * 60_000,
    processedCount: 1,
    skippedCount: 0,
    unparsedCount: 0,
    unparsedSample: null,
    createdAt: '2026-09-02T06:00:00.000Z',
    origin: 'UPLOAD',
    tokenName: null,
    complete: true,
    updatedAt: null,
    usage: null,
    noWorkReason: null,
    abortReason: null,
    budget: null,
    ...rest,
    items: (items ?? []).map(wieAufbewahrtesItem),
    outcome:
      rest.outcome ??
      serverBefund({
        complete: rest.complete ?? true,
        noWorkReason: rest.noWorkReason,
        // Der Abbruchgrund steht am Lauf **und** im Befund (Issue #1143) — ein Fixture, das ihn nur
        // am Lauf trüge, bildete eine Antwort nach, die es nicht gibt.
        abortReason: rest.abortReason,
        items: (items ?? []).map(wieAufbewahrtesItem),
      }),
  }
}

/** Leere Verbrauchsangaben — „nicht gemessen". */
const VERBRAUCH_NICHTS = {
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  cachedInputSharePercent: null,
}

const VERBRAUCH_LEER = {
  total: VERBRAUCH_NICHTS,
  cardShare: VERBRAUCH_NICHTS,
  remainder: VERBRAUCH_NICHTS,
}

/** Die Aufteilung nach Gattung, beide Anteile ungemessen (Issue #1013, #1016). */
const VERBRAUCH_JE_GATTUNG = { night: VERBRAUCH_LEER, interactive: VERBRAUCH_LEER }

/** Kennzahlen eines Zeitraums ohne Messung. */
const verbrauchKennzahlen = (type: string, firstDay: string, lastDay: string) => ({
  type,
  firstDay,
  lastDay,
  from: '2026-09-07T10:00:00Z',
  to: '2026-09-14T10:00:00Z',
  coverage: 'COMPLETE',
  noRuns: false,
  runCount: 2,
  durationMs: 60_000,
  cardCount: 1,
  usage: VERBRAUCH_LEER,
  usageByKind: VERBRAUCH_JE_GATTUNG,
  interactiveUsageSince: null,
})

/**
 * Jede Zeitraum-Anfrage bekommt denselben Zeitraum: Beim Tageszeitraum mit Rückschritt 0 liefert er
 * das Datum der zuletzt abgeschlossenen Nacht, in der Zeitraum-Sicht eine Nacht zum Anwählen.
 */
const VERBRAUCH_ZEITRAUM = {
  current: verbrauchKennzahlen('DAY', '2026-09-15', '2026-09-15'),
  previous: verbrauchKennzahlen('DAY', '2026-09-14', '2026-09-14'),
  nights: [
    {
      night: '2026-09-10',
      runCount: 1,
      cardCount: 1,
      usage: VERBRAUCH_LEER,
      usageByKind: VERBRAUCH_JE_GATTUNG,
      aborted: false,
    },
  ],
  epics: [],
  withoutEpic: { epicId: null, shortcode: null, title: null, cardCount: 0, usage: VERBRAUCH_NICHTS },
  epicsOverlap: false,
  stages: [],
}

/** Eine Nacht aus zwei Läufen — mit dem Datum, nach dem gefragt wurde. */
const verbrauchNacht = (night: string) => ({
  night,
  runCount: 2,
  durationMs: 60_000,
  cardCount: 1,
  usage: VERBRAUCH_LEER,
  usageByKind: VERBRAUCH_JE_GATTUNG,
  aborted: false,
  cards: [],
  stages: [],
})

interface Antworten {
  /** Je `GET /night-runs` eine Antwort; die letzte gilt für alle weiteren Aufrufe. */
  listen?: NightRunView[][]
  /** Statt einer Liste eine Fehlerantwort — der Ladepfad beim Öffnen der Seite. */
  listenFehler?: string
  /**
   * Lässt den `GET /night-runs` **ab dem n-ten** Aufruf scheitern (1-basiert). Anders als
   * {@link Antworten.listenFehler} trifft das nicht schon den Abruf beim Öffnen der Seite — nur so
   * ist das Neuladen nach dem Einliefern für sich prüfbar (Issue #1116, AK 6).
   */
  listenFehlerAb?: number
  /**
   * Ergebnis des `POST`; `fehler` erzeugt stattdessen eine 400-Antwort mit `detail`.
   *
   * `rohText` antwortet mit einem Body **ohne** RFC-9457-`detail` (etwa die HTML-Fehlerseite eines
   * Reverse-Proxys), `netzfehler` lässt `fetch` selbst scheitern — beides Fälle, in denen es keine
   * für den Nutzer formulierte Server-Meldung gibt.
   */
  submit?: { ergebnis?: NightRunResult[]; fehler?: string; rohText?: string; netzfehler?: boolean }
  /** Je `GET /night-runs/error-class-counts` eine Antwort; die letzte gilt für alle weiteren Aufrufe. */
  zaehler?: NightRunErrorClassCounts[]
  /** Statt der Häufigkeiten eine Fehlerantwort — der Fehlerpfad aus Issue #726. */
  zaehlerFehler?: string
  /** Karten je projektweiter Nummer; ein fehlender Eintrag antwortet mit 404. */
  karten?: Record<number, CardByNumber>
  /**
   * Hält den Nummer-Lookup an, bis dieses Promise auflöst — das Gegenstück zu
   * {@link Antworten.kartenNachIdVerzoegert} für `GET /cards/by-number/{n}`. Nur damit ist der
   * Verweis-Zustand „die Karte lädt noch" prüfbar, ohne ihn aus dem Zeitverhalten des Stubs zu
   * erraten (Issue #868, Punkt 8).
   */
  kartenVerzoegert?: Promise<void>
  /** Karten je Karten-ID (`GET /api/cards/{id}`) — die Vorhaben; fehlender Eintrag = 404. */
  kartenNachId?: Record<number, Card>
  /**
   * Hält den Einzelabruf einer Karte an, bis dieses Promise auflöst. So ist der Zustand „der
   * Vorhaben-Abruf läuft noch" prüfbar, den zwei nebenläufige `ladeKetten()`-Aufrufe erzeugen.
   */
  kartenNachIdVerzoegert?: Promise<void>
  /**
   * Die Boards des Projekts, ihre Karten und je Karten-ID der Anlagezeitpunkt — die Quellen, aus
   * denen ein eingelieferter Kettenlauf seine angelegten Karten ermittelt (Issue #1106). Ohne
   * `boards` scheitert der Abruf, wie jede unerwartete Anfrage.
   */
  boards?: number[]
  boardKarten?: Record<number, Card[]>
  angelegtAm?: Record<number, string>
}

/** Alle Anfragen dieses Tests, in Reihenfolge — Grundlage der Sende- und Ladepfad-Prüfungen. */
let anfragen: Array<{ url: string; method: string; body: string }> = []

const antwortOk = (daten: unknown) => ({
  ok: true,
  status: 200,
  statusText: 'OK',
  text: () => Promise.resolve(JSON.stringify(daten)),
})

const antwortFehler = (detail: string, status = 400) => ({
  ok: false,
  status,
  statusText: 'Bad Request',
  text: () => Promise.resolve(JSON.stringify({ detail })),
})

/** Fehlerantwort ohne RFC-9457-Problem im Body — der Roh-Body gehört nicht vor den Nutzer. */
const antwortOhneDetail = (body: string, status = 502) => ({
  ok: false,
  status,
  statusText: 'Bad Gateway',
  text: () => Promise.resolve(body),
})

/**
 * Die Antworten des Verbrauchs-Bereichs (Issue #941). Ausgelagert, weil sie keinen Zustand des
 * Stubs brauchen — anders als Liste und Haeufigkeiten, die ihren Zaehler mitfuehren.
 */
function verbrauchsAntwort(url: string) {
  if (url.startsWith('/api/projects/5/night-run-usage/night?')) {
    const datum = new URL(url, 'http://localhost').searchParams.get('date') ?? ''
    return Promise.resolve(antwortOk(verbrauchNacht(datum)))
  }
  if (url.startsWith('/api/projects/5/night-run-usage?')) {
    return Promise.resolve(antwortOk(VERBRAUCH_ZEITRAUM))
  }
  return undefined
}

/**
 * Eine gefundene Karte oder 404, wahlweise angehalten bis `verzoegert` aufloest — die Form teilen
 * sich der Abruf nach Nummer und der nach ID.
 */
function kartenErgebnis(gefunden: unknown, verzoegert: Promise<void> | undefined) {
  const antwort = () =>
    gefunden === undefined ? antwortFehler('Karte nicht gefunden', 404) : antwortOk(gefunden)
  return verzoegert === undefined ? Promise.resolve(antwort()) : verzoegert.then(antwort)
}

/** Die beiden Kartenabrufe: nach projektweiter Nummer und nach Karten-ID. */
function kartenAntwort(url: string, antworten: Antworten) {
  const nummer = /^\/api\/projects\/5\/cards\/by-number\/(\d+)$/.exec(url)
  if (nummer) {
    return kartenErgebnis(antworten.karten?.[Number(nummer[1])], antworten.kartenVerzoegert)
  }
  const kartenId = /^\/api\/cards\/(\d+)$/.exec(url)
  if (kartenId) {
    return kartenErgebnis(
      antworten.kartenNachId?.[Number(kartenId[1])],
      antworten.kartenNachIdVerzoegert,
    )
  }
  return undefined
}

/** Boards, Karten je Board und Kartenverlauf — die Herkunftsquellen aus Issue #1106. */
function herkunftsAntwort(url: string, antworten: Antworten) {
  if (url === '/api/projects/5/boards' && antworten.boards !== undefined) {
    return Promise.resolve(antwortOk(antworten.boards.map((id) => ({ id, projectId: 5, name: `Board ${id}` }))))
  }
  const board = /^\/api\/boards\/(\d+)\/cards$/.exec(url)
  if (board) {
    return Promise.resolve(antwortOk(antworten.boardKarten?.[Number(board[1])] ?? []))
  }
  const verlauf = /^\/api\/cards\/(\d+)\/activity$/.exec(url)
  if (verlauf) {
    const zeit = antworten.angelegtAm?.[Number(verlauf[1])]
    return Promise.resolve(antwortOk(zeit === undefined ? [] : [{ id: 1, type: 'CREATED', createdAt: zeit }]))
  }
  return undefined
}

/**
 * Eine Folge von Antworten: je Aufruf die naechste, die letzte gilt fuer alle weiteren. Den
 * Zaehler traegt der Abschluss statt einer Variablen im Stub — Liste und Haeufigkeiten teilen
 * sich damit dieselbe Mechanik, statt sie zweimal zu schreiben.
 */
function folge(staende: readonly unknown[]) {
  let index = 0
  return () => {
    const daten = staende[Math.min(index, staende.length - 1)]
    index += 1
    return Promise.resolve(antwortOk(daten))
  }
}

/** Die vier Ausgaenge des Sendepfads: Netzfehler, Antwort ohne `detail`, Fehler, Erfolg. */
function submitAntwort(submit: Antworten['submit']) {
  if (submit?.netzfehler === true) {
    return Promise.reject(new TypeError('Failed to fetch'))
  }
  if (submit?.rohText !== undefined) {
    return Promise.resolve(antwortOhneDetail(submit.rohText))
  }
  return Promise.resolve(
    submit?.fehler === undefined ? antwortOk(submit?.ergebnis ?? []) : antwortFehler(submit.fehler),
  )
}

function stubFetch(antworten: Antworten) {
  const naechsteListe = folge(antworten.listen ?? [[]])
  const naechsterZaehler = folge(antworten.zaehler ?? [{}])
  let listenAufrufe = 0
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      anfragen.push({ url, method, body: String(init?.body ?? '') })

      const verbrauch = verbrauchsAntwort(url)
      if (verbrauch) return verbrauch

      if (url === '/api/projects') {
        return Promise.resolve(antwortOk([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }]))
      }
      if (url === '/api/projects/5/night-runs' && method === 'GET') {
        listenAufrufe += 1
        if (antworten.listenFehler !== undefined) {
          return Promise.resolve(antwortFehler(antworten.listenFehler, 403))
        }
        return antworten.listenFehlerAb !== undefined && listenAufrufe >= antworten.listenFehlerAb
          ? Promise.resolve(antwortFehler('Liste nicht abrufbar', 403))
          : naechsteListe()
      }
      if (url === '/api/projects/5/night-runs/error-class-counts') {
        return antworten.zaehlerFehler === undefined
          ? naechsterZaehler()
          : Promise.resolve(antwortFehler(antworten.zaehlerFehler, 403))
      }
      if (url === '/api/projects/5/night-runs' && method === 'POST') {
        return submitAntwort(antworten.submit)
      }
      const karte = kartenAntwort(url, antworten) ?? herkunftsAntwort(url, antworten)
      if (karte) return karte

      return Promise.reject(new Error(`unerwartete Anfrage: ${method} ${url}`))
    }),
  )
}

function renderPage(antworten: Antworten = {}, pfad = '/projects/5/nachtlauf') {
  stubFetch(antworten)
  return render(
    <ThemeProvider theme={theme}>
      <SnackbarProvider>
        <MemoryRouter initialEntries={[pfad]}>
          <Routes>
            <Route path="/projects/:projectId/nachtlauf" element={<NightRunPage />} />
          </Routes>
        </MemoryRouter>
      </SnackbarProvider>
    </ThemeProvider>,
  )
}

/** Wählt eine Datei im versteckten Datei-Input aus — im Regelfall den Ergebnisstand eines Laufs. */
function protokollWaehlen(inhalt: string, name = 'night-run.json', typ = 'application/json') {
  const input = screen.getByLabelText('Protokolldatei auswählen')
  const datei = new File([inhalt], name, { type: typ })
  Object.defineProperty(input, 'files', { value: [datei], configurable: true })
  fireEvent.change(input)
}

/** Das Panel eines Laufs, identifiziert über seinen Startzeitpunkt. */
const lauf = (minute: number) => screen.getByTestId(`lauf-${startedAt(minute)}`)

/**
 * Die Kopfzeile eines Laufs. Bis #915 war sie eine Reihe eigener Chips, bis #988 trugen die beiden
 * Altbestand-Arten sie weiter; seither hat **jede** Lauf-Art den Kopf der Vorlage
 * `docs/mockup-nachtlauf-lauf.html` — Etikett, Titel, Metazeile und die Marken rechts. Geprüft wird
 * deshalb der ganze Kopf, nicht nur seine Metazeile: Herkunft und Einlieferung stehen als Marken
 * daneben, nicht mehr in der Zeile.
 */
const laufKopfzeile = (panelEl: HTMLElement): HTMLElement =>
  within(panelEl).getByTestId('lauf-kopf')

/**
 * Das Symbol der Lauf-Art im Kopf eines Laufs (#1141) — es steht seit diesem Paket anstelle der
 * Textmarke `lauf-art` aus #1128, unmittelbar vor dem Titel. Geprüft wird sein zugänglicher Name:
 * Das Wort ist nicht verschwunden, es steht nur nicht mehr als breitenwechselnde Marke da.
 */
const artSymbol = (bereich: HTMLElement): HTMLElement => within(bereich).getByTestId(/^art-/)

/** Die Metazeile des einzigen Laufs auf der Seite — dort steht seit #915 seine Kennzeichnung. */
const metazeile = () => screen.getByTestId('nachtlauf-meta')

/**
 * Die Fußzeile eines Laufs. Bis #988 standen die Kennzahlen des Ergebnisstands als eigene Reihe
 * über den Vorgängen; dort tragen seither die sechs Instrumente der Vorlage den Verbrauch, und die
 * Auskünfte des Stands stehen in der Fußzeile — dieselben Werte, ein anderer Ort.
 */
const FUSSZEILE = 'uebersicht-fuss'

/** Eine einzelne Angabe der Fußzeile, über ihre Benennung. */
const fussangabe = (panelEl: HTMLElement, label: string) =>
  within(panelEl).getByTestId(`fussangabe-${label}`)

/**
 * Klappt den Lauf auf; erst dabei wird die Herkunftskette aufgelöst (Plan #718, A8).
 *
 * Der **oberste** Lauf steht seit #914 beim Öffnen der Seite bereits offen (E7) — für ihn ist
 * nichts zu tun. Der Helfer bleibt deshalb still, statt auf einen zugeklappten Knopf zu warten,
 * den es nicht mehr gibt.
 */
function aufklappen(minute: number) {
  panelAufklappen(lauf(minute))
}

/**
 * Dasselbe für ein Panel, das der Test bereits in der Hand hat — und seit #988 zusätzlich jede
 * Vorgangszeile darin.
 *
 * Genommen wird der **erste** Knopf des Panels: Der Aufklapp-Pfeil steht im Kopf und damit im DOM
 * vor dem Inhalt. Danach klappt jede Vorgangszeile auf: Die Vorlage zeigt in der Zeile nur LED,
 * Nummer, Titel, Dauer, Kosten und Commit; Befund, Herkunftskette, Vorhaben, Verlauf und
 * Ergebniszeile stehen darunter (Entscheidung Manne 2026-09-17). Die Tests prüfen diese Angaben —
 * also müssen sie sie erst sichtbar machen. Wer die **Zeile selbst** prüft, klappt nicht auf.
 */
function panelAufklappen(panelEl: HTMLElement) {
  const knopf = within(panelEl).getAllByRole('button')[0]
  if (knopf.getAttribute('aria-expanded') === 'false') {
    fireEvent.click(knopf)
  }
  vorgaengeAufklappen(panelEl)
}

/**
 * Der Aufklapp-Pfeil im Kopf eines Laufs — der **erste** Knopf des Panels. Ein aufgeklappter Lauf
 * trägt weitere Knöpfe mit `aria-expanded`: die Vorgangszeilen und die Grund-Zeilen der
 * Aufschlüsselung. Eine Suche nach „dem aufgeklappten Knopf" träfe die mit.
 */
const laufTaste = (panelEl: HTMLElement) => within(panelEl).getAllByRole('button')[0]

/** Klappt jede noch zugeklappte Vorgangszeile eines Panels auf. */
function vorgaengeAufklappen(panelEl: HTMLElement) {
  for (const taste of within(panelEl).queryAllByTestId(/^vorgang-taste-\d+$/)) {
    if (taste.getAttribute('aria-expanded') === 'false') {
      fireEvent.click(taste)
    }
  }
}

/**
 * Der Text des Befunds eines Arbeitspakets im Panel eines Laufs. Seit #988 ein `<pre>` statt eines
 * schreibgeschützten Textfelds (Vorlage Z. 420–423); die Beschriftung ist dieselbe geblieben, weil
 * die Aussage dieselbe ist.
 */
const uebernahmetext = (panel: HTMLElement, cardNumber: number) =>
  within(panel).getByLabelText(`Übernahmetext zu Karte #${cardNumber}`).textContent

const byNumberAufrufe = () => anfragen.filter((a) => a.url.includes('/cards/by-number/'))

/** Die Einzelabrufe einer Karte über ihre ID — in dieser Seite ausschließlich die Vorhaben. */
const vorhabenAufrufe = () => anfragen.filter((a) => /^\/api\/cards\/\d+$/.test(a.url))

const zaehlerAufrufe = () => anfragen.filter((a) => a.url.endsWith('/night-runs/error-class-counts'))

/**
 * Die Uhr der Seite steht vor allen Fixtures (Issue #1134): Die Liste zeigt standardmäßig nur die
 * letzten zwei Zyklen, und die Läufe der Tests liegen im September 2026. Gefälscht wird allein
 * `Date` — Zeitgeber laufen echt, sonst warteten die `findBy…`-Abfragen vergeblich.
 */
const UHR_VOR_DEN_FIXTURES = new Date('2026-09-01T00:00:00Z')

beforeEach(() => {
  anfragen = []
  vi.clearAllMocks()
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(UHR_VOR_DEN_FIXTURES)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('NightRunPage — ungültige Projekt-ID', () => {
  it('meldet eine ungültige Projekt-ID und lädt nichts', async () => {
    renderPage({}, '/projects/abc/nachtlauf')

    expect(await screen.findByText('Ungültige Projekt-ID.')).toBeInTheDocument()
    expect(anfragen).toHaveLength(0)
  })

  it('behandelt einen fehlenden Projekt-Parameter als ungültig', () => {
    stubFetch({})
    render(
      <MemoryRouter initialEntries={['/nachtlauf']}>
        <Routes>
          <Route path="/nachtlauf" element={<NightRunPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Ungültige Projekt-ID.')).toBeInTheDocument()
  })
})

describe('NightRunPage — Herkunft, Vollständigkeit und Verbrauch eines Laufs', () => {
  it('nennt die maschinelle Herkunft samt Zeitpunkt und Token-Namen', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            origin: 'TOKEN',
            tokenName: 'nachtlauf-kette',
            createdAt: '2026-09-02T06:00:00.000Z',
          }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    const kopf = laufKopfzeile(lauf(0))
    expect(kopf).toHaveTextContent('maschinell eingeliefert')
    expect(kopf).toHaveTextContent('nachtlauf-kette')
    expect(kopf).toHaveTextContent(new Date('2026-09-02T06:00:00.000Z').toLocaleString('de-DE'))
  })

  it('nennt bei einem hochgeladenen Lauf stattdessen den Upload', async () => {
    renderPage({ listen: [[aufbewahrt({ id: 1, startedAt: startedAt(0), origin: 'UPLOAD' })]] })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    const kopf = laufKopfzeile(lauf(0))
    expect(kopf).toHaveTextContent('hochgeladen am')
    expect(kopf).not.toHaveTextContent('maschinell eingeliefert')
  })

  it('zeigt den Zeitpunkt der letzten Meldung nur, wenn er vom Anlegen abweicht', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            createdAt: '2026-09-02T06:00:00.000Z',
            updatedAt: '2026-09-02T06:00:00.000Z',
          }),
          aufbewahrt({
            id: 2,
            startedAt: startedAt(30),
            createdAt: '2026-09-02T06:00:00.000Z',
            updatedAt: '2026-09-02T07:30:00.000Z',
          }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    expect(laufKopfzeile(lauf(0))).not.toHaveTextContent('zuletzt gemeldet')
    const spaeter = laufKopfzeile(lauf(30))
    expect(spaeter).toHaveTextContent('zuletzt gemeldet')
    expect(spaeter).toHaveTextContent(new Date('2026-09-02T07:30:00.000Z').toLocaleString('de-DE'))
  })

  it('kennzeichnet einen unvollständig gemeldeten Lauf', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({ id: 1, startedAt: startedAt(0), complete: false }),
          aufbewahrt({ id: 2, startedAt: startedAt(30), complete: true }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('unvollständig')
    expect(laufKopfzeile(lauf(30))).not.toHaveTextContent('unvollständig')
  })

  it('sagt „nicht gemessen", wo kein Verbrauch aufbewahrt ist — und schreibt nirgends 0', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            usage: null,
            items: [{ id: 1, cardNumber: 700, title: 'Paket A', state: 'GREEN', usage: null }],
          }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    // Seit #988 tragen die Instrumente den Verbrauch des Laufs und die Kostenspalte den des
    // Vorgangs. „Nicht gemessen" steht dort als „—", der Grund nur für Vorlesewerkzeuge — und
    // nirgends eine 0 (`CLAUDE-design.md`).
    const panel = within(lauf(0))
    for (const kennung of ['instrument-kosten', 'instrument-eingabe', 'instrument-ausgabe']) {
      const wert = (await panel.findByTestId(`${kennung}-wert`)) as HTMLElement
      expect(wert).toHaveTextContent('—')
      expect(wert).toHaveTextContent('nicht gemessen')
      expect(wert.textContent).not.toMatch(/\d/)
    }
    const quote = panel.getByTestId('instrument-cache-wert')
    expect(quote).toHaveTextContent('nicht berechenbar')
    const kosten = panel.getByTestId('kosten-700')
    expect(kosten).toHaveTextContent('—')
    expect(kosten).toHaveTextContent('nicht gemessen')
    expect(kosten.textContent).not.toMatch(/\d/)
  })

  it('zeigt gesetzte Verbrauchswerte mit ihrer Einheit', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            usage: verbraucht({
              costUsd: 25.98,
              inputTokens: 1_000_000,
              outputTokens: 62411,
              cachedInputTokens: 760_000,
            }),
            items: [
              {
                id: 1,
                cardNumber: 700,
                title: 'Paket A',
                state: 'GREEN',
                usage: verbraucht({ costUsd: 11.52 }),
              },
            ],
          }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    const panel = within(lauf(0))
    expect(await panel.findByTestId('instrument-kosten-wert')).toHaveTextContent('25,98 $')
    // Die Instrumente nennen die Mengen in der Einheit der Vorlage („1,00 Mio", „62 Tsd").
    expect(panel.getByTestId('instrument-eingabe-wert')).toHaveTextContent('1,00 Mio')
    expect(panel.getByTestId('instrument-ausgabe-wert')).toHaveTextContent('62 Tsd')
    // Die Cache-Quote ist der Anteil des Zwischenspeichers an der Eingabe — dieselbe Formel, die
    // der Server für die Verbrauchs-Sicht rechnet.
    expect(panel.getByTestId('instrument-cache-wert')).toHaveTextContent('76 %')
    expect(panel.getByTestId('kosten-700')).toHaveTextContent('11,52 $')
  })

  it('nennt die maschinelle Herkunft auch ohne Token-Namen', async () => {
    // Die Spalte ist nullbar: Ein Lauf aus der Zeit vor dem Namensfeld trägt keinen. Dann steht
    // die Herkunft ohne Klammer da — und nicht „(Token: undefined)".
    renderPage({
      listen: [[aufbewahrt({ id: 1, startedAt: startedAt(0), origin: 'TOKEN', tokenName: null })]],
    })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    const kopf = laufKopfzeile(lauf(0))
    expect(kopf).toHaveTextContent('maschinell eingeliefert am')
    expect(kopf).not.toHaveTextContent('Token:')
  })

  it('meldet fehlende Kosten neben gemessenen Mengen als Fehlanzeige', async () => {
    // Der Runner kann Mengen melden, ohne die Kosten zu kennen — dann steht an der einen Stelle
    // eine Zahl und an der anderen die Fehlanzeige, nicht 0 $.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            usage: verbraucht({ inputTokens: 148, outputTokens: 62411 }),
          }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    const panel = within(lauf(0))
    expect(await panel.findByTestId('instrument-kosten-wert')).toHaveTextContent('nicht gemessen')
    expect(panel.getByTestId('instrument-eingabe-wert')).toHaveTextContent('148 Token')
    // Ohne Zwischenspeicher gibt es kein Verhältnis — und keine 0 %.
    expect(panel.getByTestId('instrument-cache-wert')).toHaveTextContent('nicht berechenbar')
  })

  it('zeigt die Lauf-Summe unverändert, auch wenn sie über der Summe der Arbeitspakete liegt', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            // 25,98 am Lauf gegen 11,52 am einzigen Paket: Die Differenz ist der keinem Paket
            // zuordenbare Rest, und genau er verschwände, wenn die Anzeige selbst summierte.
            usage: verbraucht({ costUsd: 25.98 }),
            items: [
              {
                id: 1,
                cardNumber: 700,
                title: 'Paket A',
                state: 'GREEN',
                usage: verbraucht({ costUsd: 11.52 }),
              },
            ],
          }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    expect(await within(lauf(0)).findByTestId('instrument-kosten-wert')).toHaveTextContent('25,98 $')
    expect(within(lauf(0)).getByTestId('kosten-700')).toHaveTextContent('11,52 $')
  })

  it('nennt die Kosten des zugeklappten Laufs im Kopf und aufgeklappt im Instrument', async () => {
    // Vorlage Z. 452 gegen Z. 393: Die Kosten stehen zugeklappt als Marke im Kopf, aufgeklappt im
    // Instrument — zweimal dieselbe Zahl im Blick ließe nach einem Unterschied suchen.
    renderPage({
      listen: [
        [
          aufbewahrt({ id: 1, startedAt: startedAt(0), usage: verbraucht({ costUsd: 9.8 }) }),
          aufbewahrt({ id: 2, startedAt: startedAt(30) }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    // Der spätere Lauf steht oben und offen; `lauf(0)` ist damit der zugeklappte.
    expect(within(lauf(0)).getByTestId('lauf-kosten')).toHaveTextContent('9,80 $')
    expect(within(lauf(0)).queryByTestId('instrument-kosten')).not.toBeInTheDocument()

    fireEvent.click(within(lauf(0)).getAllByRole('button')[0])

    expect(within(lauf(0)).queryByTestId('lauf-kosten')).not.toBeInTheDocument()
    expect(within(lauf(0)).getByTestId('instrument-kosten-wert')).toHaveTextContent('9,80 $')
  })
})

describe('NightRunPage — aufbewahrte Läufe beim Öffnen', () => {
  it('zeigt die aufbewahrten Läufe, neueste zuerst', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({ id: 1, startedAt: startedAt(0) }),
          aufbewahrt({ id: 2, startedAt: startedAt(30) }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    const reihenfolge = screen.getAllByTestId(/^lauf-\d/).map((el) => el.dataset.testid)
    expect(reihenfolge).toEqual([`lauf-${startedAt(30)}`, `lauf-${startedAt(0)}`])
  })

  it('löst beim Öffnen der Seite nur die Kette des obersten Laufs auf (A8, E7)', async () => {
    // A8 hält die Anfragelawine von bis zu 190 aufbewahrten Läufen fern. Seit #914 steht der
    // oberste Lauf offen (AK 2 verlangt den ersten Vorgangsblock ohne Scrollen) — genau er, und
    // deshalb bleibt es bei einer Kette statt einer je Lauf.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
          }),
          aufbewahrt({
            id: 2,
            startedAt: startedAt(30),
            items: [{ id: 21, cardNumber: 701, title: 'Paket B', state: 'GREEN' }],
          }),
        ],
      ],
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A' }),
        701: karte({ id: 2, number: 701, title: 'Paket B' }),
      },
    })

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    await waitFor(() => expect(byNumberAufrufe()).toHaveLength(1))
    expect(byNumberAufrufe()[0].url).toContain('/cards/by-number/701')
  })

  it('sagt es, solange keine Auswertung vorliegt', async () => {
    renderPage()

    expect(await screen.findByText('Noch keine Auswertung vorhanden.')).toBeInTheDocument()
  })

  it('meldet einen Fehler beim Laden der aufbewahrten Runs', async () => {
    renderPage({ listenFehler: 'Nur der Owner darf die Auswertung sehen.' })

    expect(await screen.findByText('Nur der Owner darf die Auswertung sehen.')).toBeInTheDocument()
  })
})

describe('NightRunPage — Verbrauch (Issue #941)', () => {
  it('zeigt den Verbrauchs-Bereich auf der bestehenden Seite, im Theme-Teilbaum des Entwurfs', async () => {
    renderPage()

    const bereich = await screen.findByTestId('verbrauch-bereich')
    expect(within(bereich).getByRole('heading', { level: 2, name: 'Verbrauch' })).toBeInTheDocument()
    expect(await within(bereich).findByTestId('verbrauch-nacht')).toHaveTextContent('2 Runs')
    expect(anfragen.map((a) => a.url)).toContain('/api/projects/5/night-run-usage/night?date=2026-09-15&zone=' + encodeURIComponent(Intl.DateTimeFormat().resolvedOptions().timeZone))
  })

  it('erreicht vom Zeitraum aus eine einzelne Nacht und stellt die Nachtansicht um (Issue #942, AK 8)', async () => {
    renderPage()
    const bereich = await screen.findByTestId('verbrauch-bereich')
    // Die Überschrift der Nachtansicht; die Zeitraum-Sicht darüber nennt ihren Zeitraum seit #987
    // in der Kopfzeile und nicht mehr als Überschrift.
    const nachtUeberschrift = async () =>
      within(await within(bereich).findByTestId('verbrauch-nacht')).getByRole('heading', { level: 3 })
    expect(await nachtUeberschrift()).toHaveTextContent('Schicht vom 15.09.2026 auf den 16.09.2026')

    fireEvent.click(await within(bereich).findByRole('button', { name: /Schicht vom 10\.09\.2026/ }))

    await waitFor(async () =>
      expect(await nachtUeberschrift()).toHaveTextContent('Schicht vom 10.09.2026 auf den 11.09.2026'),
    )
    expect(anfragen.some((a) => a.url.includes('/night-run-usage/night?date=2026-09-10&'))).toBe(true)
  })

  it('legt keine neue Route an: App.tsx fuehrt fuer die Seite nur /projects/:projectId/nachtlauf', () => {
    const routen = [...appQuelle.matchAll(/path="([^"]+)"\s+element=\{<NightRunPage \/>\}/g)].map(
      (treffer) => treffer[1],
    )

    expect(routen).toEqual(['/projects/:projectId/nachtlauf'])
    expect(appQuelle).not.toMatch(/verbrauch|night-run-usage/i)
  })
})

describe('NightRunPage — Ergebnisstand hineingeben', () => {
  it('sendet die Auswertung und stellt den neuen Lauf nach oben', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(EIN_LAUF) },
      listen: [
        [aufbewahrt({ id: 9, startedAt: '2026-08-30T22:00:00.000Z' })],
        [aufbewahrt({ id: 9, startedAt: '2026-08-30T22:00:00.000Z' }), ...wieAufbewahrt(EIN_LAUF)],
      ],
    })
    await screen.findByTestId('lauf-2026-08-30T22:00:00.000Z')

    protokollWaehlen(EIN_LAUF)

    await waitFor(() => expect(screen.getAllByTestId(/^lauf-\d/)).toHaveLength(2))
    expect(screen.getAllByTestId(/^lauf-\d/)[0].dataset.testid).toBe(`lauf-${startedAt(0)}`)
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('neu angelegt')
  })

  it('schickt die Auswertung hinaus, nie den Ergebnisstand selbst', async () => {
    renderPage({ submit: { ergebnis: [{ startedAt: startedAt(0), created: true }] } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    await waitFor(() => expect(anfragen.some((a) => a.method === 'POST')).toBe(true))
    for (const anfrage of anfragen) {
      expect(anfrage.url).not.toContain(GEHEIM)
      expect(anfrage.body).not.toContain(GEHEIM)
    }
    const gesendet = anfragen.find((a) => a.method === 'POST')
    expect(gesendet?.body).toContain('"cardNumber":700')
  })

  it('nimmt den gemeldeten Kostenwert mit — am Lauf und am Arbeitspaket', async () => {
    renderPage({ submit: { ergebnis: [{ startedAt: startedAt(0), created: true }] } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(MIT_KOSTEN)

    await waitFor(() => expect(anfragen.some((a) => a.method === 'POST')).toBe(true))
    const gesendet = JSON.parse(anfragen.find((a) => a.method === 'POST')!.body)
    expect(gesendet.runs[0].usage).toEqual({ costUsd: 25.983293 })
    expect(gesendet.runs[0].items[0].usage).toEqual({ costUsd: 11.5228115 })
  })

  it('lässt den usage-Schlüssel weg, wo kein Kostenwert gemeldet ist', async () => {
    renderPage({ submit: { ergebnis: [{ startedAt: startedAt(0), created: true }] } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    await waitFor(() => expect(anfragen.some((a) => a.method === 'POST')).toBe(true))
    const gesendet = JSON.parse(anfragen.find((a) => a.method === 'POST')!.body)
    // Kein `usage: undefined`, sondern gar kein Schlüssel — dieselbe Regel wie bei den übrigen
    // optionalen Feldern: Was nicht gemessen wurde, steht nicht im Body.
    expect('usage' in gesendet.runs[0]).toBe(false)
    expect('usage' in gesendet.runs[0].items[0]).toBe(false)
  })

  it('trägt den Kostenwert des echten Ketten-Laufs in den Request', async () => {
    renderPage({ submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    await waitFor(() => expect(anfragen.some((a) => a.method === 'POST')).toBe(true))
    const gesendet = JSON.parse(anfragen.find((a) => a.method === 'POST')!.body)
    expect(gesendet.runs[0].usage.costUsd).toBe(25.983292999999996)
    // Die drei Ketten-Vorgänge tragen je ihren eigenen Betrag.
    expect(gesendet.runs[0].items.map((i: { usage?: { costUsd: number } }) => i.usage?.costUsd))
      .toEqual([11.5228115, 10.366864999999999, 4.093616499999999])
  })

  it('stellt einen bereits bekannten Lauf vollständig dar und kennzeichnet ihn', async () => {
    renderPage({
      submit: { ergebnis: [{ startedAt: startedAt(0), created: false }] },
      listen: [[], wieAufbewahrt(EIN_LAUF)],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('lag schon vor')
    aufklappen(0)
    expect(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ })).toBeInTheDocument()
  })

  it('deutet den echten Ergebnisstand eines Laufs Paket für Paket', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_STAND, 'night-run-2026-09-07-085229.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTER_START}`)
    const panel = within(panelEl)
    panelAufklappen(panelEl)
    await panel.findByTestId('zustand-767')
    // Zwei erfolgreiche, aber ungeprüfte Sessions (gelb) und drei mit rotem Nachweis (rot) —
    // Zustand und Fehlerklasse stehen beide sichtbar auf der Seite.
    for (const nummer of [767, 770]) {
      expect(within(panel.getByTestId(`zustand-${nummer}`)).getByText('Erfolg, Prüfung rot')).toBeInTheDocument()
      expect(uebernahmetext(panelEl, nummer)).toContain('Fehlerklasse: Prüfungen nicht gelaufen')
    }
    for (const nummer of [768, 769, 771]) {
      expect(within(panel.getByTestId(`zustand-${nummer}`)).getByText('gescheitert')).toBeInTheDocument()
      expect(uebernahmetext(panelEl, nummer)).toContain('Fehlerklasse: Prüfungen rot')
    }
  })

  it('meldet eine Textdatei als nicht auswertbar, statt sie zu deuten', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(
      '[2026-09-01T22:00:00.000Z] Nacht-Runner startet (Modus Implementierung)',
      'nacht.log',
      'text/plain',
    )

    // Ohne geparstes Objekt gibt es weder ein Wort noch eine Herkunft — der Grundsatz steht
    // allein, die fehlende Herkunft wird benannt statt verschwiegen.
    expect(
      await screen.findByText('Nicht auswertbar — Herkunft nicht angegeben'),
    ).toBeInTheDocument()
    expect(screen.queryAllByTestId(/^lauf-\d/)).toHaveLength(0)
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('meldet eine unbekannte Fassung, statt sie zu deuten', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(stand({ schemaFassung: 2 }))

    // „Fassung" bezeichnet hier den Aufbau des Protokolls; die Herkunft steht daneben als
    // „erzeugt von" und übernimmt das Wort nicht (AK 10 aus #842, E11 aus Plan #849).
    expect(
      await screen.findByText('Fassung nicht unterstützt — erzeugt von 1.47.0'),
    ).toBeInTheDocument()
    expect(screen.queryAllByTestId(/^lauf-\d/)).toHaveLength(0)
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  // Der Prüf-Lauf selbst ist seit Issue #816 deutbar; nicht unterstützt bleibt eine
  // Kombination, die der Runner nie schreibt — hier eine unbekannte Prüfstufe.
  it('meldet eine unbekannte Lauf-Art/Stufe als nicht unterstützt', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(stand({ art: 'review', stufe: 'sonstwas' }))

    expect(
      await screen.findByText(
        'Art des Runs oder Vokabular nicht unterstützt — nicht gedeutet: art=review/stufe=sonstwas'
          + ' — erzeugt von 1.47.0',
      ),
    ).toBeInTheDocument()
    expect(screen.queryAllByTestId(/^lauf-\d/)).toHaveLength(0)
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  // AK 10 aus #842: Die Meldung nennt das unbekannte Wort selbst — hier den Ausgang einer
  // Einheit, nicht die Lauf-Art.
  it('nennt den nicht gedeuteten Ausgang einer Einheit in der Meldung', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(stand({ einheiten: [einheit({ ausgang: 'halbfertig' })] }))

    expect(
      await screen.findByText(
        'Art des Runs oder Vokabular nicht unterstützt — nicht gedeutet: halbfertig'
          + ' — erzeugt von 1.47.0',
      ),
    ).toBeInTheDocument()
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('sagt „Herkunft nicht angegeben", wenn der Stand kein erzeugtVon führt', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    const ohneHerkunft = JSON.parse(stand({ art: 'review', stufe: 'sonstwas' })) as Record<
      string,
      unknown
    >
    delete ohneHerkunft.erzeugtVon
    protokollWaehlen(JSON.stringify(ohneHerkunft))

    expect(
      await screen.findByText(
        'Art des Runs oder Vokabular nicht unterstützt — nicht gedeutet: art=review/stufe=sonstwas'
          + ' — Herkunft nicht angegeben',
      ),
    ).toBeInTheDocument()
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('zeigt einen noch nicht abgeschlossenen Lauf an, liefert ihn aber nicht ein', async () => {
    // Der Server legt je (Projekt, Startzeit) nur einmal an — ein unvollständiger Stand blockierte
    // den späteren vollständigen dauerhaft.
    renderPage({ karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(stand({ abschluss: null, einheiten: [einheit({ ausgang: 'erfolg', pruefung: GEPRUEFT })] }))

    expect(
      await screen.findByText('Run noch nicht abgeschlossen — nicht gespeichert'),
    ).toBeInTheDocument()
    expect(lauf(0)).toBeInTheDocument()
    aufklappen(0)
    expect(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ })).toBeInTheDocument()
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('meldet eine unlesbare Datei, ohne abzustürzen', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')
    class FailingReader {
      onload: (() => void) | null = null
      onerror: (() => void) | null = null
      result: string | null = null
      readAsText(): void {
        this.onerror?.()
      }
    }
    vi.stubGlobal('FileReader', FailingReader)

    protokollWaehlen(EIN_LAUF)

    expect(await screen.findByText('Die Datei konnte nicht gelesen werden.')).toBeInTheDocument()
    expect(screen.queryAllByTestId(/^lauf-\d/)).toHaveLength(0)
  })

  it('öffnet nichts, wenn die Auswahl abgebrochen wurde', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')
    const input = screen.getByLabelText('Protokolldatei auswählen')
    Object.defineProperty(input, 'files', { value: null, configurable: true })

    fireEvent.change(input)

    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('setzt den Datei-Input zurück, damit derselbe Stand erneut gewählt werden kann', async () => {
    renderPage({ submit: { ergebnis: [{ startedAt: startedAt(0), created: true }] } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    expect((screen.getByLabelText('Protokolldatei auswählen') as HTMLInputElement).value).toBe('')
  })

  it('lässt die Auswertung sichtbar, wenn das Senden scheitert, und nennt den Grund', async () => {
    renderPage({
      submit: { fehler: 'Auszug zu lang' },
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    expect(await screen.findByText('Auszug zu lang')).toBeInTheDocument()
    expect(lauf(0)).toBeInTheDocument()
    // Der Zwischenspeicher wird **vor** dem Senden gefüllt (#775): Auch ein Lauf, dessen
    // Einlieferung scheitert, ist aus dem Ergebnisstand entstanden und sagt das.
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('Ergebnisstand')
    aufklappen(0)
    expect(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ })).toBeInTheDocument()
  })

  it('zeigt statt eines Roh-Bodys ohne Server-Meldung den eigenen Text', async () => {
    // 502 vom Reverse-Proxy: Der Body ist eine HTML-Seite, keine für den Nutzer formulierte
    // Meldung — sie gehört nicht in den Toast (Issue #812).
    renderPage({ submit: { rohText: '<html><body>502 Bad Gateway</body></html>' } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    expect(await screen.findByText('Einliefern fehlgeschlagen.')).toBeInTheDocument()
    expect(screen.queryByText(/502 Bad Gateway/)).not.toBeInTheDocument()
  })

  it('nennt bei einem Netzwerkabbruch den eigenen Text', async () => {
    renderPage({ submit: { netzfehler: true } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    expect(await screen.findByText('Einliefern fehlgeschlagen.')).toBeInTheDocument()
  })

  /**
   * Review-Fund (Code-Review Schritt 7, #807-#812-Batch): Einliefern und das anschließende
   * Nachladen der Liste (plus Zähler) standen im selben try/catch. Scheitert nur das Nachladen,
   * ist die Einlieferung trotzdem durch — „Einliefern fehlgeschlagen" wäre dann falsch.
   */
  it('meldet ein gescheitertes Nachladen getrennt vom Einliefern, wenn die Einlieferung erfolgreich war', async () => {
    let listRufe = 0
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, init?: RequestInit) => {
        const method = init?.method ?? 'GET'
        anfragen.push({ url, method, body: String(init?.body ?? '') })
        if (url === '/api/projects') {
          return Promise.resolve(antwortOk([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }]))
        }
        if (url === '/api/projects/5/night-runs' && method === 'GET') {
          listRufe += 1
          // Erster Aufruf (Erstladen) klappt, der zweite (Nachladen nach dem Einliefern) scheitert.
          if (listRufe === 1) return Promise.resolve(antwortOk([]))
          return Promise.reject(new TypeError('Failed to fetch'))
        }
        if (url === '/api/projects/5/night-runs' && method === 'POST') {
          return Promise.resolve(antwortOk(alleNeu(EIN_LAUF)))
        }
        return Promise.reject(new Error(`unerwartete Anfrage: ${method} ${url}`))
      }),
    )

    render(
      <ThemeProvider theme={theme}>
        <SnackbarProvider>
          <MemoryRouter initialEntries={['/projects/5/nachtlauf']}>
            <Routes>
              <Route path="/projects/:projectId/nachtlauf" element={<NightRunPage />} />
            </Routes>
          </MemoryRouter>
        </SnackbarProvider>
      </ThemeProvider>,
    )
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    expect(
      await screen.findByText('Eingeliefert, Liste konnte nicht aktualisiert werden.'),
    ).toBeInTheDocument()
    // Der lokal aus dem Ergebnisstand erzeugte Lauf-Eintrag bleibt trotzdem sichtbar.
    expect(lauf(0)).toBeInTheDocument()
  })
})

describe('NightRunPage — Ausnahme auch im Dunkeln (#954)', () => {
  it('setzt am Wurzelknoten der Seite die Hellwerte und malt ihren Grund selbst', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    // jsdom wertet `prefers-color-scheme` nicht aus; geprüft wird deshalb die erzeugte Regel am
    // Wurzelknoten. Sie fällt, sobald die Variablen von `:root` durchschlagen könnten.
    const regel = cssRegel(screen.getByTestId('nachtlauf-wurzel'))
    expect(regel).toContain(`--mb-palette-background-paper: ${theme.colorSchemes.light!.palette.background.paper}`)
    expect(regel).toContain(`--mb-palette-divider: ${theme.colorSchemes.light!.palette.divider}`)
    expect(regel).toContain('color-scheme: light')
    expect(regel).toContain(`background-color: ${NACHTLAUF_FARBEN.ground}`)
  })
})

describe('NightRunPage — Nachtplan-Lauf (#806)', () => {
  it('zeigt den echten Nachtplan-Lauf mit eigenem Chip und den erwarteten Zuständen, liefert ihn aber nicht ein', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_NACHTPLAN_STAND, 'night-run-2026-09-09-141506.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTER_NACHTPLAN_START}`)
    expect(artSymbol(panelEl)).toHaveAccessibleName('Nachtplan')
    expect(laufKopfzeile(panelEl)).toHaveTextContent('2 bearbeitet · 33 übergangen')

    panelAufklappen(panelEl)
    const panel = within(panelEl)
    await panel.findByTestId('zustand-479')
    for (const nummer of [479, 549]) {
      expect(within(panel.getByTestId(`zustand-${nummer}`)).getByText('gescheitert')).toBeInTheDocument()
    }
    expect(panel.getAllByText("Grund: kein Label 'kit:nightplan'")).toHaveLength(33)

    // Kein Einliefern: weder ein POST noch die Kennzeichnung "neu angelegt"/"lag schon vor".
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
    expect(within(panelEl).queryByText('neu angelegt')).not.toBeInTheDocument()
    expect(within(panelEl).queryByText('lag schon vor')).not.toBeInTheDocument()
  })

  it('zeigt einen unvollständigen Nachtplan-Lauf an, liefert ihn aber ebenfalls nicht ein', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(
      stand({
        art: 'erzeugung',
        stufe: 'plan',
        abschluss: null,
        einheiten: [einheit({ ausgang: 'uebersprungen', grund: "kein Label 'kit:nightplan'" })],
      }),
    )

    expect(
      await screen.findByText('Run noch nicht abgeschlossen — nicht gespeichert'),
    ).toBeInTheDocument()
    expect(lauf(0)).toBeInTheDocument()
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })
})

describe('NightRunPage — Ketten-Lauf (#854)', () => {
  it('zeigt den echten Ketten-Lauf mit eigenem Chip und liefert ihn ein', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    expect(artSymbol(panelEl)).toHaveAccessibleName('Kette')
    expect(laufKopfzeile(panelEl)).toHaveTextContent('3 bearbeitet · 0 übergangen')

    // Anders als der Nachtplan-Lauf geht die Kette an den Server (AK 8 aus #842):
    // `istEinlieferbar` schließt weiterhin allein `NIGHTPLAN` aus.
    await waitFor(() => expect(anfragen.some((a) => a.method === 'POST')).toBe(true))
    expect(anfragen.find((a) => a.method === 'POST')?.body).toContain('"mode":"CHAIN"')
    expect(laufKopfzeile(panelEl)).toHaveTextContent('neu angelegt')
  })
})

describe('NightRunPage — Herkunftskette am Ketten-Vorgang (#858)', () => {
  /**
   * Die Wurzelkarte eines Ketten-Vorgangs **ist** die fachliche Anforderung, und das erzeugte
   * Plan-Dokument hängt unterhalb von ihr. Aufwärts gedeutet stünde an #791 und #814 zweimal
   * „ohne" und an #842 zweimal „noch nicht erreicht" — obwohl #842 den Plan #849 gerade erzeugt
   * hat. Beides sind geratene Aussagen (zweites Nicht-Ziel aus #842); die Karten hier tragen
   * genau die Vorbedingungen dafür, damit der Test ohne die Modus-Weitergabe rot wird.
   */
  const KETTEN_KARTEN = {
    791: karte({ id: 1, number: 791, title: '[Fachlich] Zugriff und Konten' }),
    814: karte({ id: 2, number: 814, title: '[Fachlich] Vorhaben ein- und ausblenden' }),
    842: karte({ id: 3, number: 842, title: '[Fachlich] Leitstand mehrstufig', derivedFrom: 700 }),
    700: karte({ id: 4, number: 700, title: 'Leitstand' }),
  }

  it('lässt an jedem Vorgang des echten Ketten-Laufs beide Stufenzeilen weg', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
      karten: KETTEN_KARTEN,
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    await waitFor(() => expect(laufKopfzeile(panelEl)).toHaveTextContent('neu angelegt'))
    panelAufklappen(panelEl)

    // Erst wenn die Wurzelkarten aufgelöst sind, steht der Block mit Stufen- und Vorhabenzeile
    // überhaupt da — sonst wäre die Erwartung darunter schon vor dem Laden erfüllt.
    expect(await within(panelEl).findAllByText('Vorhaben: ohne')).toHaveLength(3)
    expect(within(panelEl).queryAllByText(/^Fachliche Anforderung: /)).toHaveLength(0)
    expect(within(panelEl).queryAllByText(/^Plan: /)).toHaveLength(0)
  })

  it('lässt beide Stufenzeilen an einem Lauf bisheriger Art unverändert stehen (AK 9 aus #842)', async () => {
    // Der Bestandsfall: Nur der Ketten-Modus verliert die Zeilen. Dieser Test sichert die
    // Nicht-Änderung — ein Umsetzung, die die Zeilen überall wegließe, fiele hier auf.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
          }),
        ],
      ],
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf', derivedFrom: 715 }),
        715: karte({ id: 3, number: 715, title: '[Fachlich] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(
      await within(lauf(0)).findByRole('button', { name: 'Plan #718 [Plan] Nachtlauf' }),
    ).toBeInTheDocument()
    expect(
      within(lauf(0)).getByRole('button', {
        name: 'Fachliche Anforderung #715 [Fachlich] Nachtlauf',
      }),
    ).toBeInTheDocument()
  })
})

describe('NightRunPage — Zeitbudget als eigener Grund (#856)', () => {
  /**
   * Der Vorgang #842 der echten Fixture endete am Zeitbudget der Prüf-Stufe und hat trotzdem einen
   * Plan hinterlassen — gelb mit `TIME_BUDGET_EXCEEDED`. Er ist der Fall, für den AK 3 und AK 5 aus
   * #842 geschrieben sind: Die Ampel allein sagte bisher „Erfolg, Prüfung rot", und die Prüfung war
   * nicht rot, sie kam gar nicht dran.
   */
  /**
   * Der Lauf wird **vom Server geladen**, nicht als Ergebnisstand hineingegeben: Seit Issue #869
   * kürzt die Seite die Vorgangszeile an jedem Lauf, zu dem ein Sitzungsstand vorliegt — und beide
   * Aussagen hier hängen an dieser Zeile. Ohne Sitzungsstand steht sie vollständig da, und das ist
   * genau der Fall, für den AK 3 und AK 5 aus #842 geschrieben sind. `gespeichert` ist an einem
   * geladenen Lauf `true`, die Häufigkeitszeile erscheint also weiterhin.
   */
  async function ketteAufgeklappt(zaehler: NightRunErrorClassCounts) {
    renderPage({ listen: [wieAufbewahrt(ECHTE_KETTE_STAND)], zaehler: [zaehler] })

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('zustand-842')
    return panelEl
  }

  it('nennt neben der Ampel des Zeitbudget-Vorgangs das Zeitbudget, nicht „Erfolg, Prüfung rot" (AK 3)', async () => {
    const panelEl = await ketteAufgeklappt({ TIME_BUDGET_EXCEEDED: 1 })
    const panel = within(panelEl)

    expect(
      within(panel.getByTestId('zustand-842')).getByText('Am Zeitbudget beendet, Ergebnis liegt vor'),
    ).toBeInTheDocument()
    expect(panel.queryByText('Erfolg, Prüfung rot')).not.toBeInTheDocument()
  })

  it('führt das Zeitbudget in Häufigkeitszeile und Übernahmetext als eigenen Grund (AK 5)', async () => {
    const panelEl = await ketteAufgeklappt({ TIME_BUDGET_EXCEEDED: 1, CHECKS_NOT_STARTED: 3 })

    const zeile = await within(panelEl).findByTestId('haeufigkeit-842')
    expect(zeile).toHaveTextContent('Zeitbudget erschöpft: zum ersten Mal')
    expect(zeile).not.toHaveTextContent('Prüfungen nicht gelaufen')

    const text = uebernahmetext(panelEl, 842)
    expect(text).toContain('Zustand: Am Zeitbudget beendet, Ergebnis liegt vor')
    expect(text).toContain('Fehlerklasse: Zeitbudget erschöpft')
    expect(text).not.toContain('Prüfungen nicht gelaufen')
  })
})

/**
 * Ein Ketten-Ergebnisstand ohne jede Kopfangabe — kein Modell, kein Label, kein Budget, keine
 * Kostensumme, ein Vorgang ohne Arbeitsschritte. Er ist die Gegenprobe zur echten Fixture: Jede
 * Angabe der Übersicht muss auch dann eine Auskunft ergeben, wenn der Stand sie nicht führt.
 */
const kettenStand = (felder: Record<string, unknown> = {}): string =>
  JSON.stringify({
    schemaFassung: 1,
    erzeugtVon: '1.53.0',
    start: startedAt(0),
    art: 'kette',
    stufe: null,
    einheiten: [{ id: '900', titel: 'Anforderung ohne Angaben', ausgang: 'fertig' }],
    abschluss: 'regulaer',
    ...felder,
  })

/**
 * Liest einen Ketten-Stand ein, wartet auf `warten` und klappt den Lauf auf.
 *
 * `extra` legt weitere Stub-Antworten darüber — seit Issue #868 braucht die Übersicht auch den
 * Kartenkatalog, und die Verweise auf die entstandenen Karten hängen an ihm.
 */
async function uebersichtZu(
  ergebnisstand: string,
  warten: string,
  start = startedAt(0),
  extra: Partial<Antworten> = {},
) {
  renderPage({
    submit: { ergebnis: alleNeu(ergebnisstand) },
    listen: [[], wieAufbewahrt(ergebnisstand)],
    ...extra,
  })
  await screen.findByText('Noch keine Auswertung vorhanden.')

  protokollWaehlen(ergebnisstand)

  const panelEl = await screen.findByTestId(`lauf-${start}`)
  // Zwei Orte, ein Warteschritt: Der Hinweis auf einen unabgeschlossenen Lauf steht als Meldung
  // über der Liste, die Kennzeichnung „neu angelegt" seit #915 in der Metazeile des Kopfes.
  await waitFor(() =>
    expect(screen.queryByText(warten) ?? laufKopfzeile(panelEl)).toHaveTextContent(warten),
  )
  panelAufklappen(panelEl)
  // Die Übersicht ist seit #918 ganz aufgegangen: Kopf und Kennzahlen stehen oben, die
  // Vorgänge als Blöcke, der Rest in der Fußzeile. Sie ist das letzte Element des Laufs.
  return within(panelEl).findByTestId('uebersicht-fuss')
}

/** Die Übersicht des echten Ketten-Laufs, nach dem Einliefern und dem Neuladen der Liste. */
const echteUebersicht = () => uebersichtZu(ECHTE_KETTE_STAND, 'neu angelegt', ECHTE_KETTE_START)

describe('NightRunPage — Ketten-Übersicht (#866)', () => {
  /**
   * Der Kopf nennt Datum und Uhrzeit des Starts. Beide werden hier mit denselben `Intl`-Angaben
   * erwartet, mit denen die Seite sie schreibt: Die Zeitzone des Prüfrechners steht nicht fest,
   * und ein fest eingetragener Text wäre anderswo rot, ohne dass etwas kaputt wäre.
   */
  const kopfzeile = (iso: string) =>
    `${new Date(iso).toLocaleDateString('de-DE', { day: 'numeric', month: 'long' })}, ${new Date(iso).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}`

  it('zeigt die Übersicht auch nach dem Einliefern und dem Neuladen der Liste (Gegenprobe zu E1)', async () => {
    // Die abweichende Stückzahl belegt, dass der sichtbare Lauf der **neu geladene** ist. Erst
    // danach sagt die Übersicht etwas darüber aus, dass sie den Austausch des Lauf-Arrays
    // überlebt — an `gespeichert` gebunden wäre sie hier bereits verschwunden.
    const vomServer = wieAufbewahrt(ECHTE_KETTE_STAND).map((view) => ({
      ...view,
      processedCount: 42,
    }))
    renderPage({ submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) }, listen: [[], vomServer] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    await waitFor(() =>
      expect(laufKopfzeile(panelEl)).toHaveTextContent('42 bearbeitet · 0 übergangen'),
    )
    panelAufklappen(panelEl)

    // Der Beleg ist jetzt das Stufenband: Es steht allein im Ergebnisstand, und dass es nach dem
    // Neuladen der Liste noch da ist, war die Aussage dieses Tests.
    expect(await within(panelEl).findByTestId('stufenband-791')).toBeInTheDocument()
  })

  it('zeigt an einem vom Server geladenen Ketten-Lauf ohne Sitzungsstand kein Band', async () => {
    renderPage({ listen: [wieAufbewahrt(ECHTE_KETTE_STAND)] })
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)

    panelAufklappen(panelEl)

    // Der Vorgang steht da, seine Arbeitsschritte nicht: Die bewahrt der Server nicht auf.
    expect(await within(panelEl).findByTestId('zustand-791')).toBeInTheDocument()
    expect(within(panelEl).queryByTestId('stufenband-791')).not.toBeInTheDocument()
    // Und ebenso wenig die Angaben, die allein der Stand hergibt: Ohne ihn bleibt die Fußzeile
    // bei den Budgets und nennt keine durchgelaufenen Ketten.
    expect(within(panelEl).queryByTestId('fussangabe-Ketten durchgelaufen')).not.toBeInTheDocument()
    expect(within(panelEl).getByTestId(FUSSZEILE)).toBeInTheDocument()
  })

  it('trägt im Kopf und in der Kennzahlenreihe die Werte des Protokolls (AK 1 bis 3)', async () => {
    // Kopf und Kennzahlen stehen seit #915 über der Übersicht statt in ihr — dieselben Werte,
    // ein anderer Ort.
    await echteUebersicht()

    expect(screen.getByTestId('nachtlauf-ueberschrift')).toHaveTextContent(kopfzeile(ECHTE_KETTE_START))
    expect(metazeile()).toHaveTextContent('claude-opus-5 · Label kit:night · regulär beendet')

    // Zwei der drei Vorgänge sind grün; entstanden sind #844 bis #849, also sechs Karten; die
    // Summe der zehn Stufenzeiten ergibt 4 148 331 ms; die Kostensumme steht im Kopf des Stands.
    const fuss = screen.getByTestId(FUSSZEILE)
    expect(fussangabe(fuss, 'Ketten durchgelaufen')).toHaveTextContent('2 von 3')
    expect(fussangabe(fuss, 'Karten entstanden')).toHaveTextContent('6')
    expect(fussangabe(fuss, 'Laufzeit über alle Stufen')).toHaveTextContent('1 Std 9 Min')
    expect(fussangabe(fuss, 'Kosten der Schicht')).toHaveTextContent('25,98 $')
    expect(fussangabe(fuss, 'Zur Kostensumme')).toHaveTextContent(
      'ein Arbeitsschritt ohne Kostenmeldung',
    )
  })

  it('nennt je Vorgang Kosten, Züge und fehlende Kostenmeldungen (AK 11)', async () => {
    // Die Werte stehen seit #916 in der Ergebniszeile des Vorgangsblocks, nicht mehr in der
    // Übersicht — dieselben Werte, ein anderer Ort.
    await echteUebersicht()

    expect(screen.getByTestId('paket-791')).toHaveTextContent('11,52 $ · 89 Züge')
    expect(screen.getByTestId('paket-814')).toHaveTextContent('10,37 $ · 87 Züge')
    expect(screen.getByTestId('paket-842')).toHaveTextContent(
      '4,09 $ · 37 Züge · ein Arbeitsschritt ohne Kostenmeldung',
    )
  })

  it('führt in der Fußzeile Zeitvorgaben, Kostenbudget und die höchsten Kosten (AK 12)', async () => {
    // `echteUebersicht` liefert seit #918 die Fußzeile selbst — sie ist, was von der Übersicht
    // übrig blieb.
    const fuss = within(await echteUebersicht())

    expect(
      fuss.getByText('Plan 20 · Prüfung 15 · Pakete 15 · Abdeckung 10 min'),
    ).toBeInTheDocument()
    expect(fuss.getByText('50,00 $')).toBeInTheDocument()
    expect(fuss.getByText('11,52 $')).toBeInTheDocument()
  })

  it('nennt einen Lauf ohne Abschlussangabe „noch nicht abgeschlossen" und keinen Rohwert', async () => {
    const fuss = within(
      await uebersichtZu(
        kettenStand({ abschluss: null }),
        'Run noch nicht abgeschlossen — nicht gespeichert',
      ),
    )

    expect(metazeile()).toHaveTextContent('noch nicht abgeschlossen')
    expect(fuss.queryByText(/null/)).not.toBeInTheDocument()
    // Ohne Vorgaben, Budget, Kostensumme und Kostenmeldung steht überall die ausdrückliche
    // Auskunft statt einer Zahl — eine „0 $" wäre eine Behauptung über etwas, das der Stand nicht
    // führt. Seit #918 kommt die Herkunft der Budgets dazu, seit #988 die Kostensumme der Nacht.
    expect(fuss.getAllByText('nicht angegeben')).toHaveLength(5)
    expect(screen.getByTestId('kennzahlen-900')).toHaveTextContent('Kosten nicht gemeldet')
  })

  it('nennt einen hart gestoppten Lauf vorzeitig beendet und zählt fehlende Kostenmeldungen', async () => {
    // Abschluss, Kostensumme und der Vermerk stehen seit #915 alle drei im Kopf über der
    // Übersicht; sie selbst wird hier nur noch abgewartet.
    await uebersichtZu(
      kettenStand({ abschluss: 'harterStopp', kostenSumme: 3.5, kostenUnbekannt: 2 }),
      'neu angelegt',
    )

    expect(metazeile()).toHaveTextContent('vorzeitig beendet (harter Stopp)')
    expect(screen.getByTestId(FUSSZEILE)).toHaveTextContent('3,50 $')
    expect(screen.getByTestId(FUSSZEILE)).toHaveTextContent('2 Arbeitsschritte ohne Kostenmeldung')
  })

  it('gibt eine unbekannte Abschlussangabe nicht im Rohwert aus', async () => {
    const uebersicht = within(
      await uebersichtZu(kettenStand({ abschluss: 'sonntagsruhe' }), 'neu angelegt'),
    )

    expect(metazeile()).toHaveTextContent('Abschluss nicht deutbar')
    expect(uebersicht.queryByText(/sonntagsruhe/)).not.toBeInTheDocument()
  })

  it('zeigt an einem Lauf anderer Art unverändert die Zeilendarstellung (AK 16)', async () => {
    renderPage({ submit: { ergebnis: alleNeu(EIN_LAUF) }, listen: [[], wieAufbewahrt(EIN_LAUF)] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    const panelEl = await screen.findByTestId(`lauf-${startedAt(0)}`)
    await waitFor(() => expect(laufKopfzeile(panelEl)).toHaveTextContent('neu angelegt'))
    panelAufklappen(panelEl)

    expect(await within(panelEl).findByTestId('zustand-700')).toBeInTheDocument()
    // Ein Lauf anderer Art führt kein Stufenband — er kennt keine Arbeitsschritte.
    expect(within(panelEl).queryByTestId('stufenband-700')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Stufenband je Vorgang (#867)', () => {
  /** Die vier Arbeitsschritte in der Reihenfolge, in der die Kette sie läuft. */
  const SCHRITTE = ['plan', 'review', 'pakete', 'abdeckung'] as const

  // Gesucht wird auf der Seite und nicht in der Übersicht: Die Vorgangsblöcke stehen seit #916
  // im Panel des Laufs. Die Testkennungen sind dieselben geblieben (E14), und je Test steht genau
  // ein Lauf auf der Seite.
  const abschnitt = (cardNumber: number, schritt: string) =>
    screen.getByTestId(`stufe-${cardNumber}-${schritt}`)

  /** Ein Datenfeld aller vier Abschnitte eines Vorgangs, in der Reihenfolge der Kette. */
  const bandwerte = (cardNumber: number, feld: 'anteil' | 'fuellung' | 'erreicht') =>
    SCHRITTE.map((schritt) => abschnitt(cardNumber, schritt).dataset[feld])

  /** Der Grund eines Abbruchs, der **nicht** am Zeitbudget lag. */
  const GRUND_KOSTEN = 'Kostenbudget erschöpft: 52,10 $ über 50,00 $'

  it('zeichnet das Band der Einheit 791 in den Verhältnissen der Zeitvorgaben (Punkt 8)', async () => {
    await echteUebersicht()

    // Geprüft an der Beschriftung und an den Datenfeldern, nicht an berechneten Stilwerten: In
    // jsdom rechnet kein Browser ein Layout aus, ein Test auf `flex`-Anteile prüfte dort die
    // Zeichenkette, die man selbst geschrieben hat.
    expect(screen.getByTestId('stufenband-791')).toHaveAttribute(
      'aria-label',
      'Stufenband: Plan 7,8 / 20 min · Prüfung 11,8 / 15 min · Pakete 3,7 / 15 min · Abdeckung 2,2 / 10 min',
    )
    expect(bandwerte(791, 'anteil')).toEqual(['20', '15', '15', '10'])
    expect(bandwerte(791, 'fuellung')).toEqual(['39.1', '78.6', '24.6', '22.4'])
    expect(bandwerte(791, 'erreicht')).toEqual(['ja', 'ja', 'ja', 'ja'])
  })

  it('kennzeichnet Pakete und Abdeckung der Einheit 842 als nicht erreicht (Punkt 9)', async () => {
    await echteUebersicht()

    expect(bandwerte(842, 'erreicht')).toEqual(['ja', 'ja', 'nein', 'nein'])
    expect(abschnitt(842, 'pakete')).toHaveTextContent('nicht erreicht')
    expect(abschnitt(842, 'abdeckung')).toHaveTextContent('nicht erreicht')

    // Der Plan-Schritt lief mit 36,6 % seiner Vorgabe — wenig, aber gelaufen. Er trägt den Vermerk
    // nicht, und daran ist „nicht erreicht" von „wenig verbraucht" unterscheidbar.
    const plan = abschnitt(842, 'plan')
    expect(plan).not.toHaveTextContent('nicht erreicht')
    expect(plan.dataset.fuellung).toBe('36.6')
  })

  it('kappt die übergelaufene Prüfstufe der Einheit 842 bei voller Länge (Punkt 10)', async () => {
    await echteUebersicht()
    const pruefung = abschnitt(842, 'review')

    // 900 484 ms gegen eine Vorgabe von 900 000 ms: Ohne Kappung stünde hier 100.1.
    expect(pruefung.dataset.fuellung).toBe('100')
    expect(pruefung).toHaveTextContent('15,0 / 15 min')
  })

  it('kappt auch einen Schritt mit doppelter Vorgabe (Punkt 11)', async () => {
    await uebersichtZu(
      kettenStand({
        budget: { planMin: 10 },
        einheiten: [
          {
            id: '900',
            titel: 'Doppelte Vorgabe',
            ausgang: 'fertig',
            stufen: { plan: { id: '901', dauerMs: 20 * 60_000 } },
          },
        ],
      }),
      'neu angelegt',
    )
    const plan = abschnitt(900, 'plan')

    expect(plan.dataset.fuellung).toBe('100')
    expect(plan).toHaveTextContent('20,0 / 10 min')
  })

  it('nennt über dem Band der Einheit 842 den Zeitbudget-Grund samt Dokumentnummer (Punkt 12)', async () => {
    await echteUebersicht()

    const satz = screen.getByTestId('abbruch-842')
    expect(satz).toHaveTextContent(
      'Zeitbudget review: die Session wurde nach 15.0 min am Limit beendet',
    )
    expect(satz).toHaveTextContent('#849')
    expect(abschnitt(842, 'review')).toHaveTextContent('am Zeitbudget beendet')

    // Ein regulär durchgelaufener Vorgang trägt weder Satz noch Vermerk.
    expect(screen.queryByTestId('abbruch-791')).not.toBeInTheDocument()
    expect(abschnitt(791, 'abdeckung')).not.toHaveTextContent('beendet')
  })

  it('trägt an einem anders begründeten Abbruch „hier abgebrochen" (Punkt 13)', async () => {
    const uebersicht = await uebersichtZu(
      kettenStand({
        budget: { planMin: 20, reviewMin: 15, paketeMin: 15, abdeckungMin: 10 },
        einheiten: [
          {
            id: '900',
            titel: 'Abbruch ohne Zeitbezug',
            ausgang: 'abgebrochen',
            grund: GRUND_KOSTEN,
            // `id: null` heißt: Die Stufe hat begonnen, ein Plan entstand aber nicht.
            stufen: { plan: { id: null, dauerMs: 5 * 60_000 } },
          },
        ],
      }),
      'neu angelegt',
    )

    expect(abschnitt(900, 'plan')).toHaveTextContent('hier abgebrochen')
    expect(uebersicht).not.toHaveTextContent('am Zeitbudget beendet')

    const satz = screen.getByTestId('abbruch-900')
    expect(satz).toHaveTextContent(GRUND_KOSTEN)
    expect(satz).toHaveTextContent('keine Karten')
  })

  it('sagt an einem Schritt ohne Zeitvorgabe ausdrücklich „ohne Vorgabe"', async () => {
    await uebersichtZu(
      kettenStand({
        einheiten: [
          {
            id: '900',
            titel: 'Ohne Budget',
            ausgang: 'fertig',
            stufen: { plan: { id: '901' } },
          },
        ],
      }),
      'neu angelegt',
    )
    const plan = abschnitt(900, 'plan')

    // Ohne Vorgabe gibt es kein Verhältnis: Der Schritt bekommt denselben Anteil wie die übrigen,
    // und die Füllung bleibt leer statt eine Quote zu behaupten, die niemand gemeldet hat.
    expect(plan).toHaveTextContent('0,0 min · ohne Vorgabe')
    expect(plan.dataset.anteil).toBe('1')
    expect(plan.dataset.fuellung).toBe('0')
  })
})

describe('NightRunPage — Entstandene Karten je Vorgang (#868)', () => {
  /**
   * Die Karten der echten Nacht: die drei Wurzeln und die sechs, die aus ihnen entstanden sind.
   * Sie hängen **unterhalb** der Wurzel — ohne die Erweiterung von `ladeKetten` fragt die Seite
   * keine von ihnen ab, und jeder Verweis bliebe ohne Karte.
   */
  const ENTSTANDENE = {
    791: karte({ id: 1, number: 791, title: '[Fachlich] Zugriff und Konten' }),
    814: karte({ id: 2, number: 814, title: '[Fachlich] Vorhaben ein- und ausblenden' }),
    842: karte({ id: 3, number: 842, title: '[Fachlich] Leitstand mehrstufig' }),
    844: karte({ id: 11, number: 844, title: '[Plan] Sicherheits-Bauform' }),
    845: karte({ id: 12, number: 845, title: 'Bauform binden' }),
    846: karte({ id: 13, number: 846, title: '[Plan] Vorhaben-Liste' }),
    847: karte({ id: 14, number: 847, title: 'Liste als Komponente' }),
    848: karte({ id: 15, number: 848, title: 'Liste statt Kachelraster' }),
    849: karte({ id: 16, number: 849, title: '[Plan] Leitstand mehrstufig' }),
  }

  const echteUebersichtMitKarten = () =>
    uebersichtZu(ECHTE_KETTE_STAND, 'neu angelegt', ECHTE_KETTE_START, { karten: ENTSTANDENE })

  /** Ein Ketten-Stand mit genau einem Vorgang, dessen Plan-Stufe das Dokument `id` hinterließ. */
  const mitPlanDokument = (id: string) =>
    kettenStand({
      einheiten: [
        { id: '900', titel: 'Vorgang mit Plan', ausgang: 'fertig', stufen: { plan: { id } } },
      ],
    })

  /**
   * Das Element eines Vorgangs — nicht dessen `within`-Objekt. Der Aufrufer legt `within` selbst
   * um das Ergebnis: `testing-library/prefer-screen-queries` erkennt eine Teilbaum-Suche nur, wenn
   * `within(...)` an der Abfragestelle steht, und hielte eine Zwischenvariable sonst für ein
   * destrukturiertes `render`-Ergebnis.
   */
  const vorgangIn = (cardNumber: number) => screen.getByTestId(`paket-${cardNumber}`)

  /**
   * Die Zeile eines Arbeitsschritts mit den dort entstandenen Karten. Über die Test-ID und nicht
   * über den Text: Die Zeile setzt sich aus Beschriftung und Verweisen zusammen, und `getByText`
   * sucht je Element nur dessen eigene Textknoten.
   */
  const dokumentzeile = (cardNumber: number, schritt: string) =>
    screen.getByTestId(`dokumente-${cardNumber}-${schritt}`)

  it('führt an der Einheit 791 die entstandenen Karten und öffnet die angeklickte (Punkt 5)', async () => {
    await echteUebersichtMitKarten()
    const vorgang = vorgangIn(791)

    expect(
      await within(vorgang).findByRole('button', { name: 'Plan #844 [Plan] Sicherheits-Bauform' }),
    ).toBeInTheDocument()
    expect(
      within(vorgang).getByRole('button', { name: 'Pakete #845 Bauform binden' }),
    ).toBeInTheDocument()

    // Zwei Pakete an einem Vorgang stehen in **einer** Zeile. Seit #916 als je eigener Chip mit
    // eigener Vorzeile, statt als eine Beschriftung mit kommagetrennten Nummern.
    expect(dokumentzeile(814, 'pakete')).toHaveTextContent('Pakete#847Pakete#848')

    fireEvent.click(within(vorgang).getByRole('button', { name: /^Plan #844/ }))

    expect(screen.getByTestId('karten-detail')).toHaveTextContent('Karte 844')
  })

  it('nennt an der Einheit 842 den Plan #849 und ausdrücklich „keine Pakete" (Punkt 6)', async () => {
    await echteUebersichtMitKarten()
    const vorgang = vorgangIn(842)

    expect(
      await within(vorgang).findByRole('button', { name: 'Plan #849 [Plan] Leitstand mehrstufig' }),
    ).toBeInTheDocument()
    // Die Pakete-Stufe hat der Vorgang nie erreicht — das steht in Worten da, nicht als Leerstelle.
    expect(within(vorgang).getByText('keine Pakete')).toBeInTheDocument()
  })

  it('nennt einen Vorgang ohne jede Stufe „kein Plan" und „keine Pakete" (Punkt 4)', async () => {
    await uebersichtZu(kettenStand(), 'neu angelegt', startedAt(0), { karten: {} })
    const vorgang = vorgangIn(900)

    expect(within(vorgang).getByText('kein Plan')).toBeInTheDocument()
    expect(within(vorgang).getByText('keine Pakete')).toBeInTheDocument()
  })

  it('kennzeichnet eine nicht auflösbare Dokumentnummer als nicht mehr vorhanden (Punkt 7)', async () => {
    // Kein Eintrag im Katalog-Stub: Der Nummer-Lookup antwortet mit 404, und das ist ein
    // Ergebnis — nicht mehr vorhanden —, kein noch offener Abruf.
    await uebersichtZu(mitPlanDokument('901'), 'neu angelegt', startedAt(0), { karten: {} })
    await waitFor(() =>
      expect(dokumentzeile(900, 'plan')).toHaveTextContent('Plan#901 nicht mehr vorhanden'),
    )
    expect(
      within(vorgangIn(900)).queryByRole('button', { name: /901/ }),
    ).not.toBeInTheDocument()
  })

  it('zeigt eine noch ladende Dokumentnummer ohne Verweis und nicht als entfallen (Punkt 8)', async () => {
    let freigeben = () => {}
    const laedt = new Promise<void>((aufloesen) => {
      freigeben = aufloesen
    })
    await uebersichtZu(mitPlanDokument('901'), 'neu angelegt', startedAt(0), {
      karten: { 901: karte({ id: 11, number: 901, title: '[Plan] Gleich da' }) },
      kartenVerzoegert: laedt,
    })

    // Solange der Abruf läuft, steht die Nummer da — ohne Verweis und ohne die Falschaussage,
    // die Karte sei fort.
    const zeile = dokumentzeile(900, 'plan')
    expect(zeile).toHaveTextContent('Plan#901')
    expect(zeile).not.toHaveTextContent('nicht mehr vorhanden')
    expect(
      within(vorgangIn(900)).queryByRole('button', { name: /901/ }),
    ).not.toBeInTheDocument()

    freigeben()

    expect(
      await within(vorgangIn(900)).findByRole('button', {
        name: 'Plan #901 [Plan] Gleich da',
      }),
    ).toBeInTheDocument()
  })

  it('ruft eine von zwei Vorgängen genannte Dokumentnummer nur einmal ab (Punkt 9)', async () => {
    await uebersichtZu(
      kettenStand({
        einheiten: [
          { id: '900', titel: 'Erster', ausgang: 'fertig', stufen: { plan: { id: '901' } } },
          { id: '902', titel: 'Zweiter', ausgang: 'fertig', stufen: { pakete: { ids: ['901'] } } },
        ],
      }),
      'neu angelegt',
      startedAt(0),
      { karten: { 901: karte({ id: 11, number: 901, title: '[Plan] Gemeinsam' }) } },
    )

    // Beide Vorgänge zeigen den Verweis — die Karte ist also aufgelöst …
    expect(
      await within(vorgangIn(900)).findByRole('button', { name: /#901/ }),
    ).toBeInTheDocument()
    expect(
      within(vorgangIn(902)).getByRole('button', { name: /#901/ }),
    ).toBeInTheDocument()
    // … und dafür ging genau eine Anfrage hinaus.
    expect(byNumberAufrufe().filter((a) => a.url.endsWith('/901'))).toHaveLength(1)
  })
})

describe('NightRunPage — gekürzte Vorgangszeile neben der Übersicht (#869)', () => {
  /**
   * Die drei Wurzelkarten der echten Nacht. #842 hängt an einem Vorhaben, damit die Zeile eine
   * Angabe trägt, die die Übersicht gar nicht führt — genau die, die stehen bleiben soll.
   */
  const KARTEN = {
    791: karte({ id: 1, number: 791, title: '[Fachlich] Zugriff und Konten' }),
    814: karte({ id: 2, number: 814, title: '[Fachlich] Vorhaben ein- und ausblenden' }),
    842: karte({ id: 3, number: 842, title: '[Fachlich] Leitstand mehrstufig', parentId: 42 }),
  }

  const VORHABEN = { 42: vorhaben({ id: 42, number: 9, title: 'Leitstand ausbauen' }) }

  /**
   * Die Plan-Zeile des Stufenblocks im Auszug von #842. Sie steht seit #855 im `excerpt` und ist
   * damit der sichtbare Beleg dafür, dass die Zeile den Block nicht ein zweites Mal zeigt — die
   * Übersicht schreibt dieselbe Karte als „Plan #849 …" und nie in dieser Form.
   */
  const STUFENBLOCK_ZEILE = /plan #849: gelungen/

  /** Die Dauer von #842: 439 741 ms Plan plus 900 484 ms Prüfung, gerundet 22 Minuten. */
  const DAUER_842 = '22 Min'

  /**
   * Gesucht wird ausschließlich im Absatz — **nicht** im Übernahmetext: Dessen `textarea` trägt
   * Zustand und Auszug absichtlich weiter (#856, AK 5), und eine Suche über den ganzen Teilbaum
   * fände sie dort wieder. Die Aussage lautet aber, dass der Auszug nicht mehr als **Zeilentext**
   * dasteht.
   */
  const ALS_ABSATZ = { selector: 'p' } as const

  /** Der echte Ketten-Lauf aus dem Ergebnisstand — mit Sitzungsstand, also mit Übersicht. */
  async function mitUebersicht() {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
      karten: KARTEN,
      kartenNachId: VORHABEN,
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    await waitFor(() => expect(laufKopfzeile(panelEl)).toHaveTextContent('neu angelegt'))
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('uebersicht-fuss')
    return panelEl
  }

  it('zeigt am Lauf mit Übersicht jede Angabe genau einmal (Punkt 5)', async () => {
    // AK 15 aus #859 verlangt, dass nichts zweimal auf derselben Seite steht. Bis #916 wurde
    // dafür die Zeile des Arbeitspakets gekürzt, weil die Übersicht dieselben Angaben trug; seit
    // dem Vorgangsblock gibt es nur noch **eine** Darstellung, also ist nichts zu kürzen.
    const panelEl = await mitUebersicht()
    const zeile = await within(panelEl).findByTestId('paket-842')

    // Der rohe Stufenblock-Auszug entfällt: Band und Grund sagen dasselbe genauer.
    expect(within(zeile).queryByText(STUFENBLOCK_ZEILE, ALS_ABSATZ)).toBeNull()
    expect(within(zeile).queryByText(/^Auszug: /, ALS_ABSATZ)).toBeNull()
    // Zustand und Dauer stehen genau einmal — im Block, nicht zusätzlich in einer Übersicht.
    expect(screen.getAllByTestId('zustand-842')).toHaveLength(1)
    expect(within(zeile).getByTestId('kennzahlen-842')).toHaveTextContent(DAUER_842)
    expect(screen.getAllByTestId('stufenband-842')).toHaveLength(1)
  })

  it('lässt Vorhaben und Übernahmetext am Lauf mit Übersicht stehen und bedienbar (Punkt 6)', async () => {
    const panelEl = await mitUebersicht()
    const zeile = await within(panelEl).findByTestId('paket-842')

    fireEvent.click(await within(zeile).findByRole('button', { name: /^Vorhaben #9 / }))
    expect(await screen.findByTestId('karten-detail')).toHaveTextContent('Karte 9')

    expect(uebernahmetext(panelEl, 842)).toContain(
      'Zustand: Am Zeitbudget beendet, Ergebnis liegt vor',
    )
    expect(
      within(zeile).getByRole('button', { name: 'Übernahmetext zu Karte #842 kopieren' }),
    ).toBeInTheDocument()
  })

  it('lässt die Zeile an einem aufbewahrten Ketten-Lauf ohne Sitzungsstand vollständig (Punkt 7)', async () => {
    // Die Gegenprobe zu AK 2: Derselbe Lauf, dieselbe Lauf-Art `CHAIN` — nur ohne Sitzungsstand.
    // Eine an `mode === 'CHAIN'` gebundene Kürzung nähme ihm hier alles und gäbe nichts zurück.
    renderPage({ listen: [wieAufbewahrt(ECHTE_KETTE_STAND)], karten: KARTEN, kartenNachId: VORHABEN })
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)

    panelAufklappen(panelEl)

    expect(within(panelEl).queryByTestId('stufenband-842')).toBeNull()
    const zeile = await within(panelEl).findByTestId('paket-842')
    // Abgewartet, bis die Wurzelkarte und ihr Vorhaben aufgelöst sind — erst dann steht die Zeile
    // fertig da, und der Rest des Tests prüft nicht bloß ihren Zwischenstand.
    await within(zeile).findByRole('button', { name: /^Vorhaben #9 / })
    expect(within(zeile).getByTestId('zustand-842')).toHaveTextContent(
      'Am Zeitbudget beendet, Ergebnis liegt vor',
    )
    // Die Dauer steht seit #916 in der Ergebniszeile des Blocks; ohne Ergebnisstand gibt es kein
    // Band, in dem sie sonst steckte.
    expect(within(zeile).getByTestId('kennzahlen-842')).toHaveTextContent(DAUER_842)
    expect(within(zeile).getByText(STUFENBLOCK_ZEILE, ALS_ABSATZ)).toBeInTheDocument()
  })

  it('lässt die Zeile an einem Lauf anderer Art vollständig (Punkt 8)', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [
              {
                id: 11,
                cardNumber: 700,
                title: 'Paket A',
                state: 'YELLOW',
                errorClass: 'CHECKS_RED',
                durationMs: SIEBEN_MIN,
                excerpt: 'Issue #700: npm test -> rot',
              },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)
    await within(lauf(0)).findByText('Vorhaben: ohne')

    const zeile = within(lauf(0)).getByTestId('paket-700')
    expect(within(zeile).getByTestId('zustand-700')).toHaveTextContent('Erfolg, Prüfung rot')
    // Die Dauer steht seit #1116 in einer Zeile mit den Fehlanzeigen der Angaben, die dieser
    // aufbewahrte Lauf nicht gemessen hat — dieselbe Zeile, ein längerer Text.
    expect(within(zeile).getByTestId('kennzahlen-700')).toHaveTextContent('7 Min')
    expect(
      within(zeile).getByText('Auszug: Issue #700: npm test -> rot', ALS_ABSATZ),
    ).toBeInTheDocument()
    expect(within(zeile).getByLabelText('Übernahmetext zu Karte #700')).toBeInTheDocument()
  })
})

describe('NightRunPage — Laufband für Umsetzungs-Läufe (#871)', () => {
  /** Ein Abschnitt des Laufbands, über die Kartennummer seines Vorgangs. */
  const bandabschnitt = (panelEl: HTMLElement, cardNumber: number) =>
    within(panelEl).getByTestId(`laufband-abschnitt-${cardNumber}`)

  /**
   * Die fünf Vorgänge der echten Nacht vom 7. September, in der Reihenfolge des Laufs — zwei
   * erfolgreiche und drei gescheiterte.
   */
  const ECHTE_VORGAENGE = [767, 768, 769, 770, 771] as const

  /** Das aufgeklappte Panel des echten Umsetzungs-Laufs, nach Einliefern und Neuladen der Liste. */
  async function echterUmsetzungslauf() {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_STAND, 'night-run-2026-09-07-085229.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTER_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('zustand-767')
    return panelEl
  }

  it('teilt das Band der echten Nacht in den Verhältnissen der Vorgangsdauern (Punkt 9)', async () => {
    const panelEl = await echterUmsetzungslauf()

    // Geprüft an der Beschriftung und am Datenfeld, nicht an berechneten Stilwerten: In jsdom
    // rechnet kein Browser ein Layout aus, ein Test auf `flex`-Anteile prüfte dort die
    // Zeichenkette, die man selbst geschrieben hat.
    expect(within(panelEl).getAllByTestId(/^laufband-abschnitt-/)).toHaveLength(5)
    expect(ECHTE_VORGAENGE.map((n) => bandabschnitt(panelEl, n).dataset.anteil)).toEqual([
      '7.9',
      '24.9',
      '31.8',
      '5.4',
      '30',
    ])
    // Die Aussage der Nacht: Die drei gescheiterten Vorgänge verbrauchten zusammen 86,7 Prozent
    // der Zeit — die beiden erfolgreichen zusammen 13,3.
    expect(bandabschnitt(panelEl, 767)).toHaveAttribute(
      'aria-label',
      'Karte #767: Erfolg, Prüfung rot, 4 Min, 7.9 % der Schicht',
    )
    expect(bandabschnitt(panelEl, 769)).toHaveAttribute(
      'aria-label',
      'Karte #769: gescheitert, 16 Min, 31.8 % der Schicht',
    )
    // Die Dauer steht auch sichtbar unter ihrem Abschnitt, nicht nur in der Ansage (Punkt 4).
    expect(bandabschnitt(panelEl, 769)).toHaveTextContent('16 Min')
  })

  it('färbt allein die drei gescheiterten Abschnitte rot (Punkt 10)', async () => {
    const panelEl = await echterUmsetzungslauf()
    const farbe = (cardNumber: number) =>
      within(panelEl).getByTestId(`laufband-balken-${cardNumber}`)

    // Seit #988 tragen die Melder des Leitstands die Aussage; sie stehen als Variablen im Theme,
    // deshalb der Vergleich auf den Verweis und nicht auf einen aufgelösten Farbwert.
    for (const nummer of [768, 769, 771]) {
      expect(getComputedStyle(farbe(nummer)).backgroundColor).toBe(MELDER.zinnob)
    }
    for (const nummer of [767, 770]) {
      expect(getComputedStyle(farbe(nummer)).backgroundColor).toBe(MELDER.bernst)
    }
  })

  it('zeigt an einem Lauf mit einem einzigen Vorgang kein Band, aber dessen Angaben (Punkt 11)', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [
              {
                id: 11,
                cardNumber: 700,
                title: 'Paket A',
                state: 'RED',
                errorClass: 'CHECKS_RED',
                durationMs: SIEBEN_MIN,
                excerpt: 'Issue #700: npm test -> rot',
              },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByText('Vorhaben: ohne')

    // Ein Balken mit einem Abschnitt behauptet ein Verhältnis, das es nicht gibt.
    expect(within(lauf(0)).queryByTestId('laufband')).not.toBeInTheDocument()
    const zeile = within(lauf(0)).getByTestId('paket-700')
    expect(within(zeile).getByTestId('zustand-700')).toHaveTextContent('gescheitert')
    expect(within(zeile).getByTestId('kennzahlen-700')).toHaveTextContent('7 Min')
  })

  it('lässt einen Vorgang ohne Dauer in der Liste, aber nicht im Band (Punkt 12)', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [
              { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', durationMs: SIEBEN_MIN },
              { id: 12, cardNumber: 701, title: 'Paket B', state: 'GREEN', durationMs: SIEBEN_MIN },
              // Ein zurückgestelltes Paket lief nie, also trägt es auch keine Dauer.
              { id: 13, cardNumber: 702, title: 'Paket C', state: 'GREY' },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    // Seit #917 steht der Anteil je Vorgangsblock statt in einem Band über alle — dieselbe
    // Rechnung, ein anderer Ort.
    await within(lauf(0)).findByTestId('laufband-abschnitt-700')

    expect(within(lauf(0)).getAllByTestId(/^laufband-abschnitt-/)).toHaveLength(2)
    expect(within(lauf(0)).queryByTestId('laufband-abschnitt-702')).not.toBeInTheDocument()
    // In der Zeilenliste steht er weiterhin — er trägt einen Ausgang, nur keine Breite.
    expect(within(lauf(0)).getByTestId('paket-702')).toHaveTextContent('nicht bearbeitet')
  })

  it('zeigt an einem Ketten-Lauf kein Laufband, sondern die Übersicht (Punkt 13)', async () => {
    const uebersicht = await echteUebersicht()

    expect(uebersicht).toBeInTheDocument()
    expect(screen.queryAllByTestId(/^laufband-abschnitt-/)).toHaveLength(0)
  })

  it('misst die Anteile an der Summe der Vorgangsdauern, nicht an der Laufdauer (AK 2)', async () => {
    // Die Laufdauer weicht hier absichtlich von der Summe ab: Bezöge sich die Breite auf sie,
    // stünden hier 7 und 21 statt 25 und 75. Am echten Ergebnisstand ist der Unterschied nicht
    // messbar — dort **ist** die Laufdauer die Summe der Vorgangsdauern, und einen Endzeitstempel
    // führt der Stand gar nicht (Entscheidung des Issues).
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            durationMs: 100 * 60_000,
            items: [
              { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', durationMs: SIEBEN_MIN },
              { id: 12, cardNumber: 701, title: 'Paket B', state: 'GREEN', durationMs: 3 * SIEBEN_MIN },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('laufband-abschnitt-700')

    expect(bandabschnitt(lauf(0), 700).dataset.anteil).toBe('25')
    expect(bandabschnitt(lauf(0), 701).dataset.anteil).toBe('75')
  })
})

describe('NightRunPage — Modellzeit-Anteil je Vorgang (#872)', () => {
  /** Die Kennzahlenzeile eines Vorgangs, über die Kartennummer. */
  const kennzahlen = (panelEl: HTMLElement, cardNumber: number) =>
    within(panelEl).getByTestId(`kennzahlen-${cardNumber}`)

  /**
   * Klappt einen frisch eingelesenen Lauf auf und gibt sein Panel zurück. Die Kennzahlen stehen
   * **nur** im Ergebnisstand dieser Sitzung — der Server bewahrt sie nicht auf —, deshalb führt
   * jeder dieser Tests über das Einlesen einer Datei und nicht über eine Server-Antwort.
   */
  async function eingelesen(
    ergebnisstand: string,
    start: string,
    dateiname: string,
    antworten: Antworten,
  ) {
    renderPage(antworten)
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ergebnisstand, dateiname)

    const panelEl = await screen.findByTestId(`lauf-${start}`)
    panelAufklappen(panelEl)
    // Umsetzungs-Läufe tragen die Zeile seit #917 im Vorgangsblock, die beiden Altbestand-Arten
    // weiter in der eigenen Liste — auf beides wartet dieselbe Abfrage.
    await screen.findAllByTestId(/^kennzahlen-\d+$/)
    return panelEl
  }

  const echterUmsetzungslauf = () =>
    eingelesen(ECHTER_STAND, ECHTER_START, 'night-run-2026-09-07-085229.json', {
      submit: { ergebnis: alleNeu(ECHTER_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_STAND)],
    })

  it('nennt je Vorgang Dauer, Kosten, Züge und den Anteil der Modellarbeit (Punkt 6)', async () => {
    const panelEl = await echterUmsetzungslauf()

    // 180 563 ms Arbeitszeit auf 244 427 ms Dauer — 73,9 Prozent, gerundet 74.
    expect(kennzahlen(panelEl, 767)).toHaveTextContent(
      '4 Min · 2,14 $ · 38 Züge · Modellarbeit 74 % der Dauer, der Rest außerhalb',
    )
    // Der zweite Vorgang belegt, dass der Anteil je Vorgang gerechnet wird: 380 034 auf
    // 769 668 ms sind 49 Prozent, nicht noch einmal 74.
    expect(kennzahlen(panelEl, 768)).toHaveTextContent(
      'Modellarbeit 49 % der Dauer, der Rest außerhalb',
    )
  })

  it('beziffert die Restzeit nicht und nennt sie nicht Werkzeugzeit (AK 3)', async () => {
    const panelEl = await echterUmsetzungslauf()

    // Das Protokoll misst die Aufteilung der Restzeit nicht — sie enthält auch die Prüfung
    // durch den Nachtlauf selbst und Wartezeiten.
    expect(within(panelEl).queryByText(/Werkzeugzeit/)).not.toBeInTheDocument()
    expect(kennzahlen(panelEl, 767)).not.toHaveTextContent('26 %')
  })

  it('zeigt bei gleichzeitigen Arbeiten keinen Anteil, sondern den Hinweis (Punkt 7)', async () => {
    const panelEl = await eingelesen(
      ECHTER_PRUEFLAUF_STAND,
      ECHTER_PRUEFLAUF_START,
      'night-run-2026-09-11-103116.json',
      {
        submit: { ergebnis: alleNeu(ECHTER_PRUEFLAUF_STAND) },
        listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
      },
    )

    const zeile = kennzahlen(panelEl, 782)
    expect(zeile).toHaveTextContent(
      '14 Min · 21,95 $ · 252 Züge · Modellarbeit: mehrere Arbeiten liefen gleichzeitig',
    )
    // Weder ein Anteil über hundert Prozent noch ein negativer Rest: 1 084 627 auf 879 763 ms
    // wären gerundet 123 Prozent und ein Rest von minus 23.
    expect(zeile.textContent).not.toMatch(/\d+ %/)
    expect(zeile.textContent).not.toContain('−')
    expect(zeile.textContent).not.toContain('-')
  })

  it('führt einen Prüf-Lauf nur mit seinem einen bearbeiteten Vorgang (Punkt 1)', async () => {
    const panelEl = await eingelesen(
      ECHTER_PRUEFLAUF_STAND,
      ECHTER_PRUEFLAUF_START,
      'night-run-2026-09-11-103116.json',
      {
        submit: { ergebnis: alleNeu(ECHTER_PRUEFLAUF_STAND) },
        listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
      },
    )

    // 34 der 35 Karten wurden nur angesehen und aussortiert — eine Kennzahlenzeile je
    // übergangener Karte wäre eine Wand aus Fehlanzeigen.
    expect(within(panelEl).getAllByTestId(/^kennzahlen-\d+$/)).toHaveLength(1)
  })

  it('zeigt eine gemessene Dauer von null als „0 s", fehlende Angaben als Hinweis (Punkt 8)', async () => {
    const panelEl = await eingelesen(
      ECHTE_ERZEUGUNG_STAND,
      ECHTE_ERZEUGUNG_START,
      'night-run-2026-09-09-125621.json',
      { listen: [[]] },
    )

    // #535 wurde in einer früheren Nacht fortgesetzt: In dieser lief keine Sitzung mehr, also
    // ist die Null gemessen — und die Kennzahlen fehlen tatsächlich.
    expect(kennzahlen(panelEl, 535)).toHaveTextContent(
      '0 s · Kosten nicht gemeldet · Züge nicht gemeldet · Modellzeit nicht gemeldet',
    )
    // Der Nachbarvorgang derselben Nacht zeigt, dass die Fehlanzeige nicht am Lauf hängt.
    expect(kennzahlen(panelEl, 533)).toHaveTextContent(
      '32 Min · 6,88 $ · 54 Züge · Modellarbeit 54 % der Dauer, der Rest außerhalb',
    )
  })

  it('zeigt ohne Arbeitszeit die Fehlanzeige und nicht den Hinweis auf Gleichzeitigkeit (Punkt 9)', async () => {
    const OHNE_ARBEITSZEIT = stand({
      einheiten: [
        einheit({
          ausgang: 'erfolg',
          pruefung: GEPRUEFT,
          kennzahlen: { kostenUsd: 1.5, zuege: 7 },
        }),
      ],
    })
    const panelEl = await eingelesen(OHNE_ARBEITSZEIT, startedAt(0), 'night-run.json', {
      submit: { ergebnis: alleNeu(OHNE_ARBEITSZEIT) },
      listen: [[], wieAufbewahrt(OHNE_ARBEITSZEIT)],
    })

    const zeile = kennzahlen(panelEl, 700)
    expect(zeile).toHaveTextContent('7 Min · 1,50 $ · 7 Züge · Modellzeit nicht gemeldet')
    expect(zeile).not.toHaveTextContent('gleichzeitig')
  })

  it('nennt ohne messbare Dauer keinen Anteil, obwohl beide Angaben vorliegen', async () => {
    // Konstruiert: Keines der fünf Protokolle trägt eine Sitzung, die neben der Dauer auch die
    // Arbeitszeit mit null meldet. Ein Anteil an einer Dauer von null ist keine Aussage, und
    // „0 % Modellarbeit" behauptete eine Restzeit, die es nicht gibt.
    const OHNE_MESSBARE_DAUER = stand({
      einheiten: [
        einheit({
          ausgang: 'erfolg',
          pruefung: GEPRUEFT,
          dauerMs: 0,
          kennzahlen: { kostenUsd: 1.5, zuege: 7, apiDauerMs: 0 },
        }),
      ],
    })
    const panelEl = await eingelesen(OHNE_MESSBARE_DAUER, startedAt(0), 'night-run.json', {
      submit: { ergebnis: alleNeu(OHNE_MESSBARE_DAUER) },
      listen: [[], wieAufbewahrt(OHNE_MESSBARE_DAUER)],
    })

    expect(kennzahlen(panelEl, 700)).toHaveTextContent(
      '0 s · 1,50 $ · 7 Züge · Modellarbeit: ohne messbare Dauer kein Anteil',
    )
  })

  it('zeigt an einem Lauf ohne bearbeiteten Vorgang gar keinen Block', async () => {
    // Ein Lauf, der jede Karte aussortiert hat, hat nichts zu berichten — ein leerer Block
    // behauptete eine Sitzung, die es nie gab.
    const NUR_UEBERGANGEN = stand({
      einheiten: [einheit({ ausgang: 'uebersprungen', grund: 'kein Label', dauerMs: undefined })],
    })
    renderPage({
      submit: { ergebnis: alleNeu(NUR_UEBERGANGEN) },
      listen: [[], wieAufbewahrt(NUR_UEBERGANGEN)],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(NUR_UEBERGANGEN)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByText('Vorhaben: ohne')

    // Ein grauer Vorgang lief nie; seine Ergebniszeile bestünde aus lauter Fehlanzeigen.
    expect(within(lauf(0)).queryByTestId('kennzahlen-700')).not.toBeInTheDocument()
  })

  it('führt am Ketten-Vorgang Kosten, Züge und Modellzeit in seiner eigenen Form', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('uebersicht-fuss')

    // Die Kette führt Kosten und Züge je Vorgang (#866) — seit #988 in der aufgeklappten Zeile.
    expect(within(panelEl).getByTestId('kennzahlen-791')).toHaveTextContent('11,52 $ · 89 Züge')
    expect(within(panelEl).getByTestId('kennzahlen-791')).toHaveTextContent(
      'Modellzeit nicht gemeldet',
    )
  })

  it('zeigt an einem aufbewahrten Run ohne Ergebnisstand keine Kennzahlenzeile', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [
              { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', durationMs: SIEBEN_MIN },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByText('Vorhaben: ohne')

    // Seit Issue #1116 speist sich die Zeile aus der Server-Antwort. Ein aufbewahrter Lauf, an
    // dem nichts gemessen wurde, benennt die Lücke — bis dahin stand dort allein die Dauer, weil
    // der Server Kosten, Züge und Modellzeit noch gar nicht aufbewahrte.
    const zeileOhneMessung = within(lauf(0)).getByTestId('kennzahlen-700')
    expect(zeileOhneMessung).toHaveTextContent('7 Min')
    expect(zeileOhneMessung).toHaveTextContent('Kosten nicht gemeldet')
    expect(zeileOhneMessung).toHaveTextContent('Züge nicht gemeldet')
    expect(zeileOhneMessung).toHaveTextContent('Modellzeit nicht gemeldet')
    // Nie als 0 (AK 5) — und Tokenmengen bleiben ganz weg, statt eine fünfte Fehlanzeige zu sein.
    expect(zeileOhneMessung).not.toHaveTextContent('0,00 $')
    expect(zeileOhneMessung).not.toHaveTextContent('Eingabe')
  })
})

describe('NightRunPage — Herkunft eines Laufs (#775)', () => {
  /**
   * Derselbe Lauf, wie der Server ihn nach dem Einliefern zurückgibt — nur mit abweichender
   * Stückzahl. Sie ist der Beleg dafür, dass der sichtbare Lauf wirklich der **neu geladene** ist
   * und nicht mehr der eben im Browser gedeutete: Ohne diesen Unterschied stünde nach dem Upload
   * derselbe Text auf der Seite, und der Test wäre schon vor dem Neuladen grün.
   */
  const VOM_SERVER = wieAufbewahrt(EIN_LAUF).map((view) => ({ ...view, processedCount: 42 }))

  it('kennzeichnet einen aus dem Ergebnisstand eingelieferten Lauf auch nach dem Neuladen', async () => {
    renderPage({ submit: { ergebnis: alleNeu(EIN_LAUF) }, listen: [[], VOM_SERVER] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    // Erst wenn die Stückzahl des Servers dasteht, ist das Neuladen durch.
    await waitFor(() =>
      expect(laufKopfzeile(lauf(0))).toHaveTextContent('42 bearbeitet · 0 übergangen'),
    )
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('Ergebnisstand')
    expect(within(lauf(0)).queryByText('Herkunft unbekannt')).not.toBeInTheDocument()
  })

  it('nennt einen aufbewahrten Run ohne Upload in dieser Sitzung „Herkunft unbekannt"', async () => {
    // Der Server kennt die Unterscheidung nicht; die Kennzeichnung ist sitzungslokal (Plan #772,
    // Entscheidung 6). Nach einem Neuladen der Seite gilt das auch für einen eben erst
    // eingelieferten Lauf.
    renderPage({ listen: [VOM_SERVER] })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('Herkunft unbekannt')
    expect(within(lauf(0)).queryByText('Ergebnisstand')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Zustände, Kennzahlen und Auszüge', () => {
  it('macht jeden der vier Zustände am Text erkennbar, nicht nur an der Farbe', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(VIER_ZUSTAENDE)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    const panel = within(lauf(0))
    expect(await panel.findByText('Erfolg')).toBeInTheDocument()
    expect(panel.getByText('Erfolg, Prüfung rot')).toBeInTheDocument()
    expect(panel.getByText('gescheitert')).toBeInTheDocument()
    expect(panel.getByText('nicht bearbeitet')).toBeInTheDocument()
  })

/**
   * Die LED eines Vorgangs, benannt nach ihrem Melder — seit #988 trägt sie die Farbaussage
   * (`led-gruen`, `led-bernst`, `led-zinnob`, `led-grau`). Der Melder steht als Variable im Theme;
   * geprüft wird deshalb, **welcher** Melder gewählt wurde, nicht sein aufgelöster Farbwert.
   */
  const melderVon = (cardNumber: number) =>
    within(screen.getByTestId(`paket-${cardNumber}`))
      .getByTestId(/^led-/)
      .getAttribute('data-testid')

  it('macht eine Erfolgsmeldung mit roter Prüfung gelb', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(GELB) },
      listen: [[], wieAufbewahrt(GELB)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(GELB)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    expect(await within(lauf(0)).findByText('Erfolg, Prüfung rot')).toBeInTheDocument()
    // Text **und** Farbe tragen die Aussage (CLAUDE-react.md Zeile 142): der Satz in der
    // aufgeklappten Zeile, die Farbe in der LED.
    expect(melderVon(700)).toBe('led-bernst')
  })

  /**
   * Plan #1072 E28: Der eben geparste Lauf ist noch bei keinem Server gewesen und traegt deshalb
   * keinen Befund. Ohne die lokale Rechnung in `laufMelder` verloere er seine LED — er ist die
   * einzige Stelle, an der ein Lauf ohne Server-Sicht beurteilt wird.
   */
  it('beurteilt den eben geparsten Lauf weiterhin lokal — ohne Befund vom Server (#1081)', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(GELB) },
      listen: [[], wieAufbewahrt(GELB)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(GELB)
    const panelEl = await screen.findByTestId(`lauf-${startedAt(0)}`)

    const kopf = within(panelEl).getByTestId('lauf-kopf')
    expect(within(kopf).getByTestId(/^led-/).getAttribute('data-testid')).toBe('led-bernst')
  })

  it('zeigt den Zustand als ausgefuellte, gleich grosse Flaeche — unabhaengig von der Textlaenge (#738)', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(VIER_ZUSTAENDE)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    const panel = within(lauf(0))
    await panel.findByText('Erfolg')

    // Vier unterschiedlich lange Zustandstexte ("Erfolg" bis "Erfolg, Prüfung rot"), dieselbe
    // Flächengröße — die Fläche wirkt als Signal, nicht als weitere Textzeile (AC3). Seit #988
    // ist es die LED der Vorlage (9 px, Z. 190–196).
    const groesse = { width: '9px', height: '9px' }
    for (const nummer of [700, 701, 702, 703]) {
      const zeile = within(panel.getByTestId(`paket-${nummer}`))
      expect(zeile.getByTestId(/^led-/)).toHaveStyle(groesse)
    }
    // Und jeder Zustand hat seinen eigenen Melder — vier Zustände, vier LEDs.
    expect(melderVon(700)).toBe('led-gruen')
    expect(melderVon(701)).toBe('led-bernst')
    expect(melderVon(702)).toBe('led-zinnob')
    expect(melderVon(703)).toBe('led-grau')
  })

  it('zeigt bei einem grauen Arbeitspaket seinen Grund', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(VIER_ZUSTAENDE)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    // Der Ergebnisstand nennt den Grund wörtlich (#773) — die Anzeige stellt ihn als solchen voran.
    expect(
      await within(lauf(0)).findByText(`Grund: ${GRUND_ZURUECKGESTELLT}`),
    ).toBeInTheDocument()
  })

  it('kennzeichnet einen Prüf-Lauf als solchen', async () => {
    // Prüf-Läufe kommen nicht mehr über den Upload herein (der Ergebnisstand-Parser lehnt sie ab),
    // wohl aber vom Server: aus einem Lauf, den ein früherer Stand eingeliefert hat.
    renderPage({ listen: [[aufbewahrt({ id: 1, startedAt: startedAt(0), mode: 'REVIEW' })]] })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(artSymbol(lauf(0))).toHaveAccessibleName('Prüfung')
    expect(within(lauf(0)).queryByText(/Umsetzung/)).not.toBeInTheDocument()
  })

  it('kennzeichnet einen Umsetzungs-Lauf als solchen', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(EIN_LAUF) },
      listen: [[], wieAufbewahrt(EIN_LAUF)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(artSymbol(lauf(0))).toHaveAccessibleName('Umsetzung')
  })

  // **Umgekehrt mit Issue #872**: Der Test hielt bis dahin fest, dass in der Auswertung
  // überhaupt keine Kostenangabe erscheint — ein Nicht-Ziel aus #715, das die fachliche Quelle
  // #861 ausdrücklich aufhebt. Die Kennzahlenzeile je Vorgang nennt Kosten jetzt, und wo der
  // Stand keine führt, benennt sie das Fehlen, statt es zu verschweigen.
  it('zeigt Dauer und Stückzahlen je Run sowie die Dauer je Arbeitspaket, dazu die Kosten', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(VIER_ZUSTAENDE)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    const panel = within(lauf(0))
    // Die Laufdauer ist die Summe der drei gelaufenen Sessions; das zurückgestellte Paket trägt keine.
    // Die Metazeile nennt die Laufdauer in der Form der Vorlage („4 h 12 min", Z. 385).
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('21 min')
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('3 bearbeitet · 1 übergangen')
    // Die Dauer je Vorgang steht seit #917 in der Ergebniszeile seines Blocks.
    await waitFor(() => expect(panel.getByTestId('kennzahlen-700')).toHaveTextContent('7 Min'))
    // Die drei bearbeiteten Vorgänge tragen eine Kennzahlenzeile; das zurückgestellte Paket
    // ist grau und bekommt keine.
    expect(panel.getAllByTestId(/^kennzahlen-\d+$/)).toHaveLength(3)
    // Dieser Stand führt keine Sitzungs-Kennzahlen — das Fehlen wird benannt, nicht als Null
    // dargestellt.
    expect(panel.getByTestId('kennzahlen-700')).toHaveTextContent('Kosten nicht gemeldet')
    expect(panel.queryByText(/0,00 \$/)).not.toBeInTheDocument()
  })

  it('weist ungedeutete Zeilen mit Anzahl und Auszug aus, wörtlich statt gerendert', async () => {
    // Ein Ergebnisstand kennt keine ungedeuteten Zeilen (#773) — ein vom Server geladener Lauf aus
    // der Zeit der Protokolldeutung schon.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            unparsedCount: 1,
            unparsedSample: UNGEDEUTET,
            items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
          }),
        ],
      ],
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    expect(laufKopfzeile(lauf(0))).toHaveTextContent('Ungedeutete Zeilen: 1')
    aufklappen(0)
    // `findByText` mit exaktem Matcher greift nur, wenn der ganze Text **ein** Textknoten ist —
    // ein Markdown-Renderer hätte ihn in Elemente zerteilt.
    expect(await within(lauf(0)).findByText(UNGEDEUTET)).toBeInTheDocument()
    expect(within(lauf(0)).queryByText('Sternchen', { selector: 'em' })).toBeNull()
    expect(within(lauf(0)).queryByText('Backticks', { selector: 'code' })).toBeNull()
  })
})

describe('NightRunPage — Herkunftskette', () => {
  const lauf700 = (items: ItemVorgabe[]) => [
    [aufbewahrt({ id: 1, startedAt: startedAt(0), items })],
  ]

  it('löst die Kette des offenen Laufs auf und lädt jede Nummer nur einmal', async () => {
    renderPage({
      listen: lauf700([
        { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' },
        { id: 12, cardNumber: 700, title: 'Paket A', state: 'GREY' },
      ]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf', derivedFrom: 715 }),
        715: karte({ id: 3, number: 715, title: '[Fachlich] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    // Beide Arbeitspakete nennen dieselbe Karte, also erscheint die Kette zweimal. Die Stufe ist
    // seit #818 ein Knopf; ihr Name trägt die Stufe, ihr sichtbarer Text nur Nummer und Titel.
    expect(
      await within(lauf(0)).findAllByRole('button', { name: 'Plan #718 [Plan] Nachtlauf' }),
    ).toHaveLength(2)
    expect(
      within(lauf(0)).getAllByRole('button', {
        name: 'Fachliche Anforderung #715 [Fachlich] Nachtlauf',
      }),
    ).toHaveLength(2)
    expect(within(lauf(0)).getAllByText('#718 [Plan] Nachtlauf')).toHaveLength(2)
    // Die doppelt genannte Karte 700 wird genau einmal geladen.
    expect(byNumberAufrufe().filter((a) => a.url.endsWith('/700'))).toHaveLength(1)
  })

  it('zeigt ein Arbeitspaket ohne Plan und ohne fachliche Anforderung als „ohne", nicht als Abriss', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Plan: ohne')).toBeInTheDocument()
    expect(within(lauf(0)).getByText('Fachliche Anforderung: ohne')).toBeInTheDocument()
    expect(within(lauf(0)).queryByText(/abgerissen/)).not.toBeInTheDocument()
  })

  it('zeigt eine Stufe, die der Nachtlauf nicht fährt, als „noch nicht erreicht"', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 715 }),
        715: karte({ id: 3, number: 715, title: '[Fachlich] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Plan: noch nicht erreicht')).toBeInTheDocument()
    expect(
      within(lauf(0)).getByRole('button', { name: 'Fachliche Anforderung #715 [Fachlich] Nachtlauf' }),
    ).toBeInTheDocument()
  })

  it('kennzeichnet eine Stufe als abgerissen, wenn der Lauf sie rot meldet', async () => {
    renderPage({
      listen: lauf700([
        { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' },
        { id: 12, cardNumber: 718, title: '[Plan] Nachtlauf', state: 'RED', errorClass: 'AWAITING_DECISION' },
      ]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    // Der Abriss-Vermerk steht außerhalb des Links: Ein Klick öffnet die Karte, der Zusatz bleibt
    // Fließtext (WCAG 2.5.3 — der sichtbare Linktext ist Teilstring des Namens).
    expect(
      await within(lauf(0)).findByRole('button', { name: 'Plan #718 [Plan] Nachtlauf' }),
    ).toBeInTheDocument()
    expect(within(lauf(0)).getByText(/— abgerissen/)).toBeInTheDocument()
  })

  it('meldet eine nicht auflösbare Kartennummer und bleibt bedienbar', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {},
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    await waitFor(() =>
      expect(within(lauf(0)).getByTestId('paket-700')).toHaveTextContent('Karte #700 nicht gefunden'),
    )
    expect(within(lauf(0)).queryByText('Plan: ohne')).not.toBeInTheDocument()
    // Die Seite bleibt bedienbar: der Lauf lässt sich wieder zuklappen.
    fireEvent.click(laufTaste(lauf(0)))
    expect(laufTaste(lauf(0))).toHaveAttribute('aria-expanded', 'false')
  })

  it('meldet eine nicht auflösbare Stufe der Kette, ohne sie „ohne" zu nennen', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 999 }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Plan: Karte #999 nicht gefunden')).toBeInTheDocument()
    expect(within(lauf(0)).queryByText('Plan: ohne')).not.toBeInTheDocument()
  })

  it('lädt die Kette eines Laufs nur beim ersten Aufklappen', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)
    await within(lauf(0)).findByText('Plan: ohne')
    fireEvent.click(laufTaste(lauf(0)))
    panelAufklappen(lauf(0))

    await waitFor(() => expect(within(lauf(0)).getByText('Plan: ohne')).toBeInTheDocument())
    expect(byNumberAufrufe()).toHaveLength(1)
  })

  it('bricht einen Herkunftsring ab, statt endlos zu laden', async () => {
    // Ein Ring kann nur an der API vorbei entstehen (siehe `DerivationNode.broken`) — ohne
    // Abbruch liefe sowohl das Laden als auch das Aufbauen der Kette endlos.
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf', derivedFrom: 700 }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(
      await within(lauf(0)).findByRole('button', { name: 'Plan #718 [Plan] Nachtlauf' }),
    ).toBeInTheDocument()
    expect(within(lauf(0)).getByText('Fachliche Anforderung: noch nicht erreicht')).toBeInTheDocument()
    expect(byNumberAufrufe()).toHaveLength(2)
  })

  it('öffnet zu einem Arbeitspaket seine Karte', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    fireEvent.click(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ }))

    expect(await screen.findByTestId('karten-detail')).toHaveTextContent('Karte 700')
  })

  it('öffnet die Karte des Plans aus der Stufenzeile (#818)', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf', derivedFrom: 715 }),
        715: karte({ id: 3, number: 715, title: '[Fachlich] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    fireEvent.click(
      await within(lauf(0)).findByRole('button', { name: 'Plan #718 [Plan] Nachtlauf' }),
    )

    expect(await screen.findByTestId('karten-detail')).toHaveTextContent('Karte 718')
  })

  it('öffnet die Karte der fachlichen Anforderung aus der Stufenzeile (#818)', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf', derivedFrom: 715 }),
        715: karte({ id: 3, number: 715, title: '[Fachlich] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    fireEvent.click(
      await within(lauf(0)).findByRole('button', {
        name: 'Fachliche Anforderung #715 [Fachlich] Nachtlauf',
      }),
    )

    expect(await screen.findByTestId('karten-detail')).toHaveTextContent('Karte 715')
  })

  it('schließt die Karte wieder', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    fireEvent.click(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ }))
    await screen.findByTestId('karten-detail')

    fireEvent.click(screen.getByText('detail-schliessen'))

    expect(screen.queryByTestId('karten-detail')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Vorhaben eines Arbeitspakets (#818)', () => {
  const lauf700 = (items: ItemVorgabe[]) => [
    [aufbewahrt({ id: 1, startedAt: startedAt(0), items })],
  ]

  /** Zwei aufbewahrte Läufe — die Grundlage der Tests zu nebenläufigen `ladeKetten()`-Aufrufen. */
  const zweiLaeufe = (itemsA: ItemVorgabe[], itemsB: ItemVorgabe[]) => [
    [
      aufbewahrt({ id: 1, startedAt: startedAt(0), items: itemsA }),
      aufbewahrt({ id: 2, startedAt: startedAt(30), items: itemsB }),
    ],
  ]

  /** Ein Stub, der die Vorhaben-Abrufe anhält, bis {@link freigeben} gerufen wird. */
  function angehalten() {
    let freigeben = () => {}
    const verzoegert = new Promise<void>((resolve) => {
      freigeben = resolve
    })
    return { verzoegert, freigeben: () => freigeben() }
  }

  it('zeigt „ohne", wenn das Arbeitspaket keinem Vorhaben zugeordnet ist', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Vorhaben: ohne')).toBeInTheDocument()
    // Ohne Zuordnung wird auch nichts nachgeladen.
    expect(vorhabenAufrufe()).toHaveLength(0)
  })

  it('öffnet das zugeordnete Vorhaben über seine projektweite Nummer', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A', parentId: 42 }) },
      kartenNachId: { 42: vorhaben({ id: 42, number: 5, title: 'Leitstand ausbauen' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    // Abgerufen wird über die Karten-ID, angezeigt die projektweite Nummer.
    fireEvent.click(await within(lauf(0)).findByRole('button', { name: /^Vorhaben #5 / }))
    expect(await screen.findByTestId('karten-detail')).toHaveTextContent('Karte 5')
    expect(vorhabenAufrufe().map((a) => a.url)).toEqual(['/api/cards/42'])
  })

  it('meldet ein nicht abrufbares Vorhaben und bleibt bedienbar', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A', parentId: 42 }) },
      kartenNachId: {},
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Vorhaben: nicht auflösbar')).toBeInTheDocument()
    // Die Wurzelkarte bleibt öffenbar, und der Lauf lässt sich weiterhin zu- und aufklappen.
    expect(within(lauf(0)).getByRole('button', { name: '#700 Paket A' })).toBeInTheDocument()
    fireEvent.click(laufTaste(lauf(0)))
    panelAufklappen(lauf(0))
    await waitFor(() =>
      expect(within(lauf(0)).getByText('Vorhaben: nicht auflösbar')).toBeInTheDocument(),
    )
  })

  it('zeigt keine Vorhaben-Zeile, wenn schon die Wurzelkarte nicht auflösbar ist', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {},
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    await waitFor(() =>
      expect(within(lauf(0)).getByTestId('paket-700')).toHaveTextContent('Karte #700 nicht gefunden'),
    )
    expect(within(lauf(0)).queryByText(/^Vorhaben:/)).toBeNull()
    expect(vorhabenAufrufe()).toHaveLength(0)
  })

  it('zeigt keine Vorhaben-Zeile, solange der Abruf des Vorhabens noch läuft', async () => {
    const { verzoegert, freigeben } = angehalten()
    renderPage({
      // Der Lauf mit dem Vorhaben steht **oben** und klappt seit #914 von selbst auf; der zweite
      // wird im Test aufgeklappt. Die Rollen sind damit gegenüber der früheren Fassung getauscht,
      // die Inszenierung ist dieselbe: Ein Lauf hängt im Vorhaben-Abruf, der andere veröffentlicht
      // währenddessen den geteilten Katalog.
      listen: zweiLaeufe(
        [{ id: 11, cardNumber: 701, title: 'Paket B', state: 'GREEN' }],
        [{ id: 12, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
      ),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', parentId: 5 }),
        701: karte({ id: 2, number: 701, title: 'Paket B' }),
      },
      kartenNachId: { 5: vorhaben({ id: 5, number: 9, title: 'Leitstand ausbauen' }) },
      kartenNachIdVerzoegert: verzoegert,
    })
    await screen.findByTestId(`lauf-${startedAt(30)}`)
    await waitFor(() => expect(vorhabenAufrufe()).toHaveLength(1))
    // Der oberste Lauf steht offen, seine Vorgangszeile aber zugeklappt (#988) — und darin steht
    // die Vorhaben-Zeile.
    vorgaengeAufklappen(lauf(30))

    aufklappen(0)

    // Der zweite Lauf veröffentlicht den geteilten Katalog — Karte 700 ist damit aufgelöst, ihr
    // Vorhaben aber noch nicht. Dann steht dort keine Zeile, nicht etwa „ohne" oder „nicht
    // auflösbar".
    await within(lauf(0)).findByText('Vorhaben: ohne')
    expect(within(lauf(30)).getByRole('button', { name: '#700 Paket A' })).toBeInTheDocument()
    expect(within(lauf(30)).queryByText(/^Vorhaben:/)).toBeNull()

    freigeben()
    expect(
      await within(lauf(30)).findByRole('button', { name: /^Vorhaben #9 / }),
    ).toBeInTheDocument()
  })

  it('ruft ein Vorhaben auch bei zwei gleichzeitig aufgeklappten Läufen nur einmal ab', async () => {
    const { verzoegert, freigeben } = angehalten()
    renderPage({
      listen: zweiLaeufe(
        [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
        [{ id: 12, cardNumber: 701, title: 'Paket B', state: 'GREEN' }],
      ),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', parentId: 42 }),
        701: karte({ id: 2, number: 701, title: 'Paket B', parentId: 42 }),
      },
      kartenNachId: { 42: vorhaben({ id: 42, number: 9, title: 'Leitstand ausbauen' }) },
      kartenNachIdVerzoegert: verzoegert,
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)
    await waitFor(() => expect(vorhabenAufrufe()).toHaveLength(1))
    aufklappen(30)
    freigeben()

    expect(await within(lauf(0)).findByRole('button', { name: /^Vorhaben #9 / })).toBeInTheDocument()
    expect(await within(lauf(30)).findByRole('button', { name: /^Vorhaben #9 / })).toBeInTheDocument()
    expect(vorhabenAufrufe()).toHaveLength(1)
  })
})

describe('NightRunPage — Häufigkeit einer Fehlerklasse', () => {
  /**
   * Drei aufbewahrte Läufe, nur der neueste trägt Befunde. `M` ist die Länge **dieser** Liste (3),
   * nicht die Zahl der Läufe mit einer bestimmten Fehlerklasse.
   */
  const DREI_LAEUFE: NightRunView[] = [
    aufbewahrt({
      id: 3,
      startedAt: startedAt(30),
      items: [
        { id: 11, cardNumber: 700, title: 'Paket A', state: 'YELLOW', errorClass: 'CHECKS_RED' },
        { id: 12, cardNumber: 701, title: 'Paket B', state: 'RED', errorClass: 'HARD_ABORT' },
        { id: 13, cardNumber: 702, title: 'Paket C', state: 'GREEN' },
        { id: 14, cardNumber: 703, title: 'Paket D', state: 'GREY', errorClass: 'DEPENDENCY_UNMET' },
      ],
    }),
    aufbewahrt({ id: 2, startedAt: startedAt(10) }),
    aufbewahrt({ id: 1, startedAt: startedAt(0) }),
  ]

  /** Die Häufigkeitszeile eines Arbeitspakets im Lauf von Minute 30; `null`, wenn keine erscheint. */
  const haeufigkeit = (cardNumber: number) =>
    within(lauf(30)).queryByTestId(`haeufigkeit-${cardNumber}`)

  it('zeigt zu einem gelben und einem roten Befund die Häufigkeit seiner Fehlerklasse', async () => {
    renderPage({ listen: [DREI_LAEUFE], zaehler: [{ CHECKS_RED: 3, HARD_ABORT: 2 }] })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    expect(await within(lauf(30)).findByTestId('haeufigkeit-700')).toHaveTextContent(
      'Prüfungen rot: 3 von 3 aufbewahrten Runs',
    )
    expect(haeufigkeit(701)).toHaveTextContent('Harter Abbruch: 2 von 3 aufbewahrten Runs')
  })

  it('nennt ein erstes Vorkommen „zum ersten Mal", nicht „0" und nicht „1 von M"', async () => {
    renderPage({ listen: [DREI_LAEUFE], zaehler: [{ CHECKS_RED: 1 }] })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    const zeile = await within(lauf(30)).findByTestId('haeufigkeit-700')
    expect(zeile).toHaveTextContent('Prüfungen rot: zum ersten Mal')
    expect(zeile).not.toHaveTextContent('0')
    expect(zeile).not.toHaveTextContent('1 von')
  })

  it('zeigt zu einem grünen und einem grauen Arbeitspaket keine Häufigkeit', async () => {
    renderPage({
      listen: [DREI_LAEUFE],
      zaehler: [{ CHECKS_RED: 3, HARD_ABORT: 2, DEPENDENCY_UNMET: 2 }],
    })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    await within(lauf(30)).findByTestId('haeufigkeit-700')
    expect(haeufigkeit(702)).toBeNull()
    // Grau trägt eine Fehlerklasse (offene Abhängigkeit) — trotzdem ist es kein Befund.
    expect(haeufigkeit(703)).toBeNull()
  })

  it('zeigt die Zahl des Endpunkts, nicht die aus den geladenen Läufen gerechnete', async () => {
    // Genau **ein** geladener Lauf trägt CHECKS_RED; der Endpunkt meldet 3. Erschiene die 1, wäre
    // im Browser gerechnet worden.
    renderPage({ listen: [DREI_LAEUFE], zaehler: [{ CHECKS_RED: 3 }] })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    expect(await within(lauf(30)).findByTestId('haeufigkeit-700')).toHaveTextContent(
      'Prüfungen rot: 3 von 3 aufbewahrten Runs',
    )
    expect(zaehlerAufrufe()).toHaveLength(1)
  })

  it('lädt die Häufigkeit nach erfolgreichem Senden neu', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(GELB) },
      listen: [[], wieAufbewahrt(GELB)],
      zaehler: [{}, { CHECKS_RED: 1 }],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(GELB)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    expect(await within(lauf(0)).findByTestId('haeufigkeit-700')).toHaveTextContent(
      'Prüfungen rot: zum ersten Mal',
    )
    expect(zaehlerAufrufe()).toHaveLength(2)
  })

  it('hält Läufe und Befunde sichtbar, wenn der Abruf der Häufigkeit scheitert', async () => {
    renderPage({ listen: [DREI_LAEUFE], zaehlerFehler: 'Nur der Owner darf die Auswertung sehen.' })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    expect(await within(lauf(30)).findByTestId('haeufigkeit-700')).toHaveTextContent(
      'Prüfungen rot: Häufigkeit nicht abrufbar',
    )
    // Unterscheidbar von einem ersten Vorkommen — und die Seite bleibt vollständig.
    expect(within(lauf(30)).queryByText(/zum ersten Mal/)).not.toBeInTheDocument()
    expect(within(lauf(30)).getByTestId('zustand-700')).toBeInTheDocument()
    expect(within(lauf(30)).getByTestId('zustand-701')).toBeInTheDocument()
  })

  it('zeigt zu einer noch nicht gespeicherten Auswertung keine Häufigkeit', async () => {
    // Das Senden scheitert: Der Lauf steht auf der Seite, zählt aber nicht zu den aufbewahrten.
    renderPage({ submit: { fehler: 'Auszug zu lang' }, zaehler: [{ CHECKS_RED: 4 }] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(GELB)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    expect(await within(lauf(0)).findByTestId('zustand-700')).toBeInTheDocument()
    expect(within(lauf(0)).queryByTestId('haeufigkeit-700')).toBeNull()
  })

  it('zeigt keine Häufigkeit, wenn der Endpunkt die Fehlerklasse nicht kennt', async () => {
    renderPage({ listen: [DREI_LAEUFE], zaehler: [{ HARD_ABORT: 2 }] })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    expect(await within(lauf(30)).findByTestId('haeufigkeit-701')).toBeInTheDocument()
    expect(haeufigkeit(700)).toBeNull()
  })

  it('zeigt keine Häufigkeit zu einem Befund ohne Fehlerklasse', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 3,
            startedAt: startedAt(30),
            items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'RED' }],
          }),
        ],
      ],
      zaehler: [{ CHECKS_RED: 3 }],
    })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    expect(await within(lauf(30)).findByTestId('zustand-700')).toBeInTheDocument()
    expect(haeufigkeit(700)).toBeNull()
  })
})

describe('NightRunPage — Übernahmetext für die Entwicklungsumgebung', () => {
  /** Ein Auszug mit Markdown-Zeichen — er ist Fremdtext und wird nie gedeutet. */
  const AUSZUG_GELB =
    '  Issue #700: gelaufen: *fett* `npm test` -> rot (Frontend) | ausgelassen: keine'

  const GELB: NightRunHandoffItem = {
    cardNumber: 700,
    title: 'Paket A',
    state: 'YELLOW',
    errorClass: 'CHECKS_RED',
    excerpt: AUSZUG_GELB,
  }

  const ROT: NightRunHandoffItem = {
    cardNumber: 701,
    title: 'Paket B',
    state: 'RED',
    errorClass: 'HARD_ABORT',
    excerpt: '  HARTER STOPP: erfolgreiche Runde zu Issue #701 hinterlaesst einen dirty Tree',
  }

  const GRUEN: NightRunHandoffItem = {
    cardNumber: 702,
    title: 'Paket C',
    state: 'GREEN',
    errorClass: undefined,
    excerpt: '  Erfolg nach 7 min, Commit c3d4e5f, Issue #702 in In review.',
  }

  const GRAU: NightRunHandoffItem = {
    cardNumber: 703,
    title: 'Paket D',
    state: 'GREY',
    errorClass: 'DEPENDENCY_UNMET',
    excerpt: '  #703 Paket D -> uebersprungen (Abhaengigkeit #999 liegt nicht in Done)',
  }

  const BEFUNDE: NightRunView[] = [
    aufbewahrt({
      id: 1,
      startedAt: startedAt(0),
      items: [GELB, ROT, GRUEN, GRAU].map((item, position) => ({ id: position + 1, ...item })),
    }),
  ]

/**
   * Der Befund-Kasten eines Arbeitspakets; `null`, wenn keiner gerendert wird. Seit #988 ein
   * `<pre>` statt eines schreibgeschützten Feldes — die Beschriftung ist dieselbe geblieben.
   */
  const feld = (cardNumber: number) =>
    within(lauf(0)).queryByLabelText(`Übernahmetext zu Karte #${cardNumber}`)

  const kopierKnopf = (cardNumber: number) =>
    within(lauf(0)).queryByRole('button', {
      name: `Übernahmetext zu Karte #${cardNumber} kopieren`,
    })

  let writeText: ReturnType<typeof vi.fn>

  beforeEach(() => {
    writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })
  })

  /** Rendert die Seite mit den vier Befunden und klappt den Lauf auf. */
  async function befundeZeigen() {
    renderPage({ listen: [BEFUNDE] })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('zustand-700')
  }

  it('zeigt zu einem gelben und einem roten Arbeitspaket den vollständigen Text, bevor kopiert wird', async () => {
    await befundeZeigen()

    // Verglichen wird der **ganze** String: Ein gekürzter Text wäre ein halber Befund in der
    // Zwischenablage. `textContent` faltet keine Zeilenumbrüche, die Prüfung bleibt wörtlich.
    expect(feld(700)?.textContent).toBe(buildHandoffText(GELB))
    expect(feld(701)?.textContent).toBe(buildHandoffText(ROT))
    expect(feld(700)?.textContent).toContain(AUSZUG_GELB)
    expect(writeText).not.toHaveBeenCalled()
  })

  it('zeigt zu einem grünen und einem grauen Arbeitspaket weder Befund noch Knopf', async () => {
    await befundeZeigen()

    expect(feld(702)).toBeNull()
    expect(kopierKnopf(702)).toBeNull()
    expect(feld(703)).toBeNull()
    expect(kopierKnopf(703)).toBeNull()
  })

  it('legt exakt den String des sichtbaren Feldes in die Zwischenablage', async () => {
    await befundeZeigen()
    const sichtbar = feld(700)?.textContent

    fireEvent.click(kopierKnopf(700) as HTMLElement)

    await waitFor(() => expect(writeText).toHaveBeenCalledTimes(1))
    expect(writeText).toHaveBeenCalledWith(sichtbar)
  })

  it('gibt den Auszug wörtlich wieder, statt seine Markdown-Zeichen zu deuten', async () => {
    await befundeZeigen()

    // Ein `<pre>` mit Textinhalt trägt kein Markup — geprüft wird beides: der wörtliche Inhalt
    // und dass daneben nichts vom Markdown-Renderer Erzeugtes steht.
    expect(feld(700)?.textContent).toContain('*fett*')
    expect(feld(700)?.textContent).toContain('`npm test`')
    expect(within(lauf(0)).queryByText('fett', { selector: 'em' })).toBeNull()
    expect(within(lauf(0)).queryByText('npm test', { selector: 'code' })).toBeNull()
  })

  it('lässt den Text sichtbar, wenn die Zwischenablage nicht verfügbar ist', async () => {
    writeText.mockRejectedValue(new Error('Clipboard nicht verfügbar'))
    await befundeZeigen()
    const sichtbar = feld(700)?.textContent

    fireEvent.click(kopierKnopf(700) as HTMLElement)

    await waitFor(() => expect(writeText).toHaveBeenCalledTimes(1))
    // Der Text bleibt stehen und ist von Hand markierbar; eine Fehlermeldung ist nicht nötig.
    expect(feld(700)?.textContent).toBe(sichtbar)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('zeigt zu einem Befund ohne Auszug einen Text ohne `undefined`', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [{ id: 1, cardNumber: 700, title: 'Paket A', state: 'RED', errorClass: 'HARD_ABORT' }],
          }),
        ],
      ],
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('zustand-700')

    expect(feld(700)?.textContent).not.toContain('undefined')
    expect(feld(700)?.textContent).toContain('Harter Abbruch')
  })
})

describe('NightRunPage — null aus der API (#734)', () => {
  /**
   * So schickt der Server einen Lauf ohne Befunde: Fehlende Werte stehen als `null` im JSON, nicht
   * als fehlendes Feld — weder `@JsonInclude(NON_NULL)` noch `default-property-inclusion` sind
   * gesetzt. Die Anzeigeform kennt für „fehlt" nur `undefined`; wer das nicht übersetzt, rechnet
   * mit `null` weiter (`null / 1000` ist `0`) und schreibt es in Texte.
   */
  const MIT_NULL: NightRunView[] = [
    aufbewahrt({
      id: 1,
      startedAt: startedAt(0),
      unparsedSample: null,
      items: [
        {
          id: 11,
          cardNumber: 700,
          title: 'Paket A',
          state: 'RED',
          errorClass: null,
          durationMs: null,
          commitHash: null,
          excerpt: null,
        },
      ],
    }),
  ]

  /** Der Befund-Kasten des Übernahmetexts; `null`, wenn keiner gerendert wird. */
  const feld = (cardNumber: number) =>
    within(lauf(0)).queryByLabelText(`Übernahmetext zu Karte #${cardNumber}`)

  async function nullLaufZeigen() {
    renderPage({
      listen: [MIT_NULL],
      zaehler: [{ HARD_ABORT: 2 }],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('zustand-700')
  }

  it('zeigt den Lauf an, statt an einem null-Feld abzustürzen', async () => {
    await nullLaufZeigen()

    expect(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ })).toBeInTheDocument()
    expect(lauf(0).textContent).not.toContain('null')
    expect(lauf(0).textContent).not.toContain('undefined')
  })

  it('lässt Dauer und Auszug weg, statt „0 s" und „Auszug: null" zu behaupten', async () => {
    await nullLaufZeigen()

    expect(lauf(0).textContent).not.toContain('0 s')
    expect(lauf(0).textContent).not.toContain('Auszug:')
    expect(within(lauf(0)).queryByTestId('haeufigkeit-700')).toBeNull()
  })

  it('lässt die Fehlerklasse aus dem Übernahmetext weg, statt „undefined" hineinzuschreiben', async () => {
    await nullLaufZeigen()

    expect(feld(700)?.textContent).toBe(
      'Befund des Runs zu Karte #700 Paket A\nZustand: gescheitert',
    )
  })
})

describe('NightRunPage — Aufschlüsselung der gesichteten Karten (#873)', () => {
  /** Die Zahl der gesichteten Karten, wie sie über der Aufschlüsselung steht. */
  const gesichtet = (panelEl: HTMLElement) =>
    within(panelEl).getByTestId('aufschluesselung-gesichtet')

  const bearbeitet = (panelEl: HTMLElement) =>
    within(panelEl).getByTestId('aufschluesselung-bearbeitet')

  /** Die Zeilen der Überspringgründe, in der Reihenfolge ihrer Häufigkeit. */
  const grundzeilen = (panelEl: HTMLElement) =>
    within(panelEl)
      .getAllByTestId(/^aufschluesselung-grund-\d+$/)
      .map((zeile) => zeile.textContent)

  /**
   * Der frisch eingelesene Prüf-Lauf vom 11. September: 35 gesichtete Karten, eine bearbeitete
   * (#782, „geprüft mit Befund"), 34 übersprungene aus drei Gründen.
   */
  async function frischGeparsterPrueflauf() {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_PRUEFLAUF_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_PRUEFLAUF_STAND, 'night-run-2026-09-11-103116.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTER_PRUEFLAUF_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('aufschluesselung')
    return panelEl
  }

  /**
   * Derselbe Lauf **ohne** den Ergebnisstand dieser Sitzung — so, wie ihn die Seite nach einem
   * Neuladen vom Server bekommt. Das Ausgangswort fehlt dann; die Aufschlüsselung entsteht allein
   * aus Zustand, Fehlerklasse und Auszug (Punkt 5).
   */
  async function aufbewahrterPrueflauf(ansicht = wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)) {
    renderPage({ listen: [ansicht] })

    const panelEl = await screen.findByTestId(`lauf-${ansicht[0].startedAt}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('aufschluesselung')
    return panelEl
  }

  it('schlüsselt den frisch geparsten Prüf-Lauf nach seinen Ausgängen auf (Punkt 8)', async () => {
    const panelEl = await frischGeparsterPrueflauf()

    expect(gesichtet(panelEl)).toHaveTextContent('35 Karten gesichtet')
    expect(bearbeitet(panelEl)).toHaveTextContent('1 bearbeitet')
    expect(grundzeilen(panelEl)).toEqual([
      '17 übersprungen: kein Plan-Dokument ([Plan])',
      "15 übersprungen: kein Label 'review:offen'",
      '2 übersprungen: Idee ([Idee])',
    ])
    // Jede Karte zählt genau einmal: 1 + 17 + 15 + 2 = 35.
    expect(within(panelEl).queryByTestId('aufschluesselung-liegengeblieben')).not.toBeInTheDocument()
    expect(within(panelEl).queryByTestId('aufschluesselung-unbekannt')).not.toBeInTheDocument()
  })

  it('kommt am aufbewahrten Run ohne das Ausgangswort auf dieselben Zahlen (Punkt 9)', async () => {
    const panelEl = await aufbewahrterPrueflauf()

    expect(gesichtet(panelEl)).toHaveTextContent('35 Karten gesichtet')
    expect(bearbeitet(panelEl)).toHaveTextContent('1 bearbeitet')
    expect(grundzeilen(panelEl)).toEqual([
      '17 übersprungen: kein Plan-Dokument ([Plan])',
      "15 übersprungen: kein Label 'review:offen'",
      '2 übersprungen: Idee ([Idee])',
    ])
    expect(within(panelEl).queryByTestId('aufschluesselung-unbekannt')).not.toBeInTheDocument()
  })

  it('zählt einen nicht sicher zuzuordnenden Vorgang als „ohne bekannten Ausgang" (Punkt 10)', async () => {
    // Konstruiert: Keines der fünf Protokolle liefert einen aufbewahrten Lauf mit diesen Kanten.
    // Der Auszug ist das einzige Unterscheidungsmerkmal, das die Server-Sicht noch trägt — fehlt
    // er oder steht eine Fehlerklasse daneben, wird nicht geraten.
    const panelEl = await aufbewahrterPrueflauf([
      aufbewahrt({
        id: 1,
        startedAt: startedAt(0),
        mode: 'REVIEW',
        processedCount: 0,
        skippedCount: 4,
        items: [
          {
            id: 11,
            cardNumber: 700,
            title: 'Paket A',
            state: 'GREY',
            excerpt: "kein Label 'review:offen'",
          },
          {
            id: 12,
            cardNumber: 701,
            title: 'Paket B',
            state: 'GREY',
            excerpt: 'Über die Obergrenze (--max) hinaus — bleibt liegen',
          },
          // Ohne Auszug bleibt nur die graue Farbe — sie allein sagt nicht, warum.
          { id: 13, cardNumber: 702, title: 'Paket C', state: 'GREY' },
          // Eine Fehlerklasse trägt kein übersprungener und kein liegengebliebener Vorgang.
          {
            id: 14,
            cardNumber: 703,
            title: 'Paket D',
            state: 'GREY',
            errorClass: 'DEPENDENCY_UNMET',
            excerpt: 'Abhaengigkeit #999 liegt nicht in Done.',
          },
        ],
      }),
    ])

    expect(gesichtet(panelEl)).toHaveTextContent('4 Karten gesichtet')
    expect(bearbeitet(panelEl)).toHaveTextContent('0 bearbeitet')
    // Der eine sicher zuzuordnende Grund steht für sich; die beiden unklaren werden ihm nicht
    // zugeschlagen.
    expect(grundzeilen(panelEl)).toEqual(["1 übersprungen: kein Label 'review:offen'"])
    expect(within(panelEl).getByTestId('aufschluesselung-liegengeblieben')).toHaveTextContent(
      '1 liegengeblieben',
    )
    expect(within(panelEl).getByTestId('aufschluesselung-unbekannt')).toHaveTextContent(
      '2 ohne bekannten Ausgang',
    )
  })

  it('führt den Erzeugungs-Lauf mit übersprungenen, liegengebliebenen und bearbeiteten (Punkt 11)', async () => {
    renderPage({ listen: [[]] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_ERZEUGUNG_STAND, 'night-run-2026-09-09-125621.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_ERZEUGUNG_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('aufschluesselung')

    // 32 + 3 + 3 = 38 — die Summe der Teilmengen ist die Zahl der gesichteten Karten.
    expect(gesichtet(panelEl)).toHaveTextContent('38 Karten gesichtet')
    expect(bearbeitet(panelEl)).toHaveTextContent('3 bearbeitet')
    expect(grundzeilen(panelEl)).toEqual(["32 übersprungen: kein Label 'kit:nightplan'"])
    expect(within(panelEl).getByTestId('aufschluesselung-liegengeblieben')).toHaveTextContent(
      '3 liegengeblieben',
    )
    // Dieselbe Zahl wie in der unveränderten Kopfzeile des Laufs (AK 2).
    expect(laufKopfzeile(panelEl)).toHaveTextContent('3 bearbeitet · 35 übergangen')
  })

  it('zeigt die Kartennummern einer Grund-Zeile erst beim Aufklappen (Punkt 12)', async () => {
    const panelEl = await frischGeparsterPrueflauf()

    // Zugeklappt steht in der Aufschlüsselung nur die Zahl — die Zeilenliste darunter führt die
    // Karte weiterhin einzeln, und genau sie soll der Betrachter nicht mehr durchsehen müssen.
    const block = within(panelEl).getByTestId('aufschluesselung')
    expect(within(block).queryByText(/#683/)).not.toBeInTheDocument()

    fireEvent.click(within(panelEl).getByRole('button', { name: '2 übersprungen: Idee ([Idee])' }))

    const zeile = within(panelEl).getByTestId('aufschluesselung-grund-2')
    expect(within(zeile).getByText(/^#683/)).toBeInTheDocument()
    expect(within(zeile).getByText(/^#670/)).toBeInTheDocument()
  })

  it('lässt an einem Lauf ohne übersprungene Karte die Grund-Zeilen weg (Punkt 13)', async () => {
    // Konstruiert: Jeder der fünf Ergebnisstände hat übersprungene Karten.
    const OHNE_UEBERSPRUNGENE = stand({
      art: 'review',
      stufe: 'issue',
      einheiten: [einheit({ ausgang: 'mitBefund' })],
    })
    renderPage({
      submit: { ergebnis: alleNeu(OHNE_UEBERSPRUNGENE) },
      listen: [[], wieAufbewahrt(OHNE_UEBERSPRUNGENE)],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(OHNE_UEBERSPRUNGENE)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('aufschluesselung')

    expect(gesichtet(lauf(0))).toHaveTextContent('1 Karten gesichtet')
    expect(bearbeitet(lauf(0))).toHaveTextContent('1 bearbeitet')
    expect(within(lauf(0)).queryAllByTestId(/^aufschluesselung-grund-\d+$/)).toHaveLength(0)
  })

  it('benennt einen übersprungenen Vorgang ohne Grundtext, statt eine leere Zeile zu zeigen', async () => {
    // Konstruiert: `night.mjs` schreibt zu jedem Überspringen einen Grund; fehlte er, stünde hier
    // sonst „1 übersprungen: " ohne Aussage. Der Ausgang ist bekannt, nur sein Grund nicht — das
    // ist etwas anderes als „ohne bekannten Ausgang".
    const OHNE_GRUND = stand({
      art: 'erzeugung',
      stufe: 'plan',
      einheiten: [
        einheit({ ausgang: 'uebersprungen', dauerMs: undefined }),
        // `offen` ist grau, aber weder übersprungen noch liegengeblieben.
        einheit({ id: '701', titel: 'Paket B', ausgang: 'offen', dauerMs: undefined }),
      ],
    })
    renderPage({ listen: [[]] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(OHNE_GRUND)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('aufschluesselung')

    expect(grundzeilen(lauf(0))).toEqual(['1 übersprungen: ohne genannten Grund'])
    expect(within(lauf(0)).getByTestId('aufschluesselung-unbekannt')).toHaveTextContent(
      '1 ohne bekannten Ausgang',
    )
  })

  it('zeigt an einem Umsetzungs-Lauf die Anteilsbalken und keine Aufschlüsselung (Punkt 14)', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_STAND, 'night-run-2026-09-07-085229.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTER_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('laufband-abschnitt-767')

    expect(within(panelEl).queryByTestId('aufschluesselung')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Kennzahlenzeile je Run-Art (#874)', () => {
  /**
   * Der Hinweis, den `night.mjs` genau dann an den Lauf-Kopf schreibt, wenn weder `--verbose`
   * noch `--kette` gesetzt war — wörtlich wie dort und wie in `nightRunErgebnisstand.test.ts`.
   */
  const KENNZAHLEN_HINWEIS =
    'Ohne --verbose fordert der Runner die Stream-Ausgabe der Session nicht an; die Session-Kennzahlen fehlen darum in allen Einheiten.'

  /**
   * Klappt einen frisch eingelesenen Lauf auf und gibt sein Panel zurück. Die Kennzahlenzeile
   * steht **nur** am Ergebnisstand dieser Sitzung — Kosten und Züge verlassen den Browser nie
   * (Plan #718, A1), der Server bewahrt sie nicht auf.
   */
  async function eingelesen(
    ergebnisstand: string,
    start: string,
    dateiname: string,
    antworten: Antworten,
  ) {
    renderPage(antworten)
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ergebnisstand, dateiname)

    const panelEl = await screen.findByTestId(`lauf-${start}`)
    panelAufklappen(panelEl)
    // Beide Formen: Ketten- und Umsetzungs-Lauf tragen seit #915 die Reihe des Entwurfs, die
    // beiden Altbestand-Arten weiter die bisherige Zeile.
    await within(panelEl).findByTestId(FUSSZEILE)
    return panelEl
  }

  /**
   * Die Fußzeile eines aufgeklappten Laufs — seit #988 der Ort der Kennzahlen des Ergebnisstands.
   */
  const zeile = (panelEl: HTMLElement) => within(within(panelEl).getByTestId(FUSSZEILE))

  const echterUmsetzungslauf = () =>
    eingelesen(ECHTER_STAND, ECHTER_START, 'night-run-2026-09-07-085229.json', {
      submit: { ergebnis: alleNeu(ECHTER_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_STAND)],
    })

  it('zählt den grünen und den gelben Vorgang als erledigt (Punkt 7)', async () => {
    const panelEl = await echterUmsetzungslauf()

    // #767 und #770 sind erfolgreich beendet, tragen aber `pruefung.zustand: "ungeprueft"` und
    // werden dadurch **gelb**. Engt man „erledigt" auf den grünen Zustand ein, steht hier
    // „0 von 5", obwohl zwei Pakete fertig wurden — die Gegenprobe zu Plan #864, E6.
    expect(zeile(panelEl).getByText('2 von 5')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('Vorgänge erledigt')).toBeInTheDocument()
  })

  it('rechnet die Kosten der Schicht aus den fünf Einzelwerten und kennzeichnet sie (Punkt 8)', async () => {
    const panelEl = await echterUmsetzungslauf()

    // 2,1366 + 5,1342 + 10,8605 + 1,6051 + 8,1324 = 27,8688 US-Dollar. Der Stand selbst führt
    // keine Summe — nur die Kette schreibt eine (Plan #864, E9).
    expect(zeile(panelEl).getByText('27,87 $')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('Kosten der Schicht')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('gerechnet, nicht im Protokoll')).toBeInTheDocument()
    // 38 + 63 + 123 + 33 + 121 Züge, dazu die Summe der fünf Vorgangsdauern.
    expect(zeile(panelEl).getByText('378')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('51,5 min')).toBeInTheDocument()
  })

  it('nennt am Prüf-Lauf gesichtete zu bearbeiteten Karten und keine erledigten (Punkt 9)', async () => {
    const panelEl = await eingelesen(
      ECHTER_PRUEFLAUF_STAND,
      ECHTER_PRUEFLAUF_START,
      'night-run-2026-09-11-103116.json',
      {
        submit: { ergebnis: alleNeu(ECHTER_PRUEFLAUF_STAND) },
        listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
      },
    )

    expect(zeile(panelEl).getByText('1 von 35')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('Karten bearbeitet')).toBeInTheDocument()
    // „Erledigt" ergibt für einen Prüf-Lauf keinen Sinn — er erledigt keine Vorgänge, er sichtet
    // Karten. Die Kennzahl erscheint nicht und bekommt auch keinen Platzhalter.
    expect(zeile(panelEl).queryByText(/erledigt/)).not.toBeInTheDocument()
  })

  it('nennt am Erzeugungs-Lauf die entstandenen Dokumente (Punkt 10)', async () => {
    const panelEl = await eingelesen(
      ECHTE_ERZEUGUNG_STAND,
      ECHTE_ERZEUGUNG_START,
      'night-run-2026-09-09-125621.json',
      { listen: [[]] },
    )

    // #479, #533 und #535 haben je ein Dokument hinterlassen; die 35 aussortierten Karten keines.
    expect(zeile(panelEl).getByText('3')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('Dokumente entstanden')).toBeInTheDocument()
    expect(zeile(panelEl).queryByText(/erledigt/)).not.toBeInTheDocument()
    // #535 wurde in einer früheren Nacht fortgesetzt und meldet gar keine Kennzahlen — die Summe
    // der beiden übrigen stimmt, sie ist nur unvollständig (Punkt 12 an echten Daten).
    expect(zeile(panelEl).getByText('14,88 $')).toBeInTheDocument()
    expect(
      zeile(panelEl).getByText('gerechnet, unvollständig — ein Vorgang ohne Kostenmeldung'),
    ).toBeInTheDocument()
  })

  it('zeigt den Kennzahlen-Hinweis einmal am Lauf statt jeder Fehlanzeige (Punkt 11)', async () => {
    // Konstruiert: Keines der fünf Protokolle stammt aus einem Lauf ohne die ausführliche Ausgabe
    // — dabei ist genau das der Regelfall eines Umsetzungs-Laufs (Plan #864, E8).
    const MIT_HINWEIS = stand({
      kennzahlenHinweis: KENNZAHLEN_HINWEIS,
      einheiten: [
        einheit({ ausgang: 'erfolg', commit: 'a1b2c3d', pruefung: GEPRUEFT }),
        einheit({ id: '701', titel: 'Paket B', ausgang: 'fehlschlag', pruefung: NACHWEIS_ROT }),
      ],
    })
    const panelEl = await eingelesen(MIT_HINWEIS, startedAt(0), 'night-run.json', {
      submit: { ergebnis: alleNeu(MIT_HINWEIS) },
      listen: [[], wieAufbewahrt(MIT_HINWEIS)],
    })

    expect(screen.getAllByText(KENNZAHLEN_HINWEIS)).toHaveLength(1)
    expect(within(panelEl).queryByText('Kosten unbekannt')).not.toBeInTheDocument()
    expect(within(panelEl).queryByText('Züge unbekannt')).not.toBeInTheDocument()
    // Je Vorgang bleibt die gemessene Dauer stehen; die drei Fehlanzeigen für die nicht
    // angeforderten Kennzahlen entfallen.
    for (const nummer of [700, 701]) {
      const vorgang = within(panelEl).getByTestId(`kennzahlen-${nummer}`)
      expect(vorgang).toHaveTextContent('7 Min')
      expect(vorgang.textContent).not.toContain('nicht gemeldet')
    }
  })

  it('weist eine unvollständige Summe aus und kennt keine Null (Punkt 12)', async () => {
    // Konstruiert: Ein Umsetzungs-Lauf, in dem ein Vorgang Kennzahlen meldet und der andere nicht.
    const TEILWEISE = stand({
      einheiten: [
        einheit({ ausgang: 'erfolg', pruefung: GEPRUEFT, kennzahlen: { kostenUsd: 1.5, zuege: 7 } }),
        einheit({ id: '701', titel: 'Paket B', ausgang: 'fehlschlag', pruefung: NACHWEIS_ROT }),
      ],
    })
    const panelEl = await eingelesen(TEILWEISE, startedAt(0), 'night-run.json', {
      submit: { ergebnis: alleNeu(TEILWEISE) },
      listen: [[], wieAufbewahrt(TEILWEISE)],
    })

    expect(zeile(panelEl).getByText('1,50 $')).toBeInTheDocument()
    expect(
      zeile(panelEl).getByText('gerechnet, unvollständig — ein Vorgang ohne Kostenmeldung'),
    ).toBeInTheDocument()
  })

  it('sagt ohne jede Kostenangabe und ohne Hinweis „Kosten unbekannt" (Punkt 12)', async () => {
    const panelEl = await eingelesen(VIER_ZUSTAENDE, startedAt(0), 'night-run.json', {
      submit: { ergebnis: alleNeu(VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(VIER_ZUSTAENDE)],
    })

    // „0,00 $" behauptete eine Nacht ohne Kosten; gemeldet wurde nur nichts.
    expect(zeile(panelEl).getByText('Kosten unbekannt')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('Züge unbekannt')).toBeInTheDocument()
    expect(zeile(panelEl).queryByText(/0,00 \$/)).not.toBeInTheDocument()
    expect(zeile(panelEl).queryByText(/gerechnet/)).not.toBeInTheDocument()
    // Ohne den Kopf-Hinweis bleiben die Fehlanzeigen je Vorgang stehen: Hier fehlen die
    // Kennzahlen wirklich, statt gar nicht erst angefordert worden zu sein.
    expect(within(panelEl).getByTestId('kennzahlen-700')).toHaveTextContent('Kosten nicht gemeldet')
  })

  it('zeigt an einem Ketten-Lauf keine Kennzahlenzeile, sondern die Übersicht', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('uebersicht-fuss')

    // Die Kostensumme der Kette steht im Stand, statt gerechnet zu werden — deshalb trägt ihre
    // Fußzeile die Budgets der Kette und keine Züge des Modells.
    expect(within(panelEl).queryByTestId('fussangabe-Züge des Modells')).not.toBeInTheDocument()
    expect(fussangabe(panelEl, 'Kostenbudget je Kette')).toBeInTheDocument()
  })

  it('zeigt an einem aufbewahrten Run ohne Ergebnisstand keine Kennzahlenzeile', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [
              { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', durationMs: SIEBEN_MIN },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByText('Vorhaben: ohne')

    // Kosten und Züge bewahrt der Server nicht auf — eine Reihe aus lauter Fehlanzeigen wäre
    // dieselbe Wand, die der Kennzahlen-Hinweis vermeidet.
    expect(within(lauf(0)).queryByTestId('fussangabe-Züge des Modells')).not.toBeInTheDocument()
    expect(within(lauf(0)).getByTestId('fussangabe-Ergebnis der Schicht')).toBeInTheDocument()
  })
})

describe('NightRunPage — Rahmen des Entwurfs (#914)', () => {
  /** Zwei aufbewahrte Läufe, der spätere steht oben. */
  const zweiAufbewahrte = () => ({
    listen: [
      [
        aufbewahrt({
          id: 1,
          startedAt: startedAt(0),
          items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' as const }],
        }),
        aufbewahrt({
          id: 2,
          startedAt: startedAt(30),
          items: [{ id: 21, cardNumber: 701, title: 'Paket B', state: 'GREEN' as const }],
        }),
      ],
    ],
    karten: {
      700: karte({ id: 1, number: 700, title: 'Paket A' }),
      701: karte({ id: 2, number: 701, title: 'Paket B' }),
    },
  })

  it('zeigt den obersten Lauf offen und den zweiten zugeklappt', async () => {
    // AK 2: Kopf, Kennzahlenreihe und erster Vorgangsblock ohne Scrollen. Genau der oberste
    // Lauf, nicht alle (E7) — alle aufzuklappen löste die Anfragelawine aus, die A8 vermeidet.
    renderPage(zweiAufbewahrte())

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    expect(within(lauf(30)).getByRole('button', { expanded: true })).toBeInTheDocument()
    expect(within(lauf(0)).getByRole('button', { expanded: false })).toBeInTheDocument()
  })

  it('lädt die Herkunftskette des obersten Laufs genau einmal, auch bei erneutem Rendern', async () => {
    const { rerender } = renderPage(zweiAufbewahrte())

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    await waitFor(() => expect(byNumberAufrufe()).toHaveLength(1))

    rerender(
      <ThemeProvider theme={theme}>
        <SnackbarProvider>
          <MemoryRouter initialEntries={['/projects/5/nachtlauf']}>
            <Routes>
              <Route path="/projects/:projectId/nachtlauf" element={<NightRunPage />} />
            </Routes>
          </MemoryRouter>
        </SnackbarProvider>
      </ThemeProvider>,
    )

    // `geladeneLaeufe` ist die Stelle, die das sicherstellt — ein zweiter Abruf derselben Kette
    // wäre für den Nutzer unsichtbar und für den Server eine verdoppelte Last.
    await waitFor(() => expect(byNumberAufrufe()).toHaveLength(1))
  })

  it('legt das Theme des Entwurfs über den Inhaltsbereich', async () => {
    renderPage(zweiAufbewahrte())

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    const knopf = screen.getByRole('button', { name: 'Protokoll einlesen' })
    expect(getComputedStyle(knopf).fontFamily).toContain('IBM Plex Sans')
  })

  it('lässt den Kartendialog außerhalb des Theme-Teilbaums', async () => {
    renderPage(zweiAufbewahrte())

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    aufklappen(30)
    fireEvent.click(await within(lauf(30)).findByRole('button', { name: /#701/ }))

    // Nicht-Ziel 2: Der Dialog gehört zur übrigen Anwendung und trägt deren Designsprache.
    const detail = await screen.findByTestId('karten-detail')
    // Seit #978 setzt auch die Anwendung Plex Sans, aber mit eigenem Schriftstapel.
    expect(detail.dataset.schrift).toBe(theme.typography.fontFamily)
    expect(detail.dataset.schrift).not.toBe(NACHTLAUF_SCHRIFTEN.body)
  })

  it('hält Brotkrumenpfad, Protokoll-Knopf und die Meldung zu einem nicht deutbaren Stand', async () => {
    // AK 10: Wo der Entwurf kein Element vorsieht, bleibt es in seiner Funktion erhalten.
    renderPage({ listen: [[]] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    expect(screen.getByRole('link', { name: 'Projekte' })).toBeInTheDocument()
    // Das letzte Segment heißt seit #1151 „Runner" wie der Menüpunkt; der Pfad ist das `h1` der
    // Seite — ohne diese Eingrenzung träfe die Suche auch die Beschriftung im Verbrauchs-Bereich.
    expect(within(screen.getByRole('heading', { level: 1 })).getByText('Runner')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Protokoll einlesen' })).toBeInTheDocument()
    expect(screen.getByLabelText('Protokolldatei auswählen')).toBeInTheDocument()

    protokollWaehlen('{kein json')

    expect(await screen.findByText(/^Nicht auswertbar —/)).toBeInTheDocument()
  })
})

describe('NightRunPage — Kopf und Kennzahlenreihe der Nacht (#915)', () => {
  /** Liest einen Ergebnisstand ein und gibt das Panel seines Laufs zurück. */
  async function nachEinlesen(standText: string, start: string, dateiname: string) {
    renderPage({
      submit: { ergebnis: alleNeu(standText) },
      listen: [[], wieAufbewahrt(standText)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')
    protokollWaehlen(standText, dateiname)
    return await screen.findByTestId(`lauf-${start}`)
  }

  it('gibt dem Ketten-Lauf den Kopf des Entwurfs mit seiner Vorzeile', async () => {
    const panelEl = await nachEinlesen(
      ECHTE_KETTE_STAND,
      ECHTE_KETTE_START,
      'night-run-2026-09-14-131200.json',
    )

    expect(artSymbol(panelEl)).toHaveAccessibleName('Kette')
    expect(within(panelEl).getByTestId(FUSSZEILE)).toBeInTheDocument()
  })

  it('gibt dem Umsetzungs-Lauf den Kopf des Entwurfs mit seiner Vorzeile', async () => {
    const panelEl = await nachEinlesen(
      ECHTER_STAND,
      ECHTER_START,
      'night-run-2026-09-07-085229.json',
    )

    expect(artSymbol(panelEl)).toHaveAccessibleName('Umsetzung')
    expect(within(panelEl).getByTestId(FUSSZEILE)).toBeInTheDocument()
  })

  it('gibt dem Prüf-Lauf seit #988 denselben Kopf, behält aber seine Aufschlüsselung', async () => {
    const panelEl = await nachEinlesen(
      ECHTER_PRUEFLAUF_STAND,
      ECHTER_PRUEFLAUF_START,
      'night-run-2026-09-11-103116.json',
    )

    // Die Platte der Vorlage gilt seit #988 für **jede** Lauf-Art; was den Prüf-Lauf eigen macht,
    // ist die Aufschlüsselung seiner aussortierten Karten (#873), nicht mehr sein Kopf.
    expect(artSymbol(panelEl)).toHaveAccessibleName('Prüfung')
    expect(within(panelEl).getByTestId('aufschluesselung')).toBeInTheDocument()
    expect(within(panelEl).getByTestId(FUSSZEILE)).toBeInTheDocument()
  })

  it('gibt dem Erzeugungs-Lauf denselben Kopf und seine Aufschlüsselung', async () => {
    // Nachtplan-Läufe bleiben browser-only (Plan #803, Entscheidung 8) — sie gehen nicht an den
    // Server und kommen deshalb auch nicht aus der Liste zurück. Sie haben damit keine Id, und die
    // Vorzeile bleibt ohne Nummer (#1107).
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')
    protokollWaehlen(ECHTE_ERZEUGUNG_STAND, 'night-run-2026-09-09-125621.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_ERZEUGUNG_START}`)
    panelAufklappen(panelEl)

    expect(artSymbol(panelEl)).toHaveAccessibleName('Nachtplan')
    expect(await within(panelEl).findByTestId('aufschluesselung')).toBeInTheDocument()
    expect(within(panelEl).getByTestId(FUSSZEILE)).toBeInTheDocument()
  })

  it.each([
    ['Ketten-Lauf', ECHTE_KETTE_STAND, ECHTE_KETTE_START, 'night-run-2026-09-14-131200.json'],
    ['Umsetzungs-Lauf', ECHTER_STAND, ECHTER_START, 'night-run-2026-09-07-085229.json'],
  ])(
    'nennt die Herkunft des Stands im Kopf des %s (AK 9, Fall 3)',
    async (_art, standText, start, dateiname) => {
      const panelEl = await nachEinlesen(standText, start, dateiname)
      // Seit #988 als Marke rechts im Kopf (Vorlage Z. 388), nicht mehr in der Metazeile.
      expect(within(panelEl).getByTestId('lauf-stand')).toHaveTextContent('Ergebnisstand')
    },
  )

  it('vermerkt am Kostenwert der Kette die Sitzungen ohne Kostenmeldung (AK 9, Fall 1)', async () => {
    const panelEl = await nachEinlesen(
      ECHTE_KETTE_STAND,
      ECHTE_KETTE_START,
      'night-run-2026-09-14-131200.json',
    )

    expect(fussangabe(panelEl, 'Zur Kostensumme')).toHaveTextContent(/ohne Kostenmeldung/)
  })

  it('gibt einem aufbewahrten Ketten-Lauf ohne Stand denselben Kopf, aber keine Kennzahlen (E6)', async () => {
    renderPage({
      listen: [wieAufbewahrt(ECHTE_KETTE_STAND)],
    })
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)

    // Derselbe Kopf — die Gestaltung hängt an der Lauf-Art, nicht am Vorliegen eines Stands.
    expect(artSymbol(panelEl)).toHaveAccessibleName('Kette')
    // Kosten und Züge bewahrt der Server nicht auf; eine Reihe aus lauter Fehlanzeigen wäre
    // dieselbe Wand, die der Bestand schon vermeidet.
    expect(within(panelEl).queryByTestId('fussangabe-Ketten durchgelaufen')).not.toBeInTheDocument()
    expect(within(panelEl).getByTestId('lauf-stand')).toHaveTextContent('Herkunft unbekannt')
  })
})

describe('NightRunPage — Altbestand-Arten im Kopf der Vorlage (#988)', () => {
  it('nennt ungedeutete Zeilen eines Prüf-Laufs in der Metazeile', async () => {
    // Bis #988 war das ein eigener Chip. Die Vorlage sieht dafür kein Element vor; die Angabe
    // bleibt in ihrer Funktion erhalten und steht in der Metazeile (AK 10).
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            mode: 'REVIEW',
            unparsedCount: 1,
            unparsedSample: UNGEDEUTET,
            items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
          }),
        ],
      ],
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    expect(within(lauf(0)).getByTestId('nachtlauf-meta')).toHaveTextContent(
      'Ungedeutete Zeilen: 1',
    )
    expect(artSymbol(lauf(0))).toHaveAccessibleName('Prüfung')
  })

  it('zeigt den Kennzahlen-Hinweis eines Prüf-Laufs in seiner Fußzeile', async () => {
    // Der echte Prüf-Lauf, ergänzt um den Hinweis: Seine Ausgänge sind prüfungseigen, ein
    // selbstgebauter Stand mit `ausgang: 'erfolg'` wäre gar nicht deutbar.
    const PRUEFUNG_MIT_HINWEIS = JSON.stringify({
      ...echterPrueflauf,
      kennzahlenHinweis: 'Kennzahlen nicht angefordert',
    })
    renderPage({
      submit: { ergebnis: alleNeu(PRUEFUNG_MIT_HINWEIS) },
      listen: [[], wieAufbewahrt(PRUEFUNG_MIT_HINWEIS)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PRUEFUNG_MIT_HINWEIS, 'night-run-2026-09-11-103116.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTER_PRUEFLAUF_START}`)
    panelAufklappen(panelEl)

    expect(await within(panelEl).findByTestId('fussangabe-Kennzahlen')).toHaveTextContent(
      'Kennzahlen nicht angefordert',
    )
  })

  it('kennzeichnet einen schon bekannten Prüf-Lauf weiter mit seinem eigenen Chip', async () => {
    // Die Gegenprobe zu „neu angelegt": Auch der zweite Zustand des Einlieferungs-Chips gehört zu
    // den beiden Altbestand-Arten und bleibt dort in seiner bisherigen Form.
    renderPage({
      submit: { ergebnis: [{ startedAt: ECHTER_PRUEFLAUF_START, created: false }] },
      listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_PRUEFLAUF_STAND, 'night-run-2026-09-11-103116.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTER_PRUEFLAUF_START}`)

    expect(await within(panelEl).findByText('lag schon vor')).toBeInTheDocument()
  })
})

describe('NightRunPage — Vorgangsblock der Kette (#916)', () => {
  /** Der echte Ketten-Lauf, eingelesen und aufgeklappt. */
  async function kette(extra: Partial<Antworten> = {}) {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
      ...extra,
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')
    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    panelAufklappen(panelEl)
    return panelEl
  }

  it('gibt jedem Vorgang einen eigenen Block mit Nummer, Titel und Ausgang (AK 4)', async () => {
    await kette()

    const block = await screen.findByTestId('paket-791')
    expect(block).toHaveTextContent('#791')
    expect(block).toHaveTextContent('Zugriff und Konten bleiben nach Widerruf')
    expect(within(block).getByTestId('zustand-791')).toHaveTextContent('Erfolg')
  })

  it('nennt den Grund eines an der Zeitgrenze beendeten Vorgangs in Worten (AK 7)', async () => {
    await kette()

    // Auffindbar ohne jede Farbwahrnehmung: Der Satz steht als Text unter der Kopfzeile.
    const grund = await screen.findByTestId('abbruch-842')
    expect(grund).toHaveTextContent('Zeitbudget')
    expect(within(screen.getByTestId('paket-842')).getByTestId('zustand-842')).toHaveTextContent(
      'Am Zeitbudget beendet',
    )
  })

  it('unterscheidet einen nie erreichten Arbeitsschritt an einem Wort (AK 8)', async () => {
    await kette()

    await screen.findByTestId('stufe-842-pakete')
    // Der nie erreichte Schritt sagt es; der erreichte sagt seine Zeiten.
    expect(screen.getByTestId('stufe-842-pakete')).toHaveTextContent('nicht erreicht')
    expect(screen.getByTestId('stufe-842-pakete')).toHaveAttribute('data-erreicht', 'nein')
    expect(screen.getByTestId('stufe-842-plan')).not.toHaveTextContent('nicht erreicht')
    expect(screen.getByTestId('stufe-842-plan')).toHaveAttribute('data-erreicht', 'ja')
  })

  it('führt den Anteil der Modellarbeit in der Ergebniszeile jedes Vorgangs (AK 6)', async () => {
    await kette()

    expect(await screen.findByTestId('kennzahlen-791')).toHaveTextContent(/Modell(arbeit|zeit)/)
  })

  it('zeigt die entstandenen Karten als Chips und öffnet die angeklickte', async () => {
    await kette({
      karten: {
        791: karte({ id: 1, number: 791, title: 'Konten' }),
        844: karte({ id: 2, number: 844, title: '[Plan] Sicherheits-Bauform' }),
      },
    })

    const chip = await screen.findByRole('button', { name: /^Plan #844 / })
    fireEvent.click(chip)

    expect(screen.getByTestId('karten-detail')).toHaveTextContent('Karte 844')
  })

  it('hält Häufigkeit, Vorhaben-Zeile und Übernahmetext am Block (AK 10)', async () => {
    await kette({
      karten: { 842: karte({ id: 1, number: 842, title: 'Leitstand mehrstufig', parentId: 5 }) },
      kartenNachId: { 5: vorhaben({ id: 5, number: 9, title: 'Leitstand ausbauen' }) },
    })

    const block = await screen.findByTestId('paket-842')
    expect(await within(block).findByRole('button', { name: /^Vorhaben #9 / })).toBeInTheDocument()
    expect(uebernahmetext(block, 842)).toContain('Zustand: Am Zeitbudget beendet')
    expect(
      within(block).getByRole('button', { name: 'Übernahmetext zu Karte #842 kopieren' }),
    ).toBeInTheDocument()
  })

  it('nennt an einem übergangenen Vorgang ohne Ergebnisstand den Grund, nicht einen Auszug', async () => {
    // Ohne Stand gibt es kein Band; dann trägt der Auszug die Herkunft (AK 10). An einem grauen
    // Vorgang heißt er „Grund", weil er sagt, warum nichts geschah — nicht, was geschah.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            mode: 'CHAIN',
            items: [
              {
                id: 11,
                cardNumber: 700,
                title: 'Paket A',
                state: 'GREY',
                excerpt: GRUND_ZURUECKGESTELLT,
              },
            ],
          }),
        ],
      ],
    })
    const panelEl = await screen.findByTestId(`lauf-${startedAt(0)}`)

    vorgaengeAufklappen(panelEl)

    expect(within(panelEl).getByText(`Grund: ${GRUND_ZURUECKGESTELLT}`)).toBeInTheDocument()
  })

  it('lässt den Prüf-Lauf bei der Zeilendarstellung, ohne Vorgangsblock (E5)', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_PRUEFLAUF_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')
    protokollWaehlen(ECHTER_PRUEFLAUF_STAND, 'night-run-2026-09-11-103116.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTER_PRUEFLAUF_START}`)
    panelAufklappen(panelEl)

    await within(panelEl).findByTestId('aufschluesselung')
    expect(within(panelEl).queryByTestId('stufenband-782')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Anteilsbalken des Umsetzungs-Laufs (#917)', () => {
  /** Der echte Umsetzungs-Lauf vom 7. September, eingelesen und aufgeklappt. */
  async function umsetzung() {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')
    protokollWaehlen(ECHTER_STAND, 'night-run-2026-09-07-085229.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTER_START}`)
    panelAufklappen(panelEl)
    return panelEl
  }

  it('gibt jedem Umsetzungs-Vorgang einen Anteilsbalken und kein Stufenband', async () => {
    const panelEl = await umsetzung()

    expect(await within(panelEl).findByTestId('laufband-abschnitt-767')).toBeInTheDocument()
    expect(within(panelEl).queryByTestId('stufenband-767')).not.toBeInTheDocument()
  })

  it('gibt jedem Ketten-Vorgang ein Stufenband und keinen Anteilsbalken', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')
    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    panelAufklappen(panelEl)

    expect(await within(panelEl).findByTestId('stufenband-791')).toBeInTheDocument()
    expect(within(panelEl).queryByTestId('laufband-abschnitt-791')).not.toBeInTheDocument()
  })

  it('misst den Anteil an der Summe der Vorgangsdauern und nicht an der Laufdauer', async () => {
    // Die Laufdauer weicht hier absichtlich von der Summe ab: Bezöge sich die Breite auf sie,
    // stünden hier 7 und 21 statt 25 und 75.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            durationMs: 4 * 7 * 60_000,
            items: [
              { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', durationMs: SIEBEN_MIN },
              {
                id: 12,
                cardNumber: 701,
                title: 'Paket B',
                state: 'GREEN',
                durationMs: 3 * SIEBEN_MIN,
              },
            ],
          }),
        ],
      ],
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    vorgaengeAufklappen(lauf(0))

    expect(screen.getByTestId('laufband-abschnitt-700')).toHaveAttribute('data-anteil', '25')
    expect(screen.getByTestId('laufband-abschnitt-701')).toHaveAttribute('data-anteil', '75')
  })

  it('zeigt bei nur einem gemessenen Vorgang keinen Balken, aber dessen Angaben', async () => {
    // Ein Balken mit einem einzigen Abschnitt behauptet ein Verhältnis, das es nicht gibt.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [
              { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', durationMs: SIEBEN_MIN },
              { id: 12, cardNumber: 701, title: 'Paket B', state: 'GREY' },
            ],
          }),
        ],
      ],
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    vorgaengeAufklappen(lauf(0))

    expect(screen.queryAllByTestId(/^laufband-abschnitt-/)).toHaveLength(0)
    expect(screen.getByTestId('paket-700')).toHaveTextContent('Paket A')
    expect(screen.getByTestId('kennzahlen-700')).toHaveTextContent('7 Min')
  })

  it('hält Ausgang, Auszug, Häufigkeit und Modellzeit am Umsetzungs-Vorgang (AK 6)', async () => {
    const panelEl = await umsetzung()

    const block = await within(panelEl).findByTestId('paket-769')
    expect(within(block).getByTestId('zustand-769')).toHaveTextContent('gescheitert')
    expect(within(block).getByText(/^Auszug: /)).toBeInTheDocument()
    expect(within(block).getByTestId('kennzahlen-769')).toHaveTextContent(/Modell(arbeit|zeit)/)
  })

  it('öffnet an einem Prüf-Lauf die Wurzelkarte aus der Zeilendarstellung (AK 10)', async () => {
    // Die Zeilendarstellung der beiden Altbestand-Arten trägt den Verweis auf die Wurzelkarte
    // weiterhin selbst — die Vorgangsblöcke haben ihren eigenen.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            mode: 'REVIEW',
            items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    fireEvent.click(await within(lauf(0)).findByRole('button', { name: '#700 Paket A' }))

    expect(screen.getByTestId('karten-detail')).toHaveTextContent('Karte 700')
  })

  it('lässt einen aufbewahrten Prüf-Lauf bei seiner Darstellung samt Ableitungstabelle', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_PRUEFLAUF_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')
    protokollWaehlen(ECHTER_PRUEFLAUF_STAND, 'night-run-2026-09-11-103116.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTER_PRUEFLAUF_START}`)
    panelAufklappen(panelEl)

    expect(await within(panelEl).findByTestId('aufschluesselung')).toBeInTheDocument()
    expect(within(panelEl).getByTestId('kennzahlen-782')).toBeInTheDocument()
    expect(within(panelEl).queryAllByTestId(/^laufband-abschnitt-/)).toHaveLength(0)
  })
})

describe('NightRunPage — Fußzeile beider Lauf-Arten (#918)', () => {
  const fuss = () => within(screen.getByTestId('uebersicht-fuss'))

  async function eingelesenerLauf(standText: string, start: string, dateiname: string) {
    renderPage({
      submit: { ergebnis: alleNeu(standText) },
      listen: [[], wieAufbewahrt(standText)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')
    protokollWaehlen(standText, dateiname)
    const panelEl = await screen.findByTestId(`lauf-${start}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('uebersicht-fuss')
    return panelEl
  }

  it('führt am Ketten-Lauf Zeitvorgaben, Kostenbudget und höchste Kosten', async () => {
    await eingelesenerLauf(ECHTE_KETTE_STAND, ECHTE_KETTE_START, 'night-run-2026-09-14-131200.json')

    expect(fuss().getByText('Plan 20 · Prüfung 15 · Pakete 15 · Abdeckung 10 min')).toBeInTheDocument()
    expect(fuss().getByText('50,00 $')).toBeInTheDocument()
    expect(fuss().getByText('11,52 $')).toBeInTheDocument()
  })

  it('nennt die Herkunft der Budgets „nicht angegeben", ohne zu warnen (AK 9, Fall 2)', async () => {
    // Der Ergebnisstand führt das Feld heute nicht (E12). Weggelassen ließe die Zeile offen, ob
    // nichts vorlag oder nichts nachgesehen wurde — und eine fehlende Angabe ist keine Warnung.
    await eingelesenerLauf(ECHTE_KETTE_STAND, ECHTE_KETTE_START, 'night-run-2026-09-14-131200.json')

    expect(fuss().getByText('Herkunft der Budgets')).toBeInTheDocument()
    const wert = fuss().getByText('nicht angegeben')
    // Die Gegenprobe im selben Fuß: Der Vermerk zur Kostensumme **ist** ein Vorbehalt und trägt
    // den Bernstein-Melder, die fehlende Angabe daneben nicht.
    const vorbehalt = fuss().getByText(/ohne Kostenmeldung/)
    expect(getComputedStyle(vorbehalt).color).toBe(MELDER.bernst)
    expect(getComputedStyle(wert).color).not.toBe(MELDER.bernst)
  })

  it('führt am Umsetzungs-Lauf Ergebnis, teuersten Vorgang und Herkunft des Stands', async () => {
    await eingelesenerLauf(ECHTER_STAND, ECHTER_START, 'night-run-2026-09-07-085229.json')

    expect(fuss().getByText('Ergebnis der Schicht')).toBeInTheDocument()
    expect(fuss().getByText(/bearbeitet · .* übergangen/)).toBeInTheDocument()
    expect(fuss().getByText('Teuerster Vorgang')).toBeInTheDocument()
    expect(fuss().getByText(/^#\d+ mit \d/)).toBeInTheDocument()
    expect(fuss().getByText('Herkunft des Stands')).toBeInTheDocument()
    expect(fuss().getByText('Ergebnisstand')).toBeInTheDocument()
  })

  it('zeigt an einem aufbewahrten Run ohne Stand die Fußzeile mit Fehlanzeigen', async () => {
    renderPage({ listen: [wieAufbewahrt(ECHTE_KETTE_STAND)] })
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)

    // Vorgaben und Budget stehen allein im Ergebnisstand; die Zeile benennt das, statt zu fehlen.
    expect(await within(panelEl).findByTestId('uebersicht-fuss')).toBeInTheDocument()
    expect(fuss().getAllByText('nicht angegeben').length).toBeGreaterThanOrEqual(3)
  })

  it('gibt seit #988 auch einem Prüf-Lauf eine Fußzeile, mit seinen eigenen Angaben', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_PRUEFLAUF_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')
    protokollWaehlen(ECHTER_PRUEFLAUF_STAND, 'night-run-2026-09-11-103116.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTER_PRUEFLAUF_START}`)
    panelAufklappen(panelEl)

    await within(panelEl).findByTestId('aufschluesselung')
    // Die Platte der Vorlage gilt für jede Lauf-Art; der Prüf-Lauf führt in seinem Fuß die
    // art-eigene Kennzahl „Karten bearbeitet" statt der Budgets einer Kette.
    const fussEl = within(panelEl).getByTestId('uebersicht-fuss')
    expect(within(fussEl).getByTestId('fussangabe-Karten bearbeitet')).toBeInTheDocument()
    expect(within(fussEl).queryByTestId('fussangabe-Kostenbudget je Kette')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Fußzeile eines Kettenlaufs aus der Server-Antwort (#1115)', () => {
  /**
   * Die Budgets, wie der Token-Weg sie seit Issue #1113 meldet. Jedes Feld ist einzeln
   * überschreibbar — dieselbe Bauform wie {@link verbraucht}.
   */
  const budget = (felder: Partial<NightRunBudgetView> = {}): NightRunBudgetView => ({
    planMin: 20,
    reviewMin: 15,
    paketeMin: 15,
    abdeckungMin: 10,
    kostenUsd: 50,
    origin: 'CONFIGURED',
    defaultFields: [],
    ...felder,
  })

  /**
   * Ein aufbewahrter Kettenlauf **ohne** eingelesene Datei — der Normalweg des Runners. Kein
   * `staende`-Eintrag, kein `protokollWaehlen`: Genau dort stand bis zu diesem Paket viermal
   * „nicht angegeben" (AK 1).
   */
  const kettenLauf = (
    felder: Omit<Partial<NightRunView>, 'items'> & { items?: ItemVorgabe[] } = {},
  ): NightRunView =>
    aufbewahrt({
      id: 1,
      startedAt: startedAt(0),
      mode: 'CHAIN',
      processedCount: 2,
      budget: budget(),
      items: [
        {
          id: 11,
          cardNumber: 700,
          title: 'Paket A',
          state: 'GREEN',
          usage: verbraucht({ costUsd: 11.52 }),
        },
        {
          id: 12,
          cardNumber: 701,
          title: 'Paket B',
          state: 'GREEN',
          usage: verbraucht({ costUsd: 4.09 }),
        },
      ],
      ...felder,
    })

  /** Die Fußzeile des Laufs, ohne dass je eine Datei eingelesen wurde. */
  async function fussZu(view: NightRunView) {
    renderPage({ listen: [[view]] })
    const panelEl = await screen.findByTestId(`lauf-${view.startedAt}`)
    panelAufklappen(panelEl)
    return within(await within(panelEl).findByTestId(FUSSZEILE))
  }

  it('nennt Zeitvorgaben, Kostenbudget, höchste Kosten und Herkunft ohne eingelesene Datei (AK 1)', async () => {
    const fuss = await fussZu(kettenLauf())

    expect(fuss.getByText('Plan 20 · Prüfung 15 · Pakete 15 · Abdeckung 10 min')).toBeInTheDocument()
    expect(fuss.getByText('50,00 $')).toBeInTheDocument()
    expect(fuss.getByText('11,52 $')).toBeInTheDocument()
    expect(fuss.getByText('eingestellt')).toBeInTheDocument()
    // Der Kern des Anlassfalls: Keine der vier Angaben ist mehr eine Fehlanzeige.
    expect(fuss.queryByText('nicht angegeben')).not.toBeInTheDocument()
  })

  it('sagt „eingestellt", wenn alle Vorgaben aus der Konfiguration stammen (AK 3, Fall 1)', async () => {
    const fuss = await fussZu(kettenLauf({ budget: budget({ origin: 'CONFIGURED' }) }))

    expect(fuss.getByTestId('fussangabe-Herkunft der Budgets')).toHaveTextContent('eingestellt')
    expect(fuss.queryByText(/Voreinstellungen/)).not.toBeInTheDocument()
  })

  it('nennt bei „aus Voreinstellungen" genau die betroffenen Felder (AK 3, Fall 2)', async () => {
    const fuss = await fussZu(
      kettenLauf({
        budget: budget({ origin: 'DEFAULTED', defaultFields: ['kostenUsd', 'planMin'] }),
      }),
    )

    // Die Reihenfolge ist die des Laufs, nicht eine des Boards: Was er meldet, steht so da.
    expect(
      fuss.getByText('aus Voreinstellungen: Kostenbudget, Zeitvorgabe Plan'),
    ).toBeInTheDocument()
  })

  it('übersetzt alle fünf Feldnamen in die Worte aus AK 3', async () => {
    const fuss = await fussZu(
      kettenLauf({
        budget: budget({
          origin: 'DEFAULTED',
          defaultFields: ['planMin', 'reviewMin', 'paketeMin', 'abdeckungMin', 'kostenUsd'],
        }),
      }),
    )

    expect(
      fuss.getByText(
        'aus Voreinstellungen: Zeitvorgabe Plan, Zeitvorgabe Prüfung, Zeitvorgabe Pakete, Zeitvorgabe Abdeckung, Kostenbudget',
      ),
    ).toBeInTheDocument()
  })

  it('lässt einen Feldnamen aus, den das Board nicht kennt (E4, E11)', async () => {
    // `ketteBudgetDefaults` führt zehn Felder; die Fußzeile zeigt fünf. Ein fremder Name wird
    // angenommen und hier ausgelassen — abgewiesen kostete den ganzen Lauf statt einer Zeile.
    const fuss = await fussZu(
      kettenLauf({
        budget: budget({ origin: 'DEFAULTED', defaultFields: ['umsetzungMin', 'kostenUsd'] }),
      }),
    )

    expect(fuss.getByText('aus Voreinstellungen: Kostenbudget')).toBeInTheDocument()
    expect(fuss.queryByText(/umsetzungMin/)).not.toBeInTheDocument()
  })

  it('bleibt bei lauter unbekannten Feldnamen bei „aus Voreinstellungen" ohne Aufzählung', async () => {
    const fuss = await fussZu(
      kettenLauf({
        budget: budget({
          origin: 'DEFAULTED',
          defaultFields: ['varianteBLabel', 'korrekturrunden'],
        }),
      }),
    )

    expect(fuss.getByTestId('fussangabe-Herkunft der Budgets')).toHaveTextContent(
      'Herkunft der Budgetsaus Voreinstellungen',
    )
    expect(fuss.queryByText(/aus Voreinstellungen:/)).not.toBeInTheDocument()
  })

  it('sagt „nicht angegeben", wenn der Lauf keine Herkunft meldet (AK 3, Fall 3)', async () => {
    // Ein Kit-Stand vor Issue #1113 meldet Vorgaben ohne Herkunft. Die Werte stehen da, die
    // Herkunft nicht — geraten würde sie sonst als „eingestellt".
    const fuss = await fussZu(kettenLauf({ budget: budget({ origin: null }) }))

    expect(fuss.getByTestId('fussangabe-Herkunft der Budgets')).toHaveTextContent('nicht angegeben')
    expect(fuss.getByText('50,00 $')).toBeInTheDocument()
  })

  it('zeigt an einem Lauf ohne Budget weiter „nicht angegeben" statt 0 (AK 5)', async () => {
    const fuss = await fussZu(kettenLauf({ budget: null }))

    expect(fuss.getByTestId('fussangabe-Zeitvorgaben je Kette')).toHaveTextContent('nicht angegeben')
    expect(fuss.getByTestId('fussangabe-Kostenbudget je Kette')).toHaveTextContent('nicht angegeben')
    expect(fuss.getByTestId('fussangabe-Herkunft der Budgets')).toHaveTextContent('nicht angegeben')
    expect(fuss.queryByText('0,00 $')).not.toBeInTheDocument()
    expect(fuss.queryByText('0 min')).not.toBeInTheDocument()
  })

  it('lässt ein nur teilweise gemeldetes Budget feldweise fehlen, statt 0 zu zeigen (AK 5)', async () => {
    // Der Runner meldet, was er hat. Hier fehlen die Zeitvorgabe der Prüfung und das
    // Kostenbudget — eine 0 daneben behauptete, die Stufe habe keine Zeit bekommen.
    const fuss = await fussZu(kettenLauf({ budget: budget({ reviewMin: null, kostenUsd: null }) }))

    expect(fuss.getByText('Plan 20 · Pakete 15 · Abdeckung 10 min')).toBeInTheDocument()
    expect(fuss.getByTestId('fussangabe-Kostenbudget je Kette')).toHaveTextContent(
      'nicht angegeben',
    )
    expect(fuss.queryByText('0,00 $')).not.toBeInTheDocument()
  })

  it('trägt an den höchsten Kosten keinen Zusatz, wenn alle Vorgänge Kosten tragen (E10)', async () => {
    const fuss = await fussZu(kettenLauf())

    expect(fuss.getByTestId('fussangabe-Höchste Kosten eines Vorgangs')).toHaveTextContent(
      'Höchste Kosten eines Vorgangs11,52 $',
    )
    expect(fuss.queryByText(/aus \d+ von \d+ Vorgängen/)).not.toBeInTheDocument()
  })

  it('nennt an den höchsten Kosten „aus n von m Vorgängen", sobald eine Meldung fehlt (E10)', async () => {
    const fuss = await fussZu(
      kettenLauf({
        items: [
          {
            id: 11,
            cardNumber: 700,
            title: 'Paket A',
            state: 'GREEN',
            usage: verbraucht({ costUsd: 11.52 }),
          },
          { id: 12, cardNumber: 701, title: 'Paket B', state: 'GREY' },
        ],
      }),
    )

    expect(fuss.getByText('11,52 $ (aus 1 von 2 Vorgängen)')).toBeInTheDocument()
  })

  it('sagt „nicht angegeben", wenn kein einziger Vorgang Kosten trägt', async () => {
    const fuss = await fussZu(
      kettenLauf({ items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }] }),
    )

    expect(fuss.getByTestId('fussangabe-Höchste Kosten eines Vorgangs')).toHaveTextContent(
      'nicht angegeben',
    )
  })
})

describe('NightRunPage — Laufblock im Leitstand-Stil (#988)', () => {
  /** Ein Lauf mit allem, was eine Vorgangszeile der Vorlage zeigt. */
  const mitAllem = () => ({
    listen: [
      [
        aufbewahrt({
          id: 1,
          startedAt: startedAt(0),
          durationMs: 4 * 3_600_000 + 12 * 60_000,
          processedCount: 3,
          skippedCount: 0,
          usage: verbraucht({
            costUsd: 12.4,
            inputTokens: 4_820_000,
            outputTokens: 186_000,
            cachedInputTokens: 3_663_200,
          }),
          items: [
            {
              id: 11,
              cardNumber: 917,
              title: 'Anteilsbalken je Vorgang',
              state: 'GREEN' as const,
              durationMs: 25 * 60_000,
              commitHash: '9489421abcdef',
              usage: verbraucht({ costUsd: 2.3 }),
            },
            {
              id: 12,
              cardNumber: 922,
              title: 'Spaltenbreite der Vorgangsliste',
              state: 'RED' as const,
              errorClass: 'CHECKS_RED' as const,
              durationMs: 42 * 60_000 + 15_000,
              excerpt: '2 Tests rot in BoardViewTest\nweitere Zeile',
            },
            {
              id: 13,
              cardNumber: 925,
              title: 'Vorhaben-Kürzel im Kartenkopf',
              state: 'YELLOW' as const,
              errorClass: 'AWAITING_DECISION' as const,
            },
          ],
        }),
      ],
    ],
    karten: {
      917: karte({ id: 1, number: 917, title: 'Anteilsbalken je Vorgang' }),
      922: karte({ id: 2, number: 922, title: 'Spaltenbreite der Vorgangsliste' }),
      925: karte({ id: 3, number: 925, title: 'Vorhaben-Kürzel im Kartenkopf' }),
    },
  })

  it('gibt dem Lauf einen Titel von 15 px statt der bisherigen großen Überschrift', async () => {
    // Der Kern des Auftrags (Manne, 2026-09-17): Die Überschrift „Nacht vom 14. September" stand
    // in 40 px über jedem Lauf. Die Vorlage führt sie in 15 px (Z. 362).
    renderPage(mitAllem())
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    // Der Tag hängt an der Zeitzone des Laufs: `startedAt(0)` ist 22:00 UTC, in Berlin schon der
    // 2., in der CI (UTC) noch der 1. September. Geprüft wird die Form, nicht der Kalendertag.
    const titel = within(lauf(0)).getByTestId('nachtlauf-ueberschrift')
    expect(titel).toHaveTextContent(/^Run #1 · [12]\. September, \d\d:00$/)
    expect(titel).toHaveStyle({ fontSize: '15px' })
  })

  it('führt Kopf mit Art, Titel, Metazeile und Herkunft — und die LED nach Ergebnis', async () => {
    renderPage(mitAllem())
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    const kopf = laufKopfzeile(lauf(0))
    expect(artSymbol(kopf)).toHaveAccessibleName('Umsetzung')
    expect(within(kopf).getByTestId('nachtlauf-meta')).toHaveTextContent(
      '4 h 12 min · 3 bearbeitet · 0 übergangen',
    )
    expect(within(kopf).getByTestId('lauf-stand')).toHaveTextContent('Herkunft unbekannt')
    // Ein Lauf mit einem gescheiterten Vorgang meldet Zinnober — dieselbe Rechnung wie im
    // Leitstand (`laufMelder`).
    expect(within(kopf).getByTestId('led-zinnob')).toBeInTheDocument()
  })

  it('zeigt aufgeklappt die sechs Instrumente der Vorlage', async () => {
    renderPage(mitAllem())
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    const panel = within(lauf(0))
    expect(panel.getByTestId('instrument-kosten-wert')).toHaveTextContent('12,40 $')
    expect(panel.getByTestId('instrument-eingabe-wert')).toHaveTextContent('4,82 Mio')
    expect(panel.getByTestId('instrument-ausgabe-wert')).toHaveTextContent('186 Tsd')
    expect(panel.getByTestId('instrument-cache-wert')).toHaveTextContent('76 %')
    expect(panel.getByTestId('instrument-dauer-wert')).toHaveTextContent('4:12 h')
    // Die Pakete nach Zustand, alle drei Zahlen in einem Feld (Vorlage Z. 398).
    expect(panel.getByTestId('instrument-pakete-wert')).toHaveTextContent('1 grün 1 gelb 1 rot')
  })

  it('führt je Vorgangszeile Dauer, Kosten und Commit — und „—" ohne Messung', async () => {
    renderPage(mitAllem())
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    const panel = within(lauf(0))
    expect(panel.getByTestId('dauer-917')).toHaveTextContent('25:00')
    expect(panel.getByTestId('kosten-917')).toHaveTextContent('2,30 $')
    expect(panel.getByTestId('commit-917')).toHaveTextContent('9489421')
    // Der Abbruch hat keine Kostenmeldung und keinen Commit.
    expect(panel.getByTestId('dauer-922')).toHaveTextContent('42:15')
    expect(panel.getByTestId('kosten-922')).toHaveTextContent('—')
    expect(panel.queryByTestId('commit-922')).not.toBeInTheDocument()
  })

  it('nennt an einem Abbruch Fehlerklasse und Auszug in der Zeile, einzeilig', async () => {
    renderPage(mitAllem())
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    const zeile = within(lauf(0)).getByTestId('vorgang-taste-922')
    expect(zeile).toHaveTextContent('CHECKS_RED')
    // Nur die erste Zeile des Auszugs: Mehr passt in eine Zeile nicht (`ersteZeile`).
    expect(zeile).toHaveTextContent('2 Tests rot in BoardViewTest')
    expect(zeile).not.toHaveTextContent('weitere Zeile')
  })

  it('schreibt die Zeile „Kosten: nicht gemessen · Eingabe: …" nirgends mehr auf die Seite', async () => {
    // Entscheidung Manne 2026-09-17: Ihre Werte stehen in Instrumenten bzw. Kostenspalte.
    renderPage(mitAllem())
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    vorgaengeAufklappen(lauf(0))

    expect(lauf(0).textContent).not.toContain('Kosten: nicht gemessen')
    expect(lauf(0).textContent).not.toContain('Zwischenspeicher:')
  })

  it('klappt den Befund eines Abbruchs an seiner Zeile auf und wieder zu', async () => {
    renderPage(mitAllem())
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    const panel = within(lauf(0))
    expect(panel.queryByTestId('befund-922')).not.toBeInTheDocument()

    fireEvent.click(panel.getByTestId('vorgang-taste-922'))

    expect(panel.getByTestId('befund-922')).toHaveTextContent('Befund des Runs zu Karte #922')
    expect(
      panel.getByRole('button', { name: 'Übernahmetext zu Karte #922 kopieren' }),
    ).toBeInTheDocument()

    fireEvent.click(panel.getByTestId('vorgang-taste-922'))

    expect(panel.queryByTestId('befund-922')).not.toBeInTheDocument()
  })

  it('gibt einem grünen Vorgang keinen Befund, wohl aber seine Einzelheiten', async () => {
    renderPage(mitAllem())
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    fireEvent.click(within(lauf(0)).getByTestId('vorgang-taste-917'))

    expect(within(lauf(0)).getByTestId('einzelheiten-917')).toBeInTheDocument()
    expect(within(lauf(0)).queryByTestId('befund-917')).not.toBeInTheDocument()
  })

  it('stellt die Laufblöcke in den Kupferwarte-Bereich, die übrige Seite in die Ausnahme', async () => {
    // `CLAUDE-design.md`: Die Laufblöcke haben die Nachtlauf-Ausnahme verlassen; Brotkrumenpfad,
    // „Protokoll einlesen" und die Nachtansicht bleiben darin.
    renderPage(mitAllem())
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    const bereiche = screen.getAllByTestId('kupferwarte-bereich')
    // Zwei: die Zeitraum-Sicht des Verbrauchs (#987) und die Laufblöcke (#988).
    expect(bereiche).toHaveLength(2)
    expect(bereiche[1]).toContainElement(lauf(0))
  })
})

describe('NightRunPage — Lauf ohne Arbeit (#1069)', () => {
  const GRUND = 'Kein Eintrag trug das Label kit:nightrun'

  /**
   * Seit #1121 ist der Melder grau: Der Lauf fand nichts zu tun, und das ist kein Mangel. Den Sinn
   * traegt der Grund neben ihm — die Marke nennt ihn woertlich.
   */
  it('zeigt am Lauf ohne Arbeit eine Zustandsmarke mit grauem Melder und dem Grund', async () => {
    renderPage({
      listen: [
        [aufbewahrt({ id: 1, startedAt: startedAt(0), processedCount: 0, noWorkReason: GRUND })],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    // Gezielt auf die Zustandsmarke: Der Kopf traegt zusaetzlich die LED der Laufplatte selbst,
    // und ein Zaehlen ueber beide sagte nicht, dass die Marke die ihre hat.
    const kopf = laufKopfzeile(lauf(0))
    const marke = within(kopf).getByTestId('lauf-zustand')
    expect(marke).toHaveTextContent(GRUND)
    expect(within(marke).getByTestId('led-grau')).toBeInTheDocument()
    expect(within(kopf).queryAllByTestId('led-zinnob')).toHaveLength(0)
  })

  /**
   * Der Rueckfall des Servers bleibt rot (#1121): Er steht fuer einen alten Runner, den Upload-Weg
   * oder einen Lauf, der alle Pakete zurueckstellte — dahinter kann ein echtes Problem stecken.
   */
  it('zeigt am Lauf ohne Arbeit mit unbekanntem Grund weiterhin den roten Melder', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            processedCount: 0,
            noWorkReason: GRUND_UNBEKANNT,
          }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    const marke = within(laufKopfzeile(lauf(0))).getByTestId('lauf-zustand')
    expect(marke).toHaveTextContent(GRUND_UNBEKANNT)
    expect(within(marke).getByTestId('led-zinnob')).toBeInTheDocument()
  })


  /**
   * Die beiden Marken schliessen einander aus: Ein Lauf ist entweder noch nicht abgeschlossen
   * oder ohne Arbeit beendet. Beide zugleich waeren ein Widerspruch im Kopf derselben Platte.
   */
  it('zeigt am unvollstaendigen Lauf weiterhin nur „unvollständig gemeldet"', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            complete: false,
            processedCount: 0,
            noWorkReason: GRUND,
          }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    const kopf = laufKopfzeile(lauf(0))
    expect(kopf).toHaveTextContent('unvollständig')
    expect(kopf).not.toHaveTextContent(GRUND)
  })
})

describe('NightRunPage — der Abbruchgrund in der Auswertung (#1145)', () => {
  /** Die erste Zeile des Grunds — länger als {@link KURZ_GRUND_MAX}, damit die Marke kürzen muss. */
  const ERSTE_ZEILE =
    'Harter Stopp (dirty-tree) — der Working Tree trug nach der Runde unkommittete Reste, der Lauf bricht die Nacht an dieser Stelle ab'
  /** Der volle Grund, wie der Runner ihn meldet: die Lage, dann die betroffenen Pfade. */
  const ABBRUCH = [
    ERSTE_ZEILE,
    'frontend/src/pages/NightRunPage.tsx',
    'frontend/src/components/nachtlauf/NachtlaufLaufPlatte.tsx',
    'src/main/java/org/mwolff/manban/card/NightRunService.java',
  ].join('\n')
  const GEKUERZT = `${ERSTE_ZEILE.slice(0, KURZ_GRUND_MAX - 1)}…`

  const abgebrochen = () =>
    aufbewahrt({ id: 1, startedAt: startedAt(0), abortReason: ABBRUCH })

  it('kürzt den Grund in der Kopfmarke auf eine Zeile', () => {
    // Die Voraussetzung des Fixtures: Wäre die erste Zeile kurz genug, prüfte der Test unten nichts.
    expect(ERSTE_ZEILE.length).toBeGreaterThan(KURZ_GRUND_MAX)
  })

  it('zeigt am abgebrochenen Lauf eine dritte Zustandsmarke mit dem gekürzten Grund', async () => {
    renderPage({ listen: [[abgebrochen()]] })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    const marke = within(laufKopfzeile(lauf(0))).getByTestId('lauf-zustand')
    expect(marke).toHaveTextContent(GEKUERZT)
    // Der Kopf bleibt eine Zeile: Die Pfade stehen dort nicht.
    expect(marke).not.toHaveTextContent('NightRunService.java')
    expect(within(marke).getByTestId('led-zinnob')).toBeInTheDocument()
  })

  /**
   * E6: Der Server setzt beim abgebrochenen Lauf allein den Abbruchgrund. Trägt ein Lauf aus der
   * Zeit davor trotzdem beides, zeigt der Kopf den Abbruch — zwei Zustandsmarken nebeneinander
   * wären ein Widerspruch in derselben Platte.
   */
  it('zeigt neben dem Abbruchgrund keine Marke „ohne Arbeit"', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            processedCount: 0,
            abortReason: ABBRUCH,
            noWorkReason: 'Kein Eintrag trug das Label kit:nightrun',
          }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    const kopf = laufKopfzeile(lauf(0))
    expect(within(kopf).getAllByTestId('lauf-zustand')).toHaveLength(1)
    expect(kopf).not.toHaveTextContent('kit:nightrun')
  })

  it('zeigt den Grund in der aufgeklappten Platte vollständig', async () => {
    renderPage({ listen: [[abgebrochen()]] })

    const panelEl = await screen.findByTestId(`lauf-${startedAt(0)}`)

    // Der oberste Lauf steht beim Öffnen der Seite offen (#914, E7).
    expect(within(panelEl).getByTestId('nachtlauf-abbruchgrund').textContent).toBe(ABBRUCH)
  })

  /**
   * Der Fund aus WICHTIG 5 der Plan-Prüfung: In einer `LaufMarke` ragte ein Grund mit zehn Pfaden
   * aus der Platte heraus. AK 4 verlangt ihn vollständig **und** lesbar.
   */
  it('führt den vollständigen Grund nicht in einem einzeiligen Element', async () => {
    renderPage({ listen: [[abgebrochen()]] })

    const panelEl = await screen.findByTestId(`lauf-${startedAt(0)}`)

    const grundzeile = within(panelEl).getByTestId('nachtlauf-abbruchgrund')
    expect(getComputedStyle(grundzeile).whiteSpace).not.toBe('nowrap')
    // Und er steht nicht in der einzeiligen Zustandsmarke: Die trägt den gekürzten Text.
    expect(within(grundzeile).queryByTestId('lauf-zustand')).not.toBeInTheDocument()
    expect(within(laufKopfzeile(panelEl)).queryByTestId('nachtlauf-abbruchgrund')).not.toBeInTheDocument()
  })

  it('lässt die Seite ohne Abbruchgrund unverändert', async () => {
    renderPage({ listen: [[aufbewahrt({ id: 1, startedAt: startedAt(0) })]] })

    const panelEl = await screen.findByTestId(`lauf-${startedAt(0)}`)

    expect(within(panelEl).queryByTestId('nachtlauf-abbruchgrund')).not.toBeInTheDocument()
    expect(within(laufKopfzeile(panelEl)).queryByTestId('lauf-zustand')).not.toBeInTheDocument()
  })

  /**
   * E7: Der im Browser geparste Lauf war bei keinem Server — er kann keinen Abbruchgrund tragen,
   * und die Platte behauptet dort auch keinen.
   */
  it('gibt dem im Browser geparsten Lauf keinen Abbruchgrund', async () => {
    renderPage({ submit: { netzfehler: true } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    const panelEl = await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(within(panelEl).queryByTestId('nachtlauf-abbruchgrund')).not.toBeInTheDocument()
    expect(within(laufKopfzeile(panelEl)).queryByTestId('lauf-zustand')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Abschlussvermerk nur mit Ergebnisstand (#1070)', () => {
  /**
   * Der Prueffall aus dem Issue: der Lauf vom 17.09.2026, 17:02 — in elf Minuten fuer 10,10 $ ein
   * gruenes Paket abgeschlossen, und trotzdem stand „noch nicht abgeschlossen" in der Metazeile.
   * Der Vermerk hing an der Abwesenheit einer hochgeladenen Datei, nicht am Zustand des Laufs.
   */
  it('nennt einen gemeldeten Lauf ohne Ergebnisstand nicht „noch nicht abgeschlossen"', async () => {
    renderPage({ listen: [wieAufbewahrt(ECHTE_KETTE_STAND)] })
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)

    panelAufklappen(panelEl)

    await within(panelEl).findByTestId('zustand-791')
    expect(metazeile()).not.toHaveTextContent('noch nicht abgeschlossen')
  })

  // Die Aussage ueber den Abschluss traegt allein die Kopfmarke, und die haengt am gemeldeten
  // Zustand (E7) -- nicht daran, ob jemand eine Datei hochgeladen hat.
  it('zeigt die Kopfmarke „unvollständig gemeldet" unveraendert am nicht abgeschlossenen Lauf', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({ id: 1, startedAt: startedAt(0), complete: false }),
          aufbewahrt({ id: 2, startedAt: startedAt(30), complete: true }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('unvollständig')
    expect(laufKopfzeile(lauf(30))).not.toHaveTextContent('unvollständig')
  })
})

/**
 * Ein Lauf ist adressierbar (Issue #1085, fachliche Quelle #1064, AK 7).
 *
 * Eine Störzeile des Plattform-Leitstands verweist auf `?lauf=<id>`. Ohne das Aufklappen führte der
 * Verweis auf eine zugeklappte Platte — der Klick hätte den Nutzer an die richtige Seite gebracht
 * und dort allein gelassen.
 */
describe('NightRunPage — adressierbarer Lauf (#1085)', () => {
  const scrollIntoView = vi.fn()
  Element.prototype.scrollIntoView = scrollIntoView

  const dreiLaeufe = [
    aufbewahrt({ id: 11, startedAt: startedAt(0) }),
    aufbewahrt({ id: 12, startedAt: startedAt(1) }),
    aufbewahrt({ id: 13, startedAt: startedAt(2) }),
  ]

  beforeEach(() => scrollIntoView.mockClear())

  // Ziel ist der aelteste Lauf — er steht unten. Auf den obersten zu zeigen bewiese nichts, weil
  // der ohnehin aufgeklappt waere.
  it('klappt mit ?lauf=<id> genau diesen Lauf auf statt des obersten', async () => {
    renderPage({ listen: [dreiLaeufe] }, '/projects/5/nachtlauf?lauf=11')

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(laufTaste(lauf(0))).toHaveAttribute('aria-expanded', 'true')
    expect(laufTaste(lauf(2))).toHaveAttribute('aria-expanded', 'false')
  })

  it('springt zum angesteuerten Lauf', async () => {
    renderPage({ listen: [dreiLaeufe] }, '/projects/5/nachtlauf?lauf=11')

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    await waitFor(() => expect(scrollIntoView).toHaveBeenCalled())
  })

  it('klappt ohne ?lauf wie bisher den obersten Lauf auf', async () => {
    renderPage({ listen: [dreiLaeufe] })

    // Die Liste steht absteigend nach Startzeit: oben der juengste Lauf.
    await screen.findByTestId(`lauf-${startedAt(2)}`)
    expect(laufTaste(lauf(2))).toHaveAttribute('aria-expanded', 'true')
    expect(laufTaste(lauf(0))).toHaveAttribute('aria-expanded', 'false')
    expect(scrollIntoView).not.toHaveBeenCalled()
  })

  /**
   * Eine Störzeile kann veralten, während der Admin sie liest. Ein Verweis, den das System selbst
   * ausgegeben hat, darf den Nutzer nicht für eine Verdrängung bestrafen, die er nicht veranlasst
   * hat — die Seite öffnet normal, ohne Meldung.
   */
  it('nimmt ein verdrängtes ?lauf=<id> hin, ohne Fehler und ohne Aufklappen', async () => {
    renderPage({ listen: [dreiLaeufe] }, '/projects/5/nachtlauf?lauf=999')

    await screen.findByTestId(`lauf-${startedAt(2)}`)
    expect(laufTaste(lauf(2))).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText(/nicht gefunden/i)).not.toBeInTheDocument()
    expect(scrollIntoView).not.toHaveBeenCalled()
  })

  it('nimmt einen unsinnigen Parameter hin wie gar keinen', async () => {
    renderPage({ listen: [dreiLaeufe] }, '/projects/5/nachtlauf?lauf=abc')

    await screen.findByTestId(`lauf-${startedAt(2)}`)
    expect(laufTaste(lauf(2))).toHaveAttribute('aria-expanded', 'true')
  })

  /** Der gespeicherte Lauf trägt seine Id, der eben geparste nicht — er war bei keinem Server. */
  it('trägt die Id nur am gespeicherten Lauf', async () => {
    renderPage({ listen: [[aufbewahrt({ id: 11, startedAt: startedAt(0) })]] }, '/projects/5/nachtlauf?lauf=11')

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(laufTaste(lauf(0))).toHaveAttribute('aria-expanded', 'true')
  })

  /**
   * Die Nummer in der Vorzeile ist dieselbe, auf die der Verweis zeigt (Issue #1107, AK 3). Zwei
   * getrennte Tests — einer für das Aufklappen, einer für die Vorzeile — ließen offen, ob beide
   * dieselbe Zahl meinen; hier steht sie am selben Lauf.
   */
  it('nennt im Titel genau die Nummer, mit der ?lauf=<id> diesen Block aufklappt', async () => {
    // Seit Issue #1127 steht die Nummer im Titel und nicht mehr in der Vorzeile.
    renderPage({ listen: [dreiLaeufe] }, '/projects/5/nachtlauf?lauf=11')

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(laufTaste(lauf(0))).toHaveAttribute('aria-expanded', 'true')
    expect(within(lauf(0)).getByTestId('nachtlauf-ueberschrift')).toHaveTextContent(/^Run #11 · /)
    // Und nicht etwa an einem der beiden anderen Blöcke.
    expect(within(lauf(1)).getByTestId('nachtlauf-ueberschrift')).toHaveTextContent(/^Run #12 · /)
  })
})

describe('NightRunPage — Der verstummte Lauf (#1092)', () => {
  /**
   * Ein Lauf, dessen Runner abgeschossen wurde: Er hat sich nie abgeschlossen gemeldet und traegt
   * fuer immer `complete = false`. Seit #1091 nennt der Server ihn trotzdem FAILED — die Anzeige
   * liest den Befund und nicht mehr `complete`.
   */
  const verstummt = () =>
    aufbewahrt({
      id: 1,
      startedAt: startedAt(0),
      complete: false,
      outcome: serverBefund({ complete: false, verstummt: true, items: [] }),
    })

  it('zeigt den verstummten Lauf zinnober und ohne Puls', async () => {
    renderPage({ listen: [[verstummt()]] })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(within(laufKopfzeile(lauf(0))).getByTestId('led-zinnob')).toHaveAttribute('data-puls', 'aus')
  })

  // „unvollstaendig gemeldet" sagt „der Lauf ist noch unterwegs". Ein verstummter Lauf ist nicht
  // unterwegs, er ist nicht gelungen — die Marke waere dort eine zweite, falsche Wahrheit.
  it('laesst am verstummten Lauf die Marke „unvollständig gemeldet" weg', async () => {
    renderPage({ listen: [[verstummt()]] })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(within(laufKopfzeile(lauf(0))).queryByTestId('lauf-zustand')).not.toBeInTheDocument()
  })

  it('zeigt am laufenden Lauf die Marke und den Puls unveraendert', async () => {
    renderPage({ listen: [[aufbewahrt({ id: 1, startedAt: startedAt(0), complete: false })]] })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    const kopf = laufKopfzeile(lauf(0))
    expect(within(kopf).getByTestId('lauf-zustand')).toHaveTextContent('unvollständig gemeldet')
    expect(within(kopf).getAllByTestId('led-stahl').every((led) => led.getAttribute('data-puls') === 'an')).toBe(true)
    // Issue #1136: Die Lampe des Kopfs ist ein Wechselblinker aus zwei Lampen.
    const blinker = within(kopf).getAllByTestId('led-stahl').find((led) => within(led).queryAllByTestId('blinker-lampe').length === 2)
    expect(blinker).toBeDefined()
  })
})

describe('NightRunPage — Vorgangskennzahlen und Stufenband aus der Server-Antwort (#1116)', () => {
  /** Eine Stufe der Kette in der Antwortform (Issue #1113); die Dauer in Minuten, `null` = ungemessen. */
  const stufe = (
    stage: NightRunItemStageView['stage'],
    dauerMin: number | null,
  ): NightRunItemStageView => ({
    stage,
    durationMs: dauerMin === null ? null : dauerMin * 60_000,
    usage: null,
  })

  const VIER_STUFEN = [
    stufe('PLAN', 8),
    stufe('REVIEW', 12),
    stufe('PAKETE', 4),
    stufe('ABDECKUNG', 2),
  ]

  const BUDGET: NightRunBudgetView = {
    planMin: 20,
    reviewMin: 15,
    paketeMin: 15,
    abdeckungMin: 10,
    kostenUsd: 50,
    origin: 'CONFIGURED',
    defaultFields: [],
  }

  /**
   * Der Anlassfall als Server-Antwort: ein Kettenlauf, den der Runner selbst eingeliefert hat.
   * Kein `protokollWaehlen`, kein Eintrag in `staende` — genau dort stand bis zu diesem Paket
   * „Kosten nicht gemeldet" neben denselben 21,73 $ in der Kostenspalte.
   */
  const kettenLauf = (
    felder: Omit<Partial<NightRunView>, 'items'> & { items?: ItemVorgabe[] } = {},
  ): NightRunView =>
    aufbewahrt({
      id: 1,
      startedAt: startedAt(0),
      mode: 'CHAIN',
      budget: BUDGET,
      items: [
        {
          id: 11,
          cardNumber: 700,
          title: 'Paket A',
          state: 'GREEN',
          durationMs: 26 * 60_000,
          usage: verbraucht({
            costUsd: 21.73,
            inputTokens: 12_345,
            outputTokens: 678,
            modelDurationMs: 13 * 60_000,
            turns: 89,
          }),
          stages: VIER_STUFEN,
        },
      ],
      ...felder,
    })

  /** Zeigt die Läufe und klappt den ersten samt seiner Vorgänge auf — ohne eingelesene Datei. */
  async function vorgaengeZu(...views: NightRunView[]) {
    renderPage({ listen: [views] })
    const panelEl = await screen.findByTestId(`lauf-${views[0].startedAt}`)
    panelAufklappen(panelEl)
    return panelEl
  }

  const kennzahlen = (cardNumber: number) => screen.getByTestId(`kennzahlen-${cardNumber}`)

  it('zeigt die Kosten eines Vorgangs auch in seiner Ergebniszeile (AK 4, Anlassfall)', async () => {
    await vorgaengeZu(kettenLauf())

    // Derselbe Wert, den die Kostenspalte der Zeile darüber führt — die Seite widerspricht sich
    // nicht mehr selbst.
    expect(screen.getByTestId('paket-700')).toHaveTextContent('21,73')
    expect(kennzahlen(700)).toHaveTextContent('21,73 $')
    expect(kennzahlen(700)).not.toHaveTextContent('Kosten nicht gemeldet')
  })

  it('nennt je Vorgang Kosten, Dauer, Modellzeit, Tokenmengen und Züge der Antwort (AK 4)', async () => {
    await vorgaengeZu(kettenLauf())
    const zeile = kennzahlen(700)

    expect(zeile).toHaveTextContent('26 Min')
    expect(zeile).toHaveTextContent('21,73 $')
    expect(zeile).toHaveTextContent('89 Züge')
    expect(zeile).toHaveTextContent('Modellarbeit 50 % der Dauer')
    expect(zeile).toHaveTextContent('Eingabe 12.345 Token')
    expect(zeile).toHaveTextContent('Ausgabe 678 Token')
  })

  it('benennt fehlende Angaben, statt sie als 0 zu zeigen (AK 5)', async () => {
    await vorgaengeZu(
      kettenLauf({
        items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', usage: null }],
      }),
    )

    expect(kennzahlen(700)).toHaveTextContent('Kosten nicht gemeldet')
    expect(kennzahlen(700)).toHaveTextContent('Modellzeit nicht gemeldet')
    expect(kennzahlen(700)).not.toHaveTextContent('0,00 $')
    // Tokenmengen führt der Ergebnisstand nie; ohne jede Messung bleiben sie deshalb weg, statt
    // eine Lücke zu melden, die die Rückfall-Quelle gar nicht haben kann.
    expect(kennzahlen(700)).not.toHaveTextContent('Eingabe')
  })

  it('zeichnet das Stufenband ohne eingelesene Datei aus den Stufen der Antwort', async () => {
    await vorgaengeZu(kettenLauf())

    expect(screen.getByTestId('stufenband-700')).toHaveAttribute(
      'aria-label',
      'Stufenband: Plan 8,0 / 20 min · Prüfung 12,0 / 15 min · Pakete 4,0 / 15 min · Abdeckung 2,0 / 10 min',
    )
    // Die Breiten kommen aus den Vorgaben des Laufs — derselben Quelle, aus der die Fußzeile
    // seit #1115 liest.
    expect(screen.getByTestId('stufe-700-plan').dataset.anteil).toBe('20')
    expect(screen.getByTestId('stufe-700-plan').dataset.fuellung).toBe('40')
    expect(screen.getByTestId('stufe-700-review').dataset.fuellung).toBe('80')
  })

  it('kennzeichnet eine Stufe, die die Antwort nicht führt, als nicht erreicht', async () => {
    await vorgaengeZu(
      kettenLauf({
        items: [
          {
            id: 11,
            cardNumber: 700,
            title: 'Paket A',
            state: 'RED',
            errorClass: 'TIME_BUDGET_EXCEEDED',
            durationMs: 20 * 60_000,
            // Der Plan-Schritt lief, meldete aber keine Dauer — der Unterschied zu „nicht
            // erreicht" ist genau der, den das Band sichtbar machen soll.
            stages: [stufe('PLAN', null), stufe('REVIEW', 12)],
          },
        ],
      }),
    )

    expect(screen.getByTestId('stufe-700-pakete')).toHaveAttribute('data-erreicht', 'nein')
    expect(screen.getByTestId('stufe-700-pakete')).toHaveTextContent('nicht erreicht')
    expect(screen.getByTestId('stufe-700-review')).toHaveTextContent('am Zeitbudget beendet')

    const plan = screen.getByTestId('stufe-700-plan')
    expect(plan).toHaveAttribute('data-erreicht', 'ja')
    expect(plan).not.toHaveTextContent('nicht erreicht')
    expect(plan).toHaveTextContent('0,0 / 20 min')
  })

  it('zeigt an einem Vorgang mit leerer Stufenliste kein Band', async () => {
    await vorgaengeZu(
      kettenLauf({
        items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', stages: [] }],
      }),
    )

    expect(screen.getByTestId('zustand-700')).toBeInTheDocument()
    expect(screen.queryByTestId('stufenband-700')).not.toBeInTheDocument()
  })

  describe('Vorrang des Server-Stands beim Einlesen (AK 6, E8)', () => {
    /**
     * Zeigt den servergeführten Kettenlauf, merkt sich seine Fußzeile und liest dann seine
     * Ergebnisdatei ein. Zurück kommt, was die Seite vorher zeigte — der Vergleichswert.
     */
    async function servergefuehrtUndEingelesen(antworten: Partial<Antworten>) {
      renderPage({ listen: [wieAufbewahrt(ECHTE_KETTE_STAND)], ...antworten })
      const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
      panelAufklappen(panelEl)
      const vorher = within(panelEl).getByTestId(FUSSZEILE).textContent

      protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')
      await waitFor(() => expect(anfragen.some((a) => a.method === 'POST')).toBe(true))
      return vorher
    }

    /** Was die Anzeige eines servergeführten Laufs auszeichnet — vor wie nach dem Einlesen. */
    async function unveraendert(vorher: string | null) {
      const panelEl = screen.getByTestId(`lauf-${ECHTE_KETTE_START}`)
      await waitFor(() => expect(within(panelEl).getByTestId(FUSSZEILE).textContent).toBe(vorher))
      // Die Angaben, die allein die Datei trägt, kommen nicht hinzu (E8).
      expect(
        within(panelEl).queryByTestId('fussangabe-Ketten durchgelaufen'),
      ).not.toBeInTheDocument()
      expect(within(panelEl).queryByTestId('stufenband-791')).not.toBeInTheDocument()
      expect(within(panelEl).queryByTestId('abbruch-842')).not.toBeInTheDocument()
      // Und die Herkunft bleibt die des Servers: Der Lauf ist nicht „aus dem Ergebnisstand".
      expect(within(panelEl).getByTestId('lauf-stand')).toHaveTextContent('Herkunft unbekannt')
    }

    it('lässt die Anzeige unverändert, wenn `submit` „lag schon vor" meldet', async () => {
      const vorher = await servergefuehrtUndEingelesen({
        submit: { ergebnis: [{ startedAt: ECHTE_KETTE_START, created: false }] },
      })

      await screen.findByText('lag schon vor')
      await unveraendert(vorher)
    })

    it('lässt die Anzeige unverändert, wenn das Einliefern scheitert', async () => {
      const vorher = await servergefuehrtUndEingelesen({
        submit: { fehler: 'Einliefern nicht möglich' },
      })

      await screen.findByText('Einliefern nicht möglich')
      await unveraendert(vorher)
    })

    it('lässt die Anzeige unverändert, wenn das Neuladen der Liste scheitert', async () => {
      const vorher = await servergefuehrtUndEingelesen({
        submit: { ergebnis: [{ startedAt: ECHTE_KETTE_START, created: false }] },
        listenFehlerAb: 2,
      })

      await screen.findByText('Liste nicht abrufbar')
      await unveraendert(vorher)
    })

    it('nimmt den Stand auch zurück, wenn erst `submit` den Lauf als bekannt meldet', async () => {
      // Die Liste kannte ihn nicht, der Server schon — dann gilt dasselbe, nur eine Runde später
      // (E8). Scheitert danach das Neuladen, bleibt der geparste Lauf ohne seinen Stand stehen:
      // Er zeigt die Dauer aus dem Lauf selbst und keine Angabe, die der Server nicht kennt.
      renderPage({
        listen: [[]],
        submit: { ergebnis: [{ startedAt: startedAt(0), created: false }] },
        listenFehlerAb: 2,
        karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
      })
      await screen.findByText('Noch keine Auswertung vorhanden.')

      protokollWaehlen(EIN_LAUF)
      await screen.findByText('Liste nicht abrufbar')

      const panelEl = screen.getByTestId(`lauf-${startedAt(0)}`)
      panelAufklappen(panelEl)
      expect(within(panelEl).getByTestId('kennzahlen-700')).toHaveTextContent('7 Min')
      expect(within(panelEl).getByTestId('kennzahlen-700')).not.toHaveTextContent('nicht gemeldet')
      expect(within(panelEl).getByTestId('lauf-stand')).toHaveTextContent('Herkunft unbekannt')
    })
  })

  it('zeigt einen erst durch das Einlesen angelegten Lauf weiter aus der Datei (created: true)', async () => {
    await echteUebersicht()
    const panelEl = screen.getByTestId(`lauf-${ECHTE_KETTE_START}`)

    // Kosten und Züge stammen aus dem Stand — der Server kennt zu diesem Lauf nur die Kosten,
    // weil die Einlieferung genau ihn getragen hat.
    expect(within(panelEl).getByTestId('kennzahlen-791')).toHaveTextContent('11,52 $ · 89 Züge')
    expect(within(panelEl).getByTestId('stufenband-791')).toBeInTheDocument()
    expect(within(panelEl).getByTestId('lauf-stand')).toHaveTextContent('Ergebnisstand')
  })

  it('lässt einen Nachtplan-Lauf bei den Angaben seiner Datei (E8)', async () => {
    // Ein Nachtplan-Lauf geht nie an den Server (Plan #803, Entscheidung 8); sein eingelesener
    // Stand bleibt alleinige Quelle.
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(
      stand({
        art: 'erzeugung',
        stufe: 'plan',
        einheiten: [
          einheit({
            id: '900',
            ausgang: 'verbraucht',
            kennzahlen: { kostenUsd: 3.5, zuege: 12, apiDauerMs: 3 * 60_000 },
          }),
        ],
      }),
    )
    const panelEl = await screen.findByTestId(`lauf-${startedAt(0)}`)
    panelAufklappen(panelEl)

    expect(within(panelEl).getByTestId('kennzahlen-900')).toHaveTextContent(
      '7 Min · 3,50 $ · 12 Züge · Modellarbeit 43 % der Dauer, der Rest außerhalb',
    )
  })
})

describe('NightRunPage — ein eingelieferter Kettenlauf zeigt Planung und Umsetzung (#1106)', () => {
  /** Eine Stufe mit Dauer in Minuten und Kosten in Dollar; `null` = nicht gemeldet. */
  const stufe = (
    stage: NightRunItemStageView['stage'],
    dauerMin: number | null,
    kostenUsd: number | null,
  ): NightRunItemStageView => ({
    stage,
    durationMs: dauerMin === null ? null : dauerMin * 60_000,
    usage: kostenUsd === null ? null : verbraucht({ costUsd: kostenUsd }),
  })

  const MIT_KOSTEN = [
    stufe('PLAN', 8, 3),
    stufe('REVIEW', 12, 5),
    stufe('PAKETE', 4, 2),
    stufe('ABDECKUNG', 2, 1),
  ]

  /** Der Anlassfall: Anforderung #754 plant #773 mit den Paketen #774 und #775 und setzt sie um. */
  const kettenLauf = (
    felder: Omit<Partial<NightRunView>, 'items'> & { stages?: NightRunItemStageView[] } = {},
  ): NightRunView => {
    const { stages = MIT_KOSTEN, ...rest } = felder
    return aufbewahrt({
      id: 1,
      startedAt: startedAt(0),
      mode: 'CHAIN',
      durationMs: 75 * 60_000,
      usage: verbraucht({ costUsd: 50.22 }),
      items: [
        // Die gemeldete Dauer reicht bis zum Ende der Umsetzung — länger als die Stufen zusammen.
        { id: 11, cardNumber: 754, title: 'Anforderung', state: 'GREEN', durationMs: 74 * 60_000, stages },
        { id: 12, cardNumber: 774, title: 'Paket A', state: 'GREEN', durationMs: 20 * 60_000 },
        { id: 13, cardNumber: 775, title: 'Paket B', state: 'GREEN', durationMs: 25 * 60_000 },
      ],
      ...rest,
    })
  }

  /** Eine Board-Karte mit Herkunft. */
  const boardKarte = (id: number, number: number, derivedFrom: number | null): Card =>
    vorhaben({ id, number, title: `Karte ${number}`, type: 'CARD', derivedFrom })

  /** Das Board: der neue Plan #773 samt Paketen und ein alter Plan #600 derselben Anforderung. */
  const HERKUNFT: Antworten = {
    boards: [1],
    boardKarten: {
      1: [
        boardKarte(1773, 773, 754),
        boardKarte(1774, 774, 773),
        boardKarte(1775, 775, 773),
        boardKarte(1600, 600, 754),
        boardKarte(1601, 601, 600),
      ],
    },
    angelegtAm: {
      1773: '2026-09-01T22:10:00.000Z',
      1774: '2026-09-01T22:20:00.000Z',
      1775: '2026-09-01T22:21:00.000Z',
      1600: '2026-08-01T10:00:00.000Z',
      1601: '2026-08-01T10:05:00.000Z',
    },
    karten: {
      754: karte({ id: 2754, number: 754, title: 'Anforderung' }),
      773: karte({ id: 1773, number: 773, title: '[Plan] Der Plan', derivedFrom: 754 }),
      774: karte({ id: 1774, number: 774, title: 'Paket A', derivedFrom: 773 }),
      775: karte({ id: 1775, number: 775, title: 'Paket B', derivedFrom: 773 }),
    },
  }

  async function zeige(view: NightRunView, antworten: Antworten = HERKUNFT) {
    renderPage({ ...antworten, listen: [[view]] })
    const panelEl = await screen.findByTestId(`lauf-${view.startedAt}`)
    panelAufklappen(panelEl)
    return panelEl
  }

  it('zeigt am Kettenvorgang Dauer und Kosten jeder erreichten Stufe (AK 1)', async () => {
    await zeige(kettenLauf())

    expect(screen.getByTestId('stufe-754-plan-kosten')).toHaveTextContent('3,00 $')
    expect(screen.getByTestId('stufe-754-review-kosten')).toHaveTextContent('5,00 $')
    expect(screen.getByTestId('stufenband-754').getAttribute('aria-label')).toContain('Plan 8,0 min · ohne Vorgabe, 3,00 $')
  })

  it('zeigt an einer Stufe ohne gemeldete Kosten keine Kostenzeile (AK 1)', async () => {
    await zeige(kettenLauf({ stages: [stufe('PLAN', 8, null)] }))

    expect(screen.getByTestId('stufe-754-plan')).toHaveAttribute('data-erreicht', 'ja')
    expect(screen.queryByTestId('stufe-754-plan-kosten')).toBeNull()
  })

  it('nennt Plan und Pakete aus diesem Lauf als Verweise, nicht die aus früheren (AK 2)', async () => {
    await zeige(kettenLauf())

    const plan = await screen.findByTestId('dokumente-754-plan')
    await waitFor(() => expect(within(plan).getByRole('button', { name: /Plan #773/ })).toBeInTheDocument())
    const pakete = screen.getByTestId('dokumente-754-pakete')
    expect(within(pakete).getByRole('button', { name: /Pakete #774/ })).toBeInTheDocument()
    expect(within(pakete).getByRole('button', { name: /Pakete #775/ })).toBeInTheDocument()
    // #600 und #601 tragen dieselbe Herkunft, bestanden aber schon vor dem Lauf.
    expect(screen.getByTestId('ergebnis-754')).not.toHaveTextContent('#600')
    expect(screen.getByTestId('ergebnis-754')).not.toHaveTextContent('#601')
  })

  it('erkennt jede Paketzeile als Paket dieses Plans (AK 2)', async () => {
    await zeige(kettenLauf())

    const verweise = await screen.findAllByRole('button', { name: 'Paket aus Plan #773 [Plan] Der Plan' })
    // Je eines an #774 und #775 — die Anforderung selbst ist kein Paket.
    expect(verweise).toHaveLength(2)
  })

  it('zählt bei einem noch laufenden Lauf alles seit seinem Start mit (AK 2)', async () => {
    // 75 Minuten nach dem Start — bei einem abgeschlossenen Lauf von 60 Minuten läge #775 draußen.
    const spaet = { ...HERKUNFT, angelegtAm: { ...HERKUNFT.angelegtAm, 1775: '2026-09-01T23:15:00.000Z' } }
    await zeige(kettenLauf({ complete: false, durationMs: 60 * 60_000 }), spaet)

    const pakete = await screen.findByTestId('dokumente-754-pakete')
    await waitFor(() => expect(within(pakete).getByRole('button', { name: /Pakete #775/ })).toBeInTheDocument())
  })

  it('lässt ein Paket nach dem Ende eines abgeschlossenen Laufs weg (AK 2)', async () => {
    const spaet = { ...HERKUNFT, angelegtAm: { ...HERKUNFT.angelegtAm, 1775: '2026-09-01T23:15:00.000Z' } }
    await zeige(kettenLauf({ durationMs: 60 * 60_000 }), spaet)

    const pakete = await screen.findByTestId('dokumente-754-pakete')
    await waitFor(() => expect(within(pakete).getByRole('button', { name: /Pakete #774/ })).toBeInTheDocument())
    expect(within(pakete).queryByRole('button', { name: /Pakete #775/ })).toBeNull()
  })

  it('nennt einen Plan, dessen Karte nicht auflösbar ist, als nicht gefunden (AK 2)', async () => {
    const ohnePlan = Object.fromEntries(Object.entries(HERKUNFT.karten ?? {}).filter(([nummer]) => nummer !== '773'))
    await zeige(kettenLauf(), { ...HERKUNFT, karten: ohnePlan })

    const zeilen = await screen.findAllByText('Paket aus Plan: Karte #773 nicht gefunden')
    expect(zeilen).toHaveLength(2)
  })

  it('zeigt keine Chipgruppe, wenn die Herkunft nicht ermittelt werden konnte (AK 2)', async () => {
    await zeige(kettenLauf(), { ...HERKUNFT, boards: undefined })

    await waitFor(() => expect(anfragen.some((a) => a.url === '/api/projects/5/boards')).toBe(true))
    expect(screen.queryByTestId('dokumente-754-plan')).toBeNull()
    expect(screen.queryByTestId('dokumente-754-pakete')).toBeNull()
  })

  it('teilt die Kosten in Planung und Umsetzung, zusammen das Gesamt (AK 3)', async () => {
    await zeige(kettenLauf())

    expect(screen.getByTestId('instrument-kosten-wert')).toHaveTextContent('50,22')
    expect(screen.getByTestId('instrument-kosten-zusatz')).toHaveTextContent('Planung 11,00 $ · Umsetzung 39,22 $')
  })

  it('nennt einen fehlenden Anteil „nicht gemeldet", nie 0 (AK 3)', async () => {
    await zeige(kettenLauf({ stages: [stufe('PLAN', 8, null)] }))

    expect(screen.getByTestId('instrument-kosten-zusatz')).toHaveTextContent(
      'Planung nicht gemeldet · Umsetzung nicht gemeldet',
    )
  })

  it('nennt die Umsetzung „nicht gemeldet", wenn der Lauf kein Gesamt meldet (AK 3)', async () => {
    await zeige(kettenLauf({ usage: null }))

    expect(screen.getByTestId('instrument-kosten-zusatz')).toHaveTextContent(
      'Planung 11,00 $ · Umsetzung nicht gemeldet',
    )
  })

  it('rechnet die Dauer des Kettenvorgangs als Summe seiner Stufen (AK 4)', async () => {
    await zeige(kettenLauf())

    const zeile = screen.getByTestId('kennzahlen-754')
    expect(zeile).toHaveTextContent(formatDuration(26 * 60))
    expect(zeile).not.toHaveTextContent(formatDuration(74 * 60))
  })

  it('nennt die Dauer „nicht gemeldet", wenn keine Stufe eine Dauer meldet (AK 4)', async () => {
    await zeige(kettenLauf({ stages: [stufe('PLAN', null, 3)] }))

    expect(screen.getByTestId('kennzahlen-754')).toHaveTextContent('Dauer nicht gemeldet')
  })

  it('zeigt an einem Umsetzungslauf weder Aufteilung noch Herkunftsabruf (AK 5)', async () => {
    await zeige(
      aufbewahrt({
        id: 2,
        startedAt: startedAt(0),
        usage: verbraucht({ costUsd: 5 }),
        items: [{ id: 21, cardNumber: 774, title: 'Paket A', state: 'GREEN' }],
      }),
    )

    expect(screen.queryByTestId('instrument-kosten-zusatz')).toBeNull()
    expect(anfragen.some((a) => a.url === '/api/projects/5/boards')).toBe(false)
  })
})

describe('NightRunPage — Zyklus in der Vorzeile, Lauf-Nummer im Titel (#1127)', () => {
  it('trägt im Titel Nummer, Startdatum und Startzeit', async () => {
    renderPage({ listen: [[aufbewahrt({ id: 412, startedAt: '2026-09-14T20:05:00.000Z' })]] })

    const panelEl = await screen.findByTestId('lauf-2026-09-14T20:05:00.000Z')
    expect(within(panelEl).getByTestId('nachtlauf-ueberschrift')).toHaveTextContent('Run #412 · 14. September, 22:05')
  })

  it('lässt die Nummer an einem eben eingelesenen Lauf ohne Server weg', async () => {
    // Die Liste bleibt leer: Der Lauf existiert nur im Browser und hat keine Nummer.
    renderPage({ submit: { fehler: 'abgelehnt' } })
    await screen.findByLabelText('Protokolldatei auswählen')
    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    expect(within(panelEl).getByTestId('nachtlauf-ueberschrift')).toHaveTextContent(
      /^Run · 14\. September, \d\d:\d\d$/,
    )
  })

  it('stellt Läufe vor und nach Mitternacht in denselben Zyklus, einen ab 12:00 in den nächsten', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({ id: 3, startedAt: '2026-09-15T10:00:00.000Z' }), // 12:00
          aufbewahrt({ id: 2, startedAt: '2026-09-15T01:10:00.000Z' }), // 03:10
          aufbewahrt({ id: 1, startedAt: '2026-09-14T20:05:00.000Z' }), // 22:05
        ],
      ],
    })

    const vorzeile = async (iso: string) =>
      within(await screen.findByTestId(`lauf-${iso}`)).getByTestId('nachtlauf-vorzeile')
    expect(await vorzeile('2026-09-14T20:05:00.000Z')).toHaveTextContent(
      'Schicht vom 14.09.2026 auf den 15.09.2026',
    )
    expect(await vorzeile('2026-09-15T01:10:00.000Z')).toHaveTextContent(
      'Schicht vom 14.09.2026 auf den 15.09.2026',
    )
    expect(await vorzeile('2026-09-15T10:00:00.000Z')).toHaveTextContent(
      'Schicht vom 15.09.2026 auf den 16.09.2026',
    )
  })
})

describe('NightRunPage — die Art des Laufs als Symbol im Kopf (#1141)', () => {
  it('zeigt am Kettenlauf „Kette" und am Umsetzungslauf „Umsetzung" — und nicht mehr in der Vorzeile', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({ id: 2, startedAt: startedAt(5), mode: 'CHAIN' }),
          aufbewahrt({ id: 1, startedAt: startedAt(0) }),
        ],
      ],
    })

    const kette = await screen.findByTestId(`lauf-${startedAt(5)}`)
    const umsetzung = screen.getByTestId(`lauf-${startedAt(0)}`)
    expect(artSymbol(laufKopfzeile(kette))).toHaveAccessibleName('Kette')
    expect(artSymbol(laufKopfzeile(umsetzung))).toHaveAccessibleName('Umsetzung')
    expect(within(kette).getByTestId('nachtlauf-vorzeile')).not.toHaveTextContent('Kette')
    expect(within(umsetzung).getByTestId('nachtlauf-vorzeile')).not.toHaveTextContent('Umsetzung')
  })

  it('setzt das Symbol unmittelbar vor den Titel und lässt die Textmarke weg', async () => {
    renderPage({ listen: [[aufbewahrt({ id: 2, startedAt: startedAt(5), mode: 'CHAIN' })]] })

    const kette = await screen.findByTestId(`lauf-${startedAt(5)}`)
    const ueberschrift = within(kette).getByTestId('nachtlauf-ueberschrift')

    // Das Symbol steht in derselben Zeile wie der Titel, und zwar in der Überschrift selbst.
    expect(artSymbol(ueberschrift)).toHaveAccessibleName('Kette')
    expect(ueberschrift).toHaveTextContent('Run #2')
    expect(within(kette).queryByTestId('lauf-art')).toBeNull()
  })
})

describe('NightRunPage — nur die letzten zwei Zyklen (#1134)', () => {
  // Jetzt: 22.09. 15:00 in Berlin. Laufender Zyklus ab 22.09. 12:00, voriger ab 21.09. 12:00.
  const JETZT = new Date('2026-09-22T13:00:00Z')
  const HEUTE_13 = '2026-09-22T11:00:00.000Z' // 22.09. 13:00 — laufender Zyklus
  const GESTERN_23 = '2026-09-21T21:00:00.000Z' // 21.09. 23:00 — voriger Zyklus
  const GESTERN_11 = '2026-09-21T09:00:00.000Z' // 21.09. 11:00 — Zyklus davor
  const VORGESTERN = '2026-09-19T20:00:00.000Z' // 19.09. 22:00 — älter

  beforeEach(() => vi.setSystemTime(JETZT))

  const vier = () => [
    aufbewahrt({ id: 4, startedAt: HEUTE_13 }),
    aufbewahrt({ id: 3, startedAt: GESTERN_23 }),
    aufbewahrt({ id: 2, startedAt: GESTERN_11 }),
    aufbewahrt({ id: 1, startedAt: VORGESTERN }),
  ]

  const sichtbar = (iso: string) => screen.queryByTestId(`lauf-${iso}`) !== null

  it('zeigt nur die Läufe der letzten zwei Zyklen und nennt die Zahl der ausgeblendeten', async () => {
    renderPage({ listen: [vier()] })

    await screen.findByTestId(`lauf-${HEUTE_13}`)
    expect(sichtbar(GESTERN_23)).toBe(true)
    expect(sichtbar(GESTERN_11)).toBe(false)
    expect(sichtbar(VORGESTERN)).toBe(false)
    expect(screen.getByRole('button', { name: 'Ältere Runs anzeigen (2)' })).toBeInTheDocument()
  })

  it('zeigt auf Klick alle Läufe und auf den nächsten wieder nur die letzten zwei Zyklen', async () => {
    renderPage({ listen: [vier()] })
    await screen.findByTestId(`lauf-${HEUTE_13}`)

    fireEvent.click(screen.getByRole('button', { name: 'Ältere Runs anzeigen (2)' }))
    expect(sichtbar(GESTERN_11)).toBe(true)
    expect(sichtbar(VORGESTERN)).toBe(true)

    fireEvent.click(screen.getByRole('button', { name: 'Nur die letzten zwei Schichten zeigen' }))
    expect(sichtbar(GESTERN_11)).toBe(false)
    expect(sichtbar(VORGESTERN)).toBe(false)
  })

  it('lässt einen älteren Lauf sichtbar, der noch läuft', async () => {
    const laufend = aufbewahrt({
      id: 1,
      startedAt: VORGESTERN,
      complete: false,
      outcome: { abortReason: null, verdict: 'RUNNING', decisiveItem: null, noWorkReason: null },
    })
    renderPage({ listen: [[aufbewahrt({ id: 4, startedAt: HEUTE_13 }), laufend]] })

    await screen.findByTestId(`lauf-${HEUTE_13}`)
    expect(sichtbar(VORGESTERN)).toBe(true)
    expect(screen.queryByRole('button', { name: /Ältere Runs anzeigen/ })).toBeNull()
  })

  it('zeigt einen älteren Lauf, den ?lauf=<id> ansteuert', async () => {
    renderPage({ listen: [vier()] }, '/projects/5/nachtlauf?lauf=1')

    await screen.findByTestId(`lauf-${VORGESTERN}`)
    expect(sichtbar(GESTERN_11)).toBe(false)
    expect(screen.getByRole('button', { name: 'Ältere Runs anzeigen (1)' })).toBeInTheDocument()
  })

  it('zeigt einen in dieser Sitzung eingelesenen älteren Lauf', async () => {
    renderPage({ submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) }, listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)] })
    await screen.findByLabelText('Protokolldatei auswählen')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    expect(await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)).toBeInTheDocument()
    await waitFor(() => expect(anfragen.filter((a) => a.method === 'GET' && a.url.endsWith('/night-runs'))).toHaveLength(2))
    expect(screen.getByTestId(`lauf-${ECHTE_KETTE_START}`)).toBeInTheDocument()
  })

  it('sagt es, wenn die letzten zwei Zyklen leer sind, und bietet die älteren an', async () => {
    renderPage({ listen: [[aufbewahrt({ id: 1, startedAt: VORGESTERN })]] })

    expect(await screen.findByText('In den letzten zwei Schichten gab es keinen Run.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ältere Runs anzeigen (1)' })).toBeInTheDocument()
    expect(screen.queryByText('Noch keine Auswertung vorhanden.')).toBeNull()
  })

  it('zeigt ohne ausgeblendete Läufe keine Schaltfläche', async () => {
    renderPage({ listen: [[aufbewahrt({ id: 4, startedAt: HEUTE_13 })]] })

    await screen.findByTestId(`lauf-${HEUTE_13}`)
    expect(screen.queryByRole('button', { name: /Ältere Runs anzeigen|Nur die letzten zwei Schichten/ })).toBeNull()
  })
})
