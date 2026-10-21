import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useT } from '../../i18n/SpracheContext'
import { ETIKETT, MELDER, PLATTE_FUSS, RAND, ZAHL } from '../../theme'

/** Eine Angabe der Fußzeile: ihre Benennung, ihr Wert und ob der Wert ein Vorbehalt ist. */
export interface Fussangabe {
  label: string
  wert: string
  /**
   * `true` gibt dem Wert die Warnfarbe des Entwurfs (`dd.warn`). Sie gilt einem Vorbehalt, nicht
   * einer fehlenden Angabe: „nicht angegeben" ist keine Warnung, sondern eine Auskunft.
   */
  vorbehalt?: boolean
}

/**
 * Die Fußzeile eines Laufs (#918) — die Angaben unter den Vorgängen, als Beschreibungsliste in der
 * Gestalt des Entwurfs.
 *
 * <p>Beide Lauf-Arten führen andere Angaben: die Kette ihre Zeitvorgaben, ihr Kostenbudget und die
 * höchsten Kosten eines Vorgangs, der Umsetzungs-Lauf das Ergebnis der Nacht und seinen teuersten
 * Vorgang. Welche es sind, entscheidet die Seite.
 *
 * <p><b>Die Zeile „Einlieferung" steht zuletzt</b> (Plan #1882 E10): Woher der Stand kam und wann
 * er eingeliefert und zuletzt gemeldet wurde, stand bis dahin als Marken im Kopf. Es sind Angaben
 * über den Lauf, nicht über seinen Zustand — sie gehören hierher und nicht neben die Zustandsmarken.
 * Sie ersetzt die frühere Angabe „Herkunft des Stands", damit je Lauf eine Angabe die Herkunft nennt.
 *
 * <p><b>Als `<dl>`</b> und nicht als Reihe von Kästen: Jede Angabe ist ein Paar aus Benennung und
 * Wert, und das ist genau die Bedeutung einer Beschreibungsliste — ein Vorlesewerkzeug liest sie
 * dann als Paare statt als lose Textfolge.
 */
export function NachtlaufFuss({
  angaben,
  einlieferung = [],
  testId,
}: Readonly<{
  angaben: readonly Fussangabe[]
  /** Die Einlieferungsangaben eines Laufs, in ihrer Reihenfolge; leer, so entfällt die Zeile. */
  einlieferung?: readonly string[]
  testId: string
}>) {
  const t = useT().NACHTLAUF_SEITE.FUSS
  return (
    <Box
      component="dl"
      data-testid={testId}
      // Raster der Vorlage `docs/entwurf-lauf-ansicht.html` (Z. 420–427): fünf Spalten auf dem
      // Fußgrund der Platte; schmal zwei, damit lange Werte nicht in Einzelwörter zerfallen.
      sx={{
        marginTop: 0,
        padding: '12px 18px 14px',
        borderTop: `1px solid ${RAND}`,
        background: PLATTE_FUSS,
        display: 'grid',
        gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'repeat(5, minmax(0, 1fr))' },
        gap: '14px 18px',
        marginBottom: 0,
      }}
    >
      {angaben.map((angabe) => (
        <Box key={angabe.label} data-testid={`fussangabe-${angabe.label}`} sx={{ minWidth: 0 }}>
          <Box component="dt" sx={ETIKETT}>
            {angabe.label}
          </Box>
          <Typography
            component="dd"
            sx={{
              ...ZAHL,
              margin: '3px 0 0',
              fontSize: 12,
              color: angabe.vorbehalt === true ? MELDER.bernst : 'text.secondary',
            }}
          >
            {angabe.wert}
          </Typography>
        </Box>
      ))}
      {einlieferung.length > 0 && (
        // Die Zeile ist lang und darf es sein — sie nimmt den Raum zweier Angaben (Vorlage Z. 427).
        <Box data-testid="fuss-einlieferung" sx={{ gridColumn: 'span 2', minWidth: 0 }}>
          <Box component="dt" sx={ETIKETT}>
            {t.EINLIEFERUNG}
          </Box>
          <Typography component="dd" sx={{ margin: '3px 0 0', fontSize: 12, color: 'text.secondary' }}>
            {einlieferung.join(' · ')}
          </Typography>
        </Box>
      )}
    </Box>
  )
}
