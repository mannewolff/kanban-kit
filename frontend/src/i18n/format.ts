import { woerterbuch, type Sprache } from './woerterbuch'

/** Ein Zeitpunkt, wie ihn die Oberfläche bekommt: ISO-Zeichenkette, Millisekunden oder `Date`. */
export type Zeitpunkt = Date | string | number

/** Die Formatierer einer Sprache (Plan #1638, A7). */
export interface Formatierer {
  /** `de-DE` oder `en-US`. */
  readonly gebietsschema: string
  /** Datum: `13.10.2026` bzw. `Oct 13, 2026`. */
  datum(wert: Zeitpunkt): string
  /** Datum mit Uhrzeit: `13.10.2026, 14:30:00` bzw. `Oct 13, 2026, 2:30:00 PM`. */
  datumUhrzeit(wert: Zeitpunkt): string
  /** Zahl: `1.234,5` bzw. `1,234.5`. */
  zahl(wert: number, optionen?: Intl.NumberFormatOptions): string
  /** Geldbetrag, Vorgabe US-Dollar: `1,50 $` bzw. `$1.50`. */
  geld(betrag: number, waehrung?: string): string
  /** Dauer in Sekunden, grobkörnig; `null` heißt „keine Datenbasis“. */
  dauer(sekunden: number | null): string
}

const GEBIETSSCHEMA: Readonly<Record<Sprache, string>> = { de: 'de-DE', en: 'en-US' }

/**
 * Die Datumsformate je Sprache. Deutsch bleibt bei der heutigen Schreibweise der Oberfläche
 * (`toLocaleDateString('de-DE')`, `toLocaleString('de-DE')`), Englisch nimmt die US-Form mit
 * ausgeschriebenem Monat (Kriterium 9 der Quelle #1637).
 */
const DATUM: Readonly<Record<Sprache, Intl.DateTimeFormatOptions>> = {
  de: { day: 'numeric', month: 'numeric', year: 'numeric' },
  en: { dateStyle: 'medium' },
}

const DATUM_UHRZEIT: Readonly<Record<Sprache, Intl.DateTimeFormatOptions>> = {
  de: { day: 'numeric', month: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' },
  en: { dateStyle: 'medium', timeStyle: 'medium' },
}

/**
 * Die Formatierer zur Sprache. Ohne Modulzustand: Jeder Aufruf baut seine `Intl`-Objekte aus der
 * übergebenen Sprache, die Zeitzone ist die des Lesers.
 */
export function formatierer(sprache: Sprache): Formatierer {
  const gebietsschema = GEBIETSSCHEMA[sprache]
  const t = woerterbuch(sprache)
  return {
    gebietsschema,
    datum: (wert) => new Intl.DateTimeFormat(gebietsschema, DATUM[sprache]).format(new Date(wert)),
    datumUhrzeit: (wert) => new Intl.DateTimeFormat(gebietsschema, DATUM_UHRZEIT[sprache]).format(new Date(wert)),
    zahl: (wert, optionen) => new Intl.NumberFormat(gebietsschema, optionen).format(wert),
    geld: (betrag, waehrung = 'USD') =>
      new Intl.NumberFormat(gebietsschema, { style: 'currency', currency: waehrung }).format(betrag),
    dauer: (sekunden) => {
      if (sekunden == null) {
        return t.DAUER_NICHT_VERFUEGBAR
      }
      const gesamt = Math.round(sekunden)
      if (gesamt < 60) {
        return t.DAUER_SEKUNDEN(gesamt)
      }
      const minuten = Math.floor(gesamt / 60)
      if (minuten < 60) {
        return t.DAUER_MINUTEN(minuten)
      }
      const stunden = Math.floor(minuten / 60)
      if (stunden < 24) {
        return t.DAUER_STUNDEN(stunden, minuten % 60)
      }
      return t.DAUER_TAGE(Math.floor(stunden / 24), stunden % 24)
    },
  }
}
