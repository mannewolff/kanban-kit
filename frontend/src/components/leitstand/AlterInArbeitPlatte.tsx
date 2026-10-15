import Box from '@mui/material/Box'
import type { AgingCard, AgingState, AgingWip } from '../../api/dashboard'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { alterText } from '../../lib/fluss'
import { ANZEIGE, NUR_LESER_SX, RAND, TEXT_SCHWACH, ZAHL } from '../../theme'
import { LeerSatz, Led, Platte, Taste } from './LeitstandBausteine'

/** Die Zustände der Platte in der Reihenfolge des Boards (Quelle #1751, AK 12). */
const SPALTEN: readonly (keyof AgingWip)[] = ['ready', 'inProgress', 'inReview']

/**
 * Die Platte „Alter in Arbeit“ (Issue #1777, Plan #1765 E15, E18, E19): je Zustand Ready, In Progress
 * und In Review die Arbeitspakete, die älteste oben, mit Nummer, Titel und Alter. Über der Schwelle
 * trägt eine Karte den Bernstein-Melder und für Vorlesewerkzeuge einen Satz dazu — die Farbe ist nie
 * der einzige Träger. Die drei Spalten entstehen aus einer Komponente über eine Liste (E19); ab `md`
 * nebeneinander, schmal untereinander. Die Kartennummer ist eine Taste und öffnet die Karte (E18).
 */
export function AlterInArbeitPlatte({
  daten,
  onOeffnen,
}: Readonly<{ daten: AgingWip; onOeffnen: (nummer: number) => void }>) {
  const t = useT().LEITSTAND_FLUSS
  return (
    <Platte titel={t.ALTER_TITEL} notiz={t.ALTER_NOTIZ}>
      <Box
        data-testid="alter-spalten"
        sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(3, minmax(0, 1fr))' } }}
      >
        {SPALTEN.map((spalte, i) => (
          <Spalte key={spalte} spalte={spalte} zustand={daten[spalte]} erste={i === 0} onOeffnen={onOeffnen} />
        ))}
      </Box>
    </Platte>
  )
}

/** Eine Spalte: Überschrift, darunter die Karten oder der Satz, dass keine dort liegt (AK 13). */
function Spalte({
  spalte,
  zustand,
  erste,
  onOeffnen,
}: Readonly<{ spalte: keyof AgingWip; zustand: AgingState; erste: boolean; onOeffnen: (nummer: number) => void }>) {
  const t = useT().LEITSTAND_FLUSS
  const name = t.BAND[spalte]
  // Die Trennlinie liegt schmal oben, ab `md` links — außer vor der ersten Spalte.
  const trenner = erste ? {} : { borderTop: { xs: 1, md: 0 }, borderLeft: { md: 1 }, borderColor: RAND }
  return (
    <Box data-testid={`alter-spalte-${spalte}`} sx={{ minWidth: 0, pb: '8px', ...trenner }}>
      <Box component="h3" sx={{ ...ANZEIGE, m: 0, px: '16px', pt: '12px', pb: '6px', fontSize: 12.5, fontWeight: 600 }}>
        {name}
      </Box>
      {zustand.cards.length === 0 ? (
        <LeerSatz testId={`alter-leer-${spalte}`}>{t.ALTER_LEER(name)}</LeerSatz>
      ) : (
        <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
          {zustand.cards.map((karte) => (
            <Zeile key={karte.id} karte={karte} onOeffnen={onOeffnen} />
          ))}
        </Box>
      )}
    </Box>
  )
}

/** Eine Karte: Nummer als Taste, Titel, Alter und — über der Schwelle — der Melder (AK 6, 7, 18). */
function Zeile({ karte, onOeffnen }: Readonly<{ karte: AgingCard; onOeffnen: (nummer: number) => void }>) {
  const t = useT().LEITSTAND_FLUSS
  const { sprache } = useSprache()
  return (
    <Box component="li" sx={{ display: 'flex', alignItems: 'center', gap: '8px', px: '16px', py: '4px', fontSize: 12 }}>
      <Taste ariaLabel={t.ALTER_KARTE_OEFFNEN(karte.number, karte.title)} onClick={() => onOeffnen(karte.number)}>
        <Box component="span" sx={ZAHL}>{`#${karte.number}`}</Box>
      </Taste>
      <Box
        component="span"
        sx={{ flex: 1, minWidth: 0, color: TEXT_SCHWACH, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
      >
        {karte.title}
      </Box>
      {karte.overThreshold && (
        <>
          <Led melder="bernst" />
          <Box component="span" sx={NUR_LESER_SX}>
            {t.ALTER_MELDER}
          </Box>
        </>
      )}
      <Box component="span" sx={{ ...ZAHL, color: 'text.secondary', whiteSpace: 'nowrap' }}>
        {alterText(karte.ageSeconds, karte.ageIsMinimum, sprache)}
      </Box>
    </Box>
  )
}
