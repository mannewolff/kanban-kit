import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Typography from '@mui/material/Typography'
import { useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { cardsApi, type CardByNumber } from '../api/cards'
import { ApiError } from '../api/client'
import { type BoardDashboardKpis } from '../api/dashboard'
import { type Epic } from '../api/epics'
import { type NightRunErrorClassCounts, type NightRunItemView, type NightRunView } from '../api/nightRuns'
import { CardDetailModal } from '../components/CardDetailModal'
import {
  FilterTaste,
  Fuellschiene,
  Kachel,
  KACHEL_SX,
  KachelFuss,
  KachelWert,
  KlassenMarke,
  Led,
  melderFarbe,
  Platte,
  ZEILE_HOVER,
} from '../components/leitstand/LeitstandBausteine'
import { LeitstandVerbrauch } from '../components/leitstand/LeitstandVerbrauch'
import { useSnackbar } from '../components/SnackbarProvider'
import { epicColor } from '../lib/epicMeta'
import {
  abbruchgruende,
  balkenHoehen,
  durchlaufKachel,
  durchsatzKachel,
  ersteZeile,
  gruenAnteil,
  implementierungKachel,
  istAbbruch,
  kalenderwoche,
  kurzHash,
  laeuftNoch,
  laufband,
  laufMelder,
  laufNotiz,
  MELDER_JE_FEHLERKLASSE,
  MELDER_JE_ZUSTAND,
  modusName,
  paketDauer,
} from '../lib/leitstand'
import { useLeitstandDaten, type Laden } from '../lib/useLeitstandDaten'
import {
  ETIKETT,
  KUPFER,
  KUPFER_HELL,
  KUPFER_SCHIMMER,
  MELDER,
  NUT,
  PANEL_RADIUS,
  PLATTE,
  PLATTE_FUSS,
  PLATTE_HOCH,
  RAND,
  SCHATTEN_NUTE,
  SCHATTEN_PLATTE,
  TEXT_SCHWACH,
  ZAHL,
} from '../theme'

/**
 * Der Leitstand (#979) — die Hauptansicht eines Boards nach dem Entwurf
 * `docs/entwurf-leitstand.html` (CSS Z. 390–722, 977–1017; HTML Z. 1200–1678). Er ersetzt die
 * Kennzahlen-Ansicht: Laufband des jüngsten Laufs, vier Kennzahl-Kacheln, Verbrauch mit
 * Zeitraum-Wahl und der Rumpf aus „Letzter Lauf", „Durchsatz", „Abbruchgründe" und „Vorhaben".
 * Die Platte „Liegengeblieben" des Entwurfs entfällt (Manne, 2026-09-17, #983) — die Kennzahl
 * `outliers` bleibt im Backend, wird hier aber nicht mehr dargestellt.
 *
 * **Kein neuer Endpunkt.** Gespeist aus Board, Kennzahlen, Vorhaben, aufbewahrten Läufen,
 * Häufigkeit der Fehlerklassen und Verbrauch. Die Verweildauer je Spalte gehört nicht in den
 * Leitstand (Manne, 2026-09-16).
 *
 * **Die Läufe sieht nur, wer sie auch auf der Nachtlauf-Seite sieht** (Owner-Recht im Backend).
 * Ohne das Recht entfallen Laufband, Nachtlauf-Kachel, Verbrauch, Letzter Lauf und Abbruchgründe
 * still; die Board-Kennzahlen bleiben.
 */

/** Die Karte, die der Dialog zeigt, mit dem Projekt, aus dem sie kam. */
type Auswahl = { card: CardByNumber; projectId: number }

export function LeitstandPage() {
  const { boardId } = useParams()
  const daten = useLeitstandDaten(boardId)
  // Die Karte kommt aus „Letzter Lauf" und damit immer aus einem bekannten Projekt; das Projekt
  // reist im Zustand mit, statt beim Rendern des Dialogs noch einmal gegen `null` geprüft zu werden.
  const [detail, setDetail] = useState<Auswahl | null>(null)

  if (!daten.validId) {
    return <Alert severity="error">Ungültige Board-ID.</Alert>
  }

  const { board, kpis, epics, klassen, projectId, liste, juengster, epicListe } = daten

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <Box component="h1" sx={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}>
        {board.art === 'da' ? `Leitstand ${board.wert.name}` : 'Leitstand'}
      </Box>

      {juengster && <LaufbandBereich lauf={juengster} />}

      <KennzahlenBereich kpis={kpis} liste={liste} />

      {projectId !== null && liste !== null && <LeitstandVerbrauch projectId={projectId} />}

      {juengster && <Herkunft lauf={juengster} />}

      <LeitstandRumpf
        juengster={juengster}
        projectId={projectId}
        kpis={kpis}
        klassen={klassen}
        epics={epics}
        epicListe={epicListe}
        liste={liste}
        onDetail={setDetail}
      />

      {detail && (
        <CardDetailModal card={detail.card} canEdit={false} projectId={detail.projectId} onClose={() => setDetail(null)} />
      )}
    </Box>
  )
}

/** Die Kennzahlen-Zeile (Entwurf Z. 1240–1323): drei Board-Kacheln und die Nachtlauf-Kachel. */
function KennzahlenBereich({ kpis, liste }: Readonly<{ kpis: Laden<BoardDashboardKpis>; liste: readonly NightRunView[] | null }>) {
  return (
    <Box
      component="section"
      aria-label="Kennzahlen"
      sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))', lg: 'repeat(4, minmax(0,1fr))' }, gap: '14px', perspective: '1100px' }}
    >
      {kpis.art === 'da' ? (
        <>
          <Kachel titel="Durchsatz · Woche" daten={durchsatzKachel(kpis.wert.throughput)} melder="kupfer" />
          <Kachel titel="Durchlaufzeit" daten={durchlaufKachel(kpis.wert.avgLeadTimeSeconds, kpis.wert.leadTimeSampleCount)} melder="gruen" />
          <Kachel titel="Implementierungszeit" daten={implementierungKachel(kpis.wert.avgImplementationSeconds, kpis.wert.implementationSampleCount)} melder="stahl" />
        </>
      ) : (
        <Typography color="text.secondary">
          {kpis.art === 'laedt' ? 'Kennzahlen werden geladen …' : 'Kennzahlen konnten nicht geladen werden.'}
        </Typography>
      )}
      {liste && <NachtlaufKachel laeufe={liste} />}
    </Box>
  )
}

/** Der Rumpf (Entwurf Z. 1410–1712): „Letzter Run", „Durchsatz", „Abbruchgründe" und „Vorhaben". */
function LeitstandRumpf({
  juengster,
  projectId,
  kpis,
  klassen,
  epics,
  epicListe,
  liste,
  onDetail,
}: Readonly<{
  juengster: NightRunView | null
  projectId: number | null
  kpis: Laden<BoardDashboardKpis>
  klassen: Laden<NightRunErrorClassCounts>
  epics: Laden<Epic[]>
  epicListe: readonly Epic[]
  liste: readonly NightRunView[] | null
  onDetail: (auswahl: Auswahl) => void
}>) {
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: 'minmax(0,1.55fr) minmax(0,1fr)' }, gap: '16px', alignItems: 'start' }}>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px', minWidth: 0 }}>
        {juengster && projectId !== null && (
          <LetzterLauf
            lauf={juengster}
            epics={epicListe}
            projectId={projectId}
            onOeffnen={(card) => onDetail({ card, projectId })}
          />
        )}
        {kpis.art === 'da' && <Durchsatz wochen={kpis.wert.throughput} />}
      </Box>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px', minWidth: 0 }}>
        {klassen.art === 'da' && liste && <Abbruchgruende zaehler={klassen.wert} laeufe={liste.length} />}
        {epics.art === 'da' && <VorhabenPlatte epics={epics.wert} />}
      </Box>
    </Box>
  )
}

/** Laufband (Entwurf Z. 393–468, 1203–1230): der jüngste Lauf auf einen Blick. */
function LaufbandBereich({ lauf }: Readonly<{ lauf: NightRunView }>) {
  const band = laufband(lauf)
  return (
    <Box
      component="section"
      aria-label="Jüngster Run"
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: 'minmax(260px, 1.5fr) auto' },
        gap: { xs: '14px', lg: '22px' },
        alignItems: 'center',
        px: '18px',
        py: '14px',
        borderRadius: `${PANEL_RADIUS}px`,
        border: `1px solid ${RAND}`,
        background: `linear-gradient(180deg, ${PLATTE_HOCH}, ${PLATTE_FUSS})`,
        boxShadow: SCHATTEN_PLATTE,
        position: 'relative',
        overflow: 'hidden',
        '&::after': {
          content: '""',
          position: 'absolute',
          inset: 0,
          background: `linear-gradient(100deg, transparent 40%, ${KUPFER_SCHIMMER})`,
          pointerEvents: 'none',
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '11px', minWidth: 0 }}>
        <Led melder={band.melder} pulsiert={band.laeuft} />
        <Box sx={{ minWidth: 0 }}>
          <Box sx={{ fontSize: 14, fontWeight: 600, fontFamily: 'inherit' }} data-testid="laufband-titel">
            {band.titel}
          </Box>
          <Box sx={{ fontSize: 12, color: 'text.secondary' }}>
            {band.vorgang && (
              <>
                <Box component="span" sx={ZAHL}>{`#${band.vorgang.nummer}`}</Box> {band.vorgang.titel} ·{' '}
              </>
            )}
            {band.zeitpunkt}
          </Box>
        </Box>
      </Box>
      <Box sx={{ display: 'flex', gap: '20px', flex: 'none' }}>
        <Budget etikett="Zeit" wert={String(band.minuten)} einheit="min" />
        {band.kosten && <Budget etikett="Kosten" wert={band.kosten} einheit="$" />}
      </Box>
    </Box>
  )
}

function Budget({ etikett, wert, einheit }: Readonly<{ etikett: string; wert: string; einheit: string }>) {
  return (
    <div>
      <Box sx={ETIKETT}>{etikett}</Box>
      <Box sx={{ ...ZAHL, fontSize: 15, fontWeight: 500 }}>
        {wert}
        <Box component="small" sx={{ color: TEXT_SCHWACH, fontSize: 11 }}>{` ${einheit}`}</Box>
      </Box>
    </div>
  )
}

/** Run · grün (Entwurf Z. 1308–1323): Anteil grüner Pakete mit Aufschlüsselung. */
function NachtlaufKachel({ laeufe }: Readonly<{ laeufe: readonly NightRunView[] }>) {
  const anteil = gruenAnteil(laeufe)
  const breite = (zahl: number) => `${(zahl / Math.max(1, anteil.gesamt)) * 100}%`
  return (
    <Box component="article" aria-label="Run · grün" sx={KACHEL_SX}>
      <Box sx={ETIKETT}>Run · grün</Box>
      <KachelWert wert={anteil.prozent === null ? null : String(anteil.prozent)} einheit="%" />
      {anteil.gesamt > 0 && (
        <>
          <Box
            role="img"
            aria-label={`${anteil.gruen} grün, ${anteil.gelb} gelb, ${anteil.rot} rot`}
            sx={{ display: 'flex', height: 12, borderRadius: '6px', overflow: 'hidden', bgcolor: NUT, boxShadow: SCHATTEN_NUTE }}
          >
            <Box sx={{ width: breite(anteil.gruen), bgcolor: MELDER.gruen }} />
            <Box sx={{ width: breite(anteil.gelb), bgcolor: MELDER.bernst }} />
            <Box sx={{ width: breite(anteil.rot), bgcolor: MELDER.zinnob }} />
          </Box>
          <Box aria-hidden sx={{ ...ZAHL, display: 'flex', gap: '12px', fontSize: 9.5, color: TEXT_SCHWACH }}>
            <span>{`${anteil.gruen} grün`}</span>
            <span>{`${anteil.gelb} gelb`}</span>
            <span>{`${anteil.rot} rot`}</span>
          </Box>
        </>
      )}
      <KachelFuss basis={anteil.gesamt === 1 ? 'letztes Paket' : `letzte ${anteil.gesamt} Pakete`} />
    </Box>
  )
}

/** Herkunftszeile der Einlieferung (Entwurf Z. 1400–1406). */
function Herkunft({ lauf }: Readonly<{ lauf: NightRunView }>) {
  const zeitpunkt = new Date(lauf.updatedAt ?? lauf.createdAt).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
  const text =
    lauf.origin === 'TOKEN'
      ? `Eingeliefert von der ${modusName(lauf.mode)} um ${zeitpunkt} — automatisch, ohne Handgriff im Browser.`
      : `Im Browser hochgeladen um ${zeitpunkt}.`
  const vorgaengeText = lauf.items.length === 1 ? '1 Vorgang' : `${lauf.items.length} Vorgänge`
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap', fontSize: 12, color: 'text.secondary' }}>
      <Led melder={laufMelder(lauf) === 'stahl' ? 'stahl' : 'gruen'} />
      <span>{text}</span>
      <Box sx={{ ml: 'auto', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
        {lauf.tokenName && <Zustand>{`Token ${lauf.tokenName}`}</Zustand>}
        <Zustand>{`${vorgaengeText} · ${lauf.unparsedCount} ungedeutete Zeilen`}</Zustand>
      </Box>
    </Box>
  )
}

/** Eingelassene Zustandsmarke (Entwurf `.zustand`). */
function Zustand({ children }: Readonly<{ children: string }>) {
  return (
    <Box
      component="span"
      sx={{ fontSize: 11.5, color: 'text.secondary', bgcolor: NUT, border: `1px solid ${RAND}`, boxShadow: SCHATTEN_NUTE, borderRadius: '7px', px: '8px', py: '2px' }}
    >
      {children}
    </Box>
  )
}

/** Letzter Run (Entwurf Z. 565–640, 1410–1511): die Arbeitspakete mit Filter „Nur Abbrüche". */
function LetzterLauf({
  lauf,
  epics,
  projectId,
  onOeffnen,
}: Readonly<{ lauf: NightRunView; epics: readonly Epic[]; projectId: number; onOeffnen: (card: CardByNumber) => void }>) {
  const notify = useSnackbar()
  const [nurAbbrueche, setNurAbbrueche] = useState(false)
  const zeilen = nurAbbrueche ? lauf.items.filter(istAbbruch) : lauf.items

  const oeffnen = async (item: NightRunItemView) => {
    try {
      onOeffnen(await cardsApi.byNumber(projectId, item.cardNumber))
    } catch (err) {
      notify(
        err instanceof ApiError && err.status === 404
          ? `Karte #${item.cardNumber} nicht gefunden — gelöscht oder kein Zugriff.`
          : 'Karte konnte nicht geladen werden.',
        err instanceof ApiError && err.status === 404 ? 'warning' : 'error',
      )
    }
  }

  return (
    <Platte
      titel={`Letzter Run · ${modusName(lauf.mode)}`}
      notiz={laufNotiz(lauf)}
      led={<Led melder={laufMelder(lauf)} pulsiert={laeuftNoch(lauf)} />}
      werkzeug={
        <>
          <FilterTaste gewaehlt={!nurAbbrueche} onClick={() => setNurAbbrueche(false)}>
            Alle
          </FilterTaste>
          <FilterTaste gewaehlt={nurAbbrueche} onClick={() => setNurAbbrueche(true)}>
            Nur Abbrüche
          </FilterTaste>
        </>
      }
    >
      {zeilen.length === 0 && (
        <Typography color="text.secondary" sx={{ px: '16px', py: '11px' }}>
          {nurAbbrueche ? 'Kein Abbruch in diesem Run.' : 'Der Run hat noch kein Arbeitspaket gemeldet.'}
        </Typography>
      )}
      <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {zeilen.map((item) => (
          <Box component="li" key={item.id} sx={{ '&:not(:last-child)': { borderBottom: `1px solid color-mix(in srgb, ${RAND} 55%, transparent)` } }}>
            <Vorgang item={item} epic={epics.find((e) => e.memberNumbers.includes(item.cardNumber)) ?? null} onOeffnen={() => void oeffnen(item)} />
          </Box>
        ))}
      </Box>
    </Platte>
  )
}

function Vorgang({ item, epic, onOeffnen }: Readonly<{ item: NightRunItemView; epic: Epic | null; onOeffnen: () => void }>) {
  const auszug = ersteZeile(item.excerpt)
  const hash = kurzHash(item.commitHash)
  return (
    <ButtonBase
      onClick={onOeffnen}
      aria-label={`Karte #${item.cardNumber} öffnen: ${item.title}`}
      sx={{
        width: '100%',
        display: 'grid',
        gridTemplateColumns: '14px 64px minmax(0,1fr) auto',
        alignItems: 'center',
        gap: '12px',
        px: '16px',
        py: '11px',
        textAlign: 'left',
        transition: 'background .12s ease',
        '&:hover': { background: ZEILE_HOVER },
      }}
    >
      <Led melder={MELDER_JE_ZUSTAND[item.state]} />
      <Box component="span" sx={{ ...ZAHL, fontSize: 12, fontWeight: 500, color: TEXT_SCHWACH }}>{`#${item.cardNumber}`}</Box>
      <Box sx={{ minWidth: 0 }}>
        <Box sx={{ fontSize: 13.5, fontWeight: 500, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.title}</Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px', mt: '3px', flexWrap: 'wrap', fontSize: 11, color: 'text.secondary' }}>
          {item.errorClass && <KlassenMarke melder={MELDER_JE_FEHLERKLASSE[item.errorClass]}>{item.errorClass}</KlassenMarke>}
          {item.errorClass && auszug && <span>{auszug}</span>}
          {!item.errorClass && epic && (
            <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
              <Box component="span" sx={{ width: 7, height: 7, borderRadius: '2px', flex: 'none', bgcolor: epicColor(epic.id) }} />
              {epic.title}
            </Box>
          )}
        </Box>
      </Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 'none' }}>
        <Box component="span" sx={{ ...ZAHL, fontSize: 12, color: 'text.secondary' }}>
          {paketDauer(item.durationMs)}
        </Box>
        {hash && (
          <Box
            component="span"
            sx={{ ...ZAHL, fontSize: 11, color: TEXT_SCHWACH, bgcolor: NUT, border: `1px solid ${RAND}`, boxShadow: SCHATTEN_NUTE, borderRadius: '5px', px: '5px', py: '1px' }}
          >
            {hash}
          </Box>
        )}
      </Box>
    </ButtonBase>
  )
}

/** Durchsatz als Balkenwerk über zwölf Wochen (Entwurf Z. 642–688, 1513–1540). */
function Durchsatz({ wochen }: Readonly<{ wochen: BoardDashboardKpis['throughput'] }>) {
  const werte = wochen.map((w) => w.doneCount)
  const hoehen = balkenHoehen(werte)
  const ohneDatenbasis = werte.every((w) => w === 0)
  const spalten = `repeat(${Math.max(1, wochen.length)}, minmax(0,1fr))`
  return (
    <Platte titel="Durchsatz" notiz={`abgeschlossene Karten je Woche · ${wochen.length} Wochen`}>
      {ohneDatenbasis ? (
        <Typography color="text.secondary" sx={{ px: '16px', py: '14px' }}>
          Noch keine abgeschlossene Karte in den letzten Wochen.
        </Typography>
      ) : (
        <>
          <Box
            role="img"
            aria-label={`Durchsatz der letzten ${wochen.length} Wochen: ${werte.join(', ')} Karten, zuletzt ${werte.at(-1)}`}
            sx={{ display: 'grid', gridTemplateColumns: spalten, alignItems: 'end', gap: '6px', height: 132, px: '16px', pt: '16px' }}
          >
            {hoehen.map((hoehe, i) => {
              const jetzt = i === hoehen.length - 1
              return (
                <Box key={wochen[i].weekStart} sx={{ display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', height: '100%' }}>
                  <Box
                    data-testid={`balken-${hoehe}`}
                    title={`${werte[i]} Karten`}
                    sx={{
                      height: `${hoehe}%`,
                      borderRadius: '4px 4px 2px 2px',
                      background: jetzt
                        ? `linear-gradient(180deg, ${KUPFER_HELL}, ${KUPFER})`
                        : `linear-gradient(180deg, color-mix(in srgb, ${KUPFER} 78%, ${PLATTE}), color-mix(in srgb, ${KUPFER} 34%, ${PLATTE}))`,
                      border: hoehe === 0 ? 'none' : `1px solid color-mix(in srgb, ${KUPFER} 45%, ${RAND})`,
                      boxShadow: jetzt ? `0 1px 0 rgba(255,255,255,.35) inset, 0 0 14px -4px ${KUPFER}` : `0 1px 0 rgba(255,255,255,.22) inset, 0 3px 8px -5px ${KUPFER}`,
                    }}
                  />
                </Box>
              )
            })}
          </Box>
          <Box aria-hidden sx={{ display: 'grid', gridTemplateColumns: spalten, gap: '6px', px: '16px', pt: '7px', pb: '14px', borderTop: `1px solid ${RAND}`, mt: '10px' }}>
            {wochen.map((w) => (
              <Box key={w.weekStart} component="span" sx={{ ...ZAHL, fontSize: 9.5, color: TEXT_SCHWACH, textAlign: 'center' }}>
                {kalenderwoche(w.weekStart)}
              </Box>
            ))}
          </Box>
        </>
      )}
    </Platte>
  )
}

/** Abbruchgründe (Entwurf Z. 690–711, 1606–1653): je Fehlerklasse die Zahl der Läufe. */
function Abbruchgruende({ zaehler, laeufe }: Readonly<{ zaehler: NightRunErrorClassCounts; laeufe: number }>) {
  const zeilen = abbruchgruende(zaehler)
  return (
    <Platte titel="Abbruchgründe" notiz={laeufe === 1 ? '1 Run' : `${laeufe} Runs`}>
      {zeilen.length === 0 ? (
        <Typography color="text.secondary" sx={{ px: '16px', py: '14px' }}>
          Kein Abbruch in den aufbewahrten Runs.
        </Typography>
      ) : (
        <Box component="ul" sx={{ listStyle: 'none', m: 0, display: 'flex', flexDirection: 'column', gap: '9px', px: '16px', pt: '14px', pb: '16px' }}>
          {zeilen.map((zeile) => (
            <Box component="li" key={zeile.klasse} sx={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 34px', gap: '10px', alignItems: 'center' }}>
              <div>
                <Box sx={{ ...ZAHL, fontSize: 11, color: 'text.secondary' }}>{zeile.klasse}</Box>
                <Fuellschiene breite={zeile.breite} farbe={melderFarbe(zeile.melder)} />
              </div>
              <Box sx={{ ...ZAHL, fontSize: 12.5, textAlign: 'right' }}>{zeile.zahl}</Box>
            </Box>
          ))}
        </Box>
      )}
    </Platte>
  )
}

/** Vorhaben mit Fortschritt (Entwurf Z. 1682–1712): nur offene, in der Farbe ihres Schilds. */
function VorhabenPlatte({ epics }: Readonly<{ epics: readonly Epic[] }>) {
  const offen = useMemo(() => epics.filter((e) => e.done < e.total), [epics])
  return (
    <Platte titel="Vorhaben" notiz="offen">
      {offen.length === 0 ? (
        <Typography color="text.secondary" sx={{ px: '16px', py: '14px' }}>
          Kein offenes Vorhaben.
        </Typography>
      ) : (
        <Box component="ul" sx={{ listStyle: 'none', m: 0, display: 'flex', flexDirection: 'column', gap: '9px', px: '16px', pt: '14px', pb: '16px' }}>
          {offen.map((epic) => (
            <Box component="li" key={epic.id} sx={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) auto', gap: '10px', alignItems: 'center' }}>
              <div>
                <Box sx={{ fontSize: 12.5, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{epic.title}</Box>
                <Fuellschiene breite={Math.round((epic.done / epic.total) * 100)} farbe={epicColor(epic.id)} />
              </div>
              <Box sx={{ ...ZAHL, fontSize: 12.5, textAlign: 'right' }}>{`${epic.done}/${epic.total}`}</Box>
            </Box>
          ))}
        </Box>
      )}
    </Platte>
  )
}
