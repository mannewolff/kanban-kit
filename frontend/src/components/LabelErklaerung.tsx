import Box from '@mui/material/Box'
import Tooltip from '@mui/material/Tooltip'
import Typography, { type TypographyProps } from '@mui/material/Typography'
import { cloneElement, type ReactElement } from 'react'
import { useKitNamen } from '../api/kitNamen'
import { useT } from '../i18n/SpracheContext'
import { labelErklaerung, type ErklaerungsTeil } from '../lib/labelErklaerung'

/** Die Teile einer Erklärung als kurze Absätze, in der Reihenfolge, in der sie kommen. */
export function ErklaerungsText({
  teile,
  ...typo
}: Readonly<{ teile: readonly ErklaerungsTeil[] } & Pick<TypographyProps, 'variant' | 'color'>>) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
      {teile.map((teil) => (
        <Typography key={teil.art} component="p" variant="caption" {...typo} sx={{ m: 0 }}>
          {teil.text}
        </Typography>
      ))}
    </Box>
  )
}

/**
 * Legt die Erklärung eines Systemlabels als Tooltip um das Label (Issue #1672, Plan #1668 E4).
 *
 * Die Erklärung beschreibt das Label, sie benennt es nicht — darum `describeChild`, wie in der
 * Stufenleiste. Ein Systemlabel wird mit `tabIndex={0}` fokussierbar, damit die Erklärung auch mit
 * der Tastatur erscheint. Ein anderes Label kommt unverändert zurück, ebenso jedes Label, solange
 * das Namen-Register lädt oder nicht geladen werden konnte (E10). `open` steuert das Tooltip von
 * außen, etwa in einem Menü; ohne `open` öffnet es bei Maus und Fokus selbst.
 */
export function LabelErklaerung({
  name,
  open,
  children,
}: Readonly<{ name: string; open?: boolean; children: ReactElement }>) {
  const t = useT()
  const kitNamen = useKitNamen()
  const teile = kitNamen.status === 'fertig' ? labelErklaerung(kitNamen.daten, name, t) : null
  if (teile === null) return children
  return (
    <Tooltip title={<ErklaerungsText teile={teile} />} describeChild open={open}>
      {cloneElement(children, { tabIndex: 0 })}
    </Tooltip>
  )
}
