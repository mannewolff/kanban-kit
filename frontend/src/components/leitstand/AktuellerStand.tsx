import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { useId } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import type { DisruptionView, LaufPaketeView, PaketView } from '../../api/plattformLeitstand'
import { laufgruppen, type Laufeintrag } from '../../lib/aktuellerStand'
import { melderAusBefund, MELDER_JE_ZUSTAND, type Projektgruppe } from '../../lib/leitstand'
import { nightRunZustandsText } from '../../lib/nightRunHandoff'
import { ANZEIGE, RAND, TEXT_SCHWACH, ZAHL } from '../../theme'
import { LaufArtSymbol } from './LaufArtSymbol'
import { Led, LeerSatz, ZEILE_HOVER } from './LeitstandBausteine'

/**
 * Der Schluessel eines Pakets in der Menge der verschwundenen Karten (Issue #1174, E14).
 *
 * <p>Mit dem Projekt davor und nicht die Nummer allein: Kartennummern sind **projektweit**
 * eindeutig, nicht plattformweit — der Leitstand zeigt aber die Laeufe aller Projekte
 * uebereinander. Eine Menge blosser Nummern liesse ein verschwundenes „#721" aus Projekt A die
 * Zeile „#721" aus Projekt B mit „nicht gefunden" bestempeln.
 */
export function karteSchluessel(projectId: number, nummer: number): string {
  return `${projectId}#${nummer}`
}

/** Was eine Paketzeile ausser den Daten braucht: die verschwundenen Karten und der Kartenklick. */
interface Zeilenwege {
  /** Karten, die beim Abruf 404 waren — Schluessel aus {@link karteSchluessel} (E14). */
  verschwunden: ReadonlySet<string>
  onKarteOeffnen: (projectId: number, nummer: number) => void
}

/**
 * Die Sektion „Aktueller Status" des Plattform-Leitstands (Issue #1173, fachliche Quelle #1153):
 * je laufendem Run ein Kopf mit seinem Stand, darunter je gemeldetem Paket eine Zeile aus Nummer,
 * Titel und Ausgang — nach Projekt gruppiert (AK 1–8, 10–12).
 *
 * <p><b>Gerechnet wird nicht hier</b> (Plan #1167, E6): Stand und Gruppierung kommen aus
 * `lib/aktuellerStand.ts`, die Woerter aus `lib/nightRunHandoff.ts` — derselben Quelle, aus der
 * die Lauf-Ansicht sie bildet. Ein zweiter Massstab ueber den Ausgang liefe beim naechsten
 * Feinschliff gegen den der Lauf-Ansicht.
 *
 * <p><b>Keine Kappung</b> (AK 7): Anders als „Beendete Runs" begrenzt diese Sektion nichts. Wer
 * beim Auffrischen nachsieht, was der Lauf gerade gemeldet hat, soll nicht raten, ob die Zeile
 * fehlt oder das Paket.
 *
 * <p><b>Gestaltung</b> aus den vorhandenen Bausteinen der Kupferwarte ({@link Led},
 * {@link LaufArtSymbol}, {@link LeerSatz}); `docs/entwurf-leitstand.html` fuehrt fuer diese
 * Ansicht kein eigenes Mockup.
 */
export function AktuellerStand({
  laufende,
  gemeldetePakete,
  ...wege
}: Readonly<
  {
    /** Die laufenden Runs; `null`, solange die erste Antwort fehlt. */
    laufende: DisruptionView[] | null
    /** Die gemeldeten Pakete je laufendem Run, in der Ordnung der Antwort. */
    gemeldetePakete: LaufPaketeView[]
  } & Zeilenwege
>) {
  if (laufende === null) {
    return null
  }
  if (laufende.length === 0) {
    // Ein eigener Satz und nicht der von „Aktive Runs" (E9): Zweimal derselbe Satz auf einer Seite
    // ist fuer Vorlesewerkzeuge wie fuer Tests nicht auseinanderzuhalten.
    return (
      <LeerSatz testId="kein-aktueller-stand">Gerade arbeitet kein Run — nichts gemeldet.</LeerSatz>
    )
  }
  // Die Rechnung kennt vom Lauf nur Kennung und Projekt; Art und Befund holt der Kopf hier —
  // gruppiert wird trotzdem nur an einer Stelle.
  const jeLauf = new Map(laufende.map((lauf) => [lauf.nightRunId, lauf]))
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {laufgruppen(laufende, gemeldetePakete).map((gruppe) => (
        <Standgruppe key={gruppe.projectId} gruppe={gruppe} jeLauf={jeLauf} {...wege} />
      ))}
    </Box>
  )
}

/**
 * Ein Projektblock: die Ueberschriftenzeile und darunter die Laeufe des Projekts.
 *
 * Gestaltet wie der Projektblock der Stoerungen (#1087) — derselbe Einzug, dieselbe Trennlinie am
 * Kopf selbst, damit sie ueber die volle Breite der Platte laeuft.
 */
function Standgruppe({
  gruppe,
  jeLauf,
  ...wege
}: Readonly<
  {
    gruppe: Projektgruppe<Laufeintrag<PaketView>>
    jeLauf: ReadonlyMap<number, DisruptionView>
  } & Zeilenwege
>) {
  return (
    <Box data-testid={`stand-gruppe-${gruppe.projectId}`}>
      <Box
        component="h3"
        data-testid={`stand-gruppe-kopf-${gruppe.projectId}`}
        sx={{
          ...ANZEIGE,
          fontSize: 12.5,
          fontWeight: 600,
          m: 0,
          px: '16px',
          pb: '4px',
          borderBottom: `1px solid ${RAND}`,
        }}
      >
        {gruppe.projectName}
      </Box>
      {gruppe.eintraege.map((eintrag) => (
        <Standlauf
          key={eintrag.nightRunId}
          eintrag={eintrag}
          lauf={jeLauf.get(eintrag.nightRunId)!}
          projectId={gruppe.projectId}
          {...wege}
        />
      ))}
    </Box>
  )
}

/**
 * Ein Lauf im Projektblock: sein Kopf „Run #N · <Stand>" und darunter seine Paketzeilen.
 *
 * <p>Die Liste traegt ueber `aria-labelledby` den Kopf als Namen — ohne ihn sagte ein
 * Vorlesewerkzeug nur „Liste mit elf Eintraegen" und liesse offen, zu welchem Lauf sie gehoert.
 * Benannt wird mit der Kennung samt Stand und **nicht** mit dem ganzen Kopf: Die Art des Laufs
 * steht als eigenes Symbol daneben und traegt ihren Namen selbst.
 *
 * <p>Der Melder pulsiert wie in der laufenden Zeile und kommt aus demselben Befund — Farbe und
 * Bewegung koennen so nicht auseinanderlaufen.
 */
function Standlauf({
  eintrag,
  lauf,
  projectId,
  ...wege
}: Readonly<
  { eintrag: Laufeintrag<PaketView>; lauf: DisruptionView; projectId: number } & Zeilenwege
>) {
  const kopfId = useId()
  return (
    <Box data-testid={`stand-lauf-${eintrag.nightRunId}`}>
      <Box
        component="h4"
        data-testid={`stand-kopf-${eintrag.nightRunId}`}
        sx={{ display: 'flex', alignItems: 'center', gap: '8px', m: 0, px: '16px', py: '8px' }}
      >
        <Led melder={melderAusBefund(lauf.outcome)} pulsiert={lauf.outcome.verdict === 'RUNNING'} />
        <LaufArtSymbol art={lauf.mode} />
        <Box component="span" id={kopfId} sx={{ fontSize: 12, color: 'text.secondary' }}>
          {`Run #${eintrag.nightRunId} · ${eintrag.stand}`}
        </Box>
      </Box>
      <Box component="ul" aria-labelledby={kopfId} sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {eintrag.pakete.map((p) => (
          <Paketzeile
            key={p.cardNumber}
            lauf={eintrag.nightRunId}
            projectId={projectId}
            paket={p}
            {...wege}
          />
        ))}
      </Box>
    </Box>
  )
}

/**
 * Eine Paketzeile: Melder, Nummer, Titel und das Zustandswort — mit zwei Wegen hinaus (AK 9).
 *
 * <p><b>Das Wort steht sichtbar neben dem Melder</b> (AK 5) — nie nur die Farbe. Gebildet wird es
 * mit {@link nightRunZustandsText}, derselben Funktion wie in der Lauf-Ansicht; `?? undefined`,
 * weil die Antwort eine fehlende Fehlerklasse als `null` fuehrt und die Funktion `undefined`
 * annimmt.
 *
 * <p><b>Zwei Bedienelemente, nicht eines</b> (Issue #1174, E10; Muster aus
 * {@link NachtlaufVorgangszeile}): Die **Nummer** oeffnet die Karte, der **Rest der Zeile** fuehrt
 * in die Lauf-Ansicht. Sie stehen als Geschwister im Raster und **nie** geschachtelt — ein
 * Bedienelement im Bedienelement ist ungueltiges HTML, fuer die Tastatur nicht aufloesbar, und ein
 * Klick, der beides taete, waere nicht vorhersagbar.
 *
 * <p><b>Ohne Karte ist die Nummer kein Bedienelement</b>, sondern reiner Text samt dem Zusatz
 * „Karte #N nicht gefunden" — der Zustand gilt **vor** dem Klick und nicht erst nach einem
 * erfolglosen. Er kommt aus zwei Quellen: `cardExists` der Antwort (Issue #1170) und der Menge der
 * Karten, die ein Abruf mit 404 beantwortet hat (E14). Nachgerechnet wird im Browser nichts.
 *
 * <p>Der Zusatz steht <b>ausserhalb</b> beider Bedienelemente: In einem von ihnen waere er Teil
 * dessen Namens, und ein Vorlesewerkzeug nennte den Weg in die Lauf-Ansicht „… nicht gefunden".
 */
function Paketzeile({
  lauf,
  projectId,
  paket,
  verschwunden,
  onKarteOeffnen,
}: Readonly<{ lauf: number; projectId: number; paket: PaketView } & Zeilenwege>) {
  const fehlt = !paket.cardExists || verschwunden.has(karteSchluessel(projectId, paket.cardNumber))
  return (
    <Box
      component="li"
      data-testid={`stand-paket-${lauf}-${paket.cardNumber}`}
      sx={{
        display: 'grid',
        gridTemplateColumns: '14px auto minmax(0,1fr) auto',
        alignItems: 'center',
        gap: '10px',
        px: '16px',
        py: '6px',
        '&:not(:last-child)': {
          borderBottom: `1px solid color-mix(in srgb, ${RAND} 55%, transparent)`,
        },
        '&:hover': { background: ZEILE_HOVER },
      }}
    >
      <Led melder={MELDER_JE_ZUSTAND[paket.state]} />
      {fehlt ? (
        <Typography sx={NUMMER_SX}>{`#${paket.cardNumber}`}</Typography>
      ) : (
        <Link
          component="button"
          type="button"
          underline="hover"
          aria-label={`Karte #${paket.cardNumber} öffnen: ${paket.title}`}
          onClick={() => onKarteOeffnen(projectId, paket.cardNumber)}
          sx={{ ...NUMMER_SX, textAlign: 'left', justifySelf: 'start' }}
        >
          {`#${paket.cardNumber}`}
        </Link>
      )}
      {/* Der Name nennt den Titel zuerst: Er steht sichtbar in der Zeile, und ein Name, der den
          sichtbaren Text nicht enthaelt, laesst sich per Sprache nicht ansprechen (WCAG 2.5.3). */}
      <Typography
        component={RouterLink}
        to={`/projects/${projectId}/nachtlauf?lauf=${lauf}`}
        aria-label={`${paket.title} — Run #${lauf} anzeigen`}
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          minWidth: 0,
          color: 'inherit',
          textDecoration: 'none',
          '&:hover > span:first-of-type': { textDecoration: 'underline' },
        }}
      >
        <Box
          component="span"
          sx={{
            fontSize: 12.5,
            flex: 1,
            minWidth: 0,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {paket.title}
        </Box>
        <Box component="span" sx={{ fontSize: 12, color: 'text.secondary', flex: 'none' }}>
          {nightRunZustandsText(paket.state, paket.errorClass ?? undefined)}
        </Box>
      </Typography>
      {fehlt && (
        <Typography sx={{ fontSize: 11.5, color: TEXT_SCHWACH }}>
          {`Karte #${paket.cardNumber} nicht gefunden`}
        </Typography>
      )}
    </Box>
  )
}

/** Die Nummernspalte — dieselbe Gestalt, ob sie Bedienelement ist oder reiner Text. */
const NUMMER_SX = {
  ...ZAHL,
  fontSize: 12,
  color: TEXT_SCHWACH,
} as const
