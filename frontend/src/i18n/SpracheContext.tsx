import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { languageApi } from '../api/language'
import { de, type Woerterbuch } from './de'
import { woerterbuch, type Sprache } from './woerterbuch'

interface SpracheState {
  sprache: Sprache
  t: Woerterbuch
  /** Übernimmt eine neu gesetzte Sprache sofort, etwa nach dem Speichern in der Administration. */
  setzeSprache: (sprache: Sprache) => void
}

/**
 * Vorgabe ohne Provider: Deutsch (Plan #1638, A10). Bewusst kein Wurf wie bei `useAuth` — so
 * rendern alle bestehenden Tests ohne Umbau Deutsch, und ein Setzen bleibt folgenlos.
 */
const SpracheContext = createContext<SpracheState>({
  sprache: 'de',
  t: de,
  setzeSprache: () => {},
})

/**
 * Lädt die Sprache der Installation vor dem ersten Rendern (E4): Bis `GET /api/language`
 * antwortet, zeigt er nur den Ladezustand, bei einem Fehler gilt Deutsch. Danach trägt er die
 * Sprache in `lang` und den Fenstertitel ein (E8) — `index.html` bleibt als Startzustand deutsch.
 *
 * `anfangs` überspringt das Laden; dafür ist der Test-Render-Helfer `renderMitSprache` da.
 */
export function SpracheProvider({ children, anfangs }: Readonly<{ children: ReactNode; anfangs?: Sprache }>) {
  const [sprache, setSprache] = useState<Sprache | null>(anfangs ?? null)

  useEffect(() => {
    if (anfangs !== undefined) return
    let aktiv = true
    languageApi
      .lesen()
      .catch((): Sprache => 'de')
      .then((geladen) => {
        if (aktiv) setSprache(geladen)
      })
    return () => {
      aktiv = false
    }
  }, [anfangs])

  useEffect(() => {
    if (sprache === null) return
    document.documentElement.lang = sprache
    document.title = woerterbuch(sprache).APP_NAME
  }, [sprache])

  const value = useMemo(
    () => (sprache === null ? null : { sprache, t: woerterbuch(sprache), setzeSprache: setSprache }),
    [sprache],
  )

  if (value === null) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
        <CircularProgress />
      </Box>
    )
  }
  return <SpracheContext.Provider value={value}>{children}</SpracheContext.Provider>
}

/** Die Sprache und das Setzen im Kontext. */
export function useSprache(): Pick<SpracheState, 'sprache' | 'setzeSprache'> {
  const { sprache, setzeSprache } = useContext(SpracheContext)
  return { sprache, setzeSprache }
}

/** Das Wörterbuch der geltenden Sprache. */
export function useT(): Woerterbuch {
  return useContext(SpracheContext).t
}
