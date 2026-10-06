import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import HighlightOffIcon from '@mui/icons-material/HighlightOff'
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty'
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline'
import Box from '@mui/material/Box'
import { useId, type ReactNode } from 'react'
import type { CardTitleView, ReleasePreparationResult, ReleasePreparationView } from '../../api/nightRuns'
import { tagZeit, type Melder } from '../../lib/leitstand'
import {
  ANZEIGE,
  ETIKETT,
  KLEIN_RADIUS,
  NUT_SX,
  TEXT_MATT,
  TEXT_SCHWACH,
  ZAHL,
} from '../../theme'
import { Led } from '../leitstand/LeitstandBausteine'

/** Der Satz bei Grün (Fachplan #1420): Veröffentlicht wird von Hand, die Kachel hat keinen Knopf dafür. */
export const VEROEFFENTLICHEN_SATZ = 'Der Stand kann von Hand veröffentlicht werden.'

/** Text, Symbol und Lampe je Ausgang — lesbar ohne Farbe (E6): Symbol und Text stehen neben der Lampe. */
const ERGEBNIS: Record<ReleasePreparationResult, { text: string; symbol: ReactNode; melder: Melder }> = {
  GREEN: { text: 'grün', symbol: <CheckCircleOutlineIcon fontSize="inherit" />, melder: 'gruen' },
  GREEN_PENDING: { text: 'grün, Prüfung offen', symbol: <HourglassEmptyIcon fontSize="inherit" />, melder: 'bernst' },
  RED: { text: 'rot', symbol: <HighlightOffIcon fontSize="inherit" />, melder: 'zinnob' },
  NOT_PREPARED: { text: 'nicht vorbereitet', symbol: <RemoveCircleOutlineIcon fontSize="inherit" />, melder: 'grau' },
}

/**
 * Die Morgenkachel „Veröffentlichung vorbereitet“ in der aufgeklappten Laufplatte (Issue #1458,
 * Plan #1447 E12): der Ausgang mit Lampe, Symbol und Text, die Kennung des Stands (`commitHash`),
 * die Version als Beschriftung, der Eingang der Meldung als „gemeldet um“ und die enthaltenen
 * Arbeitspakete mit Nummer und Titel — auch aus fremden Ketten, ohne Titel nur die Nummer.
 *
 * <p>Die Kachel liest nur; sie bietet bewusst keinen Knopf, der veröffentlicht (Fachplan #1420).
 */
export function MorgenkachelVeroeffentlichung({ meldung }: Readonly<{ meldung: ReleasePreparationView }>) {
  const ueberschriftId = useId()
  const ergebnis = ERGEBNIS[meldung.result]

  return (
    <Box
      component="section"
      aria-labelledby={ueberschriftId}
      data-testid="morgenkachel"
      sx={{ ...NUT_SX, borderRadius: `${KLEIN_RADIUS}px`, p: '12px 14px', display: 'flex', flexDirection: 'column', gap: 1 }}
    >
      <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', columnGap: 1.5, rowGap: 0.5 }}>
        <Box component="h4" id={ueberschriftId} sx={{ ...ANZEIGE, m: 0, fontSize: 14, fontWeight: 600 }}>
          Veröffentlichung vorbereitet
        </Box>
        {meldung.version !== null && (
          <Box component="span" data-testid="morgenkachel-version" sx={{ ...ZAHL, fontSize: 12, fontWeight: 600 }}>
            {meldung.version}
          </Box>
        )}
        <Box component="span" data-testid="morgenkachel-gemeldet" sx={{ ...ZAHL, fontSize: 11, color: TEXT_SCHWACH }}>
          {`gemeldet um ${tagZeit(meldung.receivedAt)}`}
        </Box>
      </Box>

      <Box data-testid="morgenkachel-ergebnis" sx={{ display: 'flex', alignItems: 'center', gap: 1, fontWeight: 600 }}>
        <Led melder={ergebnis.melder} />
        <Box
          component="span"
          aria-hidden
          data-testid={`morgenkachel-symbol-${meldung.result}`}
          sx={{ display: 'flex', fontSize: 16 }}
        >
          {ergebnis.symbol}
        </Box>
        <span>{ergebnis.text}</span>
      </Box>

      {meldung.commitHash !== null && (
        <Box data-testid="morgenkachel-stand" sx={{ fontSize: 13 }}>
          Stand:{' '}
          <Box component="span" sx={ZAHL}>
            {meldung.commitHash}
          </Box>
        </Box>
      )}

      {meldung.result === 'GREEN' && (
        <Box component="p" sx={{ m: 0, fontSize: 13 }}>
          {VEROEFFENTLICHEN_SATZ}
        </Box>
      )}
      {meldung.result === 'NOT_PREPARED' && (
        <Box component="p" sx={{ m: 0, fontSize: 13, color: TEXT_MATT }}>
          In diesem Lauf wurde keine Veröffentlichung vorbereitet.
        </Box>
      )}
      {meldung.result === 'GREEN_PENDING' && meldung.pending.length > 0 && (
        <Liste titel="Offene Prüfungen">
          {meldung.pending.map((eintrag, position) => (
            // Zwei offene Prüfungen dürfen gleich lauten; die Liste wird nicht umsortiert.
            <Box component="li" key={position /* NOSONAR */} sx={{ ...ZAHL, fontSize: 12.5 }}>
              {eintrag}
            </Box>
          ))}
        </Liste>
      )}
      {meldung.result === 'RED' && meldung.redCheck !== null && (
        <Box data-testid="morgenkachel-rotpruefung" sx={{ fontSize: 13 }}>
          Fehlgeschlagene Prüfung:{' '}
          <Box component="span" sx={ZAHL}>
            {meldung.redCheck}
          </Box>
        </Box>
      )}
      {meldung.result === 'RED' && meldung.redCards.length > 0 && (
        <Liste titel="Betroffene Karten">
          {meldung.redCards.map((karte, position) => (
            <Karte key={`${karte.number}-${position}`} karte={karte} />
          ))}
        </Liste>
      )}

      {meldung.cards.length > 0 ? (
        <Liste titel="Enthaltene Arbeitspakete">
          {meldung.cards.map((karte, position) => (
            <Karte key={`${karte.number}-${position}`} karte={karte} />
          ))}
        </Liste>
      ) : (
        <Box data-testid="morgenkachel-keine-pakete" sx={{ fontSize: 13, color: TEXT_MATT }}>
          Enthaltene Arbeitspakete: keine
        </Box>
      )}
    </Box>
  )
}

/** Eine benannte Liste der Kachel: Etikett darüber, die Einträge als `ul` mit demselben Namen. */
function Liste({ titel, children }: Readonly<{ titel: string; children: ReactNode }>) {
  const titelId = useId()
  return (
    <Box>
      <Box id={titelId} sx={{ ...ETIKETT, mb: '2px' }}>
        {titel}
      </Box>
      <Box
        component="ul"
        aria-labelledby={titelId}
        sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexDirection: 'column', gap: 0.25 }}
      >
        {children}
      </Box>
    </Box>
  )
}

/** Eine Karte mit Nummer und Titel; ohne Titel (Nummer nicht im Projekt) nur die Nummer. */
function Karte({ karte }: Readonly<{ karte: CardTitleView }>) {
  return (
    <Box component="li" sx={{ fontSize: 13 }}>
      <Box component="span" sx={ZAHL}>{`#${karte.number}`}</Box>
      {karte.title !== null && (
        <>
          {' '}
          <span>{karte.title}</span>
        </>
      )}
    </Box>
  )
}
