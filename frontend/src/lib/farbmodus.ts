import type { ColorScheme } from '../api/auth'

/** Der Modus von MUIs `useColorScheme` (#1911): `system` bildet `prefers-color-scheme` nach. */
export type Farbmodus = 'system' | 'light' | 'dark'

const MODUS: Readonly<Record<ColorScheme, Farbmodus>> = { SYSTEM: 'system', LIGHT: 'light', DARK: 'dark' }

/** Der Modus zur Wahl am Konto; ohne Konto (vor der Anmeldung, nach dem Abmelden) gilt `system`. */
export function farbmodusAus(colorScheme: ColorScheme | undefined): Farbmodus {
  return colorScheme ? MODUS[colorScheme] : 'system'
}
