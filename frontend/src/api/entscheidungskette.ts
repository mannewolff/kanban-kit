import { apiFetch } from './client'

/**
 * Abruf der Entscheidungskette einer Karte (Issue #2118, Schnittstelle aus Issue #2117, Plan #2108).
 *
 * Eine fehlende Angabe ist `null` und erscheint in der Oberfläche als „nicht erfasst“. Die Typen
 * folgen `EntscheidungsketteView` am Server; Aufzählungen kommen als Name der Konstante.
 */

/** Das Dokument eines Abschnitts. */
export type Dokumentart = 'IDEE' | 'FACHPLAN' | 'PLAN' | 'PAKET'

/** Die Art eines Glieds. */
export type Gliedart =
  | 'IDEE'
  | 'HERKUNFT'
  | 'PRUEFRUNDE'
  | 'EINARBEITUNG'
  | 'ISSUE_REVIEW'
  | 'FREIGABE'
  | 'UMSETZUNG'
  | 'CODE_REVIEW'
  | 'UEBERTRAGUNG'
  | 'VEROEFFENTLICHUNG'

/** Ob ein Glied erfasst ist, fehlt oder auf dem Weg der Karte nicht vorgesehen ist. */
export type Gliedzustand = 'ERFASST' | 'NICHT_ERFASST' | 'NICHT_VORGESEHEN'

/** Ein Vermerk an einem Glied oder Nachfahren. */
export type Kettenhinweis = 'VON_HAND_GEAENDERT' | 'ANDERES_PROJEKT'

export type Akteurtyp = 'MENSCH' | 'MODELL' | 'WERKZEUG'

/** Die Art einer Prüfmeldung des Kits. */
export type Meldungsart =
  | 'PLAN_REVIEW'
  | 'FACHPLAN_REVIEW'
  | 'ISSUE_REVIEW'
  | 'EINARBEITUNG'
  | 'ABSCHLUSSBERICHT'
  | 'CODE_REVIEW'
  | 'UEBERTRAGUNG'
  | 'BUILD_NACHTRAG'
  | 'VEROEFFENTLICHUNG'

/** Ein fester Wert einer Kit-Meldung, unabhängig von ihrer Sprache. */
export type KitWert =
  | 'GRUEN'
  | 'ROT'
  | 'KEINE'
  | 'AUSSTEHEND'
  | 'NICHT_ERFASST'
  | 'LOKAL'
  | 'BUILD_DIENST'
  | 'NOTFALL'
  | 'VORBEREITET'
  | 'UEBERTRAGEN'
  | 'NICHT_UEBERTRAGEN'
  | 'PULL_REQUEST_ANGELEGT'
  | 'NICHT_VEROEFFENTLICHT'
  | 'PRUEFLAUF'

/**
 * Die Quelle eines Glieds. `oeffenbar` gilt für eine sichtbare Karte; eine archivierte nennt Nummer
 * und Titel, öffnet aber nicht. `kommentar` ist die ID eines Kommentars an der Karte `nummer`.
 */
export interface Verweis {
  karte: number | null
  nummer: number | null
  titel: string | null
  oeffenbar: boolean
  kommentar: number | null
}

/** Eine Angabe der Form „Wert, Zeitpunkt“ aus einer Kit-Meldung, wörtlich. */
export interface Angabe {
  wert: string | null
  zeitpunkt: string | null
}

export interface AkteurView {
  typ: Akteurtyp
  /** Anzeigename eines Menschen, Name eines Modells oder eines Zugangs. */
  name: string | null
  /** Das gemeldete Modell eines Werkzeugs. */
  modell: string | null
}

export interface Fundzahlen {
  kritisch: number
  blocker: number
  wichtig: number
  hinweis: number
}

export interface Reviewer {
  nummer: number
  rolle: string | null
  modell: string | null
  funde: Fundzahlen
}

export interface PruefungView {
  art: Meldungsart
  runde: number
  pruefer: Reviewer[]
  funde: Fundzahlen | null
  einarbeitung: GliedView
}

export interface Freigabeangaben {
  fall: 'ZUG_NACH_READY' | 'KENNZEICHEN'
  zugNachReady: string
}

export interface Umsetzungsangaben {
  ausgang: 'ABGESCHLOSSEN' | 'ZURUECKGEZOGEN' | 'OFFEN'
  beendetAm: string | null
  commit: string | null
  prueflauf: Angabe
}

export interface CodeReview {
  funde: Fundzahlen
}

export interface Uebertragung {
  versuch: string | null
  commit: string | null
  basis: string | null
  weg: KitWert | null
  prueflauf: Angabe
  buildDienst: Angabe
  /** Der Wert aus `Quelle:`; nur ein `https:`-Wert wird zum Link. */
  quelle: string | null
  ausgang: string | null
  freigegebenVon: Angabe
  ausgefuehrtVon: Angabe
}

export interface Veroeffentlichung {
  versuch: string | null
  version: string | null
  commit: string | null
  basis: string | null
  /** Der Ausgang wörtlich, wie das Kit ihn meldet. */
  ausgang: string | null
  /** Der Wert aus `Quelle:`; nur ein `https:`-Wert wird zum Link. */
  quelle: string | null
  freigegebenVon: Angabe
  ausgefuehrtVon: Angabe
}

/** Ein Glied. Höchstens die Angabe seiner Art ist gesetzt. */
export interface GliedView {
  art: Gliedart
  zustand: Gliedzustand
  freigegebenVon: AkteurView | null
  ausgefuehrtVon: AkteurView | null
  zeitpunkt: string | null
  ergebnis: string | null
  quelle: Verweis
  hinweise: Kettenhinweis[]
  pruefung: PruefungView | null
  freigabe: Freigabeangaben | null
  umsetzung: Umsetzungsangaben | null
  codeReview: CodeReview | null
  uebertragung: Uebertragung | null
  veroeffentlichung: Veroeffentlichung | null
}

export interface AbschnittView {
  art: Dokumentart
  glieder: GliedView[]
}

/** Ein Nachfahre am Fachplan oder Plan mit Spalte und zuletzt erreichtem Glied. */
export interface Nachfahre {
  zustand: Gliedzustand
  verweis: Verweis
  spalte: string | null
  spaltenSchluessel: string | null
  zuletztErreicht: Gliedart | null
  hinweise: Kettenhinweis[]
}

/** Die Entscheidungskette einer Karte; `sichtbar: false` heißt „kein Abschnitt“. */
export interface EntscheidungsketteView {
  sichtbar: boolean
  art: Dokumentart | null
  abschnitte: AbschnittView[]
  nachfahren: Nachfahre[]
}

export const entscheidungsketteApi = {
  /** Die Kette der Karte mit dieser internen ID. */
  kette: (cardId: number) => apiFetch<EntscheidungsketteView>(`/api/cards/${cardId}/entscheidungskette`),
}

export type EntscheidungsketteApi = typeof entscheidungsketteApi
