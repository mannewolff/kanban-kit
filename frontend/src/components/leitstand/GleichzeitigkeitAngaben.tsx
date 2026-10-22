import type { NightRunConcurrencyView } from '../../api/nightRuns'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { wartezeit } from '../../lib/leitstand'
import { AngabenListe, type Angabe } from './LeitstandBausteine'

/**
 * Die drei Angaben einer gleichzeitigen Nacht beim aufgeklappten Paket (Issue #1879, Plan #1876
 * E14, E16): „Gleichzeitig", „Wartezeit auf die Prüfung" und „Übernahme verschoben". Jede steht
 * immer da, eine fehlende als „nicht gemeldet" — in einer Nacht mit mehreren Plätzen ist das selbst
 * eine Aussage (K6). Ob die Angaben überhaupt erscheinen, entscheidet der Aufrufer an
 * `concurrency != null` (E6).
 *
 * <p>Keine Überschrift und keine Lampe: Eine Überschrift mit Lampe kennzeichnet bei festgefahrenen
 * Paketen einen Abbruch, und diese Angaben sind Auskunft, keine Störung.
 */
export function GleichzeitigkeitAngaben({ concurrency }: Readonly<{ concurrency: NightRunConcurrencyView }>) {
  const { sprache } = useSprache()
  const w = useT()
  const t = w.GLEICHZEITIGKEIT
  const zeile = (begriff: string, wert: string | null): Angabe =>
    wert === null
      ? { begriff, wert: w.NACHTLAUF_UEBERGABE.NICHT_GEMELDET, nichtGemeldet: true }
      : { begriff, wert, nichtGemeldet: false }
  const jaNein = (wert: boolean | null): string | null => {
    if (wert === null) return null
    return wert ? t.JA : t.NEIN
  }

  return (
    <AngabenListe
      zeilen={[
        zeile(t.GLEICHZEITIG, jaNein(concurrency.concurrent)),
        zeile(t.WARTEZEIT, concurrency.checkWaitMs === null ? null : wartezeit(concurrency.checkWaitMs, sprache)),
        zeile(t.VERSCHOBEN, jaNein(concurrency.deferredAtMerge)),
      ]}
    />
  )
}
