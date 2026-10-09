import Box from '@mui/material/Box'
import { useId } from 'react'
import { laufDauer } from '../../lib/leitstand'
import { geschaetztGespart, NICHT_GEMELDET, type NightRunStuckAngaben } from '../../lib/nightRunHandoff'
import { ANZEIGE, TEXT_SCHWACH, ZAHL } from '../../theme'
import { Led } from './LeitstandBausteine'

/** Eine Dauer im Format des Verbrauchs („1 h 0 min", „40 min") oder {@link NICHT_GEMELDET}. */
const dauer = (ms: number | null | undefined): string => (ms == null ? NICHT_GEMELDET : laufDauer(ms))

/**
 * Die Angaben eines festgefahrenen Pakets (Issue #1552, Plan #1547 E14, E17): Prüfung, Fehler,
 * Versuche, Laufzeit bis Abbruch, Zeitgrenze der Sitzung und geschätzte gesparte Zeit — jede steht
 * immer da, eine fehlende als „nicht gemeldet": Gerade dass das Kit etwas nicht gemeldet hat, ist
 * hier eine Aussage (AK 2 und 3 der fachlichen Quelle #1546).
 *
 * <p>Zustandslos und für beide Lagen: `stuck` darf fehlende Angaben als fehlendes Feld (frisch
 * eingelesen) oder als `null` (Lesesicht des Servers) tragen. Die gesparte Zeit rechnet
 * {@link geschaetztGespart} hier selbst, damit ein noch nicht eingelieferter Lauf dieselbe Zahl
 * zeigt wie ein aufbewahrter (E17).
 *
 * <p>Die Überschrift trägt das Wort „Festgefahren" neben der roten Lampe — die Unterscheidung von
 * den übrigen roten Ausgängen hängt nicht an der Farbe.
 *
 * <p>Fehler und Prüfung sind Fremdtext des Kits und stehen als reiner Text da (CLAUDE-security.md).
 */
export function FestgefahrenAngaben({
  stuck,
  durationMs,
  ebene = 'h4',
}: Readonly<{
  stuck: NightRunStuckAngaben | null | undefined
  /** Laufzeit des Pakets bis zum Abbruch. */
  durationMs: number | null | undefined
  /** Die Ebene der Überschrift, passend zur Gliederung der Seite. */
  ebene?: 'h3' | 'h4'
}>) {
  const ueberschriftId = useId()
  const angaben = stuck ?? {}
  const zeilen: readonly [string, string][] = [
    ['Prüfung', angaben.check ?? NICHT_GEMELDET],
    ['Fehler', angaben.error ?? NICHT_GEMELDET],
    ['Versuche', angaben.attempts == null ? NICHT_GEMELDET : String(angaben.attempts)],
    ['Laufzeit bis Abbruch', dauer(durationMs)],
    ['Zeitgrenze der Sitzung', dauer(angaben.sessionLimitMs)],
    ['geschätzte gesparte Zeit', dauer(geschaetztGespart(durationMs, angaben.sessionLimitMs))],
  ]

  return (
    <Box component="section" aria-labelledby={ueberschriftId} sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Led melder="zinnob" />
        <Box component={ebene} id={ueberschriftId} sx={{ ...ANZEIGE, m: 0, fontSize: 13, fontWeight: 600 }}>
          Festgefahren
        </Box>
      </Box>
      <Box
        component="dl"
        sx={{ m: 0, display: 'grid', gridTemplateColumns: 'max-content minmax(0, 1fr)', columnGap: 1.5, rowGap: 0.25, fontSize: 12 }}
      >
        {zeilen.map(([begriff, wert]) => (
          <Box key={begriff} sx={{ display: 'contents' }}>
            <Box component="dt" sx={{ color: TEXT_SCHWACH }}>
              {begriff}
            </Box>
            <Box
              component="dd"
              sx={{ m: 0, ...ZAHL, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', color: wert === NICHT_GEMELDET ? TEXT_SCHWACH : 'text.primary' }}
            >
              {wert}
            </Box>
          </Box>
        ))}
      </Box>
    </Box>
  )
}
