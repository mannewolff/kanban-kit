import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

/** Zustand und Steuerung des globalen Editiermodus. */
export interface EditModeState {
  /** Ob der Editiermodus aktiv ist (Bearbeiten-Affordances sichtbar). Default {@code false}. */
  editMode: boolean
  setEditMode: (value: boolean) => void
  toggleEditMode: () => void
}

// Default ist bewusst ein No-op mit `editMode: false`: Komponenten dürfen `useEditMode()` auch ohne
// umschließenden Provider aufrufen (z. B. in isolierten Tests), ohne dass etwas bricht.
const EditModeContext = createContext<EditModeState>({
  editMode: false,
  setEditMode: () => {},
  toggleEditMode: () => {},
})

/**
 * Stellt den globalen Editiermodus bereit. Bewusst **ohne Persistenz** (kein localStorage): Jeder
 * App-Start und jeder Reload beginnt im Ansichtsmodus ({@code editMode === false}); Bearbeiten wird
 * pro Sitzung bewusst aktiviert.
 */
export function EditModeProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [editMode, setEditMode] = useState(false)

  const toggleEditMode = useCallback(() => setEditMode((prev) => !prev), [])

  const value = useMemo<EditModeState>(
    () => ({ editMode, setEditMode, toggleEditMode }),
    [editMode, toggleEditMode],
  )

  return <EditModeContext.Provider value={value}>{children}</EditModeContext.Provider>
}

/** Liefert Zustand und Steuerung des Editiermodus. Ohne Provider ein No-op mit {@code editMode: false}. */
export function useEditMode(): EditModeState {
  return useContext(EditModeContext)
}
