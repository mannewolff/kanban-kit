import { useEffect, useRef } from 'react'

/**
 * Ruft `callback`, sobald das Fenster wieder in den Vordergrund kommt (Tab-Wechsel zurück oder
 * Fensterfokus). Damit bemerkt eine länger offene Session Änderungen, die in einer anderen Session
 * passiert sind (z. B. ein archiviertes/gelöschtes Board), ohne dauerndes Polling.
 *
 * Die aktuelle `callback`-Referenz wird über ein Ref gehalten, damit die Listener nicht bei jeder
 * Neudefinition der Funktion neu registriert werden müssen.
 */
export function useRefetchOnFocus(callback: () => void): void {
  const ref = useRef(callback)
  useEffect(() => {
    ref.current = callback
  })

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        ref.current()
      }
    }
    const onFocus = () => ref.current()
    document.addEventListener('visibilitychange', onVisible)
    window.addEventListener('focus', onFocus)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      window.removeEventListener('focus', onFocus)
    }
  }, [])
}
