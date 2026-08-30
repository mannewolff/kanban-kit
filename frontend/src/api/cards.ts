import { apiFetch } from './client'

export type CardType = 'CARD' | 'EPIC'

export interface Card {
  id: number
  boardId: number
  columnId: number
  number: number
  title: string
  description: string | null
  positionInColumn: number
  archived: boolean
  ideaStored: boolean
  movedToDoneAt: string | null
  dependencies: number[]
  type: CardType
  parentId: number | null
  shortcode: string | null
  assignees: number[]
  dueDate: string | null
  labels: number[]
  /** Projektweite Nummer der Karte, aus der diese hervorgegangen ist (Issue #601 ff.). */
  derivedFrom: number | null
}

/**
 * Strukturelle Karten-Form, die das CardDetailModal tatsächlich liest — erfüllt sowohl die
 * board-gebundene `Card` als auch die board-nullable Pool-`Idea`. So kann das Modal beide öffnen,
 * ohne die FE-`Card` global board-nullable zu machen. `number` ist board-nullable (Legacy-Ideen
 * ohne projektweite Nummer). Board-spezifische Felder (`boardId`/`columnId`/`positionInColumn`/
 * `movedToDoneAt`) nutzt das Modal nicht und stehen deshalb bewusst nicht hier.
 */
export interface CardDetail {
  id: number
  number: number | null
  title: string
  description: string | null
  type: CardType
  dependencies: number[]
  assignees: number[]
  labels: number[]
  parentId: number | null
  shortcode: string | null
  dueDate: string | null
  archived: boolean
  ideaStored: boolean
  derivedFrom: number | null
}

/**
 * Ergebnis des projektweiten Nummer-Lookups: die Karte zu einer projektweit vergebenen `number` —
 * board-gebunden oder als board-lose Pool-Idee. Deshalb sind `boardId`/`columnId` nullable. Mehr
 * als die `CardDetail`-Felder plus Board-Bindung braucht das Detail-Modal für einen `#N`-Verweis
 * nicht; die Bindung dient dort nur dazu, den Spaltennamen des fremden Boards aufzulösen.
 */
export interface CardByNumber extends CardDetail {
  boardId: number | null
  columnId: number | null
}

/**
 * Treffer der projektübergreifenden Nummernsuche (#489): die Karte plus die Angabe, wo sie liegt.
 * Gesucht wird ausschließlich in den Projekten des Aufrufers; die leere Antwort steht sowohl für
 * „Nummer existiert nirgends" als auch für „Nummer existiert nur in fremden Projekten" — sie ist
 * deshalb kein Beleg dafür, dass es die Karte nicht gibt.
 *
 * Kartennummern sind projektweit eindeutig, nicht global — dieselbe Nummer kann also in mehreren
 * Projekten liegen. Deshalb eine Liste und kein einzelner Treffer.
 *
 * Eine board-lose Pool-Idee hat weder Board noch Spalte; board-gebundene Karten haben stets beides.
 * `boardArchived` unterscheidet ein archiviertes von einem aktiven Board — die Karte bleibt
 * auffindbar, also soll ihr Ort auch dann benannt werden.
 */
export interface CardSearchHit {
  card: CardByNumber
  projectId: number
  projectName: string
  boardId: number | null
  boardName: string | null
  boardArchived: boolean
  columnId: number | null
  columnName: string | null
}

export interface CardActivity {
  id: number
  actorUserId: number | null
  type: string
  detail: string
  createdAt: string
  /** Server-verifizierte Herkunft; null bei Alt-Einträgen vor der Erfassung (#517). */
  origin: 'SESSION' | 'TOKEN' | null
  /** Anzeigename des Tokens (verifiziert); nur bei origin=TOKEN gesetzt. */
  tokenName: string | null
  /** Modell-Selbstauskunft des Clients (X-Agent-Model) — Angabe, keine Tatsache. */
  agent: string | null
}

/**
 * Eine Zeile des Herkunftsbaums (Backend #609). Die Liste kommt flach in Praeorder — jede Wurzel
 * unmittelbar gefolgt von ihrem Teilbaum —, damit sich der Baum allein aus `depth` rekonstruieren
 * laesst. `depth` ist 0-basiert; ARIA zaehlt ab 1, `aria-level` ist also `depth + 1`.
 */
export interface DerivationNode {
  number: number
  title: string
  type: CardType
  /** Nummer des Vorfahren; `null` ohne Herkunft. Bei `broken` bleibt sie gesetzt. */
  derivedFrom: number | null
  depth: number
  done: boolean
  /** Eine board-interne Abhaengigkeit liegt noch nicht in Done. Abgeleitet, nie gepflegt. */
  blocked: boolean
  dependencies: number[]
  /** Nummern, die keine Karte dieses Boards traegt — bewusst nicht aufgeloest. */
  externalDependencies: number[]
  externalOrigin: boolean
  /** Die Zeile haengt an einem Herkunftsring, der nur an der API vorbei entstehen kann. */
  broken: boolean
}

export const cardsApi = {
  /** Herkunftsbaum eines Vorhabens (Issue #643). */
  epicTree: (boardId: number, epicId: number) =>
    apiFetch<DerivationNode[]>(`/api/boards/${boardId}/epics/${epicId}/tree`),
  list: (boardId: number) => apiFetch<Card[]>(`/api/boards/${boardId}/cards`),
  get: (cardId: number) => apiFetch<Card>(`/api/cards/${cardId}`),
  getActivity: (cardId: number) => apiFetch<CardActivity[]>(`/api/cards/${cardId}/activity`),
  // Löst eine projektweite Kartennummer board-übergreifend zu ihrer Karte auf (404, wenn es sie
  // nicht gibt oder der Nutzer nicht Projektmitglied ist). Bewusst nicht `getByNumber`: dieser
  // Name kollidiert in Tests mit der Testing-Library-Query-Konvention (`no-await-sync-queries`).
  byNumber: (projectId: number, number: number) =>
    apiFetch<CardByNumber>(`/api/projects/${projectId}/cards/by-number/${number}`),
  // Löst eine Kartennummer ohne Projektkontext auf — über alle Projekte, in denen der Aufrufer
  // lesen darf (#489). Antwort ist stets eine Liste, auch leer.
  searchByNumber: (number: number) => apiFetch<CardSearchHit[]>(`/api/cards/search?number=${number}`),
  listTrash: (boardId: number) => apiFetch<Card[]>(`/api/boards/${boardId}/trash`),
  restoreDeleted: (cardId: number) =>
    apiFetch<Card>(`/api/cards/${cardId}/restore-deleted`, { method: 'POST' }),
  purge: (cardId: number) => apiFetch<void>(`/api/cards/${cardId}/purge`, { method: 'DELETE' }),
  create: (
    boardId: number,
    columnId: number,
    title: string,
    description?: string,
    parentId?: number | null,
    ideaStored?: boolean,
    // Inhaltlicher Zusatz-Feldsatz beim atomaren Anlegen (Backend #325); leer/weggelassen = wie bisher.
    extra?: {
      dependencies?: number[]
      dueDate?: string | null
      assigneeIds?: number[]
      labelIds?: number[]
    },
  ) =>
    apiFetch<Card>(`/api/boards/${boardId}/cards`, {
      method: 'POST',
      body: JSON.stringify({ columnId, title, description, parentId, ideaStored, ...extra }),
    }),
  move: (cardId: number, columnId: number, position: number) =>
    apiFetch<Card>(`/api/cards/${cardId}/move`, { method: 'POST', body: JSON.stringify({ columnId, position }) }),
  transfer: (cardId: number, targetBoardId: number, targetColumnId: number) =>
    apiFetch<Card>(`/api/cards/${cardId}/transfer`, {
      method: 'POST',
      body: JSON.stringify({ targetBoardId, targetColumnId }),
    }),
  setAssignees: (cardId: number, assignees: number[]) =>
    apiFetch<Card>(`/api/cards/${cardId}/assignees`, {
      method: 'PUT',
      body: JSON.stringify({ assignees }),
    }),
  setLabels: (cardId: number, labels: number[]) =>
    apiFetch<Card>(`/api/cards/${cardId}/labels`, {
      method: 'PUT',
      body: JSON.stringify({ labels }),
    }),
  archive: (cardId: number) => apiFetch<Card>(`/api/cards/${cardId}/archive`, { method: 'POST' }),
  moveToIdeaStorage: (cardId: number) =>
    apiFetch<Card>(`/api/cards/${cardId}/idea-storage`, { method: 'POST' }),
  bulkArchive: (cardIds: number[]) =>
    apiFetch<Card[]>(`/api/cards/bulk-archive`, { method: 'POST', body: JSON.stringify({ cardIds }) }),
  bulkTransfer: (cardIds: number[], targetBoardId: number, targetColumnId: number) =>
    apiFetch<Card[]>(`/api/cards/bulk-transfer`, {
      method: 'POST',
      body: JSON.stringify({ cardIds, targetBoardId, targetColumnId }),
    }),
  bulkDelete: (cardIds: number[]) =>
    apiFetch<void>(`/api/cards/bulk-delete`, { method: 'POST', body: JSON.stringify({ cardIds }) }),
  restore: (cardId: number) => apiFetch<Card>(`/api/cards/${cardId}/restore`, { method: 'POST' }),
  remove: (cardId: number) => apiFetch<void>(`/api/cards/${cardId}`, { method: 'DELETE' }),
  update: (
    cardId: number,
    title: string,
    description: string | null,
    dependencies?: number[],
    shortcode?: string | null,
    parentId?: number | null,
    dueDate?: string | null,
  ) =>
    apiFetch<Card>(`/api/cards/${cardId}`, {
      method: 'PATCH',
      body: JSON.stringify({ title, description, dependencies, shortcode, parentId, dueDate }),
    }),

  /**
   * Setzt die Herkunft einer Karte oder löscht sie (`null`).
   *
   * Eigener Endpunkt statt eines Feldes in `update`: Jener Pfad ist ein Voll-Update, und ein
   * fehlendes Feld ist dort nicht von `null` zu unterscheiden — jeder bestehende Aufrufer hätte
   * die Herkunft gelöscht, `toggleTask` bei jedem Checkbox-Klick (Issue #607, Entscheidung D1a).
   */
  assignDerivedFrom: (cardId: number, derivedFrom: number | null) =>
    apiFetch<Card>(`/api/cards/${cardId}/derived-from`, {
      method: 'PATCH',
      body: JSON.stringify({ derivedFrom }),
    }),
}

export type CardsApi = typeof cardsApi
