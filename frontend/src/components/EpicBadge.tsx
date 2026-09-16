import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import type { SxProps, Theme } from '@mui/material/styles'
import { epicColor, epicShortcode, epicTint } from '../lib/epicMeta'

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

/** Kürzel eines Vorhabens als Schild: Kürzel mit Rand in der Vorhaben-Farbe auf ihrer Tönung. */
export function EpicBadge({ epicId, title, shortcode, sx, onOpen }: Readonly<Props>) {
  const hue = epicColor(epicId)
  const label = epicShortcode(title, shortcode)
  // Die Fläche trägt einen eigenen Tint-Wert (#952): `hue` ist ein Variablen-Verweis, und ein
  // angehängtes Alpha-Suffix ergäbe ungültiges CSS — die Fläche verschwände ohne Fehler.
  // Schild des Leitstand-Entwurfs (`.schild`, Z. 780–786, #980): Rand und Schrift im Farbton, die
  // Fläche als eigene Tönung (#952) — ein Alpha-Suffix an einem Variablen-Verweis wäre ungültiges CSS.
  const grund = {
    width: 'fit-content',
    px: '6px',
    py: '1px',
    borderRadius: '5px',
    border: `1px solid ${hue}`,
    bgcolor: epicTint(epicId),
    flexShrink: 0,
  }
  const inhalt = (
    <Typography variant="caption" sx={{ fontSize: 10, fontWeight: 500, color: hue, lineHeight: 1.5 }}>
      {label}
    </Typography>
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
        // Ein natives <button> braechte Schrift des Browsers mit; ohne diese Neutralisierung saehe
        // der Badge mit onOpen anders aus als ohne. Rahmen und Fläche setzt `grund`.
        sx={{ ...grund, font: 'inherit', cursor: 'pointer', ...sx }}
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
