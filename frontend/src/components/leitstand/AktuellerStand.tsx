import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useId } from 'react'
import type { DisruptionView, LaufPaketeView, PaketView } from '../../api/plattformLeitstand'
import { laufgruppen, type Laufeintrag } from '../../lib/aktuellerStand'
import { melderAusBefund, MELDER_JE_ZUSTAND, type Projektgruppe } from '../../lib/leitstand'
import { nightRunZustandsText } from '../../lib/nightRunHandoff'
import { ANZEIGE, RAND, TEXT_SCHWACH } from '../../theme'
import { LaufArtSymbol } from './LaufArtSymbol'
import { Led, LeerSatz } from './LeitstandBausteine'

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
}: Readonly<{
  /** Die laufenden Runs; `null`, solange die erste Antwort fehlt. */
  laufende: DisruptionView[] | null
  /** Die gemeldeten Pakete je laufendem Run, in der Ordnung der Antwort. */
  gemeldetePakete: LaufPaketeView[]
}>) {
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
        <Standgruppe key={gruppe.projectId} gruppe={gruppe} jeLauf={jeLauf} />
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
}: Readonly<{
  gruppe: Projektgruppe<Laufeintrag<PaketView>>
  jeLauf: ReadonlyMap<number, DisruptionView>
}>) {
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
        <Standlauf key={eintrag.nightRunId} eintrag={eintrag} lauf={jeLauf.get(eintrag.nightRunId)!} />
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
}: Readonly<{ eintrag: Laufeintrag<PaketView>; lauf: DisruptionView }>) {
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
          <Paketzeile key={p.cardNumber} lauf={eintrag.nightRunId} paket={p} />
        ))}
      </Box>
    </Box>
  )
}

/**
 * Eine Paketzeile: Melder, Nummer, Titel und das Zustandswort.
 *
 * <p><b>Das Wort steht sichtbar neben dem Melder</b> (AK 5) — nie nur die Farbe. Gebildet wird es
 * mit {@link nightRunZustandsText}, derselben Funktion wie in der Lauf-Ansicht; `?? undefined`,
 * weil die Antwort eine fehlende Fehlerklasse als `null` fuehrt und die Funktion `undefined`
 * annimmt.
 *
 * <p>Die Nummer ist hier reiner Text. Die beiden Bedienelemente der Zeile — Nummer oeffnet die
 * Karte, Zeile fuehrt in die Lauf-Ansicht — kommen in Issue #1174 dazu.
 */
function Paketzeile({ lauf, paket }: Readonly<{ lauf: number; paket: PaketView }>) {
  return (
    <Box
      component="li"
      data-testid={`stand-paket-${lauf}-${paket.cardNumber}`}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        px: '16px',
        py: '6px',
        '&:not(:last-child)': {
          borderBottom: `1px solid color-mix(in srgb, ${RAND} 55%, transparent)`,
        },
      }}
    >
      <Led melder={MELDER_JE_ZUSTAND[paket.state]} />
      <Typography sx={{ fontSize: 12, fontFamily: 'monospace', color: TEXT_SCHWACH }}>
        {`#${paket.cardNumber}`}
      </Typography>
      <Typography sx={{ fontSize: 12.5, flex: 1, minWidth: 0 }}>{paket.title}</Typography>
      <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>
        {nightRunZustandsText(paket.state, paket.errorClass ?? undefined)}
      </Typography>
    </Box>
  )
}
