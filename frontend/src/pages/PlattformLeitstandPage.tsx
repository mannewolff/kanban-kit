import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
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
import { LaufArtSymbol } from '../components/leitstand/LaufArtSymbol'
import { FilterTaste, Led, Platte, Taste } from '../components/leitstand/LeitstandBausteine'
import { melderAusBefund, tagZeit, uhrzeit } from '../lib/leitstand'
import { kurzGrund, NIGHT_RUN_VERDICT_TEXT, nightRunZustandsText } from '../lib/nightRunHandoff'
import { useRefetchOnFocus } from '../lib/useRefetchOnFocus'
import { zyklusDavor, zyklusDesStarts, zyklusSpanne } from '../lib/verbrauchZeitraum'
import { ANZEIGE, ETIKETT, KLEIN_RADIUS, NUT, RAND, TEXT_MATT, TEXT_SCHWACH } from '../theme'

/** Der Anfangszustand: drei leere Listen, noch von keiner Antwort belegt. */
const LEERE_SICHT: LeitstandView = { laufende: [], durchgefuehrte: [], durchgefuehrteVoriger: [], stoerungen: [] }

/** Der Takt des Auffrischens (Kriterium 19): Was sich aendert, steht spaetestens so bald da. */
const AUFFRISCH_MS = 30_000

/**
 * Wo der Browser sich merkt, ob die vorige Schicht aufgeklappt ist (Issue #1152).
 *
 * Ein Zustand nur im Speicher der Seite waere hier keine Einstellung, sondern eine Geste: Die
 * Seite frischt sich alle 30 Sekunden auf und steht lange offen; jeder Reload setzte sie zurueck.
 */
const VORIGE_OFFEN_SCHLUESSEL = 'leitstand-voriger-zyklus-offen'

/** Ein fehlender oder unlesbarer Wert bedeutet zugeklappt (Muster aus `AppShell.tsx`). */
function leseVorigeOffen(): boolean {
  try {
    return localStorage.getItem(VORIGE_OFFEN_SCHLUESSEL) === 'true'
  } catch {
    return false
  }
}

function schreibeVorigeOffen(wert: boolean): void {
  try {
    localStorage.setItem(VORIGE_OFFEN_SCHLUESSEL, String(wert))
  } catch {
    // localStorage nicht verfuegbar — der Zustand haelt dann nur diese Sitzung, kein Hard-Fail.
  }
}

/**
 * Wie viele beendete Runs der Bereich zeigt (Issue #1140) — die Werte der drei Tasten.
 *
 * Als Zeichenkette und nicht als Zahl mit `Infinity`: Genau diese Werte stehen im Speicher des
 * Browsers, und ein gelesener Wert lässt sich damit ohne Umrechnung gegen sie prüfen.
 */
const ANZAHL_WERTE = ['10', '20', 'alle'] as const
type AnzahlWahl = (typeof ANZAHL_WERTE)[number]

/** Die Vorgabe beim ersten Besuch: Der Anlass der Einstellung ist die zu lange Liste. */
const ANZAHL_VORGABE: AnzahlWahl = '10'

/** Wo der Browser sich die Wahl merkt — die Seite steht lange offen und frischt sich selbst auf. */
const ANZAHL_SCHLUESSEL = 'manban.plattformLeitstand.anzahl'

function istAnzahlWahl(wert: string | null): wert is AnzahlWahl {
  return ANZAHL_WERTE.some((w) => w === wert)
}

/** Ein fehlender, unlesbarer oder unbekannter Wert ergibt die Vorgabe. */
function leseAnzahl(): AnzahlWahl {
  try {
    const wert = localStorage.getItem(ANZAHL_SCHLUESSEL)
    return istAnzahlWahl(wert) ? wert : ANZAHL_VORGABE
  } catch {
    return ANZAHL_VORGABE
  }
}

function schreibeAnzahl(wahl: AnzahlWahl): void {
  try {
    localStorage.setItem(ANZAHL_SCHLUESSEL, wahl)
  } catch {
    // Wie beim Klappzustand: ein gesperrter Speicher kostet die Erinnerung, nicht die Seite.
  }
}

/** Die Wahl als Obergrenze; „alle" begrenzt nicht. */
function grenzeVon(wahl: AnzahlWahl): number {
  return wahl === 'alle' ? Number.POSITIVE_INFINITY : Number(wahl)
}

function runWort(anzahl: number): string {
  return anzahl === 1 ? '1 Run' : `${anzahl} Runs`
}

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
  // Grundzustand zugeklappt (#1152): Gelesen wird an der Stelle fast immer nur die laufende
  // Schicht; ein Dutzend alter Zeilen schoebe die Stoerungen aus dem Bild.
  const [vorigeOffen, setVorigeOffen] = useState(leseVorigeOffen)

  // Wie viele beendete Runs gezeigt werden (#1140). Anders als der Klappzustand ist das eine
  // ausdrückliche Einstellung des Menschen, deshalb wird sie gemerkt.
  const [anzahlWahl, setAnzahlWahl] = useState(leseAnzahl)

  const vorigeUmschalten = useCallback(() => {
    setVorigeOffen((offen) => !offen)
  }, [])

  // Geschrieben wird als Wirkung und nicht im Umschalter: So haelt der Speicher auch dann den
  // gezeigten Zustand, wenn React den Aktualisierer doppelt ausfuehrt.
  useEffect(() => {
    schreibeVorigeOffen(vorigeOffen)
  }, [vorigeOffen])

  useEffect(() => {
    schreibeAnzahl(anzahlWahl)
  }, [anzahlWahl])

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
  // Die Spannen nennen die Zyklen, deren Grenzen der Server mit derselben Zone zieht (#1135).
  const dieserZyklus = zyklusDesStarts(new Date().toISOString())

  // Die Begrenzung zählt **beide** Abschnitte zusammen (#1140): zuerst die dieser Schicht in der
  // Reihenfolge der Antwort, der Rest aus der vorigen. Je Abschnitt N zeigte bei „10" bis zu zwanzig
  // Zeilen — die Einstellung heißt aber „wie viele Runs sehe ich".
  //
  // Gerechnet wird auf der ganzen Antwort und nicht auf dem, was gerade aufgeklappt ist: Der
  // Klappzustand ist eine Geste, die Einstellung eine Einstellung. Sonst sprängen die Zeilen der
  // vorigen Schicht beim Zuklappen dieser in die Sicht.
  const grenze = grenzeVon(anzahlWahl)
  const sichtbarDieser = sicht.durchgefuehrte.slice(0, grenze)
  const sichtbarVoriger = sicht.durchgefuehrteVoriger.slice(
    0,
    Math.max(0, grenze - sicht.durchgefuehrte.length),
  )
  const verdecktDieser = sicht.durchgefuehrte.length - sichtbarDieser.length
  const verdecktVoriger = sicht.durchgefuehrteVoriger.length - sichtbarVoriger.length

  return (
    <KupferwarteBereich>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <Platte titel="Aktive Runs">
          <LaufendeListe zeilen={geladen ? sicht.laufende : null} />
        </Platte>
        <Platte
          titel="Beendete Runs"
          werkzeug={<AnzahlWahlTasten wahl={anzahlWahl} onWaehlen={setAnzahlWahl} />}
        >
          <ZyklusAbschnitt titel="Diese Schicht" spanne={zyklusSpanne(dieserZyklus)} testId="zyklus-dieser">
            <DurchgefuehrteListe
              zeilen={geladen ? sichtbarDieser : null}
              verdeckt={verdecktDieser}
              testId="zyklus-dieser"
              mitStoerung={mitStoerung}
              leer={{ testId: 'keine-durchgefuehrten', text: 'In dieser Schicht wurde noch kein Run beendet.' }}
            />
          </ZyklusAbschnitt>
          <ZyklusAbschnitt
            titel="Vorige Schicht"
            spanne={zyklusSpanne(zyklusDavor(dieserZyklus))}
            testId="zyklus-voriger"
            anzahl={geladen ? sicht.durchgefuehrteVoriger.length : null}
            offen={vorigeOffen}
            onUmschalten={vorigeUmschalten}
          >
            <DurchgefuehrteListe
              zeilen={geladen ? sichtbarVoriger : null}
              verdeckt={verdecktVoriger}
              testId="zyklus-voriger"
              mitStoerung={mitStoerung}
              leer={{ testId: 'keine-durchgefuehrten-voriger', text: 'In der vorigen Schicht wurde kein Run beendet.' }}
            />
          </ZyklusAbschnitt>
          {geladen && verdecktDieser + verdecktVoriger > 0 && (
            <VerdecktSatz testId="ausgeblendet-hinweis">
              {verdecktDieser + verdecktVoriger === 1
                ? '1 weiterer Run ausgeblendet'
                : `${verdecktDieser + verdecktVoriger} weitere Runs ausgeblendet`}
            </VerdecktSatz>
          )}
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

/**
 * Die drei Tasten „10 · 20 · alle" im Werkzeugbereich der Platte „Beendete Runs" (Issue #1140).
 *
 * Tasten statt eines Auswahlmenüs: Drei Werte stehen so mit einem Klick bereit, und das Muster
 * {@link FilterTaste} kennt die Seite schon aus dem Board-Leitstand. Das `aria-label` trägt den
 * Bezug, den die Aufschrift „10" für sich genommen nicht hat.
 */
function AnzahlWahlTasten({
  wahl,
  onWaehlen,
}: Readonly<{ wahl: AnzahlWahl; onWaehlen: (wahl: AnzahlWahl) => void }>) {
  return (
    <>
      {ANZAHL_WERTE.map((wert) => (
        <FilterTaste
          key={wert}
          gewaehlt={wert === wahl}
          ariaLabel={`Beendete Runs: ${wert}`}
          onClick={() => onWaehlen(wert)}
        >
          {wert}
        </FilterTaste>
      ))}
    </>
  )
}

/**
 * Ein Satz über ausgeblendete Zeilen (#1140) — gestaltet wie der {@link LeerSatz}.
 *
 * Er sagt aus, was ohne ihn nur eine kurze Liste wäre: dass da mehr ist. Ohne ihn sähe eine
 * begrenzte Sicht aus wie eine ruhige Nacht.
 */
function VerdecktSatz({ testId, children }: Readonly<{ testId: string; children: ReactNode }>) {
  return (
    <Typography
      data-testid={testId}
      sx={{ fontSize: 12, color: TEXT_SCHWACH, px: '16px', py: '10px' }}
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
      aria-label={`Run #${zeile.nightRunId} von ${zeile.projectName}`}
      sx={{ fontSize: 12, fontFamily: 'monospace' }}
    >
      Run #{zeile.nightRunId}
    </Typography>
  )
}

/** Der Bereich „Aktive Laeufe" (Kriterien 1–4). */
function LaufendeListe({ zeilen }: Readonly<{ zeilen: DisruptionView[] | null }>) {
  if (zeilen === null) {
    return null
  }
  if (zeilen.length === 0) {
    return <LeerSatz testId="keine-laufenden">Gerade läuft kein Run.</LeerSatz>
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
      <LaufArtSymbol art={zeile.mode} />
      <Projektname name={zeile.projectName} />
      <Typography sx={{ fontSize: 12, color: 'text.secondary', flex: 1, minWidth: 0 }}>
        {`${NIGHT_RUN_VERDICT_TEXT[zeile.outcome.verdict]} seit ${uhrzeit(zeile.startedAt)}`}
      </Typography>
      <LaufVerweis zeile={zeile} />
    </Box>
  )
}

/**
 * Ein Abschnitt des Bereichs „Beendete Laeufe" (Issue #1135): Ueberschrift und Spanne des Zyklus,
 * darunter seine Zeilen. Die Ueberschrift benennt den Abschnitt auch fuer Vorlesewerkzeuge.
 *
 * **Klappbar nur mit `onUmschalten`** (Issue #1152): Ohne den Rueckruf verhaelt sich der Abschnitt
 * wie zuvor — „Diese Schicht" traegt keinen Pfeil und ist immer sichtbar. Eine zweite Komponente
 * daneben haette Kopfzeile, Spanne und `aria-labelledby` verdoppelt, also zwei Stellen, die
 * dasselbe sagen.
 *
 * **Zugeklappt wird der Inhalt gar nicht gerendert** — kein `display: none`: Was nicht zu sehen
 * ist, soll auch von Vorlesewerkzeugen und der Suche im Dokument nicht gefunden werden.
 */
function ZyklusAbschnitt({
  titel,
  spanne,
  testId,
  anzahl = null,
  offen = false,
  onUmschalten,
  children,
}: Readonly<{
  titel: string
  spanne: string
  testId: string
  /** Die Anzahl der Runs des Abschnitts; `null`, solange die erste Antwort fehlt. */
  anzahl?: number | null
  offen?: boolean
  onUmschalten?: () => void
  children: ReactNode
}>) {
  const id = useId()
  const inhaltId = useId()
  const klappbar = onUmschalten !== undefined
  const zeigtInhalt = !klappbar || offen

  /**
   * Ein Klick in die Kopfzeile schaltet um — ausser er beendet gerade eine Textauswahl. Wer die
   * Spanne markiert, um sie zu kopieren, will den Abschnitt nicht zuklappen (Muster aus
   * {@link NachtlaufLaufPlatte}).
   */
  const kopfKlick =
    onUmschalten &&
    (() => {
      if ((window.getSelection()?.toString() ?? '') !== '') {
        return
      }
      onUmschalten()
    })

  return (
    <Box component="section" aria-labelledby={id} data-testid={testId}>
      {/* `role="presentation"`: Die Zeile traegt keine eigene Semantik, ihr Klick ist die bequemere
          Flaeche fuer den Pfeil darin. Die Tastaturbedienung sitzt am Pfeil und waere an der Zeile
          ein zweiter Halt in derselben Reihenfolge. */}
      <Box
        {...(kopfKlick && { role: 'presentation', onClick: kopfKlick, 'data-testid': `${testId}-kopf` })}
        sx={{
          display: 'flex',
          alignItems: 'baseline',
          gap: '8px',
          px: '16px',
          pt: '10px',
          ...(klappbar && { cursor: 'pointer', pb: '10px' }),
          ...(klappbar && zeigtInhalt && { borderBottom: `1px solid ${RAND}` }),
        }}
      >
        {onUmschalten && (
          <ButtonBase
            aria-expanded={zeigtInhalt}
            aria-controls={zeigtInhalt ? inhaltId : undefined}
            aria-label={`${titel} ${zeigtInhalt ? 'zuklappen' : 'aufklappen'}`}
            // Ohne gestoppte Weitergabe schaltete die Kopfzeile ein zweites Mal — und damit gar nicht.
            onClick={(ereignis) => {
              ereignis.stopPropagation()
              onUmschalten()
            }}
            sx={{
              width: 22,
              height: 22,
              flex: 'none',
              alignSelf: 'center',
              display: 'grid',
              placeItems: 'center',
              borderRadius: `${KLEIN_RADIUS}px`,
              color: TEXT_MATT,
              '&:hover': { bgcolor: NUT },
            }}
          >
            {/* Groesse im `sx` und nicht als `width`/`height` am Element — siehe #1041. */}
            <Box
              component="svg"
              data-testid={`${testId}-pfeil`}
              viewBox="0 0 16 16"
              fill="none"
              aria-hidden
              sx={{
                width: 12,
                height: 12,
                transition: 'transform .15s ease',
                transform: zeigtInhalt ? 'none' : 'rotate(-90deg)',
                '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
              }}
            >
              <path
                d="m4 6 4 4 4-4"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </Box>
          </ButtonBase>
        )}
        <Box component="h3" id={id} sx={{ ...ETIKETT, m: 0 }}>
          {titel}
        </Box>
        <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{spanne}</Typography>
        {anzahl !== null && (
          <Typography data-testid={`${testId}-anzahl`} sx={{ fontSize: 12, color: 'text.secondary' }}>
            {runWort(anzahl)}
          </Typography>
        )}
      </Box>
      {zeigtInhalt && (klappbar ? <Box id={inhaltId}>{children}</Box> : children)}
    </Box>
  )
}

/** Der Bereich „Beendete Laeufe" (Kriterien 9–14) — je Zyklus eine Liste (Issue #1135). */
function DurchgefuehrteListe({
  zeilen,
  verdeckt,
  testId,
  mitStoerung,
  leer,
}: Readonly<{
  /** Die **sichtbaren** Zeilen des Abschnitts; `null`, solange die erste Antwort fehlt. */
  zeilen: DisruptionView[] | null
  /** Wie viele Zeilen des Abschnitts die Einstellung verdrängt hat (#1140). */
  verdeckt: number
  testId: string
  mitStoerung: ReadonlySet<number>
  leer: { testId: string; text: string }
}>) {
  if (zeilen === null) {
    return null
  }
  if (zeilen.length === 0) {
    // Ein ganz verdrängter Abschnitt hat sehr wohl Runs — der Leersatz wäre dort schlicht falsch
    // und ließe eine arbeitsreiche Schicht wie eine leere aussehen (#1140).
    return verdeckt > 0 ? (
      <VerdecktSatz testId={`${testId}-ausgeblendet`}>{`${runWort(verdeckt)} ausgeblendet`}</VerdecktSatz>
    ) : (
      <LeerSatz testId={leer.testId}>{leer.text}</LeerSatz>
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
 * **Hinter dem Wort steht der Abbruchgrund** (Issue #1146, AK 4), sofern der Lauf einen meldet —
 * gekuerzt auf eine Zeile durch {@link kurzGrund}, dieselbe Kuerzung wie in der Stoerzeile und in
 * der Kopfmarke der Nachtlauf-Auswertung. „Nicht gelungen" allein liesse offen, ob ein Paket rot
 * war oder der Lauf als ganzer riss; den vollen Text zeigt die Auswertung des Laufs.
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
      <LaufArtSymbol art={zeile.mode} />
      <Projektname name={zeile.projectName} />
      <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>
        {tagZeit(zeile.startedAt)}
      </Typography>
      <LaufVerweis zeile={zeile} />
      <Typography sx={{ fontSize: 12, color: 'text.secondary', flex: 1, minWidth: 0 }}>
        {NIGHT_RUN_VERDICT_TEXT[zeile.outcome.verdict]}
        {zeile.outcome.abortReason ? ` — ${kurzGrund(zeile.outcome.abortReason)}` : ''}
      </Typography>
      {hatStoerung && (
        <Typography
          component="a"
          href={`#stoerung-${zeile.nightRunId}`}
          aria-label={`Zur Störung von Run #${zeile.nightRunId}`}
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
  // Der selbst gemeldete Abbruch steht vor allem anderen (Issue #1146, AK 4): Er sagt, warum der
  // Lauf abbrach, und das schlaegt sowohl den Rueckfall „ohne Arbeit" als auch das massgebliche
  // Paket — nach einem harten Stopp ist dessen Zustand nur noch der letzte Stand vor dem Riss.
  // Gekuerzt wird mit {@link kurzGrund} und nicht im Server (Plan #1139 E8): Die Textbildung liegt
  // im Browser, und die Nachtlauf-Auswertung kuerzt denselben Text mit derselben Funktion.
  if (outcome.abortReason !== null && outcome.abortReason !== '') {
    return kurzGrund(outcome.abortReason)
  }
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
 *
 * **Der Melder kommt aus {@link melderAusBefund}** (Issue #1146, E13) und nicht aus einer eigenen
 * Rechnung ueber das massgebliche Paket: Ein Lauf, der nach einem zurueckgestellten oder gelben
 * Paket hart abbrach, erschien hier sonst grau oder bernstein — neben seinem Abbruchgrund als Text
 * und neben derselben Zeile in „Beendete Runs", die ihn zinnober zeigt. Zwei Farben fuer denselben
 * Ausgang auf einer einzigen Seite (AK 8).
 */
function Stoerzeile({
  stoerung,
  onQuittieren,
}: Readonly<{ stoerung: DisruptionView; onQuittieren: () => void }>) {
  return (
    <Box
      component="li"
      id={`stoerung-${stoerung.nightRunId}`}
      data-testid={`stoerung-${stoerung.nightRunId}`}
      // `px` wie in {@link LAUF_ZEILE_SX} und beim Leersatz (#1122): Sonst beruehrte die Taste
      // „Stoerung loeschen" rechts den Rahmen der Platte. Der eigene senkrechte Rhythmus bleibt.
      sx={{ display: 'flex', alignItems: 'center', gap: '10px', px: '16px', py: '6px' }}
    >
      <Led melder={melderAusBefund(stoerung.outcome)} />
      <LaufArtSymbol art={stoerung.mode} />
      <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>
        {tagZeit(stoerung.startedAt)}
      </Typography>
      {/* AK 7: Die Kennung fuehrt zur Auswertung genau dieses Laufs — auch ohne Mitgliedschaft. */}
      <Typography
        component={RouterLink}
        to={`/projects/${stoerung.projectId}/nachtlauf?lauf=${stoerung.nightRunId}`}
        sx={{ fontSize: 12, fontFamily: 'monospace' }}
      >
        Run #{stoerung.nightRunId}
      </Typography>
      <Typography sx={{ fontSize: 12, color: 'text.secondary', flex: 1, minWidth: 0 }}>
        {stoerungsGrund(stoerung.outcome)}
      </Typography>
      <Taste
        ariaLabel={`Störung von ${stoerung.projectName}, Run #${stoerung.nightRunId} löschen`}
        onClick={onQuittieren}
      >
        Störung löschen
      </Taste>
    </Box>
  )
}
