import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import { KLEIN_RADIUS, NUT, RAND, SCHATTEN_NUTE, SCHATTEN_PLATTE, TASTE_SX, ZAHL } from '../../theme'

/**
 * Der Befund eines Abbruchs mit dem Knopf, der ihn in die Zwischenablage legt (#988, Vorlage
 * `docs/mockup-nachtlauf-lauf.html` Z. 419–425).
 *
 * <p><b>Der Text steht neben dem Knopf, nie hinter ihm.</b> Er speist sich aus Protokollauszügen,
 * also aus Fremdtext (Claude-Ausgaben, Ergebnisse fremder Werkzeuge) — ein unsichtbar kopierter
 * Text wäre ein Weg von fremdem Text in die eigene Entwicklungssitzung. Seit #988 klappt er an der
 * Zeile seines Vorgangs auf (Entscheidung Manne 2026-09-17); wer den Knopf sieht, sieht damit auch,
 * was er kopiert.
 *
 * <p><b>Als `<pre>` und nie über den Markdown-Renderer</b> (`CLAUDE-security.md`): Der Wert ist
 * Fremdtext und wird als reiner Text gerendert. Der Text bleibt ungekürzt — sonst wanderte ein
 * halbes Rohprotokoll gekürzt in die Zwischenablage, und der Leser hielte es für das Ganze.
 */
export function NachtlaufBefund({
  cardNumber,
  text,
  onKopieren,
}: Readonly<{ cardNumber: number; text: string; onKopieren: () => void }>) {
  return (
    <Box sx={{ display: 'flex', gap: '10px', alignItems: 'flex-start' }}>
      <Box
        component="pre"
        data-testid={`befund-${cardNumber}`}
        aria-label={`Übernahmetext zu Karte #${cardNumber}`}
        // Der Kasten scrollt bei langen Auszügen; ohne Fokus wäre sein Inhalt mit der Tastatur
        // nicht erreichbar, und ein Vorlesewerkzeug läse seine Beschriftung nie.
        tabIndex={0}
        sx={{
          ...ZAHL,
          flex: 1,
          minWidth: 0,
          m: 0,
          maxHeight: 260,
          overflow: 'auto',
          fontSize: 11.5,
          lineHeight: 1.55,
          color: 'text.secondary',
          bgcolor: NUT,
          border: `1px solid ${RAND}`,
          boxShadow: SCHATTEN_NUTE,
          borderRadius: '8px',
          px: '11px',
          py: '9px',
          whiteSpace: 'pre-wrap',
        }}
      >
        {text}
      </Box>
      <ButtonBase
        aria-label={`Übernahmetext zu Karte #${cardNumber} kopieren`}
        onClick={onKopieren}
        sx={{
          ...TASTE_SX,
          flex: 'none',
          display: 'inline-flex',
          alignItems: 'center',
          gap: '7px',
          fontSize: 12.5,
          fontWeight: 600,
          borderRadius: `${KLEIN_RADIUS + 3}px`,
          px: '13px',
          py: '7px',
          transition: 'transform .1s ease, box-shadow .14s ease',
          '&:hover': { boxShadow: SCHATTEN_PLATTE },
          '&:active': { transform: 'translateY(1px)', boxShadow: SCHATTEN_NUTE },
          '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
        }}
      >
        <ContentCopyIcon sx={{ fontSize: 13 }} />
        Kopieren
      </ButtonBase>
    </Box>
  )
}
