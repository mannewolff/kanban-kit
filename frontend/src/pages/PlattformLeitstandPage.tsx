import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useId, useState, type ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { ApiError } from '../api/client'
import {
  plattformLeitstandApi,
  type DisruptionView,
  type LeitstandView,
} from '../api/plattformLeitstand'
import { KupferwarteBereich } from '../components/nachtlauf/KupferwarteBereich'
import { LaufMarke } from '../components/nachtlauf/NachtlaufLaufPlatte'
import { Led, Platte, Taste } from '../components/leitstand/LeitstandBausteine'
import { MELDER_JE_ZUSTAND, melderAusBefund, modusName, tagZeit, uhrzeit } from '../lib/leitstand'
import { NIGHT_RUN_VERDICT_TEXT, nightRunZustandsText } from '../lib/nightRunHandoff'
import { useRefetchOnFocus } from '../lib/useRefetchOnFocus'
import { ANZEIGE, RAND, TEXT_SCHWACH } from '../theme'

/** Der Anfangszustand: drei leere Listen, noch von keiner Antwort belegt. */
const LEERE_SICHT: LeitstandView = { laufende: [], durchgefuehrte: [], stoerungen: [] }

/** Der Takt des Auffrischens (Kriterium 19): Was sich aendert, steht spaetestens so bald da. */
const AUFFRISCH_MS = 30_000

/**
 * Ein gescheiterter Abruf — sein Text und ob es das fehlende Recht war.
 *
 * **Ob er gezeigt wird, entscheidet der Rumpf** und nicht der Fehlerzweig (Issue #1099). Beim
 * Erstladen ist ein Fehlertext richtig; beim Auffrischen wischte ein einzelner Netzaussetzer um
 * 03:00 den ganzen Leitstand weg — genau das Bild, das die Kriterien 4 und 14 vermeiden wollen. Ein
 * **403** bleibt in beiden Faellen sichtbar: Eine Rolle bildet sich nicht von selbst zurueck, und
 * wem sie entzogen wurde, der soll keine veralteten Daten weitersehen.
 *
 * Deshalb steht hier `verboten` und kein fertiges „zeigen": Der Fehlerzweig weiss, *was* geschehen
 * ist; ob schon Daten dastehen, weiss nur der Rumpf.
 */
interface Fehler {
  text: string
  verboten: boolean
}

/**
 * Der Plattform-Leitstand: die Startseite eines Plattform-Admins (Issue #1083, fachliche Quellen
 * #1064 und #1086).
 *
 * **Drei Bereiche in dieser Ordnung** (Kriterium 18, Issue #1098; benannt in #1102): *Aktive
 * Laeufe* zeigen mit pulsierendem Melder, dass gerade etwas arbeitet; *Beendete Laeufe* zeigen den
 * Ausgang jedes beendeten Laufs der laufenden Nacht; *Stoerungen* zeigt jede nicht quittierte
 * Stoerung ueber alle Naechte, juengste zuoberst. Wer mehrere Projekte betreibt, beantwortet damit
 * „laeuft gerade etwas, und ist die Nacht gut durch?" an einer Stelle statt Projekt fuer Projekt.
 *
 * **Eine Antwort fuer alle drei Bereiche** (Plan #1088 E5): Die Seite frischt sich auf, und ein
 * Lauf kann zwischen zwei Rundreisen den Bereich wechseln — aus drei Abrufen erschiene er doppelt
 * oder gar nicht. Aus derselben Antwort liest die Seite auch, zu welchem durchgefuehrten Lauf es
 * eine Stoerung gibt.
 *
 * **Nach Projekt gruppiert** ist allein der Bereich *Stoerungen* (Issue #1087): Eine flache Liste
 * liess den Leser abwechselnd Namen statt Befunde lesen. Die beiden Lauf-Bereiche gruppieren
 * **nicht** — ein Projekt darf in einer Nacht mehrmals anlaufen, und jeder Anlauf soll als eigene
 * Zeile sichtbar bleiben (Kriterium 9).
 *
 * **Gestaltung:** `docs/entwurf-leitstand.html` ist verbindlich (`CLAUDE-design.md`), fuehrt fuer
 * diese Ansicht aber kein eigenes Mockup. Sie entsteht deshalb aus den vorhandenen Bausteinen —
 * {@link KupferwarteBereich}, {@link Platte}, {@link Led}, {@link Taste} — und nicht aus einer
 * neuen Gestaltung (Plan #1072 E15).
 */
export default function PlattformLeitstandPage() {
  // Zwei Zustaende statt einer Sicht mit `null`: Solange nicht geladen ist, zeigt die Seite keinen
  // der drei Leersaetze — sonst blitzte „Keine offene Stoerung." auf, bevor die erste Antwort da
  // ist. Getrennt gehalten, damit das Quittieren keinen Rueckfall auf eine leere Sicht braucht,
  // den nichts erreichen kann.
  const [sicht, setSicht] = useState<LeitstandView>(LEERE_SICHT)
  const [geladen, setGeladen] = useState(false)
  const [fehler, setFehler] = useState<Fehler | null>(null)

  const laden = useCallback(() => {
    plattformLeitstandApi
      .leitstand()
      .then((antwort) => {
        setSicht(antwort)
        setGeladen(true)
        setFehler(null)
      })
      .catch((e) => {
        const verboten = e instanceof ApiError && e.status === 403
        setFehler({ text: verboten ? 'Kein Admin-Zugriff.' : 'Laden fehlgeschlagen.', verboten })
      })
  }, [])

  useEffect(() => {
    laden()
  }, [laden])

  // Kriterium 19 (#1099): Was sich aendert, erscheint spaetestens nach 30 Sekunden, ohne dass
  // jemand die Seite neu laedt — auch bei den beiden Uebergaengen, bei denen von aussen **nichts**
  // geschieht: dem Ablauf der Stillefrist und dem Nachtwechsel um 12:00. Beide leistet schon der
  // Abruf, **weil die Stillefrist eine Leseregel ist** (#1091): Der Server rechnet den Ausgang bei
  // jedem Abruf neu, also wandert eine Zeile von selbst, und um 12:00 leert sich der untere Bereich.
  //
  // Ein verborgenes Fenster ruft nicht ab — dort sieht ohnehin niemand hin. Nachgeholt wird beim
  // Wiedersichtbarwerden, und zwar vom vorhandenen Zuhoerer weiter unten.
  useEffect(() => {
    const takt = setInterval(() => {
      if (document.visibilityState !== 'hidden') {
        laden()
      }
    }, AUFFRISCH_MS)
    return () => clearInterval(takt)
  }, [laden])

  // Der sofortige Abruf beim Wiedersichtbarwerden kommt aus dem vorhandenen Haken (Plan #1088 E7);
  // ein eigener `visibilitychange`-Zuhoerer waere die zweite Fassung derselben Regel.
  useRefetchOnFocus(laden)

  // AK 8: kein Rueckfragen-Dialog und kein Rueckgaengig. Die Zeile verschwindet sofort; ein
  // Nachladen der ganzen Antwort waere ein zweiter Weg zur selben Aussage.
  //
  // Kriterium 13: Nur die Stoerliste wird angefasst. Der durchgefuehrte Lauf behaelt seinen
  // Ausgang — das Quittieren sagt etwas ueber die Sichtung, nichts ueber den Lauf.
  const quittieren = async (stoerung: DisruptionView) => {
    await plattformLeitstandApi.quittieren(stoerung.nightRunId)
    setSicht((vorher) => ({
      ...vorher,
      stoerungen: vorher.stoerungen.filter((s) => s.nightRunId !== stoerung.nightRunId),
    }))
  }

  // Gezeigt wird ein Fehler nur, solange noch nie Daten dastanden — oder wenn das Recht fehlt
  // (#1099, siehe {@link Fehler}). Ein stillschweigend gescheiterter Folgeabruf laesst den letzten
  // Stand stehen; bewusst ohne Zeichen: Die fachliche Quelle verlangt keines, und eins, das bei
  // jedem kurzen Aussetzer aufblitzt, erzieht zum Wegsehen.
  if (fehler !== null && (fehler.verboten || !geladen)) {
    return <Typography color="error">{fehler.text}</Typography>
  }

  // Ob es zu einem durchgefuehrten Lauf eine Stoerung gibt, entscheidet der Browser aus **einer**
  // Antwort (Kriterium 12): Ein eigenes Serverfeld waere eine zweite Quelle fuer dieselbe Aussage
  // und liefe beim Quittieren sofort gegen die Stoerliste.
  const mitStoerung = new Set(sicht.stoerungen.map((s) => s.nightRunId))

  return (
    <KupferwarteBereich>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <Platte titel="Aktive Läufe">
          <LaufendeListe zeilen={geladen ? sicht.laufende : null} />
        </Platte>
        <Platte titel="Beendete Läufe">
          <DurchgefuehrteListe
            zeilen={geladen ? sicht.durchgefuehrte : null}
            mitStoerung={mitStoerung}
          />
        </Platte>
        <Platte titel="Störungen">
          <Stoerungen liste={geladen ? sicht.stoerungen : null} onQuittieren={quittieren} />
        </Platte>
      </Box>
    </KupferwarteBereich>
  )
}

/**
 * Der Leerfall eines Lauf-Bereichs (Kriterien 4 und 14): ein ausdruecklicher Satz.
 *
 * Eine leere Flaeche waere von einer kaputten Anzeige nicht zu unterscheiden — „nichts laeuft" und
 * „die Seite hat nichts bekommen" saehen gleich aus.
 */
function LeerSatz({ testId, children }: Readonly<{ testId: string; children: ReactNode }>) {
  return (
    <Typography
      data-testid={testId}
      sx={{ fontSize: 13, color: 'text.secondary', px: '16px', py: '14px' }}
    >
      {children}
    </Typography>
  )
}

/** Eine Zeile der beiden Lauf-Bereiche; Trennlinie wie in der Laufplatte des Board-Leitstands. */
const LAUF_ZEILE_SX = {
  display: 'flex',
  alignItems: 'center',
  gap: '10px',
  px: '16px',
  py: '8px',
  '&:not(:last-child)': {
    borderBottom: `1px solid color-mix(in srgb, ${RAND} 55%, transparent)`,
  },
} as const

/** Der Projektname einer Lauf-Zeile — hier steht er in **jeder** Zeile (Kriterien 1 und 9). */
function Projektname({ name }: Readonly<{ name: string }>) {
  return (
    <Typography sx={{ ...ANZEIGE, fontSize: 12.5, fontWeight: 600, minWidth: 0 }}>{name}</Typography>
  )
}

/**
 * Der Weg zur Auswertung genau dieses Laufs (Kriterien 1 und 12) — auch ohne Mitgliedschaft im
 * Projekt.
 *
 * Das `aria-label` nennt zusaetzlich das Projekt: Steht derselbe Lauf unter den durchgefuehrten
 * **und** unter den Stoerungen, gaebe es sonst zweimal denselben Verweisnamen „Lauf #5".
 */
function LaufVerweis({ zeile }: Readonly<{ zeile: DisruptionView }>) {
  return (
    <Typography
      component={RouterLink}
      to={`/projects/${zeile.projectId}/nachtlauf?lauf=${zeile.nightRunId}`}
      aria-label={`Lauf #${zeile.nightRunId} von ${zeile.projectName}`}
      sx={{ fontSize: 12, fontFamily: 'monospace' }}
    >
      Lauf #{zeile.nightRunId}
    </Typography>
  )
}

/**
 * Die Art eines Laufs als Marke (Issue #1128) — dieselbe Markenform wie im Kopf eines Laufs auf der
 * Läufe-Seite, ohne eigene Farbe: Farbe trägt hier den Zustand.
 */
function ArtMarke({ zeile, bereich }: Readonly<{ zeile: DisruptionView; bereich: string }>) {
  return <LaufMarke testId={`art-${bereich}-${zeile.nightRunId}`}>{modusName(zeile.mode)}</LaufMarke>
}

/** Der Bereich „Aktive Laeufe" (Kriterien 1–4). */
function LaufendeListe({ zeilen }: Readonly<{ zeilen: DisruptionView[] | null }>) {
  if (zeilen === null) {
    return null
  }
  if (zeilen.length === 0) {
    return <LeerSatz testId="keine-laufenden">Gerade läuft kein Lauf.</LeerSatz>
  }
  return (
    <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {zeilen.map((zeile) => (
        <LaufendeZeile key={zeile.nightRunId} zeile={zeile} />
      ))}
    </Box>
  )
}

/**
 * Eine laufende Zeile: pulsierender Stahl-Melder, Projekt, „laeuft seit HH:MM", Kennung.
 *
 * **Melder, Wort und Puls kommen aus demselben Befund** — sie koennen nicht auseinanderlaufen. Das
 * Wort steht dabei nicht nur zur Zierde: Nach Kriterium 3 darf der Zustand weder allein an einer
 * Farbe noch allein an der Bewegung haengen. Wer Bewegung abgeschaltet hat, liest ihn trotzdem —
 * den Puls haelt die globale `prefers-reduced-motion`-Regel des Themes von selbst an.
 *
 * Die Uhrzeit kommt aus {@link uhrzeit}, also im Format des Laufbands („seit 02:41", Entwurf
 * Z. 1164–1165).
 */
function LaufendeZeile({ zeile }: Readonly<{ zeile: DisruptionView }>) {
  return (
    <Box component="li" data-testid={`laufend-${zeile.nightRunId}`} sx={LAUF_ZEILE_SX}>
      <Led melder={melderAusBefund(zeile.outcome)} pulsiert={zeile.outcome.verdict === 'RUNNING'} />
      <Projektname name={zeile.projectName} />
      <Typography sx={{ fontSize: 12, color: 'text.secondary', flex: 1, minWidth: 0 }}>
        {`${NIGHT_RUN_VERDICT_TEXT[zeile.outcome.verdict]} seit ${uhrzeit(zeile.startedAt)}`}
      </Typography>
      <ArtMarke zeile={zeile} bereich="laufend" />
      <LaufVerweis zeile={zeile} />
    </Box>
  )
}

/** Der Bereich „Beendete Laeufe" (Kriterien 9–14). */
function DurchgefuehrteListe({
  zeilen,
  mitStoerung,
}: Readonly<{ zeilen: DisruptionView[] | null; mitStoerung: ReadonlySet<number> }>) {
  if (zeilen === null) {
    return null
  }
  if (zeilen.length === 0) {
    return (
      <LeerSatz testId="keine-durchgefuehrten">In diesem Zyklus wurde noch kein Lauf beendet.</LeerSatz>
    )
  }
  return (
    <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {zeilen.map((zeile) => (
        <DurchgefuehrteZeile
          key={zeile.nightRunId}
          zeile={zeile}
          hatStoerung={mitStoerung.has(zeile.nightRunId)}
        />
      ))}
    </Box>
  )
}

/**
 * Eine durchgefuehrte Zeile: ruhender Melder, Projekt, Startzeitpunkt, Kennung, Ausgang als Wort.
 *
 * **Der Melder pulst hier nicht** (Kriterium 5): Ein beendeter Lauf arbeitet nicht mehr. Welche
 * Farbe er traegt, sagt {@link melderAusBefund} — dieselbe Stelle, aus der die Nachtlauf-Auswertung
 * ihre Farbe holt.
 *
 * **Der Ausgang steht als Wort da** (Kriterium 11) — gelungen, nicht gelungen, mit Vorbehalt. Farbe
 * ist nie der einzige Traeger der Aussage.
 *
 * **Der zweite Verweis** fuehrt zur Stoerzeile weiter unten auf derselben Seite (Kriterium 12); er
 * erscheint nur, solange die Stoerung offen ist.
 */
function DurchgefuehrteZeile({
  zeile,
  hatStoerung,
}: Readonly<{ zeile: DisruptionView; hatStoerung: boolean }>) {
  return (
    <Box component="li" data-testid={`durchgefuehrt-${zeile.nightRunId}`} sx={LAUF_ZEILE_SX}>
      <Led melder={melderAusBefund(zeile.outcome)} />
      <Projektname name={zeile.projectName} />
      <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>
        {tagZeit(zeile.startedAt)}
      </Typography>
      <ArtMarke zeile={zeile} bereich="durchgefuehrt" />
      <LaufVerweis zeile={zeile} />
      <Typography sx={{ fontSize: 12, color: 'text.secondary', flex: 1, minWidth: 0 }}>
        {NIGHT_RUN_VERDICT_TEXT[zeile.outcome.verdict]}
      </Typography>
      {hatStoerung && (
        <Typography
          component="a"
          href={`#stoerung-${zeile.nightRunId}`}
          aria-label={`Zur Störung von Lauf #${zeile.nightRunId}`}
          sx={{ fontSize: 12 }}
        >
          Störung
        </Typography>
      )}
    </Box>
  )
}

/** Die offenen Stoerungen **eines** Projekts, in der Reihenfolge der Server-Antwort (#1087). */
interface Projektgruppe {
  projectId: number
  projectName: string
  stoerungen: DisruptionView[]
}

/**
 * Gruppiert die Antwort nach Projekt, **ohne** neu zu sortieren (#1087).
 *
 * Der Server liefert `started_at DESC, id DESC`, und eine `Map` behaelt die Einfuegereihenfolge:
 * Die Gruppen stehen damit in der Reihenfolge ihres jeweils ersten — und deshalb juengsten —
 * Eintrags, und innerhalb einer Gruppe bleibt die Reihenfolge der Antwort erhalten. Ein zweites
 * Sortieren im Browser waere eine zweite Fassung von „juengste zuoberst"; sie liefe auseinander,
 * sobald der Server seine Sortierung aendert.
 */
function nachProjekt(liste: readonly DisruptionView[]): Projektgruppe[] {
  const gruppen = new Map<number, Projektgruppe>()
  for (const stoerung of liste) {
    const gruppe = gruppen.get(stoerung.projectId)
    if (gruppe === undefined) {
      gruppen.set(stoerung.projectId, {
        projectId: stoerung.projectId,
        projectName: stoerung.projectName,
        stoerungen: [stoerung],
      })
    } else {
      gruppe.stoerungen.push(stoerung)
    }
  }
  return [...gruppen.values()]
}

/**
 * Der Inhalt des Bereichs: noch nichts geladen, kein Eintrag, oder die Gruppen.
 *
 * Eigene Komponente statt dreier Zweige im Rumpf der Seite: Der Leerfall ist eine eigene Aussage
 * (AK 14) und kein Sonderweg der Liste.
 */
function Stoerungen({
  liste,
  onQuittieren,
}: Readonly<{
  liste: DisruptionView[] | null
  onQuittieren: (stoerung: DisruptionView) => Promise<void>
}>) {
  if (liste === null) {
    return null
  }
  if (liste.length === 0) {
    // AK 14: Eine leere Flaeche waere von einer kaputten Anzeige nicht zu unterscheiden — und eine
    // Gruppenliste ohne Gruppen genauso wenig (#1087). Derselbe LeerSatz wie in den beiden
    // Lauf-Bereichen, damit der Satz unter der Überschrift eingerückt steht wie dort.
    return <LeerSatz testId="keine-stoerungen">Keine offene Störung.</LeerSatz>
  }
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {nachProjekt(liste).map((gruppe) => (
        <Projektblock key={gruppe.projectId} gruppe={gruppe} onQuittieren={onQuittieren} />
      ))}
    </Box>
  )
}

/**
 * Eine Projektgruppe: die Ueberschriftenzeile und die Stoerzeilen des Projekts (#1087).
 *
 * **Eine eigene, benannte Liste je Projekt** statt einer durchlaufenden Liste mit
 * Zwischenueberschriften: Sonst sagte ein Vorlesewerkzeug „Liste mit elf Eintraegen" und liesse
 * offen, wo ein Projekt aufhoert und das naechste anfaengt. Die Liste traegt den Projektnamen als
 * Namen (`aria-labelledby`), nicht die ganze Ueberschrift — die Zahl dahinter aendert sich beim
 * Quittieren und gehoert nicht in den Namen der Liste.
 *
 * **Die Zahl steht in der Ueberschrift**, weil genau sie die Frage beantwortet, die in der flachen
 * Liste nur durch Durchzaehlen zu beantworten war: einmal betroffen oder fuenfmal?
 *
 * Eine Ueberschriftenzeile innerhalb der bestehenden {@link Platte} und keine eigene Platte je
 * Projekt: Die Platte traegt den Bereichstitel „Stoerungen"; je Projekt eine machte aus einem
 * Bereich viele und verlangte eine Gestaltung, die `docs/entwurf-leitstand.html` hier nicht fuehrt.
 */
function Projektblock({
  gruppe,
  onQuittieren,
}: Readonly<{
  gruppe: Projektgruppe
  onQuittieren: (stoerung: DisruptionView) => Promise<void>
}>) {
  const nameId = useId()
  const zahl = gruppe.stoerungen.length
  return (
    <Box data-testid={`stoergruppe-${gruppe.projectId}`}>
      <Box
        component="h3"
        data-testid={`stoergruppe-kopf-${gruppe.projectId}`}
        sx={{
          display: 'flex',
          alignItems: 'baseline',
          gap: '8px',
          m: 0,
          // Derselbe Einzug wie beim Leersatz und den Lauf-Zeilen (#1122). Er sitzt am Kopf
          // selbst und nicht am umschliessenden Kasten, damit die Trennlinie darunter weiter
          // ueber die volle Breite der Platte laeuft.
          px: '16px',
          pb: '4px',
          borderBottom: `1px solid ${RAND}`,
        }}
      >
        <Box component="span" id={nameId} sx={{ ...ANZEIGE, fontSize: 12.5, fontWeight: 600 }}>
          {gruppe.projectName}
        </Box>
        <Box component="span" sx={{ fontSize: 11.5, color: TEXT_SCHWACH }}>
          {zahl === 1 ? '1 Störung' : `${zahl} Störungen`}
        </Box>
      </Box>
      <Box component="ul" aria-labelledby={nameId} sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {gruppe.stoerungen.map((stoerung) => (
          <Stoerzeile
            key={stoerung.nightRunId}
            stoerung={stoerung}
            onQuittieren={() => void onQuittieren(stoerung)}
          />
        ))}
      </Box>
    </Box>
  )
}

/**
 * Der Grundtext einer Stoerzeile (AK 6, Plan #1072 E4).
 *
 * Gebildet wird er **hier** und nicht im Server: `nightRunZustandsText` und seine Tabellen liegen
 * im Browser, und die Nachtlauf-Auswertung sagt denselben Satz mit derselben Funktion. Ein zweiter
 * Satz im Server waere genau die zweite Formulierung, die AK 6 verbietet.
 *
 * **Ohne Dauer**, anders als die Ansage der Nachtlauf-Seite: Die Stoerzeile fuehrt den Zeitpunkt
 * des Laufs bereits als eigene Angabe, und die Laufzeit eines einzelnen Pakets sagt ueber die
 * Stoerung nichts.
 */
export function stoerungsGrund(outcome: DisruptionView['outcome']): string {
  if (outcome.noWorkReason !== null && outcome.noWorkReason !== '') {
    return outcome.noWorkReason
  }
  const paket = outcome.decisiveItem
  if (paket === null) {
    return ''
  }
  return `Karte #${paket.cardNumber}: ${nightRunZustandsText(paket.state, paket.errorClass ?? undefined)}`
}

/**
 * Eine Stoerzeile: Melder, Zeitpunkt, Kennung, Grund und die Taste zum Quittieren.
 *
 * **Ohne den Projektnamen** (#1087) — er steht in der Ueberschrift der Gruppe, und derselbe Name
 * unter seiner eigenen Ueberschrift in jeder Zeile ist Rauschen, das dem Grund den Platz nimmt.
 * Das `aria-label` der Taste behaelt ihn dagegen: Ohne Namen waeren zwei Tasten verschiedener
 * Projekte fuer ein Vorlesewerkzeug nicht zu unterscheiden.
 *
 * **Das `id` neben dem `data-testid`** (#1098) ist das Ziel des Verweises „Stoerung" aus der
 * durchgefuehrten Zeile (Kriterium 12) — ein `data-testid` allein ist kein Sprungziel.
 */
function Stoerzeile({
  stoerung,
  onQuittieren,
}: Readonly<{ stoerung: DisruptionView; onQuittieren: () => void }>) {
  const melder = stoerung.outcome.decisiveItem
    ? MELDER_JE_ZUSTAND[stoerung.outcome.decisiveItem.state]
    : 'zinnob'
  return (
    <Box
      component="li"
      id={`stoerung-${stoerung.nightRunId}`}
      data-testid={`stoerung-${stoerung.nightRunId}`}
      // `px` wie in {@link LAUF_ZEILE_SX} und beim Leersatz (#1122): Sonst beruehrte die Taste
      // „Stoerung loeschen" rechts den Rahmen der Platte. Der eigene senkrechte Rhythmus bleibt.
      sx={{ display: 'flex', alignItems: 'center', gap: '10px', px: '16px', py: '6px' }}
    >
      <Led melder={melder} />
      <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>
        {tagZeit(stoerung.startedAt)}
      </Typography>
      {/* AK 7: Die Kennung fuehrt zur Auswertung genau dieses Laufs — auch ohne Mitgliedschaft. */}
      <Typography
        component={RouterLink}
        to={`/projects/${stoerung.projectId}/nachtlauf?lauf=${stoerung.nightRunId}`}
        sx={{ fontSize: 12, fontFamily: 'monospace' }}
      >
        Lauf #{stoerung.nightRunId}
      </Typography>
      <ArtMarke zeile={stoerung} bereich="stoerung" />
      <Typography sx={{ fontSize: 12, color: 'text.secondary', flex: 1, minWidth: 0 }}>
        {stoerungsGrund(stoerung.outcome)}
      </Typography>
      <Taste
        ariaLabel={`Störung von ${stoerung.projectName}, Lauf #${stoerung.nightRunId} löschen`}
        onClick={onQuittieren}
      >
        Störung löschen
      </Taste>
    </Box>
  )
}
