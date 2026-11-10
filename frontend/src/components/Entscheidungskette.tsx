import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useId, useState, type ReactNode } from 'react'
import type {
  Angabe,
  AkteurView,
  EntscheidungsketteView,
  Fundzahlen,
  Gliedart,
  GliedView,
  KitWert,
  Nachfahre,
  Verweis,
} from '../api/entscheidungskette'
import { formatierer } from '../i18n/format'
import { useSprache, useT } from '../i18n/SpracheContext'
import type { Woerterbuch } from '../i18n/de'
import { SCHRIFT_MONO } from '../theme'

type Texte = Woerterbuch['ENTSCHEIDUNGSKETTE']

/** Öffnet die Karte mit dieser Nummer über den Verweis-Stapel des Dialogs. */
type Oeffnen = ((nummer: number) => void) | undefined

/** Die Glieder, an denen ein Mensch freigibt und jemand anders ausführen kann (fachlich #2101, AK 8 und 10). */
const MIT_FREIGABE: ReadonlySet<Gliedart> = new Set<Gliedart>(['FREIGABE', 'UEBERTRAGUNG', 'VEROEFFENTLICHUNG'])

/** Ein Wert aus `Quelle:` wird nur mit dem Schema `https:` zum Link (Plan #2108 E16, Schema-Whitelist). */
function sichereAdresse(wert: string): string | null {
  try {
    return new URL(wert).protocol === 'https:' ? wert : null
  } catch {
    return null
  }
}

const gleicherAkteur = (a: AkteurView, b: AkteurView) => a.typ === b.typ && a.name === b.name && a.modell === b.modell

/**
 * Der Abschnitt „Entscheidungskette“ im Block „Verlauf“ (Issue #2118, Plan #2108 E7, E8): zugeklappt,
 * aufklappbar über einen Knopf mit `aria-expanded` — Enter und Leertaste bedienen ihn. Zugeklappt wird
 * der Inhalt nicht gerendert. Geladen hat ihn der Dialog; ob er erscheint (`sichtbar`), entscheidet
 * der Dialog ebenfalls.
 *
 * <p>Nichts wird stillschweigend ausgelassen (fachlich #2101, AK 5): Fehlt eine Angabe, steht an ihrer
 * Stelle „nicht erfasst“.
 */
export function Entscheidungskette({
  kette,
  onOpenCard,
}: Readonly<{ kette: EntscheidungsketteView; onOpenCard?: (nummer: number) => void }>) {
  const t = useT().ENTSCHEIDUNGSKETTE
  const [offen, setOffen] = useState(false)
  const inhaltId = useId()
  return (
    <Box data-testid="entscheidungskette">
      <Button
        onClick={() => setOffen((vorher) => !vorher)}
        aria-expanded={offen}
        aria-controls={inhaltId}
        color="inherit"
        size="small"
        endIcon={
          <ExpandMoreIcon
            sx={{ transform: offen ? 'rotate(180deg)' : 'none', transition: 'transform 150ms' }}
            aria-hidden
          />
        }
        sx={{ px: 0, typography: 'subtitle2', textTransform: 'none' }}
      >
        {t.TITEL}
      </Button>
      <Box id={inhaltId}>
        {offen && (
          <Stack spacing={1.5} sx={{ mt: 1 }}>
            {kette.abschnitte.map((abschnitt, index) => (
              <Box key={`${abschnitt.art}-${index}`}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {t.DOKUMENT[abschnitt.art]}
                </Typography>
                <Gliederliste glieder={abschnitt.glieder} onOpenCard={onOpenCard} />
              </Box>
            ))}
            {kette.nachfahren.length > 0 && (
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {t.NACHFAHREN}
                </Typography>
                <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                  {kette.nachfahren.map((nachfahre, index) => (
                    <NachfahreZeile key={index} nachfahre={nachfahre} onOpenCard={onOpenCard} />
                  ))}
                </Box>
              </Box>
            )}
          </Stack>
        )}
      </Box>
    </Box>
  )
}

function Gliederliste({ glieder, onOpenCard }: Readonly<{ glieder: GliedView[]; onOpenCard: Oeffnen }>) {
  return (
    <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
      {glieder.map((glied, index) => (
        <Glied key={`${glied.art}-${index}`} glied={glied} onOpenCard={onOpenCard} />
      ))}
    </Box>
  )
}

/** Ob ein nicht erfasstes Glied gar keine Angabe trägt — dann steht es allein als „nicht erfasst“. */
function ganzLeer(glied: GliedView): boolean {
  const { quelle } = glied
  return (
    glied.ausgefuehrtVon === null &&
    glied.freigegebenVon === null &&
    glied.zeitpunkt === null &&
    glied.ergebnis === null &&
    quelle.nummer === null &&
    quelle.karte === null &&
    quelle.kommentar === null &&
    glied.hinweise.length === 0
  )
}

function Glied({ glied, onOpenCard }: Readonly<{ glied: GliedView; onOpenCard: Oeffnen }>) {
  const t = useT().ENTSCHEIDUNGSKETTE
  const { sprache } = useSprache()
  const zeit = (iso: string | null) => (iso === null ? t.NICHT_ERFASST : zeitpunkt(iso, sprache))
  const zustandText = { ERFASST: null, NICHT_ERFASST: t.NICHT_ERFASST, NICHT_VORGESEHEN: t.NICHT_VORGESEHEN }[glied.zustand]
  const mitAngaben = glied.zustand === 'ERFASST' || (glied.zustand === 'NICHT_ERFASST' && !ganzLeer(glied))
  return (
    <Box component="li" data-testid={`kettenglied-${glied.art}`} sx={{ mt: 0.5 }}>
      <Typography variant="body2" component="div">
        {t.GLIED[glied.art]}
        {zustandText !== null && (
          <Typography component="span" variant="body2" color="text.secondary">
            {' · '}
            {zustandText}
          </Typography>
        )}
        {glied.hinweise.map((hinweis) => (
          <Typography key={hinweis} component="span" variant="body2" sx={{ fontWeight: 600 }}>
            {' · '}
            {t.HINWEIS[hinweis]}
          </Typography>
        ))}
      </Typography>
      {mitAngaben && (
        <Box component="dl" sx={{ m: 0 }}>
          <Akteure glied={glied} t={t} />
          <Paar label={t.ZEITPUNKT}>{zeit(glied.zeitpunkt)}</Paar>
          <Paar label={t.ERGEBNIS}>{glied.ergebnis ?? t.NICHT_ERFASST}</Paar>
          <Paar label={t.QUELLE}>
            <VerweisText verweis={glied.quelle} zeitpunkt={glied.zeitpunkt} onOpenCard={onOpenCard} />
          </Paar>
          <Einzelheiten glied={glied} />
        </Box>
      )}
      {/* Die Einarbeitung einer Prüfrunde ist ein eigenes Glied — als Liste nach den Angaben. */}
      {glied.pruefung !== null && <Gliederliste glieder={[glied.pruefung.einarbeitung]} onOpenCard={onOpenCard} />}
    </Box>
  )
}

/** Wer das Glied getan hat; Freigabe und Ausführung getrennt, wenn sie verschieden sind (AK 10). */
function Akteure({ glied, t }: Readonly<{ glied: GliedView; t: Texte }>) {
  const { freigegebenVon, ausgefuehrtVon } = glied
  if (!MIT_FREIGABE.has(glied.art) && freigegebenVon === null) {
    return <Paar label={t.VON}>{akteurText(ausgefuehrtVon, t)}</Paar>
  }
  if (freigegebenVon !== null && ausgefuehrtVon !== null && gleicherAkteur(freigegebenVon, ausgefuehrtVon)) {
    return <Paar label={t.FREIGEGEBEN_UND_AUSGEFUEHRT_VON}>{akteurText(freigegebenVon, t)}</Paar>
  }
  return (
    <>
      <Paar label={t.FREIGEGEBEN_VON}>{akteurText(freigegebenVon, t)}</Paar>
      <Paar label={t.AUSGEFUEHRT_VON}>{akteurText(ausgefuehrtVon, t)}</Paar>
    </>
  )
}

function akteurText(akteur: AkteurView | null, t: Texte): string {
  if (akteur === null) {
    return t.NICHT_ERFASST
  }
  const art = [t.AKTEURTYP[akteur.typ], ...(akteur.modell === null ? [] : [t.AKTEUR_MODELL(akteur.modell)])]
  return `${akteur.name ?? t.NICHT_ERFASST} (${art.join(', ')})`
}

/** Die Angaben, die nur ein Glied seiner Art trägt. */
function Einzelheiten({ glied }: Readonly<{ glied: GliedView }>) {
  const t = useT().ENTSCHEIDUNGSKETTE
  const { sprache } = useSprache()
  const angabe = (wert: Angabe) => angabeText(wert, t, sprache)
  const { pruefung, freigabe, umsetzung, codeReview, uebertragung, veroeffentlichung } = glied
  return (
    <>
      {pruefung !== null && (
        <>
          <Paar label={t.RUNDE}>{String(pruefung.runde)}</Paar>
          {pruefung.pruefer.length > 0 && (
            <Paar label={t.PRUEFER}>
              {pruefung.pruefer
                .map((p) => {
                  const wer = [p.rolle, p.modell].filter((teil): teil is string => teil !== null)
                  const name = wer.length > 0 ? `${t.PRUEFER_EINTRAG(p.nummer)} (${wer.join(', ')})` : t.PRUEFER_EINTRAG(p.nummer)
                  return `${name}: ${fundText(p.funde, t)}`
                })
                .join('; ')}
            </Paar>
          )}
          <Paar label={t.FUNDE}>{pruefung.funde === null ? t.NICHT_ERFASST : fundText(pruefung.funde, t)}</Paar>
        </>
      )}
      {freigabe !== null && <Paar label={t.FALL}>{t.FREIGABEFALL[freigabe.fall]}</Paar>}
      {umsetzung !== null && (
        <>
          <Paar label={t.AUSGANG}>{t.UMSETZUNGSAUSGANG[umsetzung.ausgang]}</Paar>
          <Paar label={t.COMMIT} mono>
            {umsetzung.commit ?? t.NICHT_ERFASST}
          </Paar>
          <Paar label={t.PRUEFLAUF}>{angabe(umsetzung.prueflauf)}</Paar>
        </>
      )}
      {codeReview !== null && <Paar label={t.FUNDE}>{fundText(codeReview.funde, t)}</Paar>}
      {uebertragung !== null && (
        <>
          <Paar label={t.WEG}>{wegText(uebertragung.weg, t)}</Paar>
          <Paar label={t.PRUEFLAUF}>{angabe(uebertragung.prueflauf)}</Paar>
          <Paar label={t.BUILD_DIENST}>{angabe(uebertragung.buildDienst)}</Paar>
          <Paar label={t.AUSGANG}>{uebertragung.ausgang ?? t.NICHT_ERFASST}</Paar>
          <Paar label={t.COMMIT} mono>
            {uebertragung.commit ?? t.NICHT_ERFASST}
          </Paar>
          {uebertragung.quelle !== null && (
            <Paar label={t.QUELLE}>
              <QuelleWert wert={uebertragung.quelle} />
            </Paar>
          )}
        </>
      )}
      {veroeffentlichung !== null && (
        <>
          <Paar label={t.VERSION}>{veroeffentlichung.version ?? t.NICHT_ERFASST}</Paar>
          {/* Der Ausgang steht wörtlich da (Plan #2108 E15). */}
          <Paar label={t.AUSGANG}>{veroeffentlichung.ausgang ?? t.NICHT_ERFASST}</Paar>
          <Paar label={t.COMMIT} mono>
            {veroeffentlichung.commit ?? t.NICHT_ERFASST}
          </Paar>
          {veroeffentlichung.quelle !== null && (
            <Paar label={t.QUELLE}>
              <QuelleWert wert={veroeffentlichung.quelle} />
            </Paar>
          )}
        </>
      )}
    </>
  )
}

function fundText(funde: Fundzahlen, t: Texte): string {
  return t.FUNDZAHLEN(funde.kritisch, funde.blocker, funde.wichtig, funde.hinweis)
}

function wegText(weg: KitWert | null, t: Texte): string {
  if (weg === null) {
    return t.NICHT_ERFASST
  }
  const wege: Partial<Record<KitWert, string>> = t.WEGE
  return wege[weg] ?? weg
}

/** Ein Wert „ausstehend“ (beide Sprachen des Kits) steht als „ausstehend“ der Oberfläche (AK 11). */
const AUSSTEHEND = new Set(['ausstehend', 'pending'])

function angabeText(angabe: Angabe, t: Texte, sprache: Parameters<typeof zeitpunkt>[1]): string {
  if (angabe.wert === null) {
    return t.NICHT_ERFASST
  }
  const wert = AUSSTEHEND.has(angabe.wert.trim().toLowerCase()) ? t.AUSSTEHEND : angabe.wert
  return angabe.zeitpunkt === null ? wert : `${wert}, ${zeitpunkt(angabe.zeitpunkt, sprache)}`
}

/** Ein Wert aus `Quelle:`: Link nur bei `https:`, sonst Text (Plan #2108 E16). */
function QuelleWert({ wert }: Readonly<{ wert: string }>) {
  const adresse = sichereAdresse(wert)
  if (adresse === null) {
    return <span>{wert}</span>
  }
  return (
    <Link href={adresse} target="_blank" rel="noopener noreferrer">
      {wert}
    </Link>
  )
}

/**
 * Ein Verweis auf eine Karte oder einen Kommentar (Plan #2108 E16): Eine sichtbare Karte öffnet über
 * den Verweis-Stapel des Dialogs, eine archivierte nennt Nummer und Titel und öffnet nicht. Ein
 * Kommentar wird mit Karte und Zeitpunkt benannt.
 */
function VerweisText({
  verweis,
  zeitpunkt: zeit,
  onOpenCard,
}: Readonly<{ verweis: Verweis; zeitpunkt: string | null; onOpenCard: Oeffnen }>) {
  const t = useT().ENTSCHEIDUNGSKETTE
  const { sprache } = useSprache()
  if (verweis.nummer === null) {
    return <span>{t.NICHT_ERFASST}</span>
  }
  const nummer = verweis.nummer
  const name = verweis.titel === null ? `#${nummer}` : `#${nummer} ${verweis.titel}`
  const karte: ReactNode =
    verweis.oeffenbar && onOpenCard !== undefined ? (
      <Link component="button" type="button" variant="body2" onClick={() => onOpenCard(nummer)} sx={{ verticalAlign: 'baseline' }}>
        {name}
      </Link>
    ) : (
      name
    )
  if (verweis.kommentar === null) {
    return <span>{karte}</span>
  }
  const wann = zeit === null ? t.NICHT_ERFASST : zeitpunkt(zeit, sprache)
  return (
    <>
      {t.KOMMENTAR_AN} {karte} {t.KOMMENTAR_VOM(wann)}
    </>
  )
}

function NachfahreZeile({ nachfahre, onOpenCard }: Readonly<{ nachfahre: Nachfahre; onOpenCard: Oeffnen }>) {
  const t = useT().ENTSCHEIDUNGSKETTE
  return (
    <Box component="li" data-testid="kettennachfahre" sx={{ mt: 0.5 }}>
      <Typography variant="body2" component="div">
        <VerweisText verweis={nachfahre.verweis} zeitpunkt={null} onOpenCard={onOpenCard} />
        {nachfahre.zustand === 'NICHT_VORGESEHEN' && (
          <Typography component="span" variant="body2" color="text.secondary">
            {' · '}
            {t.NICHT_VORGESEHEN}
          </Typography>
        )}
        {nachfahre.hinweise.map((hinweis) => (
          <Typography key={hinweis} component="span" variant="body2" sx={{ fontWeight: 600 }}>
            {' · '}
            {t.HINWEIS[hinweis]}
          </Typography>
        ))}
      </Typography>
      {nachfahre.zustand === 'ERFASST' && (
        <Box component="dl" sx={{ m: 0 }}>
          <Paar label={t.SPALTE}>{nachfahre.spalte ?? t.NICHT_ERFASST}</Paar>
          <Paar label={t.ZULETZT_ERREICHT}>
            {nachfahre.zuletztErreicht === null ? t.NICHT_ERFASST : t.GLIED[nachfahre.zuletztErreicht]}
          </Paar>
        </Box>
      )}
    </Box>
  )
}

/** Ein Paar aus Benennung und Wert — als `dt`/`dd`, damit ein Vorlesewerkzeug es als Paar liest. */
function Paar({ label, mono = false, children }: Readonly<{ label: string; mono?: boolean; children: ReactNode }>) {
  return (
    <Stack direction="row" spacing={1}>
      <Typography component="dt" variant="body2" color="text.secondary" sx={{ flexShrink: 0 }}>
        {label}:
      </Typography>
      <Typography component="dd" variant="body2" sx={{ m: 0, overflowWrap: 'anywhere', ...(mono ? { fontFamily: SCHRIFT_MONO } : {}) }}>
        {children}
      </Typography>
    </Stack>
  )
}

/** Ein Zeitpunkt: Datum mittel, Uhrzeit kurz, in der Schreibweise der Sprache. */
function zeitpunkt(iso: string, sprache: ReturnType<typeof useSprache>['sprache']): string {
  return new Intl.DateTimeFormat(formatierer(sprache).gebietsschema, { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(iso),
  )
}
