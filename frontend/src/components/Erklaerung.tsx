import ClickAwayListener from '@mui/material/ClickAwayListener'
import Tooltip, { type TooltipProps } from '@mui/material/Tooltip'
import {
  cloneElement,
  createContext,
  useContext,
  useId,
  useMemo,
  useState,
  type Dispatch,
  type MouseEvent,
  type ReactElement,
  type ReactNode,
  type SetStateAction,
} from 'react'
import { useTabletQuer } from '../lib/tabletQuer'

type OffenZustand = Readonly<{ offen: string | null; setOffen: Dispatch<SetStateAction<string | null>> }>

const ErklaerungOffenKontext = createContext<OffenZustand | null>(null)

/**
 * Hält höchstens eine Erklärung offen (Plan #1707, E7): Öffnet der Finger eine zweite, schließt die
 * erste. Hängt in `AppShell`; ohne Provider führt jede Erklärung ihren eigenen Zustand.
 */
export function ErklaerungOffenProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [offen, setOffen] = useState<string | null>(null)
  const wert = useMemo(() => ({ offen, setOffen }), [offen])
  return <ErklaerungOffenKontext.Provider value={wert}>{children}</ErklaerungOffenKontext.Provider>
}

/** Offen-Zustand der Erklärung `id`, aus dem Provider oder, ohne ihn, aus eigenem Zustand. */
function useErklaerungOffen(id: string) {
  const geteilt = useContext(ErklaerungOffenKontext)
  const [eigen, setEigen] = useState<string | null>(null)
  const { offen, setOffen } = geteilt ?? { offen: eigen, setOffen: setEigen }
  return {
    offen: offen === id,
    umschalten: () => setOffen((aktuell) => (aktuell === id ? null : id)),
    // Gerufen nur vom ClickAwayListener, und der lauscht nur, solange diese Erklärung offen ist.
    schliessen: () => setOffen(null),
  }
}

/**
 * Die Erklärung im Tablet-Modus (Plan #1707, E8): ein gesteuertes Tooltip, das nur per Antippen
 * öffnet. Hover, Fokus und langes Berühren sind lokal abgeschaltet — langes Antippen bleibt dem
 * Ziehen vorbehalten. Erneutes Antippen oder Antippen daneben schließt. Das Antippen geht nicht an
 * die Eltern weiter: Ein Label öffnet so nicht die Karte, ein Info-Symbol wählt nicht den Eintrag.
 *
 * `ausklappbar` setzt `aria-expanded` am Kind — für das Info-Symbol, einen Knopf, der die Erklärung
 * aufklappt.
 */
export function TabletErklaerung({
  title,
  children,
  ausklappbar = false,
  ...tooltip
}: Readonly<Omit<TooltipProps, 'open'> & { ausklappbar?: boolean }>) {
  const { offen, umschalten, schliessen } = useErklaerungOffen(useId())
  const kind = children as ReactElement<{ onClick?: (e: MouseEvent<HTMLElement>) => void; 'aria-expanded'?: boolean }>
  const eigenerKlick = kind.props.onClick
  return (
    // Lauscht nur, solange die Erklärung offen ist — ein Board trägt viele Labels. `onTouchEnd`, weil
    // iPadOS-Safari ein Antippen auf eine Fläche ohne Klick-Handler nicht als Klick meldet.
    <ClickAwayListener onClickAway={schliessen} mouseEvent={offen ? 'onClick' : false} touchEvent={offen ? 'onTouchEnd' : false}>
      <Tooltip {...tooltip} title={title} open={offen} disableTouchListener disableHoverListener disableFocusListener>
        {cloneElement(kind, {
          ...(ausklappbar ? { 'aria-expanded': offen } : {}),
          onClick: (e: MouseEvent<HTMLElement>) => {
            e.stopPropagation()
            eigenerKlick?.(e)
            umschalten()
          },
        })}
      </Tooltip>
    </ClickAwayListener>
  )
}

/** Ein MUI-Tooltip mit den Props des Aufrufers, wie es heute an der Stelle steht. */
type TooltipErklaerung = TooltipProps & { nativ?: never }

/** Ein natives `title`, wie es heute an der Stelle steht. */
type NativeErklaerung = { nativ: true; title: string; children: ReactElement }

/**
 * Eine Erklärung, die am Desktop beim Darüberfahren erscheint und auf dem Tablet quer per Antippen
 * (Plan #1707, E7/E8).
 *
 * Außerhalb des Tablet-Modus rendert der Baustein genau das heutige Element: das MUI-`Tooltip` mit
 * unveränderten Props, mit `nativ` das native `title`. Im Tablet-Modus wird daraus eine
 * {@link TabletErklaerung}. Ein Tooltip, dessen `open` der Aufrufer steuert, bleibt in seiner Hand —
 * etwa eine Option, deren Erklärung die Auswahlliste öffnet.
 */
export function Erklaerung(props: Readonly<TooltipErklaerung | NativeErklaerung>) {
  const tabletQuer = useTabletQuer()
  if (props.nativ) {
    const { title, children } = props
    return tabletQuer ? <TabletErklaerung title={title}>{children}</TabletErklaerung> : cloneElement(children, { title })
  }
  if (!tabletQuer || props.open !== undefined) return <Tooltip {...props} />
  return <TabletErklaerung {...props} />
}
