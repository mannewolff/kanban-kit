import useMediaQuery from '@mui/material/useMediaQuery'
import { createContext, createElement, useContext, type ReactNode } from 'react'

/**
 * Betriebsart „Tablet im Querformat“ (Plan #1707, E1): per Finger bedient, ohne Hover, quer und
 * mindestens 1000 px breit. Ein Laptop mit Touchscreen meldet Maus oder Trackpad als primär und
 * bleibt Desktop, ein Handy quer liegt unter 1000 px, ein Tablet hochkant scheitert an `orientation`.
 *
 * Folge: Ein Tablet mit angeschlossener Maus oder angeschlossenem Trackpad meldet `pointer: fine`
 * und zeigt den Desktop — nach dem Nicht-Ziel „Mischbedienung“ ohne Zusage.
 */
export const TABLET_QUER_QUERY = '(hover: none) and (pointer: coarse) and (orientation: landscape) and (min-width: 1000px)'

/**
 * Ob die Oberfläche im Tablet-Modus läuft. Vorgabe `false`: Ohne Provider — etwa in Einzeltests —
 * bleibt jede Komponente im Desktop-Zweig (E2).
 */
export const TabletQuerKontext = createContext(false)

/**
 * Fragt die Query genau einmal ab und stellt das Ergebnis bereit. Ein Board mit vielen Karten und
 * Labels registriert so nicht je Aufruf einen eigenen `matchMedia`-Listener. `noSsr`, damit der erste
 * Render schon den richtigen Zweig trägt. `createElement` statt JSX, damit die Datei `.ts` bleibt.
 */
export function TabletQuerProvider({ children }: Readonly<{ children: ReactNode }>) {
  const tabletQuer = useMediaQuery(TABLET_QUER_QUERY, { noSsr: true })
  return createElement(TabletQuerKontext.Provider, { value: tabletQuer }, children)
}

/** Liest die Betriebsart aus dem Kontext; ohne Provider der Desktop. */
export function useTabletQuer(): boolean {
  return useContext(TabletQuerKontext)
}
