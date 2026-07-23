import Alert, { type AlertColor } from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Slide from '@mui/material/Slide'
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

/** Auslöser für eine Toast-Meldung. */
export type Notify = (message: string, severity?: AlertColor) => void

// Default ist bewusst ein No-op: Komponenten dürfen `useSnackbar()` auch ohne umschließenden
// Provider aufrufen (z. B. in isolierten Tests), ohne dass etwas bricht.
const SnackbarContext = createContext<Notify>(() => {})

interface Toast {
  id: number
  message: string
  severity: AlertColor
}

/** Grün/Gelb/Info blenden nach dieser Zeit selbst aus; Rot (error) bleibt bis zum Wegklicken. */
const AUTO_HIDE_MS = 3000

/**
 * Stellt einen globalen `notify(...)`-Auslöser bereit und rendert die Toasts als **Stapel** oben
 * mittig (von oben einschwebend). Erfolg/Warnung/Info verschwinden nach {@link AUTO_HIDE_MS}; ein
 * Fehler (`error`) bleibt stehen und lässt sich nur über das Schließen-X entfernen. Mehrere Toasts
 * stapeln sich, damit eine Fehlermeldung nicht von einer späteren Meldung verdrängt wird.
 */
export function SnackbarProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(0)
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>())

  const remove = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
    const timer = timers.current.get(id)
    if (timer !== undefined) {
      clearTimeout(timer)
      timers.current.delete(id)
    }
  }, [])

  const notify = useCallback<Notify>(
    (message, severity = 'info') => {
      const id = nextId.current
      nextId.current += 1
      setToasts((current) => [...current, { id, message, severity }])
      // Nur Fehler bleiben stehen; alles andere blendet nach AUTO_HIDE_MS von selbst aus.
      if (severity !== 'error') {
        timers.current.set(
          id,
          setTimeout(() => remove(id), AUTO_HIDE_MS),
        )
      }
    },
    [remove],
  )

  // Beim Unmount alle noch laufenden Auto-Hide-Timer abräumen.
  useEffect(() => {
    const map = timers.current
    return () => {
      map.forEach((timer) => clearTimeout(timer))
      map.clear()
    }
  }, [])

  const value = useMemo(() => notify, [notify])

  return (
    <SnackbarContext.Provider value={value}>
      {children}
      <Box
        sx={{
          position: 'fixed',
          top: 24,
          left: 0,
          right: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 1,
          zIndex: (t) => t.zIndex.snackbar,
          pointerEvents: 'none',
        }}
      >
        {toasts.map((toast) => (
          <Slide key={toast.id} direction="down" in mountOnEnter unmountOnExit>
            <Alert
              severity={toast.severity}
              variant="filled"
              onClose={() => remove(toast.id)}
              sx={{ pointerEvents: 'auto', boxShadow: 3, minWidth: 288, maxWidth: 480 }}
            >
              {toast.message}
            </Alert>
          </Slide>
        ))}
      </Box>
    </SnackbarContext.Provider>
  )
}

/** Liefert den `notify`-Auslöser. Ohne Provider ein No-op. */
export function useSnackbar(): Notify {
  return useContext(SnackbarContext)
}
