import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { HerkunftsGlied } from '../../api/cards'
import { useT } from '../../i18n/SpracheContext'
import { TEXT_SCHWACH, ZAHL } from '../../theme'
import { Erklaerung } from '../Erklaerung'

/** Eine Zeile des Hinweises: „#N · Titel“, archiviert mit Vermerk, eine Lücke mit ihrem Text. */
function gliedText(glied: HerkunftsGlied, t: ReturnType<typeof useT>): string {
  if (glied.luecke === 'PAPIERKORB') return t.BOARD.HERKUNFT_PAPIERKORB(glied.number)
  if (glied.luecke === 'GELOESCHT') return t.BOARD.HERKUNFT_GELOESCHT(glied.number)
  const zeile = `#${glied.number} · ${glied.title}`
  return glied.archived ? `${zeile} ${t.BOARD.HERKUNFT_ARCHIVIERT}` : zeile
}

/** Der Inhalt des Hinweises: die Überschrift, dann die Kette, nächster Vorfahr zuerst. */
function HerkunftsText({ herkunft }: Readonly<{ herkunft: readonly HerkunftsGlied[] }>) {
  const t = useT()
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.25 }}>
      <Typography component="p" variant="caption" sx={{ m: 0, fontWeight: 600 }}>
        {t.BOARD.HERKUNFT_TITEL}
      </Typography>
      {herkunft.length === 0 ? (
        <Typography component="p" variant="caption" sx={{ m: 0 }}>
          {t.BOARD.HERKUNFT_KEINE}
        </Typography>
      ) : (
        herkunft.map((glied) => (
          <Typography key={glied.number} component="p" variant="caption" sx={{ m: 0 }}>
            {gliedText(glied, t)}
          </Typography>
        ))
      )}
    </Box>
  )
}

/**
 * Die Kartennummer `#N` im Kopf der Kachel, mit der Herkunft der Karte als Hinweis (Issue #1930).
 *
 * Der Hinweis beschreibt die Nummer, er benennt sie nicht — darum `describeChild`, wie bei den
 * Labels. Die Nummer wird fokussierbar, damit er auch mit der Tastatur erscheint. Einen eigenen Klick
 * trägt sie nicht: Ein Klick geht an die Kachel weiter und öffnet die Karte. Auf dem Tablet quer
 * öffnet Antippen über {@link Erklaerung} den Hinweis und reicht nicht weiter. Bei `herkunft ===
 * null` hat der Server die Kette nicht berechnet, und die Nummer bleibt ohne Hinweis.
 */
export function KartenHerkunft({ number, herkunft }: Readonly<{ number: number; herkunft: HerkunftsGlied[] | null }>) {
  const nummer = (tabIndex?: number) => (
    <Box component="span" tabIndex={tabIndex} sx={{ ...ZAHL, fontSize: 11, color: TEXT_SCHWACH }}>{`#${number}`}</Box>
  )
  if (herkunft === null) return nummer()
  return (
    <Erklaerung title={<HerkunftsText herkunft={herkunft} />} describeChild>
      {nummer(0)}
    </Erklaerung>
  )
}
