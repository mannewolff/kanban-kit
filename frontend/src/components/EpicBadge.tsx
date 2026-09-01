import Box from '@mui/material/Box'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import type { SxProps, Theme } from '@mui/material/styles'
import { epicColor, epicShortcode } from '../lib/epicMeta'

interface Props {
  epicId: number
  title: string
  shortcode: string | null
  sx?: SxProps<Theme>
  /** Gesetzt: Der Badge wird ein Bedienelement, das zum Vorhaben führt. Fehlt sie: reine Anzeige. */
  onOpen?: () => void
}

/**
 * Tasten, die ein Bedienelement auslösen. Nur sie hält der Badge von der Ebene ringsum fern
 * (Issue #689): Auch eine Listenzeile öffnet auf Enter/Leertaste ihre Karte, und ohne diesen Stopp
 * gingen Vorhaben und Karte zugleich auf. Andere Tasten laufen weiter — sonst wären globale
 * Tastenkürzel taub, solange der Badge den Fokus hält.
 */
const AKTIVIERUNGSTASTEN = new Set(['Enter', ' '])

/** Kürzel-Badge eines Epics: farbiger Punkt + Kürzel auf zartem Grund in der Epic-Farbe (Toolbox-Stil). */
export function EpicBadge({ epicId, title, shortcode, sx, onOpen }: Readonly<Props>) {
  const hue = epicColor(epicId)
  const label = epicShortcode(title, shortcode)
  const grund = { width: 'fit-content', px: 0.75, py: 0.25, borderRadius: 1, bgcolor: `${hue}22`, flexShrink: 0 }
  const inhalt = (
    <>
      <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: hue, flexShrink: 0 }} />
      <Typography variant="caption" sx={{ fontWeight: 700, color: hue, lineHeight: 1 }}>
        {label}
      </Typography>
    </>
  )

  if (onOpen) {
    return (
      // `component="button"` rendert ein echtes <button>: per Tab erreichbar und per Enter
      // ausloesbar. Ein onClick auf der Anzeigekomponente kaeme durch alle Gates — jsx-a11y prueft
      // nur DOM-Elemente in Kleinschreibung, keine MUI-Komponenten — und waere per Tastatur
      // trotzdem unerreichbar (dieselbe Begruendung wie am Anforderungs-Verweis, Plan #637 E6).
      <Stack
        component="button"
        type="button"
        direction="row"
        alignItems="center"
        spacing={0.5}
        title={title}
        aria-label={`Vorhaben ${label} öffnen`}
        onClick={(e) => {
          // Ohne stopPropagation traefe derselbe Klick den Handler der umgebenden Ebene
          // (Kachel, Listenzeile) und oeffnete Vorhaben und Karte zugleich.
          e.stopPropagation()
          onOpen()
        }}
        // Die Auslösung per Tastatur macht der Browser selbst (natives <button>); hier wird nur
        // verhindert, dass derselbe Tastendruck zusätzlich die Ebene ringsum auslöst.
        onKeyDown={(e) => {
          if (AKTIVIERUNGSTASTEN.has(e.key)) e.stopPropagation()
        }}
        // Ein natives <button> braechte Rahmen, Schrift und Hintergrund des Browsers mit; ohne
        // diese Neutralisierung saehe der Badge mit onOpen anders aus als ohne.
        sx={{ ...grund, border: 0, font: 'inherit', cursor: 'pointer', ...sx }}
      >
        {inhalt}
      </Stack>
    )
  }

  return (
    <Stack
      direction="row"
      alignItems="center"
      spacing={0.5}
      title={title}
      aria-label={`Vorhaben ${label}`}
      sx={{ ...grund, ...sx }}
    >
      {inhalt}
    </Stack>
  )
}
