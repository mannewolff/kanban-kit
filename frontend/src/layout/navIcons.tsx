import SvgIcon, { type SvgIconProps } from '@mui/material/SvgIcon'
import type { ReactNode } from 'react'

/**
 * Symbole der Navigationsschiene, übernommen aus dem Leitstand-Entwurf
 * (`docs/entwurf-leitstand.html`, HTML Z. 1119–1154): 16 × 16, Strich statt Fläche, Farbe aus
 * `currentColor`. Einträge ohne Vorlage im Entwurf tragen weiter ein MUI-Symbol.
 */
export function strichSymbol(name: string, pfade: ReactNode) {
  function StrichSymbol(props: SvgIconProps) {
    return (
      <SvgIcon viewBox="0 0 16 16" {...props} sx={{ fill: 'none', ...props.sx }}>
        {pfade}
      </SvgIcon>
    )
  }
  StrichSymbol.displayName = name
  return StrichSymbol
}

/** Strichstärke und Farbe aller Symbole — auch die der Laufarten (#1141) folgen ihr. */
export const strich = { stroke: 'currentColor', strokeWidth: 1.5 } as const

export const LeitstandSymbol = strichSymbol(
  'LeitstandSymbol',
  <path d="M2 12.5V9m4 3.5V5m4 7.5V7.5m4 5V3" {...strich} strokeWidth={1.7} strokeLinecap="round" />,
)

export const BoardSymbol = strichSymbol(
  'BoardSymbol',
  <>
    <rect x="2" y="2.5" width="3.4" height="11" rx="1" {...strich} />
    <rect x="6.3" y="2.5" width="3.4" height="7.5" rx="1" {...strich} />
    <rect x="10.6" y="2.5" width="3.4" height="9.5" rx="1" {...strich} />
  </>,
)

export const ListeSymbol = strichSymbol(
  'ListeSymbol',
  <path d="M2.5 4h11M2.5 8h11M2.5 12h7" {...strich} strokeLinecap="round" />,
)

export const VorhabenSymbol = strichSymbol(
  'VorhabenSymbol',
  <>
    <path d="M8 2v5.5L12 10" {...strich} strokeLinecap="round" />
    <circle cx="8" cy="8" r="6" {...strich} />
  </>,
)

export const IdeenSymbol = strichSymbol(
  'IdeenSymbol',
  <path
    d="M6 13h4M8 2a4.2 4.2 0 0 0-2.4 7.7c.3.3.4.6.4 1v.3h4v-.3c0-.4.1-.7.4-1A4.2 4.2 0 0 0 8 2Z"
    {...strich}
    strokeLinejoin="round"
  />,
)

export const NachtlaeufeSymbol = strichSymbol(
  'NachtlaeufeSymbol',
  <path d="M13 9.6A5.6 5.6 0 0 1 6.4 3 5.6 5.6 0 1 0 13 9.6Z" {...strich} strokeLinejoin="round" />,
)

export const MitgliederSymbol = strichSymbol(
  'MitgliederSymbol',
  <>
    <circle cx="8" cy="5.5" r="2.4" {...strich} />
    <path d="M3 13.2c.7-2.2 2.6-3.4 5-3.4s4.3 1.2 5 3.4" {...strich} strokeLinecap="round" />
  </>,
)

export const RollenSymbol = strichSymbol(
  'RollenSymbol',
  <path d="M8 1.8 13.2 4v3.7c0 3.2-2.1 5.6-5.2 6.5-3.1-.9-5.2-3.3-5.2-6.5V4L8 1.8Z" {...strich} strokeLinejoin="round" />,
)

/** Marke (HTML Z. 1105–1111): drei Balken in abnehmender Höhe, weiß auf dem Kupfer-Mal. */
export function MarkenSymbol() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true" style={{ display: 'block' }}>
      <rect x="1.5" y="2" width="3.6" height="12" rx="1.2" fill="#fff" fillOpacity=".92" />
      <rect x="6.2" y="2" width="3.6" height="8" rx="1.2" fill="#fff" fillOpacity=".72" />
      <rect x="10.9" y="2" width="3.6" height="5" rx="1.2" fill="#fff" fillOpacity=".5" />
    </svg>
  )
}
