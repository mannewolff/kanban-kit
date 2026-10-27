import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { cardsApi, type CardByNumber } from '../api/cards'
import { ApiError } from '../api/client'
import { type AgingWip, type BoardDashboardKpis, type BoardFlow, type DeliveryView, type EpicForecast } from '../api/dashboard'
import { type Epic } from '../api/epics'
import { type NightRunErrorClassCounts, type NightRunItemView, type NightRunRework, type NightRunView } from '../api/nightRuns'
import { CardDetailModal } from '../components/CardDetailModal'
import { Erklaerung } from '../components/Erklaerung'
import { AlterInArbeitPlatte } from '../components/leitstand/AlterInArbeitPlatte'
import { AuslieferungPlatte } from '../components/leitstand/AuslieferungPlatte'
import { FestgefahrenAngaben } from '../components/leitstand/FestgefahrenAngaben'
import { GleichzeitigkeitMarken } from '../components/leitstand/GleichzeitigkeitMarken'
import { FlussPlatte } from '../components/leitstand/FlussPlatte'
import {
  FilterTaste,
  Fuellschiene,
  Grundlinie,
  Kachel,
  KACHEL_SX,
  KachelFuss,
  KachelWert,
  KlassenMarke,
  Led,
  melderFarbe,
  PaketDauer,
  Platte,
  WochenLeiste,
  wochenSpalten,
  ZEILE_HOVER_SX,
} from '../components/leitstand/LeitstandBausteine'
import { LeitstandVerbrauch } from '../components/leitstand/LeitstandVerbrauch'
import { NacharbeitPlatte } from '../components/leitstand/NacharbeitPlatte'
import { VorhabenPlatte } from '../components/leitstand/VorhabenPlatte'
import { useSnackbar } from '../components/SnackbarProvider'
import { VorschauVerborgenHinweis } from '../components/VorschauVerborgenHinweis'
import { formatierer } from '../i18n/format'
import { useSprache, useT } from '../i18n/SpracheContext'
import { epicColor } from '../lib/epicMeta'
import { gleichzeitigkeitsMarken } from '../lib/gleichzeitigkeit'
import {
  abbruchgruende,
  balkenHoehen,
  balkenHoehenMitLuecken,
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
  uhrzeit,
  vorgaenge,
} from '../lib/leitstand'
import { useLeitstandDaten, type Laden } from '../lib/useLeitstandDaten'
import {
  BLINKER_HELL,
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
 * Zeitraum-Wahl und der Rumpf aus „Letzter Lauf", „Durchsatz", „Implementierungszeit",
 * „Abbruchgründe", „Nacharbeit" und „Vorhaben"; darunter in voller Breite „Fluss", „Alter in
 * Arbeit" (Issue #1778) und „Auslieferung" (Issue #1836).
 * Die Platte „Liegengeblieben" des Entwurfs entfällt (Manne, 2026-09-17, #983) — die Kennzahl
 * `outliers` bleibt im Backend, wird hier aber nicht mehr dargestellt.
 *
 * **Gespeist aus** Board, Kennzahlen, Vorhaben, aufbewahrten Läufen, Häufigkeit der
 * Fehlerklassen und Verbrauch; dazu die Implementierungszeit je Zeitraum aus dem Abruf
 * `/dashboard/implementation-time` (Issue #1540, #1541); die Platte „Vorhaben“ allein aus der Prognose
 * `/dashboard/epic-forecast` (Issue #1829); die Platte „Auslieferung“ aus `/dashboard/delivery` mit den
 * Daten des ganzen Projekts (Issue #1836). Die Verweildauer je Spalte gehört nicht
 * in den Leitstand (Manne, 2026-09-16).
 *
 * **Die Läufe sieht nur, wer sie auch auf der Nachtlauf-Seite sieht** (Owner-Recht im Backend).
 * Ohne das Recht entfallen Laufband, Nachtlauf-Kachel, Verbrauch, Letzter Lauf, Abbruchgründe und
 * Nacharbeit still; die Board-Kennzahlen, beide Wochenplatten, „Fluss", „Alter in Arbeit" und „Auslieferung" bleiben.
 * In der „Ansicht als XY“ steht dort, wo XY die Läufe sähe, der Admin selbst aber nicht
 * (`403 vorschau-verborgen`), ein Hinweis: einer statt Laufband, Kachel, Verbrauch und Letztem Lauf,
 * je einer in „Abbruchgründe“ und „Nacharbeit“ (Plan #1914, E16).
 */

/** Die Karte, die der Dialog zeigt, mit dem Projekt, aus dem sie kam. */
type Auswahl = { card: CardByNumber; projectId: number }

export function LeitstandPage() {
  const { boardId } = useParams()
  const t = useT().LEITSTAND
  const daten = useLeitstandDaten(boardId)
  // Die Karte kommt aus „Letzter Lauf", „Nacharbeit" oder „Alter in Arbeit" und damit immer aus einem
  // bekannten Projekt; das Projekt reist im Zustand mit, statt beim Rendern des Dialogs noch einmal gegen `null` geprüft zu werden.
  const [detail, setDetail] = useState<Auswahl | null>(null)

  if (!daten.validId) {
    return <Alert severity="error">{t.UNGUELTIGE_ID}</Alert>
  }

  const { board, kpis, fluss, alter, prognose, auslieferung, klassen, nacharbeit, projectId, liste, juengster, laeufeVerborgen, epicListe } = daten

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <Box component="h1" sx={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}>
        {board.art === 'da' ? t.TITEL_BOARD(board.wert.name) : t.TITEL}
      </Box>

      {laeufeVerborgen && <VorschauVerborgenHinweis />}

      {juengster && <LaufbandBereich lauf={juengster} />}

      <KennzahlenBereich kpis={kpis} liste={liste} />

      {board.art === 'da' && liste !== null && (
        <LeitstandVerbrauch projectId={board.wert.projectId} boardId={board.wert.id} />
      )}

      {juengster && <Herkunft lauf={juengster} />}

      <LeitstandRumpf
        juengster={juengster}
        projectId={projectId}
        kpis={kpis}
        klassen={klassen}
        nacharbeit={nacharbeit}
        prognose={prognose}
        epicListe={epicListe}
        liste={liste}
        onDetail={setDetail}
      />

      <FlussBereich fluss={fluss} alter={alter} auslieferung={auslieferung} projectId={projectId} onDetail={setDetail} />

      {detail && (
        <CardDetailModal card={detail.card} canEdit={false} projectId={detail.projectId} onClose={() => setDetail(null)} />
      )}
    </Box>
  )
}

/** Die Kennzahlen-Zeile (Entwurf Z. 1240–1323): drei Board-Kacheln und die Nachtlauf-Kachel. */
function KennzahlenBereich({ kpis, liste }: Readonly<{ kpis: Laden<BoardDashboardKpis>; liste: readonly NightRunView[] | null }>) {
  const t = useT().LEITSTAND
  const { sprache } = useSprache()
  return (
    <Box
      component="section"
      aria-label={t.KENNZAHLEN}
      sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))', lg: 'repeat(4, minmax(0,1fr))' }, gap: '14px', perspective: '1100px' }}
    >
      {kpis.art === 'da' ? (
        <>
          <Kachel titel={t.DURCHSATZ_WOCHE} daten={durchsatzKachel(kpis.wert.throughput, sprache)} melder="kupfer" />
          <Kachel titel={t.DURCHLAUFZEIT} daten={durchlaufKachel(kpis.wert.avgLeadTimeSeconds, kpis.wert.leadTimeSampleCount, sprache)} melder="gruen" />
          <Kachel titel={t.IMPLEMENTIERUNGSZEIT} daten={implementierungKachel(kpis.wert.avgImplementationSeconds, kpis.wert.implementationSampleCount, sprache)} melder="stahl" />
        </>
      ) : (
        <Typography color="text.secondary">
          {kpis.art === 'laedt' ? t.KENNZAHLEN_LAEDT : t.KENNZAHLEN_FEHLER}
        </Typography>
      )}
      {liste && <NachtlaufKachel laeufe={liste} />}
    </Box>
  )
}

/**
 * Der Rumpf (Entwurf Z. 1410–1712): „Letzter Run", „Durchsatz" mit „Implementierungszeit" daneben
 * (Issue #1542), „Abbruchgründe", „Nacharbeit" (Issue #1760) und „Vorhaben".
 *
 * Beide Platten mit Kartennummern öffnen die Karte über denselben Weg ({@link useKarteOeffnen}).
 */
function LeitstandRumpf({
  juengster,
  projectId,
  kpis,
  klassen,
  nacharbeit,
  prognose,
  epicListe,
  liste,
  onDetail,
}: Readonly<{
  juengster: NightRunView | null
  projectId: number | null
  kpis: Laden<BoardDashboardKpis>
  klassen: Laden<NightRunErrorClassCounts>
  nacharbeit: Laden<NightRunRework>
  prognose: Laden<EpicForecast>
  epicListe: readonly Epic[]
  liste: readonly NightRunView[] | null
  onDetail: (auswahl: Auswahl) => void
}>) {
  const oeffnen = useKarteOeffnen(onDetail)
  return (
    <Box
      data-testid="leitstand-rumpf"
      sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', lg: 'minmax(0,1.55fr) minmax(0,1fr)' }, gap: '16px', alignItems: 'start' }}
    >
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px', minWidth: 0 }}>
        {juengster && projectId !== null && (
          <LetzterLauf
            lauf={juengster}
            epics={epicListe}
            onOeffnen={(nummer) => void oeffnen(projectId, nummer)}
          />
        )}
        {kpis.art === 'da' && (
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', md: 'repeat(2, minmax(0,1fr))' }, gap: '16px', alignItems: 'start' }}>
            <Durchsatz wochen={kpis.wert.throughput} />
            <ImplementierungVerlauf wochen={kpis.wert.implementationWeekly} />
          </Box>
        )}
      </Box>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px', minWidth: 0 }}>
        {klassen.art === 'da' && liste && <Abbruchgruende zaehler={klassen.wert} laeufe={liste.length} />}
        {klassen.art === 'vorschauVerborgen' && <AbbruchgruendeVerborgen />}
        {/* Nur mit Run-Recht (AK 7): Die Läufe sind da, wo das Recht besteht; in der Vorschau ohne
            Recht des Admins steht die Platte mit dem Hinweis (E16). */}
        {projectId !== null && (liste !== null || nacharbeit.art === 'vorschauVerborgen') && (
          <NacharbeitPlatte nacharbeit={nacharbeit} onOeffnen={(nummer) => void oeffnen(projectId, nummer)} />
        )}
        {/* Erst mit Daten der Prognose (E14): Ein Rückfall auf die Vorhabenliste zeigte die alte Zählung. */}
        {prognose.art === 'da' && <VorhabenPlatte prognose={prognose.wert} />}
      </Box>
    </Box>
  )
}

/**
 * Der Abschnitt unter dem Rumpf (Issue #1778, Plan #1765 E15): „Fluss“, darunter „Alter in Arbeit“ und
 * „Auslieferung“ (Issue #1836, Plan #1831 E15), alle in voller Seitenbreite und wie „Durchsatz“ für
 * alle, die das Board sehen dürfen. Fällt ein Abruf aus, fehlt nur seine Platte. Die Kartennummer öffnet die Karte wie in „Letzter Run“ (E18).
 */
function FlussBereich({
  fluss,
  alter,
  auslieferung,
  projectId,
  onDetail,
}: Readonly<{
  fluss: Laden<BoardFlow>
  alter: Laden<AgingWip>
  auslieferung: Laden<DeliveryView>
  projectId: number | null
  onDetail: (auswahl: Auswahl) => void
}>) {
  const oeffnen = useKarteOeffnen(onDetail)
  return (
    <Box data-testid="leitstand-fluss" sx={{ display: 'flex', flexDirection: 'column', gap: '16px', minWidth: 0 }}>
      {fluss.art === 'da' && <FlussPlatte daten={fluss.wert} />}
      {alter.art === 'da' && projectId !== null && (
        <AlterInArbeitPlatte daten={alter.wert} onOeffnen={(nummer) => void oeffnen(projectId, nummer)} />
      )}
      {auslieferung.art === 'da' && <AuslieferungPlatte daten={auslieferung.wert} />}
    </Box>
  )
}

/**
 * Öffnet eine Karte des Projekts über ihre Nummer zum Lesen (E14): erst `cardsApi.byNumber`, dann
 * der Dialog; eine nicht gefundene Karte meldet sich als Warnung, jeder andere Fehler als Fehler.
 */
function useKarteOeffnen(onDetail: (auswahl: Auswahl) => void) {
  const t = useT().LEITSTAND
  const notify = useSnackbar()
  return async (projectId: number, nummer: number) => {
    try {
      onDetail({ card: await cardsApi.byNumber(projectId, nummer), projectId })
    } catch (err) {
      notify(
        err instanceof ApiError && err.status === 404 ? t.KARTE_FEHLT(nummer) : t.KARTE_FEHLER,
        err instanceof ApiError && err.status === 404 ? 'warning' : 'error',
      )
    }
  }
}

/** Laufband (Entwurf Z. 393–468, 1203–1230): der jüngste Lauf auf einen Blick. */
function LaufbandBereich({ lauf }: Readonly<{ lauf: NightRunView }>) {
  const t = useT().LEITSTAND
  const { sprache } = useSprache()
  const band = laufband(lauf, sprache)
  return (
    <Box
      component="section"
      aria-label={t.JUENGSTER_RUN}
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
        <Budget etikett={t.ZEIT} wert={String(band.minuten)} einheit="min" />
        {band.kosten && <Budget etikett={t.KOSTEN} wert={band.kosten} einheit="$" />}
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
  const t = useT().LEITSTAND
  const anteil = gruenAnteil(laeufe)
  const breite = (zahl: number) => `${(zahl / Math.max(1, anteil.gesamt)) * 100}%`
  return (
    <Box component="article" aria-label={t.RUN_GRUEN} sx={KACHEL_SX}>
      <Box sx={ETIKETT}>{t.RUN_GRUEN}</Box>
      <KachelWert wert={anteil.prozent === null ? null : String(anteil.prozent)} einheit="%" />
      {anteil.gesamt > 0 && (
        <>
          <Box
            role="img"
            aria-label={t.ANTEIL_ARIA(anteil.gruen, anteil.gelb, anteil.rot)}
            sx={{ display: 'flex', height: 12, borderRadius: '6px', overflow: 'hidden', bgcolor: NUT, boxShadow: SCHATTEN_NUTE }}
          >
            <Box sx={{ width: breite(anteil.gruen), bgcolor: MELDER.gruen }} />
            <Box sx={{ width: breite(anteil.gelb), bgcolor: MELDER.bernst }} />
            <Box sx={{ width: breite(anteil.rot), bgcolor: MELDER.zinnob }} />
          </Box>
          <Box aria-hidden sx={{ ...ZAHL, display: 'flex', gap: '12px', fontSize: 9.5, color: TEXT_SCHWACH }}>
            <span>{t.GRUEN(anteil.gruen)}</span>
            <span>{t.GELB(anteil.gelb)}</span>
            <span>{t.ROT(anteil.rot)}</span>
          </Box>
        </>
      )}
      <KachelFuss basis={t.LETZTE_PAKETE(anteil.gesamt)} />
    </Box>
  )
}

/** Herkunftszeile der Einlieferung (Entwurf Z. 1400–1406). */
function Herkunft({ lauf }: Readonly<{ lauf: NightRunView }>) {
  const t = useT().LEITSTAND
  const { sprache } = useSprache()
  const zeitpunkt = uhrzeit(lauf.updatedAt ?? lauf.createdAt, sprache)
  const text =
    lauf.origin === 'TOKEN' ? t.EINGELIEFERT(modusName(lauf.mode, sprache), zeitpunkt) : t.HOCHGELADEN(zeitpunkt)
  const vorgaengeText = vorgaenge(lauf.items.length, sprache)
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap', fontSize: 12, color: 'text.secondary' }}>
      <Led melder={laufMelder(lauf) === 'stahl' ? 'stahl' : 'gruen'} />
      <span>{text}</span>
      <Box sx={{ ml: 'auto', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
        {lauf.tokenName && <Zustand>{t.TOKEN(lauf.tokenName)}</Zustand>}
        <Zustand>{t.UNGEDEUTET(vorgaengeText, lauf.unparsedCount)}</Zustand>
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
  onOeffnen,
}: Readonly<{ lauf: NightRunView; epics: readonly Epic[]; onOeffnen: (nummer: number) => void }>) {
  const t = useT().LEITSTAND
  const { sprache } = useSprache()
  const [nurAbbrueche, setNurAbbrueche] = useState(false)
  const zeilen = nurAbbrueche ? lauf.items.filter(istAbbruch) : lauf.items

  return (
    <Platte
      titel={t.LETZTER_RUN(modusName(lauf.mode, sprache))}
      notiz={laufNotiz(lauf, sprache)}
      led={<Led melder={laufMelder(lauf)} pulsiert={laeuftNoch(lauf)} />}
      werkzeug={
        <>
          <FilterTaste gewaehlt={!nurAbbrueche} onClick={() => setNurAbbrueche(false)}>
            {t.ALLE}
          </FilterTaste>
          <FilterTaste gewaehlt={nurAbbrueche} onClick={() => setNurAbbrueche(true)}>
            {t.NUR_ABBRUECHE}
          </FilterTaste>
        </>
      }
    >
      {zeilen.length === 0 && (
        <Typography color="text.secondary" sx={{ px: '16px', py: '11px' }}>
          {nurAbbrueche ? t.KEIN_ABBRUCH_RUN : t.KEIN_PAKET}
        </Typography>
      )}
      <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {zeilen.map((item) => (
          <Box component="li" key={item.id} sx={{ '&:not(:last-child)': { borderBottom: `1px solid color-mix(in srgb, ${RAND} 55%, transparent)` } }}>
            <Vorgang item={item} epic={epics.find((e) => e.memberNumbers.includes(item.cardNumber)) ?? null} onOeffnen={() => onOeffnen(item.cardNumber)} />
            {/* Unter der Zeile und nicht in ihr (Issue #1552, E18): Die Zeile ist ein Knopf, und eine
                Überschrift mit Beschreibungsliste gehört nicht in einen Knopf. Eingerückt bis zur
                Titelspalte: 16 Rand + 14 LED + 12 + 64 Nummer + 12. */}
            {item.errorClass === 'STUCK' && (
              <Box sx={{ pl: '118px', pr: '16px', pb: '11px' }}>
                <FestgefahrenAngaben stuck={item.stuck} durationMs={item.durationMs} ebene="h3" />
              </Box>
            )}
          </Box>
        ))}
      </Box>
    </Platte>
  )
}

function Vorgang({ item, epic, onOeffnen }: Readonly<{ item: NightRunItemView; epic: Epic | null; onOeffnen: () => void }>) {
  const woerter = useT()
  const t = woerter.LEITSTAND
  const auszug = ersteZeile(item.excerpt)
  const hash = kurzHash(item.commitHash)
  // Nur die Marken, keine Wartezeit (Issue #1880, Plan #1876 E11): Die Zahl steht eine Ebene tiefer
  // in der Lauf-Ansicht. Die Marken stehen auch in der Beschriftung, weil sie den Inhalt des Knopfs ersetzt (K4).
  const marken = gleichzeitigkeitsMarken(item.concurrency, woerter)
  return (
    <ButtonBase
      onClick={onOeffnen}
      aria-label={t.KARTE_OEFFNEN_MIT_MARKEN_ARIA(item.cardNumber, item.title, marken)}
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
        '&:hover': { background: ZEILE_HOVER_SX },
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
          <GleichzeitigkeitMarken marken={marken} />
        </Box>
      </Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 'none' }}>
        <PaketDauer ms={item.durationMs} testId={`dauer-${item.cardNumber}`} />
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
  const t = useT().LEITSTAND
  const werte = wochen.map((w) => w.doneCount)
  return (
    <Platte titel={t.DURCHSATZ} notiz={t.DURCHSATZ_NOTIZ(wochen.length)}>
      <Balkenwerk
        wochen={wochen}
        hoehen={balkenHoehen(werte)}
        titel={(i) => t.KARTEN(werte[i])}
        wert={(i) => String(werte[i])}
        ariaLabel={t.DURCHSATZ_ARIA(wochen.length, werte.join(', '), werte.at(-1))}
        leer={werte.every((w) => w === 0)}
        leersatz={t.DURCHSATZ_LEER}
        farbe={KUPFER}
        farbeHell={KUPFER_HELL}
      />
    </Platte>
  )
}

/** Stahl für die Implementierungszeit (Issue #1542, E10): wie Kachel und Funke der ersten Reihe. */
const STAHL_HELL = `color-mix(in srgb, ${MELDER.stahl} 55%, ${BLINKER_HELL})`

/**
 * Implementierungszeit je Woche (Issue #1542): dieselben zwölf Fenster wie der Durchsatz, der
 * Mittelwert in der Einheitenregel der Kachel. Eine Woche ohne gemessene Karte ist eine Lücke, nie
 * ein Balken von 0 Minuten (E6).
 */
function ImplementierungVerlauf({ wochen }: Readonly<{ wochen: BoardDashboardKpis['implementationWeekly'] }>) {
  const t = useT().LEITSTAND
  const { sprache } = useSprache()
  const kacheln = wochen.map((w) => implementierungKachel(w.avgImplementationSeconds, w.sampleCount, sprache))
  const werte = kacheln.map((k) => (k.wert === null ? null : `${k.wert} ${k.einheit}`))
  const vorgelesen = werte.map((wert) => wert ?? t.KEINE_MESSUNG)
  return (
    <Platte titel={t.IMPLEMENTIERUNG_VERLAUF_TITEL} notiz={t.IMPLEMENTIERUNG_NOTIZ(wochen.length)}>
      <Balkenwerk
        wochen={wochen}
        hoehen={balkenHoehenMitLuecken(kacheln.map((k, i) => (k.wert === null ? null : wochen[i].avgImplementationSeconds)))}
        titel={(i) => `${werte[i]} · ${kacheln[i].basis}`}
        // Über dem Balken nur die ganze Minutenzahl, die Einheit steht im Kopf (Issue #1741); eine Lücke fragt nie.
        wert={(i) => formatierer(sprache).zahl(Math.round(Number(wochen[i].avgImplementationSeconds) / 60), { maximumFractionDigits: 0 })}
        ariaLabel={t.IMPLEMENTIERUNG_ARIA(wochen.length, vorgelesen.join(', '), vorgelesen.at(-1))}
        leer={werte.every((wert) => wert === null)}
        leersatz={t.IMPLEMENTIERUNG_LEER}
        farbe={MELDER.stahl}
        farbeHell={STAHL_HELL}
      />
    </Platte>
  )
}

/** Zeilenhöhe der Zahl über dem Balken; um sie lässt der höchste Balken oben Platz (Issue #1675). */
const WERT_HOEHE = 14

/**
 * Das Balkenwerk der Wochenplatten (Issue #1542, A7): ein Balken je Woche, die Kalenderwoche
 * darunter. Eine Höhe `null` ist eine Woche ohne Messung — kein Balken, sondern ein leerer Platz mit
 * gestrichelter Grundlinie. Über jedem Balken steht sein Wert sichtbar (Issue #1675); vorgelesen wird
 * er über das `aria-label` des Ganzen, darum ist die Zahl selbst `aria-hidden`.
 */
function Balkenwerk({
  wochen,
  hoehen,
  titel,
  wert,
  ariaLabel,
  leer,
  leersatz,
  farbe,
  farbeHell,
}: Readonly<{
  wochen: readonly { weekStart: string }[]
  hoehen: readonly (number | null)[]
  titel: (i: number) => string
  wert: (i: number) => string
  ariaLabel: string
  leer: boolean
  leersatz: string
  farbe: string
  farbeHell: string
}>) {
  const t = useT().LEITSTAND
  if (leer) {
    return (
      <Typography color="text.secondary" sx={{ px: '16px', py: '14px' }}>
        {leersatz}
      </Typography>
    )
  }
  return (
    <>
      <Box
        role="img"
        aria-label={ariaLabel}
        sx={{ display: 'grid', gridTemplateColumns: wochenSpalten(wochen.length), alignItems: 'end', gap: '6px', height: 132, px: '16px', pt: '16px' }}
      >
        {hoehen.map((hoehe, i) => {
          const jetzt = i === hoehen.length - 1
          // Auf dem Tablet quer öffnet Antippen von Lücke und Balken die Erklärung (Plan #1707, E7).
          if (hoehe === null) {
            return (
              <Erklaerung key={wochen[i].weekStart} nativ title={t.LUECKE(kalenderwoche(wochen[i].weekStart))}>
                <Box
                  data-testid={`luecke-${i}`}
                  sx={{ display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', height: '100%' }}
                >
                  <Grundlinie />
                </Box>
              </Erklaerung>
            )
          }
          return (
            <Box key={wochen[i].weekStart} sx={{ display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', height: '100%' }}>
              <Box
                component="span"
                aria-hidden
                data-testid={`wert-${i}`}
                sx={{ ...ZAHL, fontSize: 9.5, lineHeight: `${WERT_HOEHE}px`, color: TEXT_SCHWACH, textAlign: 'center', whiteSpace: 'nowrap' }}
              >
                {wert(i)}
              </Box>
              <Erklaerung nativ title={titel(i)}>
                <Box
                  data-testid={`balken-${hoehe}`}
                  sx={{
                    height: `calc((100% - ${WERT_HOEHE}px) * ${hoehe / 100})`,
                    borderRadius: '4px 4px 2px 2px',
                    background: jetzt
                      ? `linear-gradient(180deg, ${farbeHell}, ${farbe})`
                      : `linear-gradient(180deg, color-mix(in srgb, ${farbe} 78%, ${PLATTE}), color-mix(in srgb, ${farbe} 34%, ${PLATTE}))`,
                    border: hoehe === 0 ? 'none' : `1px solid color-mix(in srgb, ${farbe} 45%, ${RAND})`,
                    boxShadow: jetzt ? `0 1px 0 rgba(255,255,255,.35) inset, 0 0 14px -4px ${farbe}` : `0 1px 0 rgba(255,255,255,.22) inset, 0 3px 8px -5px ${farbe}`,
                  }}
                />
              </Erklaerung>
            </Box>
          )
        })}
      </Box>
      <WochenLeiste wochen={wochen} />
    </>
  )
}

/** „Abbruchgründe“ in der „Ansicht als XY“ ohne Lauf-Recht des Admins: der Hinweis statt der Zähler (E16). */
function AbbruchgruendeVerborgen() {
  const t = useT().LEITSTAND
  return (
    <Platte titel={t.ABBRUCHGRUENDE}>
      <Box sx={{ px: '16px', py: '14px' }}>
        <VorschauVerborgenHinweis />
      </Box>
    </Platte>
  )
}

/** Abbruchgründe (Entwurf Z. 690–711, 1606–1653): je Fehlerklasse die Zahl der Läufe. */
function Abbruchgruende({ zaehler, laeufe }: Readonly<{ zaehler: NightRunErrorClassCounts; laeufe: number }>) {
  const t = useT().LEITSTAND
  const zeilen = abbruchgruende(zaehler)
  return (
    <Platte titel={t.ABBRUCHGRUENDE} notiz={t.RUNS(laeufe)}>
      {zeilen.length === 0 ? (
        <Typography color="text.secondary" sx={{ px: '16px', py: '14px' }}>
          {t.KEIN_ABBRUCH}
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

