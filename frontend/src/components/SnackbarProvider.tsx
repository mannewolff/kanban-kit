import Alert, { type AlertColor } from '@mui/material/Alert'
import Snackbar from '@mui/material/Snackbar'
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

/** Auslöser für eine kurzlebige Hinweismeldung (Toast). */
export type Notify = (message: string, severity?: AlertColor) => void

// Default ist bewusst ein No-op: Komponenten dürfen `useSnackbar()` auch ohne umschließenden
// Provider aufrufen (z. B. in isolierten Tests), ohne dass etwas bricht.
const SnackbarContext = createContext<Notify>(() => {})

interface Toast {
  message: string
  severity: AlertColor
}

/**
 * Stellt einen globalen `notify(...)`-Auslöser bereit und rendert die zugehörige MUI-`Snackbar`.
 * Eine Meldung nach der anderen; ein neuer Aufruf ersetzt die aktuelle.
 */
export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<Toast | null>(null)

  const notify = useCallback<Notify>((message, severity = 'info') => {
    setToast({ message, severity })
  }, [])

  const value = useMemo(() => notify, [notify])

  return (
    <SnackbarContext.Provider value={value}>
      {children}
      <Snackbar
        open={toast !== null}
        autoHideDuration={6000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {toast ? (
          <Alert severity={toast.severity} onClose={() => setToast(null)} variant="filled">
            {toast.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </SnackbarContext.Provider>
  )
}

/** Liefert den `notify`-Auslöser. Ohne Provider ein No-op. */
export function useSnackbar(): Notify {
  return useContext(SnackbarContext)
}
