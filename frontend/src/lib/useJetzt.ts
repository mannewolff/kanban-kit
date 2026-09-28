import { useEffect, useState } from 'react'

/**
 * Die aktuelle Zeit als `Date.now()`, im übergebenen Takt aufgefrischt — für Anzeigen, deren Wert
 * sich ohne Zutun des Servers ändert, etwa „läuft seit …" an einem laufenden Nachtlauf (#1244).
 *
 * <p><b>`taktMs === null` heißt: kein Takt.</b> Dann steht die Zeit des ersten Zeichnens, und es
 * wird kein `setInterval` angelegt — eine beendete Anzeige tickt nicht.
 *
 * <p><b>Ein verborgenes Fenster rechnet nicht nach</b> — dort sieht niemand hin, und der
 * Plattform-Leitstand hält es beim Abrufen ebenso. Sichtbar wird der Wert beim nächsten Takt nach
 * dem Wiedererscheinen wieder richtig; ein eigener `visibilitychange`-Zuhörer wäre die zweite
 * Fassung derselben Regel.
 */
export function useJetzt(taktMs: number | null): number {
  const [jetzt, setJetzt] = useState(() => Date.now())

  useEffect(() => {
    if (taktMs === null) {
      return
    }
    const takt = setInterval(() => {
      if (document.visibilityState !== 'hidden') {
        setJetzt(Date.now())
      }
    }, taktMs)
    return () => clearInterval(takt)
  }, [taktMs])

  return jetzt
}
