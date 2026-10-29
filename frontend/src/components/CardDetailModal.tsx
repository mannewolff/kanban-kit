import Alert from '@mui/material/Alert'
import Autocomplete from '@mui/material/Autocomplete'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Divider from '@mui/material/Divider'
import IconButton from '@mui/material/IconButton'
import Link from '@mui/material/Link'
import MenuItem from '@mui/material/MenuItem'
import Select from '@mui/material/Select'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import {
  createContext,
  Fragment,
  memo,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ComponentPropsWithoutRef,
  type Dispatch,
  type ReactElement,
  type ReactNode,
  type SetStateAction,
} from 'react'
import Markdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { attachmentsApi as defaultAttachmentsApi, type Attachment, type AttachmentsApi } from '../api/attachments'
import { boardsApi as defaultBoardsApi } from '../api/boards'
import CircularProgress from '@mui/material/CircularProgress'
import { ApiError, apiErrorMessage } from '../api/client'
import { DerivationTree } from './DerivationTree'
import { cardsApi as defaultCardsApi, type Card, type CardActivity, type CardByNumber, type CardDetail, type CardStatus, type DerivationNode } from '../api/cards'
import { commentsApi as defaultCommentsApi, type Comment, type CommentsApi } from '../api/comments'
import { artAusTitel, useKitNamen, type KitNamenZustand } from '../api/kitNamen'
import type { NightRunsApi } from '../api/nightRuns'
import { KartenAnlaeufe } from './nachtlauf/KartenAnlaeufe'
import { KettenStufenleiste, traegtStufenleiste, type LabelAenderung } from './nachtlauf/KettenStufenleiste'
import { Wartegrund } from './nachtlauf/Wartegrund'
import type { Epic } from '../api/epics'
import type { Label as BoardLabel } from '../api/labels'
import type { Member } from '../api/members'
import { AbnahmeSzenarienDialog } from './AbnahmeSzenarienDialog'
import { Breadcrumbs } from './Breadcrumbs'
import { isTooLong, tooLongMessage } from '../lib/textLimits'
import { szenarienAus } from '../lib/abnahmeSzenarien'
import { CardFields } from './CardFields'
import { vorhabenFeld } from '../lib/cardEpic'
import { vorhabenNameVorschlag } from '../lib/vorhabenName'
import { cardLocationCrumbs, type CardLocation } from '../lib/cardLocation'
import { effektivDone, STATUS_NAMEN, statusAnzeigename } from '../lib/columnMeta'
import { dueInputToIso, formatDueDate, isOverdue } from '../lib/dueDate'
import { normalizeTaskLists, toggleTaskAt } from '../lib/markdownTasks'
import { safeImageSrc, safeLinkHref } from '../lib/markdownUrls'
import { statusColors } from '../lib/statusColors'
import { ANZEIGE, CODE_BG, GRUND, PANEL_RADIUS, PLATTE, PLATTE_HOCH, SCHATTEN_HOCH, SCHATTEN_PLATTE, theme, ZAHL } from '../theme'
import { Erklaerung } from './Erklaerung'
import { LabelErklaerung, LabelErklaerungInfo } from './LabelErklaerung'
import { labelChipSx } from './labelChipSx'
import { useAuth } from '../auth/AuthContext'
import { AttachmentPreview } from './AttachmentPreview'
import { useSnackbar } from './SnackbarProvider'
import { VorschauGesperrt } from './VorschauGesperrt'
import { dialogTitleSx } from './dialogChromeSx'
import { useSprache, useT } from '../i18n/SpracheContext'
import { formatierer } from '../i18n/format'
import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

/** Bilder und PDF werden in der Lightbox angezeigt; andere Typen nur heruntergeladen. */
const isPreviewable = (contentType: string) =>
  contentType.startsWith('image/') || contentType === 'application/pdf'

/**
 * Lädt für alle Bild-Anhänge einer Liste die Vorschau-URL nach (Blob → Object-URL). Als
 * Modul-Helfer ausgelagert, damit die `fetchBlob`-Kette nicht tief im Effect verschachtelt steht.
 */
function loadImagePreviews(
  list: Attachment[],
  attachmentsApi: Pick<AttachmentsApi, 'fetchBlob'>,
  setPreviews: Dispatch<SetStateAction<Record<number, string>>>,
) {
  for (const a of list) {
    if (!a.contentType.startsWith('image/')) continue
    void attachmentsApi
      .fetchBlob(a.id)
      .then((blob) => setPreviews((p) => ({ ...p, [a.id]: URL.createObjectURL(blob) })))
  }
}

/**
 * Parst die kommagetrennte Abhängigkeits-Eingabe in Nummern. Nur positive Ganzzahlen sind gültig;
 * Leer-Tokens werden ignoriert, Duplikate entfernt. valid=false bei nicht-numerischem/nicht-positivem Token.
 */
export function parseDependencyInput(input: string): { deps: number[]; valid: boolean } {
  const tokens = input.split(',').map((t) => t.trim()).filter((t) => t.length > 0)
  const deps: number[] = []
  for (const token of tokens) {
    if (!/^\d+$/.test(token)) return { deps: [], valid: false }
    const n = Number(token)
    if (n <= 0) return { deps: [], valid: false }
    if (!deps.includes(n)) deps.push(n)
  }
  return { deps, valid: true }
}

/**
 * Liest die Herkunftseingabe. Leer bedeutet „keine Herkunft" und ist gültig; sonst muss eine
 * positive ganze Zahl dastehen. Gegenstück zu {@link parseDependencyInput}, mit demselben Zweck:
 * Was hier scheitert, geht gar nicht erst an den Server.
 */
export function parseHerkunftInput(input: string): { herkunft: number | null; valid: boolean } {
  const token = input.trim()
  if (token.length === 0) return { herkunft: null, valid: true }
  if (!/^\d+$/.test(token)) return { herkunft: null, valid: false }
  const n = Number(token)
  if (n <= 0) return { herkunft: null, valid: false }
  return { herkunft: n, valid: true }
}

/**
 * Kontext für den Task-Checkbox-Renderer: liefert Bearbeitbarkeit, Toggle-Callback und den
 * fortlaufenden Checkbox-Index (Dokumentreihenfolge). Erlaubt, den `input`-Renderer auf
 * Modulebene zu definieren (statt in `TaskMarkdown`) und die Daten per Kontext zu übergeben.
 */
const TaskCheckboxContext = createContext<{
  canEdit: boolean
  onToggle: (index: number) => void
  nextIndex: () => number
} | null>(null)

/**
 * `input`-Renderer für react-markdown: rendert GFM-Task-Checkboxen klickbar (Index in
 * Dokumentreihenfolge über den Kontext). Wird ausschließlich innerhalb von `TaskMarkdown`
 * verwendet (immer im TaskCheckboxContext.Provider, remarkGfm ohne rehype-raw erzeugt
 * ausschließlich `type="checkbox"`-Inputs) — kein Fallback für andere Fälle nötig.
 */
function MarkdownInput(props: Readonly<ComponentPropsWithoutRef<'input'>>) {
  // Nicht null: MarkdownInput wird ausschließlich innerhalb von TaskCheckboxContext.Provider
  // gerendert (siehe TaskMarkdown unten).
  const ctx = useContext(TaskCheckboxContext)!
  const t = useT()
  const index = ctx.nextIndex()
  const box = (
    <input
      type="checkbox"
      checked={props.checked === true}
      disabled={!ctx.canEdit}
      onChange={() => ctx.onToggle(index)}
      aria-label={t.KARTE.AUFGABE_ARIA(index + 1)}
    />
  )
  // Ohne Bearbeitungsrecht ist sie ohnehin gesperrt; mit ihm sperrt sie die Vorschau (Plan #1914, E15).
  return ctx.canEdit ? <VorschauGesperrt>{box}</VorschauGesperrt> : box
}

const markdownComponents: Components = { input: MarkdownInput }

/**
 * Rendert die Karten-Beschreibung als Markdown mit klickbaren Task-Checkboxen. Als eigene,
 * memoized Komponente ausgelagert, damit Re-Renders des Modals (z. B. Nachladen von Kommentaren)
 * die Beschreibung nicht neu mounten. Der Checkbox-Index läuft in Dokumentreihenfolge (Zähler pro
 * Render zurückgesetzt); `onToggle` muss stabil sein, damit `memo` greift.
 */
const TaskMarkdown = memo(function TaskMarkdown({
  body,
  canEdit,
  onToggle,
}: Readonly<{
  body: string
  canEdit: boolean
  onToggle: (index: number) => void
}>) {
  // Zähler muss bei jedem tatsächlichen Render dieser (memoized) Komponente zurückgesetzt werden:
  // react-markdown ruft `nextIndex` je Checkbox in Dokumentreihenfolge auf. Da die Deps exakt den
  // Props entsprechen, gegen die `memo` oben vergleicht, fällt das useMemo-Recompute mit jedem
  // echten Funktionsaufruf zusammen — der Zähler startet dabei trotzdem frisch bei 0, weil er
  // innerhalb der Factory neu angelegt wird. Bewusst kein useRef (Schreiben während des Renders).
  const ctxValue = useMemo(() => {
    const counter = { value: 0 }
    return { canEdit, onToggle, nextIndex: () => counter.value++ }
    // body wird in der Factory nicht gelesen, muss aber in den Deps stehen: ein neuer body-Wert
    // soll den Zähler zurücksetzen, obwohl body selbst nicht in ctxValue einfließt.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [body, canEdit, onToggle])
  return (
    <TaskCheckboxContext.Provider value={ctxValue}>
      <Markdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
        {normalizeTaskLists(body)}
      </Markdown>
    </TaskCheckboxContext.Provider>
  )
})

/**
 * Markdown-Grundstil für Beschreibung und Kommentar-Body. Ohne ihn rendert `<Markdown>` die
 * Browser-Defaults, und eine `##`-Überschrift im Text wirkte größer als der Modal-Titel — daher der
 * Deckel auf den Überschriftsgrößen. Lange Tokens (URLs, Hashes) brechen um; Codeblöcke und breite
 * GFM-Tabellen scrollen in ihrem eigenen Bereich, damit der Modal-Inhalt selbst keinen
 * horizontalen Scrollbalken bekommt. Genau solche Inhalte stehen in Review-Kommentaren (#575).
 *
 * Beide Objekte entstehen beim Modulstart und lesen deshalb `theme.vars`: `theme.palette` wäre der
 * helle Wert und schaltete im dunklen Erscheinungsbild nicht um (#952).
 */
const markdownBodySx = {
  overflowWrap: 'anywhere',
  '& :first-of-type': { mt: 0 },
  '& h1, & h2': { fontWeight: 600, fontSize: '1.15rem', mt: 2, pb: 0.5, borderBottom: `1px solid ${theme.vars.palette.divider}` },
  '& h3, & h4': { fontWeight: 600, fontSize: '1rem', mt: 1.5, mb: 0.5 },
  '& p, & li': { lineHeight: 1.6, color: theme.vars.palette.text.primary },
  '& ul, & ol': { pl: 3, my: 1 },
  '& code': { backgroundColor: CODE_BG, px: 0.5, borderRadius: 1, fontFamily: 'monospace', fontSize: '0.85em' },
  '& pre': { backgroundColor: CODE_BG, p: 1.5, borderRadius: 1, overflowX: 'auto' },
  '& pre code': { backgroundColor: 'transparent', px: 0 },
  // `display: block` + `width: max-content` macht die Tabelle zum eigenen Scrollbereich; als
  // echtes Table-Layout würde sie stattdessen auf die Modalbreite gestaucht.
  '& table': { display: 'block', width: 'max-content', maxWidth: '100%', overflowX: 'auto' },
} as const

const descriptionSx = {
  border: `1px solid ${theme.vars.palette.divider}`,
  borderRadius: 1,
  p: 2,
  ...markdownBodySx,
} as const

/**
 * `input`-Renderer für Kommentare: GFM-Task-Listen werden als Checkboxen angezeigt, aber gesperrt.
 * `readOnly` zusätzlich zu `disabled`, damit React das kontrollierte `checked` ohne `onChange`
 * akzeptiert.
 */
function CommentCheckbox(props: Readonly<ComponentPropsWithoutRef<'input'>>) {
  return <input type="checkbox" checked={props.checked === true} disabled readOnly />
}

/** `a`-Renderer: `href` nur bei erlaubtem Schema — sonst bleibt der Linktext ohne Ziel stehen. */
function CommentLink({ href, children }: Readonly<ComponentPropsWithoutRef<'a'>>) {
  return <a href={safeLinkHref(href)}>{children}</a>
}

/** `img`-Renderer: `src` nur bei erlaubtem Schema — sonst wird nichts abgerufen. */
function CommentImage({ src, alt }: Readonly<ComponentPropsWithoutRef<'img'>>) {
  return <img src={safeImageSrc(src)} alt={alt} />
}

const commentComponents: Components = { input: CommentCheckbox, a: CommentLink, img: CommentImage }

/**
 * Kommentar-Body als Markdown (#575). Vorher stand er in einem `<Typography>`, das als `<p>` jede
 * Folge von Whitespace zu einem Leerzeichen kollabiert — lange Berichte wurden so zur Textwand.
 *
 * Bewusst nicht `TaskMarkdown`: dessen Checkbox-Toggles schreiben über `cardsApi.update` in die
 * Karten-**Beschreibung** zurück; für Kommentare gibt es keinen solchen Persistenzpfad, ein Klick
 * liefe in die falsche Datenquelle. Task-Listen werden hier daher nur angezeigt. `normalizeTaskLists`
 * läuft trotzdem mit, damit dieselbe Schreibweise in Beschreibung und Kommentar gleich rendert.
 */
function CommentBody({ body }: Readonly<{ body: string }>) {
  return (
    <Box data-testid="comment-body" sx={markdownBodySx}>
      <Markdown remarkPlugins={[remarkGfm]} components={commentComponents}>
        {normalizeTaskLists(body)}
      </Markdown>
    </Box>
  )
}

/**
 * Zeitpunkt eines Kommentars als Text. Ein leerer oder nicht parsbarer Wert ergibt `''` statt eines
 * sichtbaren „Invalid Date" — dasselbe Muster wie `formatDate` in `ProjectsPage`.
 */
function formatCommentTime(iso: string, sprache: Sprache): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '' : formatierer(sprache).datumUhrzeit(d)
}

/**
 * Zeitzeile eines Kommentars (#843): Erstellzeitpunkt, bei nachträglicher Bearbeitung ergänzt um
 * „bearbeitet <Zeitpunkt>". Ohne den Zusatz wäre ein spät bearbeiteter alter Kommentar von einem
 * neuen nicht zu unterscheiden — und genau die zeitliche Einordnung ist der Zweck der Anzeige.
 * Ohne darstellbaren Erstellzeitpunkt bleibt die Zeile leer und wird gar nicht gerendert. Zeitpunkte
 * und Zusatz folgen `sprache`.
 */
export function commentMetaText(c: Readonly<Pick<Comment, 'createdAt' | 'updatedAt'>>, sprache: Sprache = 'de'): string {
  const erstellt = formatCommentTime(c.createdAt, sprache)
  if (!erstellt) return ''
  const bearbeitet = c.updatedAt === c.createdAt ? '' : formatCommentTime(c.updatedAt, sprache)
  return bearbeitet ? `${erstellt} · ${woerterbuch(sprache).KARTE.BEARBEITET(bearbeitet)}` : erstellt
}

/**
 * Feld-Konfiguration für Kommentare: Verfassen und Bearbeiten teilen sie, damit beide Felder gleich
 * aussehen und mehrzeilige Kommentare auch bequem entstehen können — nicht nur bearbeitbar sind.
 */
export const commentFieldProps = { multiline: true, minRows: 3 } as const

/**
 * Umschließt das Feld nur, wenn der Aufrufer die Vorschau meldet: Die Zuordnungsfelder stehen auch im
 * Anlege-Dialog, der keinen Anmeldezustand kennt (Plan #1914, E15).
 */
function InVorschauGesperrt({ vorschau, children }: Readonly<{ vorschau: boolean; children: ReactElement }>) {
  return vorschau ? <VorschauGesperrt>{children}</VorschauGesperrt> : children
}

/** Zuständige-Sektion: Autocomplete im Edit-Modus, sonst Chips oder Leer-Hinweis. */
export function AssigneeSection({
  canEdit,
  members,
  assigneeIds,
  onChange,
  vorschau = false,
}: Readonly<{
  canEdit: boolean
  members: Member[]
  assigneeIds: number[]
  onChange: (ids: number[]) => void
  /** In der „Ansicht als XY“ gesperrt mit dem Hinweis „Vorschau“ (Plan #1914, E15). */
  vorschau?: boolean
}>) {
  const t = useT()
  const memberName = (userId: number) =>
    members.find((m) => m.userId === userId)?.displayName ?? `#${userId}`
  const readOnly =
    assigneeIds.length > 0 ? (
      <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap' }}>
        {assigneeIds.map((uid) => (
          <Chip key={uid} size="small" label={memberName(uid)} />
        ))}
      </Stack>
    ) : (
      <Typography color="text.secondary">{t.KARTE.NIEMAND_ZUGEWIESEN}</Typography>
    )
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {t.KARTE.ZUSTAENDIGE}
      </Typography>
      {canEdit ? (
        <InVorschauGesperrt vorschau={vorschau}>
        <Autocomplete
          multiple
          size="small"
          options={members}
          getOptionLabel={(m) => m.displayName}
          isOptionEqualToValue={(a, b) => a.userId === b.userId}
          value={members.filter((m) => assigneeIds.includes(m.userId))}
          onChange={(_, selected) => onChange(selected.map((m) => m.userId))}
          renderInput={(params) => (
            <TextField {...params} label={t.KARTE.ZUSTAENDIGE} slotProps={{ htmlInput: { ...params.inputProps, 'aria-label': t.KARTE.ZUSTAENDIGE } }} />
          )}
        />
        </InVorschauGesperrt>
      ) : (
        readOnly
      )}
    </Box>
  )
}

/** Label-Sektion: Autocomplete im Edit-Modus, sonst farbige Chips oder Leer-Hinweis. */
export function LabelSection({
  canEdit,
  boardLabels,
  labelIds,
  onChange,
  vorschau = false,
}: Readonly<{
  canEdit: boolean
  boardLabels: BoardLabel[]
  labelIds: number[]
  onChange: (ids: number[]) => void
  /** In der „Ansicht als XY“ gesperrt mit dem Hinweis „Vorschau“ (Plan #1914, E15). */
  vorschau?: boolean
}>) {
  const t = useT()
  // Die Optionen haben keinen echten Fokus, die Tastatur markiert über `aria-activedescendant`.
  // Darum öffnet die markierte Option ihr Tooltip gesteuert (Plan #1668, E7) — die Maus markiert
  // beim Darüberfahren ebenso, damit gilt für beide dasselbe. MUI meldet nur Markierungen durch
  // Maus, Tastatur und Touch, nicht ihr Zurücksetzen beim Schließen oder Filtern; das holen
  // `onClose` und `onInputChange` nach, sonst ginge beim Wiederöffnen ein altes Tooltip auf.
  const [markiert, setMarkiert] = useState<BoardLabel | null>(null)
  const markierungWeg = () => setMarkiert(null)
  const readOnly =
    labelIds.length > 0 ? (
      <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap' }}>
        {labelIds.map((id) => {
          const l = boardLabels.find((b) => b.id === id)
          const name = l?.name ?? `#${id}`
          return (
            <LabelErklaerung key={id} name={name}>
              <Chip size="small" label={name} sx={labelChipSx(l?.color)} />
            </LabelErklaerung>
          )
        })}
      </Stack>
    ) : (
      <Typography color="text.secondary">{t.KARTE.KEINE_LABELS}</Typography>
    )
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {t.KARTE.LABELS}
      </Typography>
      {canEdit ? (
        <InVorschauGesperrt vorschau={vorschau}>
        <Autocomplete
          multiple
          size="small"
          options={boardLabels}
          getOptionLabel={(l) => l.name}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          value={boardLabels.filter((l) => labelIds.includes(l.id))}
          onChange={(_, selected) => onChange(selected.map((l) => l.id))}
          onHighlightChange={(_, option) => setMarkiert(option)}
          onClose={markierungWeg}
          onInputChange={markierungWeg}
          // Auf dem Tablet quer wählt Antippen die Option bzw. bleibt dem Chip; die Erklärung öffnet
          // das Info-Symbol daneben (Plan #1707, E7).
          renderOption={({ key, ...props }, option) => (
            <LabelErklaerung key={key} name={option.name} open={markiert?.id === option.id} placement="right" eigenerFokus eigeneAktion>
              <li {...props}>
                {option.name}
                <LabelErklaerungInfo name={option.name} placement="right" />
              </li>
            </LabelErklaerung>
          )}
          renderTags={(value, getTagProps) =>
            value.map((l, index) => (
              <Fragment key={l.id}>
                <LabelErklaerung name={l.name} eigeneAktion>
                  <Chip {...getTagProps({ index })} key={l.id} size="small" label={l.name} sx={labelChipSx(l.color)} />
                </LabelErklaerung>
                <LabelErklaerungInfo name={l.name} />
              </Fragment>
            ))
          }
          renderInput={(params) => (
            <TextField {...params} label={t.KARTE.LABELS} slotProps={{ htmlInput: { ...params.inputProps, 'aria-label': t.KARTE.LABELS } }} />
          )}
        />
        </InVorschauGesperrt>
      ) : (
        readOnly
      )}
    </Box>
  )
}

/** Anhänge-Sektion: Liste mit Vorschau/Download, Löschen und Upload (nur View-Modus). */
function AttachmentsSection({
  attachments,
  previews,
  uploadError,
  canEdit,
  onOpenPreview,
  onDelete,
  onUpload,
}: Readonly<{
  attachments: Attachment[]
  previews: Record<number, string>
  uploadError: string | null
  canEdit: boolean
  onOpenPreview: (a: Attachment) => void
  onDelete: (id: number) => void
  onUpload: (file: File) => void
}>) {
  const t = useT()
  const vorschau = Boolean(useAuth().vorschau)
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {t.KARTE.ANHAENGE}
      </Typography>
      {uploadError && <Alert severity="error" sx={{ mb: 1 }}>{uploadError}</Alert>}
      <Stack spacing={1}>
        {attachments.map((a) => (
          <Box key={a.id}>
            <Stack direction="row" spacing={1} alignItems="center">
              {isPreviewable(a.contentType) ? (
                <Link component="button" type="button" onClick={() => onOpenPreview(a)} sx={{ textAlign: 'left' }}>
                  {a.filename}
                </Link>
              ) : (
                <Link href={`/api/attachments/${a.id}`}>{a.filename}</Link>
              )}
              <Typography variant="caption" color="text.secondary">
                {t.KARTE.GROESSE_KB(Math.round(a.size / 1024))}
              </Typography>
              {canEdit && (
                <VorschauGesperrt>
                  <IconButton size="small" aria-label={t.KARTE.ANHANG_LOESCHEN_ARIA(a.filename)} onClick={() => onDelete(a.id)}>
                    ✕
                  </IconButton>
                </VorschauGesperrt>
              )}
            </Stack>
            {previews[a.id] && (
              <Box component="img" src={previews[a.id]} alt={a.filename}
                onClick={() => onOpenPreview(a)}
                sx={{ maxWidth: 240, maxHeight: 160, mt: 0.5, borderRadius: 1, cursor: 'pointer', display: 'block' }} />
            )}
          </Box>
        ))}
        {attachments.length === 0 && <Typography color="text.secondary">{t.KARTE.KEINE_ANHAENGE}</Typography>}
      </Stack>
      {canEdit && (
        <VorschauGesperrt>
          <Button component="label" size="small" sx={{ mt: 1 }}>
            {t.KARTE.DATEI_ANHAENGEN}<input
              hidden
              type="file"
              aria-label={t.KARTE.DATEI_ANHAENGEN}
              disabled={vorschau}
              onChange={(e) => {
                const f = e.target.files?.[0]
                if (f) onUpload(f)
              }}
            />
          </Button>
        </VorschauGesperrt>
      )}
    </Box>
  )
}

/** Kommentar-Sektion: Liste mit Inline-Edit/Moderation + Eingabe (nur View-Modus). */
function CommentsSection({
  comments,
  currentUserId,
  canModerateComments,
  editingCommentId,
  editingBody,
  newComment,
  onStartEdit,
  onSaveEdit,
  onCancelEdit,
  onDelete,
  onEditingBodyChange,
  onNewCommentChange,
  onAdd,
}: Readonly<{
  comments: Comment[]
  currentUserId: number | undefined
  canModerateComments: boolean
  editingCommentId: number | null
  editingBody: string
  newComment: string
  onStartEdit: (c: Comment) => void
  onSaveEdit: () => void
  onCancelEdit: () => void
  onDelete: (id: number) => void
  onEditingBodyChange: (value: string) => void
  onNewCommentChange: (value: string) => void
  onAdd: () => void
}>) {
  const { sprache } = useSprache()
  const t = useT()
  const vorschau = Boolean(useAuth().vorschau)
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {t.KARTE.KOMMENTARE}
      </Typography>
      <Stack spacing={1}>
        {comments.map((c) => {
          const isAuthor = c.authorUserId === currentUserId
          const meta = commentMetaText(c, sprache)
          return (
            <Box key={c.id}>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Stack direction="row" spacing={1} alignItems="baseline" flexWrap="wrap">
                  <Typography variant="body2" fontWeight={600}>
                    {c.authorName}
                  </Typography>
                  {/* Zeitpunkt im Muster der Aktivitätsliste desselben Modals (#843). */}
                  {meta && (
                    <Typography data-testid="comment-meta" variant="caption" color="text.secondary">
                      {meta}
                    </Typography>
                  )}
                </Stack>
                <Stack direction="row" spacing={0.5}>
                  {/* Bearbeiten darf nur der Autor selbst. */}
                  {isAuthor && editingCommentId !== c.id && (
                    <VorschauGesperrt>
                      <IconButton size="small" aria-label={t.KARTE.KOMMENTAR_BEARBEITEN} onClick={() => onStartEdit(c)}>
                        ✎
                      </IconButton>
                    </VorschauGesperrt>
                  )}
                  {/* Löschen ist Moderation: nur Admin/Owner (bzw. Plattform-Admin). */}
                  {canModerateComments && (
                    <VorschauGesperrt>
                      <IconButton size="small" aria-label={t.KARTE.KOMMENTAR_LOESCHEN} onClick={() => onDelete(c.id)}>
                        ✕
                      </IconButton>
                    </VorschauGesperrt>
                  )}
                </Stack>
              </Stack>
              {editingCommentId === c.id ? (
                <Stack spacing={1}>
                  <TextField {...commentFieldProps} size="small" value={editingBody}
                    onChange={(e) => onEditingBodyChange(e.target.value)}
                    error={isTooLong(editingBody)}
                    helperText={isTooLong(editingBody) ? tooLongMessage(editingBody.length, sprache) : undefined}
                    slotProps={{ htmlInput: { 'aria-label': t.KARTE.KOMMENTAR_BEARBEITEN } }} />
                  <Stack direction="row" spacing={1}>
                    <VorschauGesperrt>
                      <Button size="small" variant="contained" onClick={onSaveEdit} disabled={isTooLong(editingBody)}>{t.ALLGEMEIN.SPEICHERN}</Button>
                    </VorschauGesperrt>
                    <Button size="small" onClick={onCancelEdit}>{t.ALLGEMEIN.ABBRECHEN}</Button>
                  </Stack>
                </Stack>
              ) : (
                <CommentBody body={c.body} />
              )}
            </Box>
          )
        })}
        {comments.length === 0 && <Typography color="text.secondary">{t.KARTE.KEINE_KOMMENTARE}</Typography>}
      </Stack>
      {/* alignItems explizit: sonst zieht der Default `stretch` den Senden-Button auf Feldhöhe. */}
      <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ mt: 1 }}>
        {/* Das Feld trägt seinen Namen am Eingabeelement; dort steht in der Vorschau der Hinweis. */}
        <VorschauGesperrt>
          <TextField {...commentFieldProps} size="small" fullWidth placeholder={t.KARTE.KOMMENTAR_SCHREIBEN} value={newComment}
            onChange={(e) => onNewCommentChange(e.target.value)}
            error={isTooLong(newComment)}
            helperText={isTooLong(newComment) ? tooLongMessage(newComment.length, sprache) : undefined}
            slotProps={{ htmlInput: { 'aria-label': vorschau ? t.VORSCHAU.GESPERRT : t.KARTE.KOMMENTAR_SCHREIBEN } }} />
        </VorschauGesperrt>
        <VorschauGesperrt>
          <Button variant="contained" size="small" onClick={onAdd} disabled={isTooLong(newComment)}>
            {t.KARTE.SENDEN}
          </Button>
        </VorschauGesperrt>
      </Stack>
    </Box>
  )
}

/** Aktivitäts-Sektion: chronologische Ereignisliste (nur View-Modus). */
/**
 * Aktivitätsverlauf mit Herkunfts-Kennzeichnung (#518): Token-Einträge tragen einen gefüllten Chip
 * mit dem server-verifizierten Token-Namen (Tatsache), eine vorhandene Modell-Angabe einen
 * outlined-Chip in Kursivschrift (Client-Selbstauskunft) — die beiden Verlässlichkeitsklassen
 * dürfen nicht gleich aussehen. Session- und Alt-Einträge bleiben unmarkiert: der Mensch ist der
 * Default, der Verlauf bleibt ruhig.
 */
function ActivitySection({
  activities,
  actorName,
}: Readonly<{
  activities: CardActivity[]
  actorName: (userId: number | null) => string
}>) {
  const { sprache } = useSprache()
  const t = useT()
  const f = formatierer(sprache)
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {t.KARTE.AKTIVITAET}
      </Typography>
      <Stack spacing={0.5}>
        {activities.map((a) => (
          <Stack key={a.id} direction="row" spacing={0.5} alignItems="center" flexWrap="wrap">
            <Typography variant="caption" color="text.secondary">
              {f.datumUhrzeit(a.createdAt)} · {actorName(a.actorUserId)} ·{' '}
              {a.detail}
            </Typography>
            {a.origin === 'TOKEN' && (
              <Chip
                data-testid="activity-token"
                size="small"
                label={a.tokenName ?? t.KARTE.TOKEN}
                aria-label={t.KARTE.TOKEN_ARIA(a.tokenName)}
                sx={{ height: 18, fontSize: '0.65rem' }}
              />
            )}
            {a.agent && (
              <Erklaerung title={t.KARTE.AGENT_HINWEIS}>
                <Chip
                  data-testid="activity-agent"
                  size="small"
                  variant="outlined"
                  label={a.agent}
                  aria-label={t.KARTE.AGENT_ARIA(a.agent)}
                  sx={{ height: 18, fontSize: '0.65rem', fontStyle: 'italic' }}
                />
              </Erklaerung>
            )}
          </Stack>
        ))}
        {activities.length === 0 && (
          <Typography color="text.secondary">{t.KARTE.KEINE_AKTIVITAET}</Typography>
        )}
      </Stack>
    </Box>
  )
}

/** Aussehen des farbigen Status-Chips — eine Quelle für den lesenden und den interaktiven Fall. */
const statusChipSx = (colors: { bg: string; text: string }) => ({
  bgcolor: colors.bg,
  color: colors.text,
  fontWeight: 600,
})

/**
 * Status-Chip in der Kopfleiste. Ein Vorhaben zeigt „Vorhaben", eine Karte ohne eigenen Status
 * (Dokumentarten) wie bisher ihre Spalte, ein Arbeitspaket seinen Status (Plan #1294, E16, E21).
 *
 * Zum Wechsler wird der Chip allein aus der Karte heraus: `canSetStatus` sagt der Server für das
 * Board **dieser** Karte (E10) — deshalb erscheint er unabhängig davon, von wo aus sie geöffnet
 * wurde. Lesend bleibt er an einer archivierten Karte und auf einem archivierten Board. Ein Wechsel
 * setzt nur den Status; die Karte bleibt in ihrer Spalte.
 */
function CardStatusChip({
  isEpic,
  columnName,
  status,
  canSetStatus,
  archived,
  editing,
  onSetStatus,
}: Readonly<{
  isEpic: boolean
  columnName?: string
  status: CardStatus | null
  canSetStatus: boolean
  archived: boolean
  editing: boolean
  onSetStatus: (status: CardStatus) => Promise<void>
}>) {
  const { sprache } = useSprache()
  const t = useT()
  // Läuft ein Wechsel, ist die Auswahl gesperrt; die Rückfrage gilt nur für „Ready".
  const [pending, setPending] = useState(false)
  const [confirmReady, setConfirmReady] = useState(false)

  if (isEpic) return <Chip label={t.KARTE.VORHABEN} size="small" color="secondary" />
  if (status === null) {
    if (!columnName) return null
    return <Chip label={columnName} size="small" sx={statusChipSx(statusColors(columnName))} />
  }

  const name = statusAnzeigename(status, sprache)
  if (archived || !canSetStatus) {
    return <Chip label={name} size="small" sx={statusChipSx(statusColors(statusAnzeigename(status)))} />
  }

  const setze = async (ziel: CardStatus) => {
    setPending(true)
    try {
      await onSetStatus(ziel)
    } catch {
      // Die Meldung gibt `onSetStatus` selbst aus; hier zählt nur, dass die Auswahl wieder
      // bedienbar wird. Sie zeigt weiter `status` — also den unveränderten Stand.
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <VorschauGesperrt>
      <Select
        size="small"
        variant="standard"
        value={status}
        disabled={pending || editing}
        disableUnderline
        SelectDisplayProps={{ 'aria-label': t.KARTE.STATUS }}
        // Der gewählte Wert erscheint als derselbe Chip wie im lesenden Fall — Melderfarbe und
        // Radius kommen so aus `statusColors` und dem Theme, nicht aus eigenen Werten.
        renderValue={(wert) => (
          <Chip label={statusAnzeigename(wert, sprache)} size="small" sx={statusChipSx(statusColors(statusAnzeigename(wert)))} />
        )}
        onChange={(e) => {
          // Nur die übrigen Zustände lösen `onChange` aus — der aktuelle Eintrag ist `disabled`.
          const ziel = e.target.value as CardStatus
          if (ziel === 'READY') {
            setConfirmReady(true)
            return
          }
          void setze(ziel)
        }}
      >
        {STATUS_NAMEN.map(([s]) => (
          <MenuItem key={s} value={s} disabled={s === status}>
            {statusAnzeigename(s, sprache)}
          </MenuItem>
        ))}
      </Select>
      </VorschauGesperrt>

      {/*
        Rückfrage nur vor „Ready": Ready ist das menschliche GO — das Gate hängt an der Bedeutung,
        nicht an der Bewegung (Plan #1294, E20). Alle anderen Wechsel laufen ohne Zwischenschritt.
      */}
      {confirmReady && (
        <Dialog open onClose={() => setConfirmReady(false)}>
          <DialogTitle sx={dialogTitleSx}>{t.KARTE.READY_FRAGE}</DialogTitle>
          <DialogActions>
            <Button onClick={() => setConfirmReady(false)}>{t.ALLGEMEIN.ABBRECHEN}</Button>
            <VorschauGesperrt>
              <Button
                variant="contained"
                onClick={() => {
                  setConfirmReady(false)
                  void setze('READY')
                }}
              >
                {t.KARTE.READY_SETZEN}
              </Button>
            </VorschauGesperrt>
          </DialogActions>
        </Dialog>
      )}
    </>
  )
}

/**
 * Abhängigkeits-Zeile im Lesemodus. Mit `onOpen` wird jede Nummer ein echter Button (Tastatur und
 * sichtbarer Fokus kommen von MUIs `Link component="button"`), ohne bleibt es reiner Text — so
 * entsteht ohne bekannte `projectId` kein Verweis, der nirgendwohin führen könnte.
 */
function DependencyList({
  dependencies,
  onOpen,
}: Readonly<{ dependencies: number[]; onOpen?: (number: number) => void }>) {
  const t = useT()
  return (
    <Typography variant="body2" color="text.secondary" aria-label={t.KARTE.ABHAENGIGKEITEN}>
      {t.KARTE.ABHAENGIG_VON}{' '}
      {dependencies.map((n, index) => (
        <Fragment key={n}>
          {index > 0 && ', '}
          {onOpen ? (
            <Link
              component="button"
              type="button"
              variant="body2"
              aria-label={t.KARTE.KARTE_OEFFNEN_ARIA(n)}
              onClick={() => onOpen(n)}
            >
              #{n}
            </Link>
          ) : (
            `#${n}`
          )}
        </Fragment>
      ))}
    </Typography>
  )
}

/**
 * Ladezustand der beim Öffnen nachgeladenen Beschreibung (Issue #769). Die Listen-Antwort trägt den
 * Volltext nicht mehr; bis der Einzelabruf da ist, steht weder „Keine Beschreibung." noch ein
 * bearbeitbares Feld zur Verfügung — beides wäre eine Aussage über einen noch unbekannten Text.
 */
type BeschreibungStatus = 'laedt' | 'geladen' | 'fehler'

/**
 * Das Vorhaben, dessen neue Zuordnung die Karte auf dem Board verdeckt (Issue #1981): nur wenn sich
 * die Zuordnung geändert hat und das Ziel ausgeblendet ist. Sonst `undefined` — dann meldet das
 * Speichern nur sich selbst.
 */
function verdeckendesVorhaben(
  vorher: number | null,
  nachher: number | null,
  hiddenEpics: ReadonlySet<number> | undefined,
  epics: readonly Epic[],
): Epic | undefined {
  if (nachher === null || nachher === vorher || !hiddenEpics?.has(nachher)) return undefined
  return epics.find((e) => e.id === nachher)
}

/**
 * Der zuletzt vom Server bestätigte Stand der Felder, die Bearbeiten-Dialog und Checkbox-Klick
 * schreiben (Issue #1328, Plan #1325, A1). Lesemodus und Checkbox-Klick lesen nur ihn — nie die
 * `card`-Prop, die nach dem Speichern veraltet, und nie den Entwurf, den Abbrechen verwirft.
 */
interface KartenStand extends Pick<Card, 'title' | 'dependencies' | 'shortcode' | 'parentId' | 'dueDate' | 'derivedFrom'> {
  description: string
}

/** Die Stand-Felder einer Karte; eine fehlende Beschreibung gilt als leer. */
function standAus(karte: Readonly<Pick<Card, keyof KartenStand>>): KartenStand {
  return {
    title: karte.title,
    description: karte.description ?? '',
    dependencies: karte.dependencies,
    shortcode: karte.shortcode ?? null,
    parentId: karte.parentId,
    dueDate: karte.dueDate,
    derivedFrom: karte.derivedFrom ?? null,
  }
}

/**
 * Ein Block des Kartenblatts (AK 13, #958) als Platte der Instrumententafel (#980, Entwurf
 * `.platte`, `.platte-kopf`, Z. 543–557): Fläche, Haarlinie, Schatten und ein Kopf mit dem Namen,
 * als benannter Bereich für Screenreader. Die drei Blöcke und ihr Inhalt stehen fest (Plan #932):
 * Beschreibung und Details, Zuordnung, Verlauf. `bereich` ist die Rasterfläche des Blocks — ein
 * fester Schlüssel, weil der Name der Sprache folgt.
 */
function KartenBlock({ name, bereich, children }: Readonly<{ name: string; bereich: 'blatt' | 'felder' | 'verlauf'; children: ReactNode }>) {
  return (
    <Box
      component="section"
      aria-label={name}
      data-bereich={bereich}
      sx={{ bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: `${PANEL_RADIUS}px`, boxShadow: SCHATTEN_PLATTE, overflow: 'hidden', minWidth: 0 }}
    >
      <Box
        aria-hidden
        sx={{ ...ANZEIGE, fontSize: 13.5, fontWeight: 600, px: '16px', py: '11px', borderBottom: 1, borderColor: 'divider', background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE})` }}
      >
        {name}
      </Box>
      <Stack spacing={2} sx={{ px: '18px', pt: '16px', pb: '18px' }}>
        {children}
      </Stack>
    </Box>
  )
}

/** View-Modus-Inhalt: Beschreibung (Markdown mit Task-Checkboxen), Abhängigkeiten, Fälligkeitsdatum. */
function CardBodyView({
  body,
  beschreibungStatus,
  canEdit,
  onToggleTask,
  dependencies,
  derivedFrom,
  onOpenDependency,
  isEpic,
  dueDate,
  dueOverdue,
}: Readonly<{
  body: string
  beschreibungStatus: BeschreibungStatus
  canEdit: boolean
  onToggleTask: (index: number) => void
  dependencies: number[]
  onOpenDependency?: (number: number) => void
  isEpic: boolean
  dueDate: string | null
  dueOverdue: boolean
  derivedFrom: number | null
}>) {
  const { sprache } = useSprache()
  const t = useT()
  return (
    <>
      <Box aria-label={t.KARTE.BESCHREIBUNG} data-testid="description-view" sx={descriptionSx}>
        {beschreibungStatus === 'laedt' && (
          <Typography role="status" color="text.secondary">
            {t.KARTE.BESCHREIBUNG_LAEDT}
          </Typography>
        )}
        {beschreibungStatus === 'fehler' && (
          <Alert severity="error">{t.KARTE.BESCHREIBUNG_FEHLER}</Alert>
        )}
        {beschreibungStatus === 'geladen' &&
          (body ? (
            <TaskMarkdown body={body} canEdit={canEdit} onToggle={onToggleTask} />
          ) : (
            <Typography color="text.secondary">{t.KARTE.KEINE_BESCHREIBUNG}</Typography>
          ))}
      </Box>
      {dependencies.length > 0 && (
        <DependencyList dependencies={dependencies} onOpen={onOpenDependency} />
      )}
      {!isEpic && derivedFrom != null && (
        <Typography variant="body2" color="text.secondary" aria-label={t.KARTE.HERKUNFT}>
          {t.KARTE.HERVORGEGANGEN_AUS(derivedFrom)}
        </Typography>
      )}
      {!isEpic && dueDate && (
        <Typography
          variant="body2"
          aria-label={t.KARTE.FAELLIGKEIT}
          color={dueOverdue ? 'error' : 'text.secondary'}
          sx={{ fontWeight: dueOverdue ? 600 : 400 }}
        >
          {t.KARTE.FAELLIG_AM(formatDueDate(dueDate, sprache))}
          {dueOverdue && t.KARTE.UEBERFAELLIG}
        </Typography>
      )}
    </>
  )
}

/**
 * „Vorgang eröffnen" (Issue #647): der Knopf in der Fußzeile und der Dialog für Name, Kürzel und
 * Beschreibung (Issue #1932).
 * Eigene Komponente mit eigenem Zustand (Plan #1042, E11) — Name, Kürzel und Offen-Zustand
 * interessieren ausschließlich diesen einen Weg und belasteten die Kartenmaske sonst mit drei
 * weiteren Zustandsfeldern.
 *
 * Steht der Weg nicht offen, rendert die Komponente nichts: Der Knopf entfällt, und der Dialog
 * hätte ohne ihn keinen Auslöser.
 */
function VorgangEroeffnenDialog({
  cardId,
  cardTitle,
  canEdit,
  isEpic,
  archived,
  parentId,
  cardsApi,
  kitNamen,
  onChanged,
  onOpenDependency,
}: Readonly<{
  cardId: number
  cardTitle: string
  canEdit: boolean
  isEpic: boolean
  archived: boolean
  parentId: number | null
  cardsApi: Pick<typeof defaultCardsApi, 'openEpic'>
  /** Das Namen-Register aus der Kartenmaske: Präfixe für den Namensvorschlag (Issue #1932). */
  kitNamen: KitNamenZustand
  onChanged?: () => void
  onOpenDependency?: (number: number) => void
}>) {
  const notify = useSnackbar()
  const t = useT()
  // Der Name ist mit dem Kartentitel vorbelegt, ein Requirement-Praefix durch `[Epic]` ersetzt, und
  // ueberschreibbar; Kuerzel und Beschreibung sind optional. Die Beschreibung startet leer: Vom
  // Requirement wird nichts kopiert (Plan #637 E2, Issue #1932).
  const [offen, setOffen] = useState(false)
  const [name, setName] = useState('')
  const [kuerzel, setKuerzel] = useState('')
  const [beschreibung, setBeschreibung] = useState('')

  /**
   * Ob an dieser Karte ein Vorgang eroeffnet werden kann — dieselben Bedingungen, die das Backend
   * in #640 prueft. Die Oberflaeche zeigt den Weg gar nicht erst an, statt ihn in einen Fehler
   * laufen zu lassen.
   */
  const kannVorgangEroeffnen = canEdit && !isEpic && !archived && parentId === null

  const eroeffneVorgang = async () => {
    try {
      const vorhaben = await cardsApi.openEpic(
        cardId,
        name.trim(),
        kuerzel.trim() === '' ? null : kuerzel.trim(),
        beschreibung.trim() === '' ? null : beschreibung,
      )
      setOffen(false)
      onChanged?.()
      notify(t.KARTE.VORGANG_EROEFFNET(vorhaben.title), 'success')
      // Zum neuen Vorhaben: ueber denselben Verweis-Stack wie die `#N`-Spruenge. Fehlt die
      // Projekt-ID, gibt es keine Aufloesung — dann bleibt es bei der Meldung.
      onOpenDependency?.(vorhaben.number)
    } catch (error_: unknown) {
      // Die Meldung des Servers, nicht eine eigene: Die Ablehnungen aus #640 tragen einen
      // Feldbezug, und ein verschluckter Text liesse den Nutzer raten.
      notify(apiErrorMessage(error_, t.KARTE.VORGANG_FEHLER), 'error')
    }
  }

  if (!kannVorgangEroeffnen) {
    return null
  }

  return (
    <>
      <VorschauGesperrt>
        <Button
          onClick={() => {
            setName(
              vorhabenNameVorschlag(
                kitNamen.status === 'fertig' ? kitNamen.daten.titelPraefixe : undefined,
                cardTitle,
              ),
            )
            setKuerzel('')
            setBeschreibung('')
            setOffen(true)
          }}
        >
          {t.KARTE.VORGANG_EROEFFNEN}
        </Button>
      </VorschauGesperrt>
      {/* Eigener Dialog statt Inline-Feldern, damit die Kartenmaske im Lesemodus nicht zwei
          Eingabefelder traegt, die fast nie gebraucht werden. */}
      <Dialog open={offen} onClose={() => setOffen(false)} fullWidth maxWidth="xs">
        <DialogTitle sx={dialogTitleSx}>{t.KARTE.VORGANG_EROEFFNEN}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label={t.KARTE.VORHABEN_NAME}
              value={name}
              onChange={(e) => setName(e.target.value)}
              fullWidth
              autoFocus
            />
            <TextField
              label={t.KARTE.KUERZEL_OPTIONAL}
              value={kuerzel}
              onChange={(e) => setKuerzel(e.target.value)}
              fullWidth
            />
            <TextField
              label={t.KARTE.VORHABEN_BESCHREIBUNG}
              value={beschreibung}
              onChange={(e) => setBeschreibung(e.target.value)}
              fullWidth
              multiline
              minRows={3}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOffen(false)}>{t.ALLGEMEIN.ABBRECHEN}</Button>
          <VorschauGesperrt>
            <Button
              variant="contained"
              onClick={() => void eroeffneVorgang()}
              disabled={name.trim() === ''}
            >
              {t.KARTE.EROEFFNEN}
            </Button>
          </VorschauGesperrt>
        </DialogActions>
      </Dialog>
    </>
  )
}

/**
 * Fußzeile der Kartenmaske: im Editiermodus Abbrechen und Speichern, sonst die Alltags-Aktionen.
 * Eigene Komponente (Plan #1042, E11), damit der Editier-Ternär mit seinen beiden Zweigen nicht in
 * der Kartenmaske zählt. `vorgang` ist der Platz des „Vorgang eröffnen"-Wegs, der seinen Zustand
 * selbst hält.
 */
function KartenDialogAktionen({
  editing,
  canEdit,
  archived,
  speichernGesperrt,
  onAbbrechen,
  onSpeichern,
  onWiederherstellen,
  onClose,
  vorgang,
}: Readonly<{
  editing: boolean
  canEdit: boolean
  archived: boolean
  speichernGesperrt: boolean
  onAbbrechen: () => void
  onSpeichern: () => void
  onWiederherstellen: () => void
  onClose: () => void
  vorgang: ReactNode
}>) {
  const t = useT()
  if (editing) {
    return (
      <DialogActions>
        <Button onClick={onAbbrechen}>{t.ALLGEMEIN.ABBRECHEN}</Button>
        <VorschauGesperrt>
          <Button variant="contained" onClick={onSpeichern} disabled={speichernGesperrt}>
            {t.ALLGEMEIN.SPEICHERN}
          </Button>
        </VorschauGesperrt>
      </DialogActions>
    )
  }
  return (
    <DialogActions>
      {canEdit && archived && (
        <VorschauGesperrt>
          <Button onClick={onWiederherstellen}>{t.ALLGEMEIN.WIEDERHERSTELLEN}</Button>
        </VorschauGesperrt>
      )}
      {vorgang}
      <Button onClick={onClose}>{t.ALLGEMEIN.SCHLIESSEN}</Button>
    </DialogActions>
  )
}

interface Props {
  card: CardDetail
  canEdit: boolean
  /**
   * Projekt der Karte. Nur mit dieser Angabe sind die `#N`-Abhängigkeitsverweise anklickbar — die
   * Nummer ist projektweit vergeben und wird über `cardsApi.byNumber` aufgelöst.
   */
  projectId?: number
  /** Ob der Nutzer Kommentare moderieren (löschen) darf — Projekt-ADMIN/OWNER oder Plattform-Admin. */
  canModerateComments?: boolean
  onClose: () => void
  onChanged?: () => void
  /** Direkt im Edit-Modus öffnen (z. B. aus dem Karten-⋮-Menü). */
  initialEditing?: boolean
  /**
   * Spaltenname für den Status-Chip einer Karte ohne eigenen Status (Dokumentarten). Ein
   * Arbeitspaket zeigt stattdessen `card.status` (Plan #1294, E16).
   */
  columnName?: string
  /**
   * Ort der Karte (Projekt / Board / Spalte) für den Modal-Kopf. Optional: Aufrufer ohne diesen
   * Kontext (Epics-Seite) zeigen wie bisher keinen Pfad — ein leerer Platzhalter
   * wäre schlechter als keine Angabe. `null` ist gleichbedeutend mit „nicht gesetzt", damit
   * Aufrufer mit noch nicht geladenem Board den Wert direkt durchreichen können.
   */
  location?: CardLocation | null
  /**
   * Board-Epics: Datenquelle für Titelanzeige und Fortschritt eines Vorhabens und zugleich der
   * Optionsvorrat des Auswahlfelds — auch ausgeblendete stehen zur Wahl (Issue #1981).
   */
  epics?: Epic[]
  /**
   * IDs der auf dem Board ausgeblendeten Vorhaben (Default: keines). Sie stehen im Auswahlfeld
   * markiert nach den übrigen; wird die Karte beim Speichern neu einem davon zugeordnet, sagt die
   * Meldung, dass sie auf dem Board jetzt verdeckt ist.
   */
  hiddenEpics?: ReadonlySet<number>
  /**
   * Ob die Epic-Zuordnung geändert werden darf (Default `true`). `false` heißt: Der Aufrufer konnte
   * den Optionsvorrat nicht laden (archiviertes Board, board-lose Idee). Ein leeres Dropdown böte
   * dann nur „(kein Epic)" an und löschte auf Klick eine bestehende Zuordnung, ohne sie je gezeigt
   * zu haben — deshalb bleibt das Feld dort lesend, während die übrigen Felder bearbeitbar sind.
   */
  canEditEpic?: boolean
  /** Ob die Labels geändert werden dürfen (Default `true`) — dieselbe Begründung wie {@link canEditEpic}. */
  canEditLabels?: boolean
  /** Projektmitglieder für die Zuständigen-Auswahl (Namen + Auswahlliste). */
  members?: Member[]
  /** Board-Labels für die Label-Auswahl (Name + Farbe). */
  boardLabels?: BoardLabel[]
  commentsApi?: CommentsApi
  attachmentsApi?: AttachmentsApi
  cardsApi?: Pick<
    typeof defaultCardsApi,
    | 'get'
    | 'update'
    | 'setAssignees'
    | 'addLabel'
    | 'removeLabel'
    | 'getActivity'
    | 'restore'
    | 'setStatus'
    | 'byNumber'
    | 'assignDerivedFrom'
    | 'epicTree'
    | 'cardTree'
    | 'openEpic'
  >
  boardsApi?: Pick<typeof defaultBoardsApi, 'get'>
  /**
   * Abruf der Nachtlauf-Anläufe (Issue #968) und des Kettenstands der Stufenleiste (Issue #1453);
   * ohne Angabe die echten Endpunkte.
   */
  nightRunsApi?: Pick<NightRunsApi, 'anlaeufeDerKarte' | 'kettenstand' | 'wartegrund'>
}

/**
 * Interne Props der Detail-Ansicht: Die Navigation über `#N`-Verweise steuert der `CardDetailModal`-
 * Wrapper, die Ansicht selbst kennt nur den Callback und den Nachlade-Zähler.
 */
type ViewProps = Props & {
  /** Öffnet den Verweis auf die Kartennummer; fehlt, wenn keine Auflösung möglich ist. */
  onOpenDependency?: (number: number) => void
  /**
   * Zählt hoch, wenn eine darüberliegende Ebene etwas geändert hat und abgenommen wurde (Plan #1980,
   * E12): Dann lädt die Ansicht Baum und Kartenstand neu, ohne ihre Eingaben zu verlieren.
   */
  neuLaden?: number
}

/**
 * Ob der Aufrufer die Karte mitsamt ihrer Board-Bindung hereingereicht hat. `CardDetail` — die Form,
 * die das Modal verlangt — kennt keine `boardId`; Aufrufer mit Board-Kontext übergeben aber eine
 * `Card` bzw. `CardByNumber`, die beide eine tragen. Als Prädikat statt als Feldzugriff, damit der
 * strukturelle Test an einer Stelle steht und der Aufrufer ohne Typumdeutung auskommt.
 */
function mitBoardBindung(card: CardDetail): card is CardDetail & { boardId: number } {
  return 'boardId' in card
}

function CardDetailModalView({
  card,
  canEdit,
  canModerateComments = false,
  onClose,
  onChanged,
  initialEditing = false,
  columnName,
  location,
  epics = [],
  hiddenEpics,
  canEditEpic = true,
  canEditLabels = true,
  members = [],
  boardLabels = [],
  commentsApi = defaultCommentsApi,
  attachmentsApi = defaultAttachmentsApi,
  cardsApi = defaultCardsApi,
  projectId,
  nightRunsApi,
  onOpenDependency,
  neuLaden = 0,
}: Readonly<ViewProps>) {
  const { user, vorschau: laufendeVorschau } = useAuth()
  // In der „Ansicht als XY“ ist alles Verändernde gesperrt (Plan #1914, E15).
  const vorschau = Boolean(laufendeVorschau)
  const t = useT()
  const isEpic = card.type === 'EPIC'
  const [assigneeIds, setAssigneeIds] = useState<number[]>(card.assignees)
  // Zaehlt Aufrufe von saveAssignees: Loest ein spaeter gestarteter Request seinen Rollback vor
  // einem frueher gestarteten aus, darf Letzterer den inzwischen aktuelleren Stand nicht mehr
  // ueberschreiben — nur der jeweils juengste Aufruf darf zuruecksetzen.
  const assigneesRequestSeq = useRef(0)

  const saveAssignees = async (ids: number[]) => {
    // Der Stand vor dem optimistischen Setzen: Ohne ihn bliebe eine abgelehnte Zuweisung im
    // Auswahlfeld stehen und behauptete einen Stand, den der Server nicht hat.
    const vorher = assigneeIds
    const requestId = ++assigneesRequestSeq.current
    setAssigneeIds(ids)
    try {
      await cardsApi.setAssignees(card.id, ids)
      onChanged?.()
    } catch (error_: unknown) {
      if (assigneesRequestSeq.current === requestId) {
        setAssigneeIds(vorher)
      }
      notify(apiErrorMessage(error_, t.KARTE.ZUSTAENDIGE_FEHLER), 'error')
    }
  }

  // Der angezeigte Status: Die `card`-Prop des Aufrufers zieht nach einem Wechsel nicht zwingend mit
  // (etwa bei einer über `#N` geöffneten Karte), deshalb hält die Ansicht den zugesagten Stand selbst.
  const [status, setStatus] = useState<CardStatus | null>(card.status)
  useEffect(() => setStatus(card.status), [card.status])

  const setzeStatus = async (ziel: CardStatus) => {
    try {
      await cardsApi.setStatus(card.id, ziel)
    } catch (error_: unknown) {
      notify(apiErrorMessage(error_, t.KARTE.STATUS_FEHLER), 'error')
      throw error_
    }
    setStatus(ziel)
    onChanged?.()
  }

  const restore = async () => {
    try {
      await cardsApi.restore(card.id)
      onChanged?.()
      onClose()
    } catch (error_: unknown) {
      // Bei einem Fehler bleibt der Dialog offen — die Karte bleibt sichtbar, wie sie ist.
      notify(apiErrorMessage(error_, t.KARTE.WIEDERHERSTELLEN_FEHLER), 'error')
    }
  }

  const [activities, setActivities] = useState<CardActivity[]>([])
  const actorName = (userId: number | null) =>
    members.find((m) => m.userId === userId)?.displayName ?? t.KARTE.SYSTEM

  const [labelIds, setLabelIds] = useState<number[]>(card.labels)
  // Gleiche Race wie bei den Zuständigen: nur der juengste Aufruf darf zuruecksetzen.
  const labelsRequestSeq = useRef(0)
  /**
   * Schickt genau die Änderung, je Label einen Aufruf — nie die volle Liste: Die baute sich aus dem
   * angezeigten Stand, und ein veralteter schriebe ein inzwischen abgenommenes Label zurück, etwa
   * ein vom Runner verbrauchtes `kit:night` (Issue #1512). Danach gilt der Stand des Servers.
   */
  const aendereLabels = async ({ ab, an }: LabelAenderung) => {
    // Rollback wie bei den Zuständigen: der State steht vor der Zusage des Servers.
    const vorher = labelIds
    const requestId = ++labelsRequestSeq.current
    setLabelIds([...labelIds.filter((id) => !ab.includes(id)), ...an.filter((id) => !labelIds.includes(id))])
    let antwort: Card | null = null
    try {
      // eslint-disable-next-line no-await-in-loop -- jeder Aufruf liefert die ganze Karte; gleichzeitige Mutationen derselben Karte verlören eine Änderung
      for (const id of ab) antwort = await cardsApi.removeLabel(card.id, id)
      // eslint-disable-next-line no-await-in-loop -- jeder Aufruf liefert die ganze Karte; gleichzeitige Mutationen derselben Karte verlören eine Änderung
      for (const id of an) antwort = await cardsApi.addLabel(card.id, id)
      if (antwort !== null && labelsRequestSeq.current === requestId) {
        setLabelIds(antwort.labels)
      }
      onChanged?.()
    } catch (error_: unknown) {
      if (labelsRequestSeq.current === requestId) {
        // Was schon durchging, zeigt die letzte Antwort; ohne sie gilt der Ausgangsstand.
        setLabelIds(antwort?.labels ?? vorher)
      }
      notify(apiErrorMessage(error_, t.KARTE.LABELS_FEHLER), 'error')
    }
  }
  /** Die Label-Sektion meldet die volle Auswahl; gesendet wird nur, was sich daran geändert hat. */
  const saveLabels = (ids: number[]) =>
    aendereLabels({
      ab: labelIds.filter((id) => !ids.includes(id)),
      an: ids.filter((id) => !labelIds.includes(id)),
    })

  // `editing` startet immer im Lesemodus: `initialEditing` greift erst, wenn die Beschreibung da
  // ist (siehe Nachlade-Effekt) — sonst öffnete die Maske mit einem leeren Feld, dessen Speichern
  // den Volltext löschte.
  const [editing, setEditing] = useState(false)
  // Der vom Server bestätigte Stand (#1328). Die `card`-Prop des Parents bleibt nach dem Speichern
  // veraltet, ihre Beschreibung fehlt ganz (#771) — sie ist nur der Startwert bis zum Nachladen.
  // Kein Sync-Effect auf die Prop nötig: der Wrapper remountet die View per `key` je Karte.
  // `startStand` ändert sich nie und dient beim Nachladen als Rückfallwert für Felder, die eine
  // Antwort nicht trägt.
  // eslint-disable-next-line react/hook-use-state -- der Wert entsteht einmal und wird nie gesetzt; ein Setter nur fürs Lint wäre toter Code
  const [startStand] = useState<KartenStand>(() => standAus(card))
  const [stand, setStand] = useState<KartenStand>(startStand)
  const [beschreibungStatus, setBeschreibungStatus] = useState<BeschreibungStatus>('laedt')
  // Der Entwurf des Bearbeiten-Dialogs. Er wird bei jedem Start des Bearbeitens aus `stand`
  // befüllt; Abbrechen verwirft ihn, indem es nur den Editiermodus verlässt.
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [parentId, setParentId] = useState<number | null>(null)
  const [shortcode, setShortcode] = useState('')
  const [dueInput, setDueInput] = useState('')
  const [depsInput, setDepsInput] = useState('')
  const [depsError, setDepsError] = useState<string | null>(null)
  const [herkunftInput, setHerkunftInput] = useState('')
  const [herkunftError, setHerkunftError] = useState<string | null>(null)

  // Befüllt den Entwurf aus einem Stand — gemeinsam für „Bearbeiten" und `initialEditing`. Nur
  // Setter im Rumpf, deshalb stabil und im Nachlade-Effekt ohne Neustart verwendbar; der React
  // Compiler verlangt sie trotzdem ausdrücklich in den Abhängigkeiten.
  const fuelleEntwurf = useCallback((s: KartenStand) => {
    setTitle(s.title)
    setBody(s.description)
    setParentId(s.parentId)
    setShortcode(s.shortcode ?? '')
    setDueInput(s.dueDate ? s.dueDate.slice(0, 10) : '')
    setDepsInput(s.dependencies.join(', '))
    setDepsError(null)
    // Auch die Herkunft (E5): Sonst kehrte ein per Abbrechen verworfener Entwurf beim nächsten
    // Bearbeiten zurück und würde mitgespeichert.
    setHerkunftInput(s.derivedFrom == null ? '' : String(s.derivedFrom))
    setHerkunftError(null)
  }, [setTitle, setBody, setParentId, setShortcode, setDueInput, setDepsInput, setDepsError, setHerkunftInput, setHerkunftError])
  const [saving, setSaving] = useState(false)
  const notify = useSnackbar()

  const [comments, setComments] = useState<Comment[]>([])
  // Das Namen-Register des Kits (Issue #1595): Titel-Präfixe und Labels der Stufenleiste.
  const kitNamen = useKitNamen()
  // Die Szenarien der fachlichen Anforderung für die Abnahme von Hand (Issue #1629, Plan #1626).
  const beschreibung = stand.description
  const szenarien = useMemo(() => szenarienAus(beschreibung), [beschreibung])
  const [abnahmeOffen, setAbnahmeOffen] = useState(false)
  // Nur an einer fachlichen Anforderung mit Szenarien, nach dem Laden von Register und
  // Beschreibung und nicht beim Bearbeiten (E4, E5). Keine Rechteprüfung: Die macht der Server (E6).
  const zeigtAbnahme =
    kitNamen.status === 'fertig' &&
    artAusTitel(kitNamen.daten, stand.title) === 'FACHLICH' &&
    beschreibungStatus === 'geladen' &&
    !editing &&
    szenarien.length > 0
  const [newComment, setNewComment] = useState('')
  const [editingCommentId, setEditingCommentId] = useState<number | null>(null)
  const [editingBody, setEditingBody] = useState('')
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [previews, setPreviews] = useState<Record<number, string>>({})
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [preview, setPreview] = useState<{ attachment: Attachment; url: string } | null>(null)
  const [baum, setBaum] = useState<DerivationNode[] | null>(null)
  const [baumFehler, setBaumFehler] = useState<string | null>(null)
  // Der Zweig einer gewöhnlichen Karte (Issue #1986): `null` lädt, leer heißt „keine Kette“.
  const [zweig, setZweig] = useState<DerivationNode[] | null>(null)
  const [zweigFehler, setZweigFehler] = useState<string | null>(null)

  useEffect(() => {
    // Neuester zuerst (#843): Der Server liefert stabil nach `createdAt, id` aufsteigend; das
    // Umkehren der ganzen Liste ergibt exakt `createdAt desc, id desc` — der Tie-Break (#472) dreht
    // sich mit, ohne dass die Sortierregel hier ein zweites Mal formuliert werden muss. Gedreht
    // wird nur an dieser einen Stelle, damit es genau eine Wahrheit über die Reihenfolge gibt.
    void commentsApi.list(card.id).then((list) => setComments([...list].reverse()))
    void attachmentsApi.list(card.id).then((list) => {
      setAttachments(list)
      loadImagePreviews(list, attachmentsApi, setPreviews)
    })
  }, [card.id, commentsApi, attachmentsApi])

  useEffect(() => {
    void cardsApi.getActivity(card.id).then(setActivities).catch(() => setActivities([]))
  }, [card.id, cardsApi])

  // Die volle Beschreibung beim Öffnen nachladen (Issue #769) — nach dem `aktiv`-Muster des
  // Herkunftsbaum-Effekts: Der Cleanup verwirft späte Antworten, ein Kartenwechsel setzt auf
  // „lädt" zurück. Aus der Antwort werden die sieben Stand-Felder übernommen (E2): Bei synthetischen
  // Vorhaben-Karten (`epicToCard`) sind sie in der Prop nur Ersatzwerte, und ein Checkbox-Klick
  // schickte sie sonst als gespeicherten Stand zurück. Alle übrigen Felder (Spalte, Status,
  // Zuständige, Labels …) kommen weiter aus der Prop.
  // Ein Nachladen nach einer Änderung darüber (E12) setzt nicht auf „lädt" zurück und öffnet den
  // Entwurf nicht erneut: Die Ebene bleibt, wie man sie verlassen hat, nur mit dem neuen Stand.
  useEffect(() => {
    let aktiv = true
    const nachladen = neuLaden > 0
    if (!nachladen) setBeschreibungStatus('laedt')
    void cardsApi.get(card.id).then(
      (voll) => {
        if (!aktiv) return
        const geladen = standAus({ ...startStand, ...voll })
        setStand(geladen)
        setBeschreibungStatus('geladen')
        if (nachladen) {
          setStatus(voll.status)
          return
        }
        // Der Entwurf ohne Editier-Guard: Bis hierhin ist der Bearbeiten-Button gesperrt und
        // `editing` startet mit `false` — es gibt keine Eingabe, die hier überschrieben würde.
        if (initialEditing) {
          fuelleEntwurf(geladen)
          setEditing(true)
        }
      },
      () => {
        if (!aktiv || nachladen) return
        setBeschreibungStatus('fehler')
      },
    )
    return () => {
      aktiv = false
    }
  }, [card.id, cardsApi, initialEditing, fuelleEntwurf, startStand, neuLaden])

  // Der Baum eines Vorhabens (Issue #644). Das Laden liegt hier und nicht mehr in
  // `DerivationTree`: Der Dialog kennt die Karte ohnehin, und Lade-, Fehler- und Leerzustand
  // gehoeren zu ihm — die uebrigen Bereiche bleiben in jedem dieser Faelle bedienbar.
  // Nur Aufrufer mit Board-Kontext reichen eine `boardId` herein: `CardDetail` — die Form, die das
  // Modal verlangt — kennt sie nicht. Ohne sie gibt es keinen Baum abzurufen; der Dialog bleibt
  // dann ohne diesen Bereich, statt zu raten.
  const baumBoardId: number | null = mitBoardBindung(card) ? card.boardId : null

  // Der Fortschritt eines Vorhabens (Issue #686), derselbe Wortlaut wie auf der Kachel der
  // Vorhaben-Seite. Die Zahlen stehen nur in der `epics`-Prop: Der `Card`-Typ traegt kein
  // `done`/`total`. Fehlt der Eintrag, gibt es keinen Wert — und ein erfundenes „0 von 0" waere
  // eine Falschaussage. Anders als der Baum haengt die Anzeige nicht an der `boardId`: Ein ueber
  // einen `#N`-Verweis nachgeladenes Vorhaben hat keine, die Zahlen liegen trotzdem vor.
  const fortschritt = isEpic ? (epics.find((e) => e.id === card.id) ?? null) : null

  useEffect(() => {
    if (!isEpic || baumBoardId === null) {
      return undefined
    }
    let aktiv = true
    // Beim Nachladen (E12) bleibt der alte Baum stehen, bis der neue da ist — kein Sprung im Dialog.
    if (neuLaden === 0) {
      setBaum(null)
      setBaumFehler(null)
    }
    void cardsApi
      .epicTree(baumBoardId, card.id)
      .then(
        (zeilen) => ({ zeilen, fehler: null as string | null }),
        (error_: unknown) => ({
          zeilen: null,
          fehler: apiErrorMessage(error_, t.KARTE.BAUM_FEHLER),
        }),
      )
      .then((ergebnis) => {
        if (!aktiv) {
          return
        }
        setBaum(ergebnis.zeilen)
        setBaumFehler(ergebnis.fehler)
      })
    return () => {
      aktiv = false
    }
  }, [isEpic, baumBoardId, card.id, cardsApi, t, neuLaden])

  // Der Zweig einer Karte im Block „Zuordnung“ (Issue #1986, Plan #1980) — nach dem `aktiv`-Muster
  // des Vorhaben-Baums. Ein Vorhaben hat seinen eigenen Baum; der Endpunkt lieferte dort ohnehin leer.
  useEffect(() => {
    if (isEpic) {
      return undefined
    }
    let aktiv = true
    if (neuLaden === 0) {
      setZweig(null)
      setZweigFehler(null)
    }
    void cardsApi
      .cardTree(card.id)
      .then(
        (zeilen) => ({ zeilen, fehler: null as string | null }),
        (error_: unknown) => ({
          zeilen: null,
          fehler: apiErrorMessage(error_, t.KARTE.BAUM_FEHLER),
        }),
      )
      .then((ergebnis) => {
        if (!aktiv) {
          return
        }
        setZweig(ergebnis.zeilen)
        setZweigFehler(ergebnis.fehler)
      })
    return () => {
      aktiv = false
    }
  }, [isEpic, card.id, cardsApi, t, neuLaden])

  const startEditing = () => {
    // Aus dem bestätigten Stand, nicht aus der Prop — sonst verwürfe erneutes Bearbeiten nach
    // einem Save die gerade gespeicherten Werte (#537, #769).
    fuelleEntwurf(stand)
    setEditing(true)
  }

  const save = async () => {
    // Kein `!title.trim() || saving`-Guard nötig: der einzige Aufrufer ist der Speichern-Button,
    // der exakt unter diesen Bedingungen disabled ist (siehe DialogActions) — der Guard wäre tot.
    const { deps: parsedDeps, valid } = parseDependencyInput(depsInput)
    if (!valid) {
      setDepsError(t.KARTE.ABHAENGIGKEITEN_UNGUELTIG)
      return
    }
    const { herkunft, valid: herkunftValid } = parseHerkunftInput(herkunftInput)
    if (!herkunftValid) {
      setHerkunftError(t.KARTE.HERKUNFT_UNGUELTIG)
      return
    }
    setSaving(true)
    try {
      // Herkunft zuerst und nur bei Aenderung: Sie laeuft ueber einen eigenen Endpunkt (#607),
      // damit `update` sie nicht anfassen kann. Wird sie abgelehnt, bleibt die Maske im
      // Editiermodus und der uebrige Speichervorgang unterbleibt.
      // Der Stand übernimmt die Herkunft sofort aus der Antwort (E1): Scheitert `update` danach,
      // trägt er, was der Server bereits hat.
      if (!isEpic && herkunft !== stand.derivedFrom) {
        const mitHerkunft = await cardsApi.assignDerivedFrom(card.id, herkunft)
        setStand((s) => ({ ...s, derivedFrom: mitHerkunft.derivedFrom ?? null }))
      }
      const gespeichert = await cardsApi.update(
        card.id,
        title.trim(),
        body,
        parsedDeps,
        isEpic ? shortcode.trim() || null : undefined,
        isEpic ? undefined : parentId,
        isEpic ? undefined : dueInputToIso(dueInput),
      )
      // Die Antwort, nicht die gesendeten Werte: Der Server normalisiert (Fälligkeit, Kürzel).
      setStand(standAus(gespeichert))
      setEditing(false)
      onChanged?.()
      const verdeckend = verdeckendesVorhaben(stand.parentId, gespeichert.parentId, hiddenEpics, epics)
      notify(
        verdeckend ? t.BOARD.ZUORDNEN_ERFOLG_AUSGEBLENDET(1, verdeckend.title) : t.KARTE.GESPEICHERT,
        'success',
      )
    } catch (err) {
      // Die drei Herkunfts-Ablehnungen kommen feldbezogen (#607). Sie gehoeren ans Feld, nicht in
      // einen Toast: Im selben Aufruf entstehen fast wortgleiche Abhaengigkeits-Fehler, und die
      // Eingabe darf nicht verlorengehen.
      const feldmeldung = err instanceof ApiError ? err.fieldErrors?.derivedFrom : undefined
      if (feldmeldung) {
        setHerkunftError(feldmeldung)
      } else {
        notify(apiErrorMessage(err, t.ALLGEMEIN.SPEICHERN_FEHLGESCHLAGEN), 'error')
      }
    } finally {
      setSaving(false)
    }
  }

  // Klick auf eine Checkbox im View-Modus: n-ten Marker im Beschreibungstext flippen und sofort
  // persistieren (optimistisch, Rollback bei Fehler) — ohne den Edit-Modus zu öffnen. Gesendet wird
  // allein der bestätigte Stand (#1328): Die Prop wäre nach einem Speichern veraltet, und der
  // Entwurf kann ein per Abbrechen verworfener sein.
  const toggleTask = async (index: number) => {
    if (!canEdit || vorschau || saving) return
    const vorher = stand
    // `index` stammt immer aus einer real gerenderten Checkbox, und toggleTaskAt zählt über
    // denselben GFM-Parser wie der Renderer (MarkdownInput zählt dieselbe Dokumentreihenfolge),
    // daher findet toggleTaskAt stets einen Treffer und flippt — ein No-op-Ergebnis
    // (next === previous) ist ausgeschlossen, kein toter Guard nötig.
    const next = toggleTaskAt(vorher.description, index)
    setStand({ ...vorher, description: next })
    setSaving(true)
    try {
      const gespeichert = await cardsApi.update(
        card.id,
        vorher.title,
        next,
        vorher.dependencies,
        isEpic ? vorher.shortcode : undefined,
        isEpic ? undefined : vorher.parentId,
        isEpic ? undefined : vorher.dueDate,
      )
      setStand(standAus(gespeichert))
      onChanged?.()
    } catch (error_: unknown) {
      setStand(vorher)
      notify(apiErrorMessage(error_, t.KARTE.AUFGABE_FEHLER), 'error')
    } finally {
      setSaving(false)
    }
  }

  // Jede Abnahme ist ein neuer Kommentar (E11), vorne einsortiert wie bei `addComment`. Ein Fehler
  // geht an den Dialog, der ihn zeigt und die Vermerke behält.
  const schreibeAbnahme = async (text: string) => {
    const created = await commentsApi.create(card.id, text)
    setComments((c) => [created, ...c])
  }

  const addComment = async () => {
    if (!newComment.trim()) return
    try {
      const created = await commentsApi.create(card.id, newComment.trim())
      // Vorne einsortiert (#843): Der neue Kommentar ist der jüngste und steht damit ohne
      // Neuladen an der Stelle, an der ihn die Sortierregel erwartet.
      setComments((c) => [created, ...c])
      // Erst nach der Zusage des Servers leeren: Sonst wäre der verfasste Text weg, ohne dass er
      // irgendwo läge.
      setNewComment('')
    } catch (error_: unknown) {
      notify(apiErrorMessage(error_, t.KARTE.KOMMENTAR_ANLEGEN_FEHLER), 'error')
    }
  }

  const deleteComment = async (id: number) => {
    try {
      await commentsApi.remove(id)
      setComments((c) => c.filter((x) => x.id !== id))
    } catch (error_: unknown) {
      notify(apiErrorMessage(error_, t.KARTE.KOMMENTAR_LOESCHEN_FEHLER), 'error')
    }
  }

  const startEditComment = (c: Comment) => {
    setEditingCommentId(c.id)
    setEditingBody(c.body)
  }

  const saveEditComment = async () => {
    if (editingCommentId == null || !editingBody.trim()) return
    try {
      const updated = await commentsApi.update(editingCommentId, editingBody.trim())
      setComments((cs) => cs.map((x) => (x.id === updated.id ? updated : x)))
      // Das Bearbeitungsfeld schließt erst nach der Zusage — sonst wäre die Änderung verloren.
      setEditingCommentId(null)
    } catch (error_: unknown) {
      notify(apiErrorMessage(error_, t.KARTE.KOMMENTAR_SPEICHERN_FEHLER), 'error')
    }
  }

  const uploadFile = async (file: File) => {
    setUploadError(null)
    let created: Attachment
    try {
      created = await attachmentsApi.upload(card.id, file)
    } catch (error_: unknown) {
      setUploadError(apiErrorMessage(error_, t.KARTE.UPLOAD_FEHLER))
      return
    }
    setAttachments((a) => [...a, created])
    // Vorschau separat: Scheitert nur ihr Abruf, ist der Anhang trotzdem angekommen — ein erneuter
    // Upload-Versuch waere hier ein zusaetzlicher, unnoetiger Anhang.
    if (created.contentType.startsWith('image/')) {
      try {
        const blob = await attachmentsApi.fetchBlob(created.id)
        setPreviews((p) => ({ ...p, [created.id]: URL.createObjectURL(blob) }))
      } catch (error_: unknown) {
        setUploadError(apiErrorMessage(error_, t.KARTE.VORSCHAU_NACH_UPLOAD_FEHLER))
      }
    }
  }

  const deleteAttachment = async (id: number) => {
    try {
      await attachmentsApi.remove(id)
      setAttachments((a) => a.filter((x) => x.id !== id))
    } catch (error_: unknown) {
      notify(apiErrorMessage(error_, t.KARTE.ANHANG_LOESCHEN_FEHLER), 'error')
    }
  }

  const openPreview = async (attachment: Attachment) => {
    setUploadError(null)
    try {
      const blob = await attachmentsApi.fetchBlob(attachment.id)
      setPreview({ attachment, url: URL.createObjectURL(blob) })
    } catch (error_: unknown) {
      setUploadError(apiErrorMessage(error_, t.KARTE.VORSCHAU_FEHLER))
    }
  }

  const closePreview = () => {
    setPreview((p) => {
      if (p) URL.revokeObjectURL(p.url)
      return null
    })
  }

  // Optionsvorrat, Lesezustand und Lesetext des Vorhaben-Felds — die Regeln stehen als reine
  // Funktion in `lib/cardEpic.ts` (Plan #1042, E11), samt Begründung der drei Fälle.
  const { epicOptionen, epicLesend, epicLesendText } = vorhabenFeld({
    canEditEpic,
    parentId,
    hiddenEpics,
    epics,
  })

  // Erledigt ist ein Arbeitspaket nach seinem Status, alles andere nach der Spalte (Plan #1294,
  // E7) — über den gemeinsamen Done-Maßstab, damit es im Frontend nur eine Antwort gibt.
  const dueOverdue = !isEpic && isOverdue(stand.dueDate, effektivDone({ status }, columnName ?? ''))

  // Aktuellen Toggle-Handler über ein Ref halten und als stabile Callback-Identität an TaskMarkdown
  // reichen, damit dessen `memo` greift (kein Remount der Beschreibung bei Kommentar-Nachladen).
  const toggleTaskRef = useRef(toggleTask)
  useEffect(() => {
    toggleTaskRef.current = toggleTask
  })
  const onToggleTask = useCallback((index: number) => {
    void toggleTaskRef.current(index)
  }, [])

  return (
    <>
    <Dialog
      open
      onClose={editing ? () => setEditing(false) : onClose}
      maxWidth={false}
      scroll="paper"
      // Im Kontextbereich zentrieren (unter der Kopfleiste, rechts der Sidebar) statt über dem ganzen
      // Viewport; Breite UND Höhe auf 90 % dieses Bereichs, damit der Rand auf allen vier Seiten
      // gleich groß ist (reines maxWidth="md" ergäbe eine von der Breakpoint-Breite abhängige,
      // meist andere horizontale Marge als die 90%-Höhe vertikal). Der Backdrop bleibt
      // bildschirmfüllend. Außerhalb der Shell (CSS-Variablen ungesetzt) fällt es auf volle
      // Zentrierung zurück (Default 0).
      sx={{
        '& .MuiDialog-container': {
          position: 'absolute',
          top: 'var(--app-content-top, 0px)',
          left: 'var(--app-content-left, 0px)',
          right: 0,
          bottom: 0,
          height: 'auto',
        },
      }}
      slotProps={{
        paper: { sx: { width: '90%', maxWidth: '90%', height: '90%', maxHeight: '90%', m: 0, boxShadow: SCHATTEN_HOCH } },
      }}
    >
      <DialogTitle sx={dialogTitleSx}>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ flexWrap: 'wrap' }}>
          <CardStatusChip
            isEpic={isEpic}
            columnName={columnName}
            status={status}
            canSetStatus={card.canSetStatus && location?.board.archived !== true}
            archived={card.archived}
            editing={editing}
            onSetStatus={setzeStatus}
          />
          {/* Nummer in Kupfer und Titel in Archivo wie der Kopf des Blatts (Entwurf `.blatt-nr`,
              `.blatt-titel`, Z. 1023–1024). */}
          <Typography component="span" sx={{ ...ZAHL, fontSize: 13, fontWeight: 500, color: 'primary.main' }}>
            #{card.number}
          </Typography>
          <Typography component="span" sx={{ ...ANZEIGE, fontStretch: '110%', fontSize: 19, fontWeight: 700, lineHeight: 1.25, letterSpacing: '-.01em' }}>
            {stand.title}
          </Typography>
          <Box sx={{ flexGrow: 1 }} />
          {zeigtAbnahme && (
            <VorschauGesperrt>
              <Button size="small" variant="outlined" onClick={() => setAbnahmeOffen(true)}>
                {t.KARTE.ABNAHME}
              </Button>
            </VorschauGesperrt>
          )}
          {canEdit && !editing && (
            <VorschauGesperrt>
              <Button
                size="small"
                variant="outlined"
                // Bis die Beschreibung da ist (oder ihr Abruf gescheitert ist), führte Bearbeiten in
                // ein leeres Feld, dessen Speichern den Volltext löschte.
                disabled={beschreibungStatus !== 'geladen'}
                onClick={startEditing}
              >
                {t.KARTE.BEARBEITEN}
              </Button>
            </VorschauGesperrt>
          )}
        </Stack>
        {/*
          Ortspfad unter dem Titel und klar untergeordnet (kleinere Schrift, sekundäre Farbe): Die
          Hauptaussage bleibt die Karte, der Pfad beantwortet nur „wo liegt sie". Als `span` statt
          als Überschrift — der `DialogTitle` ist bereits ein `h2` und nimmt nur Phrasing-Content
          auf — und ohne `aria-current`: Die aktuelle Seite ist nicht die Spalte, sondern die
          geöffnete Karte.
        */}
        {location && (
          <Box component="span" sx={{ display: 'block', mt: 0.5 }}>
            <Breadcrumbs
              items={cardLocationCrumbs(location)}
              variant="body2"
              component="span"
              currentPage={false}
            />
          </Box>
        )}
      </DialogTitle>

      {/* Instrumententafel (Entwurf `.blatt`, Z. 1020): der Grund der Warte, darauf links das Blatt
          mit Beschreibung und Verlauf, rechts die Felder der Zuordnung. Unter 1080 px untereinander. */}
      <DialogContent dividers sx={{ overflowY: 'auto', bgcolor: GRUND }}>
        <Box
          sx={{
            mt: 0.5,
            display: 'grid',
            gap: '16px',
            alignItems: 'start',
            gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: isEpic ? 'minmax(0,1fr)' : 'minmax(0,1.9fr) minmax(0,1fr)' },
            gridTemplateAreas: { xs: '"blatt" "felder" "verlauf"', lg: isEpic ? '"blatt" "verlauf"' : '"blatt felder" "verlauf felder"' },
            '& > [data-bereich="blatt"]': { gridArea: 'blatt' },
            '& > [data-bereich="felder"]': { gridArea: 'felder' },
            '& > [data-bereich="verlauf"]': { gridArea: 'verlauf' },
          }}
        >
          <KartenBlock name={t.KARTE.BLOCK_BESCHREIBUNG} bereich="blatt">
          {editing ? (
            <CardFields
              isEpic={isEpic}
              title={title}
              body={body}
              shortcode={shortcode}
              parentId={parentId}
              epics={epicOptionen}
              epicReadOnly={epicLesend}
              epicReadOnlyLabel={epicLesendText}
              depsInput={depsInput}
              depsError={depsError}
              dueInput={dueInput}
              onTitleChange={setTitle}
              onBodyChange={setBody}
              onShortcodeChange={setShortcode}
              onParentIdChange={setParentId}
              herkunftInput={herkunftInput}
              herkunftError={herkunftError}
              onDepsInputChange={(value) => {
                setDepsInput(value)
                if (depsError) setDepsError(null)
              }}
              onHerkunftInputChange={(value) => {
                setHerkunftInput(value)
                if (herkunftError) setHerkunftError(null)
              }}
              onDueInputChange={setDueInput}
            />
          ) : (
            <CardBodyView
              derivedFrom={stand.derivedFrom}
              body={stand.description}
              beschreibungStatus={beschreibungStatus}
              canEdit={canEdit}
              onToggleTask={onToggleTask}
              dependencies={stand.dependencies}
              onOpenDependency={onOpenDependency}
              isEpic={isEpic}
              dueDate={stand.dueDate}
              dueOverdue={dueOverdue}
            />
          )}

          {!editing && fortschritt !== null && (
            <>
              <Divider />
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t.KARTE.FORTSCHRITT}
                </Typography>
                <Typography variant="body2" color="text.secondary" aria-label={t.KARTE.FORTSCHRITT}>
                  {t.LISTE.FORTSCHRITT(fortschritt.done, fortschritt.total)}
                </Typography>
              </Box>
            </>
          )}

          {!editing && isEpic && baumBoardId !== null && (
            <>
              <Divider />
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t.KARTE.HERKUNFT}
                </Typography>
                {baumFehler !== null && <Alert severity="error">{baumFehler}</Alert>}
                {baumFehler === null && baum === null && (
                  <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
                    <CircularProgress size={24} />
                  </Box>
                )}
                {baum !== null && (
                  <DerivationTree
                    rows={baum}
                    // Enter auf einer Zeile oeffnet die Karte als Dialog darueber — ueber denselben
                    // Verweis-Stapel wie die `#N`-Spruenge. Ohne `projectId` gibt es keine
                    // Aufloesung; dann bleibt die Zeile ohne Ziel, wie eine Abhaengigkeitsmarke.
                    onOpenCard={(nummer) => onOpenDependency?.(nummer)}
                  />
                )}
              </Box>
            </>
          )}
          </KartenBlock>

          {/* Ein Vorhaben trägt weder Zuständige noch Labels — ohne Inhalt entfällt der Block. */}
          {!isEpic && (
            <KartenBlock name={t.KARTE.BLOCK_ZUORDNUNG} bereich="felder">
              <AssigneeSection
                canEdit={canEdit}
                members={members}
                assigneeIds={assigneeIds}
                onChange={(ids) => void saveAssignees(ids)}
                vorschau={vorschau}
              />
              <LabelSection
                canEdit={canEdit && canEditLabels}
                boardLabels={boardLabels}
                labelIds={labelIds}
                onChange={(ids) => void saveLabels(ids)}
                vorschau={vorschau}
              />
              {/* Nur im Lesemodus (E10); eine leere Liste heißt „keine Kette“ — dann kein Bereich. */}
              {!editing && (zweig === null || zweig.length > 0) && (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    {t.KARTE.HERKUNFT}
                  </Typography>
                  {zweigFehler !== null && <Alert severity="error">{zweigFehler}</Alert>}
                  {zweigFehler === null && zweig === null && (
                    <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
                      <CircularProgress size={24} />
                    </Box>
                  )}
                  {zweig !== null && (
                    <DerivationTree
                      rows={zweig}
                      aktuelleNummer={card.number}
                      // Derselbe Verweis-Weg wie die `#N`-Sprünge und der Baum des Vorhabens.
                      onOpenCard={(nummer) => onOpenDependency?.(nummer)}
                    />
                  )}
                </Box>
              )}
            </KartenBlock>
          )}

          {!editing && (
            <KartenBlock name={t.KARTE.BLOCK_VERLAUF} bereich="verlauf">
              {/* Die Stufenleiste der Nacht-Kette (Issue #1449, E1–E3): nur an [Fachlich] und [Plan],
                  bedienbar unter denselben Bedingungen wie die Label-Sektion. Body und Kommentare
                  braucht sie für Sperrhinweise und den Rückfall, solange der Kettenstand nicht
                  geladen ist (Issue #1450, #1453). Ob die Karte sie trägt, sagen erst die Kit-Namen
                  (Issue #1595): Bis dahin steht hier ihr Lade- oder Fehlerzustand (E10). */}
              {(kitNamen.status !== 'fertig' || traegtStufenleiste(kitNamen.daten, stand.title)) && (
                <KettenStufenleiste
                  kitNamen={kitNamen}
                  titel={stand.title}
                  labelIds={labelIds}
                  boardLabels={boardLabels}
                  disabled={!(canEdit && canEditLabels) || vorschau}
                  beschreibung={stand.description}
                  kommentare={comments}
                  onChange={(aenderung) => void aendereLabels(aenderung)}
                  cardId={card.id}
                  api={nightRunsApi}
                />
              )}
              {/* Nur mit Projekt gibt es etwas abzurufen: `projectId` ist am Modal optional
                  (Issue #968). */}
              {/* Warum die Karte in Ready wartet (Issue #1730): für jede Karte, ohne eigene Prüfung
                  der Spalte — ob Ready gilt, entscheidet der Server (Plan #1724 E12). */}
              <Wartegrund cardId={card.id} api={nightRunsApi} />
              {projectId != null && (
                <KartenAnlaeufe projectId={projectId} cardNumber={card.number} api={nightRunsApi} />
              )}
              <Divider />
              <AttachmentsSection
                attachments={attachments}
                previews={previews}
                uploadError={uploadError}
                canEdit={canEdit}
                onOpenPreview={(a) => void openPreview(a)}
                onDelete={(id) => void deleteAttachment(id)}
                onUpload={(file) => void uploadFile(file)}
              />

              <Divider />
              <CommentsSection
                comments={comments}
                currentUserId={user?.userId}
                canModerateComments={canModerateComments}
                editingCommentId={editingCommentId}
                editingBody={editingBody}
                newComment={newComment}
                onStartEdit={startEditComment}
                onSaveEdit={() => void saveEditComment()}
                onCancelEdit={() => setEditingCommentId(null)}
                onDelete={(id) => void deleteComment(id)}
                onEditingBodyChange={setEditingBody}
                onNewCommentChange={setNewComment}
                onAdd={() => void addComment()}
              />

              <Divider />
              <ActivitySection activities={activities} actorName={actorName} />
            </KartenBlock>
          )}
        </Box>
      </DialogContent>

      <KartenDialogAktionen
        editing={editing}
        canEdit={canEdit}
        archived={card.archived}
        speichernGesperrt={!title.trim() || saving || isTooLong(body)}
        onAbbrechen={() => setEditing(false)}
        onSpeichern={() => void save()}
        onWiederherstellen={() => void restore()}
        onClose={onClose}
        vorgang={
          <VorgangEroeffnenDialog
            cardId={card.id}
            cardTitle={stand.title}
            canEdit={canEdit}
            isEpic={isEpic}
            archived={card.archived}
            parentId={stand.parentId}
            cardsApi={cardsApi}
            kitNamen={kitNamen}
            onChanged={onChanged}
            onOpenDependency={onOpenDependency}
          />
        }
      />
    </Dialog>

    <AbnahmeSzenarienDialog
      open={abnahmeOffen}
      titel={stand.title}
      szenarien={szenarien}
      onClose={() => setAbnahmeOffen(false)}
      onAbschicken={schreibeAbnahme}
    />

    {preview && (
      <AttachmentPreview
        filename={preview.attachment.filename}
        contentType={preview.attachment.contentType}
        url={preview.url}
        downloadHref={`/api/attachments/${preview.attachment.id}`}
        onClose={closePreview}
      />
    )}
    </>
  )
}

/**
 * Eine über einen `#N`-Verweis geöffnete Karte samt aufgelöstem Spaltennamen für den Status-Chip,
 * als Ebene des Verweis-Stapels (Plan #1980, E11, E12).
 */
interface LinkedCard {
  card: CardByNumber
  columnName?: string
  /** Schlüssel der Ebene: Dieselbe Karte kann über einen Ring mehrfach im Stapel liegen. */
  ebene: number
  /** Ob in dieser Ebene etwas geändert wurde — beim Abnehmen lädt die Ebene darunter dann neu. */
  geaendert: boolean
  /** Nachlade-Zähler dieser Ebene, siehe `ViewProps.neuLaden`. */
  neuLaden: number
}

/**
 * Spaltenname der verknüpften Karte, aufgelöst über ihr eigenes Board — der Spaltenname der
 * Ausgangskarte gilt nicht, weil der Verweis projektweit auf ein anderes Board zeigen kann.
 * Scheitert der Board-Abruf, bleibt nur der Chip leer, statt die ganze Navigation abzubrechen.
 */
async function resolveColumnName(
  linked: CardByNumber,
  boardsApi: Pick<typeof defaultBoardsApi, 'get'>,
): Promise<string | undefined> {
  try {
    const board = await boardsApi.get(linked.boardId)
    return board.columns.find((c) => c.id === linked.columnId)?.name
  } catch {
    return undefined
  }
}

/**
 * Detail-Modal einer Karte mit Navigation über die `#N`-Abhängigkeitsverweise, den Baum der Karte
 * und den Baum des Vorhabens. Der Wrapper hält den Verweis-Stapel und zeigt jede Ebene als eigenen
 * `Dialog`: die Ausgangskarte zuunterst, jede später geöffnete darüber (Plan #1980, E11). Die
 * unteren bleiben eingehängt, damit Eingaben und Scrollstellung erhalten bleiben. Schließen, Escape
 * und ein Klick auf den Hintergrund nehmen nur den obersten ab — MUI reicht beides nur an den
 * obersten Dialog weiter —, und erst die Ausgangskarte ruft `onClose` des Aufrufers.
 *
 * Meldet eine obere Ebene eine Änderung, ruft der Stapel sofort `onChanged` des Aufrufers, damit
 * das Board nachzieht, und merkt sie sich: Beim Abnehmen lädt die Ebene darunter Baum und
 * Kartenstand neu und gilt selbst als geändert, damit auch tiefere Ebenen nachziehen (E12).
 *
 * Eine über einen Verweis geöffnete Karte wird bewusst **nur gelesen**: Ihr board-spezifischer
 * Bearbeitungskontext (Epics, Labels, Spalten) gehört zum Board des Aufrufers und wäre für ein
 * fremdes Board falsch. Zum Bearbeiten öffnet man die Karte auf ihrem eigenen Board. Aus demselben
 * Grund erbt sie auch den Ortspfad (`location`) der Ausgangskarte nicht — er zeigte sonst auf ein
 * fremdes Board. Lieber kein Pfad als ein falscher; der Status-Chip nennt weiterhin ihre Spalte.
 *
 * Ausgenommen ist der Statuswechsler eines Arbeitspakets: Sein Recht (`canSetStatus`) liefert der
 * Server mit der Karte selbst, für ihr eigenes Board — es hängt nicht am Kontext des Aufrufers
 * (Plan #1294, E10, E23; Plan #1980, E13).
 */
export function CardDetailModal(props: Readonly<Props>) {
  const { projectId, onChanged, cardsApi = defaultCardsApi, boardsApi = defaultBoardsApi } = props
  const [stack, setStack] = useState<LinkedCard[]>([])
  const [basisNeuLaden, setBasisNeuLaden] = useState(0)
  const naechsteEbene = useRef(0)
  const notify = useSnackbar()
  const t = useT()

  // Nicht auflösbar (gelöscht, kein Zugriff): Hinweis statt totem Sprung — die aktuelle Karte
  // bleibt offen. Eine Vorab-Prüfung aller Verweise würde je geöffneter Karte N Requests kosten.
  const openDependency = async (project: number, number: number) => {
    try {
      const linked = await cardsApi.byNumber(project, number)
      const columnName = await resolveColumnName(linked, boardsApi)
      const ebene = ++naechsteEbene.current
      setStack((s) => [...s, { card: linked, columnName, ebene, geaendert: false, neuLaden: 0 }])
    } catch {
      notify(t.KARTE.NICHT_GEFUNDEN(number), 'error')
    }
  }
  const onOpenDependency =
    projectId == null ? undefined : (n: number) => void openDependency(projectId, n)

  const markiereGeaendert = (ebene: number) => {
    setStack((s) => s.map((e) => (e.ebene === ebene ? { ...e, geaendert: true } : e)))
    onChanged?.()
  }

  // Schließen erreicht nur den obersten Dialog; `oben` ist deshalb immer der letzte Eintrag.
  const nimmAb = (oben: LinkedCard) => {
    const rest = stack.slice(0, -1)
    const unten = rest.at(-1)
    if (oben.geaendert && unten) {
      rest[rest.length - 1] = { ...unten, geaendert: true, neuLaden: unten.neuLaden + 1 }
    } else if (oben.geaendert) {
      setBasisNeuLaden((n) => n + 1)
    }
    setStack(rest)
  }

  return (
    <>
      <CardDetailModalView {...props} onOpenDependency={onOpenDependency} neuLaden={basisNeuLaden} />
      {stack.map((eintrag) => (
        <CardDetailModalView
          key={eintrag.ebene}
          card={eintrag.card}
          canEdit={false}
          canModerateComments={props.canModerateComments}
          columnName={eintrag.columnName}
          members={props.members}
          commentsApi={props.commentsApi}
          attachmentsApi={props.attachmentsApi}
          cardsApi={cardsApi}
          onClose={() => nimmAb(eintrag)}
          onChanged={() => markiereGeaendert(eintrag.ebene)}
          onOpenDependency={onOpenDependency}
          neuLaden={eintrag.neuLaden}
        />
      ))}
    </>
  )
}
