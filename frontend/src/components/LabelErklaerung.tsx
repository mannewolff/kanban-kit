import Box from '@mui/material/Box'
import type { TooltipProps } from '@mui/material/Tooltip'
import Typography, { type TypographyProps } from '@mui/material/Typography'
import { cloneElement, type ReactElement } from 'react'
import { useKitNamen } from '../api/kitNamen'
import { useT } from '../i18n/SpracheContext'
import { labelErklaerung, type ErklaerungsTeil } from '../lib/labelErklaerung'
import { Erklaerung } from './Erklaerung'

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

/** Die Erklärung eines Systemlabels, oder `null`, solange das Register lädt, fehlt oder das Label keins ist. */
export function useLabelErklaerung(name: string): ErklaerungsTeil[] | null {
  const t = useT()
  const kitNamen = useKitNamen()
  return kitNamen.status === 'fertig' ? labelErklaerung(kitNamen.daten, name, t) : null
}

/**
 * Legt die Erklärung eines Systemlabels als Tooltip um das Label (Issue #1672, Plan #1668 E4).
 *
 * Die Erklärung beschreibt das Label, sie benennt es nicht — darum `describeChild`, wie in der
 * Stufenleiste. Ein Systemlabel wird mit `tabIndex={0}` fokussierbar, damit die Erklärung auch mit
 * der Tastatur erscheint. Ein anderes Label kommt unverändert zurück, ebenso jedes Label, solange
 * das Namen-Register lädt oder nicht geladen werden konnte (E10). `open` steuert das Tooltip von
 * außen, etwa in einer Auswahlliste; ohne `open` öffnet es bei Maus und Fokus selbst.
 *
 * Auf dem Tablet quer öffnet Antippen die Erklärung über {@link Erklaerung} (Plan #1707, E7): Das
 * Antippen geht nicht an die Eltern weiter, ein Label auf der Karte öffnet so nicht die Karte.
 *
 * `eigenerFokus` lässt den `tabIndex` des Kindes unangetastet — für Menüpunkte, Optionen und
 * Knöpfe, deren Fokus ihr Behälter oder sie selbst führen (Issue #1673, E6–E8). `autoFocus` und
 * `tabIndex` gehen an das Kind weiter: Ein `MenuList` klont seine direkten Kinder mit genau diesen
 * beiden, um den ersten Menüpunkt zu fokussieren.
 */
export function LabelErklaerung({
  name,
  open,
  placement,
  eigenerFokus = false,
  children,
  ...weiter
}: Readonly<{
  name: string
  open?: boolean
  placement?: TooltipProps['placement']
  eigenerFokus?: boolean
  children: ReactElement
  autoFocus?: boolean
  tabIndex?: number
}>) {
  const teile = useLabelErklaerung(name)
  if (teile === null) return cloneElement(children, weiter)
  return (
    <Erklaerung title={<ErklaerungsText teile={teile} />} describeChild open={open} placement={placement}>
      {cloneElement(children, eigenerFokus ? weiter : { tabIndex: 0, ...weiter })}
    </Erklaerung>
  )
}
