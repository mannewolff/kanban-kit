import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useId, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { ApiError } from '../api/client'
import { plattformLeitstandApi, type DisruptionView } from '../api/plattformLeitstand'
import { KupferwarteBereich } from '../components/nachtlauf/KupferwarteBereich'
import { Led, Platte, Taste } from '../components/leitstand/LeitstandBausteine'
import { MELDER_JE_ZUSTAND, tagZeit } from '../lib/leitstand'
import { nightRunZustandsText } from '../lib/nightRunHandoff'
import { ANZEIGE, RAND, TEXT_SCHWACH } from '../theme'

/**
 * Der Plattform-Leitstand: die Startseite eines Plattform-Admins (Issue #1083, fachliche Quelle
 * #1064).
 *
 * Ihr erster Bereich heisst **Stoerungen** und zeigt jede nicht quittierte Stoerung aus den
 * Nachtlaeufen aller teilnehmenden Projekte, juengste zuoberst. Wer mehrere Projekte betreibt,
 * erfaehrt damit von einem gescheiterten Nachtlauf, ohne jedes Projekt einzeln aufzuschlagen.
 *
 * **Nach Projekt gruppiert** (Issue #1087): Eine flache Liste liess den Leser abwechselnd Namen
 * statt Befunde lesen — zwei Stoerungen desselben Projekts konnten durch eine fremde getrennt
 * sein, und ob ein Projekt einmal oder fuenfmal betroffen ist, ergab sich erst aus dem Durchzaehlen.
 *
 * **Gestaltung:** `docs/entwurf-leitstand.html` ist verbindlich (`CLAUDE-design.md`), fuehrt fuer
 * diese Ansicht aber kein eigenes Mockup. Sie entsteht deshalb aus den vorhandenen Bausteinen —
 * {@link KupferwarteBereich}, {@link Platte}, {@link Led}, {@link Taste} — und nicht aus einer
 * neuen Gestaltung (Plan #1072 E15).
 */
export default function PlattformLeitstandPage() {
  // Zwei Zustaende statt einer Liste mit `null`: Solange nicht geladen ist, zeigt die Seite nichts —
  // sonst blitzte „Keine offene Stoerung." auf, bevor die erste Antwort da ist. Getrennt gehalten,
  // damit das Quittieren keinen Rueckfall auf eine leere Liste braucht, den nichts erreichen kann.
  const [stoerungen, setStoerungen] = useState<DisruptionView[]>([])
  const [geladen, setGeladen] = useState(false)
  const [fehler, setFehler] = useState<string | null>(null)

  const laden = useCallback(() => {
    plattformLeitstandApi
      .liste()
      .then((liste) => {
        setStoerungen(liste)
        setGeladen(true)
        setFehler(null)
      })
      .catch((e) =>
        setFehler(
          e instanceof ApiError && e.status === 403 ? 'Kein Admin-Zugriff.' : 'Laden fehlgeschlagen.',
        ),
      )
  }, [])

  useEffect(() => {
    laden()
  }, [laden])

  // AK 8: kein Rueckfragen-Dialog und kein Rueckgaengig. Die Zeile verschwindet sofort; ein
  // Nachladen der ganzen Liste waere ein zweiter Weg zur selben Aussage.
  const quittieren = async (stoerung: DisruptionView) => {
    await plattformLeitstandApi.quittieren(stoerung.nightRunId)
    setStoerungen((vorher) => vorher.filter((s) => s.nightRunId !== stoerung.nightRunId))
  }

  if (fehler !== null) {
    return <Typography color="error">{fehler}</Typography>
  }

  return (
    <KupferwarteBereich>
      <Platte titel="Störungen">
        <Stoerungen liste={geladen ? stoerungen : null} onQuittieren={quittieren} />
      </Platte>
    </KupferwarteBereich>
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
    // Gruppenliste ohne Gruppen genauso wenig (#1087).
    return (
      <Typography data-testid="keine-stoerungen" sx={{ fontSize: 13, color: 'text.secondary' }}>
        Keine offene Störung.
      </Typography>
    )
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
      data-testid={`stoerung-${stoerung.nightRunId}`}
      sx={{ display: 'flex', alignItems: 'center', gap: '10px', py: '6px' }}
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
